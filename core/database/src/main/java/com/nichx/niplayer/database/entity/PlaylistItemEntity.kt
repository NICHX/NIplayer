package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * 歌单条目。
 *
 * 字段与 :player:kernel 的 PlaylistItem 一一对应（libraryId / filePath / fileName /
 * mediaTypeValue / fileSize），保证「播放全部」时能直接组装 PlaylistItem 重建播放源。
 * [sortOrder] 为用户拖拽排序结果，唯一索引（playlist_id, file_path）防止重复添加。
 */
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "playlist_item",
    indices = [Index(value = ["playlist_id", "file_path"], unique = true)],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    @ColumnInfo(name = "playlist_id") var playlistId: Int,
    @ColumnInfo(name = "library_id") var libraryId: Int,
    @ColumnInfo(name = "file_path") var filePath: String,
    @ColumnInfo(name = "file_name") var fileName: String,
    @ColumnInfo(name = "media_type") var mediaTypeValue: String,
    @ColumnInfo(name = "file_size") var fileSize: Long = 0L,
    @ColumnInfo(name = "sort_order") var sortOrder: Int = 0,
)
