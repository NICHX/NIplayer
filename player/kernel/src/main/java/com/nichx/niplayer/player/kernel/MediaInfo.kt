package com.nichx.niplayer.player.kernel

/**
 * 音轨角色（来自 [androidx.media3.common.C] 的 `ROLE_FLAG_*`）。
 *
 * 仅当 Format 明确声明了角色标志时才非 null；未声明时由 UI 省略该行。
 */
enum class AudioRole { MAIN, COMMENTARY, DUB, ALTERNATE }

/**
 * 当前播放媒体的技术信息。
 *
 * 由 [com.nichx.niplayer.player.kernel.media3.NxMedia3Player] 在 [androidx.media3.common.Player
 * .Listener.onTracksChanged] 时从 [androidx.media3.common.Tracks] 提取，用于「媒体信息」抽屉展示。
 *
 * 约定：除轨道数量外，字段在 Format 未声明时为 null，由 UI 决定是否展示（通常「有值才显示」）。
 *
 * 通用：
 * @param container 容器格式简称，如 `MKV` / `MP4`。
 * @param bitrate 视频与音频码率之和（bps）。
 *
 * 视频：
 * @param videoCodec 视频编码，如 `video/avc`（sampleMimeType）或 `avc1.640028`（codecs）。
 * @param resolution 分辨率字符串，如 `1920×1080`。
 * @param frameRate 帧率（fps）。
 * @param videoBitrate 视频码率（bps）。
 * @param videoBitDepth 视频位深（bit，如 8 / 10 / 12）。
 * @param colorSpace 色彩空间，如 `BT.709` / `BT.2020`。
 * @param colorRange 色彩范围标识：`limited` / `full`。
 * @param rotationDegrees 旋转角度（度）；0 表示无旋转。
 * @param pixelAspectRatio 像素宽高比；1.0 表示方形像素。
 * @param stereoMode 立体模式标识：`top_bottom` / `left_right` / `mesh` / `interleaved_left` / `interleaved_right`。
 * @param hdrType HDR 类型，如 `HDR10` / `HLG` / `Dolby Vision`；SDR 或未知时为 null。
 *
 * 音频：
 * @param audioCodec 音频编码，如 `audio/mp4a-latm` 或 `mp4a.40.2`。
 * @param audioChannels 音频声道数。
 * @param audioSampleRate 音频采样率（Hz）。
 * @param audioBitrate 音频码率（bps）。
 * @param audioAverageBitrate 音频平均码率（bps）；等于 [audioBitrate] 时为 null。
 * @param audioPeakBitrate 音频峰值码率（bps）。
 * @param audioPcmEncoding PCM 编码位宽描述，如 `16-bit` / `24-bit` / `Float`。
 * @param audioLanguage 音频语言（ISO 639 代码，如 `eng` / `chi`）。
 * @param audioRole 音轨角色。
 * @param audioSelectedByDefault 是否为默认音轨。
 *
 * 轨道统计：
 * @param videoTrackCount 视频轨道数量。
 * @param audioTrackCount 音频轨道数量。
 * @param textTrackCount 字幕轨道数量。
 */
data class MediaInfo(
    val container: String?,
    val bitrate: Int?,
    val videoCodec: String?,
    val resolution: String?,
    val frameRate: Float?,
    val videoBitrate: Int?,
    val videoBitDepth: Int?,
    val colorSpace: String?,
    val colorRange: String?,
    val rotationDegrees: Int?,
    val pixelAspectRatio: Float?,
    val stereoMode: String?,
    val hdrType: String?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val audioSampleRate: Int?,
    val audioBitrate: Int?,
    val audioAverageBitrate: Int?,
    val audioPeakBitrate: Int?,
    val audioPcmEncoding: String?,
    val audioLanguage: String?,
    val audioRole: AudioRole?,
    val audioSelectedByDefault: Boolean?,
    val videoTrackCount: Int,
    val audioTrackCount: Int,
    val textTrackCount: Int,
)
