package com.nichx.niplayer.database

import androidx.room.migration.Migration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 「迁移是否登记到 Room Builder」的守卫测试。
 *
 * ## 为什么需要它
 *
 * 2026-09-25 移除视频书签功能时新增了 `MIGRATION_19_20`，并在 [NiplayerDatabase] 的 companion 里
 * 正确定义了它 —— 但**忘了**把它加进 `DatabaseModule` 的 `addMigrations(...)`。结果：
 *
 * - 编译通过、detekt 通过、`assembleDebug` 通过；
 * - `MigrationTest` 也全绿 —— 因为它是**直接引用 `MIGRATION_19_20` 常量**跑迁移的，
 *   根本不经过 `RoomDatabase.Builder`，对「漏登记」完全无感；
 * - 直到装到真机上从 v19 升级，才抛
 *   `IllegalStateException: A migration from 19 to 20 was required but not found` 崩溃。
 *
 * 本测试把「companion 里**声明**的迁移」与 [NiplayerDatabase.ALL_MIGRATIONS]
 * （Builder 的唯一来源）做交叉比对，补上这个盲区。
 *
 * > 注：这次只是崩溃而非静默丢数据，是因为 Builder 用了
 * > `fallbackToDestructiveMigrationFrom(true, 1..5)` 收窄了破坏性回退范围 ——
 * > 缺口版本会抛异常而不是删库重建。该保护按设计生效了。
 *
 * ## 实现注意
 *
 * Kotlin 会把 companion object 里 `val MIGRATION_x_y = object : Migration(...)` 编译成
 * **宿主类 `NiplayerDatabase` 的 `private static` 字段**，而不是 `Companion` 的实例字段
 * （`javap` 可验证：`Companion` 上只有 getter）。因此反射必须扫宿主类，并以 `null` 作接收者。
 */
@RunWith(RobolectricTestRunner::class)
class MigrationRegistrationTest {

    @Test
    fun `companion 声明的每个迁移都已登记进 ALL_MIGRATIONS`() {
        val declared = declaredMigrations()
        assertTrue(
            "未扫描到任何 MIGRATION_* 字段，反射逻辑可能已失效。实际字段：${fieldNames()}",
            declared.isNotEmpty(),
        )

        val registered = NiplayerDatabase.ALL_MIGRATIONS
            .map { it.startVersion to it.endVersion }
            .toSet()

        val missing = declared.filterValues { (it.startVersion to it.endVersion) !in registered }

        assertTrue(
            "以下迁移已声明但未登记进 ALL_MIGRATIONS：${missing.keys} —— " +
                "用户升级到该版本时会抛 IllegalStateException 崩溃，请把它们加入 ALL_MIGRATIONS。",
            missing.isEmpty(),
        )
    }

    @Test
    fun `ALL_MIGRATIONS 是连续无缺口的迁移链`() {
        val all = NiplayerDatabase.ALL_MIGRATIONS
        assertTrue("ALL_MIGRATIONS 为空", all.isNotEmpty())

        val edges = all.map { it.startVersion to it.endVersion }.toSet()
        val from = all.minOf { it.startVersion }
        val to = all.maxOf { it.endVersion }
        val expected = (from until to).map { it to it + 1 }.toSet()

        assertEquals("迁移链在 v$from → v$to 之间存在缺口，用户升级会崩溃", expected, edges)
        assertEquals("迁移链起点应为 v6（v1~v5 由 fallbackToDestructiveMigrationFrom 允许重建）", 6, from)
    }

    /** 反射取出声明的全部 `MIGRATION_*`（宿主类的 private static 字段）。 */
    private fun declaredMigrations(): Map<String, Migration> =
        NiplayerDatabase::class.java.declaredFields
            .filter { it.name.startsWith("MIGRATION_") }
            .onEach { it.isAccessible = true }
            .mapNotNull { field ->
                // static 字段以 null 作为接收者
                (runCatching { field.get(null) }.getOrNull() as? Migration)
                    ?.let { field.name to it }
            }
            .toMap()

    /** 诊断用：反射失效时把实际字段名打印出来。 */
    private fun fieldNames(): List<String> =
        NiplayerDatabase::class.java.declaredFields.map { it.name }
}
