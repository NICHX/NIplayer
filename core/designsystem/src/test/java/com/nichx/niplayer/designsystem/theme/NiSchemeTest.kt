package com.nichx.niplayer.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色方案枚举与序号还原的回归测试（**`:core:designsystem` 首批测试之一**）。
 *
 * ## 为什么这个测试很重要
 *
 * A1 架构修复（2026-09-21）后，`:core:datastore` **只存配色方案的 ordinal**（Int），
 * 由 UI 层在边界处调用 [NiScheme.fromOrdinal] 还原 —— 因为 MMKV 里原本存的就是 ordinal，
 * 所以无需数据迁移。代价是：
 *
 * > **枚举的声明顺序从此成为持久化契约。**
 *
 * 一旦有人在中间插入/调换枚举项，所有用户已保存的主题会**静默错位**（选了 A 显示 B），
 * 而且编译、lint、detekt 全都不会报错。本测试把顺序与还原行为钉死。
 */
class NiSchemeTest {

    /** 顺序即契约：改动此列表 = 改动所有已保存主题的含义，必须同时提供数据迁移。 */
    private val expectedOrder = listOf(
        "MISTY", "BLUEBERRY", "DENIM",
        "ROSE_DUST", "STRAWBERRY", "CORAL",
        "FOREST", "MATCHA", "CARAMEL",
        "MINT_MACARON", "SAKURA_MACARON", "LAVENDER_MACARON", "SPEARMINT", "BUBBLEGUM", "SUMMER_SODA",
        "ALMOND", "MAUVE", "SAGE",
    )

    @Test
    fun `枚举顺序是持久化契约 不得随意改动`() {
        assertEquals(
            "NiScheme 的声明顺序决定了 MMKV 里存的 ordinal 含义 —— " +
                "改动顺序会让所有用户已保存的主题静默错位，必须同时提供数据迁移",
            expectedOrder,
            NiScheme.entries.map { it.name },
        )
    }

    @Test
    fun `每个 ordinal 都能还原为自身`() {
        NiScheme.entries.forEachIndexed { ordinal, scheme ->
            assertSame("ordinal=$ordinal 应还原为 $scheme", scheme, NiScheme.fromOrdinal(ordinal))
        }
    }

    @Test
    fun `越界序号回落到 MISTY`() {
        // 覆盖「旧版本存了更大的序号 / 数据被写坏 / 序号为负」三种情况
        assertSame(NiScheme.MISTY, NiScheme.fromOrdinal(NiScheme.entries.size))
        assertSame(NiScheme.MISTY, NiScheme.fromOrdinal(999))
        assertSame(NiScheme.MISTY, NiScheme.fromOrdinal(-1))
        assertSame(NiScheme.MISTY, NiScheme.fromOrdinal(Int.MIN_VALUE))
    }

    @Test
    fun `ordinal 与 fromOrdinal 互为逆运算`() {
        NiScheme.entries.forEach { scheme ->
            assertSame(scheme, NiScheme.fromOrdinal(scheme.ordinal))
        }
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
        // 冷色 / 暖色 / 自然 / 马卡龙 / 莫兰迪 —— 同分类内应共享 categoryRes
        val byCategory = NiScheme.entries.groupBy { it.categoryRes }
        assertTrue("分类数不应少于 5 个", byCategory.size >= 5)
        byCategory.values.forEach { group ->
            assertTrue("每个分类至少应有 2 个方案", group.size >= 2)
        }
    }
}
