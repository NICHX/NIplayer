package com.nichx.niplayer.sync

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.database.normalizeBaseUrl
import com.nichx.niplayer.database.syncKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * [SyncMerge.plan] 的行为回归测试（纯函数，无 IO）。
 *
 * 锁定的关键不变式：
 * - 远端墓碑按时间杀本机记录，晚于墓碑的记录（有意重播）不被误删；
 * - 本机墓碑挡住较旧的远端记录，不复活；
 * - **本机匹配不到存储库的外来记录仍必须留在写回内容里**（单文件协议下的数据安全底线）；
 * - 计划的**幂等性**（重复计算同一状态无动作）与**收敛性**（两端各自计算得到同一份文件）。
 */
class SyncPlanTest {

    // ==================== 基础 ====================

    @Test
    fun `云端为空时本机记录原样写回且无落库动作`() {
        val plan = plan(local(records = listOf(entity())), remote = null)

        assertEquals(listOf(record()), plan.file.records)
        assertTrue(plan.file.deletes.isEmpty())
        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `本机记录无法投影时既不写回也不落库`() {
        val plan = plan(local(records = listOf(entity(path = null))), remote = null)

        assertTrue(plan.file.records.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    // ==================== 墓碑 ====================

    @Test
    fun `远端墓碑晚于本机记录时删除本机记录并保留墓碑`() {
        val stale = entity(id = 7, updatedAt = 100)
        val plan = plan(
            local(records = listOf(stale)),
            remote = file(deletes = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200))),
        )

        assertEquals(listOf(7), plan.deletes)
        assertEquals(listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)), plan.file.deletes)
        assertTrue(plan.file.records.isEmpty())
    }

    @Test
    fun `墓碑与本机记录时间戳相同时墓碑胜出`() {
        val plan = plan(
            local(records = listOf(entity(id = 7, updatedAt = 100))),
            remote = file(deletes = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 100))),
        )

        assertEquals(listOf(7), plan.deletes)
    }

    @Test
    fun `本机记录晚于墓碑时视为重播并保留记录`() {
        val plan = plan(
            local(records = listOf(entity(updatedAt = 300))),
            remote = file(deletes = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200))),
        )

        assertTrue(plan.deletes.isEmpty())
        assertTrue(plan.file.deletes.isEmpty())
        assertEquals(listOf(record(updatedAt = 300)), plan.file.records)
    }

    @Test
    fun `本机墓碑晚于远端记录时不复活且墓碑继续传播`() {
        val plan = plan(
            local(tombstones = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200))),
            remote = file(records = listOf(record(updatedAt = 100))),
        )

        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.file.records.isEmpty())
        assertEquals(listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)), plan.file.deletes)
    }

    @Test
    fun `本机墓碑与远端墓碑取较晚的删除时间`() {
        val plan = plan(
            local(tombstones = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 100))),
            remote = file(deletes = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200))),
        )

        assertEquals(listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)), plan.file.deletes)
    }

    @Test
    fun `墓碑之后重新播放的远端记录允许插入`() {
        val plan = plan(
            local(tombstones = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 100))),
            remote = file(records = listOf(record(updatedAt = 200))),
        )

        assertEquals(1, plan.inserts.size)
        assertEquals(1, plan.file.records.size)
        assertTrue(plan.file.deletes.isEmpty())
    }

    // ==================== 记录合并 ====================

    @Test
    fun `远端记录更新时覆盖本机记录并采用远端内容`() {
        val existing = entity(id = 3, updatedAt = 100, position = 10)
        val plan = plan(
            local(records = listOf(existing)),
            remote = file(records = listOf(record(updatedAt = 200, position = 900))),
        )

        assertEquals(1, plan.updates.size)
        assertEquals(3, plan.updates.first().entity.id)
        assertEquals(listOf(record(updatedAt = 200, position = 900)), plan.file.records)
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `远端记录较新且本机无对应记录时按匹配到的存储库新建`() {
        val plan = plan(
            local(),
            remote = file(records = listOf(record(updatedAt = 200))),
        )

        assertEquals(1, plan.inserts.size)
        val insert = plan.inserts.first()
        assertEquals(1, insert.storageId)
        assertEquals("1:$PATH", insert.uniqueKey)
        assertEquals(listOf(record(updatedAt = 200)), plan.file.records)
    }

    @Test
    fun `远端记录无法匹配本机存储库时不落库但仍留在写回内容里`() {
        val foreign = record(base = OTHER_BASE, updatedAt = 200)
        val plan = plan(local(), remote = file(records = listOf(foreign)))

        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertEquals(listOf(foreign), plan.file.records)
    }

    @Test
    fun `本机与远端完全等价时无动作且写回内容保持远端`() {
        val plan = plan(
            local(records = listOf(entity(updatedAt = 100, position = 10))),
            remote = file(records = listOf(record(updatedAt = 100, position = 10))),
        )

        assertTrue(plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
        assertEquals(listOf(record(updatedAt = 100, position = 10)), plan.file.records)
    }

    // ==================== 不变式 ====================

    @Test
    fun `记录候选顺序不影响计划结果`() {
        val first = entity(id = 1, path = "/movies/a.mkv", updatedAt = 100)
        val second = entity(id = 2, path = "/movies/b.mkv", updatedAt = 200)

        val forward = plan(local(records = listOf(first, second)), remote = null)
        val backward = plan(local(records = listOf(second, first)), remote = null)

        assertEquals(forward.file, backward.file)
    }

    @Test
    fun `应用计划后重新计算得到空动作`() {
        val plan = plan(
            local(records = listOf(entity(id = 3, updatedAt = 100, position = 10))),
            remote = file(records = listOf(record(updatedAt = 200, position = 900))),
        )

        val after = plan(
            local(records = listOf(entity(id = 3, updatedAt = 200, position = 900))),
            remote = plan.file,
        )

        assertTrue(after.inserts.isEmpty())
        assertTrue(after.updates.isEmpty())
        assertTrue(after.deletes.isEmpty())
        assertEquals(plan.file, after.file)
    }

    @Test
    fun `两台设备各自计算得到同一份写回内容`() {
        val newer = record(updatedAt = 200, position = 900)
        val older = record(updatedAt = 100, position = 10)

        val deviceA = plan(
            local(records = listOf(entity(id = 1, updatedAt = 200, position = 900))),
            remote = file(records = listOf(older)),
        )
        val deviceB = plan(
            local(records = listOf(entity(id = 2, updatedAt = 100, position = 10))),
            remote = file(records = listOf(newer)),
        )

        assertEquals(deviceA.file, deviceB.file)
        assertEquals(listOf(newer), deviceA.file.records)
    }

    @Test
    fun `墓碑与记录同时存在时每键只留下一个胜者`() {
        val plan = plan(
            local(records = listOf(entity(id = 1, path = "/movies/a.mkv", updatedAt = 100))),
            remote = file(
                records = listOf(record(path = "/movies/a.mkv", updatedAt = 50)),
                deletes = listOf(SyncDelete(syncKey(BASE, "/movies/a.mkv"), deletedAt = 100)),
            ),
        )

        assertTrue(plan.file.records.isEmpty())
        assertEquals(1, plan.file.deletes.size)
        assertEquals(listOf(1), plan.deletes)
    }

    @Test
    fun `来自多个来源的墓碑都参与折叠`() {
        // 共享文件不含墓碑；旧协议遗留文件带来一条 —— 必须一并生效，否则已删记录会复活
        val legacy = file(deletes = listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)))
        val plan = SyncMerge.plan(
            local(records = listOf(entity(id = 9, updatedAt = 100))),
            listOf(file(records = listOf(record(updatedAt = 100))), legacy),
        )

        assertEquals(listOf(9), plan.deletes)
        assertEquals(listOf(SyncDelete(syncKey(BASE, PATH), deletedAt = 200)), plan.file.deletes)
        assertTrue(plan.file.records.isEmpty())
    }

    // ==================== fixtures ====================

    /** 单来源便捷重载：`null` 表示云端文件尚不存在。 */
    private fun plan(local: SyncMerge.LocalState, remote: PlayHistorySyncFile?): SyncMerge.MergePlan =
        SyncMerge.plan(local, listOfNotNull(remote))

    private fun local(
        records: List<PlayHistoryEntity> = emptyList(),
        tombstones: List<SyncDelete> = emptyList(),
    ) = SyncMerge.LocalState(
        records = records,
        tombstones = tombstones,
        storageIdByNormalizedBaseUrl = mapOf(normalizeBaseUrl(BASE) to STORAGE_ID),
        normalizedBaseUrlByStorageId = mapOf(STORAGE_ID to normalizeBaseUrl(BASE)),
    )

    private fun file(
        records: List<SyncRecord> = emptyList(),
        deletes: List<SyncDelete> = emptyList(),
    ) = PlayHistorySyncFile(records = records, deletes = deletes)

    private fun entity(
        id: Int = 0,
        path: String? = PATH,
        updatedAt: Long = 100,
        position: Long = 10,
    ) = PlayHistoryEntity(
        id = id,
        videoName = path?.substringAfterLast('/') ?: "a.mkv",
        url = "$BASE$PATH",
        mediaType = MediaType.WEBDAV_SERVER,
        videoPosition = position,
        videoDuration = DURATION,
        playTime = Date(updatedAt),
        uniqueKey = "$STORAGE_ID:$path",
        storagePath = path,
        storageId = STORAGE_ID,
        updatedAt = updatedAt,
    )

    private fun record(
        path: String = PATH,
        base: String = BASE,
        updatedAt: Long = 100,
        position: Long = 10,
    ) = SyncRecord(
        key = syncKey(base, path),
        baseUrl = base,
        videoName = path.substringAfterLast('/'),
        url = "$BASE$PATH",
        mediaType = MediaType.WEBDAV_SERVER.value,
        videoPosition = position,
        videoDuration = DURATION,
        playTime = updatedAt,
        httpHeader = null,
        storagePath = path,
        updatedAt = updatedAt,
    )

    private companion object {
        const val STORAGE_ID = 1
        const val BASE = "https://dav.example.com/dav"
        const val OTHER_BASE = "https://dav.example.com/other"
        const val PATH = "/movies/a.mkv"
        const val DURATION = 1_000L
    }
}
