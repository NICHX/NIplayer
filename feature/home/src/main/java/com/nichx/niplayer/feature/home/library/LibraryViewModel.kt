package com.nichx.niplayer.feature.home.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.security.EncryptedFolderManager
import com.nichx.niplayer.database.sync.PlayHistorySyncDeleter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 媒体库 Tab 的 ViewModel。
 *
 * 注入 [MediaLibraryDao]（由 :core:database 的 DatabaseModule 提供），通过 Flow
 * 订阅存储源列表，[stateIn] 转为 [StateFlow] 供 Compose 收集。
 *
 * 存储源管理（P1）：[delete] 供 [LibraryScreen] 长按删除直接调用，无需跳转编辑页。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaLibraryDao: MediaLibraryDao,
    private val quickAccessDao: QuickAccessDao,
    private val syncDeleter: PlayHistorySyncDeleter,
    private val downloadTaskDao: DownloadTaskDao,
    private val encryptedFolderManager: EncryptedFolderManager,
) : ViewModel() {

    private val librariesFlow = mediaLibraryDao.getAll()

    /** 存储源列表，WhileSubscribed(5000) 避免配置变更时重启 Flow。 */
    val libraries: StateFlow<List<MediaLibraryEntity>> = librariesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 数据是否已就绪（避免首帧空列表误显示空状态，触发骨架屏）。 */
    val dataReady: StateFlow<Boolean> = librariesFlow
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 按名称/URL 过滤后按媒体类型分组排序的存储源列表。 */
    val filteredLibraries: StateFlow<List<MediaLibraryEntity>> = combine(
        librariesFlow,
        _searchQuery,
    ) { libs, query ->
        val filtered = if (query.isBlank()) libs
        else libs.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true)
        }
        filtered.sortedWith(
            compareBy({ it.mediaType.sortOrder }, { it.displayName.lowercase() })
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** 刚删除的实体（用于撤销恢复）。 */
    private var lastDeleted: MediaLibraryEntity? = null

    /** 删除存储源（按主键），同时级联删除关联数据。 */
    fun delete(library: MediaLibraryEntity) {
        lastDeleted = library
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mediaLibraryDao.deleteById(library.id)
                // 文件夹访问加密联动：级联清理该存储源的加密配置（防撤销恢复产生新 id 后配置失联）
                encryptedFolderManager.deleteByStorageId(library.id)
            }
        }
    }

    /** 撤销删除：重新插入被删的存储源。 */
    fun undoDelete() {
        val entity = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val restored = entity.copy(id = 0)
                mediaLibraryDao.insert(restored)
            }
        }
    }

    /** 确认删除：撤销超时后清理关联数据。 */
    fun confirmDelete(libraryId: Int) {
        lastDeleted = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                quickAccessDao.deleteByLibrary(libraryId)
                syncDeleter.deleteByStorageId(libraryId)
                downloadTaskDao.deleteByStorageId(libraryId)
            }
        }
    }
}
