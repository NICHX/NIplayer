package com.nichx.niplayer.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private const val PAGE_TRANSITION_MS = 300

// 从播放器返回的过渡时长：即"黑色平滑蒙层"从全黑渐隐揭开的时长（也控制双侧 fade）。
// 亮度已在退出瞬间（capturedBack doExit）恢复并被蒙层盖住，蒙层仅需抹平"播放器黑 -> 首页浅白"
// 的观感过渡。600ms 偏短、蒙层观感不明显，取 750ms 平衡干脆与平滑。
private const val FromPlayerTransitionMs = 750
// 蒙层缓动：ease-in 型（起点慢），开头多保持暗色、缓缓揭示，比 tween 默认的
// 快速启动缓动观感更舒缓，不会"唰"地一下变亮
private val ExitMaskEasing = CubicBezierEasing(0.45f, 0f, 0.8f, 1f)
// 返回页(首页)淡入起点：从很暗透明度起步，配合播放器黑底淡出形成连续的亮度渐变；
// 若取 0 会在播放器淡出末期先暴露白色 window 底
private const val ReturnFadeInInitialAlpha = 0.25f

// 播放器退出单独用纯 fade 过渡（v2.2.0 语义）：SurfaceView 是独立 layer 不随 Compose 淡出，
// slide 的位移会让控件层与视频画面不同步；去掉位移仅 fade，避免放大退出时的不同步观感。
// 仅影响播放器路由，其余页面保持 fade+slide
private fun fromPlayer(entry: androidx.navigation.NavBackStackEntry?): Boolean {
    return isPlayerRoute(entry?.destination?.route)
}

private fun isPlayerRoute(route: String?): Boolean {
    return route == Routes.Player.PLAYER || route == Routes.Player.AUDIO_PLAYER
}

@Composable
fun NiNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Home.ROOT,
    builder: NavGraphBuilder.(NavHostController) -> Unit = {},
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    // 从播放器返回时的全屏黑色亮度平滑蒙层：从全黑渐隐到透明，强制把"播放器黑 -> 首页
    // 浅白"的亮度跳变抹平成连续暗→亮渐变。不受 NavHost fade 起点/时序影响。
    val exitMask = remember { Animatable(0f) }
    // 记录上一个路由，检测"从播放器切回其它页"。不能用 previousBackStackEntry：
    // 播放器 pop 出去后已不在返回栈里，previous 会变成 null，永远捕获不到。
    var lastRoute by remember { mutableStateOf<String?>(null) }
    val route = currentEntry?.destination?.route
    LaunchedEffect(route, currentEntry?.id) {
        // 仅视频播放器退出时施加黑色亮度蒙层；音频播放器退出保留画面原样淡出，不遮罩
        if (lastRoute == Routes.Player.PLAYER && !isPlayerRoute(route)) {
            exitMask.snapTo(1f)
            // 慢启动缓动：蒙层在开头几乎仍是不透明黑，只缓缓揭开，避免一上来就"唰"地变亮
            exitMask.animateTo(
                0f,
                tween(FromPlayerTransitionMs, easing = ExitMaskEasing),
            )
        }
        lastRoute = route
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        // 播放器退出亮度平滑蒙层：过渡期全黑压暗画面，随 FromPlayerTransitionMs 渐隐，
        // 揭开底层页面，避免播放器黑底瞬间跳到首页浅底造成的刺眼亮点
        if (exitMask.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = exitMask.value)),
            )
        }
    }
}
