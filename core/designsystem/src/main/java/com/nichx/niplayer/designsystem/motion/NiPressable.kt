package com.nichx.niplayer.designsystem.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.nichx.niplayer.designsystem.theme.MotionTokens

/**
 * 统一的按压反馈，替换各处散落的裸 `.clickable`。
 *
 * - 缩放只走 [graphicsLayer]（transform），不动 width/height；
 * - 非按压态不挂图层（避免恒等 graphicsLayer 白白建层）；
 * - 手势用 clickable/combinedClickable 实现，不消费滚动与拖动；
 * - 开启"减少动态效果"时不缩放；可选触感；`indicationEnabled = false` 可关闭涟漪只保留缩放。
 */
@Composable
fun Modifier.niPressable(
    enabled: Boolean = true,
    pressedScale: Float = MotionTokens.SCALE_PRESS_MIN,
    onClickLabel: String? = null,
    role: Role? = null,
    hapticFeedback: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    indicationEnabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = LocalNiReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) pressedScale else 1f,
        animationSpec = MotionTokens.springPress,
        label = "niPressScale",
    )

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(interactionSource, enabled, hapticFeedback) {
        interactionSource.interactions.collect { interaction ->
            if (enabled && hapticFeedback && interaction is PressInteraction.Press) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val resolvedIndication = if (indicationEnabled) indication ?: LocalIndication.current else null
    val scaled = if (scale != 1f) {
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    } else {
        Modifier
    }

    return this.then(scaled).then(
        if (onLongClick != null) {
            Modifier.combinedClickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onLongClick = onLongClick,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = resolvedIndication,
            )
        } else {
            Modifier.clickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = resolvedIndication,
            )
        },
    )
}
