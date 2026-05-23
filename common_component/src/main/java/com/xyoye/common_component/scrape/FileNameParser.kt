package com.xyoye.common_component.scrape

object FileNameParser {

    private val seasonPatterns = listOf(
        Regex("""[Ss](\d{1,2})"""),
        Regex("""第([一二三四五六七八九十百零\d]+)季"""),
        Regex("""[Ss]eason\s*(\d{1,2})"""),
        Regex("""季"""),
        Regex("""\s+S(\d{1,2})\b""")
    )

    private val yearPattern = Regex("""[\(\[（]?(19\d{2}|20\d{2})[\)\]）]?""")

    fun handleSeasonName(name: String, reserve: Boolean = false): String? {
        var result = name.trim()
        for (pattern in seasonPatterns) {
            result = pattern.replace(result, "")
        }
        result = result.trim()
        if (result.isEmpty()) return null
        return result
    }

    fun handleNameYear(name: String): String? {
        val match = yearPattern.find(name)
        return match?.groupValues?.get(1)
    }
}
