package com.nichx.niplayer.feature.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nichx.niplayer.datastore.OnlineMatchBlacklist
import com.nichx.niplayer.datastore.OnlineMatchCache
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.resumeStartPositionMs
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.database.security.EncryptedFolderManager
import com.nichx.niplayer.player.kernel.HistoryDescriptor
import com.nichx.niplayer.player.kernel.MediaSourceBuilder
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.audio.NiEqualizer
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频播放模式（0=顺序循环 / 1=随机 / 2=单曲循环）。
 *
 * 索引与 [PlayerSettings.audioPlayModeIndex] 对齐，保证切换与持久化一致。
 */
enum class PlayMode(@StringRes val labelRes: Int) {
    Loop(R.string.player_play_mode_order),
    Shuffle(R.string.player_play_mode_shuffle),
    Single(R.string.player_play_mode_single),
}

@OptIn(UnstableApi::class)
@Singleton
class AudioPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
    private val playHistoryDao: PlayHistoryDao,
    private val encryptedFolderManager: EncryptedFolderManager,
    private val thumbnailManager: ThumbnailManager,
    private val musicMetadataService: MusicMetadataService,
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

    /** 当前播放倍速，UI 可订阅显示；由 [setPlaybackSpeed] 修改，切歌后保持。 */
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /**
     * 设置播放倍速。ExoPlayer 对音频/视频均原生支持变速，切歌（重新
     * setMediaSource）后速度保持，无需每次切歌重新应用。
     */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceAtLeast(0.25f)
        _playbackSpeed.value = clamped
        applyPlaybackParameters(clamped)
    }

    /**
     * 设置音调保持（变速不变调），并实时持久化到 [PlayerSettings]。
     *
     * BUG 修复：Manager 是 @Singleton，若把设置值缓存进字段，切换后到进程重启前都不会
     * 更新，导致“倍速音调保持”设置对音频不生效。这里统一读写 [PlayerSettings]，
     * [applyPlaybackParameters] 也读取实时值，保证设置即时生效。
     */
    fun setPitchPreservation(enabled: Boolean) {
        PlayerSettings.pitchPreservationEnabled = enabled
        applyPlaybackParameters(_playbackSpeed.value)
    }

    /** 应用倍速与音调参数（pitch=1 保持音调；pitch=speed 变速变调）。 */
    private fun applyPlaybackParameters(speed: Float) {
        val pitch = if (PlayerSettings.pitchPreservationEnabled) 1.0f else speed
        exoPlayer?.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    private val _lrcText = MutableStateFlow<String?>(null)
    val lrcText: StateFlow<String?> = _lrcText.asStateFlow()

    /** 提示类消息回调（如"已通过 API 获取歌词"），UI 层注册后转为 Snackbar。 */
    var onMessage: ((String) -> Unit)? = null

    /**
     * 为当前曲目异步加载 LRC 歌词，结果写入 [lrcText]。
     * 优先级：同目录 .lrc（远程走 Storage 流，本地走 File）→ 应用内匹配缓存 → lrcapi 兜底。
     * 与封面一致，挂在 Manager 常驻协程上，不依赖 UI 层存活，切歌即加载。
     *
     * lrcapi 兜底受三重门控：设置开关 → 在线匹配黑名单（文件/目录级）→
     * 有声书识别（时长门控 + 文件名启发式，防止大量误匹配）。匹配缓存（含手动
     * 重新匹配的结果）不受开关与黑名单影响，始终优先展示。
     *
     * @param force 手动「重新匹配」时为 true：跳过缓存读取与黑名单/有声书门控，
     * 强制再从 API 获取一次，用于误判时的人工兜底。
     */
    fun loadLrcForCurrentSong(force: Boolean = false) {
        val history = currentHistory ?: return
        val storageId = history.storageId
        val filePath = history.storagePath ?: return
        val matchKey = matchKeyFor(history, filePath)
        if (force) OnlineMatchCache.removeLrc(context, matchKey)
        scope.launch(Dispatchers.IO) {
            try {
                val fileName = filePath.substringAfterLast('/')
                val nameWithoutExt = fileName.substringBeforeLast('.')
                val dirPath = filePath.substringBeforeLast('/')
                val lrcFilePath = if (dirPath == filePath) {
                    "$nameWithoutExt.lrc"
                } else {
                    "$dirPath/$nameWithoutExt.lrc"
                }

                // 优先级1: 同目录本地/远程 LRC 文件
                if (nameWithoutExt.isNotEmpty() && nameWithoutExt != fileName) {
                    if (storageId != null) {
                        // Remote storage (SMB/WebDAV): read LRC via Storage.openInputStream
                        val library = mediaLibraryDao.getById(storageId) ?: return@launch
                        val storage = storageFactory.create(library) ?: return@launch
                        try {
                            val lrcFile = MediaSourceBuilder.createVirtualFile(lrcFilePath, "$nameWithoutExt.lrc")
                            storage.openInputStream(lrcFile)?.use { input ->
                                val text = input.bufferedReader().readText()
                                if (text.isNotBlank()) {
                                    _lrcText.value = text
                                    return@launch
                                }
                            }
                        } finally {
                            // 取消后普通 suspend 调用会立刻抛 CancellationException，清理必须在 NonCancellable 中执行
                            withContext(NonCancellable) { storage.close() }
                        }
                    } else {
                        // Local file: try direct File access
                        val lrcFile = File(lrcFilePath)
                        if (lrcFile.exists()) {
                            val text = lrcFile.readText()
                            _lrcText.value = text
                            return@launch
                        }
                    }
                }

                // 优先级2: 应用内匹配缓存（此前 API 匹配成功 / 手动重新匹配的结果）。
                // force（重新匹配）时跳过缓存，直接从 API 再取。
                if (!force) {
                    OnlineMatchCache.readLrc(context, matchKey)?.let { cached ->
                        _lrcText.value = cached
                        return@launch
                    }
                }

                // 优先级3: lrcapi 远程获取（统一准入：黑名单 + 有声书启发式 + 时长门控）
                // force 时绕过门控，允许人工强制重试。
                if (musicMetadataService.isLyricsEnabled() &&
                    nameWithoutExt.isNotEmpty() &&
                    shouldFetchOnline(
                        OnlineMatchBlacklist.Kind.LYRICS,
                        matchKey,
                        fileName,
                        force,
                    )
                ) {
                    val (matchTitle, matchArtist) = parseTitleArtist(nameWithoutExt)
                    val localFilePath = if (filePath.startsWith("/")) filePath else ""
                    val result = musicMetadataService.fetchLyrics(
                        title = matchTitle,
                        artist = matchArtist,
                        path = localFilePath,
                    )
                    val content = result.getOrNull()
                    if (result.isSuccess && !content.isNullOrBlank()) {
                        OnlineMatchCache.saveLrc(context, matchKey, content)
                        _lrcText.value = content
                        android.util.Log.i(TAG, "从API加载歌词成功: $nameWithoutExt, 长度: ${content.length}")
                        onMessage?.invoke(context.getString(R.string.player_lyrics_fetched, nameWithoutExt))
                        return@launch
                    } else {
                        android.util.Log.w(TAG, "从API加载歌词失败: ${result.exceptionOrNull()?.message}")
                    }
                }

                _lrcText.value = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _lrcText.value = null
            }
        }
    }

    /** 在线匹配键：远程/媒体库文件 `sid:<storageId>:<path>`，本地文件 `local:<uri>`。 */
    private fun matchKeyFor(history: HistoryDescriptor, pathOverride: String? = null): String {
        val path = pathOverride ?: history.storagePath ?: history.url
        return if (history.storageId != null) {
            "sid:${history.storageId}:$path"
        } else {
            "local:${(_currentSource as? NxMediaSource.Local)?.uri?.toString() ?: path}"
        }
    }

    /**
     * 等待当前曲目时长就绪（STATE_READY 后 [durationMs] 才有值）。
     * 超时返回 0，按未知处理：不再把未知时长当作短歌放行有声书（由文件名启发式兜底），
     * 但也不阻断正常音乐的匹配，时长未知仅在已知超阈值时才跳过。
     */
    private suspend fun awaitDurationForMatch(): Long =
        withTimeoutOrNull(MATCH_DURATION_WAIT_MS) { durationMs.first { it > 0 } } ?: 0L

    /**
     * 统一的在线匹配准入判断（歌词 / 封面共用）：
     * 只有「未进对应黑名单 && 非有声书（文件名启发式） &&（时长未知或未超长阈值）」才允许调 API。
     * force（手动强制）时直接放行，绕过黑名单与有声书门控，用于人工兜底。
     */
    private suspend fun shouldFetchOnline(
        kind: OnlineMatchBlacklist.Kind,
        matchKey: String,
        fileName: String,
        force: Boolean,
    ): Boolean {
        if (force) return true
        if (OnlineMatchBlacklist.isSkipped(kind, matchKey)) return false
        if (isLikelyAudiobook(fileName)) return false
        val trackDurationMs = awaitDurationForMatch()
        return trackDurationMs == 0L || trackDurationMs <= AUDIOBOOK_LIKE_DURATION_MS
    }

    /** 当前曲目在线匹配键，null 表示无当前曲目。供「清除/重新匹配」等手动纠正使用。 */
    private fun currentMatchKey(): String? {
        val history = currentHistory ?: return null
        val path = history.storagePath ?: return null
        return matchKeyFor(history, path)
    }

    /**
     * 清除并忽略当前曲目歌词：删除该曲在线歌词缓存 + 加入歌词与封面黑名单 + 立即清空显示。
     * 用于 API 匹配错误时手动兜底，命中则会黑名单自动拦截该曲（含目录级）。
     */
    fun clearIgnoreCurrentLyrics() {
        val matchKey = currentMatchKey() ?: return
        // 歌词：清缓存 + 入黑名单
        OnlineMatchCache.removeLrc(context, matchKey)
        OnlineMatchBlacklist.skip(OnlineMatchBlacklist.Kind.LYRICS, matchKey)
        _lrcText.value = null
        // 封面：删 API 封面缓存 + 入 COVER 黑名单 + 清空显示（避免有声书封面继续被错误匹配）
        val apiCoverFile = File(context.cacheDir, "audio_covers/api_${md5(matchKey)}.jpg")
        apiCoverFile.delete()
        OnlineMatchBlacklist.skip(OnlineMatchBlacklist.Kind.COVER, matchKey)
        val cover = _audioCoverPath.value
        if (cover != null && cover.contains("audio_covers/api_")) {
            _audioCoverPath.value = null
            updateCoverPath(null)
            setCoverBitmap(null)
        }
        onMessage?.invoke(context.getString(R.string.player_lyrics_ignored))
    }

    /**
     * 重新匹配当前曲目（歌词与封面）：清除缓存并绕过黑名单/有声书门控，强制再从 API
     * 获取一次。用于有声书启发式误判或 API 修复后的手动重试。
     */
    fun forceRematchLyrics() {
        loadLrcForCurrentSong(force = true)
        // 同时强制重新获取封面（绕过 COVER 黑名单/有声书门控）
        forceRematchCurrentCover()
    }

    /** 强制重新获取当前曲目封面（绕过 COVER 黑名单/有声书门控），用于「重新匹配」。 */
    private fun forceRematchCurrentCover() {
        val history = currentHistory ?: return
        val filePath = history.storagePath ?: return
        val fileName = filePath.substringAfterLast('/')
        val matchKey = matchKeyFor(history, filePath)
        // 先清掉可能存在的旧 API 封面缓存，再强制重取
        val oldCover = File(context.cacheDir, "audio_covers/api_${md5(matchKey)}.jpg")
        oldCover.delete()
        fetchAudioCoverFromApi(fileName, matchKey, force = true)
    }

    /**
     * 手动匹配当前曲目（歌词与封面）：用用户显式输入的歌名/歌手作为查询词调用 API，
     * 绕过文件名解析、黑名单与有声书门控，结果写入该曲在线匹配缓存并立即展示。
     * 用于文件命名不规范导致自动解析 / 匹配出错时的人工精确兜底。
     */
    fun manualMatchLyrics(title: String, artist: String) {
        val history = currentHistory ?: return
        val filePath = history.storagePath ?: return
        val matchKey = matchKeyFor(history, filePath)
        OnlineMatchCache.removeLrc(context, matchKey)
        val trimmedTitle = title.trim()
        val trimmedArtist = artist.trim()
        // 封面同样用输入词手动匹配（绕过 COVER 门控），与歌词一并精确兜底
        val fileName = filePath.substringAfterLast('/')
        fetchAudioCoverFromApi(
            fileName = fileName,
            matchKey = matchKey,
            force = true,
            titleOverride = trimmedTitle,
            artistOverride = trimmedArtist,
        )
        scope.launch(Dispatchers.IO) {
            try {
                val result = musicMetadataService.fetchLyrics(
                    title = trimmedTitle,
                    artist = trimmedArtist,
                    path = if (filePath.startsWith("/")) filePath else "",
                )
                val content = result.getOrNull()
                if (result.isSuccess && !content.isNullOrBlank()) {
                    OnlineMatchCache.saveLrc(context, matchKey, content)
                    _lrcText.value = content
                    onMessage?.invoke(context.getString(R.string.player_lyrics_manual_done, trimmedTitle))
                } else {
                    onMessage?.invoke(context.getString(R.string.player_lyrics_manual_failed))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onMessage?.invoke(context.getString(R.string.player_lyrics_manual_failed))
            }
        }
    }

    /**
     * 文件名启发式：判断是否疑似有声书/播客等长音频，是则跳过在线歌词匹配。
     * 规则偏保守，强关键词直接判定；章节标记需「非 歌手-歌名」结构才判定，降低误伤
     * 正常歌曲。若误判，可用「重新匹配」手动强制获取。
     */
    private fun isLikelyAudiobook(fileName: String): Boolean {
        val lower = fileName.lowercase()
        val strongKeywords = listOf(
            "有声书", "有声小说", "audio book", "audiobook", "播客", "podcast",
            "相声", "评书", "脱口秀", "广播剧", "小说连播",
        )
        if (strongKeywords.any { lower.contains(it) }) return true
        // 章节 / 分集标记：`第X章/回/集` 或 `chapter/episode/season N`
        val chapterLike = Regex(
            "第[0-9零一二两三四五六七八九十百千]*[章节回集卷话课部分]",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(fileName) ||
            Regex("\\b(chapter|episode|season)\\s*\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(fileName)
        if (chapterLike) {
            // 「歌手 - 歌名」结构一般是音乐，不判为有声书（守护正常歌曲触发识别）
            return !fileName.contains(" - ")
        }
        return false
    }

    /**
     * 从文件名拆分「歌手 - 歌曲名」供在线匹配使用。
     * 约定：以最后一个 ` - ` 为分隔符，其后为歌名，其前为歌手。
     * 无数组合（含歌手名本身带 ` - `）或只有单个「歌手-歌名」块时，整名作为歌名、歌手留空，
     * 交由 lrcapi 自行解析，避免按整名搜索因带括号/后缀片段导致误配。
     */
    private fun parseTitleArtist(nameWithoutExt: String): Pair<String, String> {
        val idx = nameWithoutExt.lastIndexOf(" - ")
        return if (idx > 0) {
            val title = nameWithoutExt.substring(idx + 3).trim()
            val artist = nameWithoutExt.substring(0, idx).trim()
            Pair(title.ifEmpty { nameWithoutExt.trim() }, artist)
        } else {
            Pair(nameWithoutExt.trim(), "")
        }
    }

    private var _currentSource: NxMediaSource? = null
    val currentSource: NxMediaSource? get() = _currentSource

    /** 当前是否为本地文件（已下载/缓存直链）来源，UI 据此隐藏下载按钮。 */
    private val _isLocalSource = MutableStateFlow(false)
    val isLocalSource: StateFlow<Boolean> = _isLocalSource.asStateFlow()
    private var _currentPositionMs: Long = 0L

    /**
     * 待生效的 seek 目标位置（ms）。seekTo 为异步操作，ExoPlayer 执行完成前
     * [currentPosition] 仍是旧值，轮询协程若在此期间覆盖 [_positionMs] 会导致
     * 进度条"先回旧位置再跳到位"的闪跳。此标记用于告知轮询协程暂停同步。
     */
    private var _pendingSeekMs: Long? = null

    /** seekTo 调用时刻（elapsedRealtime），轮询协程据此等待 seek 生效静默期。 */
    private var _pendingSeekSetAt: Long = 0L

    /** 网络类播放错误自动重试次数（成功播放后清零）。 */
    private var autoRetryCount = 0

    private var lastCoverBitmap: Bitmap? = null

    /** 封面提取协程 Job，切歌时取消旧的避免旧封面覆盖新封面。 */
    private var audioCoverJob: Job? = null

    /** 播放错误时通知外部。外部监听者（PlayerViewModel）收到后通过 messageEvent 展示给用户。 */
    var onPlaybackError: ((String) -> Unit)? = null

    /**
     * 切歌成功（[switchToIndex] 完成）时通知外部，参数为新曲目的历史描述符。
     * 外部监听者（PlayerViewModel）据此刷新封面 / LRC 等 UI 状态。
     */
    var onTrackChanged: ((HistoryDescriptor) -> Unit)? = null

    /** 当前播放曲目的历史描述符，Manager 自维护（切歌时更新，供进度保存使用）。 */
    @Volatile
    var currentHistory: HistoryDescriptor? = null

    /** 串行化切歌全流程，避免快速连续切歌时进度保存与状态覆盖竞争。 */
    private val switchMutex = Mutex()

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
                    // 曲目播放完毕先保存最终进度（_currentPositionMs 为最后一次轮询的实际位置，
                    // 约等于时长；极短曲目可能未及轮询，退化为用时长作为最终位置），再清零 UI 位置，
                    // 最后按播放模式切换下一首。原实现靠外部回调保存进度，UI 层销毁后进度会丢失。
                    val endedDuration = _durationMs.value
                    val endedPosition = _currentPositionMs.takeIf { it > 0 } ?: endedDuration
                    _positionMs.value = 0L
                    _isPlaying.value = false
                    if (endedPosition > 0) {
                        saveFinalProgress(endedPosition, endedDuration)
                    }
                    scope.launch {
                        // 播放完毕按播放模式处理
                        if (_currentIndex.value >= 0 && _playlist.value.isNotEmpty()) {
                            when (PlayMode.entries[_playModeIndex.value]) {
                                PlayMode.Single -> {
                                    seekTo(0L)
                                    exoPlayer?.playWhenReady = true
                                }
                                PlayMode.Shuffle, PlayMode.Loop -> playNext()
                            }
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            // 成功恢复播放：重置自动重试计数，供下次网络故障再次使用
            if (isPlaying) autoRetryCount = 0
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
                    401 -> context.getString(R.string.player_play_failed_401)
                    403 -> context.getString(R.string.player_play_failed_403)
                    404 -> context.getString(R.string.player_play_failed_404)
                    in 500..599 -> context.getString(R.string.player_play_failed_server, httpCode)
                    else -> context.getString(R.string.player_play_failed_http, httpCode)
                }
            } else {
                val causeMsg = error.message ?: error::class.simpleName ?: context.getString(R.string.player_unknown_error)
                when (error.cause) {
                    is java.io.FileNotFoundException -> context.getString(R.string.player_play_failed_file_not_found)
                    is java.io.IOException -> context.getString(R.string.player_play_failed_network, causeMsg)
                    else -> context.getString(R.string.player_play_failed_generic, causeMsg)
                }
            }
            _playbackError.value = msg
            onPlaybackError?.invoke(msg)

            // 自动重试：仅对网络/IO 类可恢复错误（SMB/WebDAV 临时断网、连接超时等），
            // 有限次数 + 指数退避（1s/2s/4s），确定性错误（401/403/404/5xx）不重试。
            if (shouldAutoRetry(error)) {
                if (autoRetryCount < MAX_AUTO_RETRY) {
                    autoRetryCount++
                    val backoffMs = AUTO_RETRY_BASE_MS shl (autoRetryCount - 1)
                    android.util.Log.w(TAG, "播放失败，${autoRetryCount}/$MAX_AUTO_RETRY 次自动重试，${backoffMs}ms 后重试")
                    scope.launch {
                        delay(backoffMs)
                        retry()
                    }
                } else {
                    // 达到上限：本次会话不再自动重试，等待用户手动操作（重试按钮/切歌）
                    autoRetryCount = 0
                }
            }
        }
    }

    /** 判断播放错误是否值得自动重试（网络/IO 类可恢复错误，排除 HTTP 确定性错误）。 */
    private fun shouldAutoRetry(error: androidx.media3.common.PlaybackException): Boolean {
        // 有明确 HTTP 响应码（401/403/404/5xx 等）属确定性错误，重试无意义
        if (extractHttpStatusCode(error) != null) return false
        // 通过 errorCode 精确识别网络/超时类错误，避免对解码/格式错误盲目重试
        return when (error.errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT -> true
            else -> false
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // 与视频侧对齐：变速播放时默认保持音调不变
        val restoredSpeed = PlayerSettings.audioSpeedIndex.let { idx ->
            AudioPlaybackSpeedValues.getOrNull(idx) ?: 1f
        }
        player.setPlaybackParameters(
            PlaybackParameters(
                restoredSpeed,
                if (PlayerSettings.pitchPreservationEnabled) 1.0f else restoredSpeed,
            ),
        )
        _playbackSpeed.value = restoredSpeed
        player.addListener(playerListener)
        exoPlayer = player

        scope.launch {
            // 周期保存调度：首次 5s 保存快照，之后每 30s 一次。
            // 该逻辑挂在 Manager 的常驻协程上，不依赖 UI 层（PlayerViewModel）存活，
            // MusicBar 场景 / 进程被杀前也能定期落盘当前曲目进度。
            var playedSeconds = 0
            var nextSaveAt = PROGRESS_FIRST_SAVE_DELAY_S
            while (isActive) {
                val p = exoPlayer ?: break
                val current = p.currentPosition.coerceAtLeast(0)
                val pending = _pendingSeekMs
                if (pending != null) {
                    // seek 生效静默期（250ms）：期间不覆盖 UI 位置，等待 seek 实际生效。
                    // 否则 seek 朝后（目标 < 当前）时旧位置仍 >= 目标，pending 会被过早
                    // 清除并用旧值覆盖 UI，造成"先回旧位置再跳到位"的闪跳。
                    val seekAge = SystemClock.elapsedRealtime() - _pendingSeekSetAt
                    if (seekAge >= SEEK_SETTLE_MS && current >= pending) {
                        _pendingSeekMs = null
                        _positionMs.value = current
                    }
                } else if (p.isPlaying) {
                    _positionMs.value = current
                }
                _currentPositionMs = current

                // 周期保存：仅播放中保存（避免 STATE_ENDED 后 position=0 覆盖进度）
                playedSeconds++
                if (playedSeconds >= nextSaveAt) {
                    nextSaveAt = playedSeconds + PROGRESS_SAVE_INTERVAL_S
                    if (p.isPlaying) {
                        runCatching { saveCurrentProgress() }.onFailure { e ->
                            android.util.Log.w(TAG, "周期性保存进度失败: ${e.message}", e)
                        }
                    }
                }
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
        history: HistoryDescriptor? = null,
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
        // 同步当前曲目的历史描述符（切歌/首播均在此更新，供进度保存使用）
        currentHistory = history
        // 先落 _currentSource：loadLocalAudioCover() 在 refreshAudioCover 内同步读取它，
        // 若此时仍是上一曲/首播的 null，本地封面提取会直接 return（本地音频无封面 bug）
        _currentSource = source
        // 异步提取新曲目封面（缓存命中立即替换，未命中则 Storage/API 提取；
        // 提取期间保留上一曲封面，避免切歌瞬间闪烁空白）
        if (history != null) {
            refreshAudioCover(history)
            // 异步加载同目录/远程歌词（与封面一致，不依赖 UI 层存活）
            loadLrcForCurrentSong()
        }
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

        _isLocalSource.value = source is NxMediaSource.Local

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
        _pendingSeekSetAt = SystemClock.elapsedRealtime()
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

    /**
     * 切换到指定索引的曲目（音频全流程：保存旧进度 → 查库 → 重建 Storage → 建 Source →
     * 查续播位置 → 播放 → 记录历史 → 通知 UI 刷新封面/LRC）。
     *
     * 该能力下沉自 PlayerViewModel（原 playAtIndex 音频分支），使切歌不再依赖 UI 层存活，
     * MusicBar / 通知栏 / 全屏页统一走此处。失败返回 false 且不中断当前播放。
     */
    suspend fun switchToIndex(index: Int): Boolean {
        val list = _playlist.value
        if (index !in list.indices) return false
        val item = list[index]
        return switchMutex.withLock {
            try {
                // 切歌前先保存当前曲目进度（position=0 时内部跳过，避免覆盖）
                saveCurrentProgress()

                val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(item.libraryId) }
                    ?: return@withLock false
                val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
                    ?: return@withLock false
                val file = MediaSourceBuilder.createVirtualFile(item.filePath, item.fileName, item.fileSize)
                val uniqueKey = "${library.id}:${item.filePath}"
                // W-N7：以 uniqueKey 作为 mediaId，与应用层唯一键一致，便于 MediaSession 集成
                val source = MediaSourceBuilder.buildMediaSource(storage, file, mediaId = uniqueKey)
                val startPositionMs = withContext(Dispatchers.IO) {
                    playHistoryDao.getPlayHistory(uniqueKey, library.id)?.resumeStartPositionMs() ?: 0L
                }

                val newHistory = HistoryDescriptor(
                    uniqueKey = uniqueKey,
                    url = item.filePath,
                    mediaTypeValue = item.mediaTypeValue,
                    storageId = library.id,
                    storagePath = item.filePath,
                    fileSize = item.fileSize,
                    playlistId = currentHistory?.playlistId,
                )
                play(
                    source = source,
                    title = item.fileName,
                    coverPath = _audioCoverPath.value,
                    artist = item.fileName,
                    startPositionMs = startPositionMs,
                    playlist = _playlist.value,
                    startIndex = index,
                    history = newHistory,
                )
                recordPlayStart(newHistory, item.fileName, startPositionMs)
                onTrackChanged?.invoke(newHistory)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "switchToIndex($index) failed", e)
                onPlaybackError?.invoke(context.getString(R.string.player_switch_failed, e.message ?: e::class.simpleName))
                false
            }
        }
    }

    /** 播放下一首（按当前播放模式计算索引，内部直接切歌，不依赖外部回调）。 */
    fun playNext() {
        if (_playlist.value.isEmpty()) return
        scope.launch {
            // 索引在 Mutex 排队执行时计算，连点下一首时每次读取最新 currentIndex，
            // 保证快速连点 3 次依次切到 1、2、3 而非都基于调用时刻的旧索引
            val index = nextIndex(_currentIndex.value)
            if (index >= 0) switchToIndex(index)
        }
    }

    /** 播放上一首（向前回退一位，内部直接切歌，不依赖外部回调）。 */
    fun playPrevious() {
        if (_playlist.value.isEmpty()) return
        scope.launch {
            // 与 playNext 一致：索引在排队执行时计算，避免连点回退时跳位
            val index = previousIndex(_currentIndex.value)
            if (index >= 0) switchToIndex(index)
        }
    }

    /** 是否有活跃的音频会话（列表非空且当前曲目有效）。 */
    fun hasActiveAudio(): Boolean = _currentTitle.value.isNotEmpty() && _playlist.value.isNotEmpty()

    /** 是否还存在下一首（通知栏「下一曲」按钮可用性）。 */
    fun hasNextInPlaylist(): Boolean {
        val size = _playlist.value.size
        if (size == 0) return false
        return when (PlayMode.entries[_playModeIndex.value]) {
            PlayMode.Single -> true
            PlayMode.Loop, PlayMode.Shuffle -> size > 1
        }
    }

    /** 是否还存在上一首（通知栏「上一曲」按钮可用性）。 */
    fun hasPreviousInPlaylist(): Boolean = _playlist.value.size > 1

    /** 同步保存当前播放进度（切歌前 / UI 退出时调用）。position=0 时跳过，避免覆盖已有进度。 */
    suspend fun saveCurrentProgress() {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        val position = _positionMs.value
        if (position <= 0) return
        saveProgressInternal(history, storageId, position, _durationMs.value)
    }

    /** 曲目播放结束（STATE_ENDED）时保存最终进度。 */
    private fun saveFinalProgress(positionMs: Long, durationMs: Long) {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        if (positionMs <= 0) return
        scope.launch {
            saveProgressInternal(history, storageId, positionMs, durationMs)
        }
    }

    /**
     * 进度落盘内部实现（upsert），由 [saveCurrentProgress] / [saveFinalProgress] 复用。
     *
     * 用 PlayHistoryDao.upsertProgress（@Transaction 包裹 query+update/insert），Room 在
     * 数据库层加事务锁串行化，确保与记录历史的并发安全。
     */
    private suspend fun saveProgressInternal(
        history: HistoryDescriptor,
        storageId: Int,
        position: Long,
        duration: Long,
    ) {
        // 文件夹访问加密：加密目录内的文件不写入播放历史（含进度）
        if (encryptedFolderManager.isWithinEncrypted(storageId, history.storagePath)) return
        withContext(Dispatchers.IO + NonCancellable) {
            val mediaType = MediaType.fromValue(history.mediaTypeValue)
            val title = history.url.substringAfterLast('/')
            val newEntity = PlayHistoryEntity(
                videoName = title,
                url = history.url,
                mediaType = mediaType,
                videoPosition = position,
                videoDuration = duration,
                playTime = Date(),
                uniqueKey = history.uniqueKey,
                storagePath = history.storagePath,
                storageId = storageId,
                httpHeader = history.httpHeader,
            )
            playHistoryDao.upsertProgress(
                uniqueKey = history.uniqueKey,
                storageId = storageId,
                newPosition = position,
                newDuration = duration,
                newPlayTime = newEntity.playTime,
                newEntity = newEntity,
            )
        }
    }

    /** 记录新曲目开始播放（切歌后调用），写入 play_history 作为历史来源。 */
    private suspend fun recordPlayStart(
        history: HistoryDescriptor,
        title: String,
        startPositionMs: Long,
    ) {
        val storageId = history.storageId ?: return
        // 文件夹访问加密：加密目录内的文件不写入播放历史
        if (encryptedFolderManager.isWithinEncrypted(storageId, history.storagePath)) return
        val mediaType = MediaType.fromValue(history.mediaTypeValue)
        val now = Date()
        val newEntity = PlayHistoryEntity(
            videoName = title,
            url = history.url,
            mediaType = mediaType,
            videoPosition = startPositionMs,
            playTime = now,
            uniqueKey = history.uniqueKey,
            storagePath = history.storagePath,
            storageId = storageId,
            httpHeader = history.httpHeader,
            playlistId = history.playlistId,
        )
        playHistoryDao.upsertPlayStart(
            uniqueKey = history.uniqueKey,
            storageId = storageId,
            newPlayTime = now,
            newEntity = newEntity,
        )
    }

    /**
     * 为指定曲目异步提取专辑封面，结果通过 [_audioCoverPath] 暴露。
     *
     * 封面提取从 PlayerViewModel 下沉到 Manager：MusicBar / 全屏页 / 通知栏
     * 统一订阅 [_audioCoverPath]，无论 UI 层（PlayerViewModel）是否存活，
     * 切歌后封面都能同步更新。优先级：本地缓存 → Storage 提取 → API 兜底。
     */
    private fun refreshAudioCover(history: HistoryDescriptor) {
        // 受总开关与音频封面开关双重控制
        if (!ThumbnailSettings.generateThumbnail || !ThumbnailSettings.generateForAudio) {
            _audioCoverPath.value = null
            setCoverBitmap(null)
            return
        }
        val sid = history.storageId
        if (sid != null) {
            // 播放后生成策略检查：关闭模式不提取封面
            if (!ThumbnailSettings.shouldGenerateOnPlayback(sid)) {
                _audioCoverPath.value = null
                setCoverBitmap(null)
                return
            }
            loadStorageAudioCover(history, sid)
            return
        }
        // 无 storageId（本地直链/下载缓存），走 MediaMetadataRetriever 本地提取
        loadLocalAudioCover()
    }

    /** 通过 Storage 抽象层提取音频封面（SMB/WebDAV/ExternalStorage）。 */
    private fun loadStorageAudioCover(history: HistoryDescriptor, sid: Int) {
        val filePath = history.storagePath ?: return
        val fileName = history.url.substringAfterLast('/')

        // 先检查本地缓存，命中则直接返回，避免创建不必要的 Storage 连接
        val cachedPath = thumbnailManager.getCachedAudioCoverPath(sid, filePath)
        if (cachedPath != null) {
            _audioCoverPath.value = cachedPath
            updateCoverPath(cachedPath)
            return
        }

        // 取消旧的封面生成协程，避免快速切歌时旧协程完成后覆盖新歌封面
        audioCoverJob?.cancel()
        audioCoverJob = scope.launch(Dispatchers.IO) {
            try {
                val library = mediaLibraryDao.getById(sid) ?: return@launch
                val storage = storageFactory.create(library) ?: return@launch
                try {
                    val file = MediaSourceBuilder.createVirtualFile(filePath, fileName)
                    var loaded = false
                    thumbnailManager.preloadAudioCovers(storage, sid, listOf(file)) { _, coverPath ->
                        _audioCoverPath.value = coverPath
                        updateCoverPath(coverPath)
                        loaded = true
                    }
                    if (!loaded) {
                        val path = thumbnailManager.generateAudioCover(storage, sid, file)
                        if (path != null) {
                            thumbnailManager.uploadAudioCover(storage, file)
                        }
                        if (path != null) {
                            _audioCoverPath.value = path
                            updateCoverPath(path)
                        } else {
                            // 本地封面提取失败，尝试从 API 获取（含有声书/黑名单准入）
                            fetchAudioCoverFromApi(fileName, "sid:$sid:$filePath")
                        }
                    }
                } finally {
                    // 取消后普通 suspend 调用会立刻抛 CancellationException，清理必须在 NonCancellable 中执行
                    withContext(NonCancellable) { storage.close() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _audioCoverPath.value = null
                updateCoverPath(null)
            }
        }
    }

    /** 本地文件（下载缓存/SAF content://）直接通过 MediaMetadataRetriever 提取封面。 */
    private fun loadLocalAudioCover() {
        val source = _currentSource as? NxMediaSource.Local ?: return
        audioCoverJob?.cancel()
        audioCoverJob = scope.launch(Dispatchers.IO) {
            try {
                val uri = source.uri
                val cacheKey = "local_audio_${md5(uri.toString())}"
                val cacheFile = File(context.cacheDir, "audio_covers/$cacheKey.jpg")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val path = cacheFile.absolutePath
                    _audioCoverPath.value = path
                    updateCoverPath(path)
                    return@launch
                }
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val pictureData = retriever.embeddedPicture
                    if (pictureData != null) {
                        val bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.size)
                        if (bitmap != null) {
                            cacheFile.parentFile?.mkdirs()
                            FileOutputStream(cacheFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            val path = cacheFile.absolutePath
                            _audioCoverPath.value = path
                            updateCoverPath(path)
                        }
                    } else {
                        // 本地无嵌入封面，尝试从 API 获取（含有声书/黑名单准入）
                        val fileName = source.uri.pathSegments.lastOrNull() ?: ""
                        if (fileName.isNotEmpty()) {
                            fetchAudioCoverFromApi(fileName, "local:${source.uri}")
                        } else {
                            _audioCoverPath.value = null
                            updateCoverPath(null)
                        }
                    }
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _audioCoverPath.value = null
                updateCoverPath(null)
            }
        }
    }

    /** 从 lrcapi 获取封面并缓存到本地（本地封面提取失败时的兜底）。 */
    private fun fetchAudioCoverFromApi(
        fileName: String,
        matchKey: String,
        force: Boolean = false,
        titleOverride: String = "",
        artistOverride: String = "",
    ) {
        // 与歌词共用统一准入：封面开关 + COVER 黑名单 + 有声书启发式 + 时长门控
        if (!musicMetadataService.isCoverEnabled()) return
        val title = titleOverride.ifBlank { fileName.substringBeforeLast('.').ifEmpty { fileName } }
        scope.launch(Dispatchers.IO) {
            try {
                if (!shouldFetchOnline(
                        OnlineMatchBlacklist.Kind.COVER,
                        matchKey,
                        fileName,
                        force,
                    )
                ) {
                    return@launch
                }
                val result = musicMetadataService.fetchCover(
                    title = title,
                    artist = artistOverride,
                )
                if (result.isSuccess) {
                    val coverBytes = result.getOrNull()
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        val coverDir = File(context.cacheDir, "audio_covers")
                        if (!coverDir.exists()) coverDir.mkdirs()
                        val coverFile = File(coverDir, "api_${md5(matchKey)}.jpg")
                        coverFile.writeBytes(coverBytes)
                        val path = coverFile.absolutePath
                        _audioCoverPath.value = path
                        updateCoverPath(path)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    /** 字符串 MD5 哈希，用于本地缓存文件名。 */
    private fun md5(input: String): String {
        try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray())
            // Locale.ROOT：十六进制摘要必须与区域无关
            return bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
        } catch (_: Exception) {
            return input.hashCode().toUInt().toString(16)
        }
    }

    /** 睡眠定时剩余秒数，null = 未启用。音频退出页面后（MusicBar 后台播放）仍生效。 */
    private val _sleepTimerRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerRemaining: StateFlow<Int?> = _sleepTimerRemaining.asStateFlow()

    private var sleepTimerJob: Job? = null

    /**
     * 启动睡眠定时：倒计时归零后自动暂停播放。
     *
     * 挂在 Manager 常驻协程上，不依赖 UI 层（PlayerViewModel / AudioPlayerScreen）存活，
     * 切到 MusicBar / 关闭页面后定时仍生效。
     */
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = minutes * 60
        sleepTimerJob = scope.launch {
            val deadlineMs = System.currentTimeMillis() + minutes * 60_000L
            while (true) {
                val remainMs = deadlineMs - System.currentTimeMillis()
                if (remainMs <= 0) break
                _sleepTimerRemaining.value = ((remainMs + 999) / 1000).toInt()
                delay(1000)
            }
            pausePlayback()
            _sleepTimerRemaining.value = null
        }
    }

    /** 取消睡眠定时。 */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun stopPlayback() {
        exoPlayer?.stop()
        _pendingSeekMs = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _currentSource = null
        _isLocalSource.value = false
        _currentTitle.value = ""
        _currentArtist.value = ""
        _audioCoverPath.value = null
        setCoverBitmap(null)
        _playlist.value = emptyList()
        _currentIndex.value = -1
        currentHistory = null
        _lrcText.value = null
        _playbackError.value = null
        // 播放停止后睡眠定时失去意义，一并清除，避免残留定时暂停后续会话
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
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
            // 保留历史描述符：play() 内部 currentHistory = history，缺省会置 null，
            // 导致重试后切歌/周期保存全部失效
            history = currentHistory,
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

    companion object {
        private const val TAG = "AudioPlaybackManager"

        /** F-02 爆音修复：均衡器淡入淡出步数（10 步 ≈ 100ms）。 */
        private const val EQ_FADE_STEPS = 10

        /** F-02 爆音修复：均衡器淡入淡出每步间隔（ms）。 */
        private const val EQ_FADE_STEP_DELAY_MS = 10L

        /** seek 生效静默期（ms）：seekTo 后此窗口内轮询协程不覆盖 UI 位置。 */
        private const val SEEK_SETTLE_MS = 250L

        /** 网络类错误自动重试最大次数。 */
        private const val MAX_AUTO_RETRY = 3

        /** 自动重试初始退避（ms），每次翻倍：1s、2s、4s。 */
        private const val AUTO_RETRY_BASE_MS = 1000L

        /** 周期保存：首次保存延迟（s），进入播放后尽快落盘初始进度快照。 */
        private const val PROGRESS_FIRST_SAVE_DELAY_S = 5

        /** 周期保存：后续保存间隔（s）。 */
        private const val PROGRESS_SAVE_INTERVAL_S = 30

        /** 在线歌词匹配等待时长就绪的超时（ms）。超过则认为时长未知，不阻断匹配。 */
        private const val MATCH_DURATION_WAIT_MS = 2000L

        /**
         * 疑似有声书时长阈值（ms）。超过此值时按歌曲名在线搜索歌词必然错配，
         * 直接跳过在线匹配。取 15 分钟，覆盖绝大多数歌曲曲长。
         */
        private const val AUDIOBOOK_LIKE_DURATION_MS = 15 * 60 * 1000L

        /**
         * 音频播放倍速档位（0.5x / 1.0x / 1.5x / 2.0x 四档）。
         *
         * 音频独立于视频的 8 档（SPEED_VALUES），并通过
         * PlayerSettings.audioSpeedIndex 单独持久化选择结果。
         */
        val AudioPlaybackSpeedValues = listOf(0.5f, 1.0f, 1.5f, 2.0f)

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
