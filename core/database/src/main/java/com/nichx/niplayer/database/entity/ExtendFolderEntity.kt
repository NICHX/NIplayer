package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * 扩展文件夹表，记录用户在本地扫描中手动展开的目录。
 * 迁移自旧仓库 extend_folder 表。
 */
@Entity(tableName = "extend_folder")
@JsonClass(generateAdapter = true)
data class ExtendFolderEntity(

    @PrimaryKey
    @ColumnInfo(name = "folder_path")
    var folderPath: String,

    @ColumnInfo(name = "child_count")
    var childCount: Int,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
)
