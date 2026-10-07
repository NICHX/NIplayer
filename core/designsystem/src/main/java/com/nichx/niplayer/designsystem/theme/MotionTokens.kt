package com.nichx.niplayer.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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

    const val SHIFT_XS = 4f
    const val SHIFT_S = 8f
    const val SHIFT_M = 16f

    const val SCALE_IN_MIN = 0.86f
    const val SCALE_IN_MAX = 0.94f
    const val SCALE_PRESS_MIN = 0.96f
    const val SCALE_PRESS_MAX = 0.98f

    /** 退场时长统一 ≈ 出场 × 0.65，避免各处手算。 */
    fun exitOf(enterMillis: Int): Int = (enterMillis * 0.65f).roundToInt()
}
