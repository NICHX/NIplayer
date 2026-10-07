package com.nichx.niplayer.designsystem.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nichx.niplayer.designsystem.theme.MotionTokens

/**
 * 正确的 AnimatedVisibility 封装。
 *
 * 必须从 [MutableTransitionState] 的 `false` 起步再置 targetState：
 * 直接写 `AnimatedVisibility(visible = true)` 在节点**首次组合**时会把过渡置为已完成，
 * 一帧进入动画都不播（浮层/面板/抽屉常见的"啪"出现）。
 *
 * 开启"减少动态效果"时改为无过渡的瞬时进出。
 */
@Composable
fun NiAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(tween(MotionTokens.ENTER, easing = MotionTokens.easeEnter)),
    exit: ExitTransition = fadeOut(tween(MotionTokens.EXIT, easing = MotionTokens.easeExit)),
    label: String = "NiAnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    if (LocalNiReduceMotion.current) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            label = label,
            content = content,
        )
        return
    }

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = enter,
        exit = exit,
        label = label,
        content = content,
    )
}
