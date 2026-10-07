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

/**
 * 估算「这一行唱多久」（毫秒）：字数 × [LINE_MS_PER_CHAR]，夹在 [MIN_LINE_MS] / [MAX_LINE_MS] 之间。
 *
 * 普通 LRC 只有行首时间戳，行末时间戳不可得；**不能拿「下一行的开始」当行末** ——
 * 中间隔一段长间奏时，最后一个字会被拉着慢慢擦好几秒，与实际的唱词完全对不上。
 */
private fun estimatedLineMs(textLength: Int): Long =
    (textLength * LINE_MS_PER_CHAR).coerceIn(MIN_LINE_MS, MAX_LINE_MS)

private const val LINE_MS_PER_CHAR = 220L
private const val MIN_LINE_MS = 1_500L
private const val MAX_LINE_MS = 6_000L

/**
 * 当前行已唱到的**比例**（0..1）—— 供歌词做「已唱 / 未唱」分色。
 *
 * 返回比例而不是字数：按字数取整会让高亮一个字一个字地跳。
 *
 * 行末落点取「[estimatedLineMs] 估算的吟唱结束」与「下一行开始」里**更早**的那个：
 * 直接拿下一行开始当行末，遇到长间奏时最后一个字会被拉着慢慢擦好几秒（逐字歌词尤其明显）。
 *
 * 优先用 Enhanced LRC 的逐字时间戳（把每个词的起始时间换算成「字数占比」的锚点，
 * 再在锚点之间插值，精确到词）；普通 LRC 按行时长线性推进。
 *
 * @param wordTimes 逐字时间戳；空列表表示普通 LRC
 * @param lineStartMs 本行开始时间
 * @param nextLineStartMs 下一行的开始时间；null 表示这是最后一行
 * @return 0f..1f
 */
internal fun sungFraction(
    text: String,
    wordTimes: List<Pair<String, Long>>,
    lineStartMs: Long,
    nextLineStartMs: Long?,
    positionMs: Long,
): Float {
    if (text.isEmpty()) return 0f
    if (positionMs <= lineStartMs) return 0f
    val estimatedEndMs = lineStartMs + estimatedLineMs(text.length)
    val nextStartMs = nextLineStartMs?.takeIf { it > lineStartMs }
    val endMs = (nextStartMs?.let { minOf(it, estimatedEndMs) } ?: estimatedEndMs)
        .coerceAtLeast(lineStartMs + 1L)

    if (wordTimes.isEmpty()) {
        return ((positionMs - lineStartMs).toFloat() / (endMs - lineStartMs)).coerceIn(0f, 1f)
    }

    val length = text.length.toFloat()
    var cursor = 0
    var anchorFraction = 0f
    var anchorTimeMs = lineStartMs
    wordTimes.forEach { (word, timeMs) ->
        val found = text.indexOf(word, cursor)
        if (found < 0) return@forEach
        val fraction = found / length
        if (positionMs < timeMs) {
            // 还没唱到这个词：在上一个锚点与它之间插值
            val span = (timeMs - anchorTimeMs).coerceAtLeast(1L)
            val ratio = ((positionMs - anchorTimeMs).toFloat() / span).coerceIn(0f, 1f)
            return anchorFraction + (fraction - anchorFraction) * ratio
        }
        anchorFraction = fraction
        anchorTimeMs = timeMs
        cursor = found + word.length
    }
    // 所有词都已开始：末词的结束时间没有时间戳，按词间平均间隔收口
    // （只按行末算的话，末词又会被拉长到整个行尾）
    val typicalGapMs = if (wordTimes.size >= 2) {
        ((wordTimes.last().second - wordTimes.first().second) / (wordTimes.size - 1))
            .coerceAtLeast(1L)
    } else {
        estimatedLineMs(text.length)
    }
    val lastWordEndMs = minOf(anchorTimeMs + typicalGapMs, endMs)
        .coerceAtLeast(anchorTimeMs + 1L)
    val ratio = ((positionMs - anchorTimeMs).toFloat() / (lastWordEndMs - anchorTimeMs))
        .coerceIn(0f, 1f)
    return (anchorFraction + (1f - anchorFraction) * ratio).coerceIn(0f, 1f)
}
