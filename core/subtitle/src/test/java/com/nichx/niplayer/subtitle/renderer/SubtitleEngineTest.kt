package com.nichx.niplayer.subtitle.renderer

import com.nichx.niplayer.subtitle.format.FormatSRT
import com.nichx.niplayer.subtitle.info.Caption
import com.nichx.niplayer.subtitle.info.Style
import com.nichx.niplayer.subtitle.info.Time
import com.nichx.niplayer.subtitle.info.TimedTextObject
import java.io.File
import java.util.Locale
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
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", h, m, s, milli)
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
    fun `应用内嵌样式时使用文件自带颜色`() = runTest {
        val style = Style("Default").apply { color = "ff0000ff" } // RRGGBBAA：不透明红
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello", style)), "test.ass")
        engine.update(2_000L)

        // 文件自带主色必须胜出（原实现颜色串格式错误 → 被丢弃 → 永远显示用户设置色）
        assertEquals(SubtitleColor(1f, 0f, 0f, 1f), engine.renderables.value[0].stylePrimary)
    }

    @Test
    fun `关闭内嵌样式时强制使用用户颜色`() = runTest {
        val style = Style("Default").apply { color = "ff0000ff" }
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.updateStyleConfig(
            SubtitleStyleConfig(
                applyEmbeddedStyles = false,
                primaryColor = SubtitleColor(0f, 1f, 0f, 1f),
            ),
        )
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello", style)), "test.ass")
        engine.update(2_000L)

        assertEquals(SubtitleColor(0f, 1f, 0f, 1f), engine.renderables.value[0].stylePrimary)
    }

    @Test
    fun `改字号立即生效`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.srt")
        engine.update(2_000L)
        val medium = engine.renderables.value[0].styleFontSize

        // 用户在字幕样式面板改字号：必须立刻反映到渲染结果（原先字号只在引擎构造时读一次）
        engine.updateStyleConfig(SubtitleStyleConfig(textSizeFactor = 0.1f))
        val large = engine.renderables.value[0].styleFontSize

        assertTrue("字号未随配置变化（$medium → $large）", large > medium)
    }

    @Test
    fun `字号按文件声明的 PlayRes 缩放`() = runTest {
        val style = Style("Default").apply { fontSize = "40" }
        val tto = ttoOf(caption(1_000L, 5_000L, "Hello", style)).apply {
            playResX = 1280f
            playResY = 720f
        }
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f) // viewScale = min(1920/1280, 1080/720) = 1.5
        engine.load(tto, "720p.ass")
        engine.update(2_000L)

        // 40（基于 PlayResY=720 的逻辑字号）× 1.5 = 60px；
        // 若按硬编码的 288 折算会得到 150px（放大 2.5 倍，即"字体太大"）
        assertEquals(60f, engine.renderables.value[0].styleFontSize, 0.001f)
    }

    @Test
    fun `未声明 PlayRes 时按 ASS 默认 384x288 缩放`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f) // viewScale = min(5.0, 3.75) = 3.75
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.srt")
        engine.update(2_000L)

        // 无 Style 字号 → 默认字号 = textSizeFactor × 视口高度（与 PlayRes 取值无关）
        assertEquals(0.0533f * 1080f, engine.renderables.value[0].styleFontSize, 0.5f)
    }

    @Test
    fun `暂停态装载字幕后立即渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Old")), "old.srt")
        engine.update(2_000L)
        assertEquals("Old", engine.renderables.value[0].spans[0].text)

        // 暂停态：player.positionMs 是 StateFlow，其值由 500ms 轮询器驱动，
        // 而轮询器只在 Playing/Buffering 运行 —— 暂停时值不再变化，上层不会再调用 update。
        // 换字幕（或首次装载）必须自己重算一帧，否则屏幕内容一直停在旧值。
        engine.load(ttoOf(caption(1_000L, 5_000L, "New")), "new.srt")

        assertEquals(1, engine.renderables.value.size)
        assertEquals("New", engine.renderables.value[0].spans[0].text)
    }

    @Test
    fun `装载重渲染复用最近播放位置并叠加偏移`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Old")), "old.srt")
        engine.setOffsetMs(1_000L)
        engine.update(1_500L) // effective = 2500

        engine.load(ttoOf(caption(1_000L, 5_000L, "New")), "new.srt")

        // 复用的位置是原始 positionMs = 1500，再叠加偏移仍是 effective 2500 → 命中区间
        assertEquals(1, engine.renderables.value.size)
        assertEquals("New", engine.renderables.value[0].spans[0].text)
    }

    @Test
    fun `暂停态装载首条字幕后立即渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)

        // 播放中轮询器已喂过位置，但此时还没有任何字幕 → update 早退，不写渲染缓存
        engine.update(2_000L)

        // 暂停后装载该视频的**第一条**字幕：必须立即渲染一帧，
        // 不能等下一次位置更新（暂停时轮询器已停，永远不会再来）
        engine.load(ttoOf(caption(1_000L, 5_000L, "First")), "first.srt")

        assertEquals(1, engine.renderables.value.size)
        assertEquals("First", engine.renderables.value[0].spans[0].text)
    }

    @Test
    fun `视图尺寸就绪后补渲染已装载字幕`() = runTest {
        val engine = SubtitleEngine()
        // 尺寸未设置时装载 + 喂位置：update 因 viewHeightPx<=0 早退，字幕无法渲染
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.ass")
        engine.update(2_000L)
        assertTrue(engine.renderables.value.isEmpty())

        // 尺寸到齐应当自己补一帧，不需要上层再喂一次位置（暂停态不会再喂）
        engine.setViewSize(1920f, 1080f)
        assertEquals(1, engine.renderables.value.size)
    }

    @Test
    fun `从未收到播放位置时装载不重渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)

        // 一次 update 都没发生过 —— 不知道播放位置，装载时无从重算（属预期边界）
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "test.srt")
        assertTrue(engine.renderables.value.isEmpty())

        // 首个位置到来后正常渲染
        engine.update(2_000L)
        assertEquals(1, engine.renderables.value.size)
    }

    @Test
    fun `字幕名称显示文件名`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "Hello")), "subs.ass")
        assertEquals("subs.ass", engine.subtitleName.value)
    }

    /**
     * 端到端回归：真实 .srt 文件 → [com.nichx.niplayer.subtitle.format.FormatSRT] → 引擎。
     *
     * 本用例存在的理由：解析器写的是 [Caption.content]，而渲染链路读的是 [Caption.rawContent]。
     * 上面的用例都手工设了 rawContent，FormatSRTTest 又只断言 content —— 两者之间这段
     * 「格式写出的字段与渲染读入的字段是否对得上」无人覆盖，于是 SRT 字幕全部静默渲染为空
     * （captions 非空、无异常、无提示，屏幕上什么都没有）。
     */
    @Test
    fun `SRT 文件解析后经引擎渲染出文本`() = runTest {
        val file = File.createTempFile("subtitle_test", ".srt")
        file.writeText(
            """
            1
            00:00:01,000 --> 00:00:05,000
            Hello World

            2
            00:00:06,000 --> 00:00:08,000
            第二行
            """.trimIndent() + "\n\n",
            Charsets.UTF_8,
        )
        try {
            val engine = SubtitleEngine()
            engine.setViewSize(1920f, 1080f)
            engine.load(FormatSRT().parseFile(file), file.name)

            engine.update(2_000L)
            assertEquals(1, engine.renderables.value.size)
            assertEquals("Hello World", engine.renderables.value[0].spans[0].text)

            engine.update(7_000L)
            assertEquals(1, engine.renderables.value.size)
            assertEquals("第二行", engine.renderables.value[0].spans[0].text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `纯矢量绘制行也能渲染`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "{\\p1}m 0 0 l 100 0 100 100")), "draw.ass")
        engine.update(2_000L)

        // 没有文本 span 的纯绘制行必须照常进入渲染列表（原先以 spans 非空为条件会被丢弃）
        val caption = engine.renderables.value.single()
        assertTrue(caption.spans.isEmpty())
        assertEquals(1, caption.drawings.size)
        val move = caption.drawings[0].path.first() as SubtitlePathOp.MoveTo
        assertEquals(0f, move.x, 0.001f)
    }

    @Test
    fun `clip 传递到渲染数据`() = runTest {
        val engine = SubtitleEngine()
        engine.setViewSize(1920f, 1080f)
        engine.load(ttoOf(caption(1_000L, 5_000L, "{\\clip(192,144,384,288)}clipped")), "clip.ass")
        engine.update(2_000L)

        val clip = engine.renderables.value.single().clip
        assertTrue("clip 应传递到渲染层：$clip", clip is SubtitleClip.Rect)
        val rect = (clip ?: error("clip 应传递到渲染层")) as SubtitleClip.Rect
        assertEquals(0.5f, rect.left, 0.001f)
    }

    @Test
    fun `SRT 的斜体标记渲染为斜体 span`() = runTest {
        val file = File.createTempFile("subtitle_test", ".srt")
        file.writeText(
            "1\n00:00:01,000 --> 00:00:05,000\n<i>Hello</i>\n\n",
            Charsets.UTF_8,
        )
        try {
            val engine = SubtitleEngine()
            engine.setViewSize(1920f, 1080f)
            engine.load(FormatSRT().parseFile(file), file.name)
            engine.update(2_000L)

            val spans = engine.renderables.value[0].spans
            assertEquals(1, spans.size)
            assertEquals("Hello", spans[0].text)
            assertEquals(true, spans[0].italic)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `SRT 多行文本解析为多行 span`() = runTest {
        val file = File.createTempFile("subtitle_test", ".srt")
        file.writeText(
            """
            1
            00:00:01,000 --> 00:00:05,000
            Line one
            Line two
            """.trimIndent() + "\n\n",
            Charsets.UTF_8,
        )
        try {
            val engine = SubtitleEngine()
            engine.setViewSize(1920f, 1080f)
            engine.load(FormatSRT().parseFile(file), file.name)

            engine.update(2_000L)
            // 两个文本 span + 一个换行 span：渲染层据此拆行，不能把 <br /> 当字面文本画出来
            val texts = engine.renderables.value[0].spans.map { it.text }
            assertEquals(listOf("Line one", "\n", "Line two"), texts)
        } finally {
            file.delete()
        }
    }
}
