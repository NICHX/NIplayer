package com.xyoye.common_component.utils

import java.util.concurrent.ConcurrentHashMap

data class AudioMetadata(
    val artist: String = "",
    val title: String = "",
    val duration: Long = 0L
)

object AudioMetadataCache {

    private val metadataCache = ConcurrentHashMap<String, AudioMetadata>()

    fun get(uniqueKey: String): AudioMetadata? {
        return metadataCache[uniqueKey]
    }

    fun put(uniqueKey: String, metadata: AudioMetadata) {
        metadataCache[uniqueKey] = metadata
    }

    fun remove(uniqueKey: String) {
        metadataCache.remove(uniqueKey)
    }

    fun clear() {
        metadataCache.clear()
    }
}
