package com.nichx.niplayer.feature.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
import com.nichx.niplayer.database.entity.resumeStartPositionMs
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
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageAccess
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.subtitle.format.FormatASS
import com.nichx.niplayer.sync.PlayHistorySyncManager
import com.nichx.niplayer.thumbnail.ThumbnailManager
import com.nichx.niplayer.subtitle.format.FormatSRT
import com.nichx.niplayer.subtitle.renderer.SubtitleColor
import com.nichx.niplayer.subtitle.renderer.SubtitleEngine
import com.nichx.niplayer.subtitle.renderer.SubtitleStyleConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.Date
import javax.inject.Inject

/**
 * 播放器屏幕 ViewModel。
 *
 * 从 [PlaybackRequestHolder] 消费 [PlaybackRequest]（由文件浏览页 :feature:home 构造并写入），
 * 自动加载播放源。
 *
 * 生命周期：
 * - init 时 [PlaybackRequestHolder.consume] 取出请求（取出即清空，避免跨会话残留）
 * - 有请求时 setSource → seekTo（续播）→ prepare → play
 * - 无请求时（如直接进入播放路由而无待播放源）进入 [PlaybackState.Idle]，UI 显示提示
 * - [onCleared] 释放 [NxPlayer]，并保存最终播放进度到 play_history 表
 *
 * 播放历史记录：
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
     * @Volatile：UI 主线程写入与 onCleared 的 IO 协程读取保证可见性。
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
     * @Volatile：init 主线程写入与 onCleared IO 协程读取保证可见性。
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

    /**
     * PixelCopy 完成信号。
     *
     * UI 层 [setLastFrameBitmap] 回调（主线程）与 [onCleared] 的 IO 协程读取
     * 存在竞态：`capturedBack` 发起 [android.view.PixelCopy.request]（异步）后
     * 立即导航返回，[onCleared] 执行时回调可能尚未到达。此前读到 null 会误判
     * 为"未抓帧"而走远程取帧（SMB/WebDAV 需重新建连）。
     *
     * [onCleared] 通过 [awaitLastFrameBitmap] 创建此 latch 并短超时等待，
     * [setLastFrameBitmap] 完成它；等待超时后回退远程取帧。
     */
    @Volatile
    private var lastFrameLatch: CompletableDeferred<Unit>? = null

    /** 设置退出播放时的最后一帧 Bitmap，由 UI 层调用。 */
    fun setLastFrameBitmap(bitmap: Bitmap?) {
        lastFrameBitmap = bitmap
        lastFrameLatch?.let { if (!it.isCompleted) it.complete(Unit) }
    }

    /**
     * 等待 UI 层 PixelCopy 抓帧结果。
     *
     * - 已抓帧：直接返回
     * - 未抓帧：创建 latch 后 double-check（赋值 latch 前可能已完成），
     *   仍为空则等待 [setLastFrameBitmap] 完成；超时回退 [ThumbnailManager.generateThumbnailAtMs]
     *
     * @param timeoutMs 等待上限。PixelCopy 回调通常数十毫秒到达，等待仅用于
     *   覆盖异步竞态窗口。
     */
    private suspend fun awaitLastFrameBitmap(timeoutMs: Long): Bitmap? {
        var bitmap = lastFrameBitmap
        if (bitmap != null) return bitmap
        val latch = CompletableDeferred<Unit>()
        lastFrameLatch = latch
        bitmap = lastFrameBitmap
        if (bitmap == null) {
            withTimeoutOrNull(timeoutMs) { latch.await() }
            bitmap = lastFrameBitmap
        }
        return bitmap
    }

    /**
     * 退出播放时是否需要截取最后一帧作为缩略图。
     *
     * 与 [onCleared] 中缩略图生成条件对齐（总开关 + 视频开关 + 生成策略门控
     * [ThumbnailSettings.shouldGenerateOnPlayback]），供 UI 层在返回导航前决定
     * 是否执行 PixelCopy 抓帧，避免"关闭"策略下无谓的截图与 bitmap 分配。
     *
     * HDR 播放不抓帧（SurfaceView 表面是 10-bit HDR buffer，PixelCopy 在部分
     * 设备返回损坏数据），HDR 由 [onCleared] 走 [ThumbnailManager.generateThumbnailAtMs]
     * （API 34+ 系统自动 tone map HDR→SDR，颜色正确）。
     *
     * storageId 为 null（如本地文件）时返回 false，与 [onCleared] 生成条件一致
     * （无存储源不生成缩略图）。
     */
    fun shouldCaptureThumbnailOnExit(): Boolean {
        if (isAudioPlayback) return false
        if (!ThumbnailSettings.generateThumbnail || !ThumbnailSettings.generateForVideo) return false
        // 功能开关门控：仅当"退出时更新封面"开启时才需要抓帧，关闭时避免无谓的
        // SurfaceView 截图与 bitmap 分配（与 onCleared 生成条件保持一致）
        if (!ThumbnailSettings.updateOnExit) return false
        val sid = currentHistory?.storageId ?: return false
        if (!ThumbnailSettings.shouldGenerateOnPlayback(sid)) return false
        // HDR 拦截仅为性能优化（省一次抓帧与 bitmap 分配），正确性由 onCleared 兜底
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

    /**
     * 进入播放器前预读的视频显示宽高比（[PlaybackRequest.initialAspectRatio]，已含旋转校正）。
     *
     * 供"自动方向"模式在首帧渲染前直接锁定横/竖屏；null 表示未预读成功（无缩略图缓存且
     * 预读失败），UI 层回退到等 media3 [videoSize] 后再定方向。
     */
    private val _preReadAspectRatio = MutableStateFlow<Float?>(null)
    val preReadAspectRatio: StateFlow<Float?> = _preReadAspectRatio.asStateFlow()

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

    /** LRC 歌词已下沉 AudioPlaybackManager（UI 订阅 manager.lrcText），
     *  封面同理（manager.audioCoverPath），此处不再维护副本。 */

    /**
     * 外挂字幕渲染引擎。
     *
     * 替代 media3 TextRenderer 处理外挂字幕（ASS/SSA/SRT）：
     * - ASS 特效：通过 [SubtitleEngine] + [com.nichx.niplayer.subtitle.renderer.AssOverrideParser]
     *   解析 override tags（颜色/字体/位置/淡入淡出/移动），由 [com.nichx.niplayer.feature.player.SubtitleOverlay] 渲染
     * - 字幕偏移：[SubtitleEngine.update] 查询时使用 `positionMs + offsetMs`，正负偏移都精确生效
     *   （media3 无原生 setSubtitleOffsetMs API）
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
     * @Volatile：init 主线程 / playAtIndex 协程 / onCleared IO 协程多上下文读写保证可见性。
     * 配合 [playAtIndexMutex] 防止快速切歌竞态。
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
     * 最近一次播放请求。
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
     * 保护 playAtIndex 全流程的互斥锁。
     *
     * withLock 串行化整个 playAtIndex 流程：快速连续切歌时，确保前一次切换
     * 完全结束（含当前曲目进度保存）后才开始下一次，避免进度错存到新曲目。
     */
    private val playAtIndexMutex = kotlinx.coroutines.sync.Mutex()
    // 注：保留全限定名避免顶部再加一行 import Mutex；withLock 已 import

    /**
     * 当前播放源持有的 Storage 实例（仅 SMB/DocumentFile 等需要 DataSource 注入的协议）。
     *
     * playAtIndex / PlayStarter（经 PlaybackRequest.source）创建的 Storage 随
     * NxMediaSource.DataSource 一并传递到此，由本 ViewModel 统一管理：
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
        // 增大 buffer，避免快速连续截图时 tryEmit 丢弃事件
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val screenshotEvent: SharedFlow<String> = _screenshotEvent.asSharedFlow()

    /**
     * 用户可见消息事件（OSD / Toast），用于播放器内部错误反馈。
     *
     * 如「下一集」切集失败等错误通过它通知 UI 显示中文信息。
     */
    private val _messageEvent = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        // 增大 buffer，避免快速连续切换/重试时 tryEmit 丢弃提示
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
                appContext.getString(R.string.player_screenshot_saved, displayName)
            } catch (e: Exception) {
                appContext.getString(
                    R.string.player_screenshot_failed_generic,
                    e.message ?: appContext.getString(R.string.player_unknown_error),
                )
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

    /** 待下载文件信息（用户在目标选择弹窗确认后才真正入队）。 */
    private var pendingDownload: PendingDownload? = null

    /** 是否显示下载目标选择弹窗。 */
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()

    // endregion

    // region P0 续播提示

    /** 续播提示事件（携带已保存的播放位置 ms），供 UI 显示"接着上次看"对话框。 */
    private val _resumeEvent = MutableSharedFlow<Long>(
        extraBufferCapacity = 8,
        // 增大 buffer，避免快速连续 seek 时 tryEmit 丢弃
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
        // 增大 buffer，避免快速连续设置 A/B 点时 tryEmit 丢弃
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val abLoopEvent: SharedFlow<String> = _abLoopEvent.asSharedFlow()

    /**
     * 请求 UI 层重新触发黑边检测（PixelCopy）。
     *
     * 触发场景：用户从 Crop/Stretch 切回 Fit 时，[effectiveVideoSize] 已被清除，
     * 需要重新抓图检测。UI 层收到此事件后执行 PixelCopy → [applyBlackBarDetection]。
     * extraBufferCapacity=4 + DROP_OLDEST，快速触发多次时保留最新请求。
     */
    private val _redetectBlackBars = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val redetectBlackBars: SharedFlow<Unit> = _redetectBlackBars.asSharedFlow()

    /**
     * 黑边检测失败（画面全黑/太暗）时的自动重试请求。
     *
     * [applyBlackBarDetection] 检测到全黑或过暗画面（返回 null）时，若仍在播放且未超
     * 重试上限，通过本事件请求 UI 层延迟重新抓图（PixelCopy），等画面变亮后再检测。
     * 解决多数影片首帧是黑屏导致"智能去黑边"不生效、需手动关开开关重试的问题。
     */
    private val _blackBarRetry = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val blackBarRetry: SharedFlow<Unit> = _blackBarRetry.asSharedFlow()

    /** 黑边检测失败自动重试计数（检测成功 / 切源 / 功能关闭时清零）。 */
    private var blackBarRetryCount = 0

    /** 黑边检测失败自动重试上限（× UI 层重试间隔 ≈ 最长等待时间）。 */
    private val MAX_BLACK_BAR_RETRY = 8

    /** 以当前位置设置循环起点 A。若终点 B 已设置则自动启动循环。 */
    fun setAbLoopPointA() {
        val pos = player.positionMs.value
        _abLoopA.value = pos
        _abLoopEvent.tryEmit(appContext.getString(R.string.player_ab_loop_a_set_osd, formatTime(pos)))
        if (_abLoopB.value != null && _abLoopA.value != null) {
            startAbLoop()
        }
    }

    /** 以当前位置设置循环终点 B。若起点 A 已设置则自动启动循环。 */
    fun setAbLoopPointB() {
        val pos = player.positionMs.value
        _abLoopB.value = pos
        _abLoopEvent.tryEmit(appContext.getString(R.string.player_ab_loop_b_set_osd, formatTime(pos)))
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
        _abLoopEvent.tryEmit(appContext.getString(R.string.player_ab_loop_cleared_osd))
    }

    private fun startAbLoop() {
        abLoopJob?.cancel()
        val a = _abLoopA.value ?: return
        val b = _abLoopB.value ?: return
        if (b <= a) return
        abLoopJob = viewModelScope.launch {
            // 事件驱动：订阅 player.positionMs（由 positionTicker 每 500ms 更新，
            // onPositionDiscontinuity 时立即更新），到达 B 点立即 seekTo(A)。
            // 复用 positionMs 流，无独立轮询协程；流取消时协程自动结束。
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
            _preReadAspectRatio.value = request.initialAspectRatio
            // 保存请求副本，错误后重试使用
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
                // 不占用 NxPlayer，无需 bridgeToBackgroundPlayback；
                // history 一并传入，Manager 自维护当前历史（供切歌/进度保存使用）
                audioPlaybackManager.play(
                    source = request.source,
                    title = request.title,
                    coverPath = null,
                    artist = request.title,
                    startPositionMs = request.startPositionMs,
                    playlist = _playlist.value,
                    startIndex = _currentIndex.value,
                    history = request.history,
                )
                // 封面/歌词提取与加载已下沉 AudioPlaybackManager（play 内部异步触发）
                registerAudioCallbacks()
            } else {
                // 视频：使用 NxPlayer
                swapStorage(extractStorageFromSource(request.source))
                // 将 startPositionMs 直接传给 setSource，由 media3 在 prepare 时
                // 自动 seek 到此位置开始下载，避免先从 0 buffer 再被 seekTo 中断。
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
                    // 恢复播放时自动加载历史外挂字幕（仅视频）
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
        } ?: run {
            // 从 MusicBar 切回全屏：无新播放请求，但音频仍在后台播放。
            // ViewModel 重建后回调已在 onCleared 中置空，恢复会话并重注册，
            // 否则歌曲播完无法自动切歌、全屏「上一曲/下一曲」按钮也失效。
            restoreAudioSessionFromManager()
        }

        // 监听播放结束，自动播放下一首（仅视频，音频由 AudioPlaybackManager 的 STATE_ENDED 处理）
        viewModelScope.launch {
            player.state.collect { state ->
                if (state is PlaybackState.Ended && !isAudioPlayback) {
                    playNext()
                }
            }
        }

        // 订阅播放错误事件，解析 HTTP 错误码并通过 OSD 反馈给用户。
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

        // 周期性保存播放进度：首次延迟 [PROGRESS_FIRST_SAVE_DELAY_MS]（5s）写入初始位置，
        // 后续每 [PROGRESS_SAVE_INTERVAL_MS]（30s）保存，兜底进程被杀场景。
        // onCleared 仍是最终保存点，本协程只做中途快照。
        viewModelScope.launch {
            // 音频周期保存已下沉 AudioPlaybackManager 轮询协程（不依赖 ViewModel 存活，
            // MusicBar 场景下 ViewModel 销毁后仍持续落盘），此处仅服务视频。
            if (isAudioPlayback) return@launch
            // 首次延迟短，确保进入后立即有进度快照
            delay(PROGRESS_FIRST_SAVE_DELAY_MS)
            runCatching { saveProgress() }.onFailure { e ->
                android.util.Log.w("PlayerViewModel", "首次保存进度失败: ${e.message}", e)
            }
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                // 记录保存失败日志，便于排查进度丢失问题
                runCatching { saveProgress() }.onFailure { e ->
                    android.util.Log.w("PlayerViewModel", "周期性保存进度失败: ${e.message}", e)
                }
            }
        }

        // 订阅 PlaylistHolder 的流，接收 PlayStarter 延迟异步构造的播放列表
        // （SMB/WebDAV 大目录 listFiles 较慢），避免首页/历史恢复播放时连播列表丢失。
        viewModelScope.launch {
            playlistHolder.playlistFlow.collect { update ->
                val (items, startIndex) = update ?: return@collect
                applyLatePlaylist(items, startIndex)
                playlistHolder.clear()
            }
        }
    }

    /**
     * 注册 AudioPlaybackManager 回调，使播放器单例的事件由本 ViewModel 接管。
     *
     * 切歌能力已下沉到 Manager 内部（switchToIndex），此处仅订阅：
     * - onPlaybackError：展示 Snackbar 错误提示
     * - onTrackChanged：切歌成功后同步当前历史并刷新封面 / LRC
     *
     * 必须在音频会话有效时调用（首次播放请求或从 MusicBar 恢复会话），并仅在
     * [onCleared] 中置空，保证单例不持有已销毁 ViewModel 的引用。
     */
    private fun registerAudioCallbacks() {
        // 注册播放错误回调，通过 messageEvent 展示 Snackbar 提示
        audioPlaybackManager.onPlaybackError = { msg ->
            _messageEvent.tryEmit(msg)
        }
        // 提示类消息（如"已通过 API 获取歌词"）转为 Snackbar
        audioPlaybackManager.onMessage = { msg ->
            _messageEvent.tryEmit(msg)
        }
        // 切歌成功后同步当前历史描述符并刷新书签键（封面/歌词由 Manager 自管，
        // 分别经 audioCoverPath / lrcText 暴露，UI 订阅 StateFlow 即可，无需重复加载）
        audioPlaybackManager.onTrackChanged = { descriptor ->
            currentHistory = descriptor
            _currentBookmarkKey.value = descriptor.uniqueKey to (descriptor.storageId ?: -1)
        }
    }

    /**
     * 从 MusicBar 切回全屏时恢复 UI 状态。
     *
     * 场景：全屏播放器 → 切到 MusicBar（ViewModel 销毁，onCleared 置空回调）→ 点击
     * MusicBar 回全屏（ViewModel 重建，但 playbackRequestHolder 请求已消费，play()
     * 分支不执行）。此时音频仍由单例 AudioPlaybackManager 在后台播放，本方法从单例
     * 恢复播放列表/当前索引并重注册回调；切歌本身由 Manager 自管，无需重建会话。
     */
    private fun restoreAudioSessionFromManager() {
        if (!audioPlaybackManager.hasActiveAudio()) return
        val mgrPlaylist = audioPlaybackManager.playlist.value
        val mgrIndex = audioPlaybackManager.currentIndex.value
        val item = mgrPlaylist.getOrNull(mgrIndex) ?: return
        isAudioPlayback = true
        _playlist.value = mgrPlaylist
        _currentIndex.value = mgrIndex
        _title.value = item.fileName
        currentHistory = audioPlaybackManager.currentHistory
        _currentBookmarkKey.value = (currentHistory?.uniqueKey ?: "") to (currentHistory?.storageId ?: -1)
        registerAudioCallbacks()
        // 恢复路径不触发 onTrackChanged（无切歌）；封面/歌词均由 Manager 自管，
        // UI 订阅 audioCoverPath / lrcText 即可，此处无需主动刷新
    }

    /**
     * 将 [PlaybackException] 转换为用户可读的中文错误信息。
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
                401 -> appContext.getString(R.string.player_play_failed_401)
                403 -> appContext.getString(R.string.player_play_failed_403)
                404 -> appContext.getString(R.string.player_play_failed_404)
                in 500..599 -> appContext.getString(R.string.player_play_failed_server, httpCode)
                else -> appContext.getString(R.string.player_play_failed_http, httpCode)
            }
        }
        // 非 HTTP 错误：文件不存在 / 网络异常 / 解析错误 / 解码错误等
        val msg = cause.message ?: cause::class.simpleName ?: appContext.getString(R.string.player_unknown_error)
        return when (cause) {
            is java.io.FileNotFoundException -> appContext.getString(R.string.player_play_failed_file_not_found)
            is java.io.IOException -> appContext.getString(R.string.player_play_failed_network, msg)
            else -> appContext.getString(R.string.player_play_failed_generic, msg)
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
     * 只更新 playTime（刷新"最近播放"排序）与新记录初始值，不覆盖已有 videoPosition：
     * 进度保存完全交给 [saveProgress] / [onCleared]（它们读取 player 实际位置），
     * 避免"未实际播放却用 startPositionMs/0 覆盖已有进度"的窗口期。
     *
     * 新记录（existing==null）仍写入 startPositionMs 作为初始值。
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
        // 使用 @Transaction upsert，避免并发下 query-then-update/insert 窗口期
        // 导致 insert 冲突被 IGNORE 静默丢弃（如周期保存与 recordPlayStart 并发）
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
     * 应用延迟到达的播放列表（PlayStarter 异步构造场景）。
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
     * 播放下一首。
     *
     * 索引由 [AudioPlaybackManager] 按当前播放模式计算（顺序循环/随机/单曲循环），
     * 统一规则避免 UI 按钮与播放结束自动切换行为不一致。
     */
    fun playNext() {
        if (isAudioPlayback) {
            audioPlaybackManager.playNext()
        } else {
            val list = _playlist.value
            val nextIndex = _currentIndex.value + 1
            if (list.isEmpty() || nextIndex >= list.size) return
            playAtIndex(nextIndex)
        }
    }

    /** 播放上一首。已在列表首项时不做操作。 */
    fun playPrevious() {
        if (isAudioPlayback) {
            audioPlaybackManager.playPrevious()
        } else {
            val list = _playlist.value
            val prevIndex = _currentIndex.value - 1
            if (list.isEmpty() || prevIndex < 0) return
            playAtIndex(prevIndex)
        }
    }

    /**
     * 播放列表中指定索引的项。
     *
     * 音频项：切歌全流程已下沉 [AudioPlaybackManager.switchToIndex]（保存旧进度 → 查库 →
     * 重建 Storage → 建源 → 查续播位置 → 播放 → 记录历史 → 回调刷新封面/LRC），
     * 本方法仅转发并同步标题。
     *
     * 视频项：走 [playVideoAtIndex]（原逻辑，含 Mutex 串行化与进度保存）。
     */
    fun playAtIndex(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        val item = list[index]

        if (isAudioFile(item.fileName)) {
            _title.value = item.fileName
            viewModelScope.launch { audioPlaybackManager.switchToIndex(index) }
            return
        }
        playVideoAtIndex(index)
    }

    /**
     * 视频项切歌：查询存储源 → 重建 Storage → 构造 NxMediaSource → setSource → 播放。
     *
     * 切到新曲目前先保存当前曲目的播放进度，避免 currentHistory 被覆盖后旧进度丢失。
     */
    private fun playVideoAtIndex(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        val item = list[index]

        viewModelScope.launch {
            // 用 Mutex 串行化切歌全流程，避免快速连续切歌时进度保存错曲目
            playAtIndexMutex.withLock {
                try {
                    // 为即将被切走的视频异步更新最近播放帧缩略图，不阻塞切歌
                    scheduleSwitchOutThumbnail()
                    // 切歌前先保存当前曲目进度
                    saveProgressSync()

                    val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(item.libraryId) }
                        ?: return@withLock
                    val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
                        ?: return@withLock
                    val file = MediaSourceBuilder.createVirtualFile(item.filePath, item.fileName, item.fileSize)
                    val uniqueKey = "${library.id}:${item.filePath}"
                    // 传入 uniqueKey 作为 mediaId，让 media3 MediaItem.mediaId
                    // 与应用层 uniqueKey 一致，便于未来 MediaSession 集成。
                    val source = MediaSourceBuilder.buildMediaSource(storage, file, mediaId = uniqueKey)
                    val startPositionMs = withContext(Dispatchers.IO) {
                        playHistoryDao.getPlayHistory(uniqueKey, library.id)?.resumeStartPositionMs() ?: 0L
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
                        playlistId = currentHistory?.playlistId,
                    ).also {
                        _currentBookmarkKey.value = it.uniqueKey to it.storageId
                    }

                    isAudioPlayback = false
                    // 视频：使用 NxPlayer
                    swapStorage(extractStorageFromSource(source))
                    // 同 init 路径，startPositionMs 直接传给 setSource。
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

                    // 视频路径清理：封面/歌词由 Manager 自管（audioCoverPath/lrcText），
                    // 下次音频播放时在 play() 内自动刷新，此处无需清空副本
                    // 切歌后更新 lastPlaybackRequest，避免 retryPlayback/restartFromStart 复用已关闭的旧源
                    lastPlaybackRequest = PlaybackRequest(
                        source = source,
                        title = item.fileName,
                        startPositionMs = startPositionMs,
                        history = currentHistory,
                        isAudio = false,
                    )

                    recordPlayStart(currentHistory!!, item.fileName, startPositionMs)
                } catch (e: Exception) {
                    // CancellationException 必须重新抛出，遵守结构化并发
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // 切集失败不中断当前播放，但通过 messageEvent 通知 UI 显示错误信息
                    android.util.Log.w("PlayerViewModel", "playAtIndex($index) failed", e)
                    _messageEvent.tryEmit(appContext.getString(R.string.player_switch_failed, e.message ?: e::class.simpleName))
                }
            }
        }
    }

    /**
     * 从 [NxMediaSource] 提取携带的 [com.nichx.niplayer.storage.Storage]。
     *
     * 仅 [NxMediaSource.DataSource] 类型携带 storage 引用，HTTP/Local 类型不携带。
     */
    private fun extractStorageFromSource(
        source: com.nichx.niplayer.player.kernel.NxMediaSource,
    ): com.nichx.niplayer.storage.Storage? {
        return (source as? com.nichx.niplayer.player.kernel.NxMediaSource.DataSource)?.storage
    }

    /**
     * 切换 [currentStorage]：先关闭旧 storage（异步），再赋值新 storage。
     *
     * - 旧 storage 关闭用独立 closeScope（Dispatchers.IO），避免 viewModelScope
     *   取消时阻塞；storage.close() 是 suspend，需在协程中调用
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
     * 错误状态下用户点"重试"调用。
     *
     * 用 [lastPlaybackRequest] 的原始 startPositionMs 重新装载播放源。
     * 适用于 SMB/WebDAV/FTP 临时断连、解码失败等场景。
     */
    fun retryPlayback() {
        val request = lastPlaybackRequest ?: return
        viewModelScope.launch {
            // 重新装载 source（NxMediaSource 已闭包 storage，无需重新创建）
            // setSource/prepare 会清零错误状态，UI 从 Error → Buffering
            player.setSource(request.source, request.startPositionMs)
            player.prepare()
            player.play()
        }
    }

    /**
     * 错误状态下用户点"从头播放"调用。
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
     * 下载当前播放文件：先校验共享存储写入权限，再弹出「预设 / 选择」目标选择器。
     * 实际入队由 [downloadToPreset] / [downloadToPath] 在用户确认目标后触发。
     */
    fun requestDownload() {
        if (!StorageAccess.canWriteSharedStorage(appContext)) {
            _downloadEvent.tryEmit(appContext.getString(R.string.player_download_no_permission))
            return
        }
        if (currentHistory == null) return
        val storageId = currentHistory!!.storageId ?: return
        val filePath = currentHistory!!.storagePath ?: return
        val fileName = _title.value.ifEmpty { currentHistory!!.url.substringAfterLast('/') }
        val uniqueKey = "${storageId}:$filePath"
        pendingDownload = PendingDownload(
            storageId = storageId,
            filePath = filePath,
            fileName = fileName,
            fileSize = currentHistory!!.fileSize,
            uniqueKey = uniqueKey,
        )
        _showDownloadDialog.value = true
    }

    /** 下载到预设下载目录。 */
    fun downloadToPreset() {
        _showDownloadDialog.value = false
        val pending = pendingDownload ?: return
        pendingDownload = null
        downloadManager.addTask(
            storageId = pending.storageId,
            filePath = pending.filePath,
            fileName = pending.fileName,
            uniqueKey = pending.uniqueKey,
            totalBytes = pending.fileSize,
            targetStorageUrl = DownloadSettings.downloadDirTargetUrl,
            targetStorageName = DownloadSettings.downloadDirName,
        )
        _downloadEvent.tryEmit(appContext.getString(R.string.player_added_to_download_queue))
    }

    /**
     * 下载到指定目录。
     *
     * @param setAsPreset 同时将所选目录保存为预设下载目录
     */
    fun downloadToPath(path: String, dirName: String, setAsPreset: Boolean) {
        _showDownloadDialog.value = false
        val pending = pendingDownload ?: return
        pendingDownload = null
        if (setAsPreset) DownloadSettings.setDownloadDir(path, dirName)
        downloadManager.addTask(
            storageId = pending.storageId,
            filePath = pending.filePath,
            fileName = pending.fileName,
            uniqueKey = pending.uniqueKey,
            totalBytes = pending.fileSize,
            targetStorageUrl = "file://$path",
            targetStorageName = dirName,
        )
        _downloadEvent.tryEmit(appContext.getString(R.string.player_added_to_download_queue))
    }

    /** 关闭下载目标选择弹窗。 */
    fun closeDownloadDialog() {
        _showDownloadDialog.value = false
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
            blackBarRetryCount = 0
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
                    // 全黑/过暗导致检测失败：若仍在播放且未超重试上限，请求 UI 层延迟
                    // 重新抓图，等画面变亮后再检测（多数影片首帧是黑屏，需自动重试）
                    if (PlayerSettings.autoDetectBlackBars &&
                        state.value is PlaybackState.Playing &&
                        blackBarRetryCount < MAX_BLACK_BAR_RETRY
                    ) {
                        blackBarRetryCount++
                        _blackBarRetry.tryEmit(Unit)
                    }
                }
                return@launch
            }

            // 检测到有效画面区域：重置重试计数
            blackBarRetryCount = 0

            // 用检测到的有效像素区域重算 VideoSize
            // pixelWidthHeightRatio 保持原值（黑边不影响像素形状）
            val rectAspect = rect.width.toFloat() / rect.height
            val videoAspect = currentSize.width.toFloat() / currentSize.height
            // 用阈值比较（3%），避免采样误差导致误判：
            // BlackBarDetector 用 SAMPLE_STEP=4 下采样，rect 比例可能与真实比例有微小差异，
            // 精确比较 != 会让无黑边视频也触发 effectiveVideoSize 更新，导致画面比例变化两次
            val differsFromContainer = kotlin.math.abs(rectAspect - videoAspect) > 0.03f
            // 裁剪白名单闸门（参考 mpv dynamic-crop.lua）：只有当内容区域命中已知成品比例时
            // 才允许裁剪。字幕/纵向文字等把画面边缘误判成"黑边"时，得到的裁剪矩形是
            // 不成比例的形状，命中不了白名单，从而被否决、不裁剪，避免真实画面被裁残缺。
            val longSide = maxOf(rect.width, rect.height)
            val shortSide = minOf(rect.width, rect.height)
            val knownAspect = BlackBarDetector.matchesKnownAspect(longSide, shortSide)
            val hasBlackBars = differsFromContainer && knownAspect

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
        blackBarRetryCount = 0
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
     * 解析成功后复制到持久目录 `files/subtitles/` 并经 [PlayHistoryDao.updateSubtitle]
     * 持久化路径，恢复播放时自动加载，避免用户每次重新搜索/选择字幕。
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

                // 解析成功后持久化字幕到内部存储，并更新历史记录
                persistSubtitle(tempFile, mimeType)
            } catch (e: Exception) {
                // CancellationException 必须重新抛出，遵守结构化并发
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
     * 将字幕从临时缓存复制到持久目录，确保进程重启后仍可加载。
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
            // CancellationException 必须重新抛出，遵守结构化并发
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 持久化失败不影响当前播放，仅无法恢复字幕
        }
    }

    /**
     * 从持久化路径加载外挂字幕。
     *
     * 恢复播放时，历史记录含 subtitlePath 则调用本方法自动加载。
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
                // CancellationException 必须重新抛出，遵守结构化并发
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 恢复失败静默忽略
            }
        }
    }

    /**
     * 清除外挂字幕，并同步重置 player 的 subtitleOffsetMs。
     *
     * 避免 SubtitleEngine._offsetMs=0 而 player 仍保留旧值，导致下次
     * adjustSubtitleOffset 基于旧值计算。
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
            // CancellationException 必须重新抛出，遵守结构化并发
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * 保存当前播放进度到 play_history（upsert）。
     *
     * onCleared 之外的中途保存点：周期性保存（[init] 中每 [PROGRESS_SAVE_INTERVAL_MS] 一次）、
     * onPause 保存（UI 层 [androidx.lifecycle.Lifecycle.Event.ON_PAUSE] 时调用）、
     * 切歌前 [saveProgressSync] 同步保存。
     *
     * 与 [onCleared] 不同：不释放 player，不生成缩略图，仅写 DB。多次调用安全（幂等 upsert）。
     */
    fun saveProgress() {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        // 音频走 AudioPlaybackManager 的 ExoPlayer，进度与历史均由 Manager 自维护；
        // 此处仅对视频（NxPlayer）做中途进度保存。
        if (isAudioPlayback) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                audioPlaybackManager.saveCurrentProgress()
            }
            return
        }
        // Error / Idle 状态下 player.positionMs 可能为 0（如 SMB 断网后归零），
        // 若此时保存会用 0 覆盖 DB 已有进度。仅在播放中/暂停/就绪/缓冲状态下保存。
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
        // 双重保险：即使状态判断通过，position=0 且已有进度时也跳过，
        // 避免覆盖 DB 已有进度；仅新记录（position 本就该是 0）不受影响
        if (position <= 0) return
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            saveProgressInternal(history, storageId, position, duration)
        }
    }

    /**
     * 同步保存当前播放进度，用于切歌场景。
     *
     * 在调用方协程上下文执行，不启动新协程，确保切换前保存完成。
     * 同 [saveProgress]，Error/Idle 状态下跳过，避免用 0 覆盖进度。
     */
    private suspend fun saveProgressSync() {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        // 音频切歌的进度保存在 AudioPlaybackManager.switchToIndex 内部完成，
        // 此方法仅服务视频切歌场景（NxPlayer 状态机）。
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
     * 当 [position] 为 0 且 DB 已有记录时**不覆盖**已有进度，避免 onCleared 兜底
     * 保存时 player 处于 Error 导致进度归零；新记录（existing==null）仍写入 0。
     *
     * 使用 [PlayHistoryDao.upsertProgress]（@Transaction 包裹 query+update/insert），
     * Room 在数据库层加事务锁串行化，保证并发安全。
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

    /**
     * 连续播放切歌前，为即将被切走的视频异步更新最近播放帧缩略图。
     *
     * 切走前同步快照出库视频信息，再经 appScope 异步执行（远程取帧可能耗时数秒，
     * 不阻塞切歌）。音频切走（[isAudioPlayback]）跳过。
     */
    private fun scheduleSwitchOutThumbnail() {
        val history = currentHistory ?: return
        val storageId = history.storageId ?: return
        if (isAudioPlayback) return
        val filePath = history.storagePath ?: return
        val position = player.positionMs.value
        if (position <= 0) return
        appScope.launch {
            updatePlaybackThumbnail(
                history = history,
                storageId = storageId,
                filePath = filePath,
                position = position,
                bitmap = null,
                isHdrPlayback = false,
            )
        }
    }

    /**
     * 为已播放过的视频生成/更新缩略图（最近播放帧）并上传服务端。
     *
     * [bitmap] 非空且非 HDR 时用 UI 层 PixelCopy 抓帧（质量更高），否则走远程取帧
     * [ThumbnailManager.generateThumbnailAtMs]。超时/失败静默，不影响主流程。
     *
     * @param bitmap PixelCopy 抓帧（退出播放非 HDR 路径使用）；null 走远程取帧
     * @param isHdrPlayback 当前播放是否 HDR（HDR 一律走远程取帧）
     */
    private suspend fun updatePlaybackThumbnail(
        history: HistoryDescriptor,
        storageId: Int,
        filePath: String,
        position: Long,
        bitmap: Bitmap?,
        isHdrPlayback: Boolean,
    ) {
        // 门控：总开关 + 视频开关 + 退出时更新开关 + 播放后生成策略
        if (!ThumbnailSettings.generateThumbnail || !ThumbnailSettings.generateForVideo ||
            !ThumbnailSettings.updateOnExit
        ) return
        if (!ThumbnailSettings.shouldGenerateOnPlayback(storageId)) return
        if (filePath.isBlank()) return

        // 生成后 storage 暂不关闭，交给下方上传阶段完成后再关
        var uploadTask: Pair<Storage, StorageFile>? = null
        withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
            try {
                val library = mediaLibraryDao.getById(storageId) ?: return@withTimeoutOrNull
                val storage = storageFactory.create(library) ?: return@withTimeoutOrNull
                var storageTransferred = false
                try {
                    val existing = playHistoryDao.getPlayHistory(history.uniqueKey, storageId)
                    val fileName = existing?.videoName ?: history.url.substringAfterLast('/')
                    val file = MediaSourceBuilder.createVirtualFile(filePath, fileName)

                    if (bitmap != null && !isHdrPlayback) {
                        thumbnailManager.saveThumbnailFromBitmap(
                            storageId = storageId,
                            file = file,
                            bitmap = bitmap,
                            isHdr = false,
                        )
                        lastFrameBitmap = null
                    } else {
                        thumbnailManager.generateThumbnailAtMs(storage, storageId, file, position)
                    }
                    uploadTask = storage to file
                    storageTransferred = true
                } finally {
                    if (!storageTransferred) storage.close()
                }
            } catch (_: Exception) {
                // 缩略图生成失败不影响主流程
            }
        }

        // 上传独立执行，不挤占生成超时预算；失败静默
        uploadTask?.let { (storage, file) ->
            try {
                withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    thumbnailManager.uploadThumbnail(storage, file)
                }
            } catch (_: Exception) {
            } finally {
                try { storage.close() } catch (_: Exception) {}
            }
        }
    }

    override fun onCleared() {
        // 清理 AudioPlaybackManager 回调，避免 @Singleton 持有已销毁的 ViewModel 导致泄漏
        audioPlaybackManager.onPlaybackError = null
        audioPlaybackManager.onMessage = null
        audioPlaybackManager.onTrackChanged = null

        // 快照 HDR 标志（player.release() 会清空 mediaInfo）。
        // 非 HDR 才用 PixelCopy bitmap，HDR（DV/HDR10/HLG）一律走
        // generateThumbnailAtMs（API 34+ 由系统自动 tone map HDR→SDR，颜色正确）。
        val hdrTypePlayback = player.mediaInfo.value?.hdrType
        val isHdrPlayback = hdrTypePlayback != null

        // 音频走 AudioPlaybackManager 的 ExoPlayer，视频走 NxPlayer；分别读取对应进度
        val position = if (isAudioPlayback) audioPlaybackManager.positionMs.value else player.positionMs.value
        val duration = if (isAudioPlayback) audioPlaybackManager.durationMs.value else player.durationMs.value
        // 切换后台播放时跳过 release，由 bridgeToBackgroundPlayback 暂停后台就绪后的 player
        if (!transitioningToBackground) {
            player.release()
        }
        // 释放 SubtitleEngine 持有的字幕数据，避免大字幕文件的列表占内存
        subtitleEngine.clear()

        // 释放播放源持有的 Storage（SMB 连接等），避免泄漏。
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
        // 音频场景的 currentHistory 与 Manager 内部历史同步（restore/onTrackChanged 均赋值），
        // history==null 时 Manager 侧同样无历史可保存，故统一用简单非空 guard 提前返回，
        // 保证 Kotlin 对 val 的 smart-cast 在下方 appScope.launch 闭包内仍然生效。
        if (history == null || storageId == null) return

        // viewModelScope 在 onCleared 已取消，改用应用级 appScope 异步保存：
        // - 进度保存（快）：withContext(NonCancellable) 确保完成
        // - 缩略图生成（慢）：withTimeoutOrNull(10s) 超时放弃，不阻塞 IO 线程
        appScope.launch {
            // 1. 保存播放进度（upsert，缺失时兜底 insert，避免静默丢失）
            withContext(NonCancellable) {
                // 音频进度由 Manager 自维护（currentHistory 在 Manager 内部），
                // 视频仍由 ViewModel 直接落库
                if (isAudioPlayback) {
                    audioPlaybackManager.saveCurrentProgress()
                } else {
                    saveProgressInternal(history, storageId, position, duration)
                }
            }

            // 2. 视频缩略图生成（"退出时更新封面"开启时用最后一帧覆盖缩略图）
            if (!isAudioPlayback && position > 0 &&
                ThumbnailSettings.generateThumbnail && ThumbnailSettings.generateForVideo &&
                ThumbnailSettings.updateOnExit &&
                ThumbnailSettings.shouldGenerateOnPlayback(storageId)
            ) {
                // 优先用 UI 层 PixelCopy 抓帧（非 HDR 时质量更高），等待短超时后交给公共方法
                val bitmap = awaitLastFrameBitmap(FRAME_WAIT_MS)

                updatePlaybackThumbnail(
                    history = history,
                    storageId = storageId,
                    filePath = history.storagePath ?: "",
                    position = position,
                    bitmap = bitmap,
                    isHdrPlayback = isHdrPlayback,
                )
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

/** 待下载文件信息，用于用户在选择下载目标后真正入队。 */
private data class PendingDownload(
    val storageId: Int,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val uniqueKey: String,
)

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
 * 30s 是平衡 DB 写入频率与磁盘/电量开销的折中值：
 * - 太短（如 5s）：频繁写 SQLite，低端设备可能卡顿
 * - 太长（如 5min）：进程被杀时丢失进度上限过大
 */
private const val PROGRESS_SAVE_INTERVAL_MS = 30_000L

/**
 * 进入播放页后首次保存进度的延迟。
 *
 * 5s 足以让播放稳定（Buffering → Ready），避免前 5s 内进程被杀丢失初始进度快照。
 */
private const val PROGRESS_FIRST_SAVE_DELAY_MS = 5_000L

/**
 * onCleared 中缩略图生成的超时上限。
 *
 * storageFactory.create（SMB/WebDAV 建连）+ generateThumbnailAtMs（远程取帧）
 * 在网络不佳时可能阻塞数分钟。10s 是平衡"尽量生成成功"与"不阻塞 IO 线程"的折中值。
 */
private const val THUMBNAIL_TIMEOUT_MS = 10_000L

/**
 * 退出时缩略图上传（uploadThumbnail）的独立超时上限。
 *
 * 上传移出生成超时块后单独执行，避免 HDR 远程取帧（最慢路径）挤占上传预算。
 * 10s 与生成超时一致，覆盖 SMB/WebDAV 建目录 + fileExists + 写文件往返。
 */
private const val UPLOAD_TIMEOUT_MS = 10_000L

/**
 * 退出时等待 PixelCopy 抓帧结果的上限。
 *
 * 发起 PixelCopy.request（异步）后立即导航返回，onCleared 执行时回调可能未到达；
 * PixelCopy 回调通常数十毫秒到达，300ms 足够覆盖竞态窗口，超时回退远程取帧。
 */
private const val FRAME_WAIT_MS = 300L

/** 播放器退出后自动同步的延迟（ms），等待退出时的进度保存落库。 */
private const val SYNC_DELAY_MS = 3_000L
