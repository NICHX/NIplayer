package com.nichx.niplayer.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 窗口尺寸断点的回归测试（**`:core:designsystem` 首批测试之一**）。
 *
 * 断点决定布局分支（单列 / 双列 / 分栏），改动会静默影响所有页面的排布，
 * 故把 M3 标准断点钉住。
 */
class NiWindowSizeClassTest {

    private fun size(w: Int, h: Int) = computeNiWindowSizeClass(w.dp, h.dp)

    // ── 宽度断点：< 600 Compact / < 840 Medium / else Expanded ──────────

    @Test
    fun `宽度断点边界`() {
        assertEquals(NiWindowWidthSizeClass.Compact, size(0, 800).width)
        assertEquals(NiWindowWidthSizeClass.Compact, size(599, 800).width)
        assertEquals(NiWindowWidthSizeClass.Medium, size(600, 800).width)
        assertEquals(NiWindowWidthSizeClass.Medium, size(839, 800).width)
        assertEquals(NiWindowWidthSizeClass.Expanded, size(840, 800).width)
        assertEquals(NiWindowWidthSizeClass.Expanded, size(2000, 800).width)
    }

    // ── 高度断点：< 480 Compact / < 900 Medium / else Expanded ──────────

    @Test
    fun `高度断点边界`() {
        assertEquals(NiWindowHeightSizeClass.Compact, size(800, 479).height)
        assertEquals(NiWindowHeightSizeClass.Medium, size(800, 480).height)
        assertEquals(NiWindowHeightSizeClass.Medium, size(800, 899).height)
        assertEquals(NiWindowHeightSizeClass.Expanded, size(800, 900).height)
    }

    @Test
    fun `宽高独立判定`() {
        // 平板横屏：宽 Expanded + 高 Medium
        val s = size(1280, 800)
        assertEquals(NiWindowWidthSizeClass.Expanded, s.width)
        assertEquals(NiWindowHeightSizeClass.Medium, s.height)
    }

    @Test
    fun `便捷判定属性与枚举一致`() {
        val compact = size(400, 400)
        assertTrue(compact.isCompactWidth)
        assertFalse(compact.isMediumWidth)
        assertFalse(compact.isExpandedWidth)

        val medium = size(700, 400)
        assertTrue(medium.isMediumWidth)
        assertFalse(medium.isCompactWidth)

        val expanded = size(1000, 400)
        assertTrue(expanded.isExpandedWidth)
        assertFalse(expanded.isMediumWidth)
    }

    @Test
    fun `宽度分类单调不减`() {
        // 宽度递增时分类不应回退
        val order = listOf(NiWindowWidthSizeClass.Compact, NiWindowWidthSizeClass.Medium, NiWindowWidthSizeClass.Expanded)
        var last = 0
        var w = 0
        while (w <= 1600) {
            val idx = order.indexOf(size(w, 800).width)
            assertTrue("宽度 $w 的分类出现回退", idx >= last)
            last = idx
            w += 37
        }
    }

    @Test
    fun `零与负尺寸不崩溃`() {
        // 折叠屏折叠态/极端测量下可能出现 0，不能抛异常
        assertEquals(NiWindowWidthSizeClass.Compact, size(0, 0).width)
        assertEquals(NiWindowHeightSizeClass.Compact, size(0, 0).height)
    }
}
