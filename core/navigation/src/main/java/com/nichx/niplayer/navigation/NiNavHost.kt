package com.nichx.niplayer.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import com.nichx.niplayer.designsystem.theme.MotionTokens

// 页面转场时长走令牌（PAGE_ENTER / PAGE_EXIT）。
private const val PAGE_TRANSITION_MS = MotionTokens.PAGE_ENTER
private const val PAGE_EXIT_MS = MotionTokens.PAGE_EXIT

// 进入页整体自侧边滑入（**全宽**位移，`it`）；退出页只让出 1/4 宽（`it / 4`）做视差。
// 这是 iOS push 手感的关键：新旧页位移量不等（全宽 : 1/4），层次立刻拉开；
// 之前两页都只位移 1/4、量相等，看起来就是「平平地平移」。
private const val PARALLAX_FRACTION = 4

// 视频播放器已迁移为独立 Activity（PlayerActivity），其退出转场（黑色亮度蒙层）已交由
// 该 Activity 的窗口过渡处理。导航内仅剩音频播放器（AUDIO_PLAYER），仍走纯 fade 过渡。
private const val FromPlayerTransitionMs = MotionTokens.SCENE
// 返回页(首页)淡入起点：从很暗透明度起步，配合播放器黑底淡出形成连续的亮度渐变；
// 若取 0 会在播放器淡出末期先暴露白色 window 底
private const val ReturnFadeInInitialAlpha = 0.25f

// 播放器退出单独用纯 fade 过渡：SurfaceView 是独立 layer 不随 Compose 淡出，
// slide 的位移会让控件层与视频画面不同步；去掉位移仅 fade，避免放大退出时的不同步观感。
// 仅影响播放器路由，其余页面保持 fade+slide
private fun fromPlayer(entry: NavBackStackEntry?): Boolean {
    return isPlayerRoute(entry?.destination?.route)
}

private fun isPlayerRoute(route: String?): Boolean {
    return route == Routes.Player.AUDIO_PLAYER
}

/** 前进进场：新页自右侧**整屏**滑入并淡入。 */
private fun forwardEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PAGE_TRANSITION_MS, easing = MotionTokens.easeStandard),
        initialOffsetX = { it },
    ) + fadeIn(tween(PAGE_TRANSITION_MS, easing = MotionTokens.easeStandard))

/** 前进退场：旧页淡出并只向左让出 1/4 宽（视差，不滑出屏幕）。 */
private fun forwardExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(PAGE_EXIT_MS, easing = MotionTokens.easeExit),
        targetOffsetX = { -it / PARALLAX_FRACTION },
    ) + fadeOut(tween(PAGE_EXIT_MS, easing = MotionTokens.easeExit))

/** 返回进场：上一页自左侧 1/4 宽处滑回并淡入（与前进视差镜像）。 */
private fun backwardEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PAGE_TRANSITION_MS, easing = MotionTokens.easeStandard),
        initialOffsetX = { -it / PARALLAX_FRACTION },
    ) + fadeIn(tween(PAGE_TRANSITION_MS, easing = MotionTokens.easeStandard))

/** 返回退场：当前页淡出并整屏向右滑出。 */
private fun backwardExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(PAGE_EXIT_MS, easing = MotionTokens.easeExit),
        targetOffsetX = { it },
    ) + fadeOut(tween(PAGE_EXIT_MS, easing = MotionTokens.easeExit))

/** 从播放器返回/退出：纯 fade，`initialAlpha` 从很暗起步，与播放器黑底衔接。 */
private fun fromPlayerEnter(): EnterTransition =
    fadeIn(animationSpec = tween(FromPlayerTransitionMs), initialAlpha = ReturnFadeInInitialAlpha)

private fun fromPlayerExit(): ExitTransition =
    fadeOut(tween(FromPlayerTransitionMs))

@Composable
fun NiNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Home.ROOT,
    builder: NavGraphBuilder.(NavHostController) -> Unit = {},
) {
    // 「减少动态效果」开启时，页面转场不再位移；纯 fade 的播放器衔接保留
    // （它是亮度连续性的过渡，不是运动）。
    val reduceMotion = LocalNiReduceMotion.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (reduceMotion) EnterTransition.None else forwardEnter()
        },
        exitTransition = {
            if (reduceMotion) ExitTransition.None else forwardExit()
        },
        popEnterTransition = {
            when {
                fromPlayer(initialState) -> fromPlayerEnter()
                reduceMotion -> EnterTransition.None
                else -> backwardEnter()
            }
        },
        popExitTransition = {
            when {
                // 播放器退出：纯 fade 且与 popEnter 同长同步，黑蒙层贯穿渐隐揭首页
                fromPlayer(initialState) -> fromPlayerExit()
                reduceMotion -> ExitTransition.None
                else -> backwardExit()
            }
        },
        // 系统返回手势（predictive back）与 pop 保持一致，避免默认 scaleOut(0.7) 缩放
        predictivePopEnterTransition = {
            when {
                fromPlayer(initialState) -> fromPlayerEnter()
                reduceMotion -> EnterTransition.None
                else -> backwardEnter()
            }
        },
        predictivePopExitTransition = {
            when {
                fromPlayer(initialState) -> fromPlayerExit()
                reduceMotion -> ExitTransition.None
                else -> backwardExit()
            }
        },
        builder = { builder(navController) },
    )
}
