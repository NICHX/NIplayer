package com.xyoye.common_component.scrape

import android.util.Log

object FileNameParser {

    private const val TAG = "FileNameParser"

    private val seasonPatterns = listOf(
        Regex("""[Ss](\d{1,2})[Ee](\d{1,3})"""),
        Regex("""(\d{1,2})x(\d{1,3})"""),
        Regex("""[Ss](\d{1,2})(?:\b|[^Ee])"""),
        Regex("""第([一二三四五六七八九十百零\d]+)季"""),
        Regex("""[Ss]eason\s*(\d{1,2})"""),
    )

    private val episodePatterns = listOf(
        Regex("""[Ee]p(\d{1,3})"""),
        Regex("""[Ee](\d{2,})[\s.]*"""),
        Regex("""[Ee]pisode\s*(\d{1,2})"""),
        Regex("""第(\d{1,3})[集话話]""")
    )

    private val releaseInfoPatterns = listOf(
        Regex("""\d{3,4}p"""),
        Regex("""\d{2,3}fps"""),
        Regex("""\b4K\b|\bUHD\b|\bFHD\b|\bQHD\b|\bHD\b|\bSD\b"""),
        Regex("""HEVC|H\.?264|H\.?265|x264|x265|AVC|AV1"""),
        Regex("""XVID|DIVX|VP[89]|MPEG[.\s]?[24]"""),
        Regex("""10bit|8bit|HLG"""),
        Regex("""\bHDR(?:10(?:\+)?)?\b|\bSDR\b|\bDV\b|\bDoVi\b|\bDolby[.\s]?Vision\b"""),
        Regex("""WEB-DL|WEBRip|BluRay|Blu-ray|BRRip|HDTV|DVDRip|HDRip"""),
        Regex("""BDRip|Blu[.\s\-]?[Rr]ay|WEB[.\s]?DL|WEB[.\s]?Rip|WEBDL|WEBRIP"""),
        Regex("""TELE[.\s]?SYNC|TS|TC|CAM|HDCAM|PDTV|DSR|DTH|TVRip"""),
        Regex("""DVD[.\s]?Rip|DVD[.\s]?Scr|DVD5|DVD9"""),
        Regex("""\bDDP\b|\bDTS\b|\bAC3\b|\bAAC\b|\bFLAC\b|\bEAC3\b"""),
        Regex("""DTS-X|DTS-HD|DTS[.\s]?HD[.\s]?MA|TrueHD[.\s]?Atmos"""),
        Regex("""\bAtmos\b|\bTrueHD\b"""),
        Regex("""\bNF\b|\bAMZN\b|\bDSNP\b|\bHULU\b|\bHBO\b|\bHMAX\b|\biT\b|\biPlayer\b|\bSTAN\b|\bATVP\b|\bCRAV\b"""),
        Regex("""\bNETFLIX\b|\bDISNEY\b|\bAMAZON\b"""),
        Regex("""\bMulti\b|\bMultilingual\b"""),
        Regex("""HC|HARDSUB|中字|简中|繁中|双语|中英|CHT|CHS|SUBBED|国语|国粤|国英|粤语|台配"""),
        Regex("""Directors\.Cut|Extended|UNRATED|REMASTERED|RERIP|CRITERION"""),
        Regex("""\biTunes\b|\bHybrid\b|\bREMUX\b"""),
        Regex("""\bREPACK\b|\bPROPER\b|\bEXTENDED\b|\bTHEATRICAL\b|\bIMAX\b"""),
        Regex("""\bComplete\b|\bCollection\b|\bTrilogy\b|\bBoxset\b"""),
        Regex("""\bHQ\b"""),
    )

    private val yearPattern = Regex("""[\(\[（]?(19\d{2}|20\d{2})[\)\]）]?""")

    fun handleSeasonName(name: String): String? {
        var result = name.trim()
        Log.d(TAG, "handleSeasonName input: $result")
        for (pattern in seasonPatterns) {
            val before = result
            result = pattern.replace(result, " ").trim()
            if (result != before) Log.d(TAG, "  season pattern [${pattern.pattern}] removed, now: $result")
        }
        for (pattern in episodePatterns) {
            val before = result
            result = pattern.replace(result, " ").trim()
            if (result != before) Log.d(TAG, "  episode pattern [${pattern.pattern}] removed, now: $result")
        }
        for (pattern in releaseInfoPatterns) {
            val before = result
            result = pattern.replace(result, " ").trim()
            if (result != before) Log.d(TAG, "  release pattern [${pattern.pattern}] removed, now: $result")
        }
        result = result.replace(Regex("[.\\s]+"), " ").trim()
        result = result.replace(Regex("[\\[\\]()（）【】]"), " ").trim()
        result = result.replace(Regex("\\s{2,}"), " ").trim()
        result = result.trimEnd('-', '.', ' ', '_')
        Log.d(TAG, "handleSeasonName result: $result")
        if (result.isEmpty() || result.length < 2) return null
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

    fun extractTmdbId(text: String): String? {
        val patterns = listOf(
            Regex("""\[tmdbid=(\d+)\]""", RegexOption.IGNORE_CASE),
            Regex("""tmdbid[=\-](\d+)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    fun extractBaseTitle(name: String): String {
        val cleaned = handleSeasonName(name) ?: return name.substringBeforeLast('.').trim()
        return cleaned
    }
}