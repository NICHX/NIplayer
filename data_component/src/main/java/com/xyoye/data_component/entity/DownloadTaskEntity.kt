package com.xyoye.data_component.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_task")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "storage_id")
    var storageId: Int,

    @ColumnInfo(name = "file_name")
    var fileName: String,

    @ColumnInfo(name = "file_path")
    var filePath: String,

    @ColumnInfo(name = "unique_key")
    var uniqueKey: String,

    @ColumnInfo(name = "total_bytes")
    var totalBytes: Long = 0,

    @ColumnInfo(name = "downloaded_bytes")
    var downloadedBytes: Long = 0,

    @ColumnInfo(name = "state")
    var state: Int = DownloadState.WAITING,

    @ColumnInfo(name = "error_message")
    var errorMessage: String? = null,

    @ColumnInfo(name = "target_storage_url")
    var targetStorageUrl: String? = null,

    @ColumnInfo(name = "target_storage_name")
    var targetStorageName: String? = null,

    @ColumnInfo(name = "create_time")
    var createTime: Long = System.currentTimeMillis()
)

object DownloadState {
    const val WAITING = 0
    const val DOWNLOADING = 1
    const val PAUSED = 2
    const val COMPLETED = 3
    const val FAILED = 4
    const val CANCELLED = 5
}
