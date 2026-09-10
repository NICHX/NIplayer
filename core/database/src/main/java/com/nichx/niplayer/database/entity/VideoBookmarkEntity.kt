package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * 视频书签（F-19）。
 *
 * 记录用户在视频中标记的关键时间点，用于快速跳转。
 * 通过 [uniqueKey] + [storageId] 与 [PlayHistoryEntity] 关联，定位到具体视频。
 *
 * 复合唯一索引 `(unique_key, storage_id, position_ms)` 确保同一视频同一时间点只有一个书签。
 *
 * @param uniqueKey 业务唯一键，格式 `"${libraryId}:${storagePath}"`，与 play_history 一致
 * @param storageId 存储源 id，与 play_history 一致（可空，直链播放时为 null）
 * @param videoName 视频名（冗余存储，供书签列表显示）
 * @param positionMs 书签位置（ms）
 * @param label 用户备注（可空）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间戳（增量同步用）
 */
@Entity(
    tableName = "video_bookmark",
    indices = [
        Index(value = ["unique_key", "storage_id", "position_ms"], unique = true),
        Index(value = ["unique_key", "storage_id"], unique = false),
    ],
)
@JsonClass(generateAdapter = true)
data class VideoBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "unique_key")
    val uniqueKey: String,
    @ColumnInfo(name = "storage_id")
    val storageId: Int?,
    @ColumnInfo(name = "video_name")
    val videoName: String,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    val label: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)
