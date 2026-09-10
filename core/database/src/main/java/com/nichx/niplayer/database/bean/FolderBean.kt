package com.nichx.niplayer.database.bean

import androidx.room.ColumnInfo

/**
 * VideoDao.getAllFolder / getFolderByFilter 的查询结果 POJO。
 * 已移除 @Ignore isLastPlay（UI 层临时状态，不应持久化）。
 */
data class FolderBean(
    @ColumnInfo(name = "folder_path")
    var folderPath: String,

    @ColumnInfo(name = "file_count")
    var fileCount: Int,

    @ColumnInfo(name = "filter")
    var isFilter: Boolean = false
)
