package com.nichx.niplayer.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * 线性循环相位（0..1，按真实流逝时间推进）。
 *
 * **只在 [enabled] 时推进**：关掉后循环直接退出、不再请求帧，相位停在原处 ——
 * 所以调用方可以把相位乘一个平滑衰减的系数，让动画「缓缓停住」而不是「啪」一下归零。
 *
 * 相位由调用方在**绘制阶段**读（`graphicsLayer` / `Canvas` 的 draw 里），
 * 因此每帧只重画那一层，不触发重组。
 *
 * @param periodMs 走完一整圈（0→1）所需的毫秒数
 */
@Composable
internal fun rememberLoopPhase(enabled: Boolean, periodMs: Int, label: String): State<Float> {
    val phase = remember(label) { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, periodMs, label) {
        if (!enabled) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            val nowNanos = withFrameNanos { it }
            if (lastNanos != 0L) {
                phase.floatValue =
                    (phase.floatValue + (nowNanos - lastNanos) / (periodMs * 1_000_000f)) % 1f
            }
            lastNanos = nowNanos
        }
    }
    return phase
}
