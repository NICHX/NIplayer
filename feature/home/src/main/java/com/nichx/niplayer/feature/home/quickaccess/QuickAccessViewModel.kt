package com.nichx.niplayer.feature.home.quickaccess

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.MediaFileTypes.isImageFile
import com.nichx.niplayer.feature.home.PlayStarter
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 快速访问列表页 ViewModel。
 *
 * 替代旧仓库 `QuickAccessViewModel`（基于 MMKV JSON 列表）。数据源改为 Room
 * [QuickAccessDao]，[combine] 关联 [MediaLibraryDao] 获取存储源显示名，响应式刷新。
 *
 * 打开逻辑：
 * - 文件夹 → emit [QuickAccessEvent.NavigateToStorageFile]，由 UI 跳转文件浏览页
 *   （当前仅跳到存储源根目录，深层定位待 Storage.pathFile 增强后补齐）
 * - 文件 → 委托 [PlayStarter.startFromQuickAccess] 构造 PlaybackRequest，emit
 *   [QuickAccessEvent.NavigateToPlayer]
 *
 * 若关联存储源已删除（libraryValid=false），打开时 emit 错误提示。
 */
@HiltViewModel
class QuickAccessViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quickAccessDao: QuickAccessDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val playStarter: PlayStarter,
    private val storageFactory: StorageFactory,
    private val thumbnailManager: ThumbnailManager,
) : ViewModel() {

    /** 快速访问列表，关联存储源显示名；WhileSubscribed(5000) 避免配置变更重启 Flow。 */
    val items: StateFlow<List<QuickAccessUiItem>> =
        combine(
            quickAccessDao.getAllFlow(),
            mediaLibraryDao.getAll(),
        ) { quickList, libraries ->
            val libMap = libraries.associateBy { it.id }
            quickList.map { entity ->
                val lib = libMap[entity.libraryId]
                QuickAccessUiItem(
                    entity = entity,
                    libraryName = lib?.displayName,
                    libraryValid = lib != null,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 数据是否已就绪（避免首帧空列表误显示空状态，触发骨架屏）。 */
    val dataReady: StateFlow<Boolean> = quickAccessDao.getAllFlow()
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    /** 快速访问缩略图路径映射：storagePath → 本地缓存路径。 */
    private val _qaThumbnailUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val qaThumbnailUrls: StateFlow<Map<String, String>> = _qaThumbnailUrls.asStateFlow()

    init {
        viewModelScope.launch {
            items.collect { items ->
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
                    } else if (isImageFile(name)) {
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
    }

    private val _events = MutableSharedFlow<QuickAccessEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<QuickAccessEvent> = _events.asSharedFlow()

    /**
     * 为快速访问中的媒体文件生成缩略图。
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

    /** 打开书签：文件夹跳文件浏览页，文件跳播放页。 */
    fun openItem(item: QuickAccessUiItem) {
        val entity = item.entity
        viewModelScope.launch {
            if (!item.libraryValid) {
                _events.tryEmit(QuickAccessEvent.ShowError(context.getString(R.string.storage_library_deleted)))
                return@launch
            }
            if (entity.isDirectory) {
                _events.tryEmit(QuickAccessEvent.NavigateToStorageFile(entity.libraryId, entity.storagePath))
            } else {
                when (val result = playStarter.startFromQuickAccess(entity)) {
                    is PlayStarter.StartResult.Success ->
                        _events.tryEmit(QuickAccessEvent.NavigateToPlayer)

                    is PlayStarter.StartResult.Error ->
                        _events.tryEmit(QuickAccessEvent.ShowError(result.message))
                }
            }
        }
    }

    /** 删除书签。 */
    fun deleteItem(id: Int) {
        viewModelScope.launch { quickAccessDao.delete(id) }
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 250L
    }

    /**
     * 拖拽排序后持久化新顺序。按 [newOrder] 顺序重新分配 0..n 的 sortIndex，
     * 一次性批量更新。UI 层在拖拽结束时调用。
     */
    fun persistOrder(newOrder: List<QuickAccessUiItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                newOrder.forEachIndexed { index, item ->
                    quickAccessDao.updateOrder(item.entity.id, index)
                }
            }
        }
    }
}

/** 快速访问列表 UI 项，携带关联存储源信息用于展示与有效性判断。 */
data class QuickAccessUiItem(
    val entity: QuickAccessEntity,
    val libraryName: String?,
    val libraryValid: Boolean,
)

/** 一次性事件。 */
sealed class QuickAccessEvent {
    object NavigateToPlayer : QuickAccessEvent()
    data class NavigateToStorageFile(val libraryId: Int, val relativePath: String = "") : QuickAccessEvent()
    data class ShowError(val message: String) : QuickAccessEvent()
}
