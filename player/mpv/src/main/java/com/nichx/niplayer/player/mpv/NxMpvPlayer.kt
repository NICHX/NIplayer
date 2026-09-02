package com.nichx.niplayer.player.mpv

import android.content.Context
import android.view.Surface
import androidx.media3.common.text.Cue
import androidx.media3.datasource.DefaultDataSource
import com.nichx.niplayer.player.kernel.AudioTrackInfo
import com.nichx.niplayer.player.kernel.MediaInfo
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.NxPlayer
import com.nichx.niplayer.player.kernel.NxPlayerBackend
import com.nichx.niplayer.player.kernel.NxVideoScaleMode
import com.nichx.niplayer.player.kernel.PlaybackEvent
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo
import com.nichx.niplayer.player.kernel.VideoSize
import com.nichx.niplayer.player.kernel.audio.NiEqualizer
import dagger.hilt.android.qualifiers.ApplicationContext
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvEvent
import `is`.xyz.mpv.MPVLib.MpvFormat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * 基于 mpv（libmpv + `is.xyz.mpv.MPVLib` JNI 封装）的播放内核后端，`backendId = "mpv"`。
 *
 * **职责**：把 mpv 属性观测 / 事件与 [NxPlayer] 的状态流做桥接，并驱动播放命令。
 * mpv 为单进程单实例（[MPVLib] 为 object），本实现采用 lazy 惰性初始化（首次 [setSource]
 * 时 [ensureInitialized]），多个播放器实例共享同一 handle，各自作为 [MPVLib.EventObserver]。
 *
 * **重要：当前 `supports()` 恒 false、`backendPriority` 低于 media3，media3 仍是生效内核。**
 * 原因：NxPlayer 的状态机是 media3 风格（Idle→Buffering→Ready→Playing/Paused→Ended），mpv
 * 是无状态模型，下方 [deriveState] 为近似映射；且 mpv 渲染宿主（BaseMPVView / GL 表面）尚未
 * 接入 PlayerScreen，且未经真机验证。待渲染接入 + 真机校准 [deriveState] 后才可开启 supports()。
 */
class NxMpvPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : NxPlayerBackend, MPVLib.EventObserver {

    // region 多内核能力声明

    override val backendId: String = "mpv"

    /**
     * 能力声明。
     *
     * 当前恒 false：auto 模式下 media3（优先级 Int.MAX_VALUE）恒优先，mpv 仅作为实验性内核，
     * 通过设置显式指定 `playerBackend = "mpv"` 才启用（解析器对该强制选择跳过能力过滤）。
     * 待渲染宿主接入 + 真机校准状态机后，可按 [NxMediaSource] 实判并放开 auto 选择。
     */
    override fun supports(source: NxMediaSource): Boolean = false

    override val backendPriority: Int = Int.MAX_VALUE - 1

    override val backendVariantOf: String? = null

    // endregion

    // region 惰性命周期（mpv 顺序：create → attachSurface(wid) → init → 命令）

    @Volatile
    private var created = false

    /** mpv_initialize 是否已完成（此后才可发命令，且 wid 须在此前设好）。 */
    @Volatile
    private var activated = false

    /** 是否已收到首次文件加载成功事件，用于状态推导。 */
    @Volatile
    private var fileLoaded = false

    // init 之前到达的播放请求暂时缓存，待 attachSurface→init 后补发
    private var pendingSource: NxMediaSource? = null
    private var pendingStartMs = 0L
    private var pendingPlay = false

    /** 待续播位置（ms），FILE_LOADED 后 seek 定位。 */
    @Volatile
    private var resumeMs = 0L

    /** 本地 HTTP 读代理（mpv 读非 http 源时使用）。 */
    private var proxy: StorageProxyServer? = null

    private val createLock = Any()

    private fun ensureCreated() {
        if (created) return
        synchronized(createLock) {
            if (created) return
            MPVLib.create(context)
            // 观测驱动状态流的核心属性（格式与 JNI event.cpp 中的分派对应）
            MPVLib.observeProperty("pause", MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("eof-reached", MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("idle-active", MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("time-pos", MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("speed", MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("width", MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("height", MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("aid", MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("sid", MpvFormat.MPV_FORMAT_INT64)
            MPVLib.addObserver(this)
            created = true
        }
    }

    /** 在 wid 已设好后初始化 mpv 并补发缓存的播放请求。 */
    private fun activateAfterSurface() {
        if (activated) return
        MPVLib.init()
        activated = true
        pendingSource?.let { src ->
            pendingSource = null
            loadFile(src, pendingStartMs)
            if (pendingPlay) MPVLib.setPropertyBoolean("pause", false)
        }
    }

    /** 仅当 mpv 已初始化（可安全发命令）时执行 [action]，否则返回 false 供调用方缓存。 */
    private inline fun ifActivated(action: () -> Unit): Boolean {
        if (!activated) return false
        action()
        return true
    }

    // endregion

    // region 状态流

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _bufferedMs = MutableStateFlow(0L)
    override val bufferedMs: StateFlow<Long> = _bufferedMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _videoSize = MutableStateFlow(VideoSize(0, 0))
    override val videoSize: StateFlow<VideoSize> = _videoSize.asStateFlow()

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    override val mediaInfo: StateFlow<MediaInfo?> = _mediaInfo.asStateFlow()

    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    override val cues: StateFlow<List<Cue>> = _cues.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _networkSpeed = MutableStateFlow(0L)
    override val networkSpeed: StateFlow<Long> = _networkSpeed.asStateFlow()

    private val _pitchPreservation = MutableStateFlow(true)
    override val pitchPreservation: StateFlow<Boolean> = _pitchPreservation.asStateFlow()

    private val _equalizer = NiEqualizer()
    override val equalizer: NiEqualizer = _equalizer

    private val _videoScaleMode = MutableStateFlow(NxVideoScaleMode.Fit)
    override val videoScaleMode: StateFlow<NxVideoScaleMode> = _videoScaleMode.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    override val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _selectedAudioTrackIndex = MutableStateFlow(-1)
    override val selectedAudioTrackIndex: StateFlow<Int> = _selectedAudioTrackIndex.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    override val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

    private val _selectedSubtitleTrackIndex = MutableStateFlow(-1)
    override val selectedSubtitleTrackIndex: StateFlow<Int> =
        _selectedSubtitleTrackIndex.asStateFlow()

    private val _activeSubtitleTrackIndex = MutableStateFlow(-1)
    override val activeSubtitleTrackIndex: StateFlow<Int> = _activeSubtitleTrackIndex.asStateFlow()

    private val _subtitleOffsetMs = MutableStateFlow(0L)
    override val subtitleOffsetMs: StateFlow<Long> = _subtitleOffsetMs.asStateFlow()

    // endregion

    // region 状态推导（mpv 无状态模型 → NxPlayer 状态机 的近似映射，待真机校准）

    private val mpvPaused = MutableStateFlow(true)
    private val mpvEof = MutableStateFlow(false)
    private val mpvIdle = MutableStateFlow(true)

    private fun deriveState() {
        _state.value = when {
            mpvEof.value -> PlaybackState.Ended
            mpvPaused.value -> PlaybackState.Paused
            fileLoaded && !mpvIdle.value -> PlaybackState.Playing
            fileLoaded -> PlaybackState.Ready
            else -> PlaybackState.Buffering
        }
    }

    // endregion

    // region MPVLib.EventObserver

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "width" -> _videoSize.value = _videoSize.value.copy(width = value.toInt())
            "height" -> _videoSize.value = _videoSize.value.copy(height = value.toInt())
            "aid" -> _selectedAudioTrackIndex.value = (value - 1).toInt()
            "sid" -> {
                _selectedSubtitleTrackIndex.value = (value - 1).toInt()
                _activeSubtitleTrackIndex.value = _selectedSubtitleTrackIndex.value
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> { mpvPaused.value = value; deriveState() }
            "eof-reached" -> { mpvEof.value = value; deriveState() }
            "idle-active" -> { mpvIdle.value = value; deriveState() }
        }
    }

    override fun eventProperty(property: String, value: String) = Unit

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> if (value >= 0) _positionMs.value = (value * 1000).toLong()
            "duration" -> _durationMs.value = (value * 1000).toLong()
            "speed" -> _playbackSpeed.value = value.toFloat()
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MpvEvent.MPV_EVENT_START_FILE -> {
                fileLoaded = false
                _state.value = PlaybackState.Buffering
            }
            MpvEvent.MPV_EVENT_FILE_LOADED -> {
                fileLoaded = true
                _mediaInfo.value = readMediaInfo()
                if (resumeMs > 0) {
                    // 续播定位：seek 到记录位置（mpv time-pos 接受浮点秒）
                    MPVLib.setPropertyDouble("time-pos", resumeMs / 1000.0)
                }
                deriveState()
            }
            MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                _events.tryEmit(PlaybackEvent.RenderingStart)
                _events.tryEmit(PlaybackEvent.VideoSizeChanged(_videoSize.value))
            }
            MpvEvent.MPV_EVENT_END_FILE -> if (mpvEof.value) _state.value = PlaybackState.Ended
        }
    }

    // endregion

    // region 命令

    override fun setSource(source: NxMediaSource, startPositionMs: Long) {
        ensureCreated()
        fileLoaded = false
        if (!ifActivated { loadFile(source, startPositionMs) }) {
            // mpv 尚未 init（surface 未就绪）：缓存待命，attachSurface→init 后补发
            pendingSource = source
            pendingStartMs = startPositionMs
        }
    }

    private fun loadFile(source: NxMediaSource, startPositionMs: Long) {
        fileLoaded = false
        resumeMs = startPositionMs
        when (source) {
            is NxMediaSource.Http -> {
                // mpv 内置 libcurl，可直连 http(s)
                val opts = if (source.headers.isNotEmpty()) {
                    // mpv 的 http-header-fields 以 "Key: Value" 分号分隔
                    "http-header-fields=" + source.headers.entries.joinToString(";") { (k, v) -> "$k: $v" }
                } else null
                MPVLib.command(if (opts == null) {
                    arrayOf("loadfile", source.uri.toString(), "replace")
                } else {
                    arrayOf("loadfile", source.uri.toString(), "replace", opts)
                })
            }
            is NxMediaSource.DataSource -> {
                // 自定义存储（SMB 等）：用其 media3 DataSource.Factory 起本地 http 代理给 mpv
                loadViaProxy(source.factory, source.uri, source.storage)
            }
            is NxMediaSource.Local -> {
                // 本地 file/content：用 DefaultDataSource 代理
                loadViaProxy(DefaultDataSource.Factory(context), source.uri)
            }
        }
    }

    /**
     * 起（或替换）本地 http 代理，让 mpv 通过 http 读 media3 DataSource 的字节流。
     *
     * 参考 mpvExtended-android 的 SMB 稳定化方案：传入 [Storage] 注册保活回调，在 mpv 缓冲
     * 暂停读取的间隙维持 SMB 播放会话不被空闲断开（否则 seek/切集可能卡住十几秒）。
     */
    private fun loadViaProxy(
        factory: androidx.media3.datasource.DataSource.Factory,
        uri: android.net.Uri,
        storage: com.nichx.niplayer.storage.Storage? = null,
    ) {
        proxy?.stop()
        val server = StorageProxyServer(
            factory = factory,
            uri = uri,
            keepAlive = storage?.let { s ->
                { runCatching { runBlocking { s.pingPlay() } } }
            },
        )
        proxy = server
        val url = server.start()
        MPVLib.command(arrayOf("loadfile", url, "replace"))
    }

    private fun stopProxy() {
        proxy?.stop()
        proxy = null
    }

    override fun prepare() {
        ensureCreated()
        // mpv loadfile 后即处于待播放态；此处无额外动作（状态由事件驱动）
    }

    override fun play() {
        ensureCreated()
        if (!ifActivated { MPVLib.setPropertyBoolean("pause", false) }) {
            pendingPlay = true
        }
    }

    override fun pause() {
        ensureCreated()
        ifActivated { MPVLib.setPropertyBoolean("pause", true) }
    }

    override fun seekTo(positionMs: Long) {
        ifActivated { MPVLib.setPropertyDouble("time-pos", positionMs / 1000.0) }
    }

    override fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        ifActivated { MPVLib.setPropertyDouble("speed", speed.toDouble()) }
    }

    override fun setPitchPreservationEnabled(enabled: Boolean) {
        // mpv 默认恒音调（scaletempo）；此开关暂不映射，保持记录
        _pitchPreservation.value = enabled
    }

    override fun setVideoScaleMode(mode: NxVideoScaleMode) {
        _videoScaleMode.value = mode
        // mpv 拉伸/裁剪近似：Stretch 时禁用保持比例（video-unscaled），其余复位
        ifActivated {
            if (mode == NxVideoScaleMode.Stretch) {
                MPVLib.setPropertyBoolean("video-unscaled", true)
            } else {
                MPVLib.setPropertyBoolean("video-unscaled", false)
            }
        }
    }

    override fun setBlackBarCropEnabled(enabled: Boolean) = Unit

    override fun selectAudioTrack(index: Int) {
        ifActivated { MPVLib.setPropertyInt("aid", index + 1) }
    }

    override fun selectSubtitleTrack(index: Int) {
        ifActivated {
            when (index) {
                -2 -> MPVLib.setPropertyString("sid", "no") // 关闭
                -1 -> MPVLib.setPropertyString("sid", "auto") // 自动
                else -> MPVLib.setPropertyInt("sid", index + 1)
            }
        }
    }

    override fun setSubtitleOffsetMs(offsetMs: Long) {
        _subtitleOffsetMs.value = offsetMs
        ifActivated { MPVLib.setPropertyDouble("sub-delay", offsetMs / 1000.0) }
    }

    override fun setVolume(volume: Float) {
        ifActivated { MPVLib.setPropertyDouble("volume", volume * 100.0) }
    }

    override fun setLooping(looping: Boolean) {
        ifActivated { MPVLib.setPropertyBoolean("loop-file", looping) }
    }

    override fun attachSurface(surface: Surface?) {
        if (surface != null) {
            ensureCreated()
            // wid 必须设在 mpv_initialize 之前；此处先 attach（设 wid）再 init
            MPVLib.attachSurface(surface)
            activateAfterSurface()
        } else {
            if (created) MPVLib.detachSurface()
        }
    }

    override fun release() {
        _equalizer.release()
        stopProxy()
        if (created) {
            MPVLib.removeObserver(this)
            MPVLib.destroy()
            created = false
            activated = false
        }
    }

    // endregion

    // region 媒体信息读取

    private fun readMediaInfo(): MediaInfo {
        val videoCodec = MPVLib.getPropertyString("video-codec")
        val audioCodec = MPVLib.getPropertyString("audio-codec")
        val w = _videoSize.value.width
        val h = _videoSize.value.height
        return MediaInfo(
            videoCodec = videoCodec?.takeIf { it.isNotBlank() },
            audioCodec = audioCodec?.takeIf { it.isNotBlank() },
            resolution = if (w > 0 && h > 0) "${w}×${h}" else null,
            bitrate = null,
            frameRate = MPVLib.getPropertyDouble("estimated-vf-fps")?.toFloat(),
            hdrType = null,
        )
    }

    // endregion
}