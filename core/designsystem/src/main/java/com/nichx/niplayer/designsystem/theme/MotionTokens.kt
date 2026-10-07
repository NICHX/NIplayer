package com.nichx.niplayer.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt

/**
 * 动效令牌（唯一来源）。
 *
 * 业务代码禁止写裸 ms / 裸 easing —— 一律引用这里的常量与曲线。
 * 核心规则：退场统一 ≈ 出场 × 0.65（[EXIT] = [ENTER] × 0.65）。
 */
object MotionTokens {

    const val INSTANT = 90
    const val QUICK = 150
    const val ENTER = 200
    const val EXIT = 130
    const val SURFACE = 320
    const val PAGE = 420
    const val SCENE = 750

    /**
     * 页面 push / pop 转场时长（ms）。
     *
     * 进入页整体自侧边滑入（全宽位移），退出页只让出 1/4 宽做视差——Apple/iOS 的
     * push 手感来源。转场走 tween（[easeStandard]）而非 spring：整页位移一旦过冲会越过
     * 屏幕边缘再弹回，观感是「撞了一下」而不是「灵动」，所以弹性只留给小尺度浮层。
     */
    const val PAGE_ENTER = 280
    const val PAGE_EXIT = 220

    val easeStandard: Easing = FastOutSlowInEasing
    val easeEnter: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)
    val easeExit: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)

    val springSoft: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessLow,
    )
    val springPress: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val springDrag: SpringSpec<Float> = spring(
        stiffness = Spring.StiffnessLow,
        visibilityThreshold = 0.001f,
    )

    // 弹性参数集中定义：Float 与非 Float（如 IntOffset 位移）过渡共用同一组参数，
    // 避免「同一条曲线在两处各写一半」而漂移。
    //
    // 浮层（弹窗 / 底部面板 / 抽屉）弹性口径与 Apple HIG 对齐：`bounce = 1 − ζ`。
    // 取 bounce≈0.14（ζ=0.86）——「就位时收一下」而不是来回弹的卡通感；刚度压到 420，
    // 使收尾读作一次回弹而非抖动（刚度越高过冲窗口越短，越像「顿一下」）。
    // 退场用更高阻尼 + 更高刚度：更快更稳地收掉，不做可见过冲。
    private const val PANEL_DAMPING = 0.86f
    private const val PANEL_STIFFNESS = 420f
    private const val PANEL_EXIT_DAMPING = 0.90f
    private const val PANEL_EXIT_STIFFNESS = 500f

    val springPanel: SpringSpec<Float> = panelSpring()
    val springPanelExit: SpringSpec<Float> = panelExitSpring()

    /** [springPanel] 的任意类型版本（如 `IntOffset` 位移过渡）。 */
    fun <T> panelSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(PANEL_DAMPING, PANEL_STIFFNESS, visibilityThreshold)

    /** [springPanelExit] 的任意类型版本。 */
    fun <T> panelExitSpring(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(PANEL_EXIT_DAMPING, PANEL_EXIT_STIFFNESS, visibilityThreshold)

    /**
     * 长距离滚动（歌词自动跟随）的弹簧。
     *
     * 比 Compose 滚动默认弹簧（StiffnessMedium≈1500，收尾约 0.16s）更慢更顺，
     * 读起来是「滑过去」而不是「跳过去」；阻尼接近临界，不产生可见过冲。
     */
    val springScroll: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)

    const val SHIFT_XS = 4f
    const val SHIFT_S = 8f
    const val SHIFT_M = 16f

    const val SCALE_IN_MIN = 0.86f
    const val SCALE_IN_MAX = 0.94f
    const val SCALE_PRESS_MIN = 0.96f
    const val SCALE_PRESS_MAX = 0.98f

    /** 浮层（弹窗 / 面板）进场缩放起点与退场缩放终点。 */
    const val SCALE_PANEL_IN = 0.96f
    const val SCALE_PANEL_OUT = 0.98f

    /** 锚定下拉菜单（自锚点那一角长出来）的缩放起止点。 */
    const val SCALE_MENU_IN = 0.86f
    const val SCALE_MENU_OUT = 0.94f

    /** 缩放起点：由中心均匀放大（弹窗等居中元素）。 */
    val OriginCenter = TransformOrigin.Center

    /** 缩放起点：由顶部中心向外长（自上方滑入的浮层）。 */
    val OriginTopCenter = TransformOrigin(0.5f, 0f)

    /** 缩放起点：由右上角向外长（锚定在下拉锚点的菜单）。 */
    val OriginTopEnd = TransformOrigin(1f, 0f)

    /** 缩放起点：由底部中心向外长（底部面板）。 */
    val OriginBottomCenter = TransformOrigin(0.5f, 1f)

    /** 缩放起点：由内容起始边（左）向外展开（抽屉 / 侧栏）。 */
    val OriginStartCenter = TransformOrigin(0f, 0.5f)

    /** 退场时长统一 ≈ 出场 × 0.65，避免各处手算。 */
    fun exitOf(enterMillis: Int): Int = (enterMillis * 0.65f).roundToInt()
}
