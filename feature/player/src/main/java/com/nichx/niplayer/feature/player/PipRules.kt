package com.nichx.niplayer.feature.player

import android.util.Rational
import androidx.lifecycle.Lifecycle
import com.nichx.niplayer.player.kernel.VideoSize

/**
 * PiP（画中画）规则集合：宽高比换算 + 退出判定。
 *
 * 抽成纯逻辑集中于此，便于单测钉住（见 [PipRulesTest]）—— 这两条规则都曾因「凭直觉写」
 * 出过问题，且失败形态都很隐蔽：
 * 1. **宽高比**：超出系统区间会抛异常，被调用方 try/catch 吞掉 → 表现为「点 PiP 按钮毫无反应」；
 * 2. **退出判定**：用 onStart 判断「已回到前台」→ 展开回全屏被误判成关闭 → 播放器被 finish。
 */

/** 系统允许的 PiP 宽高比下界（超出则 enterPictureInPictureMode 抛 IllegalArgumentException）。 */
internal const val MIN_PIP_ASPECT = 0.418410f

/** 系统允许的 PiP 宽高比上界。 */
internal const val MAX_PIP_ASPECT = 2.390000f

/** 宽高比不可用（尺寸为 0×0）时的兜底值。 */
private const val FALLBACK_PIP_ASPECT = 16f / 9f

/**
 * [Rational] 的分母：千分之一精度，误差 < 0.1%，小窗尺寸上肉眼不可见。
 *
 * 抽成常量是为了让单测能脱离 `android.util.Rational`（单测 classpath 上的 Android 桩类
 * 不暴露继承自 `java.lang.Number` 的方法，如 `floatValue()`），直接对分子做断言。
 */
internal const val PIP_ASPECT_DENOMINATOR = 1000

/**
 * 把视频显示宽高比换算成系统可接受的 PiP 宽高比。
 *
 * - 取 [VideoSize.aspectRatio]（已折算像素宽高比与未应用旋转），**不用**裸的 width/height：
 *   竖拍视频带 90° 旋转标记时，裸宽高比会把小窗比例搞反。
 * - 钳制到 [[MIN_PIP_ASPECT], [MAX_PIP_ASPECT]]。系统原文：
 *   `IllegalArgumentException: enterPictureInPictureMode: Aspect ratio is too extreme
 *   (must be between 0.418410 and 2.390000)`。该异常被 [PlayerActivity.enterPip] 的
 *   try/catch 吞掉，用户侧表现是「点 PiP 按钮毫无反应」。
 *   常见触发场景：2.40:1 影视（1920×800 / 3840×1600）与智能去黑边后的有效尺寸。
 */
internal fun pipAspectRatio(size: VideoSize): Rational =
    Rational(pipAspectRatioNumerator(size), PIP_ASPECT_DENOMINATOR)

/**
 * 量化后的宽高比分子（分母恒为 [PIP_ASPECT_DENOMINATOR]），**保证比值落在系统区间内**。
 *
 * 纯数值、不碰 Android 类型，单测直接断言本函数（见 [PipRulesTest]）。
 */
internal fun pipAspectRatioNumerator(size: VideoSize): Int {
    val scale = PIP_ASPECT_DENOMINATOR.toFloat()
    val ratio = size.aspectRatio.takeIf { it > 0f } ?: FALLBACK_PIP_ASPECT
    var numerator = (ratio.coerceIn(MIN_PIP_ASPECT, MAX_PIP_ASPECT) * scale).toInt()
    // 截断（等价向下取整）后可能掉出下界（如 0.418410 → 0.418），逐位校正回区间内。
    // 上界方向截断天然安全（只会更小），但一并校正以保证「换算结果必定合法」这一契约。
    if (numerator / scale < MIN_PIP_ASPECT) numerator++
    if (numerator / scale > MAX_PIP_ASPECT) numerator--
    return numerator
}

/**
 * PiP 退出判定策略。
 *
 * 背景：**进入 PiP 时 Activity 只被置为 PAUSED** —— 小窗仍可见，不会 onStop；
 * 因此「展开回全屏」只走 onResume、**永远不走 onStart**。任何以 onStart 为「已回到前台」
 * 判据的逻辑都会把「展开」误判成「关闭」（历史缺陷：展开后 250ms 播放器被 finish）。
 * 正确判据是**有没有走 onStop**：关闭小窗会先 onStop 再回调 onPictureInPictureModeChanged。
 */
internal object PipExitPolicy {

    /**
     * 关闭小窗时 Activity 已不可见（onStop 通常先于 onPictureInPictureModeChanged 到达），
     * 据此在 PiP 退出回调里补判一次。
     */
    fun activityIsInvisible(state: Lifecycle.State): Boolean = state < Lifecycle.State.STARTED

    /**
     * onStop 中判定「是否因关闭小窗而不可见」。
     *
     * @param inPipSession 处于 PiP 会话（进入 PiP 置位，展开回全屏复位）
     * @param isInPictureInPictureMode 是否仍在 PiP 模式。**PiP 中息屏同样会 onStop**，
     *        但小窗并未被关闭，不应结束播放，故要求该值为 false。
     */
    fun closedPipWindow(inPipSession: Boolean, isInPictureInPictureMode: Boolean): Boolean =
        inPipSession && !isInPictureInPictureMode
}
