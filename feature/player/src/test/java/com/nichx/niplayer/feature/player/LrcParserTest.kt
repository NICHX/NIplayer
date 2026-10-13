package com.nichx.niplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `解析标准 LRC 时间标签`() {
        val lrc = """
            [00:01.50]Line one
            [00:03.20]Line two
        """.trimIndent()

        val lines = LrcParser.parse(lrc)

        assertEquals(2, lines.size)
        assertEquals(1_500L, lines[0].timeMs)
        assertEquals("Line one", lines[0].text)
        assertEquals(3_200L, lines[1].timeMs)
        assertEquals("Line two", lines[1].text)
    }

    @Test
    fun `解析毫秒级时间标签`() {
        val lrc = "[00:01.500]Precise"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(1_500L, lines[0].timeMs)
        assertEquals("Precise", lines[0].text)
    }

    @Test
    fun `冒号分隔的毫秒时间标签`() {
        val lrc = "[00:01:50]Colon"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(1_500L, lines[0].timeMs)
        assertEquals("Colon", lines[0].text)
    }

    @Test
    fun `一行多个时间标签生成多条记录`() {
        val lrc = "[00:01.00][00:02.00]Repeated"
        val lines = LrcParser.parse(lrc)

        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(2_000L, lines[1].timeMs)
        assertEquals("Repeated", lines[0].text)
        assertEquals("Repeated", lines[1].text)
    }

    @Test
    fun `元数据行被跳过`() {
        val lrc = """
            [ti:Title]
            [ar:Artist]
            [00:01.00]Song
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("Song", lines[0].text)
    }

    @Test
    fun `结果按时间戳排序`() {
        val lrc = """
            [00:05.00]Later
            [00:01.00]Earlier
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(listOf(1_000L, 5_000L), lines.map { it.timeMs })
    }

    @Test
    fun `解析包含空格的文本`() {
        val lines = LrcParser.parse("[00:01.00]Hello  world")
        assertEquals("Hello  world", lines[0].text)
    }

    @Test
    fun `空内容返回空列表`() {
        assertTrue(LrcParser.parse("").isEmpty())
    }

    @Test
    fun `无有效时间标签返回空列表`() {
        assertTrue(LrcParser.parse("just plain text").isEmpty())
    }

    @Test
    fun `解析增强 LRC 逐字时间戳`() {
        val lrc = "[00:14.20]<00:14.25>When <00:14.67>the <00:15.12>night"
        val lines = LrcParser.parse(lrc)

        assertEquals(1, lines.size)
        assertEquals(14_200L, lines[0].timeMs)
        // 纯文本 = 各词**原样**拼接（词之间不另加分隔符；英文的间隔来自原文里的空格）
        assertEquals("When the night", lines[0].text)
        // 逐字时间戳被正确解析（词文本保留其自身的尾随空格，故 "When " / "the "）
        assertEquals(3, lines[0].wordTimes.size)
        assertEquals("When " to 14_250L, lines[0].wordTimes[0])
        assertEquals("the " to 14_670L, lines[0].wordTimes[1])
        assertEquals("night" to 15_120L, lines[0].wordTimes[2])
    }

    @Test
    fun `普通 LRC 无逐字时间戳`() {
        val lrc = "[00:01.00]Plain line"
        val lines = LrcParser.parse(lrc)

        assertEquals(1, lines.size)
        assertEquals("Plain line", lines[0].text)
        assertTrue(lines[0].wordTimes.isEmpty())
    }

    @Test
    fun `解析逐字标记 LRC（nilyric）字级时间轴`() {
        val nil = """
            [00:12.34][[00:12.34]]你[[00:12.80]]好[[00:13.20]]世界
            [00:15.00]副歌
        """.trimIndent()

        val lines = LrcParser.parse(nil)

        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].timeMs)
        // 逐字来源不带分隔符，各词直接拼接（旧实现会变成「你 好 世界」）
        assertEquals("你好世界", lines[0].text)
        assertEquals(3, lines[0].wordTimes.size)
        assertEquals("你" to 12_340L, lines[0].wordTimes[0])
        assertEquals("好" to 12_800L, lines[0].wordTimes[1])
        assertEquals("世界" to 13_200L, lines[0].wordTimes[2])
        // 单字行不带逐字标记
        assertEquals("副歌", lines[1].text)
        assertTrue(lines[1].wordTimes.isEmpty())
    }

    @Test
    fun `相同时间戳的相邻行合并为原文与译文`() {
        val lrc = """
            [00:01.00]Hello
            [00:01.00]你好
            [00:03.00]World
            [00:03.00]世界
        """.trimIndent()

        val lines = LrcParser.parse(lrc)

        assertEquals(2, lines.size)
        assertEquals("Hello", lines[0].text)
        assertEquals("你好", lines[0].translation)
        assertEquals("World", lines[1].text)
        assertEquals("世界", lines[1].translation)
    }

    @Test
    fun `相同时间戳但文本相同不产生译文`() {
        val lrc = "[00:01.00]Same\n[00:01.00]Same"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(null, lines[0].translation)
    }

    @Test
    fun `合并逐行来源的译文与逐字来源的时间轴`() {
        val plain = "[00:01.00]Hello\n[00:01.00]你好"
        val word = "[00:01.00][[00:01.00]]Hel[[00:01.50]]lo"

        val lines = LrcParser.parse(plain, word)

        assertEquals(1, lines.size)
        assertEquals("Hello", lines[0].text)
        assertEquals("你好", lines[0].translation)
        assertEquals(2, lines[0].wordTimes.size)
        assertEquals("Hel" to 1_000L, lines[0].wordTimes[0])
        assertEquals("lo" to 1_500L, lines[0].wordTimes[1])
    }

    @Test
    fun `只有逐行来源时直接返回其解析结果`() {
        val plain = "[00:01.00]Hello\n[00:01.00]你好"
        val lines = LrcParser.parse(plain, null)
        assertEquals(1, lines.size)
        assertEquals("你好", lines[0].translation)
    }
}
