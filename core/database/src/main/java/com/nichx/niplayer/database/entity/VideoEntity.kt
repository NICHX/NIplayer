package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地视频扫描结果表。
 *
 * 迁移自旧仓库 common_component 的 video 表（DatabaseManager.MIGRATION_16_17 重建为当前 schema）。
 * `subtitle_path` 字段已 @Deprecated，旧版 ManualMigration 已将字幕信息迁移到 play_history 表，
 * 后续阶段清理 VideoDao 时考虑移除该字段。
 */
@Entity(tableName = "video", indices = [Index(value = ["file_path"], unique = true)])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "file_id")
    var fileId: Long,

    @ColumnInfo(name = "file_path")
    var filePath: String,

    @ColumnInfo(name = "folder_path")
    var folderPath: String,

    @Deprecated("migrate to play_history")
    @ColumnInfo(name = "subtitle_path")
    var subtitlePath: String? = null,

    @ColumnInfo(name = "video_duration")
    var videoDuration: Long = 0,

    @ColumnInfo(name = "file_length")
    var fileLength: Long = 0,

    @ColumnInfo(name = "filter")
    var isFilter: Boolean = false,

    @ColumnInfo(name = "extend")
    var isExtend: Boolean = false
)
