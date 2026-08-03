package com.nichx.niplayer.feature.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.text.Cue
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.VideoBookmarkDao
import com.nichx.niplayer.database.security.EncryptedFolderManager
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.player.kernel.AudioTrackInfo
import com.nichx.niplayer.player.kernel.HistoryDescriptor
import com.nichx.niplayer.player.kernel.MediaInfo
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.MediaSourceBuilder
import com.nichx.niplayer.player.kernel.NxPlayer
import com.nichx.niplayer.player.kernel.NxVideoScaleMode
import com.nichx.niplayer.player.kernel.PlaybackEvent
import com.nichx.niplayer.player.kernel.PlaybackRequest
import com.nichx.niplayer.player.kernel.PlaybackRequestHolder
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.isAudioFile
import com.nichx.niplayer.player.kernel.PlaylistHolder
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo
import com.nichx.niplayer.player.kernel.VideoSize
import com.nichx.niplayer.common.coroutine.AppCoroutineScope
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.subtitle.format.FormatASS
import com.nichx.niplayer.sync.PlayHistorySyncManager
import com.nichx.niplayer.thumbnail.ThumbnailManager
import com.nichx.niplayer.subtitle.format.FormatSRT
import com.nichx.niplayer.subtitle.renderer.SubtitleColor
import com.nichx.niplayer.subtitle.renderer.SubtitleEngine
import com.nichx.niplayer.subtitle.renderer.SubtitleStyleConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import javax.inject.Inject

/**
 * 播放器屏幕 ViewModel。
 *
 * 从 [PlaybackRequestHolder] 消费 [PlaybackRequest]（由文件浏览页 :feature:home 构造并写入），
 * 自动加载播放源。替代阶段3 验证版的测试视频 URL。
 *
 * 生命周期：
 * - init 时 [PlaybackRequestHolder.consume] 取出请求（取出即清空，避免跨会话残留）
 * - 有请求时 setSource → seekTo（续播）→ prepare → play
 * - 无请求时（如直接进入播放路由而无待播放源）进入 [PlaybackState.Idle]，UI 显示提示
 * - [onCleared] 释放 [NxPlayer]，并保存最终播放进度到 play_history 表
 *
 * 播放历史记录（P1）：
 * - init 时若 [PlaybackRequest.history] 非空，写入/更新 play_history（标记开始播放）
 * - [onCleared] 时用独立 [CoroutineScope(Dispatchers.IO)] 异步保存
 *   最终 videoPosition / videoDuration / playTime（NonCancellable 保护进度写入），
 *   缩略图生成用 withTimeoutOrNull 限时 10s 避免阻塞 IO 线程
 * - 当前仅支持 [HistoryDescriptor.storageId] 非空的场景（存储库播放）；本地/直链播放
 *   （storageId=null）的历史记录待 LocalStorage 实现后补充
 *
 * @param player 由 [com.nichx.niplayer.player.kernel.di.PlayerModule] 绑定，
 *               实际实现为 [com.nichx.niplayer.player.kernel.media3.NxMedia3Player]
 * @param playbackRequestHolder 跨模块传递播放请求的 @Singleton 持有者
 * @param playHistoryDao 播放历史 Dao，记录/更新播放进度
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val player: NxPlayer,
    playbackRequestHolder: PlaybackRequestHolder,
    private val playHistoryDao: PlayHistoryDao,
    private val videoBookmarkDao: VideoBookmarkDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
    private val playlistHolder: PlaylistHolder,
    private val thumbnailManager: ThumbnailManager,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val downloadManager: com.nichx.niplayer.storage.download.DownloadManager,
    private val musicMetadataService: MusicMetadataService,
    private val appScope: AppCoroutineScope,
    private val encryptedFolderManager: EncryptedFolderManager,
    private val syncManager: PlayHistorySyncManager,
) : ViewModel() {

    /** 播放状态（WhileSubscribed(5000) 避免短暂配置变化导致 Player 释放）。 */
    val state: StateFlow<PlaybackState> = player.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackState.Idle)

    /** 当前播放位置（ms）。 */
    val positionMs: StateFlow<Long> = player.positionMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 总时长（ms）。 */
    val durationMs: StateFlow<Long> = player.durationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 已缓冲位置（ms）。 */
    val bufferedMs: StateFlow<Long> = player.bufferedMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 当前视频尺寸。 */
    val videoSize: StateFlow<VideoSize> = player.videoSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VideoSize(0, 0))

    /**
     * 智能黑边检测后的有效视频尺寸。
     *
     * - 未检测 / 检测失败 / 功能关闭时：与 [videoSize] 一致
     * - 检测成功时：[VideoSize.width]/[VideoSize.height] 为去除黑边后的有效画面像素，
     *   [VideoSize.aspectRatio] 为真实内容宽高比，供 UI 层 Fit 模式计算 SurfaceView 尺寸
     *
     * 仅在 Fit 模式下由 UI 层订阅使用；Crop/Stretch 不受影响。
     * 检测由 UI 层在首帧后抓图触发（[applyBlackBarDetection]），结果在 ViewModel 持久化。
     */
    private val _effectiveVideoSize = MutableStateFlow<VideoSize?>(null)
    val effectiveVideoSize: StateFlow<VideoSize?> = _effectiveVideoSize.asStateFlow()

    /**
     * 退出播放时通过 PixelCopy 截取的最后一帧 Bitmap。
     *
     * 由 UI 层在返回导航前通过 [setLastFrameBitmap] 设置，
     * [onCleared] 中优先使用此 Bitmap 保存为缩略图（对 SMB/WebDAV 更可靠），
     * 失败时回退到 [ThumbnailManager.generateThumbnailAtMs]。
     *
     * M-08 修复：加 @Volatile，UI 主线程写入与 onCleared 的 IO 协程读取保证可见性。
     */
    @Volatile
    private var lastFrameBitmap: Bitmap? = null

    /**
     * 当前播放是否为音频。
     *
     * 由 [PlaybackRequest.isAudio] 在 init 中设置，[onCleared] 据此跳过视频缩略图生成
     * （音频文件无视频帧，[MediaMetadataRetriever.getFrameAtTime] 必然返回 null，
     * 且对 SMB/WebDAV 远程音频会建立 MediaDataSource 并阻塞数秒）。
     *
     * M-08 修复：加 @Volatile，init 主线程写入与 onCleared IO 协程读取保证可见性。
     */
    @Volatile
    private var isAudioPlayback: Boolean = false

    /**
     * onCleared 跳过 player.release 标志位。
     *
     * bridgeToBackgroundPlayback 启动前置 true，onCleared 检查此值决定是否释放
     * 前台 player。NonCancellable 协程完成后置 false。
     * 防止 onCleared → player.release() 在后台播放器就绪前中断音频输出。
     */
    @Volatile
    private var transitioningToBackground = false

    /** 设置退出播放时的最后一帧 Bitmap，由 UI 层调用。 */
    fun setLastFrameBitmap(bitmap: Bitmap?) {
        lastFrameBitmap = bitmap
    }

    /**
     * 退出播放时是否需要截取最后一帧作为缩略图。
     *
     * 与 [onCleared] 中缩略图生成条件对齐（总开关 + 视频开关 + 生成策略门控
     * [ThumbnailSettings.shouldGenerateOnPlayback]），供 UI 层在返回导航前决定
     * 是否执行 PixelCopy 抓帧，避免"关闭"策略下无谓的 SurfaceView 截图与 bitmap 分配。
     *
     * HDR 播放（Dolby Vision / HDR10 / HLG）跳过抓帧：SurfaceView 表面是 10-bit
     * HDR buffer，PixelCopy 抓取在部分设备上返回损坏数据（白屏 + 品红块），
     * 由 [onCleared] 走 [ThumbnailManager.generateThumbnailAtMs]（getFrameAtTime +
     * 系统/软件 tone map）生成正确色彩。
     *
     * storageId 为 null（如本地文件）时返回 false，与 [onCleared] 生成条件一致
     * （无存储源不生成缩略图）。
     */
    fun shouldCaptureThumbnailOnExit(): Boolean {
        if (isAudioPlayback) return false
        if (!ThumbnailSettings.generateThumbnail || !ThumbnailSettings.generateForVideo) return false
        val sid = currentHistory?.storageId ?: return false
        if (!ThumbnailSettings.shouldGenerateOnPlayback(sid)) return false
        if (player.mediaInfo.value?.hdrType != null) return false
        return true
    }

    /** 当前播放媒体技术信息（编码/分辨率/码率/帧率/HDR）。未准备好时为 null。 */
    val mediaInfo: StateFlow<MediaInfo?> = player.mediaInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 当前字幕渲染数据（[Cue] 列表）。空列表表示无字幕。 */
    val cues: StateFlow<List<Cue>> = player.cues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 播放标题（文件名），用于顶栏显示。无播放源时为空。 */
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    /** 暴露 [NxPlayer] 供 UI 层 SurfaceView 挂载渲染。 */
    val nxPlayer: NxPlayer get() = player

    /** 当前缩放模式索引（0:1:2 对应 [SCALE_MODES] 适应/裁剪/拉伸）。 */
    private val _scaleIndex = MutableStateFlow(0)
    val scaleIndex: StateFlow<Int> = _scaleIndex.asStateFlow()

    /** 当前可用音频轨道列表。 */
    val audioTracks: StateFlow<List<AudioTrackInfo>> = player.audioTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的音频轨道索引，-1 表示自动选择。 */
    val selectedAudioTrackIndex: StateFlow<Int> = player.selectedAudioTrackIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    /** 当前可用字幕轨道列表（含内嵌与外挂）。 */
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = player.subtitleTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的字幕轨道索引。-1 自动；-2 关闭。 */
    val selectedSubtitleTrackIndex: StateFlow<Int> = player.selectedSubtitleTrackIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    /** 当前字幕延迟（ms），正数延后，负数提前。 */
    val subtitleOffsetMs: StateFlow<Long> = player.subtitleOffsetMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** 当前网络下载速度（B/s）。对应 [NxPlayer.networkSpeed]。 */
    private val _networkSpeed = MutableStateFlow(0L)
    val networkSpeed: StateFlow<Long> = _networkSpeed.asStateFlow()

    /** 音频专辑封面本地缓存路径，null 表示无封面/尚未提取/非音频。 */
    private val _audioCoverPath = MutableStateFlow<String?>(null)
    val audioCoverPath: StateFlow<String?> = _audioCoverPath.asStateFlow()

    /** N-001 修复：音频封面异步生成 Job，用于切歌时取消旧协程，避免封面错乱。 */
    private var audioCoverJob: Job? = null

    /** LRC 歌词原始文本内容，null 表示未找到歌词。 */
    private val _lrcText = MutableStateFlow<String?>(null)
    val lrcText: StateFlow<String?> = _lrcText.asStateFlow()

    /**
     * 外挂字幕渲染引擎。
     *
     * 替代 media3 TextRenderer 处理外挂字幕（ASS/SSA/SRT）：
     * - ASS 特效：通过 [SubtitleEngine] + [com.nichx.niplayer.subtitle.renderer.AssOverrideParser]
     *   解析 override tags（颜色/字体/位置/淡入淡出/移动），由 [com.nichx.niplayer.feature.player.SubtitleOverlay] 渲染
     * - 字幕偏移：[SubtitleEngine.update] 查询时使用 `positionMs + offsetMs`，正负偏移都精确生效
     *   （替代 media3 无原生 setSubtitleOffsetMs API 的缺陷，issue #1976 仍 Open）
     *
     * 内嵌字幕仍走 media3 TextRenderer → SubtitleView（[cues] StateFlow）。
     */
    val subtitleEngine: SubtitleEngine = SubtitleEngine(
        textSizeFactor = SubtitleSettings.textSizeFraction,
    ).apply {
        // 初始化注入用户样式配置（描边宽度/阴影/颜色/applyEmbeddedStyles）
        updateStyleConfig(buildSubtitleStyleConfig())
    }

    /**
     * 从 [SubtitleSettings] 构造 [SubtitleStyleConfig] 注入 [subtitleEngine]。
     *
     * 在 [refreshSubtitleStyle] 时再次调用以应用用户改设置后的最新值。
     */
    private fun buildSubtitleStyleConfig(): SubtitleStyleConfig = SubtitleStyleConfig(
        outlineWidth = SubtitleSettings.outlineWidth,
        shadowDepth = SubtitleSettings.outlineWidth.coerceAtLeast(0f).let {
            // 阴影深度跟随描边宽度：描边越粗阴影越深（保持视觉一致），但描边为 0 时阴影也归零
            if (SubtitleSettings.outlineWidth <= 0f) 0f else it
        },
        applyEmbeddedStyles = SubtitleSettings.applyEmbeddedStyles,
        primaryColor = SubtitleColor.fromArgb(SubtitleSettings.fontColor),
        outlineColor = SubtitleColor.fromArgb(SubtitleSettings.outlineColor),
    )

    /**
     * 刷新字幕样式配置（用户从设置页返回播放器时调用）。
     *
     * MMKV 不是响应式的，设置页修改不会自动通知；调用此方法重新读取 [SubtitleSettings]
     * 并通过 [SubtitleEngine.updateStyleConfig] 应用，下次 [SubtitleEngine.update] 即生效。
     */
    fun refreshSubtitleStyle() {
        subtitleEngine.updateStyleConfig(buildSubtitleStyleConfig())
    }

    /**
     * 当前播放请求的历史描述符，onCleared 时用于定位并更新 play_history 记录。
     *
     * M-08 修复：加 @Volatile，init 主线程 / playAtIndex 协程 / onCleared IO 协程
     * 多上下文读写保证可见性。配合 [playAtIndexMutex] 防止快速切歌竞态。
     */
    @Volatile
    private var currentHistory: HistoryDescriptor? = null

    /** 当前视频的书签 key（uniqueKey + storageId），用于响应式查询书签列表（F-19）。 */
    private val _currentBookmarkKey = MutableStateFlow<Pair<String, Int?>?>(null)

    /** 当前视频的书签列表，按位置升序。UI 据此在进度条上显示标记（F-19）。 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<VideoBookmarkEntity>> = _currentBookmarkKey
        .flatMapLatest { key ->
            if (key != null) {
                videoBookmarkDao.getBookmarksFlow(key.first, key.second)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 最近一次播放请求（C-02 修复：错误后重试需要）。
     *
     * 持有原始 [PlaybackRequest] 副本，错误状态下用户点"重试"/"从头播放"时复用：
     * - 重试：用原 [PlaybackRequest.startPositionMs] 重新 setSource/prepare/play
     * - 从头播放：将 startPositionMs 改为 0 重新 setSource/prepare/play
     */
    @Volatile
    private var lastPlaybackRequest: PlaybackRequest? = null

    // 专用于 storage.close() 的结构化作用域，避免每次切歌/退出都新建游离 CoroutineScope
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * M-10 修复：保护 playAtIndex 全流程的互斥锁。
     *
     * 原实现入口同步读取 _playlist/_currentIndex 后启动协程，协程内先 saveProgressSync（保存当前曲目）
     * 再覆盖 currentHistory（指向新曲目）。用户快速连续调用 playAtIndex 两次时：
     * - 第一次 saveProgressSync 尚未完成
     * - 第二次已覆盖 currentHistory
     * - 第一次保存的是错误曲目的进度
     *
     * withLock 串行化整个 playAtIndex 流程，确保前一次切歌完全结束（含进度保存）后才开始下一次。
     */
    private val playAtIndexMutex = kotlinx.coroutines.sync.Mutex()
    // 注：保留全限定名避免顶部再加一行 import Mutex；withLock 已 import

    /**
     * 当前播放源持有的 Storage 实例（仅 SMB/DocumentFile 等需要 DataSource 注入的协议）。
     *
     * BUG-19+23 修复：playAtIndex / PlayStarter（经 PlaybackRequest.source）创建的
     * Storage 现在随 NxMediaSource.DataSource 一并传递到此，由本 ViewModel 统一管理：
     * - 切换源（[playAtIndex] / setSource）前关闭旧 storage
     * - [onCleared] 中关闭当前 storage
     * - HTTP/Local 类型 source 不携带 storage（为 null），无需关闭
     */
    private var currentStorage: com.nichx.niplayer.storage.Storage? = null

    /** 播放列表（同目录视频文件），空列表表示无连播。 */
    private val _playlist = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlist: StateFlow<List<PlaylistItem>> = _playlist.asStateFlow()

    /** 当前播放项在播放列表中的索引，-1 表示不在列表中（无连播）。 */
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 播放列表信息「1/12」格式，空列表时为空字符串。 */
    val playlistInfo: StateFlow<String> = combine(_playlist, _currentIndex) { list, index ->
        if (list.isEmpty() || index < 0) "" else "${index + 1}/${list.size}"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // region 长按倍速

    /** 长按画面时显示的倍速（松手恢复），null = 未处于长按态。 */
    private val _longPressSpeedActive = MutableStateFlow<Float?>(null)
    val longPressSpeedActive: StateFlow<Float?> = _longPressSpeedActive.asStateFlow()

    /**
     * 长按倍速是否已锁定。
     *
     * 长按拖动到屏幕底部后松手即锁定：倍速保持，不随松手恢复。
     * 锁定后点击 OSD 或切换倍速可解除。
     */
    private val _longPressSpeedLocked = MutableStateFlow(false)
    val longPressSpeedLocked: StateFlow<Boolean> = _longPressSpeedLocked.asStateFlow()

    /** 长按倍速设置值（持久化在 PlayerSettings，UI 通过此属性读写设置面板）。 */
    var longPressSpeed: Float
        get() = PlayerSettings.longPressSpeed
        set(value) { PlayerSettings.longPressSpeed = value }

    /** 倍速音调保持开关（F-01），持久化在 PlayerSettings。 */
    val pitchPreservation: StateFlow<Boolean> = player.pitchPreservation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 设置倍速音调保持开关（F-01），同步持久化并应用到播放器。 */
    fun setPitchPreservation(enabled: Boolean) {
        PlayerSettings.pitchPreservationEnabled = enabled
        player.setPitchPreservationEnabled(enabled)
    }

    // region F-19 视频书签

    /**
     * 在当前播放位置添加书签（F-19）。
     *
     * @param label 用户备注，可空
     */
    fun addBookmark(label: String? = null) {
        val history = currentHistory ?: return
        val position = player.positionMs.value
        if (position <= 0) return
        val title = _title.value.ifEmpty { history.uniqueKey }
        appScope.launch {
            videoBookmarkDao.insert(
                VideoBookmarkEntity(
                    uniqueKey = history.uniqueKey,
                    storageId = history.storageId,
                    videoName = title,
                    positionMs = position,
                    label = label,
                )
            )
        }
    }

    /** 删除指定书签（F-19）。 */
    fun removeBookmark(bookmarkId: Int) {
        appScope.launch { videoBookmarkDao.delete(bookmarkId) }
    }

    /** 跳转到书签位置（F-19）。 */
    fun seekToBookmark(positionMs: Long) {
        player.seekTo(positionMs)
    }

    // endregion

    /** 长按开始：切到设置的长按倍速，UI 显示倍速 OSD。 */
    fun applyLongPressSpeed() {
        if (_longPressSpeedActive.value != null) return
        val target = PlayerSettings.longPressSpeed
        _longPressSpeedActive.value = target
        player.setSpeed(target)
    }

    /**
     * 松手：恢复到 UI 层当前速度（SPEED_VALUES[speedIndex]）。
     *
     * 若已锁定（[lockLongPressSpeed] 调用过），则保持倍速不恢复。
     */
    fun releaseLongPressSpeed(normalSpeed: Float) {
        if (_longPressSpeedActive.value == null) return
        if (_longPressSpeedLocked.value) return // 锁定态：保持倍速
        _longPressSpeedActive.value = null
        player.setSpeed(normalSpeed)
    }

    /** 长按拖动到底部时调用：标记锁定，松手后倍速保持。 */
    fun lockLongPressSpeed() {
        if (_longPressSpeedActive.value != null) {
            _longPressSpeedLocked.value = true
        }
    }

    /** 是否处于长按拖动可锁定区域（UI 据此显示"拖到底部锁定"提示）。 */
    private val _inLockZone = MutableStateFlow(false)
    val inLockZone: StateFlow<Boolean> = _inLockZone.asStateFlow()

    /** 更新是否处于锁定区域（手势层实时调用）。 */
    fun setInLockZone(inZone: Boolean) {
        _inLockZone.value = inZone
    }

    /** 解除长按倍速锁定：恢复正常倍速，清除 OSD。 */
    fun unlockLongPressSpeed(normalSpeed: Float) {
        _longPressSpeedLocked.value = false
        _longPressSpeedActive.value = null
        player.setSpeed(normalSpeed)
    }

    // endregion

    // region P1.7 睡眠定时

    /** 睡眠定时剩余秒数，null = 未启用。UI 顶栏据此显示「⏱ mm:ss」。 */
    private val _sleepTimerRemaining = MutableStateFlow<Int?>(null)
    val sleepTimerRemaining: StateFlow<Int?> = _sleepTimerRemaining.asStateFlow()

    private var sleepTimerJob: Job? = null

    /**
     * 启动睡眠定时。取消已有定时，开始 [minutes] 分钟倒计时。
     * 倒计时归零后自动暂停播放。
     *
     * 使用基于系统时钟的剩余时间计算，避免 delay 累积误差导致长时间定时偏差。
     */
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = minutes * 60
        sleepTimerJob = viewModelScope.launch {
            val deadlineMs = System.currentTimeMillis() + minutes * 60_000L
            while (true) {
                val remainMs = deadlineMs - System.currentTimeMillis()
                if (remainMs <= 0) break
                // 向上取整到秒，避免显示 0 后仍延迟 1 秒
                _sleepTimerRemaining.value = ((remainMs + 999) / 1000).toInt()
                delay(1000)
            }
            player.pause()
            _sleepTimerRemaining.value = null
        }
    }

    /** 取消睡眠定时。 */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
    }

    // endregion

    // region P1.4 截图

    /** 截图结果事件（保存成功时的文件名 / 失败时的错误信息），供 UI 层显示 Toast。 */
    private val _screenshotEvent = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        // m-06 修复：增大 buffer，避免快速连续截图时 tryEmit 丢弃事件
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val screenshotEvent: SharedFlow<String> = _screenshotEvent.asSharedFlow()

    /**
     * 用户可见消息事件（OSD / Toast），用于播放器内部错误反馈。
     *
     * BUG-20 修复：原 playAtIndex 切集失败仅 Log.w 不通知 UI，用户点「下一集」无反馈。
     * 现通过此 SharedFlow 发送中文错误信息，UI 层 collect 后用 OSD 显示。
     */
    private val _messageEvent = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        // m-06 修复：增大 buffer，避免快速连续切换/重试时 tryEmit 丢弃提示
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    /** HDR 格式检测事件（首帧渲染后触发一次），供 UI 层显示 OSD 提示。 */
    private val _hdrEvent = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val hdrEvent: SharedFlow<String> = _hdrEvent.asSharedFlow()

    /**
     * 保存截图 Bitmap 到 MediaStore（Pictures/NIplayer 目录）。
     *
     * - Android 10+ (API 29+)：作用域存储，通过 [MediaStore] 写入
     * - Android 8-9 (API 26-28)：直接写入 `Pictures/NIplayer` 公共目录
     *
     * 保存完成后 emit [screenshotEvent] 供 UI 层显示 Toast。
     */
    fun saveScreenshot(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val displayName = "screenshot_${System.currentTimeMillis()}.png"
            val result = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(bitmap, displayName)
                } else {
                    saveToLegacyFile(bitmap, displayName)
                }
                "截图已保存：$displayName"
            } catch (e: Exception) {
                "截图失败：${e.message ?: "未知错误"}"
            }
            _screenshotEvent.tryEmit(result)
        }
    }

    private fun saveToMediaStore(bitmap: Bitmap, displayName: String) {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NIplayer")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw RuntimeException("无法创建 MediaStore 条目")
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: throw RuntimeException("无法打开输出流")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun saveToLegacyFile(bitmap: Bitmap, displayName: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "NIplayer",
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, displayName)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // endregion

    // region P1.5 下载反馈

    private val _downloadEvent = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val downloadEvent: SharedFlow<String> = _downloadEvent.asSharedFlow()

    // endregion

    // region P0 续播提示

    /** 续播提示事件（携带已保存的播放位置 ms），供 UI 显示"接着上次看"对话框。 */
    private val _resumeEvent = MutableSharedFlow<Long>(
        extraBufferCapacity = 8,
        // m-06 修复：增大 buffer，避免快速连续 seek 时 tryEmit 丢弃
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val resumeEvent: SharedFlow<Long> = _resumeEvent.asSharedFlow()

    // endregion

    // region P2 A-B 段循环

    /** 循环起点 A（ms），null 表示未设置。 */
    private val _abLoopA = MutableStateFlow<Long?>(null)
    val abLoopA: StateFlow<Long?> = _abLoopA.asStateFlow()

    /** 循环终点 B（ms），null 表示未设置。 */
    private val _abLoopB = MutableStateFlow<Long?>(null)
    val abLoopB: StateFlow<Long?> = _abLoopB.asStateFlow()

    private var abLoopJob: Job? = null

    /** A-B 循环事件（提示消息），供 UI 层显示 Toast。 */
    private val _abLoopEvent = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        // m-06 修复：增大 buffer，避免快速连续设置 A/B 点时 tryEmit 丢弃
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val abLoopEvent: SharedFlow<String> = _abLoopEvent.asSharedFlow()

    /**
     * 请求 UI 层重新触发黑边检测（PixelCopy）。
     *
     * 触发场景：用户从 Crop/Stretch 切回 Fit 时，[effectiveVideoSize] 已被清除，
     * 需要重新抓图检测。UI 层收到此事件后执行 PixelCopy → [applyBlackBarDetection]。
     *
     * m-06 修复：原 [MutableSharedFlow] extraBufferCapacity=1，快速触发两次（如
     * 渲染首帧 + 用户点击重检测按钮）时第二次 tryEmit 静默丢弃。
     * 现增到 4 + DROP_OLDEST，保留最新请求。
     */
    private val _redetectBlackBars = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val redetectBlackBars: SharedFlow<Unit> = _redetectBlackBars.asSharedFlow()

    /** 以当前位置设置循环起点 A。若终点 B 已设置则自动启动循环。 */
    fun setAbLoopPointA() {
        val pos = player.positionMs.value
        _abLoopA.value = pos
        _abLoopEvent.tryEmit("起点 A：${formatTime(pos)}")
        if (_abLoopB.value != null && _abLoopA.value != null) {
            startAbLoop()
        }
    }

    /** 以当前位置设置循环终点 B。若起点 A 已设置则自动启动循环。 */
    fun setAbLoopPointB() {
        val pos = player.positionMs.value
        _abLoopB.value = pos
        _abLoopEvent.tryEmit("终点 B：${formatTime(pos)}")
        if (_abLoopA.value != null && _abLoopB.value != null) {
            startAbLoop()
        }
    }

    /** 清除 A-B 循环设置。 */
    fun clearAbLoop() {
        _abLoopA.value = null
        _abLoopB.value = null
        abLoopJob?.cancel()
        abLoopJob = null
        _abLoopEvent.tryEmit("A-B 循环已清除")
    }

    private fun startAbLoop() {
        abLoopJob?.cancel()
        val a = _abLoopA.value ?: return
        val b = _abLoopB.value ?: return
        if (b <= a) return
        abLoopJob = viewModelScope.launch {
            // m-05 修复：原实现 200ms 轮询 player.positionMs 检测是否到达 B 点，
            // 切回 A 点最多有 200ms 延迟，且持续轮询占用 CPU。
            //
            // 现改为事件驱动：订阅 player.positionMs StateFlow（由 positionTicker 每 500ms
            // 更新 + onPositionDiscontinuity 立即更新），到达 B 点立即 seekTo(A)。
            // 优势：
            // 1. 无独立轮询协程，复用 positionMs 流；CPU 占用降低
            // 2. 检测精度由 200ms 提升到 positionMs 流更新频率（500ms 或 onPositionDiscontinuity 立即）
            // 3. positionMs 流取消时（如 onCleared）协程自动结束，无悬挂 Job
            player.positionMs.collect { pos ->
                if (pos >= b) {
                    player.seekTo(a)
                }
            }
        }
    }

    // endregion

    init {
        // F-01：从持久化设置恢复音调保持开关
        player.setPitchPreservationEnabled(PlayerSettings.pitchPreservationEnabled)

        // 消费播放列表
        playlistHolder.consume()?.let { (items, startIndex) ->
            if (items.isNotEmpty() && startIndex in items.indices) {
                _playlist.value = items
                _currentIndex.value = startIndex
            }
        }

        playbackRequestHolder.consume()?.let { request ->
            _title.value = request.title
            // C-02 修复：保存请求副本，错误后重试使用
            lastPlaybackRequest = request
            isAudioPlayback = request.isAudio

            // 按请求类型过滤播放列表，避免上一会话残留的异构列表混入本次播放
            if (_playlist.value.isNotEmpty()) {
                val typeFilter: (PlaylistItem) -> Boolean =
                    if (request.isAudio) { item -> isAudioFile(item.fileName) }
                    else { item -> !isAudioFile(item.fileName) }
                val filtered = _playlist.value.filter(typeFilter)
                if (filtered.isNotEmpty()) {
                    val oldPath = _playlist.value.getOrNull(_currentIndex.value)?.filePath
                    _currentIndex.value = filtered.indexOfFirst { it.filePath == oldPath }.takeIf { it >= 0 } ?: 0
                } else {
                    _currentIndex.value = -1
                }
                _playlist.value = filtered
            }

            // 提前设置 currentHistory，让 loadAudioCover() 能正常获取 history
            request.history?.let { history ->
                currentHistory = history
                _currentBookmarkKey.value = history.uniqueKey to history.storageId
            }

            if (request.isAudio) {
                // 音频：直接委托给 AudioPlaybackManager，单 ExoPlayer 架构
                // 不占用 NxPlayer，无需 bridgeToBackgroundPlayback
                audioPlaybackManager.play(
                    source = request.source,
                    title = request.title,
                    coverPath = null,
                    artist = request.title,
                    startPositionMs = request.startPositionMs,
                    playlist = _playlist.value,
                    startIndex = _currentIndex.value,
                )
                loadAudioCover()
                loadLrcForCurrentSong()

                // 注册切歌回调，AudioPlaybackManager 触发时由 ViewModel 执行实际 source 切换
                audioPlaybackManager.onPlayNextRequest = { playNext() }
                audioPlaybackManager.onPlayPreviousRequest = { playPrevious() }
                // 注册播放错误回调，通过 messageEvent 展示 Snackbar 提示
                audioPlaybackManager.onPlaybackError = { msg ->
                    _messageEvent.tryEmit(msg)
                }
            } else {
                // 视频：使用 NxPlayer
                swapStorage(extractStorageFromSource(request.source))
                // W-M8 修复：将 startPositionMs 直接传给 setSource，由 media3 在 prepare 时
                // 自动 seek 到此位置开始下载，避免先从 0 buffer 再被 seekTo 中断的无效请求。
                player.setSource(request.source, request.startPositionMs)
                val hasResume = request.startPositionMs > 30_000
                player.prepare()
                player.play()

                // 续播提示：超过 30 秒时弹出"接着上次看"对话框
                if (hasResume) {
                    _resumeEvent.tryEmit(request.startPositionMs)
                }
            }

            // 记录播放历史（开始播放）
            request.history?.let { history ->
                viewModelScope.launch {
                    recordPlayStart(history, request.title, request.startPositionMs)
                    // BUG-30：恢复播放时自动加载历史外挂字幕（仅视频）
                    if (!request.isAudio) {
                        val storageId = history.storageId
                        if (storageId != null) {
                            val existing = playHistoryDao.getPlayHistory(history.uniqueKey, storageId)
                            existing?.subtitlePath?.takeIf { it.isNotBlank() }?.let { path ->
                                loadPersistedSubtitle(path)
                            }
                        }
                    }
                }
            }
        }

        // 监听播放结束，自动播放下一首（仅视频，音频由 AudioPlaybackManager 的 STATE_ENDED 处理）
        viewModelScope.launch {
            player.state.collect { state ->
                if (state is PlaybackState.Ended && !isAudioPlayback) {
                    playNext()
                }
            }
        }

        // W-M5 修复：订阅播放错误事件，解析 HTTP 错误码并通过 OSD 反馈给用户。
        // 原 onPlayerError 仅更新 state 为 PlaybackState.Error 和 emit PlaybackEvent.Error，
        // 但 PlayerViewModel 未订阅 events 的 Error 分支，用户在 401/403/404/网络异常场景
        // 下只能看到加载转圈消失后无任何提示。
        viewModelScope.launch {
            player.events.collect { event ->
                when (event) {
                    is PlaybackEvent.Error -> {
                        val msg = describePlaybackError(event.cause)
                        _messageEvent.tryEmit(msg)
                    }
                    is PlaybackEvent.HdrDetected -> {
                        _hdrEvent.tryEmit(event.hdrType)
                    }
                    else -> {}
                }
            }
        }

        // 同步播放位置到字幕引擎（positionMs 由 NxMedia3Player 的 positionTicker 周期更新）
        viewModelScope.launch {
            player.positionMs.collect { positionMs ->
                subtitleEngine.update(positionMs)
            }
        }

        // 同步网络下载速度到 _networkSpeed
        viewModelScope.launch {
            player.networkSpeed.collect { speed ->
                _networkSpeed.value = speed
            }
        }

        // BUG-10 修复：周期性保存播放进度（每 30s 一次），兜底进程被杀 /
        // 长时间播放未退出场景。onCleared 仍是最终保存点，本协程只做中途快照。
        // m-04 修复：原实现 `while { delay(30_000); saveProgress() }` 首次保存延迟 30s，
        // 进入播放页后前 30s 内若进程被杀，进度完全丢失（仅有 onCleared 兜底）。
        // 现首次保存提前到 [PROGRESS_FIRST_SAVE_DELAY_MS]（5s），后续按
        // [PROGRESS_SAVE_INTERVAL_MS]（30s）周期保存。5s 足以等播放稳定后写入初始位置。
        viewModelScope.launch {
            // 首次延迟短，确保进入后立即有进度快照
            delay(PROGRESS_FIRST_SAVE_DELAY_MS)
            runCatching { saveProgress() }.onFailure { e ->
                android.util.Log.w("PlayerViewModel", "首次保存进度失败: ${e.message}", e)
            }
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                // W-M10 修复：原 runCatching 静默吞掉所有异常，DB 错误（如 SQLiteFullException）
                // 无日志，排查"进度丢失"问题困难。现记录失败日志。
                runCatching { saveProgress() }.onFailure { e ->
                    android.util.Log.w("PlayerViewModel", "周期性保存进度失败: ${e.message}", e)
                }
            }
        }

        // BUG-H2 修复：PlayStarter 在后台异步构造同目录播放列表（SMB/WebDAV 大目录
        // listFiles 耗时 1-3 秒），可能晚于本 ViewModel 初始化。同步路径（文件浏览页）
        // 已在上方 consume 消费；此处订阅 PlaylistHolder 的流，接收延迟到达的列表，
        // 避免首页英雄卡/播放历史恢复播放时连播列表丢失（竞态）。此时请求已消费，
        // isAudioPlayback 已确定，可安全按请求类型过滤并同步 AudioPlaybackManager。
        viewModelScope.launch {
            playlistHolder.playlistFlow.collect { update ->
                val (items, startIndex) = update ?: return@collect
                applyLatePlaylist(items, startIndex)
                playlistHolder.clear()
            }
        }
    }

    /**
     * W-M5 修复：将 [PlaybackException] 转换为用户可读的中文错误信息。
     *
     * 识别 media3 包装的 HttpDataSourceException，提取 HTTP 响应码分类提示：
     * - 401：账号密码错误或凭据过期
     * - 403：无访问权限
     * - 404：文件不存在（可能被移动/删除）
     * - 5xx：服务器错误
     * - 其他：通用错误
     */
    private fun describePlaybackError(cause: Throwable): String {
        val httpCode = extractHttpStatusCode(cause)
        if (httpCode != null) {
            return when (httpCode) {
                401 -> "播放失败：账号密码错误或凭据过期（401）"
                403 -> "播放失败：无访问权限（403）"
                404 -> "播放失败：文件不存在，可能已被移动或删除（404）"
                in 500..599 -> "播放失败：服务器错误（$httpCode）"
                else -> "播放失败：HTTP $httpCode"
            }
        }
        // 非 HTTP 错误：文件不存在 / 网络异常 / 解析错误 / 解码错误等
        val msg = cause.message ?: cause::class.simpleName ?: "未知错误"
        return when (cause) {
            is java.io.FileNotFoundException -> "播放失败：文件不存在，可能已被移动或删除"
            is java.io.IOException -> "播放失败：网络错误（${msg}）"
            else -> "播放失败：$msg"
        }
    }

    /** 从 PlaybackException 链中提取 HTTP 响应码（递归查找 HttpDataSourceException）。 */
    private fun extractHttpStatusCode(cause: Throwable?): Int? {
        if (cause == null) return null
        // media3 的 HttpDataSourceException 有 httpResponseStatusCode 字段（media3 1.x+）
        // 用反射兼容不同版本，避免硬依赖具体类名
        val className = cause.javaClass.name
        if (className.contains("HttpDataSourceException") || className.contains("InvalidResponseCodeException")) {
            try {
                val field = cause.javaClass.getDeclaredField("responseCode")
                    ?: cause.javaClass.superclass?.getDeclaredField("responseCode")
                field?.isAccessible = true
                val code = field?.getInt(cause)
                if (code != null && code > 0) return code
            } catch (_: NoSuchFieldException) {
                // 某些版本字段名不同，继续递归
            } catch (_: Exception) {
            }
        }
        // 递归查找 cause 链
        return extractHttpStatusCode(cause.cause)
    }

    /**
     * 记录播放开始（写入 play_history 表）。
     *
     * BUG-H3 修复：不再用 [startPositionMs] 覆盖 [PlayHistoryEntity.videoPosition]。
     *
     * 原实现 `existing.videoPosition = startPositionMs` 的问题：
     * - 用户从 1h 处续播 → recordPlayStart 写入 videoPosition=1h（与 DB 一致，无变化）
     * - 用户立即退出（未实际播放）→ onCleared 中 player.positionMs=0
     * - saveProgressInternal 用 0 覆盖 1h → **进度丢失**
     *
     * 修复后：recordPlayStart 只更新 playTime（刷新"最近播放"排序），
     * 不写 videoPosition。进度保存完全交给 [saveProgress] / [onCleared]，
     * 它们读取 player 实际位置，避免"未播放但覆盖为 startPositionMs"的窗口期。
     *
     * 新记录（existing==null）仍写入 startPositionMs 作为初始值，
     * 因为此时 DB 无任何进度，startPositionMs（续播位置）是合理的初始值。
     */
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
        // C-04 修复：改用 @Transaction upsert，避免并发场景下 query-then-update/insert
        // 窗口期导致 insert 冲突被 IGNORE 静默丢弃（如周期性 saveProgress 与 recordPlayStart 并发）
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
        )
        playHistoryDao.upsertPlayStart(
            uniqueKey = history.uniqueKey,
            storageId = storageId,
            newPlayTime = now,
            newEntity = newEntity,
        )
    }

    /**
     * 应用延迟到达的播放列表（PlayStarter 异步构造场景，BUG-H2 修复）。
     *
     * 与 init 中同步路径的区别：调用时请求已消费、[isAudioPlayback] 已确定，
     * 可安全按当前请求类型过滤，避免上一会话残留的异构列表混入。音频场景同步
     * [AudioPlaybackManager] 的列表与索引，激活「下一首/上一首」与播放列表面板。
     */
    private fun applyLatePlaylist(items: List<PlaylistItem>, startIndex: Int) {
        if (items.isEmpty()) return
        val typeFilter: (PlaylistItem) -> Boolean =
            if (isAudioPlayback) { item -> isAudioFile(item.fileName) }
            else { item -> !isAudioFile(item.fileName) }
        val filtered = items.filter(typeFilter)
        if (filtered.isEmpty()) {
            _playlist.value = emptyList()
            _currentIndex.value = -1
            return
        }
        val oldPath = items.getOrNull(startIndex)?.filePath
        val newIndex = filtered.indexOfFirst { it.filePath == oldPath }.takeIf { it >= 0 } ?: 0
        _playlist.value = filtered
        _currentIndex.value = newIndex
        if (isAudioPlayback) {
            audioPlaybackManager.updatePlaylist(filtered, newIndex)
        }
    }

    /**
     * 播放下一首。无下一首时（已在列表末尾）不做操作。
     *
     * 重建播放源：通过 [PlaylistItem.libraryId] 查询 [com.nichx.niplayer.database
     * .entity.MediaLibraryEntity]，用 [StorageFactory] 重建 Storage，
     * 再用 [MediaSourceBuilder] 构造播放源。
     */
    fun playNext() {
        val list = _playlist.value
        val nextIndex = _currentIndex.value + 1
        if (list.isEmpty() || nextIndex >= list.size) return
        playAtIndex(nextIndex)
    }

    /** 播放上一首。已在列表首项时不做操作。 */
    fun playPrevious() {
        val list = _playlist.value
        val prevIndex = _currentIndex.value - 1
        if (list.isEmpty() || prevIndex < 0) return
        playAtIndex(prevIndex)
    }

    /**
     * 播放列表中指定索引的项。
     *
     * 1. 查询存储源 → 重建 Storage → 构造 NxMediaSource
     * 2. 查询续播位置
     * 3. setSource → seekTo → prepare → play
     * 4. 更新 [currentHistory] + 写入 play_history
     *
     * BUG-P2 修复：切换到新曲目前，先保存当前曲目的播放进度，
     * 避免 currentHistory 被覆盖后旧进度丢失。
     */
    fun playAtIndex(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        val item = list[index]

        viewModelScope.launch {
            // M-10 修复：用 Mutex 串行化 playAtIndex 全流程，避免快速连续切歌时
            // 第一次 saveProgressSync 尚未完成、第二次已覆盖 currentHistory 导致
            // 第一次保存的是错误曲目的进度
            playAtIndexMutex.withLock {
                try {
                    // 切歌前先保存当前曲目进度（BUG-P2 修复）
                    saveProgressSync()

                    val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(item.libraryId) }
                        ?: return@withLock
                    val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
                        ?: return@withLock
                    val file = MediaSourceBuilder.createVirtualFile(item.filePath, item.fileName, item.fileSize)
                    val uniqueKey = "${library.id}:${item.filePath}"
                    // W-N7 修复：传入 uniqueKey 作为 mediaId，让 media3 MediaItem.mediaId
                    // 与应用层 uniqueKey 一致，便于未来 MediaSession 集成。
                    val source = MediaSourceBuilder.buildMediaSource(storage, file, mediaId = uniqueKey)
                    val startPositionMs = withContext(Dispatchers.IO) {
                        playHistoryDao.getPlayHistory(uniqueKey, library.id)?.videoPosition ?: 0L
                    }

                    _title.value = item.fileName
                    _currentIndex.value = index
                    currentHistory = HistoryDescriptor(
                        uniqueKey = uniqueKey,
                        url = item.filePath,
                        mediaTypeValue = item.mediaTypeValue,
                        storageId = library.id,
                        storagePath = item.filePath,
                        fileSize = item.fileSize,
                    ).also {
                        _currentBookmarkKey.value = it.uniqueKey to it.storageId
                    }

                    isAudioPlayback = isAudioFile(item.fileName)
                    if (isAudioPlayback) {
                        // 音频：委托给 AudioPlaybackManager，单播放器架构
                        audioPlaybackManager.play(
                            source = source,
                            title = item.fileName,
                            coverPath = _audioCoverPath.value,
                            artist = item.fileName,
                            startPositionMs = startPositionMs,
                            playlist = _playlist.value,
                            startIndex = index,
                        )
                        loadAudioCover()
                        loadLrcForCurrentSong()
                    } else {
                        // 视频：使用 NxPlayer
                        swapStorage(extractStorageFromSource(source))
                        // W-M8 修复：同 init 路径，startPositionMs 直接传给 setSource。
                        player.setSource(source, startPositionMs)
                        player.prepare()
                        player.play()

                        // 字幕清理（仅视频）
                        subtitleEngine.clear()
                        player.setSubtitleOffsetMs(0L)
                        val existingSub = withContext(Dispatchers.IO) {
                            playHistoryDao.getPlayHistory(currentHistory!!.uniqueKey, library.id)
                        }
                        existingSub?.subtitlePath?.takeIf { it.isNotBlank() }?.let { path ->
                            loadPersistedSubtitle(path)
                        }

                        _audioCoverPath.value = null
                        _lrcText.value = null
                    }

                    // 切歌后更新 lastPlaybackRequest，避免 retryPlayback/restartFromStart 复用已关闭的旧源
                    lastPlaybackRequest = PlaybackRequest(
                        source = source,
                        title = item.fileName,
                        startPositionMs = startPositionMs,
                        history = currentHistory,
                        isAudio = isAudioPlayback,
                    )

                    recordPlayStart(currentHistory!!, item.fileName, startPositionMs)
                } catch (e: Exception) {
                    // M-09 修复：CancellationException 必须重新抛出，遵守结构化并发。
                    // viewModelScope 取消时若吞掉 CancellationException，协程继续执行 catch 块
                    // 可能导致资源未释放或状态不一致。
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // BUG-20 修复：切集失败不中断当前播放，但需通过 messageEvent 通知 UI
                    // 显示错误信息（OSD），避免用户点「下一集」后无反馈。
                    android.util.Log.w("PlayerViewModel", "playAtIndex($index) failed", e)
                    _messageEvent.tryEmit("切换失败：${e.message ?: e::class.simpleName}")
                }
            }
        }
    }

    /**
     * 从 [NxMediaSource] 提取携带的 [com.nichx.niplayer.storage.Storage]（仅 DataSource 类型有）。
     *
     * BUG-19+23 修复：[NxMediaSource.DataSource] 现携带 storage 引用，HTTP/Local 类型不携带。
     */
    private fun extractStorageFromSource(
        source: com.nichx.niplayer.player.kernel.NxMediaSource,
    ): com.nichx.niplayer.storage.Storage? {
        return (source as? com.nichx.niplayer.player.kernel.NxMediaSource.DataSource)?.storage
    }

    /**
     * 切换 [currentStorage]：先关闭旧 storage（异步），再赋值新 storage。
     *
     * BUG-19+23 修复：原 playAtIndex / PlaybackRequest 消费路径创建的 Storage 永不关闭，
     * 每次切集或退出泄漏一个 SMB 连接。现统一由本方法管理：
     * - 旧 storage 关闭用独立 CoroutineScope(Dispatchers.IO)，避免 viewModelScope 取消时阻塞
     * - storage.close() 是 suspend，需在协程中调用
     * - null 入参表示新源是 HTTP/Local，无需持有 storage
     */
    private fun swapStorage(newStorage: com.nichx.niplayer.storage.Storage?) {
        val old = currentStorage
        currentStorage = newStorage
        if (old != null && old !== newStorage) {
            // 用 closeScope 关闭，避免 onCleared 中 viewModelScope 已取消时无法执行
            closeScope.launch {
                try { old.close() } catch (_: Exception) {}
            }
        }
    }

    /** 音频文件：异步提取专辑封面到本地缓存，更新 [audioCoverPath]。 */
    private fun loadAudioCover() {
        // 受总开关与音频封面开关双重控制
        if (!ThumbnailSettings.generateThumbnail || !ThumbnailSettings.generateForAudio) {
            _audioCoverPath.value = null
            return
        }
        val history = currentHistory
        if (history != null) {
            val sid = history.storageId
            if (sid != null) {
                // 播放后生成策略检查：关闭模式不提取封面（播放中提取属正常行为，非关闭模式均放行）
                if (!ThumbnailSettings.shouldGenerateOnPlayback(sid)) {
                    _audioCoverPath.value = null
                    return
                }
                loadStorageAudioCover(history, sid)
                return
            }
        }
        // 无历史或 storageId 时（如从下载管理打开本地文件），尝试直接提取本地封面
        loadLocalAudioCover()
    }

    /** 通过 Storage 抽象层提取音频封面（SMB/WebDAV/ExternalStorage）。 */
    private fun loadStorageAudioCover(history: HistoryDescriptor, sid: Int) {
        val filePath = history.storagePath ?: return
        val fileName = history.url.substringAfterLast('/')

        // N-002 修复：先检查本地缓存，命中则直接返回，避免创建不必要的 Storage 连接。
        val cachedPath = thumbnailManager.getCachedAudioCoverPath(sid, filePath)
        if (cachedPath != null) {
            _audioCoverPath.value = cachedPath
            audioPlaybackManager.updateCoverPath(cachedPath)
            return
        }

        // N-001 修复：取消旧的封面生成协程，避免快速切歌时旧协程完成后覆盖新歌封面。
        audioCoverJob?.cancel()
        audioCoverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val library = mediaLibraryDao.getById(sid) ?: return@launch
                val storage = storageFactory.create(library) ?: return@launch
                try {
                    val file = MediaSourceBuilder.createVirtualFile(filePath, fileName)
                    var loaded = false
                    thumbnailManager.preloadAudioCovers(storage, sid, listOf(file)) { _, coverPath ->
                        _audioCoverPath.value = coverPath
                        audioPlaybackManager.updateCoverPath(coverPath)
                        loaded = true
                    }
                    if (!loaded) {
                        val path = thumbnailManager.generateAudioCover(storage, sid, file)
                        if (path != null) {
                            thumbnailManager.uploadAudioCover(storage, file)
                        }
                        if (path != null) {
                            _audioCoverPath.value = path
                            audioPlaybackManager.updateCoverPath(path)
                        } else {
                            // 本地封面提取失败，尝试从 API 获取
                            fetchAudioCoverFromApi(fileName)
                        }
                    }
                } finally {
                    storage.close()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _audioCoverPath.value = null
                audioPlaybackManager.updateCoverPath(null)
            }
        }
    }

    /** 本地文件（下载缓存/SAF content://）直接通过 MediaMetadataRetriever 提取封面。 */
    private fun loadLocalAudioCover() {
        val source = lastPlaybackRequest?.source as? NxMediaSource.Local ?: return
        audioCoverJob?.cancel()
        audioCoverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = source.uri
                val cacheKey = "local_audio_${md5(uri.toString())}"
                val cacheFile = File(appContext.cacheDir, "audio_covers/$cacheKey.jpg")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val path = cacheFile.absolutePath
                    _audioCoverPath.value = path
                    audioPlaybackManager.updateCoverPath(path)
                    return@launch
                }
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(appContext, uri)
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
                            audioPlaybackManager.updateCoverPath(path)
                        }
                    } else {
                        // 本地无嵌入封面，尝试从 API 获取
                        val nameWithoutExt = source.uri.pathSegments.lastOrNull()
                            ?.substringBeforeLast('.') ?: ""
                        if (nameWithoutExt.isNotEmpty()) {
                            fetchAudioCoverFromApi(nameWithoutExt)
                        } else {
                            _audioCoverPath.value = null
                            audioPlaybackManager.updateCoverPath(null)
                        }
                    }
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _audioCoverPath.value = null
                audioPlaybackManager.updateCoverPath(null)
            }
        }
    }

    /** 从 lrcapi 获取封面并缓存到本地。 */
    private fun fetchAudioCoverFromApi(nameWithoutExt: String) {
        if (!musicMetadataService.isConfigured()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.i("PlayerViewModel", "开始从API加载封面: $nameWithoutExt")
                val result = musicMetadataService.fetchCover(title = nameWithoutExt)
                if (result.isSuccess) {
                    val coverBytes = result.getOrNull()
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        val coverDir = File(appContext.cacheDir, "audio_covers")
                        if (!coverDir.exists()) coverDir.mkdirs()
                        val coverFile = File(coverDir, "api_${md5(nameWithoutExt)}.jpg")
                        coverFile.writeBytes(coverBytes)
                        val path = coverFile.absolutePath
                        _audioCoverPath.value = path
                        audioPlaybackManager.updateCoverPath(path)
                        android.util.Log.i("PlayerViewModel", "从API加载封面成功: $nameWithoutExt, 大小: ${coverBytes.size} bytes")
                        _messageEvent.tryEmit("已通过 API 获取封面：$nameWithoutExt")
                    }
                } else {
                    android.util.Log.w("PlayerViewModel", "从API加载封面失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("PlayerViewModel", "从API加载封面异常", e)
            }
        }
    }

    /** 字符串 MD5 哈希，用于本地缓存文件名。 */
    private fun md5(input: String): String {
        try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            return input.hashCode().toUInt().toString(16)
        }
    }

    /** 为当前歌曲异步加载 LRC 歌词。 */
    private fun loadLrcForCurrentSong() {
        val history = currentHistory ?: return
        val storageId = history.storageId
        val filePath = history.storagePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
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
                var found = false
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
                                    audioPlaybackManager.setLrcText(text)
                                    found = true
                                    return@launch
                                }
                            }
                        } finally {
                            storage.close()
                        }
                    } else {
                        // Local file: try direct File access
                        val lrcFile = File(lrcFilePath)
                        if (lrcFile.exists()) {
                            val text = lrcFile.readText()
                            _lrcText.value = text
                            audioPlaybackManager.setLrcText(text)
                            found = true
                            return@launch
                        }
                    }
                }

                // 优先级2: 从 lrcapi 远程获取歌词（仅在已配置时启用）
                if (!found && musicMetadataService.isConfigured() && nameWithoutExt.isNotEmpty()) {
                    val localFilePath = if (filePath.startsWith("/")) filePath else ""
                    val result = musicMetadataService.fetchLyrics(
                        title = nameWithoutExt,
                        artist = "",
                        path = localFilePath,
                    )
                    if (result.isSuccess) {
                        val content = result.getOrNull()
                        if (!content.isNullOrBlank()) {
                            val cacheFile = saveLrcToCache(nameWithoutExt, content)
                            _lrcText.value = content
                            audioPlaybackManager.setLrcText(content)
                            android.util.Log.i("PlayerViewModel", "从API加载歌词成功: $nameWithoutExt, 长度: ${content.length}")
                            _messageEvent.tryEmit("已通过 API 获取歌词：$nameWithoutExt")
                            return@launch
                        }
                    } else {
                        android.util.Log.w("PlayerViewModel", "从API加载歌词失败: ${result.exceptionOrNull()?.message}")
                    }
                }

                _lrcText.value = null
                audioPlaybackManager.setLrcText(null)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _lrcText.value = null
                audioPlaybackManager.setLrcText(null)
            }
        }
    }

    private fun saveLrcToCache(nameWithoutExt: String, content: String): File {
        val lrcDir = File(appContext.cacheDir, "lrc_cache")
        if (!lrcDir.exists()) lrcDir.mkdirs()
        val lrcFile = File(lrcDir, "$nameWithoutExt.lrc")
        lrcFile.writeText(content)
        return lrcFile
    }

    /** 根据当前状态切换播放/暂停。Ended 状态下调用会从头播放。 */
    fun togglePlayPause() {
        when (player.state.value) {
            is PlaybackState.Playing -> player.pause()
            is PlaybackState.Paused,
            is PlaybackState.Ready,
            is PlaybackState.Ended -> player.play()
            else -> Unit
        }
    }

    /**
     * C-02 修复：错误状态下用户点"重试"调用。
     *
     * 用 [lastPlaybackRequest] 的原始 startPositionMs 重新装载播放源。
     * 适用于 SMB/WebDAV/FTP 临时断连、解码失败等场景。
     */
    fun retryPlayback() {
        val request = lastPlaybackRequest ?: return
        viewModelScope.launch {
            // 重新装载 source（NxMediaSource 已闭包 storage，无需重新创建）
            // C-01 修复：setSource/prepare 会清零 hasError，UI 状态从 Error → Buffering
            player.setSource(request.source, request.startPositionMs)
            player.prepare()
            player.play()
        }
    }

    /**
     * C-02 修复：错误状态下用户点"从头播放"调用。
     *
     * 用 [lastPlaybackRequest] 但 startPositionMs=0 重新装载播放源。
     */
    fun restartFromStart() {
        val request = lastPlaybackRequest ?: return
        viewModelScope.launch {
            player.setSource(request.source, 0L)
            player.prepare()
            player.play()
        }
    }

    /** 跳转到指定位置（ms）。 */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    /**
     * 桥接到后台播放（已废弃）。
     *
     * AudioPlaybackManager 现已作为唯一的音频播放源，音频播放始终在其中运行，
     * 无需在离开 AudioPlayerScreen 时进行播放权交接。保留此空方法以兼容外部调用。
     */
    fun bridgeToBackgroundPlayback() {
        // 单 ExoPlayer 架构：音频播放始终由 AudioPlaybackManager 持有，无需交接
    }

    /**
     * 下载当前播放文件。
     *
     * 若已设置下载目录，下载到该目录并使用实际文件大小；
     * 若未设置，提示用户在下载管理中设置。
     */
    fun downloadCurrentFile() {
        if (!DownloadSettings.isDownloadDirSet) {
            _downloadEvent.tryEmit("请先在下载管理页面设置下载目录")
            return
        }
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        val filePath = history.storagePath ?: return
        val fileName = _title.value.ifEmpty { history.url.substringAfterLast('/') }
        val uniqueKey = "${storageId}:$filePath"
        downloadManager.addTask(
            storageId = storageId,
            filePath = filePath,
            fileName = fileName,
            uniqueKey = uniqueKey,
            totalBytes = history.fileSize,
            targetStorageUrl = DownloadSettings.downloadDirUri,
            targetStorageName = DownloadSettings.downloadDirName,
        )
        _downloadEvent.tryEmit("已添加到下载队列")
    }

    /**
     * 切换到下一个缩放模式并应用到播放器。返回新的索引。
     *
     * 三档循环：适应 → 裁剪 → 拉伸 → 适应。
     * - 适应/裁剪：由 media3 videoScalingMode 处理
     * - 拉伸：UI 层订阅 [NxPlayer.videoScaleMode]，将 SurfaceView 改为 fillMaxSize
     *
     * 内置 200ms 防抖，避免连续快速点击导致 SurfaceView 反复重建与 OSD 抖动。
     */
    private var lastScaleClickMs = 0L

    fun cycleScaleMode(): Int {
        val now = System.currentTimeMillis()
        if (now - lastScaleClickMs < 200) return _scaleIndex.value
        lastScaleClickMs = now
        val next = (_scaleIndex.value + 1) % SCALE_MODES.size
        _scaleIndex.value = next
        player.setVideoScaleMode(SCALE_MODES[next])
        if (SCALE_MODES[next] != NxVideoScaleMode.Fit) {
            // 切到 Crop/Stretch 时禁用黑边裁剪覆盖，清除已检测结果
            player.setBlackBarCropEnabled(false)
            _effectiveVideoSize.value = null
        } else {
            // 切回 Fit 时：若之前检测过黑边（现已清除），请求 UI 层重新触发 PixelCopy 检测
            // 首次进入 Fit（从未检测过）不触发，避免无意义的抓图
            if (videoSize.value.isValid) {
                _redetectBlackBars.tryEmit(Unit)
            }
        }
        return next
    }

    /**
     * 应用智能黑边检测结果。
     *
     * 由 UI 层在首帧渲染后抓图调用：PixelCopy 抓取 SurfaceView 位图 → 传入本方法。
     * 本方法在 IO 调度器执行 [BlackBarDetector.detect]，成功时更新 [effectiveVideoSize]。
     *
     * 不会抛异常：检测失败 / 功能关闭 / 全黑画面时保持 [effectiveVideoSize] 为 null，
     * UI 层将回退到原始 [videoSize]。
     *
     * @param bitmap 首帧位图（UI 层通过 PixelCopy 获取）
     */
    fun applyBlackBarDetection(bitmap: Bitmap) {
        if (!PlayerSettings.autoDetectBlackBars) {
            _effectiveVideoSize.value = null
            bitmap.recycle()
            return
        }
        val currentSize = videoSize.value
        if (!currentSize.isValid) {
            bitmap.recycle()
            return
        }

        // 检测在 IO 线程执行（像素扫描耗时），
        // setBlackBarCropEnabled / StateFlow 更新切回主线程（ExoPlayer 要求主线程访问）
        viewModelScope.launch(Dispatchers.IO) {
            val rect = try {
                BlackBarDetector.detect(bitmap)
            } catch (_: Exception) {
                null
            } finally {
                bitmap.recycle()
            }

            if (rect == null) {
                withContext(Dispatchers.Main) {
                    _effectiveVideoSize.value = null
                    player.setBlackBarCropEnabled(false)
                }
                return@launch
            }

            // 用检测到的有效像素区域重算 VideoSize
            // pixelWidthHeightRatio 保持原值（黑边不影响像素形状）
            val rectAspect = rect.width.toFloat() / rect.height
            val videoAspect = currentSize.width.toFloat() / currentSize.height
            // 用阈值比较（3%），避免采样误差导致误判：
            // BlackBarDetector 用 SAMPLE_STEP=4 下采样，rect 比例可能与真实比例有微小差异，
            // 精确比较 != 会让无黑边视频也触发 effectiveVideoSize 更新，导致画面比例变化两次
            val hasBlackBars = kotlin.math.abs(rectAspect - videoAspect) > 0.03f

            withContext(Dispatchers.Main) {
                if (hasBlackBars) {
                    // 检测到黑边：用有效区域重算 VideoSize，启用裁剪覆盖
                    // 让 media3 把原始视频帧保持比例裁剪填满缩小后的 surface，正好裁掉黑边
                    val effective = VideoSize(
                        width = rect.width,
                        height = rect.height,
                        pixelWidthHeightRatio = currentSize.pixelWidthHeightRatio,
                        unappliedRotationDegrees = currentSize.unappliedRotationDegrees,
                    )
                    _effectiveVideoSize.value = effective
                    player.setBlackBarCropEnabled(true)
                } else {
                    // 无黑边：保持 effectiveVideoSize = null，使用原始 videoSize
                    // 避免不必要的画面比例变化
                    _effectiveVideoSize.value = null
                    player.setBlackBarCropEnabled(false)
                }
            }
        }
    }

    /** 重置黑边检测结果（切换视频 / seek 跨度大时调用）。 */
    fun resetBlackBarDetection() {
        _effectiveVideoSize.value = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            player.setBlackBarCropEnabled(false)
        } else {
            viewModelScope.launch(Dispatchers.Main) {
                player.setBlackBarCropEnabled(false)
            }
        }
    }

    /** 选择指定音频轨道。透传至 [NxPlayer.selectAudioTrack]。 */
    fun selectAudioTrack(index: Int) = player.selectAudioTrack(index)

    /** 选择字幕轨道。-1 自动；-2 关闭；>=0 选中指定轨道。 */
    fun selectSubtitleTrack(index: Int) = player.selectSubtitleTrack(index)

    /** 调整字幕延迟（增量，ms）。 */
    fun adjustSubtitleOffset(deltaMs: Long) {
        val newOffset = player.subtitleOffsetMs.value + deltaMs
        player.setSubtitleOffsetMs(newOffset)
        subtitleEngine.setOffsetMs(newOffset)
    }

    /** 重置字幕延迟为 0。 */
    fun resetSubtitleOffset() {
        player.setSubtitleOffsetMs(0L)
        subtitleEngine.setOffsetMs(0L)
    }

    /**
     * 添加外挂字幕文件。
     *
     * 外挂字幕（ASS/SSA/SRT）走自渲染链路：
     * 1. 复制 URI 内容到临时文件（兼容 content:// 和 file://）
     * 2. 用 [FormatASS] / [FormatSRT] 解析为 [com.nichx.niplayer.subtitle.info.TimedTextObject]
     * 3. 加载到 [subtitleEngine]，由 [SubtitleOverlay] 渲染
     *
     * 外挂字幕不走 media3 TextRenderer，以支持 ASS 特效和精确字幕偏移。
     * （BUG-P7 修复后 [NxPlayer] 不再提供 addSubtitle 接口）
     *
     * BUG-30 修复：解析成功后将字幕复制到持久目录 `files/subtitles/`，并调用
     * [PlayHistoryDao.updateSubtitle] 持久化路径。下次恢复播放时 init 自动加载，
     * 避免用户每次重新搜索/选择字幕。
     *
     * @param uri 字幕文件 URI（content:// / file://）
     * @param mimeType 字幕 MIME 类型（用于选择解析器）
     * @param language 字幕语言标签（保留参数兼容性，当前未使用）
     */
    fun addSubtitle(uri: Uri, mimeType: String, language: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = copyUriToTempFile(uri) ?: return@launch
            try {
                val tto = when {
                    mimeType.contains("ssa", ignoreCase = true) ||
                        mimeType.contains("ass", ignoreCase = true) -> FormatASS().parseFile(tempFile)
                    else -> FormatSRT().parseFile(tempFile)
                }
                subtitleEngine.load(tto, tempFile.name)

                // BUG-30：解析成功后持久化字幕到内部存储，并更新历史记录
                persistSubtitle(tempFile, mimeType)
            } catch (e: Exception) {
                // M-09 修复：CancellationException 必须重新抛出，遵守结构化并发
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 解析失败时静默忽略，避免崩溃；后续可加错误提示
            } finally {
                tempFile.delete()
            }
        }
    }

    /**
     * 持久化字幕文件到 `files/subtitles/` 并更新 [PlayHistoryEntity.subtitlePath]。
     *
     * BUG-30 修复：将字幕从临时缓存复制到持久目录，确保进程重启后仍可加载。
     * 文件名基于 uniqueKey 哈希避免冲突，扩展名从 mimeType 推断。
     */
    private suspend fun persistSubtitle(tempFile: File, mimeType: String) {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        // 文件夹访问加密：加密目录内的文件不写历史，字幕路径也不持久化
        if (encryptedFolderManager.isWithinEncrypted(storageId, history.storagePath)) return
        val ext = when {
            mimeType.contains("ass", ignoreCase = true) -> ".ass"
            mimeType.contains("ssa", ignoreCase = true) -> ".ssa"
            mimeType.contains("srt", ignoreCase = true) -> ".srt"
            else -> ".sub"
        }
        val subtitleDir = File(appContext.filesDir, "subtitles").apply { mkdirs() }
        val persistentFile = File(subtitleDir, "${history.uniqueKey.hashCode()}$ext")
        try {
            tempFile.copyTo(persistentFile, overwrite = true)
            playHistoryDao.updateSubtitle(history.uniqueKey, storageId, persistentFile.absolutePath)
        } catch (e: Exception) {
            // M-09 修复：CancellationException 必须重新抛出，遵守结构化并发
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 持久化失败不影响当前播放，仅无法恢复字幕
        }
    }

    /**
     * 从持久化路径加载外挂字幕。
     *
     * BUG-30 修复：恢复播放时若历史记录含 subtitlePath，调用本方法自动加载。
     */
    private fun loadPersistedSubtitle(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@launch
            try {
                val tto = when {
                    path.endsWith(".ass", ignoreCase = true) ||
                        path.endsWith(".ssa", ignoreCase = true) -> FormatASS().parseFile(file)
                    else -> FormatSRT().parseFile(file)
                }
                subtitleEngine.load(tto, file.name)
            } catch (e: Exception) {
                // M-09 修复：CancellationException 必须重新抛出，遵守结构化并发
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 恢复失败静默忽略
            }
        }
    }

    /**
     * 清除外挂字幕。
     *
     * M-13 修复：同步重置 player 的 subtitleOffsetMs，避免 SubtitleEngine._offsetMs=0
     * 而 player.subtitleOffsetMs 仍保留旧值导致下次 adjustSubtitleOffset 基于旧值计算。
     */
    fun clearExternalSubtitle() {
        subtitleEngine.clear()
        player.setSubtitleOffsetMs(0L)
    }

    /** 将 URI 内容复制到临时文件（用于字幕解析）。 */
    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val suffix = when {
                uri.path?.endsWith(".ass", ignoreCase = true) == true -> ".ass"
                uri.path?.endsWith(".ssa", ignoreCase = true) == true -> ".ssa"
                uri.path?.endsWith(".srt", ignoreCase = true) == true -> ".srt"
                else -> ".sub"
            }
            val tempFile = File.createTempFile("subtitle_", suffix, appContext.cacheDir)
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            tempFile
        } catch (e: Exception) {
            // M-09 修复：CancellationException 必须重新抛出，遵守结构化并发
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * 保存当前播放进度到 play_history（upsert）。
     *
     * BUG-10 修复：补充 [onCleared] 之外的中途保存点：
     * - 周期性保存（[init] 中每 [PROGRESS_SAVE_INTERVAL_MS] 一次，兜底进程被杀）
     * - onPause 保存（UI 层在 [androidx.lifecycle.Lifecycle.Event.ON_PAUSE] 时调用）
     * - playAtIndex 切歌前调用 [saveProgressSync] 同步保存
     *
     * 与 [onCleared] 不同：不释放 player，不生成缩略图，仅写 DB。
     * 多次调用安全（幂等 upsert）。
     */
    fun saveProgress() {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        // BUG-24 修复：播放器处于 Error / Idle 状态时 player.positionMs 可能为 0
        // （如 SMB 断网后 onPlayerError 触发，exoPlayer.currentPosition 归零）。
        // 若此时调用 saveProgressInternal 会用 0 覆盖 DB 中已有进度（如 1 小时），
        // 导致用户下次恢复从头播放。仅在播放中/暂停/就绪/缓冲状态下保存。
        // 音频走 AudioPlaybackManager 的 ExoPlayer，不走 NxPlayer，需分别校验状态
        if (isAudioPlayback) {
            val position = audioPlaybackManager.positionMs.value
            val duration = audioPlaybackManager.durationMs.value
            if (position <= 0) return
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                saveProgressInternal(history, storageId, position, duration)
            }
            return
        }
        val state = player.state.value
        if (state !is PlaybackState.Playing &&
            state !is PlaybackState.Paused &&
            state !is PlaybackState.Ready &&
            state !is PlaybackState.Buffering
        ) {
            return
        }
        val position = player.positionMs.value
        val duration = player.durationMs.value
        // BUG-24 双重保险：即使状态判断通过，position=0 且 duration>0 也跳过
        // （理论上 Ready/Buffering 早期 position 可能短暂为 0，但此时 DB 已有进度，
        // 写入 0 会覆盖。仅在 DB 已有记录且 position=0 时跳过，新记录（position 本就该是 0）不受影响）
        if (position <= 0) return
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            saveProgressInternal(history, storageId, position, duration)
        }
    }

    /**
     * 同步保存当前播放进度，用于切歌场景。
     *
     * BUG-P2 修复：[playAtIndex] 切换前必须在覆盖 [currentHistory] 之前保存当前进度，
     * 否则旧曲目的最终位置会被新曲目的 currentHistory 覆盖丢失。
     *
     * 在调用方协程上下文执行，不启动新协程，确保保存完成后再切换曲目。
     *
     * BUG-24 修复：同 [saveProgress]，Error/Idle 状态下跳过，避免用 0 覆盖进度。
     * 注意：切歌场景下切歌前状态通常是 Playing/Paused，此检查不会误拦正常切歌保存。
     */
    private suspend fun saveProgressSync() {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        // 音频走 AudioPlaybackManager 的 ExoPlayer，不走 NxPlayer，需分别校验状态
        if (isAudioPlayback) {
            val position = audioPlaybackManager.positionMs.value
            val duration = audioPlaybackManager.durationMs.value
            if (position <= 0) return
            saveProgressInternal(history, storageId, position, duration)
            return
        }
        val state = player.state.value
        if (state !is PlaybackState.Playing &&
            state !is PlaybackState.Paused &&
            state !is PlaybackState.Ready &&
            state !is PlaybackState.Buffering
        ) {
            return
        }
        val position = player.positionMs.value
        val duration = player.durationMs.value
        if (position <= 0) return
        saveProgressInternal(history, storageId, position, duration)
    }

    /**
     * 进度落盘内部实现（upsert），由 [saveProgress] 和 [saveProgressSync] 复用。
     *
     * BUG-24 修复：当传入 [position] 为 0 且 DB 已有记录时，**不覆盖**已有进度。
     * 触发场景：onCleared 兜底保存时，player 可能处于 Error 状态导致 position=0，
     * 若直接覆盖会让 DB 中真实的 1 小时进度归零。新记录（existing==null）仍写入 0
     * 作为初始值，不影响首次 insert。中途保存（[saveProgress]）已在调用方拦截 position=0，
     * 此处的保护主要针对 onCleared 兜底路径。
     *
     * W-N13 修复：原实现采用非事务的 query-then-update/insert 模式，并发场景
     * （周期性保存 + 切歌保存同时触发）下两个协程可能都查到 existing==null，
     * 都尝试 insert，第二个被 IGNORE 静默失败导致进度丢失。改为调用
     * [PlayHistoryDao.upsertProgress]（@Transaction 包裹 query+update/insert），
     * Room 在数据库层加事务锁串行化，确保并发安全。
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

    override fun onCleared() {
        // 清理 AudioPlaybackManager 回调，避免 @Singleton 持有已销毁的 ViewModel 导致泄漏与后台切歌失效
        audioPlaybackManager.onPlayNextRequest = null
        audioPlaybackManager.onPlayPreviousRequest = null
        audioPlaybackManager.onPlaybackError = null

        // HDR 播放已在 shouldCaptureThumbnailOnExit 中拦截（PixelCopy 对 HDR surface
        // 抓帧不可靠，走 getFrameAtTime 路径），lastFrameBitmap 为 null 时此处不会执行；
        // 快照 HDR 标志仅为防御性传递（player.release() 会清空 mediaInfo）。
        val isHdrPlayback = player.mediaInfo.value?.hdrType != null

        // 音频走 AudioPlaybackManager 的 ExoPlayer，视频走 NxPlayer；分别读取对应进度
        val position = if (isAudioPlayback) audioPlaybackManager.positionMs.value else player.positionMs.value
        val duration = if (isAudioPlayback) audioPlaybackManager.durationMs.value else player.durationMs.value
        // 切换后台播放时跳过 release，由 bridgeToBackgroundPlayback 暂停后台就绪后的 player
        if (!transitioningToBackground) {
            player.release()
        }
        // M-12 修复：释放 SubtitleEngine 持有的字幕数据（parsed List、startMsToIndex TreeMap），
        // 避免大字幕文件（含特效）的 ParsedCaption 列表延迟到 GC 才回收，占用数 MB 内存。
        subtitleEngine.clear()

        // BUG-19+23 修复：释放播放源持有的 Storage（SMB 连接等），避免泄漏。
        // 用独立 scope + NonCancellable 确保关闭完成（storage.close 是 suspend）。
        val storageToClose = currentStorage
        currentStorage = null
        if (storageToClose != null) {
            closeScope.launch(NonCancellable) {
                try { storageToClose.close() } catch (_: Exception) {}
            }
        }

        val history = currentHistory
        val storageId = history?.storageId
        if (history == null || storageId == null) return

        // BUG-H7 修复：onCleared 后 viewModelScope 已取消，改用独立协程作用域异步保存。
        // 原实现用 CoroutineScope(Dispatchers.IO + NonCancellable).launch 包裹全部逻辑，
        // 缩略图生成（storageFactory.create + generateThumbnailAtMs）可能因 SMB/WebDAV
        // 连接问题阻塞数分钟，NonCancellable 导致协程无法被取消，IO 线程被无限占用。
        // 改为分阶段保护：
        // - 进度保存（快）：withContext(NonCancellable) 确保完成
        // - 缩略图生成（慢）：withTimeoutOrNull(10s) 超时自动放弃，不阻塞 IO 线程
        //
        // M-11 修复：保留独立 CoroutineScope 设计（viewModelScope 已取消无法使用），
        // 但通过 withTimeoutOrNull 限制总执行时间，避免进程被杀时协程未完成导致进度丢失。
        // 进度保存用 NonCancellable 确保完成；缩略图生成用 withTimeoutOrNull 超时放弃。
        // O-13：使用注入的 AppCoroutineScope 替代游离 CoroutineScope(Dispatchers.IO)，
        // 该作用域生命周期与进程一致，onCleared 后仍可完成进度保存与缩略图生成。
        appScope.launch {
            // 1. 优先保存播放进度（upsert：有则 update，无则 insert 兜底）
            //    recordPlayStart 协程在 viewModelScope 中，用户快速退出时可能被取消，
            //    导致 DB 中无记录，此处必须兜底 insert，否则进度会静默丢失
            //    （WebDAV/SMB 音频起播快、用户易快速返回，是高发场景）
            withContext(NonCancellable) {
                saveProgressInternal(history, storageId, position, duration)
            }

            // 2. 视频缩略图生成（音频跳过：音频无视频帧，getFrameAtTime 必然返回 null，
            //    且对远程音频会建立 MediaDataSource 阻塞数秒）
            //    用 withTimeoutOrNull 包裹，超时后自动放弃，不阻塞 IO 线程；
            //    runCatching 隔离其他异常，任何失败都不影响上面已写入的进度
            if (!isAudioPlayback && position > 0 &&
                ThumbnailSettings.generateThumbnail && ThumbnailSettings.generateForVideo
            ) {
                // 播放后生成策略检查：非关闭模式均生成（文件已被读取，无额外封控风险）
                if (ThumbnailSettings.shouldGenerateOnPlayback(storageId)) {
                    withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
                        // BUG-01 修复：原 runCatching 无法释放 storage，改为 try-catch + 内层 try-finally：
                        // 外层 catch 吞异常（保持原"缩略图失败不影响退出"语义），内层 finally 关闭 storage。
                        try {
                            val library = mediaLibraryDao.getById(storageId) ?: return@withTimeoutOrNull
                            val storage = storageFactory.create(library) ?: return@withTimeoutOrNull
                        try {
                            val filePath = history.storagePath ?: return@withTimeoutOrNull
                            val existing = playHistoryDao.getPlayHistory(history.uniqueKey, storageId)
                            val fileName = existing?.videoName ?: history.url.substringAfterLast('/')
                            val file = MediaSourceBuilder.createVirtualFile(filePath, fileName)

                            val bitmap = lastFrameBitmap
                            if (bitmap != null) {
                                // HDR 修复：Dolby Vision / HDR10 / HLG 的 Surface 捕获是原始
                                // PQ/HLG 像素，需软件补偿后再保存，否则首页缩略图色彩失真
                                thumbnailManager.saveThumbnailFromBitmap(
                                    storageId = storageId,
                                    file = file,
                                    bitmap = bitmap,
                                    isHdr = isHdrPlayback,
                                )
                                // BUG-P4 修复：不手动 recycle，置 null 交给 GC 回收。
                                // 原 bitmap.recycle() 与 UI 层 dispose 存在竞态：
                                // PlayerScreen 在 onDispose 中通过 PixelCopy 截图并 setLastFrameBitmap，
                                // 若 onCleared 的 recycle 先于 Compose 完成对 bitmap 的最后一次绘制，
                                // 会触发 "Cannot draw a recycled Bitmap" IllegalStateException。
                                // Bitmap 的 native 内存由 GC 最终回收，延迟回收的内存开销可接受。
                                lastFrameBitmap = null
                            } else {
                                thumbnailManager.generateThumbnailAtMs(storage, storageId, file, position)
                            }
                            // BUG-T-m5 修复：上传到服务端 .thumb/ 目录，跨设备复用
                            // uploadThumbnail 内部会检查 saveInSameDir 和 cacheFile.exists()，
                            // 并通过 fileExists 避免覆盖服务端已有缩略图；失败不影响退出流程
                            // （外层 catch 已捕获）
                            thumbnailManager.uploadThumbnail(storage, file)
                        } finally {
                            storage.close()
                        }
                        } catch (_: Exception) {
                        // 缩略图生成失败不影响退出流程
                        }
                    }
                }
            }

            // 3. 播放器退出后触发播放历史云同步（若启用自动同步）。
            //    延迟等待上面的进度保存落库；失败静默，留待下次同步时机重试
            if (PlayHistorySyncSettings.autoSync) {
                delay(SYNC_DELAY_MS)
                syncManager.sync(auto = true)
            }
        }
    }
}

/**
 * 缩放模式常量，索引 0:1:2:3 对应「适应:裁剪:拉伸:16:9」。
 *
 * - Fit：media3 SCALE_TO_FIT，保持宽高比可能留黑边
 * - Crop：media3 SCALE_TO_FIT_WITH_CROPPING，裁剪填满
 * - Stretch：UI 层 SurfaceView 填满全屏，由 NxPlayer.videoScaleMode 状态驱动
 * - Ratio16_9：强制 16:9 显示比例，忽略视频原始宽高比
 */
private val SCALE_MODES = listOf(
    NxVideoScaleMode.Fit,
    NxVideoScaleMode.Crop,
    NxVideoScaleMode.Stretch,
    NxVideoScaleMode.Ratio16_9,
)

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * 周期性保存播放进度的时间间隔。
 *
 * BUG-10：30s 是平衡 DB 写入频率与磁盘/电量开销的折中值。
 * - 太短（如 5s）：频繁写 SQLite，低端设备可能卡顿
 * - 太长（如 5min）：进程被杀时丢失进度上限过大
 */
private const val PROGRESS_SAVE_INTERVAL_MS = 30_000L

/**
 * m-04 修复：进入播放页后首次保存进度的延迟。
 *
 * 原 [PROGRESS_SAVE_INTERVAL_MS] 首次延迟 30s 期间进程被杀会丢失全部进度。
 * 5s 足以让播放稳定（Buffering → Ready），写入初始位置作为快照。
 */
private const val PROGRESS_FIRST_SAVE_DELAY_MS = 5_000L

/**
 * onCleared 中缩略图生成的超时上限。
 *
 * BUG-H7：storageFactory.create（SMB/WebDAV 建连）+ generateThumbnailAtMs（远程取帧）
 * 在网络不佳时可能阻塞数分钟。10s 是平衡"尽量生成成功"与"不阻塞 IO 线程"的折中值：
 * - SMB 局域网建连 + 取帧通常 < 5s
 * - WebDAV 广域网可能 5-8s
 * - 超过 10s 视为网络异常，放弃生成（下次播放时会重新生成）
 */
private const val THUMBNAIL_TIMEOUT_MS = 10_000L

/** 播放器退出后自动同步的延迟（ms），等待退出时的进度保存落库。 */
private const val SYNC_DELAY_MS = 3_000L
