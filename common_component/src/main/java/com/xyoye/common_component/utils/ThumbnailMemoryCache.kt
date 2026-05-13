package com.xyoye.common_component.utils

import android.graphics.Bitmap
import android.util.LruCache

object ThumbnailMemoryCache {

    private const val MAX_CACHE_SIZE_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.allocationByteCount
        }
    }

    fun get(uniqueKey: String): Bitmap? {
        return cache.get(uniqueKey)
    }

    fun put(uniqueKey: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        cache.put(uniqueKey, bitmap)
    }

    fun remove(uniqueKey: String) {
        cache.remove(uniqueKey)
    }

    fun clear() {
        cache.evictAll()
    }
}
