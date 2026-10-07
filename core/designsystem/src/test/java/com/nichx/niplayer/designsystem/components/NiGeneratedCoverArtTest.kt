package com.nichx.niplayer.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 无封面纸封面的「文件名 → 三行文字」规则回归测试。
 *
 * 这些规则决定封面上写什么、哪一行放大，一旦改动会直接影响观感，
 * 因此把几个典型形态**钉死**：带序号 + 带来源的中文名、纯中文名、
 * 英文名、带年份的标题（年份必须保留）、短横线序号、以及空输入。
 */
class NiGeneratedCoverArtTest {

    @Test
    fun `中文名 去掉开头序号 并从括号切出来源行`() {
        val lines = buildCoverLabelLines("3.仙逆 第2集 离乡（微信群：laoqu2...")

        assertEquals("仙逆 第2集", lines.prefix)
        assertEquals("离乡", lines.main)
        assertEquals("微信群：laoqu2...", lines.source)
    }

    @Test
    fun `纯中文名 只有主名 不带前缀与来源`() {
        val lines = buildCoverLabelLines("晴天.mp3")

        assertNull(lines.prefix)
        assertEquals("晴天", lines.main)
        assertNull(lines.source)
    }

    @Test
    fun `两段英文名 整体作主名 不硬拆`() {
        val lines = buildCoverLabelLines("Song Title.flac")

        assertNull(lines.prefix)
        assertEquals("Song Title", lines.main)
        assertNull(lines.source)
    }

    @Test
    fun `三段及以上 最后一段作主名`() {
        val lines = buildCoverLabelLines("Live at Budokan (Live).flac")

        assertEquals("Live at", lines.prefix)
        assertEquals("Budokan", lines.main)
        assertEquals("Live", lines.source)
    }

    @Test
    fun `年份开头的标题不被当作序号删掉`() {
        val lines = buildCoverLabelLines("1989 序曲.flac")

        assertNull(lines.prefix)
        assertEquals("1989 序曲", lines.main)
        assertNull(lines.source)
    }

    @Test
    fun `短横线分隔的序号与标题剥离`() {
        val lines = buildCoverLabelLines("01 - Song Title.mp3")

        assertNull(lines.prefix)
        assertEquals("Song Title", lines.main)
        assertNull(lines.source)
    }

    @Test
    fun `空文件名 返回空主名`() {
        val lines = buildCoverLabelLines("")

        assertNull(lines.prefix)
        assertEquals("", lines.main)
        assertNull(lines.source)
    }
}
