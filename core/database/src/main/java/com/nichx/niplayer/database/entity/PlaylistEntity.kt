package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 用户歌单（持久化播放列表）。
 *
 * 对应扩展功能方案二「播放列表系统」：从临时连播（PlaylistHolder 内存态）升级为
 * 可持久化、可编辑、可跨目录混合条目的歌单。条目见 [PlaylistItemEntity]。
 */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "created_at") var createdAt: Date = Date(),
    @ColumnInfo(name = "updated_at", defaultValue = "0") var updatedAt: Long = System.currentTimeMillis(),
)
