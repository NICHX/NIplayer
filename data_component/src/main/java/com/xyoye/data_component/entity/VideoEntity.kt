package com.xyoye.data_component.entity

import androidx.room.*

/**
 * Created by xyoye on 2020/7/29.
 */

@Entity(tableName = "video", indices = [Index(value = arrayOf("file_path"), unique = true)])
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