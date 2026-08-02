package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nichx.niplayer.database.enums.MediaType
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * 播放历史表。
 *
 * 迁移自旧仓库 play_history 表。v15→v16 已移除 `danmu_path`、`episode_id` 字段
 * （参见方案 1.4.1 弹幕能力移除），当前 schema 不再包含这两个字段。
 * `is_last_play` 字段不持久化，仅在内存中标记最后一次播放项。
 */
@Entity(
    tableName = "play_history",
    indices = [Index(value = ["unique_key", "storage_id"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "video_name")
    val videoName: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,

    @ColumnInfo(name = "video_position")
    var videoPosition: Long = 0,

    @ColumnInfo(name = "video_duration")
    var videoDuration: Long = 0,

    @ColumnInfo(name = "play_time")
    var playTime: Date = Date(),

    @ColumnInfo(name = "subtitle_path")
    var subtitlePath: String? = null,

    @ColumnInfo(name = "torrent_path")
    var torrentPath: String? = null,

    @ColumnInfo(name = "torrent_index")
    var torrentIndex: Int = -1,

    @ColumnInfo(name = "http_header")
    var httpHeader: String? = null,

    @ColumnInfo(name = "unique_key")
    var uniqueKey: String = "",

    @ColumnInfo(name = "storage_path")
    var storagePath: String? = null,

    @ColumnInfo(name = "storage_id")
    var storageId: Int? = null,

    @ColumnInfo(name = "audio_path")
    var audioPath: String? = null,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) {
    @androidx.room.Ignore
    var isLastPlay: Boolean = false
}
