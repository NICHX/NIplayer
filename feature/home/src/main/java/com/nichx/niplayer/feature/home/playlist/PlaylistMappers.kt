package com.nichx.niplayer.feature.home.playlist

import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.player.kernel.PlaylistItem

/** 歌单条目（持久化）→ 播放列表项（:player:kernel）。 */
internal fun PlaylistItemEntity.toPlaylistItem(): PlaylistItem = PlaylistItem(
    libraryId = libraryId,
    filePath = filePath,
    fileName = fileName,
    mediaTypeValue = mediaTypeValue,
    fileSize = fileSize,
)

/** 播放列表项 → 歌单条目（持久化，playlistId 由调用方补充）。 */
internal fun PlaylistItem.toEntity(playlistId: Int): PlaylistItemEntity = PlaylistItemEntity(
    playlistId = playlistId,
    libraryId = libraryId,
    filePath = filePath,
    fileName = fileName,
    mediaTypeValue = mediaTypeValue,
    fileSize = fileSize,
)
