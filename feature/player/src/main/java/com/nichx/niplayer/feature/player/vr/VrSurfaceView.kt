package com.nichx.niplayer.feature.player.vr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
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
 * 把视频解码帧当作等距柱面（equirectangular）全景贴图，用陀螺仪（[SensorManager]
 * 的 TYPE_GAME_ROTATION_VECTOR）提供视角旋转，通过 OpenGL 片段着色器从全景里实时
 * 切出一个"窗外"/环视矩形视口。支持左右（SBS）与上下（OU）两种封装格式的单眼采样。
 *
 * 设计要点：
 * - 解码仍走 MediaCodec 硬解，把输出 [Surface] 挂到本视图内部的 [android.graphics.SurfaceTexture]，
 *   由 SurfaceTexture 驱动解码帧更新；FFmpeg 音频软解链路与现有播放器完全不受影响。
 * - 默认 [GLSurfaceView.RENDERMODE_CONTINUOUSLY]：单纹理单次采样、GPU 开销极低，
 *   既能跟帧也能让陀螺仪视角平滑连续。
 * - 视角模型：把"贴饼"前方的设备朝向作为视线方向，用 3x3 旋转矩阵在着色器内完成
 *   视线→世界→经纬→全景 UV 的映射；[recenter] 把当前朝向置为视野正前方。
 * - [VrSettings.invertYaw] 通过镜像屏幕横轴实现水平转向反向，无需改陀螺仪数据。
 *
 * 生命周期：由调用方（PlayerScreen）在 VR 模式下通过 [androidx.compose.ui.viewinterop.AndroidView]
 * 挂载；视图销毁（surfaceDestroyed）时自动 detach 播放器 Surface 并释放 GL 资源。
 */
class VrSurfaceView @JvmOverloads constructor(
    context: Context,
    @androidx.annotation.MainThread private val player: NxPlayer,
) : GLSurfaceView(context), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val renderer = VrRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        // 连续渲染：画面跟随陀螺仪实时、平滑地环视
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        keepScreenOn = true
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

    /** 设置陀螺仪灵敏度（slerp 权重，越大越灵敏）。 */
    @JvmName("updateGyroSensitivity")
    fun setGyroSensitivity(sensitivity: Float) {
        renderer.setGyroSensitivity(sensitivity)
    }

    /** 视距（Zoom）倍率，>=1，越大越拉近。由外部（Compose）调用。 */
    @JvmName("updateZoom")
    fun setZoom(zoom: Float) {
        renderer.setZoom(zoom)
    }

    /** 把当前朝向置为视野正前方（同时清空手滑偏移）。 */
    fun recenter() {
        renderer.recenter()
        renderer.resetManual()
    }

    /**
     * 手滑转向（由原生触摸监听调用，主线程）。
     * @param dxPx 本次横向位移（像素），右滑为正
     * @param dyPx 本次纵向位移（像素），下滑为正
     */
    @androidx.annotation.MainThread
    fun addDragPixels(dxPx: Float, dyPx: Float) {
        renderer.addDrag(dxPx, dyPx)
    }

    /** 轻点（未位移）回调，用于唤出 VR 控制条。由 PlayerScreen 注入。 */
    var onTap: (() -> Unit)? = null

    // 原生触摸：拖动手势 → 转向；轻点（无位移）→ 唤出控制条。
    // 仅此一路处理触摸，VR 模式下外层 Compose 手势已屏蔽，互不冲突。
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
                        renderer.addDrag(dx, dy)
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

        private val quadVertices = floatArrayOf(
            // x, y, u, v
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f,
        )

        private var vertexBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        private var program = 0
        private var surfaceTexture: android.graphics.SurfaceTexture? = null
        private var texId = 0
        @Volatile private var hasNewFrame = false

        // uniform 位置
        private var uTex = 0
        private var uOrientation = 0
        private var uAspect = 0
        private var uHalfFovY = 0
        private var uZoom = 0
        private var uLayout = 0
        private var uHalfPano = 0
        private var uInvertYaw = 0

        // 视口 / 参数
        private var viewportW = 1
        private var viewportH = 1
        private var builtInAspect = 1f

        // 视角（球面单位方向）
        // 陀螺仪姿态（四元数 x,y,z,w）
        private val targetQuat = FloatArray(4).apply { this[3] = 1f } // 最近一次传感器
        private val currentQuat = FloatArray(4).apply { this[3] = 1f } // 平滑中的姿态
        private val refQuat = FloatArray(4).apply { this[3] = 1f } // 归中基准
        private val invRef = FloatArray(4)
        private val relQuat = FloatArray(4)
        private val relMatrix16 = FloatArray(16)

        @Volatile var videoSize: IntArray? = null

        /** 编译 / 链接着色器。 */
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

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)

            program = loadProgram(VERTEX_SRC, FRAGMENT_SRC)
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            uOrientation = GLES20.glGetUniformLocation(program, "uOrientation")
            uAspect = GLES20.glGetUniformLocation(program, "uAspect")
            uHalfFovY = GLES20.glGetUniformLocation(program, "uHalfFovY")
            uLayout = GLES20.glGetUniformLocation(program, "uLayout")
            uZoom = GLES20.glGetUniformLocation(program, "uZoom")
            uHalfPano = GLES20.glGetUniformLocation(program, "uHalfPano")
            uInvertYaw = GLES20.glGetUniformLocation(program, "uInvertYaw")

            // Varying 采样外部 OES 纹理：创建纹理对象
            val tmp = IntArray(1)
            GLES20.glGenTextures(1, tmp, 0)
            texId = tmp[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture = android.graphics.SurfaceTexture(texId).also { tex ->
                tex.setOnFrameAvailableListener { hasNewFrame = true }
                // 兜底初始尺寸，避免解码器首帧前无尺寸
                tex.setDefaultBufferSize(1920, 1080)
            }

            // 播放器解码帧输出到该纹理；在主线程安全切换表面
            mainHandler.post {
                player.attachSurface(Surface(surfaceTexture!!))
            }
            registerSensor()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportW = width.coerceAtLeast(1)
            viewportH = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, viewportW, viewportH)
            builtInAspect = viewportW.toFloat() / viewportH.toFloat()
        }

        override fun onDrawFrame(gl: GL10?) {
            val tex = surfaceTexture ?: return
            if (hasNewFrame) {
                try {
                    tex.updateTexImage()
                    hasNewFrame = false
                } catch (_: Exception) {
                    hasNewFrame = false
                }
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            // 平滑陀螺仪姿态并向归中基准取相对旋转，再叠加手滑视角
            smoothRotation()
            buildRelativeMatrix()
            computeOrientation(combinedOrient)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texId)
            GLES20.glUniform1i(uTex, 0)

            GLES20.glUniformMatrix3fv(uOrientation, 1, false, combinedOrient, 0)
            GLES20.glUniform1f(uAspect, builtInAspect)
            val fovRad = Math.toRadians(fovDegrees.toDouble()).toFloat()
            GLES20.glUniform1f(uHalfFovY, fovRad * 0.5f)
            GLES20.glUniform1f(uZoom, zoomFactor)
            GLES20.glUniform1i(uLayout, activeLayout)
            // 全景覆盖角度的一半（弧度）：360°→π，180°→π/2
            GLES20.glUniform1f(uHalfPano, activeHalfPanoRadians)
            GLES20.glUniform1i(uInvertYaw, if (invertYaw) 1 else 0)

            // 全屏四边形
            val aPos = GLES20.glGetAttribLocation(program, "aPos")
            val aUV = GLES20.glGetAttribLocation(program, "aUV")
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(aUV)
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUV)
        }

        // -- 姿态与旋转 --

        /** 由传感器事件（GAME_ROTATION_VECTOR）更新目标姿态四元数。 */
        @androidx.annotation.AnyThread
        fun setTargetRotation(values: FloatArray) {
            val len2 = values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
            synchronized(targetQuat) {
                targetQuat[0] = values[0]
                targetQuat[1] = values[1]
                targetQuat[2] = values[2]
                targetQuat[3] = if (len2 < 1f) Math.sqrt((1.0 - len2).toDouble()).toFloat() else 0f
            }
        }

        @androidx.annotation.MainThread
        fun recenter() {
            synchronized(refQuat) {
                System.arraycopy(targetQuat, 0, refQuat, 0, 4)
            }
        }

        private fun smoothRotation() {
            // 从目标姿态做较小幅度球面插值，得到平滑的当前姿态。
            // gyroSensitivity 越大跟手越快（越灵敏）；越小越平缓抗抖。
            val target = FloatArray(4)
            synchronized(targetQuat) { System.arraycopy(targetQuat, 0, target, 0, 4) }
            synchronized(currentQuat) {
                slerp(currentQuat, target, gyroSensitivity)
            }
        }

        private fun buildRelativeMatrix() {
            val current = FloatArray(4)
            val ref = FloatArray(4)
            synchronized(currentQuat) { System.arraycopy(currentQuat, 0, current, 0, 4) }
            synchronized(refQuat) { System.arraycopy(refQuat, 0, ref, 0, 4) }

            conjugate(ref, invRef)
            multiplyQuat(current, invRef, relQuat)
            quatToMatrix(relQuat, relMatrix16)
        }

        // -- 视角合成：设备相对旋转 × 手滑 -- //

        /** 可复用的 3x3 矩阵缓冲。 */
        private val rel3x3 = FloatArray(9)
        private val manual = FloatArray(9)
        private val combinedOrient = FloatArray(9) // 列主序 3x3，供 uniform
        private val manualYaw = FloatArray(1)
        private val manualPitch = FloatArray(1)

        /** 由手滑累计的 yaw / pitch 构建手动视角旋转（绕内容坐标轴）。 */
        private fun buildManualRotation() {
            val yaw = manualYaw[0]
            val pitch = manualPitch[0]
            val cy = Math.cos(yaw.toDouble()).toFloat(); val sy = Math.sin(yaw.toDouble()).toFloat()
            val cp = Math.cos(pitch.toDouble()).toFloat(); val sp = Math.sin(pitch.toDouble()).toFloat()
            // Ry(yaw) * Rx(pitch)：先竖直仰角，再水平转向
            // 列主序 3x3
            manual[0] = cy; manual[1] = 0f; manual[2] = -sy
            manual[3] = sp * sy; manual[4] = cp; manual[5] = sp * cy
            manual[6] = cp * sy; manual[7] = -sp; manual[8] = cp * cy
        }

        /**
         * 合成最终朝向矩阵：deviceToWorld × Rmanual(手滑)。
         * world = combined * contentDir，结果写入 [combinedOrient]。
         */
        private fun computeOrientation(out: FloatArray) {
            relMatrix16extract()
            buildManualRotation()
            mat3Mul(rel3x3, manual, out)
        }

        private fun mat3Mul(a: FloatArray, b: FloatArray, out: FloatArray) {
            // 列主序 3x3：C = A * B。a 与 out 可能不同，故先读回 a 三列
            val c0 = a[0]; val c1 = a[1]; val c2 = a[2]
            out[0] = c0 * b[0] + a[3] * b[1] + a[6] * b[2]
            out[1] = c1 * b[0] + a[4] * b[1] + a[7] * b[2]
            out[2] = c2 * b[0] + a[5] * b[1] + a[8] * b[2]
            out[3] = c0 * b[3] + a[3] * b[4] + a[6] * b[5]
            out[4] = c1 * b[3] + a[4] * b[4] + a[7] * b[5]
            out[5] = c2 * b[3] + a[5] * b[4] + a[8] * b[5]
            out[6] = c0 * b[6] + a[3] * b[7] + a[6] * b[8]
            out[7] = c1 * b[6] + a[4] * b[7] + a[7] * b[8]
            out[8] = c2 * b[6] + a[5] * b[7] + a[8] * b[8]
        }

        /** 从 relMatrix16（4x4）提取左上 3x3 到 [rel3x3]。 */
        private fun relMatrix16extract() {
            rel3x3[0] = relMatrix16[0]; rel3x3[1] = relMatrix16[1]; rel3x3[2] = relMatrix16[2]
            rel3x3[3] = relMatrix16[4]; rel3x3[4] = relMatrix16[5]; rel3x3[5] = relMatrix16[6]
            rel3x3[6] = relMatrix16[8]; rel3x3[7] = relMatrix16[9]; rel3x3[8] = relMatrix16[10]
        }

        /**
         * 手滑累加视角（主线程调用）。
         * @param dxPx 本次横向位移（相对上一次，像素），右滑为正
         * @param dyPx 本次纵向位移（像素），下滑为正
         */
        @androidx.annotation.MainThread
        fun addDrag(dxPx: Float, dyPx: Float) {
            manualYaw[0] += -dxPx * DRAG_SENSITIVITY
            manualPitch[0] += dyPx * DRAG_SENSITIVITY
        }

        /** 重置手滑视角（通常与陀螺仪归中一起调用）。 */
        @androidx.annotation.MainThread
        fun resetManual() {
            manualYaw[0] = 0f
            manualPitch[0] = 0f
        }

        private fun quatToMatrix(q: FloatArray, m: FloatArray) {
            val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
            val xx = x * x; val yy = y * y; val zz = z * z
            val xy = x * y; val xz = x * z; val yz = y * z
            val wx = w * x; val wy = w * y; val wz = w * z

            // 构造列主序 3x3 旋转矩阵，嵌入 4x4 左上角
            m[0] = 1 - 2 * (yy + zz); m[1] = 2 * (xy + wz); m[2] = 2 * (xz - wy)
            m[4] = 2 * (xy - wz); m[5] = 1 - 2 * (xx + zz); m[6] = 2 * (yz + wx)
            m[8] = 2 * (xz + wy); m[9] = 2 * (yz - wx); m[10] = 1 - 2 * (xx + yy)
        }

        private fun conjugate(q: FloatArray, out: FloatArray) {
            out[0] = -q[0]; out[1] = -q[1]; out[2] = -q[2]; out[3] = q[3]
        }

        private fun multiplyQuat(a: FloatArray, b: FloatArray, out: FloatArray) {
            val ax = a[0]; val ay = a[1]; val az = a[2]; val aw = a[3]
            val bx = b[0]; val by = b[1]; val bz = b[2]; val bw = b[3]
            out[0] = aw * bx + ax * bw + ay * bz - az * by
            out[1] = aw * by - ax * bz + ay * bw + az * bx
            out[2] = aw * bz + ax * by - ay * bx + az * bw
            out[3] = aw * bw - ax * bx - ay * by - az * bz
        }

        private fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

        private fun slerp(q1: FloatArray, q2: FloatArray, t: Float) {
            var dot = dot(q1, q2)
            var b = FloatArray(4) { q2[it] }
            if (dot < 0f) {
                dot = -dot
                for (i in 0..3) b[i] = -b[i]
            }
            if (dot > 0.9995f) {
                // 接近重合：线性插值后归一
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
        }

        private fun normalize(q: FloatArray) {
            val len = Math.sqrt((q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).toDouble()).toFloat()
            if (len > 1e-6f) {
                for (i in 0..3) q[i] /= len
            }
        }

        // -- 可调参数 --

        @Volatile private var fovDegrees: Float = 85f
        @Volatile private var activeLayout: Int = VrResolvedLayout.SBS.shaderIndex
        // 全景覆盖角度（度）：360 / 180
        @Volatile private var activeHalfPanoDeg: Int = 360
        @Volatile private var invertYaw: Boolean = false
        // 陀螺仪灵敏度（slerp 权重），越大跟手越快
        @Volatile private var gyroSensitivity: Float = DEFAULT_GYRO_SENSITIVITY
        // 视距（Zoom）倍率，>=1，越大越拉近
        @Volatile private var zoomFactor: Float = 1f

        private val activeHalfPanoRadians: Float
            get() = (Math.toRadians(activeHalfPanoDeg / 2.0)).toFloat()

        /** 播放器设置布局（0=左右 / 1=上下）与全景覆盖角度（180/360）。 */
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
            zoomFactor = zoom.coerceIn(1f, 3f)
        }

        @JvmName("rendererSetInvertYaw")
        fun setInvertYaw(invert: Boolean) {
            invertYaw = invert
        }

        @JvmName("rendererVideoSize")
        fun setVideoSize(width: Int, height: Int) {
            videoSize = intArrayOf(width, height)
            surfaceTexture?.setDefaultBufferSize(width, height)
        }
    }

    // endregion

    private companion object {

        /** GL_TEXTURE_EXTERNAL_OES (0x8D65)：外部 OES 纹理目标（隐藏类，故用字面量）。 */
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

        /** 陀螺仪灵敏度默认值（slerp 权重），越大跟手越快。 */
        private const val DEFAULT_GYRO_SENSITIVITY = 0.12f

        /** 手滑转向灵敏度（弧度/像素）。 */
        private const val DRAG_SENSITIVITY = 0.008f

        val VERTEX_SRC = """
            attribute vec4 aPos;
            attribute vec2 aUV;
            varying vec2 vUV;
            void main() {
                gl_Position = aPos;
                vUV = aUV;
            }
        """.trimIndent()

        val FRAGMENT_SRC = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUV;
            uniform samplerExternalOES uTex;
            uniform mat3 uOrientation;
            uniform float uAspect;
            uniform float uHalfFovY;
            uniform float uZoom;
            uniform int uLayout;
            uniform float uHalfPano;
            uniform int uInvertYaw;

            const float PI = 3.14159265359;
            const float TWO_PI = 6.28318530718;

            void main() {
                vec2 ndc = vUV * 2.0 - 1.0;
                if (uInvertYaw == 1) ndc.x = -ndc.x;

                // 屏幕平面 → 视点方向（屏幕外为 +z）。uZoom 倍率缩小视场等价拉近视距。
                float effHalfFovY = uHalfFovY / uZoom;
                float halfFovX = effHalfFovY * uAspect;
                vec3 view = normalize(vec3(ndc.x * tan(halfFovX),
                                           ndc.y * tan(effHalfFovY),
                                           1.0));

                // 旋转矩阵：设备朝向 → 世界方向，实现陀螺仪环视
                vec3 world = uOrientation * view;

                float lon = atan(world.x, world.z);
                float lat = asin(clamp(world.y, -1.0, 1.0));

                // 全景覆盖：uHalfPano 为该半幅对应的覆盖角的一半（弧度）。
                // 360° → PI（整幅等距柱面）；180° → PI/2（只涵盖正前方半球）。
                // 左右格式：水平跨度映射到半幅，垂直取全景全程；
                // 上下格式：垂直跨度映射到半幅，水平取全景全程。
                vec2 uv;
                if (uLayout == 0) {
                    uv = vec2((lon / (2.0 * uHalfPano) + 0.5) * 0.5,
                                 lat / PI + 0.5);
                } else {
                    uv = vec2(lon / TWO_PI + 0.5,
                              (lat / (2.0 * uHalfPano) + 0.5) * 0.5);
                }
                // SurfaceTexture 帧纵向与此处坐标相反，翻转 v 使画面保持正立
                uv.y = 1.0 - uv.y;
                gl_FragColor = texture2D(uTex, uv);
            }
        """.trimIndent()
    }
}