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

    /**
     * 回归测试（缺陷审计 #22）：`magenta` 常量原为 `"ff00ffff "`（**含尾随空格**，共 9 字符），
     * 而 `SubtitleEngine.parseStyleColor` 的长度检查要求恰好 8 字符 —— 该颜色名会被静默丢弃、
     * 回退到默认色。同义的 `fuchsia` 一直是正确的 8 字符，两者输出必须一致。
     */
    @Test
    fun `magenta 与 fuchsia 必须产出相同的 8 字符色值`() {
        val magenta = Style.getRGBValue("name", "magenta")
        val fuchsia = Style.getRGBValue("name", "fuchsia")
        assertEquals("ff00ffff", magenta)
        assertEquals("ff00ffff", fuchsia)
        assertEquals("magenta 与 fuchsia 同义，色值必须一致", fuchsia, magenta)
        assertEquals("色值长度必须为 8（RRGGBBAA）", 8, magenta.length)
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
        // 0x000000FF = AA=00 BB=00 GG=00 RR=FF，AA=00 在 ASS 里表示**不透明**
        // → RRGGBBAA = ff0000ff（原实现把 AA 原样搬到末尾，得到 ff000000 = 全透明）
        assertEquals("ff0000ff", Style.getRGBValue("decimalCodedAABBGGRR", "255"))
    }

    @Test
    fun `ampH AABBGGRR 转换`() {
        // AA=00 不透明，BB=12 GG=34 RR=56 → RRGGBBAA = 563412ff
        assertEquals("563412ff", Style.getRGBValue("&HAABBGGRR", "&H00123456"))
        // AA=ff 表示全透明 → 输出 alpha 取反为 00
        assertEquals("56341200", Style.getRGBValue("&HAABBGGRR", "&Hff123456"))
        // 省略 AA（6 位十六进制）时按 00 补 → 不透明
        assertEquals("563412ff", Style.getRGBValue("&HAABBGGRR", "&H123456"))
        // 最常见的默认主色：不透明白，绝不能被解析成透明（否则字幕直接看不见）
        assertEquals("ffffffff", Style.getRGBValue("&HAABBGGRR", "&H00FFFFFF"))
        // 消费方（parseStyleColor）只接受 6/8 位：输出长度必须为 8
        assertEquals(8, Style.getRGBValue("&HAABBGGRR", "&H00123456")!!.length)
        // 带尾随 & 与大小写前缀（&h）的写法等价
        assertEquals("563412ff", Style.getRGBValue("&HAABBGGRR", "&h00123456&"))
    }

    @Test
    fun `ampH BBGGRR 转换（SSA）`() {
        // BB=FF GG=80 RR=40 → RRGGBBAA = 4080ffff（原实现单字符切片会算错 G 通道）
        assertEquals("4080ffff", Style.getRGBValue("&HBBGGRR", "&HFF8040"))
    }

    @Test
    fun `颜色名称 cyan 与 aqua 同为 8 字符`() {
        assertEquals("00ffffff", Style.getRGBValue("name", "cyan"))
        assertEquals("00ffffff", Style.getRGBValue("name", "aqua"))
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
        source.outlineColor = "000000ff"
        source.backgroundColor = "000000ff"
        source.textAlign = "top-center"
        source.italic = true
        source.bold = true
        source.underline = true

        val copy = Style("Dst", source)
        assertEquals("Arial", copy.font)
        assertEquals("28", copy.fontSize)
        assertEquals("ffffff00", copy.color)
        assertEquals("000000ff", copy.outlineColor)
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
