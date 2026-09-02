package com.nichx.niplayer.player.mpv

import android.view.Surface
import androidx.media3.common.text.Cue
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 基于 mpv 的播放内核后端（多内核接入骨架，`backendId = "mpv"`）。
 *
 * **当前状态：仅供接入脚手架，未接通 native（libmpv）**：
 * - [supports] 当前恒返回 false，[backendPriority] 低于 media3（兜底），
 *   因此默认能力解析总会选中 media3，本后端不会被真正用于播放。
 * - 各 [NxPlayer] 成员为占位默认值，具体桥接（MPVLib 命令 / 状态属性观察 →
 *   StateFlow / 事件）待后续按方案接入 `is.xyz.mpv` 封装与自编译产物 `libmpv.so`。
 *
 * 作用：让 mpv 成为「可配置注册、当前休眠」的第二内核预留位，验证 @IntoSet 多绑定链路。
 */
class NxMpvPlayer @Inject constructor() : NxPlayerBackend {

    // region 多内核能力声明

    override val backendId: String = "mpv"

    /** 骨架阶段不参与能力解析，恒返回 false（media3 兜底选中）。 */
    override fun supports(source: NxMediaSource): Boolean = false

    /** 仅低于 media3（Int.MAX_VALUE），为后续真实 mpv 预留优先级位。 */
    override val backendPriority: Int = Int.MAX_VALUE - 1

    /** 独立内核，不属于任何变体。 */
    override val backendVariantOf: String? = null

    // endregion

    // region 播放状态（占位默认值，待桥接 mpv 属性观测）

    override val state: StateFlow<PlaybackState> =
        MutableStateFlow<PlaybackState>(PlaybackState.Idle).asStateFlow()

    override val positionMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    override val bufferedMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    override val durationMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    override val videoSize: StateFlow<VideoSize> =
        MutableStateFlow(VideoSize(0, 0)).asStateFlow()

    override val mediaInfo: StateFlow<MediaInfo?> = MutableStateFlow<MediaInfo?>(null).asStateFlow()

    override val cues: StateFlow<List<Cue>> = MutableStateFlow<List<Cue>>(emptyList()).asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>()
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    override val playbackSpeed: StateFlow<Float> = MutableStateFlow(1f).asStateFlow()

    override val networkSpeed: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    override val pitchPreservation: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override val equalizer: NiEqualizer = NiEqualizer()

    override val videoScaleMode: StateFlow<NxVideoScaleMode> =
        MutableStateFlow(NxVideoScaleMode.Fit).asStateFlow()

    override val audioTracks: StateFlow<List<AudioTrackInfo>> =
        MutableStateFlow<List<AudioTrackInfo>>(emptyList()).asStateFlow()

    override val selectedAudioTrackIndex: StateFlow<Int> = MutableStateFlow(-1).asStateFlow()

    override val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> =
        MutableStateFlow<List<SubtitleTrackInfo>>(emptyList()).asStateFlow()

    override val selectedSubtitleTrackIndex: StateFlow<Int> = MutableStateFlow(-1).asStateFlow()

    override val activeSubtitleTrackIndex: StateFlow<Int> = MutableStateFlow(-1).asStateFlow()

    override val subtitleOffsetMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    // endregion

    // region 控制命令（占位空实现，待桥接 mpv 命令）

    override fun setSource(source: NxMediaSource, startPositionMs: Long) = Unit

    override fun prepare() = Unit

    override fun play() = Unit

    override fun pause() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun setSpeed(speed: Float) = Unit

    override fun setPitchPreservationEnabled(enabled: Boolean) = Unit

    override fun setVideoScaleMode(mode: NxVideoScaleMode) = Unit

    override fun setBlackBarCropEnabled(enabled: Boolean) = Unit

    override fun selectAudioTrack(index: Int) = Unit

    override fun selectSubtitleTrack(index: Int) = Unit

    override fun setSubtitleOffsetMs(offsetMs: Long) = Unit

    override fun setVolume(volume: Float) = Unit

    override fun setLooping(looping: Boolean) = Unit

    override fun attachSurface(surface: Surface?) = Unit

    override fun release() {
        equalizer.release()
    }

    // endregion
}