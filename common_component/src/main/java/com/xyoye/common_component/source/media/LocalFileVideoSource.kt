package com.xyoye.common_component.source.media

import com.xyoye.common_component.source.base.BaseVideoSource
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.MediaType

class LocalFileVideoSource(
    private val filePath: String,
    private val fileName: String
) : BaseVideoSource(0, emptyList<Any>()) {
    override fun getVideoUrl() = filePath
    override fun getVideoTitle() = fileName
    override fun getCurrentPosition() = 0L
    override fun getMediaType() = MediaType.LOCAL_STORAGE
    override fun getUniqueKey() = filePath
    override fun getHttpHeader(): Map<String, String>? = null
    override fun getStorageId() = 0
    override fun getStoragePath() = ""
    override fun indexTitle(index: Int): String = fileName
    override suspend fun indexSource(index: Int): BaseVideoSource? = null
    override fun updateFileHistory(playHistory: PlayHistoryEntity?) {}
}
