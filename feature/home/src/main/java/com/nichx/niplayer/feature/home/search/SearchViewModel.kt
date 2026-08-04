package com.nichx.niplayer.feature.home.search

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.PlayStarter
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessUiItem
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 首页搜索 ViewModel。
 *
 * 聚合三张本地表的 LIKE 搜索：
 * - **播放历史**：[PlayHistoryDao.searchByKeyword]（可续播）
 * - **快速访问**：[QuickAccessDao.searchByKeyword]（文件夹跳文件浏览，文件跳播放）
 * - **存储源**：[MediaLibraryDao.searchByKeyword]（跳文件浏览根目录）
 *
 * 全本地查询（Room 毫秒级），不发起任何网络请求。
 * 输入经 [DEBOUNCE_MS] 防抖后再查询，避免每次按键都触发三表扫描。
 *
 * 打开行为与既有页面保持一致：
 * - 历史记录 → [PlayStarter.startFromHistory] 续播
 * - 快速访问 → 复用 [QuickAccessViewModel.openItem] 同款分流（文件夹 / 文件）
 * - 存储源 → emit [SearchEvent.NavigateToStorageFile] 跳文件浏览
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playHistoryDao: PlayHistoryDao,
    private val quickAccessDao: QuickAccessDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val thumbnailManager: ThumbnailManager,
    private val playStarter: PlayStarter,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SearchEvent> = _events.asSharedFlow()

    /** 防抖任务：新关键词到来时取消旧任务，避免快速输入触发重复查询。 */
    private var debounceJob: Job? = null

    /** 设置搜索关键词。空白关键词清空结果；非空白经防抖后并发查询三张表。 */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val q = query.trim()
        debounceJob?.cancel()
        if (q.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        // 清空旧结果立即进入搜索中状态，避免展示上一条关键词的过期结果
        _uiState.value = SearchUiState(query = q, searching = true)
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (q != _searchQuery.value.trim()) return@launch
            val results = withContext(Dispatchers.IO) {
                SearchResults(
                    histories = playHistoryDao.searchByKeyword(q),
                    quickAccess = quickAccessDao.searchByKeyword(q),
                    libraries = mediaLibraryDao.searchByKeyword(q),
                )
            }
            val libMap = withContext(Dispatchers.IO) {
                mediaLibraryDao.getAllSuspend().associateBy { it.id }
            }
            // 读取本地缩略图缓存（不触发网络生成，保持搜索即时性）
            val historyThumbs = withContext(Dispatchers.IO) {
                results.histories.mapNotNull { item ->
                    val sid = item.storageId
                    val sPath = item.storagePath
                    if (sid == null || sPath.isNullOrEmpty()) return@mapNotNull null
                    val path = if (MediaFileTypes.isAudioFile(item.videoName)) {
                        thumbnailManager.getCachedAudioCoverPath(sid, sPath)
                    } else {
                        thumbnailManager.getCachedThumbnailPath(sid, sPath)
                    }
                    if (path != null) item.url to path else null
                }.toMap()
            }
            val qaThumbs = withContext(Dispatchers.IO) {
                results.quickAccess.filter { !it.isDirectory && it.storagePath.isNotEmpty() }
                    .mapNotNull { qa ->
                        val path = when {
                            MediaFileTypes.isAudioFile(qa.name) ->
                                thumbnailManager.getCachedAudioCoverPath(qa.libraryId, qa.storagePath)
                            MediaFileTypes.isImageFile(qa.name) ->
                                thumbnailManager.getCachedImageThumbnailPath(qa.libraryId, qa.storagePath)
                            else ->
                                thumbnailManager.getCachedThumbnailPath(qa.libraryId, qa.storagePath)
                        }
                        if (path != null) "${qa.libraryId}/${qa.storagePath}" to path else null
                    }.toMap()
            }
            if (q != _searchQuery.value.trim()) return@launch
            _uiState.value = SearchUiState(
                query = q,
                histories = results.histories,
                quickAccessItems = results.quickAccess.map { entity ->
                    QuickAccessUiItem(
                        entity = entity,
                        libraryName = libMap[entity.libraryId]?.displayName,
                        libraryValid = libMap[entity.libraryId] != null,
                    )
                },
                libraries = results.libraries,
                historyThumbs = historyThumbs,
                qaThumbs = qaThumbs,
            )
        }
    }

    /** 续播历史记录（与播放历史页行为一致）。 */
    fun resumePlay(history: PlayHistoryEntity) {
        viewModelScope.launch {
            when (val result = playStarter.startFromHistory(history)) {
                is PlayStarter.StartResult.Success ->
                    _events.tryEmit(SearchEvent.NavigateToPlayer)

                is PlayStarter.StartResult.Error ->
                    _events.tryEmit(SearchEvent.ShowError(result.message))
            }
        }
    }

    /** 打开快速访问书签：文件夹跳文件浏览，文件跳播放（与快速访问页行为一致）。 */
    fun openQuickAccess(item: QuickAccessUiItem) {
        val entity = item.entity
        viewModelScope.launch {
            if (!item.libraryValid) {
                _events.tryEmit(SearchEvent.ShowError(context.getString(R.string.storage_library_deleted)))
                return@launch
            }
            if (entity.isDirectory) {
                _events.tryEmit(SearchEvent.NavigateToStorageFile(entity.libraryId, entity.storagePath))
            } else {
                when (val result = playStarter.startFromQuickAccess(entity)) {
                    is PlayStarter.StartResult.Success ->
                        _events.tryEmit(SearchEvent.NavigateToPlayer)

                    is PlayStarter.StartResult.Error ->
                        _events.tryEmit(SearchEvent.ShowError(result.message))
                }
            }
        }
    }

    /** 打开存储源，跳文件浏览根目录。 */
    fun openLibrary(library: MediaLibraryEntity) {
        _events.tryEmit(SearchEvent.NavigateToStorageFile(library.id, ""))
    }

    private companion object {
        /** 输入防抖间隔（ms）。平衡响应速度与查询次数。 */
        const val DEBOUNCE_MS = 300L
    }
}

/** 三表查询结果中间容器。 */
private data class SearchResults(
    val histories: List<PlayHistoryEntity>,
    val quickAccess: List<com.nichx.niplayer.database.entity.QuickAccessEntity>,
    val libraries: List<MediaLibraryEntity>,
)

/** 首页搜索 UI 状态。 */
data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val histories: List<PlayHistoryEntity> = emptyList(),
    val quickAccessItems: List<QuickAccessUiItem> = emptyList(),
    val libraries: List<MediaLibraryEntity> = emptyList(),
    /** 历史缩略图缓存映射：播放 url → 本地缓存路径。 */
    val historyThumbs: Map<String, String> = emptyMap(),
    /** 快速访问文件缩略图缓存映射："libraryId/storagePath" → 本地缓存路径。 */
    val qaThumbs: Map<String, String> = emptyMap(),
)

/** 首页搜索一次性事件，由 [SearchScreen] collect。 */
sealed class SearchEvent {
    /** 播放请求已就绪，导航到播放页。 */
    object NavigateToPlayer : SearchEvent()

    /** 打开文件浏览页（快速访问文件夹 / 存储源根目录）。 */
    data class NavigateToStorageFile(val libraryId: Int, val relativePath: String = "") : SearchEvent()

    /** 打开 / 续播失败，显示错误提示。 */
    data class ShowError(val message: String) : SearchEvent()
}
