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

    private val _lrcText = MutableStateFlow<String?>(null)
    val lrcText: StateFlow<String?> = _lrcText.asStateFlow()

    private var _currentSource: NxMediaSource? = null
    val currentSource: NxMediaSource? get() = _currentSource
    private var _currentPositionMs: Long = 0L

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
     * 应用均衡器设置（F-02 爆音修复）。
     *
     * 直接切换 [android.media.audiofx.Equalizer] 的 enabled 会立即改变 DSP 处理路径
     * （滤波 → 旁路），播放中信号在非零交叉点被硬切断，产生一声爆响。此处先淡出
     * 播放音量到 0，切换后再淡入恢复原音量，切换发生在静音时刻，从而消除爆音。
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
                        // 播放完毕自动下一首
                        if (_currentIndex.value >= 0 && _playlist.value.isNotEmpty()) {
                            playNext()
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
                if (p.isPlaying) {
                    _positionMs.value = p.currentPosition.coerceAtLeast(0)
                }
                _currentPositionMs = exoPlayer?.currentPosition?.coerceAtLeast(0) ?: 0L
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
        exoPlayer?.seekTo(positionMs.coerceAtLeast(0))
    }

    /** 请求切换到下一首。只更新索引，实际 source 切换由外部（PlayerViewModel）处理。 */
    fun requestNext() {
        val list = _playlist.value
        val nextIndex = _currentIndex.value + 1
        if (list.isEmpty() || nextIndex >= list.size) return
        _currentIndex.value = nextIndex
        onPlayNextRequest?.invoke()
    }

    /** 请求切换到上一首。只更新索引，实际 source 切换由外部（PlayerViewModel）处理。 */
    fun requestPrevious() {
        val list = _playlist.value
        val prevIndex = _currentIndex.value - 1
        if (list.isEmpty() || prevIndex < 0) return
        _currentIndex.value = prevIndex
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
