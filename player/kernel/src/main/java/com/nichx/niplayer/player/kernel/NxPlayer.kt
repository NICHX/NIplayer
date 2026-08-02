package com.nichx.niplayer.player.kernel

import android.view.Surface
import androidx.media3.common.text.Cue
import com.nichx.niplayer.player.kernel.audio.NiEqualizer
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 音频轨道信息，用于 UI 展示和选择。 */
data class AudioTrackInfo(
    val index: Int,
    val label: String,
    val language: String?,
)

/**
 * 字幕轨道信息。
 *
 * @param index 在 [NxPlayer.subtitleTracks] 列表中的索引
 * @param label 显示名称（语言 + 编码，如「中文 (srt)」）
 * @param language 语言代码（如「zh」），可空
 * @param isExternal 是否外挂字幕（保留字段，外挂字幕统一由
 *   [com.nichx.niplayer.subtitle.renderer.SubtitleEngine] 渲染，不进入此列表；
 *   此字段当前始终为 false，仅作向后兼容保留）
 * @param isAutoSelected 当用户选择「自动」模式（[NxPlayer.selectedSubtitleTrackIndex] == -1）时，
 *   media3 根据语言偏好自动选中的轨道此字段为 true。UI 层据此在「自动」行下方显示
 *   实际加载的字幕名称。仅一条字幕可能为 true；无字幕时全部为 false。
 */
data class SubtitleTrackInfo(
    val index: Int,
    val label: String,
    val language: String?,
    val isExternal: Boolean = false,
    val isAutoSelected: Boolean = false,
)

/**
 * 视频缩放模式。
 *
 * - [Fit]：保持宽高比，居中显示，可能留黑边（media3 SCALE_TO_FIT）
 * - [Crop]：保持宽高比，裁剪填满画面（media3 SCALE_TO_FIT_WITH_CROPPING）
 * - [Stretch]：拉伸填满画面，不保比（由 UI 层用全屏 SurfaceView 实现，
 *   media3 视频缩放模式不支持拉伸，必须通过 Surface 尺寸控制）
 * - [Ratio16_9]：强制 16:9 比例，忽略视频原始宽高比，适合老番/老剧填满现代屏幕
 */
enum class NxVideoScaleMode {
    Fit,
    Crop,
    Stretch,
    Ratio16_9,
}

/**
 * NIplayer 播放器内核抽象接口。
 *
 * 设计要点：
 * - **不继承 View**：UI 层（后续 PlayerScreen Composable）通过 [attachSurface] 挂载渲染表面。
 *   替代旧仓库 DanDanVideoPlayer 继承 FrameLayout 的耦合设计。
 * - **StateFlow 暴露状态**：[state] / [positionMs] / [durationMs] 等，避免回调地狱。
 * - **SharedFlow 暴露事件**：[events] 用于一次性 UI 反馈（Toast / 首帧渲染动画等）。
 * - **单一 media3 内核**：本接口仅由 [com.nichx.niplayer.player.kernel.media3.NxMedia3Player] 实现，
 *   无工厂模式（旧仓库 PlayerFactory 三套实现已废弃）。
 *
 * 生命周期：调用方（PlayerViewModel）在销毁前必须调用 [release]，
 * 否则会泄漏 ExoPlayer 实例。
 */
interface NxPlayer {

    /** 当前播放状态。新订阅者立即收到当前值。 */
    val state: StateFlow<PlaybackState>

    /** 当前播放位置（ms），由内核按 ~500ms 频率更新。 */
    val positionMs: StateFlow<Long>

    /** 已缓冲位置（ms）。 */
    val bufferedMs: StateFlow<Long>

    /** 总时长（ms）。未准备好时为 0。 */
    val durationMs: StateFlow<Long>

    /** 当前视频尺寸。 */
    val videoSize: StateFlow<VideoSize>

    /** 当前播放媒体技术信息（编码/分辨率/码率/帧率/HDR）。未准备好时为 null。 */
    val mediaInfo: StateFlow<MediaInfo?>

    /** 当前字幕渲染数据（[Cue] 列表），由内核 textRenderer 输出。空列表表示无字幕。 */
    val cues: StateFlow<List<Cue>>

    /** 一次性事件流（错误、首帧渲染、视频尺寸变化等）。 */
    val events: SharedFlow<PlaybackEvent>

    /**
     * 当前播放速度（m-03 修复）。
     *
     * 由 [setSpeed] 写入，并由 [androidx.media3.common.Player.Listener.onPlaybackParametersChanged]
     * 同步——media3 内部某些场景（如系统音频焦点切换、某些 codec 限制）可能改写速度，
     * 监听回调保证 UI 永远看到 media3 实际生效的值。
     *
     * 1.0f = 正常速度。
     */
    val playbackSpeed: StateFlow<Float>

    /** 当前网络下载速度（B/s），通过 TransferListener 统计。本地文件为 0。 */
    val networkSpeed: StateFlow<Long>

    /**
     * 设置媒体源。不立即播放，等待 [prepare]。
     *
     * W-M8 修复：新增 [startPositionMs] 参数，直接传给 media3 setMediaSource，
     * 避免续播场景下先从 0 开始 buffer 再被外层 seekTo 中断导致的无效 Range 请求。
     * 传 0 表示从头播放。
     */
    fun setSource(source: NxMediaSource, startPositionMs: Long = 0L)

    /** 准备播放（开始 buffer，到达 READY 后由调用方调用 [play]）。 */
    fun prepare()

    /** 开始或恢复播放。 */
    fun play()

    /** 暂停。 */
    fun pause()

    /** 跳转到指定位置（ms）。 */
    fun seekTo(positionMs: Long)

    /** 设置播放速度，1.0 = 正常速度。 */
    fun setSpeed(speed: Float)

    /**
     * 倍速音调保持开关（F-01）。
     *
     * - true：倍速时保持原音调（pitch=1.0），适合正常观影
     * - false：变速变调（pitch 随 speed 变化），适合快速浏览
     *
     * 切换后立即以当前速度重新应用 [androidx.media3.common.PlaybackParameters]。
     */
    val pitchPreservation: StateFlow<Boolean>

    /** 设置倍速音调保持开关（F-01）。 */
    fun setPitchPreservationEnabled(enabled: Boolean)

    /**
     * 均衡器实例（F-02）。
     *
     * 在 audioSessionId 就绪后内部自动 attach。UI 通过此引用读取频段信息、
     * 并在修改 [com.nichx.niplayer.datastore.AudioSettings] 后调用 [NiEqualizer.applySettings] 实时生效。
     */
    val equalizer: NiEqualizer

    /**
     * 设置视频缩放模式（高阶语义，含拉伸）。
     *
     * - [NxVideoScaleMode.Fit] / [NxVideoScaleMode.Crop]：直接映射到 media3 videoScalingMode
     * - [NxVideoScaleMode.Stretch]：通过返回值/状态由 UI 层调整 Surface 尺寸实现，
     *   调用方应订阅 [videoScaleMode] 并据此决定 SurfaceView 是按 aspectRatio 还是 fillMaxSize
     */
    fun setVideoScaleMode(mode: NxVideoScaleMode)

    /** 当前缩放模式。UI 层据此决定 SurfaceView 的尺寸约束（Stretch 时填满全屏）。 */
    val videoScaleMode: StateFlow<NxVideoScaleMode>

    /**
     * 启用/禁用智能黑边裁剪。
     *
     * 独立于 [videoScaleMode] 的内部裁剪覆盖，用于智能黑边检测：
     * - 启用时：media3 临时切到 SCALE_TO_FIT_WITH_CROPPING，配合 UI 层用有效画面比例
     *   设置 SurfaceView 尺寸，让视频帧保持比例裁剪填满 surface，正好裁掉内容黑边
     * - 禁用时：按 [videoScaleMode] 恢复（Fit→SCALE_TO_FIT，Crop→CROPPING）
     *
     * 仅在用户选择 [NxVideoScaleMode.Fit] 且检测到黑边时启用；
     * 切换视频源 / 切换 scaleMode / 检测失败时禁用。
     *
     * @param enabled true=启用裁剪覆盖，false=恢复用户 scaleMode
     */
    fun setBlackBarCropEnabled(enabled: Boolean)

    /** 当前可用音频轨道列表。 */
    val audioTracks: StateFlow<List<AudioTrackInfo>>

    /** 当前选中的音频轨道索引，-1 表示自动（无覆盖）。 */
    val selectedAudioTrackIndex: StateFlow<Int>

    /** 选择指定音频轨道。传 -1 恢复自动选择。 */
    fun selectAudioTrack(index: Int)

    /** 当前可用字幕轨道列表（含内嵌与外挂）。 */
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>>

    /**
     * 当前选中的字幕轨道索引。
     *
     * - `>= 0`：选中对应轨道
     * - `-1`：自动选择（无覆盖）
     * - `-2`：字幕关闭
     */
    val selectedSubtitleTrackIndex: StateFlow<Int>

    /**
     * 选择字幕轨道。
     *
     * @param index `>= 0` 选中指定轨道；`-1` 自动；`-2` 关闭字幕
     */
    fun selectSubtitleTrack(index: Int)

    /** 当前字幕延迟（ms）。正数延后，负数提前。 */
    val subtitleOffsetMs: StateFlow<Long>

    /** 设置字幕延迟。正数延后，负数提前。 */
    fun setSubtitleOffsetMs(offsetMs: Long)

    /** 设置音量，0.0 ~ 1.0。 */
    fun setVolume(volume: Float)

    /** 是否循环播放。 */
    fun setLooping(looping: Boolean)

    /**
     * 挂载渲染表面。
     *
     * @param surface SurfaceView / TextureView 提供的 Surface。传 null 解绑
     *               （如 Activity 切到后台或 Surface 销毁时）。
     */
    fun attachSurface(surface: Surface?)

    /** 释放播放器资源。调用后实例不可再用。 */
    fun release()
}
