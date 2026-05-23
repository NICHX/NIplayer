package com.xyoye.common_component.scrape

object SeasonExtractor {

    private val seasonPatterns = listOf(
        Regex("""[Ss](\d{1,2})"""),
        Regex("""第([一二三四五六七八九十百零\d]+)季"""),
        Regex("""[Ss]eason\s*(\d{1,2})""")
    )

    private val startsWithSeasonPatterns = listOf(
        Regex("""^[Ss]\d{1,2}"""),
        Regex("""^第[一二三四五六七八九十百零\d]+季"""),
        Regex("""^[Ss]eason\s*\d{1,2}""")
    )

    fun extractSeasonNumber(folderName: String): String {
        for (pattern in seasonPatterns) {
            val match = pattern.find(folderName)
            if (match != null) {
                val value = match.groupValues[1]
                val num = ChineseNumberMapper.chineseToNumber(value)
                if (num > 0) return num.toString()
            }
        }
        return "1"
    }

    fun startsWithSeasonFormat(name: String): Boolean {
        return startsWithSeasonPatterns.any { it.containsMatchIn(name) }
    }
}
