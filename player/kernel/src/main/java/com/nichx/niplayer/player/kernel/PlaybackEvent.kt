package com.nichx.niplayer.player.kernel

/**
 * 一次性播放事件（通过 [NxPlayer.events] SharedFlow 暴露）。
 *
 * 与 [PlaybackState] 区分：状态是"当前是什么"，事件是"刚发生了什么"。
 * 适合 Toast / 日志 / UI 一次性动画（如首帧渲染）。
 */
sealed class PlaybackEvent {

    /**
     * **新装载的媒体源**首帧渲染开始。
     *
     * 仅在 [NxPlayer.setSource] 装载新源后的首次渲染发射一次。seek 引起的画面重渲染
     * **不会**再次发射：media3 每次 seek 都会经 `MediaCodecRenderer.onPositionReset()`
     * 重置首帧状态并再次回调 `onRenderedFirstFrame()`，若原样透传，监听方会把 seek
     * 误判成换源而重跑首帧逻辑。
     */
    data object RenderingStart : PlaybackEvent()

    /** 视频尺寸变化。同步也会更新 [NxPlayer.videoSize] StateFlow。 */
    data class VideoSizeChanged(val size: VideoSize) : PlaybackEvent()

    /** 播放错误。同步也会将状态切到 [PlaybackState.Error]。 */
    data class Error(val cause: Throwable) : PlaybackEvent()

    /** 检测到 HDR 视频格式（首帧渲染后触发一次）。 */
    data class HdrDetected(val hdrType: String) : PlaybackEvent()
}
