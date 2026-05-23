package com.xyoye.common_component.scrape

import android.util.Log

object FileNameParser {

    private const val TAG = "FileNameParser"

    private val seasonPatterns = listOf(
        Regex("""[Ss](\d{1,2})[Ee]\d{1,3}"""),
        Regex("""[Ss](\d{1,2})"""),
        Regex("""第([一二三四五六七八九十百零\d]+)季"""),
        Regex("""[Ss]eason\s*(\d{1,2})"""),
        Regex("""季"""),
        Regex("""\s+S(\d{1,2})\b""")
    )

    private val episodePatterns = listOf(
        Regex("""[Ee](\d{2,})[\s.]*"""),
        Regex("""[Ee]pisode\s*\d{1,2}"""),
        Regex("""第(\d{1,3})[集话話]""")
    )

    private val releaseInfoPatterns = listOf(
        Regex("""\d{3,4}p"""),
        Regex("""\d{2,3}fps"""),
        Regex("""\b4K\b"""),
        Regex("""HEVC|H\.?264|H\.?265|x264|x265|AVC|AV1"""),
        Regex("""10bit|8bit|HLG"""),
        Regex("""HDR10\+|HDR10"""),
        Regex("""WEB-DL|WEBRip|BluRay|BRRip|HDTV|DVDRip|HDRip|WEB\.DL"""),
        Regex("""\bDDP\b|\bDTS\b"""),
        Regex("""DDP\d+(?:\.\d+)?|DTS\d+(?:\.\d+)?|AC3|AAC|FLAC|EAC3"""),
        Regex("""\d+Audios\b"""),
        Regex("""\bComplete\b"""),
        Regex("""\bHQ\b|\bFHD\b|\bUHD\b|\bSDR\b|\bHDR\b|\bDV\b|\bDoVi\b|\bAtmos\b|\bTrueHD\b"""),
        Regex("""\biTunes\b|\bHybrid\b|\bREMUX\b"""),
        Regex("""[\-\s]+[\w\u4e00-\u9fff]+$""")
    )

    private val yearPattern = Regex("""[\(\[（]?(19\d{2}|20\d{2})[\)\]）]?""")

    private val cleanupPatterns = listOf(
        Regex("""\b\d{1,2}\b"""),
        Regex("""\b[A-Z]{2,3}\b"""),
        Regex("""\b(?=[A-Z0-9]*[A-Z])[A-Z0-9]{3,}\b"""),
        Regex("""\b[a-zA-Z]*[Uu]dio\w*\b"""),
        Regex("""\b[a-zA-Z]*[Ss]tudio\w*\b""")
    )

    fun handleSeasonName(name: String): String? {
        var result = name.trim()
        Log.d(TAG, "handleSeasonName input: $result")
        for (pattern in seasonPatterns) {
            val before = result
            result = pattern.replace(result, "")
            if (result != before) Log.d(TAG, "  season pattern [${pattern.pattern}] removed, now: $result")
        }
        for (pattern in episodePatterns) {
            val before = result
            result = pattern.replace(result, "")
            if (result != before) Log.d(TAG, "  episode pattern [${pattern.pattern}] removed, now: $result")
        }
        for (pattern in releaseInfoPatterns) {
            val before = result
            result = pattern.replace(result, "")
            if (result != before) Log.d(TAG, "  release pattern [${pattern.pattern}] removed, now: $result")
        }
        result = result.replace(Regex("[.\\s]+"), " ").trim()
        Log.d(TAG, "  after period-to-space: $result")
        for (pattern in cleanupPatterns) {
            val before = result
            result = pattern.replace(result, "")
            if (result != before) Log.d(TAG, "  cleanup pattern [${pattern.pattern}] removed: $before -> $result")
        }
        result = result.replace(Regex("\\s{2,}"), " ").trim()
        result = result.replace(Regex("[()（）]"), "")
        result = result.trimEnd('-', ' ', '.')
        Log.d(TAG, "handleSeasonName result: $result")
        if (result.isEmpty()) return null
        return result
    }

    fun handleNameYear(name: String): String? {
        val match = yearPattern.find(name)
        return match?.groupValues?.get(1)
    }

    fun detectMediaType(name: String): String {
        var check = name.replace(Regex("[.\\s]+"), " ")
        for (pattern in seasonPatterns) {
            if (pattern.containsMatchIn(check)) return "tv"
        }
        for (pattern in episodePatterns) {
            if (pattern.containsMatchIn(check)) return "tv"
        }
        return "movie"
    }
}