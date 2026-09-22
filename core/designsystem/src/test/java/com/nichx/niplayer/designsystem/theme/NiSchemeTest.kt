package com.nichx.niplayer.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色方案枚举的持久化契约测试（**`:core:designsystem` 首批测试之一**）。
 *
 * ## 为什么这个测试很重要
 *
 * MMKV（`:core:datastore` 的 `ThemeSettings`）与备份文件里存的是 [NiScheme.key] 字符串。
 * 增删枚举项、调整声明顺序都**不会**影响已保存的主题；但**改动某个 key 的值 = 改动所有
 * 已保存主题的含义**（选了 A 显示 B），而且编译、lint、detekt 全都不会报错。
 * 本测试把 key 集合钉死。
 *
 * ## 历史
 *
 * 2026-09-22 之前存的是 ordinal，所以当时钉的是「枚举声明顺序」。配色从 18 套精简到
 * 12 套（删除整个糖果系 / 薄荷曼波）时改用稳定 key；旧 ordinal → key 的换算由
 * `:core:datastore` 的 `ThemeSchemeMigration` 负责，其契约由该模块的
 * `ThemeSchemeMigrationTest` 钉住。
 */
class NiSchemeTest {

    /** key 即契约：改动此列表 = 改动所有已保存主题的含义，必须同时提供数据迁移。 */
    private val expectedKeys = listOf(
        "MISTY", "BLUEBERRY", "DENIM",
        "ROSE_DUST", "STRAWBERRY", "CORAL",
        "FOREST", "MATCHA", "CARAMEL",
        "ALMOND", "MAUVE", "SAGE",
    )

    @Test
    fun `key 集合是持久化契约 不得随意改动`() {
        assertEquals(
            "NiScheme 的 key 决定 MMKV / 备份里已保存主题的含义 —— " +
                "改动 key 会让用户已保存的主题静默错位，必须同时提供数据迁移",
            expectedKeys,
            NiScheme.entries.map { it.key },
        )
    }

    @Test
    fun `每个 key 都能还原为自身`() {
        NiScheme.entries.forEach { scheme ->
            assertSame("key=${scheme.key} 应还原为 $scheme", scheme, NiScheme.fromKey(scheme.key))
        }
    }

    @Test
    fun `key 不重复`() {
        assertEquals(NiScheme.entries.size, NiScheme.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `未知或缺失的 key 回落到默认方案`() {
        // 覆盖「全新安装（key 缺失）」与「数据被写坏 / 被未来版本写入（未知 key）」两种情况
        assertSame(NiScheme.MISTY, NiScheme.fromKey(null))
        assertSame(NiScheme.MISTY, NiScheme.fromKey(""))
        assertSame(NiScheme.MISTY, NiScheme.fromKey("NO_SUCH_SCHEME"))
        // 大小写敏感：小写形式不是合法 key，同样回落默认（此处不能用 MISTY 验证，它本身就是默认值）
        assertSame(NiScheme.MISTY, NiScheme.fromKey("sage"))
        assertSame(NiScheme.DEFAULT, NiScheme.fromKey(null))
    }

    @Test
    fun `每个方案都带本地化文案资源`() {
        // labelRes / categoryRes 必须是有效资源 id（非 0），否则主题选择页会显示空白
        NiScheme.entries.forEach { scheme ->
            assertTrue("$scheme 缺少 labelRes", scheme.labelRes != 0)
            assertTrue("$scheme 缺少 categoryRes", scheme.categoryRes != 0)
        }
    }

    @Test
    fun `分类被合理分组`() {
        // 冷色 / 暖色 / 自然 / 莫兰迪 —— 同分类内共享 categoryRes，且每类恰好 3 套
        // （主题页网格每行 3 个，每类 3 套正好整除，不留空位）
        val byCategory = NiScheme.entries.groupBy { it.categoryRes }
        assertEquals("分类数应为 4 个", 4, byCategory.size)
        byCategory.values.forEach { group ->
            assertEquals("每个分类应恰好 3 个方案", 3, group.size)
        }
    }

    @Test
    fun `主色相要铺开 既不能扎堆也不能缺席某一段`() {
        // 2026-09-22 用户反馈「配色分布不均匀，蓝绿紫色偏多，粉色没有了」—— 精简配色时
        // 只按「删掉一整个分类」做减法，结果三支蓝挤在 21° 内、粉紫区一支不剩。
        // 把这条经验固化成断言，日后再换色板时不会重犯。
        val hues = NiScheme.entries.map { it to hueOf(NiSchemes.buildLight(it).primary) }

        // 1) 任意两套主色相至少拉开 8°（同族靠饱和度 / 明度区分，色相不该挤在一起）
        val sorted = hues.map { it.second }.sorted()
        sorted.forEachIndexed { index, hue ->
            val next = sorted[(index + 1) % sorted.size]
            val gap = (next - hue + 360f) % 360f
            assertTrue(
                "主色相 ${hue.toInt()}° 与 ${next.toInt()}° 只差 ${gap.toInt()}°，扎堆了",
                gap >= 8f,
            )
        }

        // 2) 粉紫区（300°–360°）至少 2 套 —— 不能再出现「一支粉都没有」
        val pinkish = hues.count { it.second >= 300f && it.second < 360f }
        assertTrue("粉紫区只有 $pinkish 套，粉色缺席", pinkish >= 2)
    }
}

/** 取颜色的色相角（0–360°）；灰阶（无彩度）返回 0。 */
private fun hueOf(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val delta = max - min
    if (delta < 0.0001f) return 0f
    val hue = when (max) {
        color.red -> 60f * (((color.green - color.blue) / delta) % 6f)
        color.green -> 60f * (((color.blue - color.red) / delta) + 2f)
        else -> 60f * (((color.red - color.green) / delta) + 4f)
    }
    return if (hue < 0f) hue + 360f else hue
}
