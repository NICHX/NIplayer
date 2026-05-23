package com.xyoye.common_component.scrape

import android.util.Log

data class NfoData(
    val title: String? = null,
    val plot: String? = null,
    val rating: Double = 0.0,
    val premiered: String? = null,
    val tmdbId: Int? = null,
    val thumb: String? = null,
    val fanart: String? = null
)

object NfoReader {

    fun parseNfo(nfoContent: String): NfoData? {
        return try {
            NfoData(
                title = extractTag(nfoContent, "title"),
                plot = extractTag(nfoContent, "plot"),
                rating = extractTag(nfoContent, "rating")?.toDoubleOrNull() ?: 0.0,
                premiered = extractTag(nfoContent, "premiered"),
                tmdbId = extractTag(nfoContent, "tmdbid")?.toIntOrNull(),
                thumb = extractTopLevelThumb(nfoContent),
                fanart = extractFanartThumb(nfoContent)
            )
        } catch (e: Exception) {
            Log.e("NfoReader", "parseNfo failed", e)
            null
        }
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractTopLevelThumb(xml: String): String? {
        val fanartBlock = Regex("<fanart>.*?</fanart>", RegexOption.DOT_MATCHES_ALL).find(xml)
        val beforeFanart = fanartBlock?.let { xml.substring(0, it.range.first) } ?: xml
        val regex = Regex("<thumb>(.*?)</thumb>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(beforeFanart)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractFanartThumb(xml: String): String? {
        val regex = Regex("<fanart>\\s*<thumb>(.*?)</thumb>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
