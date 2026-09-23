package com.nichx.niplayer.subtitle.renderer

import com.nichx.niplayer.subtitle.info.Caption
import com.nichx.niplayer.subtitle.info.Style
import com.nichx.niplayer.subtitle.info.Time
import com.nichx.niplayer.subtitle.info.TimedTextObject
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssOverrideParserTest {

    private fun caption(raw: String, startMs: Long = 1000L, endMs: Long = 5000L): Caption {
        val caption = Caption()
        caption.style = Style("Default")
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

    private val tto = TimedTextObject()

    @Test
    fun `纯文本解析为单 span`() {
        val parsed = AssOverrideParser.parse(caption("hello world"), tto)
        assertEquals(1, parsed.rawSpans.size)
        assertEquals("hello world", parsed.rawSpans[0].text)
        assertEquals(SubtitleAlign.BOTTOM_CENTER, parsed.align)
        assertNull(parsed.pos)
    }

    @Test
    fun `粗体 tag 解析`() {
        val parsed = AssOverrideParser.parse(caption("{\\b1}bold"), tto)
        assertEquals(true, parsed.rawSpans[0].bold)
    }

    @Test
    fun `斜体关闭 tag 解析`() {
        val parsed = AssOverrideParser.parse(caption("{\\i0}normal"), tto)
        assertEquals(false, parsed.rawSpans[0].italic)
    }

    /**
     * 行内样式切换必须按「标签之前 / 之后」拆成两个 span。
     *
     * 原实现只在换行与行尾 flush 文本缓冲，标签切样式时缓冲里已累积的文本会被**新样式**一并带走
     *（`{\i1}斜体{\i0}` 整段都变成关闭斜体之后的样式），行内样式等于失效。
     */
    @Test
    fun `行内样式切换拆成多个 span`() {
        val parsed = AssOverrideParser.parse(caption("{\\i1}italic{\\i0} normal"), tto)

        assertEquals(2, parsed.rawSpans.size)
        assertEquals("italic", parsed.rawSpans[0].text)
        assertEquals(true, parsed.rawSpans[0].italic)
        assertEquals(" normal", parsed.rawSpans[1].text)
        assertEquals(false, parsed.rawSpans[1].italic)
    }

    @Test
    fun `行内颜色切换只作用于其后文本`() {
        // {\c&H0000FF&} 是 AABBGGRR → 红（RR=FF）
        val parsed = AssOverrideParser.parse(caption("plain{\\c&H0000FF&}red"), tto)

        assertEquals(2, parsed.rawSpans.size)
        assertEquals(null, parsed.rawSpans[0].primaryColor)
        val red = parsed.rawSpans[1].primaryColor!!
        assertEquals(1f, red.r, 0.001f)
        assertEquals(0f, red.g, 0.001f)
    }

    @Test
    fun `字体大小 tag 解析`() {
        val parsed = AssOverrideParser.parse(caption("{\\fs24}size24"), tto)
        assertEquals(24f, parsed.rawSpans[0].fontSize!!, 0.001f)
    }

    @Test
    fun `颜色 tag 解析为 AABBGGRR`() {
        val parsed = AssOverrideParser.parse(caption("{\\c&H0000FF&}red"), tto)
        val color = parsed.rawSpans[0].primaryColor
        assertEquals(1f, color!!.r, 0.001f)
        assertEquals(0f, color.g, 0.001f)
        assertEquals(0f, color.b, 0.001f)
        assertEquals(1f, color.a, 0.001f)
    }

    @Test
    fun `定位 tag 按 PlayRes 归一化`() {
        val parsed = AssOverrideParser.parse(caption("{\\pos(192,144)}centered"), tto)
        assertEquals(0.5f, parsed.pos!!.first, 0.001f)
        assertEquals(0.5f, parsed.pos!!.second, 0.001f)
    }

    @Test
    fun `淡入淡出 tag 解析`() {
        val parsed = AssOverrideParser.parse(caption("{\\fad(200,300)}fade"), tto)
        assertEquals(200L, parsed.fade!!.inMs)
        assertEquals(300L, parsed.fade!!.outMs)
    }

    @Test
    fun `移动 tag 解析并归一化坐标`() {
        val parsed = AssOverrideParser.parse(caption("{\\move(0,0,384,288,0,1000)}move"), tto)
        val move = parsed.move!!
        assertEquals(0f, move.x1, 0.001f)
        assertEquals(0f, move.y1, 0.001f)
        assertEquals(1f, move.x2, 0.001f)
        assertEquals(1f, move.y2, 0.001f)
        assertEquals(0L, move.t1)
        assertEquals(1000L, move.t2)
    }

    @Test
    fun `变换 tag 解析时间参数与目标字号`() {
        val parsed = AssOverrideParser.parse(caption("{\\t(500,1500,\\fs40)}anim"), tto)
        assertEquals(1, parsed.transforms.size)
        val transform = parsed.transforms[0]
        assertEquals(500L, transform.t1)
        assertEquals(1500L, transform.t2)
        assertEquals(40f, transform.targetFontSize!!, 0.001f)
    }

    @Test
    fun `对齐覆盖 tag 解析`() {
        val parsed = AssOverrideParser.parse(caption("{\\a8}top"), tto)
        assertEquals(SubtitleAlign.TOP_CENTER, parsed.align)
    }

    @Test
    fun `硬换行拆分为独立换行 span`() {
        // \N 须在 {} 外才触发换行
        val parsed = AssOverrideParser.parse(caption("line1\\Nline2"), tto)
        assertEquals(3, parsed.rawSpans.size)
        assertEquals("line1", parsed.rawSpans[0].text)
        assertEquals(AssOverrideParser.NEWLINE, parsed.rawSpans[1].text)
        assertEquals("line2", parsed.rawSpans[2].text)
    }

    @Test
    fun `旋转 tag 解析`() {
        val parsed = AssOverrideParser.parse(caption("{\\frz45}rotated"), tto)
        assertEquals(45f, parsed.rotationZ, 0.001f)
    }

    @Test
    fun `未闭合大括号按普通文本处理`() {
        val parsed = AssOverrideParser.parse(caption("text {unclosed"), tto)
        assertEquals("text {unclosed", parsed.rawSpans[0].text)
    }

    @Test
    fun `p 绘制模式不再把坐标当文本`() {
        // 无 \p0 时由行尾结束绘制模式
        val parsed = AssOverrideParser.parse(caption("{\\p1}m 0 0 l 100 0 100 100"), tto)

        assertTrue("绘制命令不得进入文本 span：${parsed.rawSpans}", parsed.rawSpans.isEmpty())
        assertEquals(1, parsed.drawings.size)

        val ops = parsed.drawings[0].path
        // 坐标系与 \pos 一致：脚本坐标 / PlayRes（测试 TTO 为默认 384x288）
        val move = ops[0] as SubtitlePathOp.MoveTo
        assertEquals(0f, move.x, 0.001f)
        assertEquals(0f, move.y, 0.001f)
        val line = ops[1] as SubtitlePathOp.LineTo
        assertEquals(100f / 384f, line.x, 0.001f)
        assertEquals(0f, line.y, 0.001f)
    }

    @Test
    fun `p 的指数决定坐标缩放`() {
        // \p2 → 坐标缩放 1/2：l 384 0 → 192/384 = 0.5
        val parsed = AssOverrideParser.parse(caption("{\\p2}m 0 0 l 384 0"), tto)
        val line = parsed.drawings[0].path[1] as SubtitlePathOp.LineTo
        assertEquals(0.5f, line.x, 0.001f)
    }

    @Test
    fun `p0 退出绘制模式后恢复文本`() {
        val parsed = AssOverrideParser.parse(caption("{\\p1}m 0 0 l 10 0{\\p0}text"), tto)

        assertEquals(1, parsed.drawings.size)
        assertEquals(1, parsed.rawSpans.size)
        assertEquals("text", parsed.rawSpans[0].text)
    }

    @Test
    fun `矩形 clip 解析为归一化区域`() {
        val parsed = AssOverrideParser.parse(caption("{\\clip(192,144,384,288)}clipped"), tto)

        val clip = parsed.clip
        assertTrue("clip 应被解析为矩形：$clip", clip is SubtitleClip.Rect)
        val rect = (clip ?: error("clip 应被解析为矩形")) as SubtitleClip.Rect
        assertEquals(0.5f, rect.left, 0.001f)
        assertEquals(0.5f, rect.top, 0.001f)
        assertEquals(1f, rect.right, 0.001f)
        assertEquals(1f, rect.bottom, 0.001f)
        assertEquals(false, parsed.clipInverted)
    }

    @Test
    fun `矢量 clip 与 iclip 逆裁剪`() {
        val vector = AssOverrideParser.parse(caption("{\\clip(m 0 0 l 384 0 384 288)}x"), tto)
        assertTrue("矢量 clip 应被识别：${vector.clip}", vector.clip is SubtitleClip.Vector)

        val inverted = AssOverrideParser.parse(caption("{\\iclip(0,0,192,144)}x"), tto)
        assertEquals(true, inverted.clipInverted)
        val rect = (inverted.clip ?: error("iclip 应被解析为矩形")) as SubtitleClip.Rect
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0.5f, rect.right, 0.001f)
    }

    @Test
    fun `时间戳从 caption 读取`() {
        val parsed = AssOverrideParser.parse(caption("hello"), tto)
        assertEquals(1000L, parsed.startMs)
        assertEquals(5000L, parsed.endMs)
    }

    @Test
    fun `样式覆盖 tag 后恢复`() {
        // 行内切换样式必须按位置分段：前段用 tag 之前的样式，后段用 tag 之后的样式。
        //（原实现把整段合并成一个 span 并取"最终覆盖值"，等于行内样式失效 —— 已修正）
        val raw = "{\\b1}bold{\\b0}normal"
        val parsed = AssOverrideParser.parse(caption(raw), tto)
        assertEquals(2, parsed.rawSpans.size)
        assertEquals("bold", parsed.rawSpans[0].text)
        assertEquals(true, parsed.rawSpans[0].bold)
        assertEquals("normal", parsed.rawSpans[1].text)
        assertEquals(false, parsed.rawSpans[1].bold)
    }

    @Test
    fun `换行分隔后样式分别生效`() {
        val raw = "{\\b1}bold\\N{\\b0}normal"
        val parsed = AssOverrideParser.parse(caption(raw), tto)
        assertEquals(3, parsed.rawSpans.size)
        assertEquals(true, parsed.rawSpans[0].bold)
        assertEquals(AssOverrideParser.NEWLINE, parsed.rawSpans[1].text)
        assertEquals(false, parsed.rawSpans[2].bold)
    }

    @Test
    fun `未知 tag 被忽略`() {
        val parsed = AssOverrideParser.parse(caption("{\\unknown}text"), tto)
        assertEquals("text", parsed.rawSpans[0].text)
    }

    @Test
    fun `空文本返回空 span 列表`() {
        val parsed = AssOverrideParser.parse(caption(""), tto)
        assertTrue(parsed.rawSpans.isEmpty())
    }
}
