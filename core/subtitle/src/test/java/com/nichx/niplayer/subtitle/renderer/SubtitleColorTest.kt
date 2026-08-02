package com.nichx.niplayer.subtitle.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleColorTest {

    @Test
    fun `fromAss 解析 AABBGGRR 全不透明白色`() {
        // ASS alpha 00 = 不透明，BBGGRR = FFFFFF = 白色
        val color = SubtitleColor.fromAss("&H00FFFFFF")
        assertEquals(1f, color.r, 0.001f)
        assertEquals(1f, color.g, 0.001f)
        assertEquals(1f, color.b, 0.001f)
        assertEquals(1f, color.a, 0.001f)
    }

    @Test
    fun `fromAss 解析无前缀字符串`() {
        val color = SubtitleColor.fromAss("00FFFFFF")
        assertEquals(1f, color.a, 0.001f)
        assertEquals(1f, color.r, 0.001f)
    }

    @Test
    fun `fromAss 解析全透明黑色`() {
        // ASS alpha FF = 全透明
        val color = SubtitleColor.fromAss("FF000000")
        assertEquals(0f, color.a, 0.001f)
        assertEquals(0f, color.r, 0.001f)
    }

    @Test
    fun `fromAss 解析半透明红色`() {
        // ASS alpha 80 (128) → a = 1 - 128/255 ≈ 0.498；BBGGRR = 0000FF → R 通道满
        val color = SubtitleColor.fromAss("800000FF")
        assertEquals(0.498f, color.a, 0.001f)
        assertEquals(1f, color.r, 0.001f)
        assertEquals(0f, color.g, 0.001f)
        assertEquals(0f, color.b, 0.001f)
    }

    @Test
    fun `fromAss 短输入左侧补零`() {
        val color = SubtitleColor.fromAss("FFFFFF")
        assertEquals(1f, color.a, 0.001f)
        assertEquals(1f, color.r, 0.001f)
    }

    @Test
    fun `fromAss 蓝色通道映射正确（BBGGRR 顺序）`() {
        // BBGGRR = FF0000 → 蓝色满
        val color = SubtitleColor.fromAss("00FF0000")
        assertEquals(0f, color.r, 0.001f)
        assertEquals(0f, color.g, 0.001f)
        assertEquals(1f, color.b, 0.001f)
    }

    @Test
    fun `fromArgb 解析白色`() {
        val color = SubtitleColor.fromArgb(0xFFFFFFFF.toInt())
        assertEquals(1f, color.r, 0.001f)
        assertEquals(1f, color.g, 0.001f)
        assertEquals(1f, color.b, 0.001f)
        assertEquals(1f, color.a, 0.001f)
    }

    @Test
    fun `fromArgb 解析红色 ARGB`() {
        val color = SubtitleColor.fromArgb(0xFFFF0000.toInt())
        assertEquals(1f, color.r, 0.001f)
        assertEquals(0f, color.g, 0.001f)
        assertEquals(0f, color.b, 0.001f)
        assertEquals(1f, color.a, 0.001f)
    }

    @Test
    fun `fromArgb 解析半透明 ARGB`() {
        // ARGB 的 alpha 语义与 ASS 相反：0x80 = 128 → a = 128/255 ≈ 0.502
        val color = SubtitleColor.fromArgb(0x80FFFFFF.toInt())
        assertEquals(0.502f, color.a, 0.001f)
        assertEquals(1f, color.r, 0.001f)
    }
}

class SubtitleAlignTest {

    @Test
    fun `fromAss 映射数字键盘布局`() {
        assertEquals(SubtitleAlign.BOTTOM_LEFT, SubtitleAlign.fromAss(1))
        assertEquals(SubtitleAlign.BOTTOM_CENTER, SubtitleAlign.fromAss(2))
        assertEquals(SubtitleAlign.BOTTOM_RIGHT, SubtitleAlign.fromAss(3))
        assertEquals(SubtitleAlign.MIDDLE_LEFT, SubtitleAlign.fromAss(4))
        assertEquals(SubtitleAlign.MIDDLE_CENTER, SubtitleAlign.fromAss(5))
        assertEquals(SubtitleAlign.MIDDLE_RIGHT, SubtitleAlign.fromAss(6))
        assertEquals(SubtitleAlign.TOP_LEFT, SubtitleAlign.fromAss(7))
        assertEquals(SubtitleAlign.TOP_CENTER, SubtitleAlign.fromAss(8))
        assertEquals(SubtitleAlign.TOP_RIGHT, SubtitleAlign.fromAss(9))
    }

    @Test
    fun `fromAss 非法值回退底部居中`() {
        assertEquals(SubtitleAlign.BOTTOM_CENTER, SubtitleAlign.fromAss(0))
        assertEquals(SubtitleAlign.BOTTOM_CENTER, SubtitleAlign.fromAss(10))
        assertEquals(SubtitleAlign.BOTTOM_CENTER, SubtitleAlign.fromAss(-1))
    }
}
