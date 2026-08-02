package com.nichx.niplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.player.kernel.PlaybackRequestHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 播放路由守卫 ViewModel。
 *
 * 替代旧仓库 `PlayerInterceptorActivity`（透明无 UI 网关 Activity）。
 *
 * 职责：[peek]（不清空）[PlaybackRequestHolder] 中的 [com.nichx.niplayer.player.kernel.PlaybackRequest]，
 * 按 [com.nichx.niplayer.player.kernel.PlaybackRequest.isAudio] 决定分流目标，
 * 由 [PlayerGuardScreen] 执行 Compose Navigation 跳转。
 *
 * 与旧仓库的差异：
 * - **不消费请求**：仅 [PlaybackRequestHolder.peek]，真正的 [PlaybackRequestHolder.consume]
 *   由目标播放页（PlayerViewModel）执行，避免请求在守卫阶段被吞掉
 * - **无 Activity**：纯 Compose 路由，无透明 Activity 中转，无 `overridePendingTransition`
 * - **无音频播放列表**：旧仓库对组播放（groupSize > 1）构建 AudioPlayManager 播放列表，
 *   新仓库暂不支持组播放，单文件直接路由
 *
 * C-03 修复：原实现 `target` 在 VM 构造时 `peek()` 一次后转为只读 `asStateFlow()`，
 * 未保留 mutable 引用。Guard VM 构造时 PlaybackRequestHolder 可能尚未有请求
 * （如 deeplink 触发的播放请求、NavHost 回栈复用 Guard 节点等场景），导致 `target` 永远是 None。
 * 现保留 MutableStateFlow 引用，并启动协程轮询请求到达事件，更新 target。
 */
@HiltViewModel
class PlayerGuardViewModel @Inject constructor(
    private val playbackRequestHolder: PlaybackRequestHolder,
) : ViewModel() {

    /** 分流目标。[None] 表示无待播放请求（应返回上一页）。 */
    private val _target = MutableStateFlow(computeTarget(playbackRequestHolder.peek()))
    val target: StateFlow<GuardTarget> = _target.asStateFlow()

    init {
        // C-03 修复：如果构造时已 peek 到请求，无需轮询；
        // 否则启动轮询协程，等待请求到达后更新 target。
        // 超时 500ms 后放弃（保持原 None 状态，UI 会调用 onBack 退出），
        // 避免 deeplink 路由竞态下永久轮询。
        if (_target.value == GuardTarget.None) {
            viewModelScope.launch {
                val deadlineMs = System.currentTimeMillis() + POLL_TIMEOUT_MS
                while (_target.value == GuardTarget.None && System.currentTimeMillis() < deadlineMs) {
                    kotlinx.coroutines.delay(POLL_INTERVAL_MS)
                    val request = playbackRequestHolder.peek()
                    if (request != null) {
                        _target.value = computeTarget(request)
                    }
                }
            }
        }
    }

    /** 根据 [PlaybackRequest.isAudio] 计算分流目标。 */
    private fun computeTarget(request: com.nichx.niplayer.player.kernel.PlaybackRequest?): GuardTarget {
        return request?.let {
            if (it.isAudio) GuardTarget.Audio else GuardTarget.Video
        } ?: GuardTarget.None
    }

    private companion object {
        /** C-03 修复：轮询间隔（ms），等待 PlaybackRequest 到达。 */
        const val POLL_INTERVAL_MS = 50L

        /** C-03 修复：轮询超时（ms），超时后保持 None 状态由 UI 调 onBack 退出。 */
        const val POLL_TIMEOUT_MS = 500L
    }
}

/** 守卫分流目标。 */
sealed class GuardTarget {
    /** 视频文件 → [com.nichx.niplayer.navigation.Routes.Player.PLAYER]。 */
    object Video : GuardTarget()

    /** 音频文件 → [com.nichx.niplayer.navigation.Routes.Player.AUDIO_PLAYER]。 */
    object Audio : GuardTarget()

    /** 无播放请求（直接进入守卫路由），应返回上一页。 */
    object None : GuardTarget()
}
