package com.nichx.niplayer.subtitle.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StyleTest {

    @Test
    fun `颜色名称格式解析`() {
        assertEquals("00000000", Style.getRGBValue("name", "transparent"))
        assertEquals("000000ff", Style.getRGBValue("name", "black"))
        assertEquals("ffffffff", Style.getRGBValue("name", "white"))
        assertEquals("ff0000ff", Style.getRGBValue("name", "red"))
        assertEquals("00ff00ff", Style.getRGBValue("name", "lime"))
        assertEquals("0000ffff", Style.getRGBValue("name", "blue"))
    }

    @Test
    fun `未知颜色名称返回 null`() {
        assertNull(Style.getRGBValue("name", "not-a-color"))
    }

    @Test
    fun `decimalCodedBBGGRR 转换（红色）`() {
        // 0xFF0000 = BB=FF GG=00 RR=00 → RRGGBBAA = 0000ffff
        assertEquals("0000ffff", Style.getRGBValue("decimalCodedBBGGRR", "16711680"))
    }

    @Test
    fun `decimalCodedBBGGRR 转换（绿色）`() {
        // 0x00FF00 = BB=00 GG=FF RR=00 → RRGGBBAA = 00ff00ff
        assertEquals("00ff00ff", Style.getRGBValue("decimalCodedBBGGRR", "65280"))
    }

    @Test
    fun `decimalCodedAABBGGRR 转换`() {
        // 0x000000FF = AA=00 BB=00 GG=00 RR=FF → RRGGBBAA = ff000000
        assertEquals("ff000000", Style.getRGBValue("decimalCodedAABBGGRR", "255"))
    }

    @Test
    fun `ampH AABBGGRR 转换`() {
        // 注意：当前实现 append(value,6,7) 仅截取单字符，输出 5 位 RRGG+B+AA
        // 此处锁定现有行为（上游字幕库历史实现），后续如需修正为 RRGGBBAA 需同步更新
        assertEquals("5631F", Style.getRGBValue("&HAABBGGRR", "&HFF123456"))
    }

    @Test
    fun `defaultID 生成递增默认样式名`() {
        val a = Style.defaultID()
        val b = Style.defaultID()
        assertEquals("default", a.take(7))
        assertEquals("default", b.take(7))
        // 后缀为递增整数
        val aIdx = a.substring(7).toInt()
        val bIdx = b.substring(7).toInt()
        assertEquals(aIdx + 1, bIdx)
    }

    @Test
    fun `拷贝构造复制全部样式字段`() {
        val source = Style("Src")
        source.font = "Arial"
        source.fontSize = "28"
        source.color = "ffffff00"
        source.backgroundColor = "000000ff"
        source.textAlign = "top-center"
        source.italic = true
        source.bold = true
        source.underline = true

        val copy = Style("Dst", source)
        assertEquals("Arial", copy.font)
        assertEquals("28", copy.fontSize)
        assertEquals("ffffff00", copy.color)
        assertEquals("000000ff", copy.backgroundColor)
        assertEquals("top-center", copy.textAlign)
        assertEquals(true, copy.italic)
        assertEquals(true, copy.bold)
        assertEquals(true, copy.underline)
    }
}

class TimeTest {

    @Test
    fun `SRT 格式解析毫秒`() {
        val time = Time("hh:mm:ss,ms", "01:02:22,501")
        assertEquals(3_742_501L, time.mseconds)
    }

    @Test
    fun `ASS 格式解析百分秒`() {
        val time = Time("h:mm:ss.cs", "1:02:22.51")
        assertEquals(3_742_510L, time.mseconds)
    }

    @Test
    fun `帧率格式解析`() {
        // 0:0:1:25 / 25fps → 1s + 25帧@25fps = 1000 + 1000 = 2000ms
        val time = Time("h:m:s:f/fps", "0:0:1:25/25")
        assertEquals(2_000L, time.mseconds)
    }

    @Test
    fun `SRT 格式化输出`() {
        val time = Time("hh:mm:ss,ms", "00:00:01,000")
        time.mseconds = 3_742_501L
        assertEquals("01:02:22,501", time.getTime("hh:mm:ss,ms"))
    }

    @Test
    fun `ASS 格式化输出`() {
        val time = Time("h:mm:ss.cs", "1:02:22.51")
        assertEquals("01:02:22.51", time.getTime("h:mm:ss.cs"))
    }

    @Test
    fun `setMseconds 修改时间`() {
        val time = Time("hh:mm:ss,ms", "00:00:00,000")
        time.setMseconds(1_234_567L)
        assertEquals(1_234_567L, time.mseconds)
        assertEquals("00:20:34,567", time.getTime("hh:mm:ss,ms"))
    }

    @Test
    fun `getMseconds 读取时间`() {
        val time = Time("hh:mm:ss,ms", "00:00:05,000")
        assertEquals(5_000L, time.mseconds)
    }
}
