package com.nichx.niplayer.feature.player

data class LrcLine(
    val timeMs: Long,
    val text: String,
)

object LrcParser {

    /** 匹配 [mm:ss.xx] / [mm:ss.xxx] / [mm:ss:xx] 形式的时间标签。 */
    private val TIME_TAG_REGEX = Regex("""\[(\d{2}):(\d{2})[\.:](\d{2,3})\]""")

    fun parse(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        content.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            val tags = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (tags.isEmpty()) return@forEach

            // 歌词文本取最后一个时间标签之后的内容；一行多个标签时整行按每个标签重复
            val lyricText = trimmed.substring(tags.last().range.last + 1).trim()
            // skip empty text lines to avoid visual gaps in lyrics display
            if (lyricText.isEmpty()) return@forEach

            tags.forEach { match ->
                val minutes = match.groupValues[1].toIntOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toIntOrNull() ?: return@forEach
                val millisStr = match.groupValues[3].padEnd(3, '0').take(3)
                val millis = millisStr.toIntOrNull() ?: return@forEach
                val timeMs = minutes * 60_000L + seconds * 1_000L + millis
                lines.add(LrcLine(timeMs, lyricText))
            }
        }

        // 稳定排序：相同 timeMs 保留原插入顺序，避免在排序中对同一可变列表做 indexOf 违反一致性
        lines.sortBy { it.timeMs }
        return lines
    }
}
