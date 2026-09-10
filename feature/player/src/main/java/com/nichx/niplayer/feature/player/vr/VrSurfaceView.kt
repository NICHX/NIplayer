package com.nichx.niplayer.feature.player.vr

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import com.nichx.niplayer.player.kernel.NxPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 布局格式索引（已解析，用于采样半幅）。
 * - [SBS] 左右格式，取左半幅
 * - [OU] 上下格式，取上半幅
 */
enum class VrResolvedLayout(val shaderIndex: Int) {
    SBS(0),
    OU(1),
}

/**
 * VR（全景单眼）播放视图。
 *
 * 重设计：放弃"全屏四边形 + 片元和点着色器重投影 + 手写四元数"的旧方案，改为
 * **球面网格（mesh sphere）+ 相机在球心** 的经典 360 观看模型：
 * - 解码仍走 MediaCodec 硬解，输出 [Surface] 挂到视图内部的 [android.graphics.SurfaceTexture]，
 *   由 GL 线程以 `samplerExternalOES` 采样；FFmpeg 音频软解链路不受影响。
 * - 球面网格在 GL 线程 CPU 生成一次（仅位置，UV 在片元里由球面方向反算），相机置于球心，
 *   view 矩阵来自陀螺仪（`SensorManager.getRotationMatrixFromVector` +
 *   `remapCoordinateSystem` 矫正横屏 `Display.rotation`），projection 由 FOV/视距决定。
 * - [recenter] 通过 volatile 标志在 GL 线程消费，把当前朝向置为视野正前方。
 * - [VrSettings.invertYaw] 在片元里反转经度（镜像水平转向）。
 *
 * 线程铁律：主线程只写 volatile 标量 / 排队，绝不触碰 GL / SurfaceTexture / EGL，
 * 从根上规避旧版"主线程 `setDefaultBufferSize` 与 GL 线程 `updateTexImage` 争 BufferQueue 锁"导致的 ANR。
 */
class VrSurfaceView @JvmOverloads constructor(
    context: Context,
    @androidx.annotation.MainThread private val player: NxPlayer,
) : GLSurfaceView(context), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // 显示（屏幕）相对设备机身的旋转，0/1/2/3 对应 0°/90°/180°/270°。
    // 播放期间方向通常已锁定，构造时在主线程解析一次即可；GL 线程据此重映射传感器坐标系。
    private val displayRotQuarter: Int = resolveDisplayRotQuarter(context)

    private val renderer = VrRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        // 连续渲染：画面跟随陀螺仪实时、平滑地环视
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        keepScreenOn = true
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplayRotQuarter(ctx: Context): Int {
        val wm = (ctx as? Activity)?.windowManager
            ?: ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return 0
        return when (wm.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 1
            Surface.ROTATION_180 -> 2
            Surface.ROTATION_270 -> 3
            else -> 0
        }
    }

    /**
     * 更新视频源尺寸，并把感知尺寸同步给 SurfaceTexture
     * （多数路径由 MediaCodec 自行决定，这里仅兜底）。
     */
    @JvmName("updateVideoSize")
    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        renderer.setVideoSize(width, height)
    }

    /**
     * 设置画面格式。
     *
     * @param layout [VrResolvedLayout] 的 shaderIndex（0=左右 / 1=上下）
     * @param halfPanoDeg 全景覆盖角度：360 或 180
     */
    @JvmName("updateFormat")
    fun setFormat(layout: Int, halfPanoDeg: Int) {
        renderer.setFormat(layout, halfPanoDeg)
    }

    /** 设置垂直视场角（度）。 */
    @JvmName("updateFov")
    fun setFovDegrees(fov: Float) {
        renderer.setFovDegrees(fov)
    }

    /** 设置水平转向反向开关。 */
    @JvmName("updateInvertYaw")
    fun setInvertYaw(invert: Boolean) {
        renderer.setInvertYaw(invert)
    }

    /** 设置视角锁定：锁定后陀螺仪不再转动视角（画面固定）。 */
    @JvmName("updateViewLocked")
    fun setViewLocked(locked: Boolean) {
        renderer.setViewLocked(locked)
    }

    /** 设置陀螺仪灵敏度（slerp 权重，越大越灵敏）。 */
    @JvmName("updateGyroSensitivity")
    fun setGyroSensitivity(sensitivity: Float) {
        renderer.setGyroSensitivity(sensitivity)
    }

    /** 视距（Zoom）倍率，MIN_ZOOM~MAX_ZOOM。>1 推近，<1 拉远。由外部（Compose）调用。 */
    @JvmName("updateZoom")
    fun setZoom(zoom: Float) {
        renderer.setZoom(zoom)
    }

    /** 把当前朝向置为视野正前方（仅置标志，GL 线程消费，保持主线程不碰 GL）。 */
    fun recenter() {
        renderer.recenter()
    }

    /** 轻点（未位移）回调，用于唤出 VR 控制条。由 PlayerScreen 注入。 */
    var onTap: (() -> Unit)? = null

    // 原生触摸：轻点（无位移）→ 唤出控制条。仅此一路处理触摸，VR 模式下外层 Compose 手势已屏蔽。
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var touchMoved = false

    init {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownTime = event.eventTime
                    touchMoved = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    touchDownX = event.x
                    touchDownY = event.y
                    if (dx * dx + dy * dy > 1f) {
                        touchMoved = true
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!touchMoved &&
                        event.eventTime - touchDownTime < 300
                    ) {
                        onTap?.invoke()
                    }
                    touchMoved = false
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    touchMoved = false
                }
                else -> return@setOnTouchListener false
            }
            true
        }
    }

    // region 陀螺仪

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // 主线程只拷贝到 volatile 引用，不做任何矩阵/四元数计算；由 GL 线程每帧消费
            renderer.setTargetRotation(event.values)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private var sensorRegistered = false

    private fun registerSensor() {
        if (sensorRegistered) return
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            sensorRegistered = true
        }
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager.unregisterListener(this)
        sensorRegistered = false
    }

    // endregion

    override fun onDetachedFromWindow() {
        runCatching { unregisterSensor() }
        // 视图从窗口移除（Compose 销毁 AndroidView）：解除播放器表面，避免解码帧落入已失效纹理
        try {
            player.attachSurface(null)
        } catch (_: Exception) {
        }
        super.onDetachedFromWindow()
    }

    // region 渲染器

    private inner class VrRenderer : GLSurfaceView.Renderer {

        // -- 着色器 / 对象句柄 --
        private var program = 0
        private var aPos = 0
        private var uMvp = 0
        private var uTex = 0
        private var uLayout = 0
        private var uHalfPano = 0
        private var uInvertYaw = 0
        private val uMvpBuf = FloatArray(16)

        // -- 球面网格 --
        private var sphereVB = 0
        private var sphereCount = 0

        // -- 解码纹理 --
        private var texId = 0
        @Volatile private var surfaceTexture: android.graphics.SurfaceTexture? = null
        @Volatile private var hasNewFrame = false
        // 已下发到 GL 线程的缓冲尺寸（变更守卫，避免主线程重复 setDefaultBufferSize）
        @Volatile private var lastBufferSetW = 0
        @Volatile private var lastBufferSetH = 0

        // -- 视口 --
        private var viewportW = 1
        private var viewportH = 1
        private var aspect = 1f

        // -- 姿态（平滑中的 device→world 四元数 + 归中基准） --
        private val currentQuat = FloatArray(4).apply { this[3] = 1f }
        private val refQuat = FloatArray(4).apply { this[3] = 1f }
        @Volatile private var targetRotVec: FloatArray? = null
        @Volatile private var recenterRequested = false
        private var firstPose = true // 首次有效传感器帧时自动归中，使进入 VR 时默认就面向设备当前朝向
        private val lastMvp = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            setPerspective(this, 85f, 1f, 1f)
        }

        // -- 可调参数 --
        @Volatile private var fovDegrees: Float = 85f
        @Volatile private var activeLayout: Int = VrResolvedLayout.SBS.shaderIndex
        @Volatile private var activeHalfPanoDeg: Int = 360
        @Volatile private var invertYaw: Boolean = false
        @Volatile private var viewLocked: Boolean = false
        @Volatile private var gyroSensitivity: Float = DEFAULT_GYRO_SENSITIVITY
        @Volatile private var zoomFactor: Float = 1f

        private val activeHalfPanoRadians: Float
            get() = (Math.toRadians(activeHalfPanoDeg / 2.0)).toFloat()

        // -- 公共参数入口（全部只写 volatile，主线程安全，不触碰 GL） --

        @JvmName("rendererSetFormat")
        fun setFormat(layout: Int, halfPanoDeg: Int) {
            activeLayout = layout
            activeHalfPanoDeg = halfPanoDeg
        }

        @JvmName("rendererSetFov")
        fun setFovDegrees(fov: Float) {
            fovDegrees = fov.coerceIn(30f, 120f)
        }

        @JvmName("rendererSetGyroSensitivity")
        fun setGyroSensitivity(sensitivity: Float) {
            gyroSensitivity = sensitivity.coerceIn(0.05f, 0.5f)
        }

        @JvmName("rendererSetZoom")
        fun setZoom(zoom: Float) {
            // 视距可拉远（<1，视野变宽）也可推近（>1）
            zoomFactor = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        }

        @JvmName("rendererSetInvertYaw")
        fun setInvertYaw(invert: Boolean) {
            invertYaw = invert
        }

        @JvmName("rendererSetViewLocked")
        fun setViewLocked(locked: Boolean) {
            viewLocked = locked
        }

        /** 主线程：只拷贝旋转向量到 volatile；GL 线程消费。 */
        @androidx.annotation.AnyThread
        fun setTargetRotation(values: FloatArray) {
            targetRotVec = values.copyOf(4)
        }

        /** 主线程：只置标志；GL 线程在下一帧归中。 */
        @androidx.annotation.MainThread
        fun recenter() {
            recenterRequested = true
        }

        @JvmName("rendererVideoSize")
        fun setVideoSize(width: Int, height: Int) {
            val buf = surfaceTexture ?: return
            // setDefaultBufferSize 必须在拥有 EGL 上下文的 GL 线程调用；经 queueEvent 投递，
            // 并用尺寸守卫避免主线程反复与 updateTexImage 争用 BufferQueue 锁（ANR 根因规避）。
            if (width == lastBufferSetW && height == lastBufferSetH) return
            lastBufferSetW = width
            lastBufferSetH = height
            queueEvent {
                try {
                    buf.setDefaultBufferSize(width, height)
                } catch (_: Exception) {
                }
            }
        }

        // -- 着色器编译 / 链接 --

        private fun loadProgram(vsSrc: String, fsSrc: String): Int {
            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(prog)
                throw RuntimeException("VR shader link failed: $log")
            }
            return prog
        }

        private fun compileShader(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                throw RuntimeException("VR shader compile failed: $log")
            }
            return shader
        }

        // -- 球面网格（CPU 生成，仅位置；UV 在片元由方向反算，规避极点伪影） --

        private fun buildSphere(longSeg: Int, latSeg: Int): FloatArray {
            val verts = ArrayList<Float>(longSeg * latSeg * 6 * 3)
            val dTheta = 2.0 * Math.PI / longSeg
            for (j in 0 until latSeg) {
                val phi0 = (Math.PI / 2 - (j).toDouble() * Math.PI / latSeg)
                val phi1 = (Math.PI / 2 - (j + 1).toDouble() * Math.PI / latSeg)
                for (i in 0 until longSeg) {
                    val th0 = i.toDouble() * dTheta
                    val th1 = (i + 1).toDouble() * dTheta
                    fun v(phi: Double, th: Double): FloatArray {
                        val cp = Math.cos(phi)
                        return floatArrayOf(
                            (Math.cos(th) * cp).toFloat(),
                            (Math.sin(phi)).toFloat(),
                            (Math.sin(th) * cp).toFloat(),
                        )
                    }
                    val a = v(phi0, th0)
                    val b = v(phi1, th0)
                    val c = v(phi1, th1)
                    val d = v(phi0, th1)
                    verts.addAll(a.asIterable()); verts.addAll(b.asIterable()); verts.addAll(c.asIterable())
                    verts.addAll(a.asIterable()); verts.addAll(c.asIterable()); verts.addAll(d.asIterable())
                }
            }
            return verts.toFloatArray()
        }

        // -- 相机矩阵（球心、向 +z 看，经 view=refR*curR^T 做相对旋转与归中） --

        /** 把当前 device→world 旋转向量（展示重映射后）作平滑并合成 mvp。在 GL 线程调用。 */
        private fun updateCamera() {
            val rv = targetRotVec ?: return // 尚无传感器数据，沿用 lastMvp
            // 视角锁定：跳过传感器姿态更新与归中，保持当前视角不变（画面固定）
            if (viewLocked) return
            val base = FloatArray(9)
            // GAME_ROTATION_VECTOR 旋转向量 → 3x3 device→world（API 37 起返回 void，直接填 R）
            SensorManager.getRotationMatrixFromVector(base, rv)

            // 按横屏 Display.rotation 重映射，把"屏幕坐标系"对齐到陀螺仪设备系（方向修正核心）
            val remap = FloatArray(9)
            if (SensorManager.remapCoordinateSystem(base, remapXAxis, remapYAxis, remap)) {
                System.arraycopy(remap, 0, base, 0, 9)
            }

            // 3x3 → 四元数，做 slerp 平滑（对 device→world 姿态平滑，避免归一化漂移）
            val targetQuat = FloatArray(4)
            matToQuat(base, targetQuat)
            slerp(currentQuat, targetQuat, gyroSensitivity)

            // recenter：以"原始设备朝向"(targetQuat) 为归中基准，而非平滑后的 currentQuat。
            // 首帧自动归中 → 进入即面向设备当前朝向；手动回中 → 立即将当前看的方向置为前方。
            if (firstPose) {
                System.arraycopy(targetQuat, 0, refQuat, 0, 4)
                firstPose = false
            } else if (recenterRequested) {
                System.arraycopy(targetQuat, 0, refQuat, 0, 4)
                recenterRequested = false
            }

            // 相对旋转 view = refR * curR^T；归中（current==ref）时 view=identity → 前方=pano 中心
            val curR = quatToMatrix3(currentQuat)
            val curRT = transpose3(curR)
            val refR = quatToMatrix3(refQuat)
            val view3 = mat3Mul(refR, curRT)

            // 3x3 view 嵌入 4x4（列主序），projection 由 FOV/视距决定
            val view16 = FloatArray(16)
            view16[0] = view3[0]; view16[1] = view3[1]; view16[2] = view3[2]; view16[3] = 0f
            view16[4] = view3[3]; view16[5] = view3[4]; view16[6] = view3[5]; view16[7] = 0f
            view16[8] = view3[6]; view16[9] = view3[7]; view16[10] = view3[8]; view16[11] = 0f
            view16[12] = 0f; view16[13] = 0f; view16[14] = 0f; view16[15] = 1f

            val proj16 = FloatArray(16)
            // 有效透视竖直 FOV（度）：视距 zoom<1 拉远（FOV 变大），>1 推近（FOV 变小）
            val effFovDeg = (fovDegrees / zoomFactor).coerceIn(5f, 165f)
            Matrix.perspectiveM(proj16, 0, effFovDeg, aspect, 0.1f, 10f)

            Matrix.multiplyMM(uMvpBuf, 0, proj16, 0, view16, 0)
        }

        private fun setPerspective(out: FloatArray, fovY: Float, aspect: Float, near: Float = 0.1f, far: Float = 10f) {
            Matrix.perspectiveM(out, 0, fovY, aspect, near, far)
        }

        private fun matToQuat(m: FloatArray, out: FloatArray) {
            val m00 = m[0]; val m10 = m[1]; val m20 = m[2]
            val m01 = m[3]; val m11 = m[4]; val m21 = m[5]
            val m02 = m[6]; val m12 = m[7]; val m22 = m[8]
            val tr = m00 + m11 + m22
            var s: Double
            val x: Double; val y: Double; val z: Double; val w: Double
            when {
                tr > 0f -> {
                    s = Math.sqrt(tr + 1.0) * 2.0
                    w = 0.25 * s
                    x = (m21 - m12) / s
                    y = (m02 - m20) / s
                    z = (m10 - m01) / s
                }
                m00 > m11 && m00 > m22 -> {
                    s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0
                    w = (m21 - m12) / s
                    x = 0.25 * s
                    y = (m01 + m10) / s
                    z = (m02 + m20) / s
                }
                m11 > m22 -> {
                    s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0
                    w = (m02 - m20) / s
                    x = (m01 + m10) / s
                    y = 0.25 * s
                    z = (m12 + m21) / s
                }
                else -> {
                    s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0
                    w = (m10 - m01) / s
                    x = (m02 + m20) / s
                    y = (m12 + m21) / s
                    z = 0.25 * s
                }
            }
            val n = Math.sqrt(x * x + y * y + z * z + w * w)
            if (n > 1e-6) {
                out[0] = (x / n).toFloat()
                out[1] = (y / n).toFloat()
                out[2] = (z / n).toFloat()
                out[3] = (w / n).toFloat()
            } else {
                out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 1f
            }
        }

        /** 四元数 → 3x3 列主序旋转矩阵（device→world）。 */
        private fun quatToMatrix3(q: FloatArray): FloatArray {
            val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
            val xx = x * x; val yy = y * y; val zz = z * z
            val xy = x * y; val xz = x * z; val yz = y * z
            val wx = w * x; val wy = w * y; val wz = w * z
            return floatArrayOf(
                1 - 2 * (yy + zz), 2 * (xy + wz), 2 * (xz - wy),
                2 * (xy - wz), 1 - 2 * (xx + zz), 2 * (yz + wx),
                2 * (xz + wy), 2 * (yz - wx), 1 - 2 * (xx + yy),
            )
        }

        private fun transpose3(m: FloatArray): FloatArray =
            floatArrayOf(
                m[0], m[3], m[6],
                m[1], m[4], m[7],
                m[2], m[5], m[8],
            )

        /** 3x3 列主序矩阵相乘。 */
        private fun mat3Mul(a: FloatArray, b: FloatArray): FloatArray {
            val r = FloatArray(9)
            for (col in 0 until 3) {
                for (row in 0 until 3) {
                    r[col * 3 + row] =
                        a[0 * 3 + row] * b[col * 3 + 0] +
                        a[1 * 3 + row] * b[col * 3 + 1] +
                        a[2 * 3 + row] * b[col * 3 + 2]
                }
            }
            return r
        }

        private fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

        private fun slerp(q1: FloatArray, q2: FloatArray, t: Float) {
            var dot = dot(q1, q2)
            val b = q2.copyOf()
            if (dot < 0f) {
                dot = -dot
                for (i in 0..3) b[i] = -b[i]
            }
            if (dot > 0.9995f || t >= 1f) {
                for (i in 0..3) q1[i] += t * (b[i] - q1[i])
                normalize(q1)
                return
            }
            val theta0 = Math.acos(dot.coerceIn(-1f, 1f).toDouble()).toFloat()
            val theta = theta0 * t
            val sinTheta = Math.sin(theta.toDouble())
            val sinTheta0 = Math.sin(theta0.toDouble())
            val s0 = Math.cos(theta.toDouble()) - dot * sinTheta / sinTheta0
            val s1 = sinTheta / sinTheta0
            for (i in 0..3) {
                q1[i] = (s0 * q1[i] + s1 * b[i]).toFloat()
            }
            normalize(q1)
        }

        private fun normalize(q: FloatArray) {
            val len = Math.sqrt((q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).toDouble()).toFloat()
            if (len > 1e-6f) {
                for (i in 0..3) q[i] /= len
            }
        }

        // -- GLSurfaceView.Renderer 生命周期 --

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            // 单球无遮挡，球内表面面向相机，关闭背面剔除规避 winding 问题
            GLES20.glDisable(GLES20.GL_CULL_FACE)

            program = loadProgram(VERTEX_SRC, FRAGMENT_SRC)
            // attrib/uniform 位置在链接后缓存一次，onDrawFrame 内禁止 glGet*
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            uMvp = GLES20.glGetUniformLocation(program, "uMvp")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uLayout = GLES20.glGetUniformLocation(program, "uLayout")
            uHalfPano = GLES20.glGetUniformLocation(program, "uHalfPano")
            uInvertYaw = GLES20.glGetUniformLocation(program, "uInvertYaw")

            // 球面 VBO
            val sphere = buildSphere(SPHERE_LONG_SEG, SPHERE_LAT_SEG)
            sphereCount = sphere.size / 3
            val vb = ByteBuffer.allocateDirect(sphere.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vb.put(sphere)
            vb.position(0)
            val buf = IntArray(1)
            GLES20.glGenBuffers(1, buf, 0)
            sphereVB = buf[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVB)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, sphere.size * 4, vb, GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

            // OES 外部纹理 + SurfaceTexture
            val tmp = IntArray(1)
            GLES20.glGenTextures(1, tmp, 0)
            texId = tmp[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0)

            surfaceTexture = android.graphics.SurfaceTexture(texId).also { tex ->
                tex.setOnFrameAvailableListener { hasNewFrame = true }
                // GL 线程兜底初始尺寸，避免解码器首帧前尺寸未知（MediaCodec 随后自行覆盖）
                tex.setDefaultBufferSize(1920, 1080)
            }

            // 播放器解码帧输出到该纹理；主线程安全切换表面，每次重建面只挂一次
            mainHandler.post {
                try {
                    val tex = surfaceTexture
                    if (tex != null) player.attachSurface(Surface(tex))
                } catch (_: Exception) {
                }
            }
            registerSensor()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportW = width.coerceAtLeast(1)
            viewportH = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, viewportW, viewportH)
            aspect = viewportW.toFloat() / viewportH.toFloat()
        }

        override fun onDrawFrame(gl: GL10?) {
            val tex = surfaceTexture
            if (tex != null && hasNewFrame) {
                try {
                    tex.updateTexImage()
                    hasNewFrame = false
                } catch (_: Exception) {
                    hasNewFrame = false
                }
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (program == 0) return

            GLES20.glUseProgram(program)

            // 相机矩阵：球心、陀螺仪相对旋转 + 透视
            updateCamera()

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, uMvpBuf, 0)
            GLES20.glUniform1i(uLayout, activeLayout)
            GLES20.glUniform1f(uHalfPano, activeHalfPanoRadians)
            GLES20.glUniform1i(uInvertYaw, if (invertYaw) 1 else 0)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sphereVB)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sphereCount)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0)
        }
    }

    // endregion

    private val remapXAxis: Int
        get() = when (displayRotQuarter) {
            1 -> SensorManager.AXIS_Y
            2 -> SensorManager.AXIS_MINUS_X
            3 -> SensorManager.AXIS_MINUS_Y
            else -> SensorManager.AXIS_X
        }

    private val remapYAxis: Int
        get() = when (displayRotQuarter) {
            1 -> SensorManager.AXIS_MINUS_X
            2 -> SensorManager.AXIS_MINUS_Y
            3 -> SensorManager.AXIS_X
            else -> SensorManager.AXIS_Y
        }

    private companion object {

        /** GL_TEXTURE_EXTERNAL_OES (0x8D65)：外部 OES 纹理目标（隐藏类，故用字面量）。 */
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

        /** 陀螺仪灵敏度默认值（slerp 权重），越大跟手越快。 */
        private const val DEFAULT_GYRO_SENSITIVITY = 0.12f

        /** 视距（Zoom）可调范围：<1 拉远（视野变宽），>1 推近（视野变窄）。 */
        private const val MIN_ZOOM = 0.4f
        private const val MAX_ZOOM = 3f

        /** 球面网格细分：经线 × 纬线。 */
        private const val SPHERE_LONG_SEG = 96
        private const val SPHERE_LAT_SEG = 48

        val VERTEX_SRC = """
            attribute vec3 aPos;
            uniform mat4 uMvp;
            varying vec3 vDir;
            void main() {
                vDir = aPos;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """.trimIndent()

        val FRAGMENT_SRC = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec3 vDir;
            uniform samplerExternalOES uTex;
            uniform int uLayout;
            uniform float uHalfPano;
            uniform int uInvertYaw;

            const float PI = 3.14159265359;
            const float TWO_PI = 6.28318530718;

            void main() {
                // 球面顶点方向（对象空间）。相机在球心，view=identity 时前方为 -z（透视向 -z 看），
                // 故以 -z 为前向：经度 = atan(x, -z)。uInvertYaw 镜像水平（反转 yaw）。
                vec3 w = normalize(vDir);
                float lon = atan(w.x, -w.z);
                if (uInvertYaw == 1) lon = -lon;
                float lat = asin(clamp(w.y, -1.0, 1.0));

                // 180° 内容：超出全景覆盖角（SBS 水平 ±90° / OU 垂直 ±90°）的区域置黑，
                // 避免 uv 越出对应半幅露出另一只眼或贴边拉伸，形成"拼接错位"。
                // 360°（uHalfPano=π）时该判断恒为假，不影响。
                float halfPano = uHalfPano;
                bool outside = (uLayout == 0 && abs(lon) > halfPano) ||
                               (uLayout == 1 && abs(lat) > halfPano);
                if (outside) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }

                // 半幅采样：SBS 水平映射到半幅（取左半幅），OU 垂直映射到上半幅。
                vec2 uv = (uLayout == 0)
                    ? vec2((lon / (2.0 * uHalfPano) + 0.5) * 0.5, lat / PI + 0.5)
                    : vec2(lon / TWO_PI + 0.5,                   (lat / (2.0 * uHalfPano) + 0.5) * 0.5);
                // SurfaceTexture 帧纵向与此处坐标相反，翻转 v 使画面保持正立
                uv.y = 1.0 - uv.y;
                gl_FragColor = texture2D(uTex, uv);
            }
        """.trimIndent()
    }
}