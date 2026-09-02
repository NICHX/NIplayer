package com.nichx.niplayer.player.kernel.media3

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize as M3VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nichx.niplayer.player.kernel.AudioTrackInfo
import com.nichx.niplayer.player.kernel.MediaInfo
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.NxPlayer
import com.nichx.niplayer.player.kernel.NxPlayerBackend
import com.nichx.niplayer.player.kernel.NxVideoScaleMode
import com.nichx.niplayer.player.kernel.PlaybackEvent
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.R
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo
import com.nichx.niplayer.player.kernel.VideoSize
import com.nichx.niplayer.player.kernel.audio.NiEqualizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.security.cert.X509Certificate
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 基于 androidx.media3 的 [NxPlayer] 实现。
 *
 * 替代旧仓库 ExoVideoPlayer / IjkVideoPlayer / VlcVideoPlayer 三套实现。
 *
 * 设计差异：
 * - **统一内核**：移除 ijk / vlc / ffmpeg so / VlcProxyServer 代理。
 *   headers 由 [OkHttpDataSource] 直接处理（替代 VlcProxyServer 反射改端口的 hack）。
 * - **共享 OkHttpClient**：由 :core:network 通过 Hilt 注入，替代旧版内部 new OkHttpClient。
 *   全局共享连接池与超时配置（15s connect / 120s read / 30s write）。
 * - **无工厂模式**：单内核无需 PlayerFactory，直接 @Inject 构造。
 * - **不继承 View**：渲染表面由调用方通过 [attachSurface] 注入。
 *
 * 生命周期：实例由调用方持有（通常 PlayerViewModel 在 onCleared 中调用 [release]）。
 */
@OptIn(UnstableApi::class)
class NxMedia3Player @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val mediaCache: SimpleCache,
) : NxPlayerBackend, Player.Listener {

    // region 多内核能力声明（默认兜底内核）

    override val backendId: String = "media3"

    /** 默认兜底内核恒支持一切媒体源。 */
    override fun supports(source: NxMediaSource): Boolean = true

    /** 默认优先级，后续能力解析器以此为基准。 */
    override val backendPriority: Int = Int.MAX_VALUE

    /** 独立内核，不属于任何变体。 */
    override val backendVariantOf: String? = null

    // endregion

    // region 数据源与渲染：headers 通过 OkHttpDataSource 注入

    private val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

    /**
     * W-M7 修复：HTTP 播放路径的本地字节缓存。
     *
     * media3 OkHttpDataSource 每次 seek 都重新 `open(DataSpec)` 发起新的 Range 请求，
     * 不保留前一个连接。对于高延迟广域网 WebDAV（200-500ms RTT），每次前向 seek 都要
     * 等 1-2 个 RTT 才开始接收数据，拖动进度条响应延迟明显。
     *
     * 引入 [SimpleCache] + [CacheDataSource] 包装 [okHttpDataSourceFactory]：
     * - 已下载的 HTTP chunks 持久化到本地磁盘，前向/后向 seek 命中缓存时无需 HTTP 请求
     * - [LeastRecentlyUsedCacheEvictor] LRU 淘汰策略保证缓存大小不超过上限（由 :player:kernel
     *   PlayerModule 提供，500MB）
     * - [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR]：缓存读写失败时回退到直接 HTTP，
     *   避免磁盘满 / cacheDir 只读等异常影响播放
     *
     * 仅对 HTTP 路径生效（[DefaultDataSource] 内部按 URI scheme 分流，http/https 走
     * [CacheDataSource]，file/content 直接处理）；Local / DataSource (SMB) 路径不经过缓存。
     *
     * 缓存目录位于 `cacheDir/exo_media_cache/`，系统低存储时可自动清理。缓存键默认为 URL
     * 本身（不含 credentials），同一 URL 在 strict / trust-all 路径间共享缓存。
     *
     * [mediaCache] 为 Hilt 注入的进程级单例：media3 对同一目录是进程独占的，多播放器实例
     * 若各自 new SimpleCache 会撞同目录锁（IllegalStateException）。共享单例可避免该崩溃。
     */
    private val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(okHttpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val defaultDataSourceFactory = DefaultDataSource.Factory(
        context,
        cacheDataSourceFactory,
    )

    /**
     * W-C3 修复：trust-all TLS 证书的 OkHttpClient（lazy）。
     *
     * WebDAV 非 strict 模式（`webDavStrict=false`）下，浏览/缩略图路径由 WebDavStorage
     * 内部派生 trust-all client，但播放路径走本类注入的 strict 单例 client，导致自签证书
     * WebDAV 服务器播放失败。此处用 [okHttpClient].newBuilder() 派生 trust-all client，
     * 共享原 client 的连接池与超时配置，仅覆盖 SSL 校验。
     *
     * lazy 确保仅在首次遇到 trustAllCertificates=true 的 MediaSource 时才创建。
     */
    private val trustAllClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .sslSocketFactory(TRUST_ALL_SSL.socketFactory, TRUST_ALL_MANAGER)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /** trust-all OkHttpDataSource.Factory，配套 [trustAllClient] 使用。 */
    private val trustAllDataSourceFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(trustAllClient).also {
            it.setTransferListener(speedListener)
        }
    }

    /** trust-all 路径的缓存 DataSource.Factory，包装 [trustAllDataSourceFactory]，共享 [mediaCache]。 */
    private val trustAllCacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(trustAllDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** trust-all DefaultDataSource.Factory，配套 [trustAllCacheDataSourceFactory] 使用。 */
    private val trustAllDefaultDataSourceFactory: DefaultDataSource.Factory by lazy {
        DefaultDataSource.Factory(context, trustAllCacheDataSourceFactory)
    }

    /**
     * 缓冲策略：增大 minBufferMs / maxBufferMs，容忍 SMB 等网络存储的吞吐波动。
     *
     * - minBufferMs=30s：至少缓冲 30s 才开始播放（默认 15s）
     * - maxBufferMs=120s：最多缓冲 120s（默认 50s），大文件预读更多数据
     * - bufferForPlaybackMs=1s：缓冲 1s 即可起播（默认 2.5s）
     * - backBufferMs=30s：保留 30s 后向缓冲用于快退（默认 30s）
     */
    private val loadControl: LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 30_000,
            /* maxBufferMs = */ 120_000,
            /* bufferForPlaybackMs = */ 1_000,
            /* bufferForPlaybackAfterRebufferMs = */ 3_000,
        )
        .setBackBuffer(
            /* backBufferDurationMs = */ 30_000,
            /* retainBackBufferFromKeyframe = */ true,
        )
        .build()

    // region 网速采集

    private var bytesSinceLastTick = 0L

    private val speedListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytes: Int) {
            bytesSinceLastTick += bytes
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    }

    // endregion

    // region ExoPlayer 实例

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(FfmpegRenderersFactory(context))
        .setLoadControl(loadControl)
        // m-01 修复：原字段 mediaSourceFactory 已删除，此处直接内联构造初始 factory。
        // ExoPlayer 仅在内部 `createMediaSource` 时使用此 factory；实际 setSource 路径
        // 调用方通过 `ExoPlayer.setMediaSource(MediaSource, Long)` 直接传入已构造的
        // MediaSource，绕过 ExoPlayer 内部 factory，故初始 factory 仅用于 ExoPlayer 启动。
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(defaultDataSourceFactory),
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .also { it.addListener(this) }

    // endregion

    // region StateFlow 状态暴露

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

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    override val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    override val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

    private val _selectedSubtitleTrackIndex = MutableStateFlow(-1)
    override val selectedSubtitleTrackIndex: StateFlow<Int> = _selectedSubtitleTrackIndex.asStateFlow()

    private val _activeSubtitleTrackIndex = MutableStateFlow(-1)
    override val activeSubtitleTrackIndex: StateFlow<Int> = _activeSubtitleTrackIndex.asStateFlow()

    private val _subtitleOffsetMs = MutableStateFlow(0L)
    override val subtitleOffsetMs: StateFlow<Long> = _subtitleOffsetMs.asStateFlow()

    private val _videoScaleMode = MutableStateFlow(NxVideoScaleMode.Fit)
    override val videoScaleMode: StateFlow<NxVideoScaleMode> = _videoScaleMode.asStateFlow()

    private val _selectedAudioTrackIndex = MutableStateFlow(-1)
    override val selectedAudioTrackIndex: StateFlow<Int> = _selectedAudioTrackIndex.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(
        extraBufferCapacity = 16,
        // M-05 修复：原默认 SUSPEND + tryEmit 组合在缓冲满时静默丢弃事件。
        // 改为 DROP_OLDEST：缓冲满时丢弃最旧事件而非新事件，保留最新状态。
        // 对 VideoSizeChanged/RenderingStart 等高频事件更合理（旧值无意义）。
        // Error 事件缓冲为 16，正常播放不会触发丢弃。
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    /**
     * 当前播放速度（m-03 修复）。
     *
     * - [setSpeed] 经 clamp 后写入并同步给 media3
     * - [onPlaybackParametersChanged] 在 media3 内部改写速度时同步回写，保证 UI 一致
     *
     * 初值 1.0f，[release] 时重置回 1.0f。
     */
    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /** 倍速音调保持开关（F-01），默认 true 保持原音调。 */
    private val _pitchPreservation = MutableStateFlow(true)
    override val pitchPreservation: StateFlow<Boolean> = _pitchPreservation.asStateFlow()

    /** 均衡器实例（F-02），在 audioSessionId 就绪后自动 attach。 */
    private val _equalizer = NiEqualizer()
    override val equalizer: NiEqualizer = _equalizer

    private val _networkSpeed = MutableStateFlow(0L)
    override val networkSpeed: StateFlow<Long> = _networkSpeed.asStateFlow()

    /** 是否已发射过 HDR 检测事件（避免重复）。 */
    private val hdrDetectedEmitted = AtomicBoolean(false)

    // endregion

    // region 位置 / 时长轮询

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * C-01 修复：标记最近发生过 onPlayerError，防止随后 media3 自动转入 STATE_IDLE 时
     * [onPlaybackStateChanged] 把 [PlaybackState.Error] 覆盖为 [PlaybackState.Idle]。
     *
     * 生命周期：[onPlayerError] 置 true；新的 [setSource] / [prepare] 调用时清零
     * （用户主动重试视为已脱离错误态）。
     */
    @Volatile
    private var hasError: Boolean = false

    /**
     * M-01 修复：标记 ExoPlayer 已 release，[positionTicker] 据此短路避免访问已释放实例
     * 抛 IllegalStateException。release 顺序保证：先置 [isReleased]=true 再 release ExoPlayer。
     */
    @Volatile
    private var isReleased: Boolean = false

    /** 是否为网络源（HTTP 或 SMB 等），本地文件不显示网速。 */
    private var isNetworkSource: Boolean = false

    /** 无数据接收的连续 tick 计数，用于空闲超时清零。 */
    private var networkIdleTicks: Int = 0

    private val positionTicker = object : Runnable {
        override fun run() {
            // M-01 修复：已 release 后不再访问 exoPlayer，避免 IllegalStateException
            if (!isReleased && _state.value !is PlaybackState.Idle) {
                try {
                    _positionMs.value = exoPlayer.currentPosition.coerceAtLeast(0)
                    _bufferedMs.value = exoPlayer.bufferedPosition.coerceAtLeast(0)
                    _durationMs.value = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0
                    val bytes = bytesSinceLastTick
                    bytesSinceLastTick = 0L
                    if (isNetworkSource) {
                        if (bytes > 0) {
                            _networkSpeed.value = (bytes * 1000L) / POSITION_UPDATE_INTERVAL_MS
                            networkIdleTicks = 0
                        } else {
                            if (networkIdleTicks >= 2) {
                                _networkSpeed.value = 0L
                            } else {
                                networkIdleTicks++
                            }
                        }
                    } else {
                        _networkSpeed.value = 0L
                    }
                } catch (_: IllegalStateException) {
                    // M-01 兜底：release 与本块竞态时仍可能抛 IllegalStateException，吞掉避免崩溃
                }
            }
            if (!isReleased) {
                mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    init {
        okHttpDataSourceFactory.setTransferListener(speedListener)
        mainHandler.post(positionTicker)
    }

    // endregion

    // region NxPlayer API

    override fun setSource(source: NxMediaSource, startPositionMs: Long) {
        // m-01 修复：原实现对 `mediaSourceFactory` 字段重新赋值（var），
        // 但 ExoPlayer 构造时已持有原始工厂引用，字段重赋值无意义。
        // 现改为构造局部 [DefaultMediaSourceFactory]，直接用其 createMediaSource
        // 传给 setMediaSource，不依赖字段状态。
        //
        // 按 NxMediaSource 类型选择 DataSource.Factory：
        // - Http / Local: 使用默认 DefaultDataSource.Factory（OkHttpDataSource + file/content）
        // - DataSource:   使用调用方提供的自定义 DataSource.Factory（SMB 等）
        val httpSource = source as? NxMediaSource.Http
        val headers = httpSource?.headers.orEmpty()

        // W-C3 修复：Http 模式下，若需要信任自签证书，切换到 trust-all factory 并注入 headers。
        // 否则用默认 strict factory。headers 通过对应的 OkHttpDataSource.Factory 注入。
        val effectiveFactory = (source as? NxMediaSource.DataSource)?.factory
        val workingFactory: DefaultMediaSourceFactory = when {
            httpSource != null && httpSource.trustAllCertificates -> {
                DefaultMediaSourceFactory(context).setDataSourceFactory(trustAllDefaultDataSourceFactory)
            }
            effectiveFactory != null -> {
                val meteredFactory = DataSource.Factory {
                    effectiveFactory.createDataSource().also { ds ->
                        if (ds is BaseDataSource) {
                            ds.addTransferListener(speedListener)
                        }
                    }
                }
                DefaultMediaSourceFactory(context).setDataSourceFactory(meteredFactory)
            }
            else -> {
                DefaultMediaSourceFactory(context).setDataSourceFactory(defaultDataSourceFactory)
            }
        }
        // M-04 修复：media3 的 setDefaultRequestProperties 是 clear-then-put 语义，
        // 传入空 map 会先清空旧 headers 再写入。无论 headers 是否为空都显式调用对应 factory，
        // 确保切换到非 Http 源（headers 为空）时旧 Referer/Cookie 被清除，避免跨源污染。
        if (httpSource != null && httpSource.trustAllCertificates) {
            trustAllDataSourceFactory.setDefaultRequestProperties(headers)
        } else {
            okHttpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        // 直接构造 MediaSource 并设置给 ExoPlayer（避免重建 ExoPlayer 实例）
        // W-M8 修复：将 startPositionMs 直接传给 setMediaSource，避免续播场景下先从 0
        // 开始 buffer 再被外层 seekTo 中断导致的无效 Range 请求。media3 会在 prepare 时
        // 自动 seek 到此位置开始下载，省去一次握手。
        // W-N7 修复：显式设置 mediaId，与应用层 uniqueKey 保持一致。
        // 调用方未提供时回退到 uri 字符串（保持与原 MediaItem.fromUri 行为一致）。
        val mediaItem = MediaItem.Builder()
            .setMediaId(source.mediaId.ifEmpty { source.uri.toString() })
            .setUri(source.uri)
            .build()
        val mediaSource = workingFactory.createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
        // C-01 修复：用户主动切换源视为脱离错误态，清除 hasError
        hasError = false
        isNetworkSource = source !is NxMediaSource.Local
        networkIdleTicks = 0
        hdrDetectedEmitted.set(false)
        // M-03 修复：不在此处写 Buffering。setSource 后 ExoPlayer 仍处于 STATE_IDLE，
        // 状态与实际不一致；PlayerViewModel 在 setSource 后会显式 prepare() + play()，
        // prepare 触发的 onPlaybackStateChanged(STATE_BUFFERING) 会自然驱动状态。
        // 此处写 Buffering 会让 UI 在中间窗口看到"缓冲中"但实际无缓冲动作。
    }

    override fun prepare() {
        // C-01 修复：用户主动 prepare（如错误后点重试）视为脱离错误态
        hasError = false
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0)
        // M-07 修复：STATE_IDLE 下 exoPlayer.seekTo 是 no-op（media3 在 IDLE 态不响应 seek）。
        // 触发场景：onPlayerError 后 ExoPlayer 自动转入 STATE_IDLE，用户拖动进度条无效。
        // 此时先 prepare() 让 ExoPlayer 进入 BUFFERING，再 seekTo 才生效。
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        exoPlayer.seekTo(target)
        // M-07 修复：立即更新 _positionMs，避免 UI 拖动进度条后 thumb 等 500ms（轮询周期）
        // 才跳到新位置。positionTicker 仍会持续覆盖，此处只是消除 UI 响应延迟。
        _positionMs.value = target
    }

    override fun setSpeed(speed: Float) {
        // m-03 修复：clamp 到 [0.1, 8.0]，避免极端速度导致 codec 异常或无声。
        // - 下界 0.1：低于此值多数 codec 无法稳定输出帧
        // - 上界 8.0：超过此值音频重采样失真严重，且部分设备 ANR
        // clamp 后同步写入 StateFlow 与 media3（onPlaybackParametersChanged 也会回写，
        // 此处主动写入避免 UI 等 media3 回调延迟）。
        val clamped = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        _playbackSpeed.value = clamped
        applyPlaybackParameters(clamped)
    }

    override fun setPitchPreservationEnabled(enabled: Boolean) {
        _pitchPreservation.value = enabled
        // 立即以当前速度重新应用 PlaybackParameters
        applyPlaybackParameters(_playbackSpeed.value)
    }

    /**
     * 应用 PlaybackParameters（F-01 倍速音调修正）。
     *
     * - 音调保持开启：pitch=1.0，media3 Sonic 算法 time-stretching 保持原音调
     * - 音调保持关闭：pitch=speed，变速变调（类似磁带快进），适合快速浏览
     */
    private fun applyPlaybackParameters(speed: Float) {
        val pitch = if (_pitchPreservation.value) {
            1.0f
        } else {
            speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        }
        exoPlayer.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    override fun setVideoScaleMode(mode: NxVideoScaleMode) {
        _videoScaleMode.value = mode
        applyVideoScalingMode()
    }

    override fun setBlackBarCropEnabled(enabled: Boolean) {
        blackBarCropEnabled = enabled
        applyVideoScalingMode()
    }

    /**
     * 黑边裁剪覆盖标志。true 时强制用 CROPPING（用于智能黑边检测的 Fit 模式）。
     *
     * 由 [setBlackBarCropEnabled] 设置，[applyVideoScalingMode] 读取。
     * 切换 [setVideoScaleMode] 不会重置此标志——黑边检测的使能由 PlayerViewModel 管理。
     */
    private var blackBarCropEnabled = false

    /**
     * 根据当前 [videoScaleMode] 和 [blackBarCropEnabled] 计算并应用 media3 videoScalingMode。
     *
     * 优先级：blackBarCropEnabled > videoScaleMode
     * - blackBarCropEnabled=true → CROPPING（智能黑边检测在 Fit 模式下临时裁剪：
     *   surface 用 effective aspectRatio 设比例，CROPPING 让 media3 在 surface 内裁剪填满，
     *   正好裁掉视频自带的内容黑边）
     * - Crop → SCALE_TO_FIT（BUG-9 修复：原用 CROPPING 是冗余设置。
     *   Crop 模式 surface 用 videoAspect 设比例，与视频比例一致，
     *   media3 在等比例 surface 内无黑边可裁，CROPPING 与 SCALE_TO_FIT 行为相同。
     *   实际裁剪效果靠 SurfaceView 溢出父 Box + clipToBounds 实现。
     *   统一只有"Fit + 智能去黑边"使用 CROPPING，语义更清晰）
     * - Fit / Stretch / Ratio16_9 → SCALE_TO_FIT
     *   （Stretch 由 UI 层 fillMaxSize；Ratio16_9 由 UI 层强制 16:9；
     *   Fit 由 UI 层按 aspectRatio 居中）
     */
    private fun applyVideoScalingMode() {
        exoPlayer.videoScalingMode = when {
            blackBarCropEnabled -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            else -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    override fun selectAudioTrack(index: Int) {
        val currentTracks = exoPlayer.currentTracks ?: return
        val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        val builder = exoPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)

        if (index >= 0 && index < audioGroups.size) {
            val group = audioGroups[index]
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            // M-06 修复：仅当 index 在有效范围内才写入状态，避免越界 index 污染 UI 状态
            _selectedAudioTrackIndex.value = index
        } else if (index < 0) {
            // 负值（如 -1 自动选择）允许写入
            _selectedAudioTrackIndex.value = index
        }
        // index 越界（>=audioGroups.size）时不写入状态，保持上次有效值

        exoPlayer.trackSelectionParameters = builder.build()
    }

    override fun selectSubtitleTrack(index: Int) {
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        // 先清除字幕禁用状态与已有覆盖
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)

        when (index) {
            -1 -> Unit // 自动选择：不清除禁用、不加覆盖
            -2 -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) // 关闭字幕
            else -> {
                val subtitleGroups = exoPlayer.currentTracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                if (index in subtitleGroups.indices) {
                    val group = subtitleGroups[index]
                    builder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, 0),
                    )
                    // M-06 修复：仅当 index 在有效范围内才写入状态
                    _selectedSubtitleTrackIndex.value = index
                } else {
                    // 越界：不写入状态，保持上次有效值，避免 UI 显示"已选轨道 99"但实际无覆盖
                    return
                }
            }
        }
        exoPlayer.trackSelectionParameters = builder.build()
        // -1 / -2 路径在此写入状态
        if (index < 0) {
            _selectedSubtitleTrackIndex.value = index
        }
    }

    override fun setSubtitleOffsetMs(offsetMs: Long) {
        // media3 暂无 setSubtitleOffsetMs API（issue #1976 仍 Open，标记 enhancement/low priority）。
        // 当前仅记录状态供 UI 显示，实际字幕时序未改变。
        // 若需真实生效，需自定义 TextRenderer 拦截 CueGroup 并按 offset 调整 presentationTimeUs。
        _subtitleOffsetMs.value = offsetMs
    }

    override fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    override fun setLooping(looping: Boolean) {
        exoPlayer.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun attachSurface(surface: Surface?) {
        exoPlayer.setVideoSurface(surface)
    }

    override fun release() {
        // M-01 修复：先置标志位再 release ExoPlayer，positionTicker 据此短路
        isReleased = true
        // 先写 Idle 让 positionTicker 的 _state 检查短路，再 removeCallbacks，
        // 避免ticker 在 release 后访问 exoPlayer 抛 IllegalStateException
        _state.value = PlaybackState.Idle
        mainHandler.removeCallbacks(positionTicker)
        exoPlayer.removeListener(this)
        exoPlayer.release()
        // M-02 修复：重置所有 StateFlow，避免 UI 在新播放器实例初始化前看到残留数据
        _positionMs.value = 0L
        _bufferedMs.value = 0L
        _durationMs.value = 0L
        _videoSize.value = VideoSize(0, 0)
        _mediaInfo.value = null
        _cues.value = emptyList()
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _selectedAudioTrackIndex.value = -1
        _selectedSubtitleTrackIndex.value = -1
        _subtitleOffsetMs.value = 0L
        // m-03 修复：重置播放速度，避免新实例看到旧速度
        _playbackSpeed.value = 1.0f
        // F-01：重置音调保持开关为默认值
        _pitchPreservation.value = true
        // F-02：释放均衡器
        _equalizer.release()
        _networkSpeed.value = 0L
        bytesSinceLastTick = 0L
        networkIdleTicks = 0
        hdrDetectedEmitted.set(false)
        hasError = false
        // BUG-7 修复：重置缩放相关状态，避免单例场景下新调用方继承旧模式。
        // 当前 NxMedia3Player 不 @Singleton，每次 @Inject 新实例无实际影响；
        // 但保持重置完整性，防止未来改为单例时出现状态漂移。
        _videoScaleMode.value = NxVideoScaleMode.Fit
        blackBarCropEnabled = false
    }

    // endregion

    // region Player.Listener 回调

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {
                // C-01 修复：media3 在 onPlayerError 后会自动转入 STATE_IDLE，
                // 此时 hasError=true，不应覆盖刚写入的 Error 状态。
                // 仅当非错误上下文进入 IDLE 时才写 Idle（如初次 setSource 前的初始态）。
                if (!hasError) {
                    _state.value = PlaybackState.Idle
                }
            }
            Player.STATE_BUFFERING -> _state.value = PlaybackState.Buffering
            Player.STATE_READY -> {
                // READY + playWhenReady → Playing；否则 Ready/Paused 由 onIsPlayingChanged 处理
                if (!exoPlayer.playWhenReady) {
                    _state.value = PlaybackState.Ready
                }
                // 进入 READY 表示已成功缓冲，清除错误标志
                hasError = false
            }
            Player.STATE_ENDED -> _state.value = PlaybackState.Ended
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.value = when {
            isPlaying -> PlaybackState.Playing
            exoPlayer.playbackState == Player.STATE_READY -> PlaybackState.Paused
            else -> _state.value // BUFFERING / ENDED / IDLE 由 onPlaybackStateChanged 处理
        }
    }

    @Suppress("DEPRECATION")
    override fun onVideoSizeChanged(videoSize: M3VideoSize) {
        val size = VideoSize(
            width = videoSize.width,
            height = videoSize.height,
            pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
            unappliedRotationDegrees = videoSize.unappliedRotationDegrees,
        )
        _videoSize.value = size
        _events.tryEmit(PlaybackEvent.VideoSizeChanged(size))
    }

    override fun onRenderedFirstFrame() {
        _events.tryEmit(PlaybackEvent.RenderingStart)
        _mediaInfo.value?.hdrType?.let { hdrType ->
            if (hdrDetectedEmitted.compareAndSet(false, true)) {
                _events.tryEmit(PlaybackEvent.HdrDetected(hdrType))
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        // C-01 修复：置标志位，防止随后 media3 自动转入 STATE_IDLE 时覆盖 Error 状态
        hasError = true
        _state.value = PlaybackState.Error(error)
        _events.tryEmit(PlaybackEvent.Error(error))
    }

    override fun onCues(cueGroup: CueGroup) {
        _cues.value = cueGroup.cues
    }

    /**
     * m-02 修复：监听位置不连续事件（seek、跳转、playlist 切换）。
     *
     * 触发场景：
     * - 用户拖动进度条后 [seekTo] 完成
     * - AB 循环切回 A 点
     * - playlist 上下首切换
     * - 周期性 onPositionDiscontinuity（PERIOD_TRANSITION）
     *
     * 原实现仅靠 [positionTicker] 500ms 轮询更新 _positionMs，seek 后 UI 仍要等下次轮询
     * 才看到新位置（最多 500ms 延迟）。本回调在 seek 完成后立即同步 _positionMs，
     * UI 响应延迟从最多 500ms 降到回调触发时（通常 < 50ms）。
     *
     * 注意：[Player.DISCONTINUITY_REASON_AUTO_TRANSITION] / [Player.DISCONTINUITY_REASON_REMOVE]
     * 等也会触发，但同步 _positionMs 对所有 reason 都是无害的（写实际当前位置）。
     */
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        @Player.DiscontinuityReason reason: Int,
    ) {
        // 同步当前位置与缓冲位置，UI 立即响应
        _positionMs.value = newPosition.positionMs.coerceAtLeast(0)
        // windowStartTimeMs 与 contentDurationMs 等不在 PositionInfo 直接暴露，
        // 通过 exoPlayer 当前实例读取 bufferedPosition / duration
        runCatching {
            _bufferedMs.value = exoPlayer.bufferedPosition.coerceAtLeast(0)
            _durationMs.value = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0
        }
    }

    /**
     * m-02 + m-03 修复：监听 media3 内部播放参数变化，同步 _playbackSpeed。
     *
     * 触发场景：
     * - [setSpeed] 主动调用（与主动写入的值一致，无副作用）
     * - 系统音频焦点临时降速（AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK）
     * - 某些 codec 在无法支持高速时自动降速
     *
     * 不监听会导致 UI 显示的速度与 media3 实际速度不一致。
     */
    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
        _playbackSpeed.value = playbackParameters.speed
    }

    /**
     * F-02：audioSessionId 就绪后 attach 均衡器。
     *
     * ExoPlayer 创建时 audioSessionId 为 0，音频渲染器初始化后才分配有效值。
     * 此回调在 audioSessionId 变化时触发，是挂载 Equalizer 的正确时机。
     */
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        _equalizer.attach(audioSessionId)
    }

    override fun onTracksChanged(tracks: Tracks) {
        updateAudioTracks(tracks)
        updateSubtitleTracks(tracks)
        updateMediaInfo(tracks)
    }

    // endregion

    // region 音轨

    private fun updateAudioTracks(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        _audioTracks.value = audioGroups.mapIndexed { index, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val lang = format.language
            val label = buildString {
                if (!lang.isNullOrBlank()) {
                    append(languageDisplayName(lang))
                } else {
                    append("Audio ${index + 1}")
                }
                val codec = format.codecs ?: format.sampleMimeType
                if (!codec.isNullOrBlank()) {
                    append(" ($codec)")
                }
            }
            AudioTrackInfo(
                index = index,
                label = label,
                language = lang,
            )
        }
        val selectedIndex = audioGroups.indexOfFirst { it.isSelected }
        _selectedAudioTrackIndex.value = if (selectedIndex >= 0) selectedIndex else -1
    }

    private fun languageDisplayName(code: String): String {
        return try {
            val loc = Locale.forLanguageTag(code)
            val name = loc.displayLanguage
            if (name.equals(code, ignoreCase = true) || name.isBlank()) code else name
        } catch (_: IllegalStateException) {
            code
        }
    }

    // endregion

    // region 字幕轨道

    private fun updateSubtitleTracks(tracks: Tracks) {
        val subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        _subtitleTracks.value = subtitleGroups.mapIndexed { index, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val lang = format.language
            val label = buildString {
                if (!lang.isNullOrBlank()) {
                    append(languageDisplayName(lang))
                } else {
                    append(context.getString(R.string.subtitle_track_fallback, index + 1))
                }
                val codec = format.codecs ?: format.sampleMimeType
                if (!codec.isNullOrBlank()) {
                    append(" ($codec)")
                }
            }
            SubtitleTrackInfo(
                index = index,
                label = label,
                language = lang,
                isExternal = false,
            )
        }
        // 同步当前实际生效的字幕轨道（传输无关，供 UI 在「自动」模式下显示实际加载的字幕）
        val activeIndex = subtitleGroups.indexOfFirst { it.isSelected }
        _activeSubtitleTrackIndex.value = activeIndex
        // 若用户未手动选择，同步当前自动选中的轨道索引
        if (_selectedSubtitleTrackIndex.value !in -2..-1 && _selectedSubtitleTrackIndex.value !in subtitleGroups.indices) {
            _selectedSubtitleTrackIndex.value = if (activeIndex >= 0) activeIndex else -1
        }
    }

    // endregion

    // region 媒体信息

    /**
     * 从 [Tracks] 提取当前媒体技术信息（视频/音频编码、分辨率、码率、帧率、HDR）。
     *
     * 优先取已选中的轨道组；若无选中，取第一个支持的组。提取后写入 [_mediaInfo]。
     */
    private fun updateMediaInfo(tracks: Tracks) {
        val videoFormat = tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .firstOrNull { it.isSelected }
            ?.mediaTrackGroup?.getFormat(0)
            ?: tracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .firstOrNull { it.isSupported }
                ?.mediaTrackGroup?.getFormat(0)

        val audioFormat = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .firstOrNull { it.isSelected }
            ?.mediaTrackGroup?.getFormat(0)
            ?: tracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .firstOrNull { it.isSupported }
                ?.mediaTrackGroup?.getFormat(0)

        if (videoFormat == null && audioFormat == null) {
            _mediaInfo.value = null
            return
        }

        val resolution = videoFormat?.let { f ->
            if (f.width > 0 && f.height > 0) "${f.width}×${f.height}" else null
        }
        val videoBitrate = videoFormat?.bitrate?.takeIf { it > 0 }
        val audioBitrate = audioFormat?.bitrate?.takeIf { it > 0 }
        val totalBitrate = listOfNotNull(videoBitrate, audioBitrate).takeIf { it.isNotEmpty() }?.sum()

        val hdrType = videoFormat?.let { detectHdrType(it) }
        _mediaInfo.value = MediaInfo(
            videoCodec = videoFormat?.let { it.codecs ?: it.sampleMimeType },
            audioCodec = audioFormat?.let { it.codecs ?: it.sampleMimeType },
            resolution = resolution,
            bitrate = totalBitrate,
            frameRate = videoFormat?.frameRate?.takeIf { it > 0f },
            hdrType = hdrType,
        )
        // P3-3 修复：在 tracks 就绪时立即发射 HDR 事件，而不是等首帧渲染才检查。
        // 原实现在 onRenderedFirstFrame 中检查 _mediaInfo.hdrType，但 onTracksChanged
        // 可能晚于 onRenderedFirstFrame 触发（某些网络流、自适应码率场景），导致 OSD 不显示。
        if (hdrType != null && hdrDetectedEmitted.compareAndSet(false, true)) {
            _events.tryEmit(PlaybackEvent.HdrDetected(hdrType))
        }
    }

    private fun detectHdrType(format: Format): String? {
        val codec = format.codecs ?: format.sampleMimeType
        if (codec != null) {
            val lower = codec.lowercase()
            if (lower.startsWith("dv") || lower.contains("dolby")) {
                return "Dolby Vision"
            }
        }
        val colorInfo = format.colorInfo ?: return null
        return when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_HLG -> "HLG"
            C.COLOR_TRANSFER_ST2084 -> "HDR10"
            else -> null
        }
    }

    // endregion

    private companion object {
        private const val TAG = "NxMedia3Player"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L

        /**
         * m-03 修复：播放速度上下界。
         *
         * - [MIN_PLAYBACK_SPEED] 0.1f：低于此值多数 codec 无法稳定输出帧
         * - [MAX_PLAYBACK_SPEED] 8.0f：超过此值音频重采样失真严重
         */
        private const val MIN_PLAYBACK_SPEED = 0.1f
        private const val MAX_PLAYBACK_SPEED = 8.0f

        /** W-C3 修复：信任所有证书的 X509TrustManager，用于自签证书 WebDAV 播放。 */
        private val TRUST_ALL_MANAGER = object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {}

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {}

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        /** W-C3 修复：trust-all SSLContext，配套 [TRUST_ALL_MANAGER] 使用。 */
        private val TRUST_ALL_SSL: SSLContext by lazy {
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(TRUST_ALL_MANAGER), SecureRandom())
            }
        }
    }
}
