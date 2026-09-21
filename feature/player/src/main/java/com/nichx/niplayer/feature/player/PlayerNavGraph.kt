package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.nichx.niplayer.navigation.Routes

/**
 * :feature:player 的导航图（**A2 架构修复：feature 聚合层**）。
 *
 * 原先音频播放页的路由注册写在 `:app` 的 `MainActivity` 里（含 4 组转场配置），
 * 使宿主必须 import `AudioPlayerScreen`。现由本模块自注册。
 *
 * @param navController 宿主导航控制器
 * @param audioPlaybackManager 全局音频播放管理器（音乐条 / 播放页共享同一实例）
 * @param onOpenEqualizer 打开均衡器设置页。该页位于 :feature:home（本模块不依赖它），
 *   故由宿主注入跳转动作
 */
fun NavGraphBuilder.playerNavGraph(
    navController: NavHostController,
    audioPlaybackManager: AudioPlaybackManager,
    onOpenEqualizer: () -> Unit,
) {
    composable(
        route = Routes.Player.AUDIO_PLAYER,
        enterTransition = {
            slideInVertically(tween(350)) { it } + fadeIn(tween(350))
        },
        exitTransition = {
            slideOutVertically(tween(350)) { it } + fadeOut(tween(350))
        },
        popEnterTransition = { fadeIn(tween(0)) },
        popExitTransition = { fadeOut(tween(0)) },
    ) {
        AudioPlayerScreen(
            onBack = { navController.popBackStack() },
            onEqualizer = onOpenEqualizer,
            audioPlaybackManager = audioPlaybackManager,
        )
    }
}
