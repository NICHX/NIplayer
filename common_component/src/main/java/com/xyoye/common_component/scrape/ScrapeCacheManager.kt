package com.xyoye.common_component.scrape

import java.util.concurrent.ConcurrentHashMap

class ScrapeCacheManager(
    private val cacheTimeMinutes: Long = 30
) {

    data class CacheEntry(
        val seriesKey: String,
        val title: String,
        val poster: String?,
        val backdrop: String?,
        val tmdbId: Int?,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(seriesKey: String): CacheEntry? {
        val entry = cache[seriesKey] ?: return null
        if (elapsedMinutes(entry.timestamp) > cacheTimeMinutes) {
            cache.remove(seriesKey)
            return null
        }
        return entry
    }

    fun put(
        seriesKey: String,
        title: String,
        poster: String?,
        backdrop: String?,
        tmdbId: Int?
    ) {
        cache[seriesKey] = CacheEntry(
            seriesKey = seriesKey,
            title = title,
            poster = poster,
            backdrop = backdrop,
            tmdbId = tmdbId,
            timestamp = System.currentTimeMillis()
        )
    }

    fun clear() {
        cache.clear()
    }

    private fun elapsedMinutes(timestamp: Long): Long {
        return (System.currentTimeMillis() - timestamp) / 60_000
    }
}