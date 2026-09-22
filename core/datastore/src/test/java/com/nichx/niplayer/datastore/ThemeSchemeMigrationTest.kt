package com.nichx.niplayer.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色方案迁移表测试：旧 ordinal（18 套时代）→ 稳定 key（12 套时代）。
 *
 * ## 为什么这个测试很重要
 *
 * 老设备升级后靠这张表把已保存的主题换算过去。改错一项，那部分用户的主题就会**静默**
 * 变成另一套配色，而编译、lint、detekt 全都不会报错。因此这里把 18 项内容钉死，
 * 并单独验证「保留方案必须映射回自身」这条不变式 —— 它保证绝大多数老用户
 * （选的是保留下来的 12 套）升级后看到的是**完全一样**的主题。
 *
 * 该表是**冻结的历史契约**：一旦发布过带本迁移的版本，就不要再改动其中的对应关系。
 */
class ThemeSchemeMigrationTest {

    /** 12 套保留方案的 key，需与 `:core:designsystem` 的 `NiScheme` 保持一致。 */
    private val validKeys = setOf(
        "MISTY", "BLUEBERRY", "DENIM",
        "ROSE_DUST", "STRAWBERRY", "CORAL",
        "FOREST", "MATCHA", "CARAMEL",
        "ALMOND", "MAUVE", "SAGE",
    )

    /** 旧版 18 套的声明顺序（MISTY … SAGE），即迁移映射的输入。 */
    private val legacyOrder = listOf(
        "MISTY", "BLUEBERRY", "DENIM",
        "ROSE_DUST", "STRAWBERRY", "CORAL",
        "FOREST", "MATCHA", "CARAMEL",
        "MINT_MACARON", "SAKURA_MACARON", "LAVENDER_MACARON",
        "SPEARMINT", "BUBBLEGUM", "SUMMER_SODA",
        "ALMOND", "MAUVE", "SAGE",
    )

    @Test
    fun `映射表长度等于旧版方案数`() {
        assertEquals(legacyOrder.size, ThemeSchemeMigration.LEGACY_ORDINAL_KEYS.size)
    }

    @Test
    fun `映射目标都是保留方案`() {
        ThemeSchemeMigration.LEGACY_ORDINAL_KEYS.forEachIndexed { ordinal, key ->
            assertTrue("旧序号 $ordinal 映射到了未知 key：$key", key in validKeys)
        }
    }

    @Test
    fun `保留的方案映射到自身 用户原选择不变`() {
        val kept = legacyOrder.filter { it in validKeys }
        assertEquals("保留方案应为 12 套", 12, kept.size)
        legacyOrder.forEachIndexed { ordinal, legacyKey ->
            if (legacyKey in validKeys) {
                assertEquals(
                    "旧序号 $ordinal（$legacyKey）应映射回自身",
                    legacyKey,
                    ThemeSchemeMigration.keyFromLegacyOrdinal(ordinal),
                )
            }
        }
    }

    @Test
    fun `被删除的六套就近落位到保留方案`() {
        val removed = listOf(
            "MINT_MACARON", "SAKURA_MACARON", "LAVENDER_MACARON",
            "SPEARMINT", "BUBBLEGUM", "SUMMER_SODA",
        )
        val mapped = removed.map {
            ThemeSchemeMigration.keyFromLegacyOrdinal(legacyOrder.indexOf(it))
        }
        // 显式钉住这 6 项：改了说明迁移语义变了，必须是有意为之
        assertEquals(
            listOf("FOREST", "STRAWBERRY", "BLUEBERRY", "FOREST", "STRAWBERRY", "CARAMEL"),
            mapped,
        )
        mapped.forEach { assertTrue("$it 不是保留方案", it in validKeys) }
    }

    @Test
    fun `越界序号回落默认方案`() {
        // 覆盖「旧版本存了更大的序号」「数据被写坏」「序号为负」三种情况
        assertEquals(
            ThemeSettings.DEFAULT_SCHEME_KEY,
            ThemeSchemeMigration.keyFromLegacyOrdinal(legacyOrder.size),
        )
        assertEquals(ThemeSettings.DEFAULT_SCHEME_KEY, ThemeSchemeMigration.keyFromLegacyOrdinal(999))
        assertEquals(ThemeSettings.DEFAULT_SCHEME_KEY, ThemeSchemeMigration.keyFromLegacyOrdinal(-1))
        assertEquals(
            ThemeSettings.DEFAULT_SCHEME_KEY,
            ThemeSchemeMigration.keyFromLegacyOrdinal(Int.MIN_VALUE),
        )
    }

    @Test
    fun `已有稳定 key 时直接采用 迁移不覆盖当前选择`() {
        // 已经写过新格式的设备，即使旧键还在（如降级后再升级），也以新 key 为准
        assertEquals("SAGE", ThemeSchemeMigration.resolveKey("SAGE", 0))
        assertEquals("MATCHA", ThemeSchemeMigration.resolveKey("MATCHA", 9))
    }

    @Test
    fun `只有遗留序号时走换算`() {
        assertEquals("FOREST", ThemeSchemeMigration.resolveKey(null, 9))
        assertEquals("MATCHA", ThemeSchemeMigration.resolveKey(null, 7))
        // 空白 key 视为缺失，不能当成有效值
        assertEquals("MATCHA", ThemeSchemeMigration.resolveKey("   ", 7))
    }

    @Test
    fun `既无稳定 key 也无遗留序号时用默认方案`() {
        // 全新安装：不应凭空写盘，也不应报错
        assertEquals(
            ThemeSettings.DEFAULT_SCHEME_KEY,
            ThemeSchemeMigration.resolveKey(null, null),
        )
        assertEquals(
            ThemeSettings.DEFAULT_SCHEME_KEY,
            ThemeSchemeMigration.resolveKey("", null),
        )
    }
}
