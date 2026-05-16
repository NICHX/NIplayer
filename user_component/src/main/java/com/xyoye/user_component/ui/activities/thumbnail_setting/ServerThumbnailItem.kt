package com.xyoye.user_component.ui.activities.thumbnail_setting

import com.xyoye.data_component.enums.MediaType

data class ServerThumbnailItem(
    val libraryId: Int,
    val displayName: String,
    val mediaType: MediaType,
    val enabled: Boolean
)
