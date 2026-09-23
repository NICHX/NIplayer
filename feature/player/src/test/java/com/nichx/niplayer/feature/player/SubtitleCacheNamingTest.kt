package com.nichx.niplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCacheNamingTest {

    @Test
    fun `缓存名带哈希前缀并保留原文件名（用于展示）`() {
        val cache = SubtitleCacheNaming.fileName("7:/movies/a.mkv", "电影.chs.srt")

        assertTrue("应保留原文件名：$cache", cache.endsWith("_电影.chs.srt"))
        assertEquals("电影.chs.srt", SubtitleCacheNaming.displayName(cache, LEGACY))
    }

    /**
     * 回归保护：恢复播放时字幕菜单/提示显示的是展示名。
     * 若展示名取成缓存文件名，界面里就会是一串数字（用户看不懂那是什么）。
     */
    @Test
    fun `展示名不含哈希数字`() {
        val cache = SubtitleCacheNaming.fileName("123:/tv/ep01.mkv", "ep01.chs.srt")

        val display = SubtitleCacheNaming.displayName(cache, LEGACY)
        assertEquals("ep01.chs.srt", display)
        assertTrue("展示名不应含哈希前缀：$display", !display.contains("_"))
    }

    @Test
    fun `不同视频的同名字幕互不覆盖`() {
        val a = SubtitleCacheNaming.fileName("1:/movies/a.mkv", "chs.srt")
        val b = SubtitleCacheNaming.fileName("2:/movies/b.mkv", "chs.srt")

        assertNotEquals(a, b)
        // 两者展示名相同（都是用户看到的 chs.srt），但落盘路径不同
        assertEquals(
            SubtitleCacheNaming.displayName(a, LEGACY),
            SubtitleCacheNaming.displayName(b, LEGACY),
        )
    }

    @Test
    fun `同一视频换字幕覆盖同一哈希前缀下的同名文件`() {
        val first = SubtitleCacheNaming.fileName("9:/movies/a.mkv", "chs.srt")
        val again = SubtitleCacheNaming.fileName("9:/movies/a.mkv", "chs.srt")

        assertEquals(first, again)
    }

    @Test
    fun `原文件名里的非法字符被替换`() {
        val cache = SubtitleCacheNaming.fileName("1:/a.mkv", "dir/we:ird*name?.srt")

        assertTrue("路径分隔符与保留字符都要替换：$cache", cache.endsWith("_we_ird_name_.srt"))
    }

    @Test
    fun `超长原文件名被截断`() {
        val longName = "字".repeat(200) + ".srt"
        val cache = SubtitleCacheNaming.fileName("1:/a.mkv", longName)

        // 文件名（不含哈希前缀）不超过上限，避免超出文件系统 255 字节限制
        val safePart = cache.substringAfter('_')
        assertTrue("截断后长度应受限：${safePart.length}", safePart.length <= 80)
    }

    @Test
    fun `原文件名含下划线时可完整还原`() {
        val cache = SubtitleCacheNaming.fileName("1:/a.mkv", "电影_01.chs.srt")

        // 只按首个下划线切分，原文件名自身的下划线必须保留
        assertEquals("电影_01.chs.srt", SubtitleCacheNaming.displayName(cache, LEGACY))
    }

    @Test
    fun `旧版纯哈希缓存名退化为泛化标签`() {
        // 旧版本缓存文件名为 `-1234567890.srt`，原文件名无从还原
        assertEquals(LEGACY, SubtitleCacheNaming.displayName("-1234567890.srt", LEGACY))
        assertEquals(LEGACY, SubtitleCacheNaming.displayName("1234567890.ass", LEGACY))
    }

    @Test
    fun `原文件名为空时使用兜底名`() {
        val cache = SubtitleCacheNaming.fileName("1:/a.mkv", "")

        assertTrue("应有兜底名：$cache", cache.endsWith("_subtitle"))
    }

    private companion object {
        const val LEGACY = "已保存的字幕"
    }
}
