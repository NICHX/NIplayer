package com.nichx.niplayer.player.kernel

/**
 * 当前播放媒体的技术信息。
 *
 * 由 [com.nichx.niplayer.player.kernel.media3.NxMedia3Player] 在 [androidx.media3.common.Player
 * .Listener.onTracksChanged] 时从 [androidx.media3.common.Tracks] 提取，用于「媒体信息」抽屉展示。
 *
 * @param videoCodec 视频编码，如 `video/avc`（sampleMimeType）或 `avc1.640028`（codecs）。
 * @param audioCodec 音频编码，如 `audio/mp4a-latm` 或 `mp4a.40.2`。
 * @param resolution 分辨率字符串，如 `1920×1080`。无视频轨时为 null。
 * @param bitrate 码率（bps）。Format 未声明时为 null。
 * @param frameRate 帧率（fps）。Format 未声明时为 null。
 * @param hdrType HDR 类型，如 `HDR10` / `HLG` / `Dolby Vision`。SDR 或未知时为 null。
 */
data class MediaInfo(
    val videoCodec: String?,
    val audioCodec: String?,
    val resolution: String?,
    val bitrate: Int?,
    val frameRate: Float?,
    val hdrType: String?,
)
