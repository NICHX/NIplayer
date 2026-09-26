package com.nichx.niplayer.metadata.parse

import com.nichx.niplayer.metadata.model.ParsedTrack

/** 音频扩展名：字母开头、最长 5 位。避免把 `Track 1.2` 的 `.2` 误当扩展名。 */
private val EXTENSION_REGEX = Regex("""\.[A-Za-z][A-Za-z0-9]{0,4}$""")

/**
 * 音轨号前缀。三种形式：`[01]` / `(01)` / `01 -`（含 `.` `_` 变体）。
 *
 * 刻意**不**匹配「数字 + 纯空格」，否则 `500 Miles` 会被解析成音轨 500、歌名 Miles。
 * 宁可漏判（整名当歌名，稍差但可接受）也不误判（歌名被截断，明显错误）。
 */
private val TRACK_NO_REGEX = Regex("""^\s*(?:\[(\d{1,3})\]|\((\d{1,3})\)|(\d{1,3})\s*[-._])\s*""")

/** 成对括号（全角已在 normalize 中转成半角）。 */
private val BRACKET_REGEX = Regex("""[\[(]([^\[\]()]{1,24})[\])]""")

/** 位于开头、内容通常是歌手的方括号。 */
private val LEADING_BRACKET_REGEX = Regex("""^\[([^\[\]]{1,40})\]\s*""")

/** 带空格的破折号：最常见的「歌手 - 歌名」分隔符，含 en dash / em dash。 */
private val SPACED_DASH_REGEX = Regex("""\s+[-–—]\s+""")

/** 任意破折号。 */
private val BARE_DASH_REGEX = Regex("""[-–—]""")

/** 中日韩汉字。用于判断裸破折号是否可信（`周杰伦-稻香` vs `X-Ray`）。 */
private val CJK_REGEX = Regex("""[\u4e00-\u9fff]""")

private val SEPARATOR_REGEX = Regex("""[._]+""")

private val WHITESPACE_REGEX = Regex("""\s+""")

/** 歌手名开头的裸数字，如 `01 周杰伦 - 稻香` 里残留的音轨号。 */
private val LEADING_NUMBER_REGEX = Regex("""^\d{1,3}\s+""")

private val TRIM_CHARS = setOf(' ', '-', '–', '—', '_', '.', '·', '|', ',', '、', ':', ';')

/**
 * 版本标记关键词（比较时去掉空格并转小写）。
 *
 * 命中才把括号内容当版本剥离；否则括号内容可能是歌手名（如 `[周杰伦]`），必须保留。
 */
private val VERSION_KEYWORDS = listOf(
    "live", "remix", "acoustic", "instrumental", "karaoke", "cover", "demo",
    "tvsize", "offvocal", "official", "inst.",
    "现场", "演唱会", "伴奏", "纯音乐", "翻唱", "重制", "修复", "试听", "无损", "重置",
    "hq", "hd", "hires", "remaster",
)

/**
 * 通用目录名。这些目录名不是专辑 / 歌手，不能作为目录线索。
 * 例：`/sdcard/Music/稻香.mp3` 的父目录 `Music` 与专辑无关。
 */
private val GENERIC_DIRS = setOf(
    "music", "musics", "audio", "audios", "song", "songs", "download", "downloads",
    "音乐", "歌曲", "音频", "下载", "我的音乐", "本地音乐", "音乐库",
    "sdcard", "storage", "emulated", "dcim", "documents", "0", "1",
)

/** 中间结果：`value` 为提取到的信息，`remainder` 为剥离后的剩余字符串。 */
private data class Step<T>(val value: T?, val remainder: String)

/**
 * 从文件名 + 目录结构解析曲目信息。**纯函数**，零 Android 依赖，可直接跑 JVM 单测。
 *
 * 这是整个音频匹配链的地基。重做前对应的 `parseTitleArtist()`
 * （`AudioPlaybackManager.kt:439`）只有 10 行，仅按**最后一个** `" - "` 切分，导致：
 * - `01 - 周杰伦 - 稻香` → 歌手变成 `01 - 周杰伦`（音轨号混进歌手）
 * - `周杰伦-稻香`（无空格）→ 完全不切分
 * - `【周杰伦】稻香` → 方括号歌手不识别
 * - `稻香 (Live)` → 带着版本标记去查询，查不到
 *
 * 解析顺序即成败：
 * 1. 去扩展名、全角括号归一
 * 2. 提取音轨号前缀
 * 3. 剥离版本标记括号（只剥命中关键词的，避免误伤歌手括号）
 * 4. 提取前置方括号歌手
 * 5. 按破折号切分「歌手 - 歌名」
 * 6. 清理残留分隔符得到歌名，空则回退父目录名
 * 7. 父 / 祖父目录作为专辑 / 歌手线索
 *
 * @param fileName 文件名，可含扩展名（会被去掉）
 * @param parentDir 父目录名，通常为专辑名
 * @param grandParentDir 祖父目录名，通常为歌手名
 */
fun parseFileName(
    fileName: String,
    parentDir: String? = null,
    grandParentDir: String? = null,
): ParsedTrack {
    val original = stripExtension(normalize(fileName))
    var work = original

    val trackStep = extractTrackNo(work)
    work = trackStep.remainder

    val versionStep = extractVersions(work)
    work = versionStep.remainder

    val bracketStep = extractLeadingBracketArtist(work)
    if (bracketStep != null) work = bracketStep.remainder

    val dash = splitByDash(work)
    val artist = (bracketStep?.value ?: dash.artist)?.let(::cleanArtist)

    var title = cleanText(dash.title)
    if (title.isEmpty()) title = cleanText(work)
    if (title.isEmpty()) title = cleanText(original)

    val album = parentDir?.let(::normalize)?.takeIf(::isMeaningfulDir)
    val artistFromDir = grandParentDir?.let(::normalize)?.takeIf(::isMeaningfulDir)

    return ParsedTrack(
        title = title,
        artist = artist ?: artistFromDir,
        album = album,
        trackNo = trackStep.value,
        version = versionStep.value,
    )
}

/** 全角括号与全角空格归一为半角，去首尾空白。 */
private fun normalize(raw: String): String = raw
    .replace('（', '(')
    .replace('）', ')')
    .replace('【', '[')
    .replace('】', ']')
    .replace('\u3000', ' ')
    .trim()

private fun stripExtension(name: String): String = EXTENSION_REGEX.replace(name, "").trim()

private fun extractTrackNo(name: String): Step<Int> {
    val match = TRACK_NO_REGEX.find(name) ?: return Step(null, name)
    val digits = match.groupValues[1]
        .ifEmpty { match.groupValues[2] }
        .ifEmpty { match.groupValues[3] }
    return Step(digits.toIntOrNull(), name.removeRange(match.range).trim())
}

private fun extractVersions(name: String): Step<String> {
    var firstVersion: String? = null
    val cleaned = BRACKET_REGEX.replace(name) { match ->
        val inner = match.groupValues[1].trim()
        if (isVersionTag(inner)) {
            if (firstVersion == null) firstVersion = inner
            " "
        } else {
            match.value
        }
    }
    return Step(firstVersion, cleaned.trim())
}

private fun isVersionTag(inner: String): Boolean {
    val compact = inner.lowercase().replace(" ", "")
    if (compact.isEmpty()) return false
    return VERSION_KEYWORDS.any { compact.contains(it.replace(" ", "")) }
}

private fun extractLeadingBracketArtist(name: String): Step<String>? {
    val match = LEADING_BRACKET_REGEX.find(name) ?: return null
    val inner = match.groupValues[1].trim()
    if (inner.isEmpty() || isVersionTag(inner)) return null
    return Step(inner, name.removeRange(match.range).trim())
}

/** 破折号切分结果。`artist` 为 null 表示未识别出歌手。 */
private data class DashSplit(val artist: String?, val title: String)

private fun splitByDash(name: String): DashSplit {
    // 优先带空格的破折号：`周杰伦 - 稻香`
    SPACED_DASH_REGEX.find(name)?.let { match ->
        val left = cleanText(name.substring(0, match.range.first))
        val right = cleanText(name.substring(match.range.last + 1))
        if (left.isNotEmpty() && right.isNotEmpty()) return DashSplit(left, right)
    }

    // 裸破折号：仅当至少一侧含中文时才可信，否则 `X-Ray` 会被拆成 X / Ray
    BARE_DASH_REGEX.find(name)?.let { match ->
        val left = cleanText(name.substring(0, match.range.first))
        val right = cleanText(name.substring(match.range.last + 1))
        val hasCjk = CJK_REGEX.containsMatchIn(left) || CJK_REGEX.containsMatchIn(right)
        if (hasCjk && left.isNotEmpty() && right.isNotEmpty()) return DashSplit(left, right)
    }

    return DashSplit(null, cleanText(name))
}

private fun cleanText(raw: String): String = raw
    .replace(SEPARATOR_REGEX, " ")
    .replace(WHITESPACE_REGEX, " ")
    .trim { it in TRIM_CHARS }

/**
 * 清理歌手名：剥掉开头的裸数字残留，返回 null 表示无有效歌手。
 *
 * `01 周杰伦 - 稻香` 这类「音轨号 + 空格」不在 [TRACK_NO_REGEX] 的匹配范围内
 * （那里刻意要求 `-` `.` `_` 等明确分隔符，以免误伤 `500 Miles`），
 * 但破折号切分会把它带进歌手位，故在此补一刀。
 */
private fun cleanArtist(raw: String): String? =
    cleanText(LEADING_NUMBER_REGEX.replace(raw, "")).ifEmpty { null }

private fun isMeaningfulDir(dir: String): Boolean {
    val trimmed = dir.trim()
    if (trimmed.isEmpty()) return false
    return trimmed.lowercase() !in GENERIC_DIRS
}
