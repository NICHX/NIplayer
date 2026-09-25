package com.nichx.niplayer.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 迁移的**数据库级**验证（工程化 E3）。
 *
 * ## 为什么需要它
 *
 * 在此之前，v10→v18 的迁移只有两层验证：
 * 1. `core/database/tools/verify_migrations.py` —— 离线校验器，把迁移里的 SQL 与 schemas 目录下的
 *    JSON 做文本比对，**不执行 SQL**，因此发现不了「SQL 语法正确但约束/默认值/索引与 Room 期望不符」；
 * 2. 人工审查。
 *
 * 本测试用 Room 官方的 [MigrationTestHelper]，在**真实 SQLite** 上把旧版本库建出来、跑迁移、
 * 再拿迁移结果与目标版本的 schema JSON 逐表逐列比对。这是唯一能捕获「迁移写错但看起来对」的层级。
 *
 * ## 覆盖范围
 *
 * | 迁移 | 说明 |
 * |---|---|
 * | 10→11 | 新增 playlist / playlist_item |
 * | 11→12 | play_history 增加 playlist_id |
 * | 14→15 | playlist 增加 is_pinned |
 * | 15→16 | 新增 upload_task |
 * | 16→18 | 空迁移（16→17）+ 删除歌单表（17→18），**链式验证** |
 * | 18→19 | 删除播放历史云同步冲突表 sync_conflict |
 * | 19→20 | 删除视频书签表 video_bookmark（书签功能下线） |
 * | 10→20 | 全链路一次跑完，同时验证数据保留 |
 *
 * ## 为什么 16→17 不能单独验证
 *
 * `schemas/com.nichx.niplayer.database.NiplayerDatabase/` 下**没有 17.json**：v17 的 schema 从未被
 * 导出（DB 版本从 16 直跳 18，KSP 没有为 v17 生成过 schema），因此
 * `runMigrationsAndValidate(name, 17, ...)` 无从加载期望 schema。
 * 好在 Room 只在**全部迁移跑完后**校验一次最终 schema，所以 `16→18` 链式验证在语义上等价 ——
 * 它正是真实升级路径上唯一会被观察到的中间态。
 *
 * [schemas_不含_17_json_这是_16_17_只能链式验证的原因] 把这个约束钉成断言：将来若有人补出 17.json，
 * 该测试会失败并提醒把 16→17 拆成独立用例。
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NiplayerDatabase::class.java,
    )

    // ==================== 六段补齐的迁移 ====================

    @Test
    fun `10到11_建出歌单两张表且既有数据保留`() {
        helper.createDatabase(TEST_DB, 10).use { db ->
            insertLibrary(db, "smb://192.168.1.10", "家庭 NAS")
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, NiplayerDatabase.MIGRATION_10_11)
        db.use {
            assertTrue("playlist 应被创建", it.hasTable("playlist"))
            assertTrue("playlist_item 应被创建", it.hasTable("playlist_item"))
            assertEquals(0, it.countRows("playlist_item"))
            assertEquals(1, it.countRows("media_library"))
            assertEquals("家庭 NAS", it.queryString("SELECT display_name FROM media_library"))
        }
    }

    @Test
    fun `11到12_play_history_增加_playlist_id_列`() {
        helper.createDatabase(TEST_DB, 11).use { db ->
            insertPlayHistory(db, "第 1 集.mkv")
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, NiplayerDatabase.MIGRATION_11_12)
        db.use {
            assertTrue("playlist_id 列应存在", it.hasColumn("play_history", "playlist_id"))
            assertEquals(1, it.countRows("play_history"))
            // 旧行的新列为 NULL，不做默认值填充
            assertTrue(it.queryIsNull("SELECT playlist_id FROM play_history"))
        }
    }

    @Test
    fun `14到15_playlist_增加_is_pinned_且旧行取默认值0`() {
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL("INSERT INTO playlist (name, created_at, updated_at) VALUES ('稍后看', 100, 100)")
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, NiplayerDatabase.MIGRATION_14_15)
        db.use {
            assertTrue("is_pinned 列应存在", it.hasColumn("playlist", "is_pinned"))
            assertEquals(1, it.countRows("playlist"))
            assertEquals(0L, it.queryLong("SELECT is_pinned FROM playlist"))
        }
    }

    @Test
    fun `15到16_建出_upload_task_表`() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            insertLibrary(db, "webdav://nas.local/dav", "坚果云")
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, NiplayerDatabase.MIGRATION_15_16)
        db.use {
            assertEquals(0, it.countRows("upload_task"))
            assertEquals(1, it.countRows("media_library"))
        }
    }

    @Test
    fun `16到18_空迁移加删歌单表_链式验证`() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            insertLibrary(db, "smb://192.168.1.10", "家庭 NAS")
            insertPlayHistory(db, "第 1 集.mkv")
            db.execSQL("INSERT INTO playlist (name, created_at, updated_at, is_pinned) VALUES ('稍后看', 100, 100, 1)")
            db.execSQL(
                "INSERT INTO playlist_item (playlist_id, library_id, file_path, file_name, media_type, file_size, sort_order) " +
                    "VALUES (1, 1, '/a/b.mkv', 'b.mkv', 'SMB', 1024, 0)"
            )
        }
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            true,
            NiplayerDatabase.MIGRATION_16_17,
            NiplayerDatabase.MIGRATION_17_18,
        )
        db.use {
            assertFalse("playlist 应被删除", it.hasTable("playlist"))
            assertFalse("playlist_item 应被删除", it.hasTable("playlist_item"))
            // play_history.playlist_id 刻意保留（实体仍声明该列，仅不再写入）
            assertTrue("playlist_id 列应保留", it.hasColumn("play_history", "playlist_id"))
            assertEquals(1, it.countRows("media_library"))
            assertEquals(1, it.countRows("play_history"))
        }
    }

    // ==================== 全链路 ====================

    @Test
    fun `18到19_删除冲突表且播放历史与删除队列保留`() {
        helper.createDatabase(TEST_DB, 18).use { db ->
            insertLibrary(db, "smb://192.168.1.10", "家庭 NAS")
            insertPlayHistory(db, "第 1 集.mkv")
            db.execSQL(
                "INSERT INTO sync_delete_log (table_name, record_key, deleted_at, synced) " +
                    "VALUES ('play_history', 'k1', 100, 0)"
            )
            db.execSQL(
                "INSERT INTO sync_conflict " +
                    "(record_key, storage_id, unique_key, video_name, local_video_position, " +
                    "local_video_duration, local_updated_at, local_play_time, remote_video_position, " +
                    "remote_video_duration, remote_updated_at, resolved, created_at) " +
                    "VALUES ('k1', 1, 'uk1', '第 1 集.mkv', 100, 200, 100, 100, 150, 200, 150, 0, 100)"
            )
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, NiplayerDatabase.MIGRATION_18_19)
        db.use {
            assertFalse("sync_conflict 应被删除", it.hasTable("sync_conflict"))
            assertEquals("播放历史不能丢", 1, it.countRows("play_history"))
            assertEquals("待发布删除队列不能丢", 1, it.countRows("sync_delete_log"))
            assertEquals(1, it.countRows("media_library"))
        }
    }

    @Test
    fun `10到20_全链路升级成功且用户数据零丢失`() {
        helper.createDatabase(TEST_DB, 10).use { db ->
            insertLibrary(db, "smb://192.168.1.10", "家庭 NAS")
            insertLibrary(db, "webdav://nas.local/dav", "坚果云")
            insertPlayHistory(db, "第 1 集.mkv")
            insertPlayHistory(db, "第 2 集.mkv")
            // v10 的 encrypted_folder.iterations 是 NOT NULL **且无 DEFAULT**（实体当时未声明
            // defaultValue），所以这里必须显式给值 —— 顺带说明 MIGRATION_9_10 里那句
            // `DEFAULT 120000` 只是迁移 SQL 的写法，Room 的默认值比较规则会跳过该列，不是缺陷。
            db.execSQL(
                "INSERT INTO encrypted_folder (storage_id, folder_path, password_hash, password_salt, iterations, created_at, updated_at) " +
                    "VALUES (1, '/私密', 'hash', 'salt', 120000, 100, 100)"
            )
            db.execSQL(
                "INSERT INTO video_bookmark (unique_key, storage_id, video_name, position_ms, created_at, updated_at) " +
                    "VALUES ('k1', 1, '第 1 集.mkv', 12345, 100, 100)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            20,
            true,
            NiplayerDatabase.MIGRATION_10_11,
            NiplayerDatabase.MIGRATION_11_12,
            NiplayerDatabase.MIGRATION_12_13,
            NiplayerDatabase.MIGRATION_13_14,
            NiplayerDatabase.MIGRATION_14_15,
            NiplayerDatabase.MIGRATION_15_16,
            NiplayerDatabase.MIGRATION_16_17,
            NiplayerDatabase.MIGRATION_17_18,
            NiplayerDatabase.MIGRATION_18_19,
            NiplayerDatabase.MIGRATION_19_20,
        )

        db.use {
            assertEquals("媒体库配置不能丢", 2, it.countRows("media_library"))
            assertEquals("播放历史不能丢", 2, it.countRows("play_history"))
            assertEquals("加密目录记录不能丢", 1, it.countRows("encrypted_folder"))
            assertFalse("video_bookmark 表应随书签功能下线被移除", it.hasTable("video_bookmark"))
            assertEquals("家庭 NAS", it.queryString("SELECT display_name FROM media_library WHERE url = 'smb://192.168.1.10'"))
            assertFalse(it.hasTable("playlist"))
            assertFalse(it.hasTable("playlist_item"))
            assertFalse(it.hasTable("sync_conflict"))
        }
    }

    /**
     * 把「17.json 不存在」这一事实固化为断言。
     *
     * 这不是在测试业务逻辑，而是在测试**验证能力本身的边界**：只要 17.json 缺失，
     * 16→17 就无法独立验证，[MigrationTest] 的链式写法就是唯一正确写法。
     * 若将来 v17 的 schema 被重新导出，本用例会失败 —— 那时应当把 16→17 拆成独立用例。
     */
    @Test
    fun `schemas_不含_17_json_这是_16_17_只能链式验证的原因`() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val files = assets.list(SCHEMA_ASSETS_DIR).orEmpty().toSet()
        assertTrue("schemas 目录未挂进测试 assets，迁移测试的前提不成立", files.isNotEmpty())
        assertTrue("缺少 16.json，16→18 链式验证无法进行", "16.json" in files)
        assertTrue("缺少 18.json，18→19 与链式验证无法进行", "18.json" in files)
        assertTrue("缺少 19.json，18→19 与 19→20 的链式验证无法进行", "19.json" in files)
        assertTrue("缺少 20.json，最终 schema 无法校验", "20.json" in files)
        assertFalse(
            "17.json 已被补出：请把 16→17 拆成独立的 runMigrationsAndValidate(TEST_DB, 17, ...) 用例",
            "17.json" in files,
        )
    }

    // ==================== 辅助 ====================

    private fun SupportSQLiteDatabase.countRows(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun SupportSQLiteDatabase.queryString(sql: String): String? =
        query(sql).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun SupportSQLiteDatabase.queryLong(sql: String): Long =
        query(sql).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }

    private fun SupportSQLiteDatabase.queryIsNull(sql: String): Boolean =
        query(sql).use { c ->
            c.moveToFirst()
            c.isNull(0)
        }

    private fun SupportSQLiteDatabase.hasTable(table: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.moveToFirst() }

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        query("PRAGMA table_info(`$table`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == column) {
                    found = true
                    break
                }
            }
            found
        }

    private fun insertLibrary(db: SupportSQLiteDatabase, url: String, name: String) {
        db.execSQL(
            """
            INSERT INTO media_library
                (display_name, url, media_type, is_anonymous, port, smb_v2, smb_encryption,
                 web_dav_strict, screencast_address, updated_at)
            VALUES (?, ?, 'SMB', 1, 445, 1, 0, 1, '', 100)
            """.trimIndent(),
            arrayOf(name, url),
        )
    }

    private fun insertPlayHistory(db: SupportSQLiteDatabase, name: String) {
        db.execSQL(
            """
            INSERT INTO play_history
                (video_name, url, media_type, video_position, video_duration, play_time,
                 torrent_index, unique_key, updated_at)
            VALUES (?, ?, 'SMB', 0, 0, 100, 0, ?, 100)
            """.trimIndent(),
            arrayOf(name, "/a/$name", name),
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val SCHEMA_ASSETS_DIR = "com.nichx.niplayer.database.NiplayerDatabase"
    }
}
