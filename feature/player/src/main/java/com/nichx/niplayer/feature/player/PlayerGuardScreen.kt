package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.navigation.Routes

/**
 * 播放路由守卫（无 UI 网关）。
 *
 * 替代旧仓库 `PlayerInterceptorActivity`：旧仓库用透明 Activity 读取
 * `VideoSourceManager` 单例后按文件扩展名分流到 PlayerActivity / AudioPlayerActivity，
 * 新仓库改为 Compose 路由——[PlayerGuardViewModel] peek [com.nichx.niplayer
 * .player.kernel.PlaybackRequestHolder] 判断 [GuardTarget]，本 Composable
 * 执行一次性导航后由 [onConsumed] 回调将自身弹出回退栈。
 *
 * 生命周期：仅存在一帧（LaunchedEffect 立即导航），用户不可见。
 *
 * @param onNavigate 目标路由回调，MainActivity 用 `popUpTo(GUARD) { inclusive = true }` 替换守卫
 * @param onBack 无播放请求时返回上一页
 */
@Composable
fun PlayerGuardScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PlayerGuardViewModel = hiltViewModel(),
) {
    // M-29 修复：改用 collectAsStateWithLifecycle，与 PlayerScreen 习惯一致。
    // 守卫 Composable 在 NavHost 转场期间（如目标页 push 过程中）应停止订阅，
    // 避免后台期间持续收集 target Flow 浪费资源。
    val target by viewModel.target.collectAsStateWithLifecycle()

    LaunchedEffect(target) {
        when (target) {
            GuardTarget.Video -> onNavigate(Routes.Player.PLAYER)
            GuardTarget.Audio -> onNavigate(Routes.Player.AUDIO_PLAYER)
            GuardTarget.None -> onBack()
        }
    }

    // 守卫无 UI，仅占位防止白屏闪烁
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text("", style = MaterialTheme.typography.bodySmall)
    }
}
