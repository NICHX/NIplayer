package com.xyoye.data_component.bean

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuickAccessItem(
    val name: String,
    val storagePath: String,
    val isDirectory: Boolean,
    val libraryId: Long,
    val libraryUrl: String,
    val libraryDisplayName: String,
    val uniqueKey: String = ""
)