package com.xyoye.common_component.scrape

object MediaInfoExtractor {

    private val STANDALONE_YEAR_REGEX = Regex("""(^|[.\s_\-\(\[【])((?:19|20)\d{2})($|[.\s_\-\)\]】])""")
    private val DATE_EPISODE_REGEX = Regex("""(20\d{2})[-_. ](0[1-9]|1[0-2])[-_. ](0[1-9]|[12]\d|3[01])""")

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "m2ts", "avi", "mov", "ts", "m3u8", "iso",
        "flv", "wmv", "rmvb", "3gp", "webm"
    )

    private val TECH_WORDS = listOf(
        "1080p", "720p", "2160p", "4k", "8k", "uhd", "fhd", "hd", "sd",
        "hdr", "hdr10", "hdr10+", "dolby vision", "dv", "hlg",
        "dolby", "atmos", "truehd", "dts", "dts-hd", "aac", "ac3", "flac", "flac2.0",
        "x264", "x265", "hevc", "h264", "h265", "h.264", "h.265", "avc", "av1", "10bit", "8bit",
        "bluray", "blu-ray", "bdrip", "brrip", "webrip", "web-dl", "webdl", "hdrip", "dvdrip",
        "hdtv", "pdtv", "dsr", "dvdscr", "r5", "tc", "ts", "cam", "hdcam",
        "60fps", "30fps", "24fps", "fps",
        "简体中文", "繁体中文", "中英字幕", "国语", "粤语", "英语", "日语", "韩语",
        "chinese", "english", "subbed", "dubbed", "sub", "dub",
        "uncut", "directors cut", "extended", "remastered", "unrated", "proper", "rerip",
        "web", "amzn", "nf", "dsnp", "hmax", "atvp", "pmtp", "pcok", "hulu",
        "gb", "mb", "高清", "超清", "蓝光", "收藏版", "纪念版", "高码率"
    )

    fun extract(
        filename: String,
        parentFolder: String = ""
    ): MediaInfo {
        val nameWithoutExt = removeExtension(filename)
        val folderWithoutExt = removeExtension(parentFolder)

        val tmdbId = extractTmdbId(nameWithoutExt) ?: extractTmdbId(folderWithoutExt)
        val fileNameType = analyzeFileNameType(nameWithoutExt)

        val episodeInfo = extractEpisodeInfo(nameWithoutExt)
        val dateEpisode = DATE_EPISODE_REGEX.find(nameWithoutExt)

        val yearFromFile = extractYear(nameWithoutExt)
        val yearFromFolder = extractYear(folderWithoutExt)
        val year = yearFromFile ?: yearFromFolder

        val titleExtract = extractTitle(nameWithoutExt, folderWithoutExt, fileNameType)
        val mediaType = detectMediaType(titleExtract.title, nameWithoutExt, parentFolder, episodeInfo)

        val mergedSeason = titleExtract.seasonFromFolder ?: episodeInfo?.first
        val mergedEpisode = dateEpisode?.let { 0 } ?: episodeInfo?.second

        return MediaInfo(
            title = titleExtract.title,
            displayTitle = titleExtract.displayTitle,
            year = year,
            season = mergedSeason,
            episode = mergedEpisode,
            type = mediaType,
            originalFilename = filename,
            tmdbId = tmdbId
        )
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

    private fun analyzeFileNameType(name: String): FileNameType {
        if (name.matches(Regex("^\\d{1,3}([\\-_.\\s].*)?$"))) {
            return FileNameType.PURE_EPISODE
        }
        if (DATE_EPISODE_REGEX.containsMatchIn(name)) {
            return FileNameType.DATE_EPISODE
        }
        if (name.matches(Regex("^[Ss]\\d{1,2}[Ee]\\d{1,3}.*"))) {
            return FileNameType.EPISODE_ONLY
        }
        if (Regex("[Ss]\\d{1,2}[Ee]\\d{1,3}").containsMatchIn(name) ||
            Regex("第\\d{1,3}集|[Ee]\\d{1,3}|\\d{1,2}x\\d{1,3}").containsMatchIn(name)) {
            return FileNameType.FULL_INFO
        }
        return FileNameType.MOVIE_OR_OTHER
    }

    private fun extractTitle(
        filename: String,
        parentFolder: String,
        fileNameType: FileNameType
    ): TitleExtract {
        val cleanedFilename = cleanTechWords(filename)

        return when (fileNameType) {
            FileNameType.PURE_EPISODE, FileNameType.DATE_EPISODE, FileNameType.EPISODE_ONLY -> {
                val (t, season) = extractTitleAndSeasonFromFolder(parentFolder)
                TitleExtract(t, t, season)
            }
            FileNameType.FULL_INFO -> {
                val title = extractTitleBeforeSeasonEpisode(cleanedFilename)
                val (folderTitle, folderSeason) = extractTitleAndSeasonFromFolder(parentFolder)
                val displayTitle = title.ifEmpty { folderTitle }
                TitleExtract(title.ifEmpty { folderTitle }, displayTitle, folderSeason.takeIf { title.isEmpty() })
            }
            FileNameType.MOVIE_OR_OTHER -> {
                val (folderTitle, folderSeason) = extractTitleAndSeasonFromFolder(parentFolder)
                val title = if (cleanedFilename.length > 2 && !cleanedFilename.matches(Regex("^\\d+$"))) {
                    cleanedFilename
                } else {
                    folderTitle
                }
                TitleExtract(title, title, folderSeason.takeIf { title == folderTitle })
            }
        }
    }

    private fun extractTitleAndSeasonFromFolder(folder: String): Pair<String, Int?> {
        if (folder.isEmpty()) return "" to null
        var title = removeStandaloneYear(folder)
        title = title.replace(Regex("""\[tmdbid=\d+\]"""), " ")
        val seasonPatterns = listOf(
            Regex("""第\s*([0-9]+)\s*季"""),
            Regex("""第\s*([一二三四五六七八九十百千万两]+)\s*季"""),
            Regex("""(?i)S(?:eason)?\s*(\d{1,2})""")
        )
        var season: Int? = null
        for (pattern in seasonPatterns) {
            val match = pattern.find(title)
            if (match != null) {
                val value = match.groupValues[1]
                season = value.toIntOrNull() ?: chineseNumeralToInt(value)
                if (season != null) {
                    title = title.removeRange(match.range).trim()
                    break
                }
            }
        }
        title = title.replace(".", " ").replace("_", " ").replace("-", " ")
        title = title.replace(Regex("\\s+"), " ").trim()
        return title to season
    }

    private fun extractTitleBeforeSeasonEpisode(filename: String): String {
        val patterns = listOf(
            Regex("[Ss]\\d{1,2}[.\\s_-]*[Ee]\\d{1,3}"),
            Regex("第\\d{1,2}季"),
            Regex("[Ee]\\d{1,3}"),
            Regex("\\d{1,2}x\\d{1,3}")
        )
        for (pattern in patterns) {
            val match = pattern.find(filename)
            if (match != null && match.range.first > 0) {
                var title = filename.substring(0, match.range.first)
                title = removeStandaloneYear(title)
                title = title.replace(Regex("""\[tmdbid=\d+\]"""), " ")
                title = title.replace(".", " ").replace("_", " ").replace("-", " ")
                title = title.replace(Regex("\\s+"), " ").trim()
                if (title.isNotEmpty() && title.length > 1) return title
            }
        }
        return ""
    }

    private fun extractEpisodeInfo(filename: String): Pair<Int, Int>? {
        val patterns = listOf(
            Regex("[Ss](\\d{1,2})[.\\s_-]*[Ee](\\d{1,3})"),
            Regex("[Ss]eason\\s*(\\d{1,2}).*[Ee]pisode\\s*(\\d{1,3})"),
            Regex("(\\d{1,2})x(\\d{1,3})"),
            Regex("第(\\d{1,2})季.*第(\\d{1,3})集"),
            Regex("[Ee][Pp]?(\\d{1,3})"),
            Regex("第(\\d{1,3})集"),
            Regex("(\\d{1,3})\\s*[Vv][Oo][Ll]")
        )
        for (pattern in patterns) {
            val match = pattern.find(filename) ?: continue
            val groups = match.groupValues
            if (groups.size >= 3) {
                val s = groups[1].toIntOrNull() ?: continue
                val e = groups[2].toIntOrNull() ?: continue
                return Pair(s, e)
            }
            if (groups.size == 2) {
                val e = groups[1].toIntOrNull() ?: continue
                return Pair(1, e)
            }
        }
        return null
    }

    private fun extractYear(text: String): Int? {
        val match = STANDALONE_YEAR_REGEX.find(text) ?: Regex("""((?:19|20)\d{2})""").find(text) ?: return null
        val yearStr = match.groupValues.lastOrNull { it.length == 4 } ?: return null
        val year = yearStr.toIntOrNull() ?: return null
        return if (year in 1900..2099) year else null
    }

    private fun detectMediaType(
        title: String,
        filename: String,
        parentFolder: String,
        episodeInfo: Pair<Int, Int>?
    ): MediaType {
        if (episodeInfo != null) return MediaType.TV_SHOW
        val lower = (parentFolder + "/" + filename).lowercase()
        return when {
            CONCERT_KEYWORDS.any { lower.contains(it) } -> MediaType.CONCERT
            VARIETY_KEYWORDS.any { lower.contains(it) } -> MediaType.VARIETY
            ANIME_KEYWORDS.any { lower.contains(it) } -> MediaType.ANIME
            DOCUMENTARY_KEYWORDS.any { lower.contains(it) } -> MediaType.DOCUMENTARY
            else -> MediaType.MOVIE
        }
    }

    private fun cleanTechWords(text: String): String {
        var result = text
        TECH_WORDS.forEach { word ->
            result = result.replace(word, " ", ignoreCase = true)
        }
        result = result.replace(Regex("""[\\-–@][A-Za-z0-9]+$"""), "")
        result = result.replace(Regex("\\s+"), " ").trim()
        return result
    }

    private fun removeStandaloneYear(text: String): String {
        return text.replace(STANDALONE_YEAR_REGEX, "$1$3")
    }

    private fun removeExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex <= 0) return name
        val ext = name.substring(dotIndex + 1).lowercase()
        return if (ext in VIDEO_EXTENSIONS || ext.length <= 4) name.substring(0, dotIndex) else name
    }

    private fun chineseNumeralToInt(s: String): Int? {
        if (s.isEmpty()) return null
        val map = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
            '两' to 2, '壹' to 1, '贰' to 2, '叁' to 3, '肆' to 4, '伍' to 5,
            '陆' to 6, '柒' to 7, '捌' to 8, '玖' to 9
        )
        if (s.length == 1) {
            val d = map[s[0]]
            if (d != null && d in 1..9) return d
        }
        if (s == "十") return 10
        if (s.startsWith("十") && s.length == 2) {
            val u = map[s[1]] ?: return null
            return 10 + u
        }
        if (s.endsWith("十") && s.length == 2) {
            val t = map[s[0]] ?: return null
            return t * 10
        }
        if (s.length == 3 && s[1] == '十') {
            val a = map[s[0]] ?: return null
            val b = map[s[2]] ?: return null
            return a * 10 + b
        }
        return s.toIntOrNull()
    }

    private data class TitleExtract(
        val title: String,
        val displayTitle: String,
        val seasonFromFolder: Int? = null
    )

    private val CONCERT_KEYWORDS = listOf("演唱会", "音乐会", "concert", "live show", "live")
    private val VARIETY_KEYWORDS = listOf("综艺", "真人秀", "脱口秀", "variety", "reality show")
    private val ANIME_KEYWORDS = listOf("动漫", "动画", "番剧", "ova", "剧场版", "anime")
    private val DOCUMENTARY_KEYWORDS = listOf("纪录片", "纪实", "documentary", "docu", "bbc")
}