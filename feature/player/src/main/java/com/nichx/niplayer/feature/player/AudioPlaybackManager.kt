package com.nichx.niplayer.feature.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.audio.NiEqualizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频播放模式（0=顺序循环 / 1=随机 / 2=单曲循环）。
 *
 * 索引与 [PlayerSettings.audioPlayModeIndex] 对齐，保证切换与持久化一致。
 */
enum class PlayMode(val label: String) {
    Loop("顺序播放"),
    Shuffle("随机播放"),
    Single("单曲循环"),
}

@OptIn(UnstableApi::class)
@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // 专用于 storage.close() 的结构化作用域，避免每次切歌都新建游离 CoroutineScope
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentStorage: com.nichx.niplayer.storage.Storage? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow("")
    val currentArtist: StateFlow<String> = _currentArtist.asStateFlow()

    private val _audioCoverPath = MutableStateFlow<String?>(null)
    val audioCoverPath: StateFlow<String?> = _audioCoverPath.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playlist = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlist: StateFlow<List<PlaylistItem>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 当前播放模式索引，从持久化设置恢复。 */
    private val _playModeIndex = MutableStateFlow(PlayerSettings.audioPlayModeIndex)
    val playModeIndex: StateFlow<Int> = _playModeIndex.asStateFlow()

    /** 循环切换播放模式（顺序→随机→单曲→顺序），并持久化。 */
    fun cyclePlayMode() {
        val next = (_playModeIndex.value + 1) % PlayMode.entries.size
        _playModeIndex.value = next
        PlayerSettings.audioPlayModeIndex = next
    }

    private val _lrcText = MutableStateFlow<String?>(null)
    val lrcText: StateFlow<String?> = _lrcText.asStateFlow()

    private var _currentSource: NxMediaSource? = null
    val currentSource: NxMediaSource? get() = _currentSource
    private var _currentPositionMs: Long = 0L

    /**
     * 待生效的 seek 目标位置（ms）。seekTo 为异步操作，ExoPlayer 执行完成前
     * [currentPosition] 仍是旧值，轮询协程若在此期间覆盖 [_positionMs] 会导致
     * 进度条"先回旧位置再跳到位"的闪跳。此标记用于告知轮询协程暂停同步。
     */
    private var _pendingSeekMs: Long? = null

    private var lastCoverBitmap: Bitmap? = null

    /** playNext/playPrevious 触发切歌时通知外部。外部监听者（PlayerViewModel）收到后执行实际 source 切换。 */
    var onPlayNextRequest: (() -> Unit)? = null
    var onPlayPreviousRequest: (() -> Unit)? = null

    /** 播放错误时通知外部。外部监听者（PlayerViewModel）收到后通过 messageEvent 展示给用户。 */
    var onPlaybackError: ((String) -> Unit)? = null

    /** 当前播放错误信息，null 表示无错误。UI 层据此显示错误状态。 */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    /** 均衡器实例（F-02），在 audioSessionId 就绪后自动 attach。 */
    private val equalizer = NiEqualizer()

    /** 均衡器 enabled 切换时的淡出/淡入任务，重复调用时取消前一个，避免并发 fade 竞争。 */
    private var eqFadeJob: Job? = null

    /** 暴露均衡器供 UI 读取频段信息 / 应用设置。 */
    fun getEqualizer(): NiEqualizer = equalizer

    /**
     * 应用均衡器设置（开关切换路径）。
     *
     * 关闭均衡器的爆响根源是 [android.media.audiofx.Equalizer] 的 enabled 切换触发
     * AudioFlinger 重建效果链（硬件 DSP 旁路），该爆响在效果链输出层面，无法靠音量
     * 静音消除——已由 [NiEqualizer.applySettings] 内部"拉平增益而非 disable"解决。
     * 此处再包一层淡出/淡入作为兜底，吸收切换瞬间可能残留的微小瞬态。
     */
    fun applyEqualizerSettings() {
        eqFadeJob?.cancel()
        val player = exoPlayer ?: run {
            equalizer.applySettings()
            return
        }
        eqFadeJob = scope.launch {
            val originalVolume = try { player.volume } catch (_: IllegalStateException) { return@launch }
            val wasPlaying = try { player.isPlaying } catch (_: IllegalStateException) { return@launch }
            var fadedOut = false
            if (wasPlaying && originalVolume > 0f) {
                fadeVolume(player, originalVolume, 0f)
                fadedOut = true
            }
            equalizer.applySettings()
            if (fadedOut && originalVolume > 0f) {
                fadeVolume(player, 0f, originalVolume)
            }
        }
    }

    /**
     * 实时应用均衡器参数（滑块拖动 / 预设点击路径）。
     *
     * 仅修改频段增益等参数，不触发效果链重建，直接应用无需静音包装；
     * 若对每次拖动都做淡入淡出，会打断音乐造成明显卡顿。
     */
    fun applyEqualizerLive() {
        equalizer.applySettings()
    }

    /** 将 [player] 音量从 [from] 平滑过渡到 [to]（约 100ms，10 步 × 10ms）。 */
    private suspend fun fadeVolume(player: ExoPlayer, from: Float, to: Float) {
        if (from == to) return
        for (step in 1..EQ_FADE_STEPS) {
            try {
                player.volume = from + (to - from) * (step / EQ_FADE_STEPS.toFloat())
            } catch (_: IllegalStateException) {
                // 播放器已释放，中止淡入淡出
                return
            }
            delay(EQ_FADE_STEP_DELAY_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val player = exoPlayer ?: return
                    val dur = player.duration
                    if (dur > 0 && dur != C.TIME_UNSET) {
                        _durationMs.value = dur
                    }
                }
                Player.STATE_ENDED -> {
                    _positionMs.value = 0L
                    _isPlaying.value = false
                    scope.launch {
                        // 播放完毕按播放模式处理：单曲循环重播当前，顺序/随机进入下一首
                        if (_currentIndex.value >= 0 && _playlist.value.isNotEmpty()) {
                            when (PlayMode.entries[_playModeIndex.value]) {
                                PlayMode.Single -> {
                                    // 单曲循环：重播当前曲目（seek 后需恢复播放）
                                    seekTo(0L)
                                    exoPlayer?.playWhenReady = true
                                }
                                PlayMode.Shuffle,
                                PlayMode.Loop -> playNext()
                            }
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizer.attach(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaMetadata?.let { meta ->
                meta.title?.toString()?.takeIf { it.isNotEmpty() }?.let {
                    _currentTitle.value = it
                }
                meta.artist?.toString()?.takeIf { it.isNotEmpty() }?.let {
                    _currentArtist.value = it
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _isPlaying.value = false
            // 尝试解析 HTTP 错误码
            val httpCode = extractHttpStatusCode(error)
            val msg = if (httpCode != null) {
                when (httpCode) {
                    401 -> "播放失败：账号密码错误或凭据过期（401）"
                    403 -> "播放失败：无访问权限（403）"
                    404 -> "播放失败：文件不存在，可能已被移动或删除（404）"
                    in 500..599 -> "播放失败：服务器错误（$httpCode）"
                    else -> "播放失败：HTTP $httpCode"
                }
            } else {
                val causeMsg = error.message ?: error::class.simpleName ?: "未知错误"
                when (val cause = error.cause) {
                    is java.io.FileNotFoundException -> "播放失败：文件不存在，可能已被移动或删除"
                    is java.io.IOException -> "播放失败：网络错误（$causeMsg）"
                    else -> "播放失败：$causeMsg"
                }
            }
            _playbackError.value = msg
            onPlaybackError?.invoke(msg)
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(playerListener)
        exoPlayer = player

        scope.launch {
            while (isActive) {
                val p = exoPlayer ?: break
                val current = p.currentPosition.coerceAtLeast(0)
                val pending = _pendingSeekMs
                if (pending != null) {
                    // seek 尚未生效：currentPosition 可能仍是旧值。一旦追上目标位置即视为完成。
                    if (current >= pending) {
                        _pendingSeekMs = null
                        _positionMs.value = current
                    }
                    // 若 seek 朝后（current 仍 < pending），继续保持 pending，不覆盖 UI 位置
                } else if (p.isPlaying) {
                    _positionMs.value = current
                }
                _currentPositionMs = current
                delay(1000)
            }
        }
        return player
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun play(
        source: NxMediaSource,
        title: String,
        coverPath: String?,
        artist: String = "",
        startPositionMs: Long = 0L,
        playlist: List<PlaylistItem> = emptyList(),
        startIndex: Int = -1,
    ) {
        val player = ensurePlayer()

        _currentTitle.value = title
        _currentArtist.value = artist
        _audioCoverPath.value = coverPath
        // 在 IO 线程预解码封面 Bitmap 并缓存，避免通知刷新时在主线程重复解码
        decodeCoverAsync(coverPath)
        _positionMs.value = startPositionMs
        _durationMs.value = 0L
        _playlist.value = playlist
        _currentIndex.value = startIndex
        // 清除上次的播放错误，让 UI 恢复正常显示
        _playbackError.value = null
        // 切歌时清除残留的 seek 标记，避免影响新曲目的进度显示
        _pendingSeekMs = null

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifEmpty { null })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(source.mediaId.ifEmpty { source.uri.toString() })
            .setUri(source.uri)
            .setMediaMetadata(metadata)
            .build()

        val mediaSource = when (source) {
            is NxMediaSource.Http -> {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setKeepPostFor302Redirects(false)
                source.headers.forEach { (key, value) ->
                    dataSourceFactory.setDefaultRequestProperties(mapOf(key to value))
                }
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            is NxMediaSource.Local -> {
                val dataSourceFactory = DefaultDataSource.Factory(context)
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            is NxMediaSource.DataSource -> {
                ProgressiveMediaSource.Factory(source.factory)
                    .createMediaSource(mediaItem)
            }
        }

        _currentSource = source

        closeStorageAsync()
        currentStorage = (source as? NxMediaSource.DataSource)?.storage

        player.stop()
        player.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0))
        player.prepare()
        player.play()

        startService()
    }

    /**
     * 更新播放列表与当前索引（不重启播放器）。
     *
     * BUG-H2 修复：首页英雄卡/播放历史恢复经 PlayStarter 异步构造同目录列表，
     * 可能晚于 [play] 调用（SMB/WebDAV listFiles 耗时 1-3 秒）。列表就绪后调用
     * 本方法同步，使「下一首/上一首」与播放列表面板立即可用。
     */
    fun updatePlaylist(items: List<PlaylistItem>, startIndex: Int) {
        if (items.isEmpty() || startIndex !in items.indices) return
        _playlist.value = items
        _currentIndex.value = startIndex
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val target = positionMs.coerceAtLeast(0)
        // 先标记 pending 并立即更新 UI 位置，避免轮询协程用旧的 currentPosition 覆盖导致闪跳
        _pendingSeekMs = target
        _positionMs.value = target
        player.seekTo(target)
    }

    /**
     * 计算下一首索引。依据播放模式：
     * - 顺序循环：末位回到 0
     * - 随机：随机挑选，避免与当前相同（列表 > 1 时）
     * - 单曲循环：返回当前索引（保持重播）
     */
    fun nextIndex(current: Int): Int {
        val size = _playlist.value.size
        if (size == 0) return -1
        return when (PlayMode.entries[_playModeIndex.value]) {
            PlayMode.Loop -> if (current >= size - 1) 0 else current + 1
            PlayMode.Single -> current.coerceIn(0, size - 1)
            PlayMode.Shuffle -> {
                if (size <= 1) current.coerceIn(0, size - 1)
                else {
                    var next = kotlin.random.Random.nextInt(size)
                    while (next == current) {
                        next = kotlin.random.Random.nextInt(size)
                    }
                    next
                }
            }
        }
    }

    /** 计算上一首索引（仅顺序模式支持，随机/单曲循环同样向前回退一位）。 */
    fun previousIndex(current: Int): Int {
        val size = _playlist.value.size
        if (size == 0) return -1
        return if (current <= 0) size - 1 else current - 1
    }

    /** 请求切换到下一首。仅触发回调，实际索引计算与 source 切换由 PlayerViewModel.playNext 完成。 */
    fun requestNext() {
        if (_playlist.value.isEmpty()) return
        onPlayNextRequest?.invoke()
    }

    /** 请求切换到上一首。仅触发回调，实际索引计算与 source 切换由 PlayerViewModel.playPrevious 完成。 */
    fun requestPrevious() {
        if (_playlist.value.isEmpty()) return
        onPlayPreviousRequest?.invoke()
    }

    /** 播放下一首。更新索引并通知外部进行实际切换。 */
    fun playNext() {
        requestNext()
    }

    /** 播放上一首。更新索引并通知外部进行实际切换。 */
    fun playPrevious() {
        requestPrevious()
    }

    fun setLrcText(text: String?) {
        _lrcText.value = text
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun stopPlayback() {
        exoPlayer?.stop()
        _pendingSeekMs = null
        _positionMs.value = 0L
        _isPlaying.value = false
        _currentSource = null
        _currentTitle.value = ""
        _currentArtist.value = ""
        _audioCoverPath.value = null
        setCoverBitmap(null)
        _playlist.value = emptyList()
        _currentIndex.value = -1
        _lrcText.value = null
        _playbackError.value = null
        closeStorageAsync()
        stopService()
    }

    fun release() {
        stopService()
        closeStorageAsync()
        // F-02 爆音修复：取消进行中的均衡器淡入淡出，避免访问已释放的播放器
        eqFadeJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        equalizer.release()
    }

    fun setCoverBitmap(bitmap: Bitmap?) {
        lastCoverBitmap = bitmap
    }

    fun getCoverBitmap(): Bitmap? = lastCoverBitmap

    fun updateCoverPath(path: String?) {
        _audioCoverPath.value = path
        // 封面路径变化时在 IO 线程预解码并缓存，避免通知刷新在主线程解码
        decodeCoverAsync(path)
    }

    /**
     * 在 IO 线程解码封面文件并缓存到 [lastCoverBitmap]。
     *
     * 通知每次刷新（状态变化）都需封面 Bitmap，原实现由 AudioPlaybackService 在主线程
     * BitmapFactory.decodeFile，远程文件较大时造成卡顿。改为切歌时预解码一次，后续刷新直接复用缓存。
     */
    private fun decodeCoverAsync(path: String?) {
        scope.launch(Dispatchers.IO) {
            val bitmap = if (path.isNullOrBlank()) {
                null
            } else {
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            }
            setCoverBitmap(bitmap)
        }
    }

    /** 重新播放当前源（播放失败后重试）。 */
    fun retry() {
        val source = _currentSource ?: return
        play(
            source = source,
            title = _currentTitle.value,
            coverPath = _audioCoverPath.value,
            artist = _currentArtist.value,
            startPositionMs = _currentPositionMs,
            playlist = _playlist.value,
            startIndex = _currentIndex.value,
        )
    }

    private fun startService() {
        val intent = Intent(context, AudioPlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopService() {
        AudioPlaybackService.stopService()
    }

    private fun closeStorageAsync() {
        val storage = currentStorage ?: return
        currentStorage = null
        closeScope.launch {
            try { storage.close() } catch (_: Exception) {}
        }
    }

    private companion object {
        private const val TAG = "AudioPlaybackManager"

        /** F-02 爆音修复：均衡器淡入淡出步数（10 步 ≈ 100ms）。 */
        private const val EQ_FADE_STEPS = 10

        /** F-02 爆音修复：均衡器淡入淡出每步间隔（ms）。 */
        private const val EQ_FADE_STEP_DELAY_MS = 10L

        /**
         * 从异常链中提取 HTTP 状态码。
         *
         * media3 的 [androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException]
         * 或其变体在抛出时携带 responseCode 字段，通过反射递归查找。
         */
        private fun extractHttpStatusCode(error: Throwable, depth: Int = 0): Int? {
            if (depth > 5) return null
            // 检查 media3 HttpDataSource 异常
            try {
                val cls = Class.forName("androidx.media3.datasource.HttpDataSource\$InvalidResponseCodeException")
                if (cls.isInstance(error)) {
                    val field = cls.getDeclaredField("responseCode")
                    field.isAccessible = true
                    return (field.get(error) as? Int)?.takeIf { it > 0 }
                }
            } catch (_: Exception) {}
            // 递归检查 cause
            val cause = error.cause ?: return null
            return extractHttpStatusCode(cause, depth + 1)
        }
    }
}
