package com.xyoye.common_component.utils

import android.graphics.Bitmap
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

object ThumbnailMemoryCache {

    private const val MAX_CACHE_SIZE_BYTES = 32 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.allocationByteCount
        }
    }

    /**
     * 缩略图文件路径缓存
     * key: uniqueKey, value: 缩略图文件的绝对路径
     * 避免每次绑定都调用 fileCover() 中的 File.exists() IO 操作
     */
    private val coverPathCache = ConcurrentHashMap<String, String>()

    fun get(uniqueKey: String): Bitmap? {
        return cache.get(uniqueKey)
    }

    fun put(uniqueKey: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        cache.put(uniqueKey, bitmap)
    }

    fun remove(uniqueKey: String) {
        cache.remove(uniqueKey)
        coverPathCache.remove(uniqueKey)
    }

    fun clear() {
        cache.evictAll()
        coverPathCache.clear()
    }

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
}
