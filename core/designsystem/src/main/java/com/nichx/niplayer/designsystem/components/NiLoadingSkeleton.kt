package com.nichx.niplayer.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SkeletonPulseDurationMs = 900
private const val SkeletonAlphaMin = 0.3f
private const val SkeletonAlphaMax = 0.6f

/**
 * 骨架屏共享脉冲进度。
 *
 * 由 [NiSkeletonPulse] 在骨架容器处创建**一次**，子块从中读取；避免每个骨架块各自
 * 持有 `rememberInfiniteTransition`（一屏几十个无限动画时钟）。
 */
val LocalSkeletonAlpha = compositionLocalOf<State<Float>?> { null }

/**
 * 骨架屏脉冲宿主：为整棵子树提供同一个无限动画时钟。
 *
 * 子块在**绘制阶段**读取 alpha（[Modifier.drawBehind]），不在组合期读取，
 * 因此脉冲只触发重绘、不会逐帧重组。
 */
@Composable
fun NiSkeletonPulse(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha = transition.animateFloat(
        initialValue = SkeletonAlphaMin,
        targetValue = SkeletonAlphaMax,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SkeletonPulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    CompositionLocalProvider(LocalSkeletonAlpha provides alpha) { content() }
}

/** 取共享脉冲；容器未提供 [NiSkeletonPulse] 时退化为自持一个时钟（仍走绘制期读取）。 */
@Composable
private fun skeletonAlpha(): State<Float> {
    LocalSkeletonAlpha.current?.let { return it }
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = SkeletonAlphaMin,
        targetValue = SkeletonAlphaMax,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SkeletonPulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
}

@Composable
fun NiSkeletonBox(
    width: Dp? = null,
    height: Dp = 16.dp,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    modifier: Modifier = Modifier,
) {
    val alpha = skeletonAlpha()
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .drawBehind {
                drawRoundRect(
                    color = baseColor,
                    alpha = alpha.value,
                    cornerRadius = CornerRadius(shape.topStart.toPx(size, this)),
                )
            },
    )
}

@Composable
fun NiSkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    val alpha = skeletonAlpha()
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val corner = 4.dp
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .drawBehind {
                drawRoundRect(
                    color = baseColor,
                    alpha = alpha.value,
                    cornerRadius = CornerRadius(corner.toPx()),
                )
            },
    )
}
