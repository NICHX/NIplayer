package com.xyoye.common_component.scrape

import android.util.Log

object FileNameParser {

    private const val TAG = "FileNameParser"

    private val seasonPatterns = listOf(
        Regex("""[Ss](\d{1,2})[Ee](\d{1,3})"""),
        Regex("""(\d{1,2})x(\d{1,3})"""),
        Regex("""[Ss](\d{1,2})"""),
        Regex("""第([一二三四五六七八九十百零\d]+)季"""),
        Regex("""[Ss]eason\s*(\d{1,2})"""),
        Regex("""季"""),
        Regex("""\s+S(\d{1,2})\b""")
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
        Regex("""23\.976fps|24fps|25fps|30fps|50fps|60fps|120fps"""),
        Regex("""\b4K\b|\bUHD\b|\bFHD\b|\bQHD\b|\bHD\b|\bSD\b"""),
        Regex("""HEVC|H\.?264|H\.?265|x264|x265|AVC|AV1"""),
        Regex("""XVID|DIVX|VP[89]|MPEG[.\s]?[24]"""),
        Regex("""10bit|8bit|HLG"""),
        Regex("""HDR10\+|HDR10"""),
        Regex("""\bDV\b|\bDoVi\b|\bDolby[.\s]?Vision\b|\bSDR\b|\bHDR\b"""),
        Regex("""WEB-DL|WEBRip|BluRay|Blu-ray|Blu-Ray|BRRip|HDTV|DVDRip|HDRip|WEB\.DL"""),
        Regex("""BDRip|Blu[.\s\-]?[Rr]ay|WEB[.\s]?DL|WEB[.\s]?Rip|WEBDL|WEBRIP"""),
        Regex("""TELE[.\s]?SYNC|TS|TC|CAM|HDCAM|PDTV|DSR|DTH|TVRip"""),
        Regex("""DVD[.\s]?Rip|DVD[.\s]?Scr|DVD5|DVD9|DVD-R"""),
        Regex("""\bDDP\b|\bDTS\b"""),
        Regex("""DDP\d+(?:\.\d+)?|DTS\d+(?:\.\d+)?|AC3|AAC|FLAC|EAC3"""),
        Regex("""DTS-X|DTS-HD|DTS[.\s]?HD[.\s]?MA|TrueHD[.\s]?Atmos"""),
        Regex("""\bAtmos\d*\b|\bTrueHD\d*\b"""),
        Regex("""\d+Audios?\b"""),
        Regex("""PCM|LPCM"""),
        Regex("""[.\s]+[257][.\s]?1(?:ch)?[.\s]+"""),
        Regex("""[.\s]+2[.\s]?0(?:ch)?[.\s]+"""),
        Regex("""DDP[.\s]?5[.\s]?1[.\s]?Atmos|DD\+[.\s]?5[.\s]?1[.\s]?Atmos|DV[.\s]?HDR10\+"""),
        Regex("""\bNF\b|\bAMZN\b|\bDSNP\b|\bHULU\b|\bHBO\b|\bHMAX\b|\biT\b|\biPlayer\b|\bSTAN\b|\bATVP\b|\bCRAV\b"""),
        Regex("""\bNETFLIX\b|\bDISNEY\b|\bAMAZON\b"""),
        Regex("""\bMulti\b|\bMultilingual\b"""),
        Regex("""带国语|带国粤|带国英|国粤双语|国英双语"""),
        Regex("""HC|HARDSUB|中字|简中|繁中|双语|中英|CHT|CHS|SUBBED|国语|国粤|国英|粤语|台配"""),
        Regex("""Directors\.Cut|Extended|UNRATED|REMASTERED|RERIP|CRITERION"""),
        Regex("""\bComplete\b|\bCollection\b|\bTrilogy\b|\bBoxset\b"""),
        Regex("""\bHQ\b"""),
        Regex("""\biTunes\b|\bHybrid\b|\bREMUX\b"""),
        Regex("""\bREPACK\b|\bPROPER\b|\bEXTENDED\b|\bTHEATRICAL\b|\bIMAX\b"""),
        Regex("""\bV\d{1,2}\b"""),
        Regex("""(?<=[\u4e00-\u9fff])1\b"""),
        Regex("""\bmUHD\b|\bmHD\b"""),
        Regex("""[\-\s]+[\w\u4e00-\u9fff]+$""")
    )

    private val yearPattern = Regex("""[\(\[（]?(19\d{2}|20\d{2})[\)\]）]?""")

    private val cleanupPatterns = listOf(
        Regex("""\b\d{1,2}\b"""),
        Regex("""\b[A-Z]{2,3}\b"""),
        Regex("""\b(?=[A-Z0-9]{2,})[A-Z0-9]{3,}\b"""),
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
        result = result.replace(Regex("[\\[\\]()（）【】]"), "")
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