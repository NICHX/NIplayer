package com.nichx.niplayer.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.PlaylistDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.database.entity.PlaylistEntity
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.isAudioFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 保存目标：新建歌单或已有歌单。 */
sealed class SavePlaylistTarget {
    object New : SavePlaylistTarget()
    data class Existing(val playlistId: Int) : SavePlaylistTarget()
}

/** 播放页「添加到歌单」结果事件。 */
sealed class PlaylistSaveEvent {
    /** 添加成功，[addedCount] 为实际新增条数（0 表示重复），[playlistName] 为目标歌单名。 */
    data class Saved(val addedCount: Int, val playlistName: String) : PlaylistSaveEvent()
}

/**
 * 播放页「添加到歌单」ViewModel。
 *
 * 将当前正在播放的歌曲（[items] 通常为单元素列表）持久化到已有歌单或新建歌单，
 * 复用 [PlaylistItemDao.addItems] 的唯一索引去重（重复文件自动跳过）。
 */
@HiltViewModel
class PlaylistSaveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
) : ViewModel() {

    /** 已有歌单（含条目数）。 */
    val playlists: StateFlow<List<PlaylistWithCount>> = playlistDao.getAllWithCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _events = MutableSharedFlow<PlaylistSaveEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlaylistSaveEvent> = _events.asSharedFlow()

    /** 添加当前歌曲到目标歌单；重复文件自动跳过。 */
    fun save(target: SavePlaylistTarget, newName: String, items: List<PlaylistItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val audioItems = items.filter { isAudioFile(it.fileName) }
                if (audioItems.isEmpty()) {
                    _events.tryEmit(
                        PlaylistSaveEvent.Saved(
                            0,
                            newName.trim().ifBlank { context.getString(R.string.player_default_playlist_name) },
                        ),
                    )
                    return@withContext
                }
                val playlistId = when (target) {
                    is SavePlaylistTarget.Existing -> target.playlistId
                    SavePlaylistTarget.New -> playlistDao.insert(
                        PlaylistEntity(name = newName.trim())
                    ).toInt()
                }
                val entities = audioItems.map { item ->
                    PlaylistItemEntity(
                        playlistId = playlistId,
                        libraryId = item.libraryId,
                        filePath = item.filePath,
                        fileName = item.fileName,
                        mediaTypeValue = item.mediaTypeValue,
                        fileSize = item.fileSize,
                    )
                }
                val addedCount = playlistItemDao.addItems(playlistId, entities)
                playlistDao.touch(playlistId, System.currentTimeMillis())
                val name = if (target is SavePlaylistTarget.Existing) {
                    playlists.value.firstOrNull { it.playlist.id == playlistId }?.playlist?.name
                        ?: context.getString(R.string.player_default_playlist_name)
                } else {
                    newName.trim()
                }
                _events.tryEmit(PlaylistSaveEvent.Saved(addedCount, name))
            }
        }
    }
}
