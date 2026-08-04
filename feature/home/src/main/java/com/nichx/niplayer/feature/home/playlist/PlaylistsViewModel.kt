package com.nichx.niplayer.feature.home.playlist

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlaylistDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.database.entity.PlaylistEntity
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 「我的歌单」列表页 ViewModel。
 *
 * 订阅 [PlaylistDao.getAllWithCountFlow]（歌单 + 条目数），支持新建 / 删除歌单，
 * 并为每个歌单的首个音频条目生成封面缩略图（[coverUrls]）。
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
    private val thumbnailManager: ThumbnailManager,
) : ViewModel() {

    /** 全量歌单（含条目数），按最近更新倒序。 */
    val playlists: StateFlow<List<PlaylistWithCount>> = playlistDao.getAllWithCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 数据是否已就绪（避免首帧空列表误显示空状态）。 */
    val dataReady: StateFlow<Boolean> = playlistDao.getAllWithCountFlow()
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    /** 歌单封面：playlistId → 首个条目的音频封面本地路径。 */
    private val _coverUrls = MutableStateFlow<Map<Int, String>>(emptyMap())
    val coverUrls: StateFlow<Map<Int, String>> = _coverUrls.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        // 歌单变化时刷新封面：缓存命中立即可用，未命中异步生成
        viewModelScope.launch {
            playlists.collect { lists ->
                if (lists.isEmpty()) {
                    _coverUrls.value = emptyMap()
                    return@collect
                }
                val firstItems = withContext(Dispatchers.IO) {
                    playlistItemDao.getFirstItemPerPlaylist()
                }
                val covers = withContext(Dispatchers.IO) {
                    firstItems.mapNotNull { item ->
                        resolveCover(item)?.let { item.playlistId to it }
                    }.toMap()
                }
                _coverUrls.value = covers
            }
        }
    }

    /** 解析单个条目的音频封面：先查缓存，未命中则生成。 */
    private suspend fun resolveCover(item: PlaylistItemEntity): String? {
        thumbnailManager.getCachedAudioCoverPath(item.libraryId, item.filePath)?.let { return it }
        val library = mediaLibraryDao.getById(item.libraryId) ?: return null
        val storage = storageFactory.create(library) ?: return null
        val file = object : AbstractStorageFile(
            path = item.filePath,
            name = item.fileName,
            isDirectory = false,
            length = item.fileSize,
        ) {}
        return runCatching {
            thumbnailManager.generateAudioCover(storage, item.libraryId, file)
        }.getOrNull()
    }

    /** 新建歌单（名称去空格，空名忽略）。 */
    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.insert(PlaylistEntity(name = trimmed))
            }
            _toast.tryEmit(context.getString(R.string.playlist_created, trimmed))
        }
    }

    /** 删除歌单（连带清空条目）。 */
    fun deletePlaylist(playlistId: Int, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistItemDao.deleteByPlaylist(playlistId)
                playlistDao.deleteById(playlistId)
            }
            _toast.tryEmit(context.getString(R.string.playlist_deleted, name))
        }
    }

    /** 重命名歌单（名称去空格，空名忽略）。 */
    fun renamePlaylist(playlistId: Int, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.renamePlaylist(playlistId, trimmed, System.currentTimeMillis())
            }
            _toast.tryEmit(context.getString(R.string.playlist_renamed, trimmed))
        }
    }

    /** 复制歌单：新建「原名 副本」歌单并复制全部条目。 */
    fun duplicatePlaylist(playlistId: Int, name: String) {
        viewModelScope.launch {
            val newName = context.getString(R.string.playlist_duplicate_name, name)
            withContext(Dispatchers.IO) {
                playlistItemDao.duplicatePlaylist(playlistId, newName, playlistDao)
            }
            _toast.tryEmit(context.getString(R.string.playlist_duplicated, newName))
        }
    }

    /** 合并歌单：将 sourceId 的条目复制进 targetId（重复项自动跳过）。 */
    fun mergePlaylist(sourceId: Int, sourceName: String, targetId: Int, targetName: String) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                playlistItemDao.mergeInto(sourceId, targetId, playlistDao)
            }
            _toast.tryEmit(
                if (added > 0) {
                    context.getString(R.string.playlist_merged_from_to, sourceName, added, targetName)
                } else {
                    context.getString(R.string.playlist_merge_all_duplicate, targetName, sourceName)
                },
            )
        }
    }

    /** 置顶 / 取消置顶。 */
    fun togglePinned(playlistId: Int, pinned: Boolean, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.setPinned(playlistId, pinned, System.currentTimeMillis())
            }
            _toast.tryEmit(if (pinned) context.getString(R.string.playlist_pinned, name) else context.getString(R.string.playlist_unpinned, name))
        }
    }
}
