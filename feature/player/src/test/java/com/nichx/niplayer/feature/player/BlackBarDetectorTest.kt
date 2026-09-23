package com.nichx.niplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BlackBarDetector] 的纯函数部分（不涉及 Bitmap 像素扫描）。
 *
 * [BlackBarDetector.detect] 需要 Android Bitmap，无法在 JVM 单测里跑；但判决用到的
 * 比例白名单、偶数对齐与多帧合并都是纯数据运算，这里把它们钉住。
 */
class BlackBarDetectorTest {

    // region 成品比例白名单

    @Test
    fun `常见影视比例命中白名单`() {
        // 2.40 / 2.39（宽银幕）、1.90（IMAX 扩展）、1.78（16:9）、1.33（4:3）
        assertTrue(BlackBarDetector.matchesKnownAspect(3840, 1600))
        assertTrue(BlackBarDetector.matchesKnownAspect(3840, 1610))
        assertTrue(BlackBarDetector.matchesKnownAspect(3840, 2024))
        assertTrue(BlackBarDetector.matchesKnownAspect(1920, 1080))
        assertTrue(BlackBarDetector.matchesKnownAspect(1440, 1080))
    }

    @Test
    fun `竖屏内容按长边比短边归一化 —— 横竖屏都能命中`() {
        assertTrue(BlackBarDetector.matchesKnownAspect(1080, 1920))
    }

    @Test
    fun `不成比例的形状被否决 —— 字幕与竖排文字造成的矩形不会触发裁剪`() {
        assertFalse(BlackBarDetector.matchesKnownAspect(3840, 1000))
        assertFalse(BlackBarDetector.matchesKnownAspect(100, 100))
        assertFalse(BlackBarDetector.matchesKnownAspect(384, 100))
    }

    @Test
    fun `非法尺寸一律不命中`() {
        assertFalse(BlackBarDetector.matchesKnownAspect(0, 0))
        assertFalse(BlackBarDetector.matchesKnownAspect(-1, 100))
        assertFalse(BlackBarDetector.matchesKnownAspect(100, 0))
    }

    // endregion

    // region 偶数对齐

    @Test
    fun `偶数尺寸的整帧裁剪保持不变`() {
        val rect = BlackBarDetector.CropDetectResult(
            left = 0, top = 0, right = 383, bottom = 201,
            bmpWidth = 384, bmpHeight = 202,
        )
        val aligned = BlackBarDetector.roundAlign(rect)!!
        assertEquals(0, aligned.left)
        assertEquals(0, aligned.top)
        assertEquals(383, aligned.right)
        assertEquals(201, aligned.bottom)
    }

    @Test
    fun `奇数边界被向内对齐到偶数 —— 满足 YUV 4比2比0 要求`() {
        val rect = BlackBarDetector.CropDetectResult(
            left = 1, top = 1, right = 100, bottom = 100,
            bmpWidth = 640, bmpHeight = 480,
        )
        val aligned = BlackBarDetector.roundAlign(rect)!!
        assertEquals(0, aligned.left % 2)
        assertEquals(0, aligned.top % 2)
        assertEquals(0, aligned.width % 2)
        assertEquals(0, aligned.height % 2)
        // 只允许向中心收缩，不允许扩大到全图之外
        assertTrue(aligned.left >= rect.left)
        assertTrue(aligned.top >= rect.top)
        assertTrue(aligned.right <= rect.right)
    }

    @Test
    fun `无效结果对齐后返回 null —— 交由调用方退回不裁`() {
        val collapsed = BlackBarDetector.CropDetectResult(
            left = 5, top = 5, right = 5, bottom = 5,
            bmpWidth = 640, bmpHeight = 480,
        )
        assertNull(BlackBarDetector.roundAlign(collapsed))
    }

    // endregion

    // region 多帧合并

    @Test
    fun `交集合并只收缩边界 —— 用于同一帧的重复扫描`() {
        val previous = BlackBarDetector.CropDetectResult(10, 10, 100, 100, 200, 200)
        val current = BlackBarDetector.CropDetectResult(5, 20, 120, 90, 200, 200)
        val merged = BlackBarDetector.merge(previous, current)
        assertEquals(10, merged.left)
        assertEquals(20, merged.top)
        assertEquals(100, merged.right)
        assertEquals(90, merged.bottom)
    }

    @Test
    fun `并集合并只扩大边界 —— 用于多帧累积防止暗场误裁`() {
        val previous = BlackBarDetector.CropDetectResult(10, 10, 100, 100, 200, 200)
        val current = BlackBarDetector.CropDetectResult(5, 20, 120, 90, 200, 200)
        val merged = BlackBarDetector.mergeUnion(previous, current)
        assertEquals(5, merged.left)
        assertEquals(10, merged.top)
        assertEquals(120, merged.right)
        assertEquals(100, merged.bottom)
    }

    // endregion
}
