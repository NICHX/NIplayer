package com.xyoye.data_component.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scrape_mulu_config")
data class MuluConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "media_library_id")
    val mediaLibraryId: Int,

    @ColumnInfo(name = "mulu_type")
    val muluType: String,

    @ColumnInfo(name = "path")
    val path: String
)
