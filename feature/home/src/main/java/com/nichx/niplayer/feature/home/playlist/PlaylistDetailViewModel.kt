package com.nichx.niplayer.feature.home.playlist

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlaylistDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.database.entity.PlaylistEntity
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.PlayStarter
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 歌单详情页 ViewModel（扩展功能方案二 · 页面 2）。
 *
 * 订阅歌单基本信息与条目列表（[PlaylistItemDao.getByPlaylistFlow]），支持：
 * - [playAll] 播放全部：条目组装 [com.nichx.niplayer.player.kernel.PlaylistItem] 列表，
 *   经 [PlayStarter.startFromPlaylist] 写入 PlaylistHolder / PlaybackRequestHolder，
 *   成功后由 UI 导航到播放守卫路由（覆盖同目录自动连播）
 * - [removeItem] 移出单个条目
 * - [persistOrder] 拖拽排序落盘
 * - 条目封面缩略图（[coverUrls]，缓存优先，未命中异步生成）
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
    private val thumbnailManager: ThumbnailManager,
    private val playStarter: PlayStarter,
) : ViewModel() {

    private val playlistId: Int = checkNotNull(savedStateHandle["playlistId"])

    /** 歌单基本信息（名称等）。 */
    val playlist: StateFlow<PlaylistEntity?> = playlistDao.getByIdFlow(playlistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    /** 歌单条目（已按用户排序）。 */
    val items: StateFlow<List<PlaylistItemEntity>> = playlistItemDao.getByPlaylistFlow(playlistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 本地列表快照：供拖拽排序时同步重排（避免 Flow 重发导致回跳）。 */
    private val _draftItems = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())
    val draftItems: StateFlow<List<PlaylistItemEntity>> = _draftItems.asStateFlow()

    /** 全部歌单（含条目数）：供「合并到 / 移动到 / 复制到」目标选择（列表页选择器复用）。 */
    val allPlaylists: StateFlow<List<PlaylistWithCount>> = playlistDao.getAllWithCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 条目封面：filePath → 音频封面本地路径（缓存优先，未命中异步生成）。 */
    private val _coverUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val coverUrls: StateFlow<Map<String, String>> = _coverUrls.asStateFlow()

    private val _events = MutableSharedFlow<PlaylistDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlaylistDetailEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            items.collect { _draftItems.value = it }
        }
        // 条目变化时刷新封面
        viewModelScope.launch {
            items.collect { list ->
                if (list.isEmpty()) {
                    _coverUrls.value = emptyMap()
                    return@collect
                }
                val covers = withContext(Dispatchers.IO) {
                    list.mapNotNull { item ->
                        resolveCover(item)?.let { item.filePath to it }
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

    /** 播放全部：从头播放歌单（用户排序顺序）。歌单仅支持音频，非音频条目自动跳过。 */
    fun playAll() {
        val audioItems = _draftItems.value.filter {
            MediaFileTypes.isAudioFile(it.fileName)
        }
        if (audioItems.isEmpty()) {
            _events.tryEmit(PlaylistDetailEvent.ShowError(context.getString(R.string.playlist_detail_no_audio)))
            return
        }
        viewModelScope.launch {
            val items = audioItems.map { it.toPlaylistItem() }
            when (val result = playStarter.startFromPlaylist(playlistId, items, 0)) {
                is PlayStarter.StartResult.Success -> {
                    _events.tryEmit(PlaylistDetailEvent.NavigateToPlayer)
                }
                is PlayStarter.StartResult.Error -> {
                    _events.tryEmit(PlaylistDetailEvent.ShowError(result.message))
                }
            }
        }
    }

    /** 点击条目：从该条目开始播放（后续条目按歌单顺序连播）。 */
    fun playItem(index: Int) {
        val audioItems = _draftItems.value.filter {
            MediaFileTypes.isAudioFile(it.fileName)
        }
        if (audioItems.isEmpty()) {
            _events.tryEmit(PlaylistDetailEvent.ShowError(context.getString(R.string.playlist_detail_no_audio)))
            return
        }
        if (index !in audioItems.indices) return
        viewModelScope.launch {
            val items = audioItems.map { it.toPlaylistItem() }
            when (val result = playStarter.startFromPlaylist(playlistId, items, index)) {
                is PlayStarter.StartResult.Success -> {
                    _events.tryEmit(PlaylistDetailEvent.NavigateToPlayer)
                }
                is PlayStarter.StartResult.Error -> {
                    _events.tryEmit(PlaylistDetailEvent.ShowError(result.message))
                }
            }
        }
    }

    /** 移除单个条目。 */
    fun removeItem(itemId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistItemDao.deleteById(itemId)
                playlistDao.touch(playlistId, System.currentTimeMillis())
            }
        }
    }

    /** 拖拽排序落盘：整批重写 sort_order 并刷新歌单更新序。 */
    fun persistOrder(ordered: List<PlaylistItemEntity>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistItemDao.persistOrder(ordered)
                playlistDao.touch(playlistId, System.currentTimeMillis())
            }
        }
    }

    /** 批量移除选中条目。 */
    fun removeItems(itemIds: List<Int>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistItemDao.deleteByIds(itemIds)
                playlistDao.touch(playlistId, System.currentTimeMillis())
            }
        }
    }

    /** 置顶 / 取消置顶当前歌单。 */
    fun togglePinned(pinned: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.setPinned(playlistId, pinned, System.currentTimeMillis())
            }
            _events.tryEmit(
                PlaylistDetailEvent.ShowMessage(
                    if (pinned) context.getString(R.string.playlist_detail_pinned)
                    else context.getString(R.string.playlist_detail_unpinned),
                ),
            )
        }
    }

    /** 删除当前歌单（连带清空条目），完成后由 UI 返回上一页。 */
    fun deletePlaylist() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistItemDao.deleteByPlaylist(playlistId)
                playlistDao.deleteById(playlistId)
            }
            _events.tryEmit(PlaylistDetailEvent.PlaylistDeleted)
        }
    }

    /** 重命名当前歌单（名称去空格，空名忽略）。 */
    fun renamePlaylist(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                playlistDao.renamePlaylist(playlistId, trimmed, System.currentTimeMillis())
            }
            _events.tryEmit(PlaylistDetailEvent.ShowMessage(context.getString(R.string.playlist_renamed, trimmed)))
        }
    }

    /** 复制当前歌单为「原名 副本」。 */
    fun duplicatePlaylist() {
        val name = playlist.value?.name ?: return
        viewModelScope.launch {
            val newName = context.getString(R.string.playlist_duplicate_name, name)
            withContext(Dispatchers.IO) {
                playlistItemDao.duplicatePlaylist(playlistId, newName, playlistDao)
            }
            _events.tryEmit(PlaylistDetailEvent.ShowMessage(context.getString(R.string.playlist_duplicated, newName)))
        }
    }

    /** 合并当前歌单到目标歌单（重复项自动跳过）。 */
    fun mergeInto(targetId: Int) {
        val sourceName = playlist.value?.name ?: return
        viewModelScope.launch {
            val targetName = withContext(Dispatchers.IO) {
                playlistDao.getById(targetId)?.name ?: ""
            }
            val added = withContext(Dispatchers.IO) {
                playlistItemDao.mergeInto(playlistId, targetId, playlistDao)
            }
            _events.tryEmit(
                PlaylistDetailEvent.ShowMessage(
                    if (added > 0) {
                        context.getString(R.string.playlist_detail_merged_into, added, targetName)
                    } else {
                        context.getString(R.string.playlist_detail_all_exist_in, sourceName, targetName)
                    },
                ),
            )
        }
    }

    /** 批量复制选中条目到目标歌单（重复项自动跳过）。 */
    fun copySelectedTo(targetId: Int, itemIds: List<Int>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                playlistItemDao.copyItemsTo(targetId, itemIds, playlistDao)
            }
            val targetName = withContext(Dispatchers.IO) {
                playlistDao.getById(targetId)?.name ?: ""
            }
            _events.tryEmit(
                PlaylistDetailEvent.ShowMessage(
                    if (added > 0) {
                        context.getString(R.string.playlist_detail_copied_into, added, targetName)
                    } else {
                        context.getString(R.string.playlist_detail_selected_all_exist, targetName)
                    },
                ),
            )
        }
    }

    /** 批量移动选中条目到目标歌单（源歌单删除，目标重复项自动跳过）。 */
    fun moveSelectedTo(targetId: Int, itemIds: List<Int>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                playlistItemDao.moveItemsTo(targetId, itemIds, playlistId, playlistDao)
            }
            val targetName = withContext(Dispatchers.IO) {
                playlistDao.getById(targetId)?.name ?: ""
            }
            _events.tryEmit(
                PlaylistDetailEvent.ShowMessage(
                    if (added > 0) {
                        context.getString(R.string.playlist_detail_moved_into, added, targetName)
                    } else {
                        context.getString(R.string.playlist_detail_moved_all_exist, targetName)
                    },
                ),
            )
        }
    }
}

sealed class PlaylistDetailEvent {
    /** 播放请求已就绪，导航到播放页。 */
    object NavigateToPlayer : PlaylistDetailEvent()

    /** 操作失败，显示错误提示。 */
    data class ShowError(val message: String) : PlaylistDetailEvent()

    /** 操作成功，显示提示消息。 */
    data class ShowMessage(val message: String) : PlaylistDetailEvent()

    /** 歌单已删除，导航返回上一页。 */
    object PlaylistDeleted : PlaylistDetailEvent()
}
