package com.nichx.niplayer.player.kernel

/**
 * 播放器状态机。
 *
 * 转移关系：
 *   Idle → Buffering → Ready → Playing ↔ Paused → Ended
 *   任意状态 → Error（播放异常）
 *
 * 重新调用 [NxPlayer.setSource] 后内核应回到 Buffering。
 */
sealed class PlaybackState {

    /** 初始状态或已 release。 */
    data object Idle : PlaybackState()

    /** 缓冲中（首次或 seek 后）。 */
    data object Buffering : PlaybackState()

    /** 已就绪（首次缓冲完成），等待 [NxPlayer.play]。 */
    data object Ready : PlaybackState()

    /** 正在播放。 */
    data object Playing : PlaybackState()

    /** 已暂停。 */
    data object Paused : PlaybackState()

    /** 播放结束。 */
    data object Ended : PlaybackState()

    /** 错误。 */
    data class Error(val cause: Throwable) : PlaybackState()
}
