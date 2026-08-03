package com.nichx.niplayer.feature.player

/**
 * 歌词行。
 *
 * @param timeMs 行开始时间（毫秒）。
 * @param text 该行显示的纯文本（已去除时间标签）。
 * @param wordTimes 逐字时间戳列表（Enhanced LRC 格式）；为空时表示普通行级歌词。
 *                  [wordTimes] 中每个元素为「词文本, 开始时间(毫秒)」，
 *                  且 [wordTimes] 拼接后的文本与 [text] 一致（仅去除空格差异）。
 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
    val wordTimes: List<Pair<String, Long>> = emptyList(),
)

object LrcParser {

    /** 匹配 [mm:ss.xx] / [mm:ss.xxx] / [mm:ss:xx] 形式的时间标签。 */
    private val TIME_TAG_REGEX = Regex("""\[(\d{2}):(\d{2})[\.:](\d{2,3})\]""")

    /** 匹配 Enhanced LRC 的逐字时间戳内部时间（mm:ss.xx，不含尖括号）。 */
    private val WORD_TIME_REGEX = Regex("""(\d{1,2}):(\d{2})[\.:](\d{2,3})""")

    fun parse(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        content.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            val tags = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (tags.isEmpty()) return@forEach

            // 歌词文本：行级时间标签之后的全部内容
            val lyricRaw = trimmed.substring(tags.last().range.last + 1).trim()
            if (lyricRaw.isEmpty()) return@forEach

            // 提取逐字时间戳（Enhanced LRC：<mm:ss.xx>word <mm:ss.xx>word ...）
            val wordTimes = parseWordTimes(lyricRaw)

            // 若含逐字时间戳，则纯文本 = 各词拼接；否则整行文本
            val lyricText = if (wordTimes.isEmpty()) {
                lyricRaw
            } else {
                wordTimes.joinToString(" ") { it.first }
            }
            if (lyricText.isBlank()) return@forEach

            tags.forEach { match ->
                val timeMs = parseTime(match)
                if (timeMs != null) {
                    lines.add(LrcLine(timeMs, lyricText, wordTimes))
                }
            }
        }

        // 稳定排序：相同 timeMs 保留原插入顺序
        lines.sortBy { it.timeMs }
        return lines
    }

    /** 解析 Enhanced LRC 逐字时间戳，返回「词, 开始时间」列表；无逐字时间戳时返回空列表。 */
    private fun parseWordTimes(raw: String): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        var index = 0
        while (index < raw.length) {
            val open = raw.indexOf('<', index)
            if (open < 0) break
            val close = raw.indexOf('>', open)
            if (close < 0) break
            val tagText = raw.substring(open + 1, close)
            val match = WORD_TIME_REGEX.matchEntire(tagText)
            if (match == null) {
                index = close + 1
                continue
            }
            val timeMs = parseTime(match) ?: run {
                index = close + 1
                continue
            }
            // 词文本 = 时间戳闭合后、下一个 < 之前的内容（含中间空格）
            val nextOpen = raw.indexOf('<', close + 1)
            val wordEnd = if (nextOpen < 0) raw.length else nextOpen
            val wordText = raw.substring(close + 1, wordEnd).trim()
            if (wordText.isNotEmpty()) {
                result.add(wordText to timeMs)
            }
            index = wordEnd
        }
        return result
    }

    private fun parseTime(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val seconds = match.groupValues[2].toIntOrNull() ?: return null
        val millisStr = match.groupValues[3].padEnd(3, '0').take(3)
        val millis = millisStr.toIntOrNull() ?: return null
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
