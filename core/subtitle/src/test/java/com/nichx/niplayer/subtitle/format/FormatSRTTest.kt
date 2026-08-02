package com.nichx.niplayer.subtitle.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class FormatSRTTest {

    private fun tempSrt(content: String): File {
        val file = File.createTempFile("test", ".srt")
        file.deleteOnExit()
        // 解析器读取文本行后须以空行结束，最后一条字幕后必须跟空行，否则触发 EOF NPE
        file.writeText(content.trim() + "\n\n", StandardCharsets.UTF_8)
        return file
    }

    @Test
    fun `解析标准 SRT 文件`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            Hello World

            2
            00:00:03,500 --> 00:00:04,250
            Second line
        """.trimIndent()
        val tto = FormatSRT().parseFile(tempSrt(srt))

        assertEquals(2, tto.captions.size)
        val first = tto.captions.firstEntry().value!!
        assertEquals(1_000L, first.start.mseconds)
        assertEquals(2_000L, first.end.mseconds)
        assertTrue(first.content.contains("Hello World"))
        assertTrue(first.content.contains("<br />"))
    }

    @Test
    fun `解析多行字幕文本`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            Line one
            Line two

            2
            00:00:03,000 --> 00:00:04,000
            Single
        """.trimIndent()
        val tto = FormatSRT().parseFile(tempSrt(srt))

        assertEquals(2, tto.captions.size)
        val first = tto.captions.firstEntry().value!!
        assertTrue(first.content.contains("Line one"))
        assertTrue(first.content.contains("Line two"))
        assertTrue(first.content.contains("<br />"))
    }

    @Test
    fun `相同开始时间的字幕自动平移 1ms`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            First

            2
            00:00:01,000 --> 00:00:03,000
            Second
        """.trimIndent()
        val tto = FormatSRT().parseFile(tempSrt(srt))

        assertEquals(2, tto.captions.size)
        val keys = tto.captions.keys.toList()
        assertEquals(listOf(1_000L, 1_001L), keys)
    }

    @Test
    fun `解析中文内容`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            你好世界
        """.trimIndent()
        val tto = FormatSRT().parseFile(tempSrt(srt))

        assertEquals(1, tto.captions.size)
        assertTrue(tto.captions.firstEntry().value!!.content.contains("你好世界"))
    }

    @Test
    fun `文件名被记录`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            Hi
        """.trimIndent()
        val file = tempSrt(srt)
        val tto = FormatSRT().parseFile(file)
        assertEquals(file.name, tto.fileName)
    }
}

class FormatASSTest {

    private fun tempAss(content: String): File {
        val file = File.createTempFile("test", ".ass")
        file.deleteOnExit()
        file.writeText(content, StandardCharsets.UTF_8)
        return file
    }

    private val header = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1280
        PlayResY: 720

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,28,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
    """.trimIndent()

    @Test
    fun `解析标准 ASS 文件`() {
        val ass = """
            $header
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello ASS
        """.trimIndent()
        val tto = FormatASS().parseFile(tempAss(ass))

        assertEquals(1, tto.captions.size)
        val caption = tto.captions.firstEntry().value!!
        assertEquals(1_000L, caption.start.mseconds)
        assertEquals(2_000L, caption.end.mseconds)
        assertTrue(caption.rawContent.contains("Hello ASS"))
    }

    @Test
    fun `解析 PlayResX 与 PlayResY`() {
        val ass = """
            $header
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello
        """.trimIndent()
        val tto = FormatASS().parseFile(tempAss(ass))

        assertEquals(1280f, tto.playResX, 0.001f)
        assertEquals(720f, tto.playResY, 0.001f)
    }

    @Test
    fun `解析样式定义`() {
        val ass = """
            $header
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello
        """.trimIndent()
        val tto = FormatASS().parseFile(tempAss(ass))

        val style = tto.styling["Default"]
        assertEquals("Arial", style!!.font)
        assertEquals("28", style.fontSize)
    }

    @Test
    fun `文本中的标签被清理进 content`() {
        val ass = """
            $header
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\b1}bold{\b0} text
        """.trimIndent()
        val tto = FormatASS().parseFile(tempAss(ass))

        val caption = tto.captions.firstEntry().value!!
        assertTrue(caption.content.contains("bold"))
        assertTrue(!caption.content.contains("{"))
    }

    @Test
    fun `多条对话按开始时间排序`() {
        val ass = """
            $header
            Dialogue: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,Later
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Earlier
        """.trimIndent()
        val tto = FormatASS().parseFile(tempAss(ass))

        assertEquals(2, tto.captions.size)
        val keys = tto.captions.keys.toList()
        assertEquals(listOf(1_000L, 5_000L), keys)
    }
}
