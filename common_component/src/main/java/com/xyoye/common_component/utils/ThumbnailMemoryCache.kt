package com.xyoye.common_component.utils

import java.util.concurrent.ConcurrentHashMap

object ThumbnailMemoryCache {

    private val coverPathCache = ConcurrentHashMap<String, String>()

    fun getCoverPath(uniqueKey: String): String? {
        return coverPathCache[uniqueKey]
    }

    fun putCoverPath(uniqueKey: String, coverPath: String) {
        coverPathCache[uniqueKey] = coverPath
    }

    fun removeCoverPath(uniqueKey: String) {
        coverPathCache.remove(uniqueKey)
    }

    fun clearCoverPathCache() {
        coverPathCache.clear()
    }

    fun clear() {
        coverPathCache.clear()
    }
}
