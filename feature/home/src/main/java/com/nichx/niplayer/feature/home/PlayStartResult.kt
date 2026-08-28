package com.nichx.niplayer.feature.home

/**
 * 播放启动结果。
 *
 * 由 [PlayStarter] 及内部委托的 [HistoryStartProvider] 共用，
 * ViewModel emit 导航 / 错误提示事件时依赖此类型。
 */
sealed class PlayStartResult {
    /** PlaybackRequest 已写入 Holder，ViewModel 应 emit 导航到播放页事件。 */
    object Success : PlayStartResult()

    /** 启动失败，ViewModel 应 emit 错误提示。 */
    data class Error(val message: String) : PlayStartResult()
}