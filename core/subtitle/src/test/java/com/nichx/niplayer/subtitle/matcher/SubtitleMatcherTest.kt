package com.nichx.niplayer.subtitle.matcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMatcherTest {

    // ── 扩展名识别 ────────────────────────────────────────────────

    @Test
    fun `识别四种外挂字幕扩展名`() {
        assertTrue(SubtitleMatcher.isSubtitleFile("movie.srt"))
        assertTrue(SubtitleMatcher.isSubtitleFile("movie.ass"))
        assertTrue(SubtitleMatcher.isSubtitleFile("movie.ssa"))
        assertTrue(SubtitleMatcher.isSubtitleFile("movie.vtt"))
    }

    @Test
    fun `扩展名识别不区分大小写`() {
        assertTrue(SubtitleMatcher.isSubtitleFile("MOVIE.SRT"))
        assertTrue(SubtitleMatcher.isSubtitleFile("Movie.Ass"))
    }

    @Test
    fun `视频与歌词不算字幕`() {
        assertFalse(SubtitleMatcher.isSubtitleFile("movie.mkv"))
        assertFalse(SubtitleMatcher.isSubtitleFile("movie.mp4"))
        assertFalse(SubtitleMatcher.isSubtitleFile("song.lrc"))
        assertFalse(SubtitleMatcher.isSubtitleFile("README"))
    }

    @Test
    fun `stemOf 去掉最后一段扩展名`() {
        assertEquals("movie", SubtitleMatcher.stemOf("movie.mkv"))
        assertEquals("movie.chs", SubtitleMatcher.stemOf("movie.chs.srt"))
        assertEquals("README", SubtitleMatcher.stemOf("README"))
    }

    // ── 同名优先 ─────────────────────────────────────────────────

    @Test
    fun `完全同名优先于其他字幕`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("other.srt", "movie.srt", "movie.ass"),
        )
        assertEquals("movie.ass", picked) // 两个同名（movie.srt / movie.ass），按文件名排序取 ass
    }

    @Test
    fun `同名带语言后缀命中同名等级`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("another-movie.srt", "movie.chs.srt"),
        )
        assertEquals("movie.chs.srt", picked)
    }

    @Test
    fun `存在同名字幕时不退到目录内其他字幕`() {
        // 关键回归保护：目录里另一部片子的字幕不得错配到当前视频
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("episode02.srt", "movie.chs.ass"),
        )
        assertEquals("movie.chs.ass", picked)
    }

    @Test
    fun `同名匹配不区分大小写`() {
        val picked = SubtitleMatcher.pickBest(
            "Movie.MKV",
            listOf("other.srt", "movie.chs.srt"),
        )
        assertEquals("movie.chs.srt", picked)
    }

    // ── 目录兜底 ─────────────────────────────────────────────────

    @Test
    fun `无同名字幕时使用目录内字幕`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("movie.mkv", "subtitle-a.srt", "cover.jpg"),
        )
        assertEquals("subtitle-a.srt", picked)
    }

    @Test
    fun `目录内多个非同名字幕按文件名排序`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("b-sub.srt", "a-sub.ass"),
        )
        assertEquals("a-sub.ass", picked)
    }

    // ── 语言优先级 ───────────────────────────────────────────────

    @Test
    fun `同名多语言候选按 priority 选择`() {
        val candidates = listOf("movie.cht.srt", "movie.chs.srt", "movie.eng.srt")
        assertEquals("movie.chs.srt", SubtitleMatcher.pickBest("movie.mkv", candidates, "chs,cht"))
        assertEquals("movie.cht.srt", SubtitleMatcher.pickBest("movie.mkv", candidates, "cht,chs"))
        assertEquals("movie.eng.srt", SubtitleMatcher.pickBest("movie.mkv", candidates, "eng"))
    }

    @Test
    fun `priority 未命中时回退到文件名排序`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("movie.cht.srt", "movie.chs.srt"),
            "jpn",
        )
        assertEquals("movie.chs.srt", picked)
    }

    @Test
    fun `priority 支持中文逗号与多余空白`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("movie.eng.srt", "movie.chs.srt"),
            " eng ， chs ",
        )
        assertEquals("movie.eng.srt", picked)
    }

    @Test
    fun `priority 也作用于目录兜底候选`() {
        val picked = SubtitleMatcher.pickBest(
            "movie.mkv",
            listOf("track-eng.srt", "track-chs.srt"),
            "chs,eng",
        )
        assertEquals("track-chs.srt", picked)
    }

    // ── 边界 ─────────────────────────────────────────────────────

    @Test
    fun `无可用字幕返回 null`() {
        assertNull(SubtitleMatcher.pickBest("movie.mkv", listOf("movie.mkv", "cover.jpg")))
        assertNull(SubtitleMatcher.pickBest("movie.mkv", emptyList()))
    }

    @Test
    fun `视频文件名无 stem 时返回 null`() {
        assertNull(SubtitleMatcher.pickBest(".mkv", listOf("movie.srt")))
    }

    @Test
    fun `默认无 priority 时按文件名排序`() {
        val picked = SubtitleMatcher.pickBest("movie.mkv", listOf("movie.cht.srt", "movie.chs.srt"))
        assertEquals("movie.chs.srt", picked)
    }
}
