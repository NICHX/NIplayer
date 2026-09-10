package com.nichx.niplayer.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

private const val PAGE_TRANSITION_MS = 300

// 视频播放器已迁移为独立 Activity（PlayerActivity），其退出转场（黑色亮度蒙层）已交由
// 该 Activity 的窗口过渡处理。导航内仅剩音频播放器（AUDIO_PLAYER），仍走纯 fade 过渡。
private const val FromPlayerTransitionMs = 750
// 蒙层缓动：ease-in 型（起点慢），开头多保持暗色、缓缓揭示，比 tween 默认的
// 快速启动缓动观感更舒缓，不会"唰"地一下变亮
private val ExitMaskEasing = CubicBezierEasing(0.45f, 0f, 0.8f, 1f)
// 返回页(首页)淡入起点：从很暗透明度起步，配合播放器黑底淡出形成连续的亮度渐变；
// 若取 0 会在播放器淡出末期先暴露白色 window 底
private const val ReturnFadeInInitialAlpha = 0.25f

// 播放器退出单独用纯 fade 过渡：SurfaceView 是独立 layer 不随 Compose 淡出，
// slide 的位移会让控件层与视频画面不同步；去掉位移仅 fade，避免放大退出时的不同步观感。
// 仅影响播放器路由，其余页面保持 fade+slide
private fun fromPlayer(entry: androidx.navigation.NavBackStackEntry?): Boolean {
    return isPlayerRoute(entry?.destination?.route)
}

private fun isPlayerRoute(route: String?): Boolean {
    return route == Routes.Player.AUDIO_PLAYER
}

@Composable
fun NiNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Home.ROOT,
    builder: NavGraphBuilder.(NavHostController) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(PAGE_TRANSITION_MS)) +
                slideInHorizontally(
                    animationSpec = tween(PAGE_TRANSITION_MS),
                    initialOffsetX = { it / 4 },
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(PAGE_TRANSITION_MS))
        },
        popEnterTransition = {
            if (fromPlayer(initialState)) {
                // 从播放器返回（首页/列表进入）：纯 fade，无位移。
                // initialAlpha 很暗起步 + 更长的时长，让亮度从播放器黑底平滑归位
                fadeIn(
                    animationSpec = tween(FromPlayerTransitionMs),
                    initialAlpha = ReturnFadeInInitialAlpha,
                )
            } else {
                fadeIn(tween(PAGE_TRANSITION_MS)) +
                    slideInHorizontally(
                        animationSpec = tween(PAGE_TRANSITION_MS),
                        initialOffsetX = { -it / 4 },
                    )
            }
        },
        popExitTransition = {
            if (fromPlayer(initialState)) {
                // 播放器退出：纯 fade 且与 popEnter 同长同步，黑蒙层贯穿渐隐揭首页
                fadeOut(tween(FromPlayerTransitionMs))
            } else {
                fadeOut(tween(PAGE_TRANSITION_MS)) +
                    slideOutHorizontally(
                        animationSpec = tween(PAGE_TRANSITION_MS),
                        targetOffsetX = { it / 4 },
                    )
            }
        },
        // 系统返回手势（predictive back）统一为滑入/滑出，避免默认 scaleOut(0.7) 缩放
        predictivePopEnterTransition = {
            if (fromPlayer(initialState)) {
                fadeIn(
                    animationSpec = tween(FromPlayerTransitionMs),
                    initialAlpha = ReturnFadeInInitialAlpha,
                )
            } else {
                fadeIn(tween(PAGE_TRANSITION_MS)) +
                    slideInHorizontally(
                        animationSpec = tween(PAGE_TRANSITION_MS),
                        initialOffsetX = { -it / 4 },
                    )
            }
        },
        predictivePopExitTransition = {
            if (fromPlayer(initialState)) {
                fadeOut(tween(FromPlayerTransitionMs))
            } else {
                fadeOut(tween(PAGE_TRANSITION_MS)) +
                    slideOutHorizontally(
                        animationSpec = tween(PAGE_TRANSITION_MS),
                        targetOffsetX = { it / 4 },
                    )
            }
        },
        builder = { builder(navController) },
    )
}
