package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.MediaFileTypes.isImageFile
import com.nichx.niplayer.feature.home.PlayStartResult
import com.nichx.niplayer.feature.home.PlayStarter
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessUiItem
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.thumbnail.RemoteThumbnailRequest
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 首页 Tab 的 ViewModel。
 *
 * 聚合两类数据：
 * - **最近播放**：[PlayHistoryDao.getRecentFlow] 订阅最近 [RECENT_WINDOW] 条播放记录
 * - **快速访问**：[QuickAccessDao.getRecentFlow] 订阅最近 [QUICK_ACCESS_LIMIT] 条书签，
 *   [combine] 关联 [MediaLibraryDao.getAll] 获取存储源显示名与有效性
 *
 * 续播（P1）：[resumePlay] 委托 [PlayStarter.startFromHistory] 构造 PlaybackRequest 并写入
 * [com.nichx.niplayer.player.kernel.PlaybackRequestHolder]，成功后 emit
 * [HomeTabEvent.NavigateToPlayer] 供 UI 导航。
 *
 * 快速访问打开（P1）：[openQuickAccessItem] 按类型分流——文件夹 emit
 * [HomeTabEvent.NavigateToStorageFile]，文件委托 [PlayStarter.startFromQuickAccess]。
 */
@HiltViewModel
class HomeTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    playHistoryDao: PlayHistoryDao,
    quickAccessDao: QuickAccessDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val playStarter: PlayStarter,
    private val storageFactory: StorageFactory,
    private val thumbnailManager: ThumbnailManager,
    private val videoDao: VideoDao,
) : ViewModel() {

    private val recentFlow = playHistoryDao.getRecentFlow(RECENT_WINDOW)
    private val quickFlow = quickAccessDao.getRecentFlow(QUICK_ACCESS_LIMIT)
    private val shieldedPathsFlow = videoDao.getAllFolder()
    /** 全部播放历史（用于统计各类型总数，展示在分区标题；展示行仍由 [recentFlow] 截断）。 */
    private val allHistoryFlow = playHistoryDao.getAllFlow()

    /**
     * 首页统一展示态：一次 combine 产出「已加载 + 最近播放 + 快速访问 + 各类型总数」。
     *
     * 让 dataReady 与列表从同一帧 emit，避免「dataReady 用原始流、列表用派生态」导致的首帧闪空；
     * 各派生子流由 [distinctUntilChanged] 去重，避免无关源变化触发多余的下游刷新。
     */
    private val homeUi: StateFlow<HomeUiState> = combine(
        recentFlow, quickFlow, mediaLibraryDao.getAll(), shieldedPathsFlow, allHistoryFlow,
    ) { histories, quickList, libraries, folders, allHistory ->
        val shielded = folders.filter { it.isFilter }.map { it.folderPath }.toSet()
        val recent = if (shielded.isEmpty()) histories
        else histories.filter { h ->
            h.storagePath == null || shielded.none { prefix -> h.storagePath!!.startsWith(prefix) }
        }
        val libMap = libraries.associateBy { it.id }
        HomeUiState(
            loaded = true,
            recent = recent,
            videoCount = allHistory.count { !MediaFileTypes.isAudioFile(it.videoName) },
            audioCount = allHistory.count { MediaFileTypes.isAudioFile(it.videoName) },
            quick = quickList.map { entity ->
                val lib = libMap[entity.libraryId]
                QuickAccessUiItem(
                    entity = entity,
                    libraryName = lib?.displayName,
                    libraryValid = lib != null,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(),
    )

    /** 视频历史总数（展示于分区标题，作"有界预览 + 查看全部"的提示）。 */
    val videoHistoryCount: StateFlow<Int> = homeUi
        .map { it.videoCount }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 音频历史总数（展示于分区标题）。 */
    val audioHistoryCount: StateFlow<Int> = homeUi
        .map { it.audioCount }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 标记数据是否已就绪（随 [homeUi] 同步下发，避免首次加载展示空状态）。 */
    val dataReady: StateFlow<Boolean> = homeUi
        .map { it.loaded }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 最近播放列表（已过滤屏蔽目录），WhileSubscribed(5000) 避免配置变更时重启 Flow。 */
    val recentPlays: StateFlow<List<PlayHistoryEntity>> = homeUi
        .map { it.recent }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 最近播放 - 视频（已过滤屏蔽目录），行内有界预览 [ROW_PREVIEW_LIMIT] 条。 */
    val recentVideoPlays: StateFlow<List<PlayHistoryEntity>> = homeUi
        .map { it.recent.filter { h -> !MediaFileTypes.isAudioFile(h.videoName) }.take(ROW_PREVIEW_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 最近播放 - 音频（已过滤屏蔽目录），行内有界预览 [ROW_PREVIEW_LIMIT] 条。 */
    val recentAudioPlays: StateFlow<List<PlayHistoryEntity>> = homeUi
        .map { it.recent.filter { h -> MediaFileTypes.isAudioFile(h.videoName) }.take(ROW_PREVIEW_LIMIT) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 首页快速访问列表，关联存储源显示名与有效性，仅展示最近 [QUICK_ACCESS_LIMIT] 条。 */
    val quickAccessItems: StateFlow<List<QuickAccessUiItem>> = homeUi
        .map { it.quick }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 存储源可达性：storageId → 是否可达。
     *
     * 首页加载时自动验证：库存在性（DB 查询）+ 远程连接（SMB/WebDAV testConnection）。
     * UI 层据此对不可达条目显示视觉提示（降低透明度、提示文字）。
     */
    private val _storageReachability = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val storageReachability: StateFlow<Map<Int, Boolean>> = _storageReachability.asStateFlow()

    /** 远程存储文件的缩略图路径映射：播放记录的 url → 本地缓存路径。 */
    private val _thumbnailUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val thumbnailUrls: StateFlow<Map<String, String>> = _thumbnailUrls.asStateFlow()

    /** 快速访问缩略图路径映射：storagePath → 本地缓存路径。 */
    private val _qaThumbnailUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val qaThumbnailUrls: StateFlow<Map<String, String>> = _qaThumbnailUrls.asStateFlow()

    /** 下拉刷新状态：true 时首页顶部显示刷新指示器。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            recentPlays.collect { plays ->
                val currentUrls = plays.map { it.url }.toSet()
                if (_thumbnailUrls.value.keys.any { it !in currentUrls }) {
                    _thumbnailUrls.value = _thumbnailUrls.value.filterKeys { it in currentUrls }
                }

                // 先扫描本地缓存（视频 + 音频），已存在的缩略图立即可用
                val uncached = plays.filter { it.url !in _thumbnailUrls.value }
                    .filter { it.storageId != null && !it.storagePath.isNullOrEmpty() }
                val cached = uncached.mapNotNull { item ->
                    val path = if (MediaFileTypes.isAudioFile(item.videoName)) {
                        thumbnailManager.getCachedAudioCoverPath(
                            item.storageId!!, item.storagePath!!
                        )
                    } else {
                        thumbnailManager.getCachedThumbnailPath(
                            item.storageId!!, item.storagePath!!
                        )
                    }
                    if (path != null) item.url to path else null
                }.toMap()
                if (cached.isNotEmpty()) {
                    _thumbnailUrls.update { it + cached }
                }

                // 仅对无本地缓存的项走远程生成
                generateRemoteThumbnails(plays, _thumbnailUrls.value)
            }
        }

        // 快速访问缩略图：收集 quickAccessItems 并生成缩略图
        viewModelScope.launch {
            quickAccessItems.collect { items ->
                val currentPaths = items.map { it.entity.storagePath }.toSet()
                if (_qaThumbnailUrls.value.keys.any { it !in currentPaths }) {
                    _qaThumbnailUrls.value = _qaThumbnailUrls.value.filterKeys { it in currentPaths }
                }

                val mediaItems = items.filter {
                    it.libraryValid && !it.entity.isDirectory &&
                        it.entity.storagePath.isNotEmpty() &&
                        it.entity.storagePath !in _qaThumbnailUrls.value
                }
                if (mediaItems.isEmpty()) return@collect

                val cached = mediaItems.mapNotNull { item ->
                    val name = item.entity.name
                    val sid = item.entity.libraryId
                    val path = item.entity.storagePath
                    val thumbPath = if (MediaFileTypes.isAudioFile(name)) {
                        thumbnailManager.getCachedAudioCoverPath(sid, path)
                    } else if (MediaFileTypes.isImageFile(name)) {
                        thumbnailManager.getCachedImageThumbnailPath(sid, path)
                    } else {
                        thumbnailManager.getCachedThumbnailPath(sid, path)
                    }
                    if (thumbPath != null) path to thumbPath else null
                }.toMap()
                if (cached.isNotEmpty()) {
                    _qaThumbnailUrls.update { it + cached }
                }

                generateQuickAccessThumbnails(mediaItems, _qaThumbnailUrls.value)
            }
        }

        // 首页加载时验证存储源可达性（仅一次；下拉刷新会再次验证）
        viewModelScope.launch {
            recentPlays.collect {
                if (!connectionsValidated) {
                    connectionsValidated = true
                    validateStorageConnections()
                }
            }
        }

        // 缩略图更新（如退出播放后保存最后一帧）后刷新对应条目，
        // 追加 ?t=timestamp 触发 Compose 重组并绕过 Coil 内存缓存
        viewModelScope.launch {
            thumbnailManager.thumbnailUpdated.collect { updatedPath ->
                val cleanPath = updatedPath.substringBefore("?t=")
                val entry = _thumbnailUrls.value.entries.find {
                    it.value.substringBefore("?t=") == cleanPath
                }
                if (entry != null) {
                    _thumbnailUrls.update {
                        it + (entry.key to "$cleanPath?t=${System.currentTimeMillis()}")
                    }
                }
            }
        }
    }

    /**
     * 为最近播放记录生成缩略图。
     *
     * BUG-H4 修复：委托 [ThumbnailManager.generateRemoteThumbnails] 批量生成，
     * 按 storageId 分组，每组创建 Storage 实例后传入。ViewModel 仅负责实体转换
     * 与 URL→path 映射 emit，不再持有预加载 / Semaphore / 视频音频分流逻辑。
     *
     * BUG-T-m7 修复：用 batchAccumulator + 节流 flush 合并 onLoaded 回调，
     * 避免 100 个缓存命中 = 100 次 StateFlow emit + 100 次 Map 全量拷贝。
     * flusher 协程每 250ms 批量提交一次，函数返回前最后 flush 一次确保不丢结果。
     */
    private suspend fun generateRemoteThumbnails(
        plays: List<PlayHistoryEntity>,
        existingThumbnails: Map<String, String>,
    ) {
        val pending = plays.filter {
            it.storageId != null && !it.storagePath.isNullOrEmpty() && it.url !in existingThumbnails
        }
        if (pending.isEmpty()) return

        val byStorage = pending.groupBy { it.storageId!! }
        // BUG-T-m7 修复：批量合并 onLoaded 回调，减少 StateFlow emit 次数
        val batchAccumulator = java.util.Collections.synchronizedMap(mutableMapOf<String, String>())
        coroutineScope {
            val flusher = launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flushThumbnailBatch(batchAccumulator)
                }
            }
            try {
                for ((sid, items) in byStorage) {
                    try {
                        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(sid) } ?: continue
                        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) } ?: continue
                        // BUG-01 修复：缩略图批量生成完成后关闭 Storage，避免 SMB/WebDAV 连接泄漏。
                        // 内层 try-finally 保证 storage 在任何路径（含异常）下都被关闭。
                        try {
                            val requests = items.map { item ->
                                RemoteThumbnailRequest(
                                    storageId = sid,
                                    filePath = item.storagePath ?: "",
                                    fileName = item.videoName,
                                    url = item.url,
                                    isAudio = MediaFileTypes.isAudioFile(item.videoName),
                                )
                            }
                            thumbnailManager.generateRemoteThumbnails(storage, requests) { url, thumbPath ->
                                batchAccumulator[url] = thumbPath
                            }
                        } finally {
                            storage.close()
                        }
                    } catch (_: Exception) { continue }
                }
            } finally {
                flusher.cancel()
                // 最后 flush 一次，确保所有结果都已提交
                flushThumbnailBatch(batchAccumulator)
            }
        }
    }

    /**
     * 批量提交累积的缩略图结果到 [_thumbnailUrls]。
     *
     * BUG-T-m7 修复：通过快照 + clear 原子化取出累积结果，一次性 update 到 StateFlow，
     * 避免每次 onLoaded 都触发 emit + Map 全量拷贝。
     */
    private fun flushThumbnailBatch(accumulator: MutableMap<String, String>) {
        val batch = synchronized(accumulator) {
            if (accumulator.isEmpty()) return@synchronized null
            val snapshot = accumulator.toMap()
            accumulator.clear()
            snapshot
        } ?: return
        if (batch.isNotEmpty()) {
            _thumbnailUrls.update { it + batch }
        }
    }

    /**
     * 为快速访问中的媒体文件生成缩略图。
     * 按 storageId 分组，每组创建 Storage 实例后委托 [ThumbnailManager.generateRemoteThumbnails]。
     * 使用 storagePath 作为回调 key 映射到 _qaThumbnailUrls。
     */
    private suspend fun generateQuickAccessThumbnails(
        items: List<QuickAccessUiItem>,
        existingThumbnails: Map<String, String>,
    ) {
        val pending = items.filter {
            it.libraryValid && !it.entity.isDirectory &&
                it.entity.storagePath !in existingThumbnails
        }
        if (pending.isEmpty()) return

        val byStorage = pending.groupBy { it.entity.libraryId }
        val batchAccumulator = java.util.Collections.synchronizedMap(mutableMapOf<String, String>())
        coroutineScope {
            val flusher = launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flushQaThumbnailBatch(batchAccumulator)
                }
            }
            try {
                for ((sid, items) in byStorage) {
                    try {
                        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(sid) } ?: continue
                        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) } ?: continue
                        try {
                            val requests = items.mapNotNull { item ->
                                val name = item.entity.name
                                val isAudio = MediaFileTypes.isAudioFile(name)
                                val isImage = isImageFile(name)
                                if (!isAudio && !isImage && !MediaFileTypes.isVideoFile(name)) return@mapNotNull null
                                RemoteThumbnailRequest(
                                    storageId = sid,
                                    filePath = item.entity.storagePath,
                                    fileName = name,
                                    url = item.entity.storagePath,
                                    isAudio = isAudio,
                                )
                            }
                            if (requests.isEmpty()) continue
                            thumbnailManager.generateRemoteThumbnails(storage, requests) { path, thumbPath ->
                                batchAccumulator[path] = thumbPath
                            }
                        } finally {
                            storage.close()
                        }
                    } catch (_: Exception) { continue }
                }
            } finally {
                flusher.cancel()
                flushQaThumbnailBatch(batchAccumulator)
            }
        }
    }

    private fun flushQaThumbnailBatch(accumulator: MutableMap<String, String>) {
        val batch = synchronized(accumulator) {
            if (accumulator.isEmpty()) return@synchronized null
            val snapshot = accumulator.toMap()
            accumulator.clear()
            snapshot
        } ?: return
        if (batch.isNotEmpty()) {
            _qaThumbnailUrls.update { it + batch }
        }
    }

    /**
     * 验证存储源可达性：检查库存在性（DB）和远程连接（testConnection）。
     *
     * 并发验证所有不同的 storageId，每个 storageId 只测一次连接。
     * 验证结果写入 [_storageReachability]，UI 据此显示视觉提示。
     * 首页加载与下拉刷新共用（suspend，调用方持协程）。
     */
    private suspend fun validateStorageConnections() {
        val plays = recentPlays.value
        val quickItems = quickAccessItems.value
        coroutineScope {
            val storageIds = mutableSetOf<Int>()
            plays.filter { it.storageId != null }.forEach { storageIds.add(it.storageId!!) }
            quickItems.filter { it.libraryValid }.forEach { storageIds.add(it.entity.libraryId) }

            val results = storageIds.map { sid ->
                async(Dispatchers.IO) {
                    try {
                        val library = mediaLibraryDao.getById(sid) ?: return@async sid to false
                        // 本地存储始终可达；其他类型（SAF/SMB/WebDAV）均需验证
                        if (library.mediaType == MediaType.LOCAL_STORAGE) {
                            return@async sid to true
                        }
                        val storage = storageFactory.create(library) ?: return@async sid to false
                        try {
                            val reachable = storage.testConnection()
                            sid to reachable
                        } finally {
                            storage.close()
                        }
                    } catch (_: Exception) {
                        sid to false
                    }
                }
            }.awaitAll().toMap()
            _storageReachability.value = results
        }
    }

    private val _events = MutableSharedFlow<HomeTabEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeTabEvent> = _events.asSharedFlow()

    /** 续播指定历史记录。 */
    fun resumePlay(history: PlayHistoryEntity) {
        viewModelScope.launch {
            when (val result = playStarter.startFromHistory(history)) {
                is PlayStartResult.Success ->
                    _events.tryEmit(HomeTabEvent.NavigateToPlayer)

                is PlayStartResult.Error ->
                    _events.tryEmit(HomeTabEvent.ShowError(result.message))
            }
        }
    }

    /** 打开快速访问书签：文件夹跳文件浏览页，文件跳播放页。 */
    fun openQuickAccessItem(item: QuickAccessUiItem) {
        val entity = item.entity
        viewModelScope.launch {
            if (!item.libraryValid) {
                _events.tryEmit(HomeTabEvent.ShowError(context.getString(R.string.storage_library_deleted)))
                return@launch
            }
            if (entity.isDirectory) {
                _events.tryEmit(HomeTabEvent.NavigateToStorageFile(entity.libraryId, entity.storagePath))
            } else {
                when (val result = playStarter.startFromQuickAccess(entity)) {
                    is PlayStartResult.Success ->
                        _events.tryEmit(HomeTabEvent.NavigateToPlayer)

                    is PlayStartResult.Error ->
                        _events.tryEmit(HomeTabEvent.ShowError(result.message))
                }
            }
        }
    }

    /**
     * 下拉刷新首页。
     *
     * 播放记录 / 快速访问数据来自 Room Flow，增删改会自动推送，无需重查；
     * 刷新针对的是会过期的状态：重新验证存储源可达性（连接可能已恢复/断开），
     * 并重新生成本地缺失的缩略图。
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                validateStorageConnections()
                generateRemoteThumbnails(recentPlays.value, _thumbnailUrls.value)
                generateQuickAccessThumbnails(quickAccessItems.value, _qaThumbnailUrls.value)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 存储源可达性验证是否已执行（仅首页首次加载时触发一次）。 */
    private var connectionsValidated = false

    private companion object {
        /** 首页拉取最近播放的窗口大小（混合视频+单曲，取大窗口再按类型拆，避免某类型被挤空）。 */
        const val RECENT_WINDOW = 30
        /** 首页快速访问条数。 */
        const val QUICK_ACCESS_LIMIT = 10
        /** 首页各类历史行的展示上限（有界预览，控制横滑长度）。 */
        const val ROW_PREVIEW_LIMIT = 8
        /** 批量提交缩略图结果的间隔（ms）。降低 StateFlow emit 次数，减少 Compose 重组。 */
        const val FLUSH_INTERVAL_MS = 250L
    }
}

/** 首页展示态：同帧携带加载标记与列表，供派生子流使用。 */
private data class HomeUiState(
    val loaded: Boolean = false,
    val recent: List<PlayHistoryEntity> = emptyList(),
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val quick: List<QuickAccessUiItem> = emptyList(),
)

/** 首页一次性事件，由 [HomeTabScreen] collect。 */
sealed class HomeTabEvent {
    /** 续播请求已就绪，导航到播放页。 */
    object NavigateToPlayer : HomeTabEvent()

    /** 文件夹书签打开，导航到文件浏览页。 */
    data class NavigateToStorageFile(val libraryId: Int, val relativePath: String = "") : HomeTabEvent()

    /** 续播 / 打开失败，显示错误提示。 */
    data class ShowError(val message: String) : HomeTabEvent()
}
