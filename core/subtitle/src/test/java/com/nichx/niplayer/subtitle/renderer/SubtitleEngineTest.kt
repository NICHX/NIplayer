package com.nichx.niplayer.subtitle.renderer

import com.nichx.niplayer.subtitle.info.Caption
import com.nichx.niplayer.subtitle.info.Style
import com.nichx.niplayer.subtitle.info.Time
import com.nichx.niplayer.subtitle.info.TimedTextObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleEngineTest {

    private fun caption(startMs: Long, endMs: Long, raw: String, style: Style? = null): Caption {
        val caption = Caption()
        caption.style = style ?: Style("Default")
        caption.start = Time("hh:mm:ss,ms", formatMs(startMs))
        caption.end = Time("hh:mm:ss,ms", formatMs(endMs))
        caption.rawContent = raw
        return caption
    }

    private fun formatMs(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1_000) % 60
        val milli = ms % 1_000
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    private fun ttoOf(vararg captions: Caption): TimedTextObject {
        val tto = TimedTextObject()
        captions.forEach { caption ->
            val key = caption.start.mseconds
            var actual = key
            while (tto.captions.containsKey(actual)) actual++
            tto.captions[actual] = caption
        }
        return tto
    }

    @Test
    fun `加载后加载时间区间内返回对应字幕`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")

        engine.update(2_000L)
        assertEquals(1, engine.renderables.value.size)
        assertEquals("Hello", engine.renderables.value[0].spans[0].text)
    }

    @Test
    fun `时间区间外返回空列表`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")

        engine.update(500L)
        assertTrue(engine.renderables.value.isEmpty())

        engine.update(5_001L)
        assertTrue(engine.renderables.value.isEmpty())
    }

    @Test
    fun `同开始时间的多条字幕全部渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(
            ttoOf(
                caption(1_000L, 5_000L, "Line A"),
                caption(1_000L, 4_000L, "Line B"),
            ),
            "test.ass",
        )

        engine.update(3_000L)
        assertEquals(2, engine.renderables.value.size)
    }

    @Test
    fun `重叠字幕在重叠区间同时显示`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(
            ttoOf(
                caption(1_000L, 10_000L, "Long"),
                caption(5_000L, 8_000L, "Short"),
            ),
            "test.ass",
        )

        // t=7000 两条都在显示区间
        engine.update(7_000L)
        assertEquals(2, engine.renderables.value.size)

        // t=9000 只有 Long 显示
        engine.update(9_000L)
        assertEquals(1, engine.renderables.value.size)
        assertEquals("Long", engine.renderables.value[0].spans[0].text)
    }

    @Test
    fun `淡入淡出动画计算 alpha`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "{\\fad(1000,1000)}Fade")), "test.ass")

        // 起始时刻 alpha=0（刚进入）
        engine.update(1_000L)
        assertEquals(0f, engine.renderables.value[0].alpha, 0.001f)

        // 中间时刻 alpha=1
        engine.update(3_000L)
        assertEquals(1f, engine.renderables.value[0].alpha, 0.001f)

        // 接近结束 alpha<1
        engine.update(4_500L)
        assertTrue(engine.renderables.value[0].alpha < 1f)
        assertTrue(engine.renderables.value[0].alpha > 0f)
    }

    @Test
    fun `移动动画计算位置插值`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        // 默认 PlayRes 384x288，\move 坐标按此归一化 → (0,0)→(1,1)
        engine.load(ttoOf(caption(1_000L, 5_000L, "{\\move(0,0,384,288,0,1000)}Move")), "test.ass")

        // 移动起点
        engine.update(1_000L)
        assertEquals(0f, engine.renderables.value[0].position!!.first, 0.001f)

        // 移动中点 0.5
        engine.update(1_500L)
        assertEquals(0.5f, engine.renderables.value[0].position!!.first, 0.001f)

        // 移动终点
        engine.update(2_000L)
        assertEquals(1f, engine.renderables.value[0].position!!.first, 0.001f)
    }

    @Test
    fun `正偏移让字幕提前显示`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")

        // 实现行为：effectiveMs = positionMs + offset，正偏移使字幕更早进入显示区间
        engine.setOffsetMs(500L)
        engine.update(400L)
        assertTrue(engine.renderables.value.isEmpty())

        engine.update(600L)
        assertEquals(1, engine.renderables.value.size)
    }

    @Test
    fun `负偏移让字幕延后显示`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")

        engine.setOffsetMs(-500L)
        engine.update(1_400L)
        assertTrue(engine.renderables.value.isEmpty())

        engine.update(1_600L)
        assertEquals(1, engine.renderables.value.size)
    }

    @Test
    fun `视图尺寸未设置时不渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")

        engine.update(2_000L)
        assertTrue(engine.renderables.value.isEmpty())

        engine.setViewSize(1920f, 1080f)
        engine.update(2_000L)
        assertEquals(1, engine.renderables.value.size)
    }

    @Test
    fun `清空后重置状态`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")
        engine.setOffsetMs(300L)
        engine.update(2_000L)

        engine.clear()

        assertNull(engine.subtitleName.value)
        assertTrue(engine.renderables.value.isEmpty())
        assertEquals(0L, engine.offsetMs.value)
        engine.update(2_000L)
        assertTrue(engine.renderables.value.isEmpty())
    }

    @Test
    fun `更新样式配置后生效并立即重渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")
        engine.update(2_000L)

        val config = SubtitleStyleConfig(primaryColor = SubtitleColor(1f, 0f, 0f, 1f))
        engine.updateStyleConfig(config)

        assertEquals(SubtitleColor(1f, 0f, 0f, 1f), engine.renderables.value[0].stylePrimary)
    }

    @Test
    fun `字幕名称显示文件名`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "subs.ass")
        assertEquals("subs.ass", engine.subtitleName.value)
    }
}
