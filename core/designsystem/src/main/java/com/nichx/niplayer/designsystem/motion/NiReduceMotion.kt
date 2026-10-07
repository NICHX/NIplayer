package com.nichx.niplayer.designsystem.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.nichx.niplayer.designsystem.theme.MotionTokens

/**
 * 是否开启"减少动态效果"。
 *
 * 由 App 根部（应用内设置 + 系统 animator_duration_scale）计算后下发；默认关闭。
 * 为 true 时，所有原语直接跳到终态、不做任何过渡。
 */
val LocalNiReduceMotion = staticCompositionLocalOf { false }

/**
 * 尊重"减少动态效果"的 tween：开启时返回 0ms（直接到终态）。
 */
@Composable
fun niTween(
    durationMillis: Int,
    easing: Easing = MotionTokens.easeStandard,
): TweenSpec<Float> {
    val reduced = LocalNiReduceMotion.current
    return remember(durationMillis, easing, reduced) {
        tween(durationMillis = if (reduced) 0 else durationMillis, easing = easing)
    }
}
