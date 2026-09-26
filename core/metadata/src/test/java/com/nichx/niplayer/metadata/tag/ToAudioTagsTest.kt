package com.nichx.niplayer.metadata.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签转换纯函数测试。
 *
 * `toAudioTags` 刻意与 MediaMetadataRetriever 解耦，正是为了能在这里
 * 用纯 JVM 覆盖各种脏输入 —— 系统对缺失标签的返回值并不规整（null / 空串 / 空白 /
 * `[Unknown Album]` 这类占位符都会出现）。
 */
class ToAudioTagsTest {

    @Test
    fun `全空输入得到空标签`() {
        val tags = toAudioTags(RawTagValues())

        assertNull(tags.title)
        assertNull(tags.artist)
        assertNull(tags.album)
        assertNull(tags.albumArtist)
        assertNull(tags.trackNo)
        assertNull(tags.year)
        assertNull(tags.embeddedLyrics)
        assertFalse(tags.hasEmbeddedPicture)
        assertFalse(tags.isUsable)
    }

    @Test
    fun `空白字符串按缺失处理`() {
        val tags = toAudioTags(RawTagValues(title = "   ", artist = "\t", album = ""))

        assertNull(tags.title)
        assertNull(tags.artist)
        assertNull(tags.album)
    }

    @Test
    fun `首尾空白被去除`() {
        val tags = toAudioTags(RawTagValues(title = "  稻香 ", artist = " 周杰伦"))

        assertEquals("稻香", tags.title)
        assertEquals("周杰伦", tags.artist)
    }

    @Test
    fun `占位符按缺失处理`() {
        val tags = toAudioTags(
            RawTagValues(
                title = "<unknown>",
                artist = "[Unknown Artist]",
                album = "[Unknown Album]",
            ),
        )

        assertNull(tags.title)
        assertNull(tags.artist)
        assertNull(tags.album)
    }

    @Test
    fun `音轨号取斜杠前的部分`() {
        assertEquals(3, toAudioTags(RawTagValues(trackNo = "3/12")).trackNo)
        assertEquals(7, toAudioTags(RawTagValues(trackNo = "7")).trackNo)
        assertEquals(5, toAudioTags(RawTagValues(trackNo = " 5 ")).trackNo)
    }

    @Test
    fun `非法音轨号与零转 null`() {
        assertNull(toAudioTags(RawTagValues(trackNo = "abc")).trackNo)
        assertNull(toAudioTags(RawTagValues(trackNo = "0")).trackNo)
        assertNull(toAudioTags(RawTagValues(trackNo = "-5")).trackNo)
        assertNull(toAudioTags(RawTagValues(trackNo = "")).trackNo)
    }

    @Test
    fun `年份支持完整日期并做范围检查`() {
        assertEquals(2023, toAudioTags(RawTagValues(year = "2023")).year)
        assertEquals(1999, toAudioTags(RawTagValues(year = "1999-05-01")).year)
        assertNull(toAudioTags(RawTagValues(year = "42")).year)
        assertNull(toAudioTags(RawTagValues(year = "n/a")).year)
    }

    @Test
    fun `歌词为空时转 null`() {
        assertNull(toAudioTags(RawTagValues(lyrics = "   ")).embeddedLyrics)
        assertEquals(
            "[00:01.00]词",
            toAudioTags(RawTagValues(lyrics = "[00:01.00]词")).embeddedLyrics,
        )
    }

    @Test
    fun `内嵌封面标记原样透传`() {
        assertTrue(toAudioTags(RawTagValues(hasEmbeddedPicture = true)).hasEmbeddedPicture)
        assertFalse(toAudioTags(RawTagValues(hasEmbeddedPicture = false)).hasEmbeddedPicture)
    }

    @Test
    fun `完整标签被完整保留且判定为可用`() {
        val tags = toAudioTags(
            RawTagValues(
                title = "稻香",
                artist = "周杰伦",
                album = "魔杰座",
                albumArtist = "周杰伦",
                trackNo = "2/11",
                year = "2008",
            ),
        )

        assertEquals("稻香", tags.title)
        assertEquals("周杰伦", tags.artist)
        assertEquals("魔杰座", tags.album)
        assertEquals("周杰伦", tags.albumArtist)
        assertEquals(2, tags.trackNo)
        assertEquals(2008, tags.year)
        assertTrue(tags.isUsable)
    }

    @Test
    fun `只有歌名没有歌手时判定为不可用`() {
        // isUsable 要求 title 与 artist 同时存在：半截标签不如回退文件名解析
        assertFalse(toAudioTags(RawTagValues(title = "稻香")).isUsable)
        assertFalse(toAudioTags(RawTagValues(artist = "周杰伦")).isUsable)
    }
}
