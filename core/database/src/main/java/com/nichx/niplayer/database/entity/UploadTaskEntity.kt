package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 上传任务表，记录本地文件上传到远程存储（SMB/WebDAV 等）的状态。
 *
 * 与下载任务 [DownloadTaskEntity] 共用状态常量 [DownloadState]（WAITING/TRANSFERRING/
 * COMPLETED/FAILED/CANCELLED 语义一致）。由 [com.nichx.niplayer.storage.download.UploadManager]
 * 在 App 级作用域调度，切出上传触发页面后仍继续执行。
 */
@Entity(tableName = "upload_task")
data class UploadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "storage_id")
    var storageId: Int,

    @ColumnInfo(name = "storage_name")
    var storageName: String,

    @ColumnInfo(name = "file_name")
    var fileName: String,

    /** 远程目标路径（相对存储库根，含文件名）。 */
    @ColumnInfo(name = "remote_path")
    var remotePath: String,

    /** 本地文件源（SAF content:// Uri）。 */
    @ColumnInfo(name = "source_uri")
    var sourceUri: String,

    @ColumnInfo(name = "total_bytes")
    var totalBytes: Long = 0,

    @ColumnInfo(name = "uploaded_bytes")
    var uploadedBytes: Long = 0,

    @ColumnInfo(name = "state")
    var state: Int = DownloadState.WAITING,

    @ColumnInfo(name = "error_message")
    var errorMessage: String? = null,

    @ColumnInfo(name = "create_time")
    var createTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis(),
)