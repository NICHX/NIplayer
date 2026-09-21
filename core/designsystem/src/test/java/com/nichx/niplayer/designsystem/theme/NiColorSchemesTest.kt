package com.nichx.niplayer.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色方案构建的回归测试（**`:core:designsystem` 首批测试之一**）。
 *
 * 覆盖两件容易被改坏、且坏了不会报错的事：
 *
 * 1. **18 套方案 × 明暗两态都能构建成功** —— `buildLight` / `buildDark` 内部的 `when`
 *    是穷尽式（无 `else`），新增枚举项时编译器会拦下；但 `schemeLight` / `schemeDark`
 *    的映射写错（例如把 Dark 方案接到 Light 蓝图）**编译器不会报**，只能靠测试。
 * 2. **`brandScale` 至少 10 个元素** —— [NiExtraColors.brand10] / `brand20` / `brand40` /
 *    `brand80` / `brand90` 直接按 `brandScale[1]`、`[2]`、`[4]`、`[8]`、`[9]` **无边界检查**地取值，
 *    长度不足会在运行期抛 `IndexOutOfBoundsException`。
 */
class NiColorSchemesTest {

    @Test
    fun `每套方案的明暗两态都能构建`() {
        NiScheme.entries.forEach { scheme ->
            val light = NiSchemes.buildLight(scheme)
            val dark = NiSchemes.buildDark(scheme)
            // primary 是每套方案的标识色，必须存在且两态不同（否则明暗主题会撞色）
            assertNotEquals("$scheme 的明暗两态 primary 不应相同", light.primary, dark.primary)
        }
    }

    @Test
    fun `每套方案都能构建装饰色`() {
        NiScheme.entries.forEach { scheme ->
            val light = NiSchemes.buildLightExtra(scheme)
            val dark = NiSchemes.buildDarkExtra(scheme)
            assertFalse("buildLightExtra 不应返回 isDark=true", light.isDark)
            assertTrue("buildDarkExtra 应返回 isDark=true", dark.isDark)
        }
    }

    @Test
    fun `brandScale 至少 10 个元素 否则 brand90 会越界`() {
        // brand10/20/40/80/90 直接索引 [1]/[2]/[4]/[8]/[9]，无边界检查
        NiScheme.entries.forEach { scheme ->
            val scale = NiSchemes.buildLightExtra(scheme).brandScale
            assertTrue("$scheme 的 brandScale 只有 ${scale.size} 个元素，brand90 会越界", scale.size >= 10)
        }
    }

    @Test
    fun `brand 便捷取值不会越界`() {
        // 逐项访问一遍，任一处越界都会在此抛出
        NiScheme.entries.forEach { scheme ->
            val e = NiSchemes.buildLightExtra(scheme)
            listOf(e.brand10, e.brand20, e.brand40, e.brand80, e.brand90)
        }
    }

    @Test
    fun `brandScale 首尾是最暗与最亮`() {
        NiScheme.entries.forEach { scheme ->
            val scale = NiSchemes.buildLightExtra(scheme).brandScale.take(10)
            val first = scale.first().luminance()
            val last = scale.last().luminance()
            scale.forEachIndexed { i, c ->
                assertTrue(
                    "$scheme: brandScale[0] 应是最暗的（下标 $i 更暗）",
                    first <= c.luminance() + 1e-4f,
                )
                assertTrue(
                    "$scheme: brandScale[9] 应是最亮的（下标 $i 更亮）",
                    last >= c.luminance() - 1e-4f,
                )
            }
        }
    }

    @Test
    fun `brandScale 全部为不透明色`() {
        // 色阶用于色块/进度等实心绘制，半透明会露出底下的内容
        NiScheme.entries.forEach { scheme ->
            NiSchemes.buildLightExtra(scheme).brandScale.forEachIndexed { i, c ->
                assertEquals("$scheme brandScale[$i] 应为不透明", 1f, c.alpha, 1e-4f)
            }
        }
    }

    @Test
    fun `明暗两态的存储类型色不同`() {
        val light = NiSchemes.buildLightExtra(NiScheme.MISTY)
        val dark = NiSchemes.buildDarkExtra(NiScheme.MISTY)
        assertNotEquals(light.storageLocalColor, dark.storageLocalColor)
        assertNotEquals(light.storageSmbColor, dark.storageSmbColor)
        assertNotEquals(light.storageWebdavColor, dark.storageWebdavColor)
    }

    @Test
    fun `同一方案重复构建结果稳定`() {
        // 纯函数：同样的输入必须给出同样的输出（防止引入随机/时间依赖）
        assertEquals(
            NiSchemes.buildLightExtra(NiScheme.DENIM),
            NiSchemes.buildLightExtra(NiScheme.DENIM),
        )
    }

    @Test
    fun `surfaceLevel 层级递进`() {
        // 注释里写明是「主色 + 中性底的轻量 tint」，层级越深越偏向主色
        NiScheme.entries.forEach { scheme ->
            val e = NiSchemes.buildLightExtra(scheme)
            val base = Color.White
            val d1 = colorDistance(base, e.surfaceLevel1)
            val d2 = colorDistance(base, e.surfaceLevel2)
            val d3 = colorDistance(base, e.surfaceLevel3)
            assertTrue("$scheme: surfaceLevel1 应最接近底色", d1 <= d2 + 1e-4f)
            assertTrue("$scheme: surfaceLevel3 应最远离底色", d3 >= d2 - 1e-4f)
        }
    }

    private fun colorDistance(a: Color, b: Color): Float =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
}
