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

/**
 * 页面转场（push / pop）——**无重叠版**。
 *
 * 约束来自界面本身：本应用大量使用**半透明**表面（液态玻璃、自定义背景图透出等），
 * 只要过渡中途两页**同时出现在屏幕上且相互叠加**，透过上层就能看到下层 ⇒ 发花、发糊。
 * 因此这里**不允许任何形式的页面重叠**：
 *
 * - **同速同向「传送带」滑移**：新页自右侧整屏滑入、旧页同时**整屏**滑出到左侧，两页位移量
 *   与缓动完全一致。设屏宽 W、进度 p：旧页占 [−Wp, W−Wp]、新页占 [W−Wp, 2W−Wp]，屏幕
 *   [0, W] 恰好由「旧页右半 + 新页左半」首尾相接铺满 —— **既不重叠、也不露背景**；
 * - **无淡入淡出**：任一页的整页 alpha 过渡都会让它在半透明界面里与背后内容混色（这正是
 *   旧版「重影」的来源），故页面级一律不做 fade；
 * - **无缩放**：缩放会破坏上面那条「首尾相接」的几何（缩小的一侧会与相邻页之间裂开缝），
 *   所以退场页也不缩。
 *
 * 方向语义与导航一致：前进 = 整条内容带向左移动（新页自右入），返回 = 整条向右移动。
 *
 * 视频播放器已迁移为独立 Activity（PlayerActivity），其退出转场（黑色亮度蒙层）交由该
 * Activity 的窗口过渡处理。导航内仅剩音频播放器（AUDIO_PLAYER），它与黑底播放器的**亮度
 * 连续性**是特例，仍单独走纯 fade，不套上面这套位移。
 */

// 纯 fade 的播放器衔接（亮度连续性，非运动），沿用旧口径。
private const val FromPlayerTransitionMs = MotionTokens.SCENE
private const val ReturnFadeInInitialAlpha = 0.25f

private fun isPlayerRoute(route: String?): Boolean = route == Routes.Player.AUDIO_PLAYER

private fun fromPlayer(entry: NavBackStackEntry?): Boolean = isPlayerRoute(entry?.destination?.route)

/** 前进进场：新页自右侧**整屏**滑入。 */
private fun forwardEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
        initialOffsetX = { it },
    )

/** 前进退场：旧页**整屏**滑出到左侧（与新页同速同向，首尾相接、不重叠）。 */
private fun forwardExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
        targetOffsetX = { -it },
    )

/** 返回进场：上一页自左侧**整屏**滑回。 */
private fun backwardEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
        initialOffsetX = { -it },
    )

/** 返回退场：当前页**整屏**滑出到右侧（与上一页同速同向，首尾相接、不重叠）。 */
private fun backwardExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
        targetOffsetX = { it },
    )

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
