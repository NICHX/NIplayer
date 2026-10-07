package com.nichx.niplayer.designsystem.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 底部 chrome（导航栏等玻璃浮层）的**暗度标量**：`0 = 亮式`，`1 = 暗式`，`NaN = 未决定`。
 *
 * 移植自 MeloX-Android 的 `LocalBottomBarTone`：底栏不跟随应用主题二值，而是**由背后内容的
 * 感知亮度决定**，在明暗之间整体翻转（Apple 对 navbars / tabbars 的定义）。
 *
 * ⚠ **一切深浅相关的取值都必须由这一个标量推出来**（`lerp(亮端点, 暗端点, tone)`）。
 * 任何一层再自己去读主题的 `isDark`，都会在 tone 停在中间态时错位 —— 表现为「切换了一半」。
 */
val LocalNiBottomBarTone = staticCompositionLocalOf { Float.NaN }

/**
 * 读取当前底栏暗度；未被 [NiBottomBarToneHost] Provide 时，退化为跟随主题的二值。
 */
@Composable
fun niBottomBarTone(): Float {
    val provided = LocalNiBottomBarTone.current
    return if (provided.isNaN()) (if (NiExtraColors.current.isDark) 1f else 0f) else provided
}

/** 采样位图尺寸：只需输出均值，尽量小以压低分配与读像素成本。 */
private const val SAMPLE_W = 32
private const val SAMPLE_H = 8

/** 明暗稳定且离翻转门槛较远时的采样间隔。底栏停着时不需要频繁抓窗口。 */
private const val SAMPLE_INTERVAL_IDLE_MS = 2_000L

/** 亮度贴近门槛时加快采样，避免滑过深色区块要等两秒才反应。 */
private const val SAMPLE_INTERVAL_NEAR_MS = 500L

/** 出现一次越界、正在确认时更快。 */
private const val SAMPLE_INTERVAL_CONFIRM_MS = 200L

/** 离门槛不到这么多 L* 视为「贴近」，改用 [SAMPLE_INTERVAL_NEAR_MS]。 */
private const val TONE_NEAR_BAND_LSTAR = 8f

/** 单次 PixelCopy 超时兜底——拿不到回调也不能让循环卡死。 */
private const val SAMPLE_TIMEOUT_MS = 900L

/** 采样带高度：底栏上沿再往上这一条。 */
private const val TONE_BAND_DP = 8f

/** 采样带左右各内缩，躲开屏幕圆角/边缘那圈不代表内容的像素。 */
private const val TONE_BAND_INSET_DP = 8f

/**
 * 切换判据的**中点**，单位 CIELAB L*（感知亮度，0–100）。
 *
 * ⚠ 不要用线性光 Y 当中灰：`Y = 0.5` 对应 `L* ≈ 72`（肉眼明显偏亮），拿 Y 当门槛会把
 * 「本该切暗式」的内容判成亮式。L* 才是感知等距标度，`L* = 50` 才是字面中灰。
 */
private const val TONE_FLIP_MID_LSTAR = 50.0f

/**
 * 围绕 [TONE_FLIP_MID_LSTAR] 的双侧迟滞宽度（L* 单位）：落在中间 2Δ 窗口里的内容保持现状，
 * 配合净采样（不含自身玻璃）⇒ 背景静止时候选值为常数 ⇒ 数学上不可能来回翻转。
 */
private const val TONE_FLIP_DEADBAND_LSTAR = 5.0f

/** 需要连续几次「同侧越界」才真的切换，过滤封面旋转/进度条这类单帧噪声。 */
private const val TONE_CONFIRM_COUNT = 2

/** 两次翻转之间的最短驻留，既覆盖过渡收敛时间，也挡掉快速滑过时的一连串翻转。 */
private const val TONE_SWITCH_COOLDOWN_MS = 400L

/** 过渡曲线：刻意用临界阻尼（`dampingRatio = 1`）——换材质调子，过冲会被读成「闪一下」。 */
private val NiToneTransition = spring<Float>(dampingRatio = 1f, stiffness = 180f)

/**
 * 底栏暗度采样状态。由 [NiBottomBarToneHost] 创建，底栏经 [updateBounds] 汇报自身窗口矩形。
 */
class NiBottomBarToneState internal constructor() {

    /** 窗口坐标系里底栏的矩形；由 `onGloballyPositioned` 喂进来。 */
    internal var bounds: Rect? = null

    /** 对外消费的动画目标（`mutableStateOf`，驱动重组）。`NaN` = 还没决出来。 */
    internal var target by mutableFloatStateOf(Float.NaN)
        private set

    private var bitmap: Bitmap? = null

    /** 已经释放——采样循环看到它必须退出，别再去 `ensureBitmap()` 造新的。 */
    internal var released: Boolean = false

    internal fun publishTarget(v: Float) {
        target = v
    }

    /**
     * **本次采样应抓的矩形**：底栏**上沿再往上**一条带。
     *
     * ⚠ 绝不能用 [bounds] 本身——那里面已经混进我们自己的玻璃涂层，会与判据形成自反馈环路。
     * 只采底栏上方不含自身玻璃的背景，判据即退化为内容本身，配对称迟滞后不可能来回翻。
     */
    internal fun sampleRect(bandPx: Float, insetPx: Float): Rect? {
        val b = bounds ?: return null
        if (b.width() <= 0 || b.height() <= 0) return null
        val left = b.left + insetPx.toInt()
        val right = b.right - insetPx.toInt()
        if (right - left <= 0) return null
        val bottom = b.top
        val top = bottom - bandPx.toInt()
        if (top < 0) return null
        return Rect(left, top, right, bottom)
    }

    /** 由底栏在 `onGloballyPositioned` 中汇报窗口矩形。 */
    fun updateBounds(coords: LayoutCoordinates) {
        val pos = coords.localToWindow(Offset.Zero)
        bounds = Rect(
            pos.x.toInt(),
            pos.y.toInt(),
            (pos.x + coords.size.width).toInt(),
            (pos.y + coords.size.height).toInt(),
        )
    }

    internal fun ensureBitmap(): Bitmap =
        bitmap ?: Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888).also { bitmap = it }

    internal fun release() {
        released = true
        // 不 recycle：PixelCopy 可能仍在另一个线程写这张图。仅 32x8，交给 GC 回收。
        bitmap = null
    }
}

/**
 * 创建并驱动底栏暗度采样：周期性 PixelCopy 底栏上方背景带 → 感知亮度 → 迟滞切换 → 过渡动画。
 *
 * @param systemDark 系统/应用主题是否为暗色，作为「尚未决出」时的打底值。
 */
@Composable
fun rememberNiBottomBarToneState(systemDark: Boolean): NiBottomBarToneState {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { NiBottomBarToneState() }
    val bandPx = remember(density) { with(density) { TONE_BAND_DP.dp.toPx() } }
    val insetPx = remember(density) { with(density) { TONE_BAND_INSET_DP.dp.toPx() } }

    LaunchedEffect(state, systemDark, lifecycle) {
        val window = context.findActivity()?.window
        val fallback = if (systemDark) 1f else 0f
        if (window == null) {
            state.publishTarget(fallback)
            return@LaunchedEffect
        }
        // 首拍先落到系统主题，避免还没采样就翻一下。
        state.publishTarget(fallback)

        var decided = Float.NaN
        var pending = Float.NaN
        var pendingCount = 0
        var lastSwitchAt = 0L
        var intervalMs = SAMPLE_INTERVAL_IDLE_MS

        while (true) {
            delay(intervalMs)
            if (state.released) break
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                intervalMs = SAMPLE_INTERVAL_IDLE_MS
                continue
            }
            val rect = state.sampleRect(bandPx, insetPx) ?: continue
            val lum = withTimeoutOrNull(SAMPLE_TIMEOUT_MS) { state.capture(window, rect) } ?: continue
            val lstar = yToLstar(lum)

            val current = if (decided.isNaN()) fallback else decided
            val threshold = if (current > 0.5f) {
                TONE_FLIP_MID_LSTAR + TONE_FLIP_DEADBAND_LSTAR
            } else {
                TONE_FLIP_MID_LSTAR - TONE_FLIP_DEADBAND_LSTAR
            }
            val want = if (current > 0.5f) {
                // 现在暗式 ⇒ 背后要明显亮于感知中灰才回亮式
                if (lstar > threshold) 0f else 1f
            } else {
                // 现在亮式 ⇒ 背后要明显暗于感知中灰才进暗式
                if (lstar < threshold) 1f else 0f
            }
            val nearThreshold = abs(lstar - threshold) < TONE_NEAR_BAND_LSTAR

            if (want == current) {
                pending = Float.NaN
                pendingCount = 0
                intervalMs = if (nearThreshold) SAMPLE_INTERVAL_NEAR_MS else SAMPLE_INTERVAL_IDLE_MS
                continue
            }
            intervalMs = SAMPLE_INTERVAL_CONFIRM_MS
            if (pending != want) {
                pending = want
                pendingCount = 1
            } else {
                pendingCount++
            }
            if (pendingCount < TONE_CONFIRM_COUNT) continue

            val now = SystemClock.uptimeMillis()
            if (now - lastSwitchAt < TONE_SWITCH_COOLDOWN_MS) continue

            decided = want
            pendingCount = 0
            lastSwitchAt = now
            state.publishTarget(want)
        }
    }

    return state
}

/**
 * 读取当前暗度（带动画）；无论采样是否可用都返回稳定值。
 *
 * @param systemDark 系统/应用主题（打底值）。
 */
@Composable
fun NiBottomBarToneState.darkness(systemDark: Boolean): Float {
    val fallback = if (systemDark) 1f else 0f
    val targetValue = if (target.isNaN()) fallback else target
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = NiToneTransition,
        label = "ni-bottom-bar-tone",
    ).value
}

/**
 * 采样宿主：创建 [NiBottomBarToneState]，把动画后的暗度经 [LocalNiBottomBarTone] 下发给子树，
 * 并把状态回调给调用方（底栏据此汇报自身矩形）。
 */
@Composable
fun NiBottomBarToneHost(
    systemDark: Boolean,
    content: @Composable (NiBottomBarToneState) -> Unit,
) {
    val state = rememberNiBottomBarToneState(systemDark)
    val darkness = state.darkness(systemDark)
    CompositionLocalProvider(LocalNiBottomBarTone provides darkness) {
        content(state)
    }
}

/** 对窗口做一次 PixelCopy，返回**线性光**平均亮度；任何失败都退化为 null。 */
private suspend fun NiBottomBarToneState.capture(window: Window, rect: Rect): Float? =
    suspendCancellableCoroutine { cont ->
        if (released) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val bmp = ensureBitmap()
        // ⚠ 用 suspendCancellableCoroutine：外层套了 withTimeoutOrNull，超时会取消；
        //   若回调再来 resume 一次会抛 IllegalStateException。
        // ⚠ 窗口还没有 backing surface 时（首帧/窗口重建/刚从后台回），PixelCopy.request 会
        //   **同步抛**（IAE/ISE/SecurityException，OEM 各异）。采样只是锦上添花，任何失败都要退化。
        val launched = try {
            PixelCopy.request(
                window,
                rect,
                bmp,
                { result ->
                    if (cont.isActive && !bmp.isRecycled) {
                        cont.resume(
                            if (result == PixelCopy.SUCCESS) {
                                if (bmp.isRecycled || released) null else meanLinearLuminance(bmp)
                            } else {
                                null
                            }
                        )
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            true
        } catch (t: Throwable) {
            // CancellationException 必须放行，否则破坏协程取消语义。
            if (t is kotlinx.coroutines.CancellationException) throw t
            false
        }
        if (!launched) cont.resume(null)
    }

/** sRGB 像素 → 线性光 → 相对亮度，取均值。必须在线性光空间算，否则暗部会被压缩。 */
private fun meanLinearLuminance(bmp: Bitmap): Float {
    val n = SAMPLE_W * SAMPLE_H
    val px = IntArray(n)
    bmp.getPixels(px, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
    var sum = 0f
    for (i in 0 until n) {
        val c = px[i]
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        sum += 0.2126f * srgbToLinear(r) + 0.7152f * srgbToLinear(g) + 0.0722f * srgbToLinear(b)
    }
    return sum / n
}

private fun srgbToLinear(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

/** 线性光相对亮度 Y → CIELAB L*（感知亮度，D65 白点，0–100）。分段点在 216/24389 处连续。 */
private fun yToLstar(y: Float): Float {
    val yn = y.coerceIn(0f, 1f)
    val eps = 216f / 24389f
    return if (yn > eps) {
        116f * yn.pow(1f / 3f) - 16f
    } else {
        116f * 841f / 108f * yn
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx != null) {
        when (ctx) {
            is Activity -> return ctx
            is ContextWrapper -> ctx = ctx.baseContext
            else -> return null
        }
    }
    return null
}
