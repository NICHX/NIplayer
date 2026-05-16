package com.xyoye.data_component.bean

data class StorageFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val fileSize: Long,
    val lastModified: Long,
    val childCount: Int = 0,
    val isHidden: Boolean = false,
    val readable: Boolean = true,
    val writable: Boolean = true,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isImage: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val durationMs: Long = 0,
    val bitrate: Long = 0,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val frameRate: String? = null,
    val sampleRate: Int = 0,
    val audioChannelCount: Int = 0
) {
    val mimeType: String?
        get() = when {
            isVideo -> "video"
            isAudio -> "audio"
            isImage -> "image"
            else -> null
        }
}
