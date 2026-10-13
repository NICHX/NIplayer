package com.nichx.niplayer.feature.player

/**
 * 歌词行。
 *
 * @param timeMs 行开始时间（毫秒）。
 * @param text 该行显示的纯文本（已去除时间标签与逐字标记）。
 * @param wordTimes 逐字时间戳列表；为空时表示普通行级歌词。
 *                  [wordTimes] 中每个元素为「词文本, 开始时间(毫秒)」，
 *                  且 [wordTimes] 拼接后的文本与 [text] 一致（仅去除空格差异）。
 * @param translation 该行的译文 / 对照翻译；为 null 表示该行无译文。
 */
data class LrcLine(
    val timeMs: Long,
    val text: String,
    val wordTimes: List<Pair<String, Long>> = emptyList(),
    val translation: String? = null,
)

object LrcParser {

    /** 行级时间标签：[mm:ss.xx] / [mm:ss.xxx] / [mm:ss:xx]。 */
    private val LINE_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})[\.:](\d{2,3})\]""")

    /**
     * 逐字时间标签，两种写法都支持：
     * - Enhanced LRC：`<mm:ss.xx>`；
     * - 逐字标记 LRC（`.nilyric`）：`[[mm:ss.xx]]`。
     *
     * 捕获组 1..3 对应 `<..>`，4..6 对应 `[[..]]`（[parseTime] 据此取时间）。
     */
    private val WORD_TAG_REGEX = Regex(
        """<(\d{1,3}):(\d{2})[\.:](\d{2,3})>|\[\[(\d{1,3}):(\d{2})[\.:](\d{2,3})\]\]""",
    )

    /**
     * 解析歌词文本（`.lrc` / `.nilyric` 均可）。
     *
     * 支持三种时间轴：行级 `[mm:ss.xx]`、Enhanced LRC 逐字 `<mm:ss.xx>`、
     * 以及逐字标记 LRC 的 `[[mm:ss.xx]]`。连续多行若时间戳相同，视为「原文 + 译文」对照，
     * 合并为一条 [LrcLine]（首行为原文，其余作为译文）。
     */
    fun parse(content: String): List<LrcLine> = parse(content, null)

    /**
     * 合并「逐行歌词（`.lrc`，可含译文）」与「逐字歌词（`.nilyric`）」两个来源：
     * 逐字来源提供精确的字级时间轴，逐行来源提供译文对照（按相同时间戳挂接）。
     *
     * 两个来源都为空/无效时返回空列表。译文来源（[plainContent]）通常是标准 `.lrc`，
     * 逐字来源（[wordContent]）是同名 `.nilyric`。
     */
    fun parse(plainContent: String?, wordContent: String?): List<LrcLine> {
        val plain = plainContent?.let(::parseSingle).orEmpty()
        val word = wordContent?.let(::parseSingle).orEmpty()
        return when {
            word.isEmpty() -> plain
            plain.isEmpty() -> word
            else -> {
                // 以逐字来源为骨架，按时间戳挂上逐行来源的译文
                val translations = plain.mapNotNull { line ->
                    line.translation?.let { line.timeMs to it }
                }.toMap()
                word.map { line ->
                    when {
                        line.translation != null -> line
                        else -> translations[line.timeMs]?.let { line.copy(translation = it) } ?: line
                    }
                }
            }
        }
    }

    /** 解析单个歌词文件的原始内容。 */
    private fun parseSingle(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        content.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // 先摘出逐字标记，剩余部分（行首）才是行级时间标签与普通文本
            val wordTags = WORD_TAG_REGEX.findAll(trimmed).toList()
            val head = if (wordTags.isEmpty()) {
                trimmed
            } else {
                trimmed.substring(0, wordTags.first().range.first)
            }
            val lineTags = LINE_TAG_REGEX.findAll(head).toList()
            if (lineTags.isEmpty()) return@forEach

            // 逐字时间戳：每个标记之后、下一个标记之前为该词的文本
            val wordTimes = mutableListOf<Pair<String, Long>>()
            wordTags.forEachIndexed { index, tag ->
                val timeMs = parseTime(tag)
                if (timeMs != null) {
                    val textEnd = wordTags.getOrNull(index + 1)?.range?.first ?: trimmed.length
                    val word = trimmed.substring(tag.range.last + 1, textEnd).trim()
                    if (word.isNotEmpty()) wordTimes.add(word to timeMs)
                }
            }

            // 有逐字时间戳时文本 = 各词拼接；否则为行级时间标签之后的整段文本
            val lyricText = if (wordTimes.isEmpty()) {
                head.substring(lineTags.last().range.last + 1).trim()
            } else {
                wordTimes.joinToString(" ") { it.first }
            }
            if (lyricText.isBlank()) return@forEach

            lineTags.forEach { tag ->
                parseTime(tag)?.let { lines.add(LrcLine(it, lyricText, wordTimes)) }
            }
        }

        // 稳定排序：相同 timeMs 保留原插入顺序（译文紧随原文）
        lines.sortBy { it.timeMs }
        return mergeTranslations(lines)
    }

    /**
     * 把时间戳相同的连续多行合并为「原文 + 译文」：首行为原文，其余（去重）作为译文。
     *
     * 普通 LRC 的「一行多时间标签」产生的时间戳各不相同，不受影响；仅当两行
     * （如原文与译文）落在同一时间戳时才发生合并。
     */
    private fun mergeTranslations(lines: List<LrcLine>): List<LrcLine> {
        if (lines.size < 2) return lines
        val merged = ArrayList<LrcLine>(lines.size)
        var index = 0
        while (index < lines.size) {
            val first = lines[index]
            val extras = mutableListOf<String>()
            var next = index + 1
            while (next < lines.size && lines[next].timeMs == first.timeMs) {
                val candidate = lines[next].text
                if (candidate != first.text && extras.none { it == candidate }) extras.add(candidate)
                next++
            }
            merged.add(
                if (extras.isEmpty()) first else first.copy(translation = extras.joinToString("\n")),
            )
            index = next
        }
        return merged
    }

    /**
     * 从匹配结果中取时间（毫秒）。
     *
     * 行级标签与 `<..>` 逐字标签用捕获组 1..3；`[[..]]` 逐字标签用捕获组 4..6
     * （此时组 1 为空串，据此切换偏移）。
     */
    private fun parseTime(match: MatchResult): Long? {
        val offset = if (match.groupValues.getOrNull(1).isNullOrEmpty()) 3 else 0
        val minutes = match.groupValues.getOrNull(offset + 1)?.toIntOrNull() ?: return null
        val seconds = match.groupValues.getOrNull(offset + 2)?.toIntOrNull() ?: return null
        val millisStr = match.groupValues.getOrNull(offset + 3)?.padEnd(3, '0')?.take(3)
            ?: return null
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
