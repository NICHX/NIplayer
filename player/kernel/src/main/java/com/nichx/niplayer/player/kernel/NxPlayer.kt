package com.nichx.niplayer.player.kernel

import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import com.nichx.niplayer.player.kernel.audio.EqualizerConfig
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
 * 视频缩放模式（互斥三档）。
 *
 * 与「目标比例」正交：目标比例由调用方计算（去黑边开启时取内容比例，否则取视频比例），
 * 本枚举只决定把目标比例**怎么铺到屏幕上**。三个值的语义穷尽且不重叠。
 *
 * - [Contain]：完整放下，可能留边（surface 按目标比例缩进屏幕内）
 * - [Cover]：保持比例放大到铺满屏幕，溢出由父容器裁掉
 * - [Fill]：不保持比例直接铺满屏幕（画面变形）
 *
 * 渲染机制：SurfaceView 恒按「目标比例」设定尺寸，media3 缩放模式由
 * [NxPlayer.setVideoCropEnabled] 决定。Android 的 `SCALE_TO_FIT` 是把内容缩放**到 surface
 * 尺寸**（官方文档：调用方必须让 Surface 具备正确的显示比例），因此只要 surface 比例与
 * 目标比例一致就不会变形；[Fill] 正是故意让 surface 与视频比例不一致以产生拉伸。
 */
enum class NxVideoScaleMode {
    Contain,
    Cover,
    Fill,
}

/**
 * NIplayer 播放器内核抽象接口。
 *
 * 设计要点：
 * - **不继承 View**：UI 层（后续 PlayerScreen Composable）通过 [attachSurface] 挂载渲染表面，
 *   避免继承 View 的耦合设计。
 * - **StateFlow 暴露状态**：[state] / [positionMs] / [durationMs] 等，避免回调地狱。
 * - **SharedFlow 暴露事件**：[events] 用于一次性 UI 反馈（Toast / 首帧渲染动画等）。
 * - **单一 media3 内核**：本接口仅由 [com.nichx.niplayer.player.kernel.media3.NxMedia3Player] 实现，
 *   无工厂模式，仅保留单一 media3 内核实现，避免多套实现切换。
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
     * 并在修改均衡器设置后调用 [NiEqualizer.applySettings]（传入最新 [EqualizerConfig]）实时生效。
     */
    val equalizer: NiEqualizer

    /**
     * 设置视频缩放模式。
     *
     * 调用方应订阅 [videoScaleMode] 并据此决定 SurfaceView 的尺寸约束
     * （[NxVideoScaleMode.Fill] 时填满全屏，其余按目标比例设定）。
     */
    fun setVideoScaleMode(mode: NxVideoScaleMode)

    /** 当前缩放模式。UI 层据此决定 SurfaceView 的尺寸约束。 */
    val videoScaleMode: StateFlow<NxVideoScaleMode>

    /**
     * 启用/禁用 media3 层裁剪。
     *
     * 由调用方按几何推出，**不是**独立的用户开关：
     * 当「目标比例 ≠ 视频比例」（即去黑边生效，需要把视频帧在 surface 内裁掉自带黑边）时置 true。
     * - true → `SCALE_TO_FIT_WITH_CROPPING`：保持比例裁剪填满 surface
     * - false → `SCALE_TO_FIT`：缩放**到** surface 尺寸（surface 比例与目标比例一致，故无变形）
     *
     * 内核不再持有第二套裁剪状态，也不再有优先级分支——只有这一个布尔输入。
     */
    fun setVideoCropEnabled(enabled: Boolean)

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

    /**
     * 暴露给媒体会话（前台服务的 MediaSession / 媒体通知）的 media3 [Player] 桥。
     *
     * **不转移所有权**：释放仍由调用方（PlayerViewModel）在其 [release] 时统一完成。
     * 实现方直接返回内部 ExoPlayer；非 media3 实现返回 null。
     */
    val mediaSessionPlayer: Player?

    /** 释放播放器资源。调用后实例不可再用。 */
    fun release()
}
