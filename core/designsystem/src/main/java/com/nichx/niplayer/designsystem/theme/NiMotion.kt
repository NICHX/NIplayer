package com.nichx.niplayer.designsystem.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * 动效令牌。
 */
object NiMotion {
    val Emphasized: Easing = FastOutSlowInEasing

    const val DURATION_MICRO = 150
    const val DURATION_SWITCH = 220
    const val DURATION_PAGE = 300
}