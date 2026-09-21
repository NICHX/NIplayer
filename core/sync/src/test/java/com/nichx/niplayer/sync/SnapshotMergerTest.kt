package com.nichx.niplayer.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [SnapshotMerger] 的回归测试。
 *
 * 对应缺陷审计报告 #9：`mergeSnapshot` 对记录是无条件覆盖（不比较 `updatedAt`），
 * 而墓碑分支却比较了 `deletedAt` —— 这种不对称使得过期的 delta 一旦被叠加到新 base 上
 * （典型场景：压缩时旧 delta 删除失败而残留），旧记录会覆盖新记录，造成播放进度回滚。
 */
class SnapshotMergerTest {

    // ==================== 空增量 ====================

    @Test
    fun `delta 为 null 时原样返回 base`() {
        val base = file(updatedAt = 100, records = listOf(record("a", position = 10, updatedAt = 100)))
        assertSame(base, SnapshotMerger.merge(base, null))
    }

    @Test
    fun `delta 为空增量时原样返回 base`() {
        val base = file(updatedAt = 100, records = listOf(record("a", position = 10, updatedAt = 100)))
        val emptyDelta = file(deviceId = "other", updatedAt = 999)
        assertSame(base, SnapshotMerger.merge(base, emptyDelta))
    }

    // ==================== 记录合并（#9 回归点） ====================

    @Test
    fun `delta 中较新的记录覆盖 base`() {
        val base = file(records = listOf(record("a", position = 10, updatedAt = 100)))
        val delta = file(deviceId = "b", records = listOf(record("a", position = 55, updatedAt = 200)))

        val merged = SnapshotMerger.merge(base, delta)

        assertEquals(1, merged.records.size)
        assertEquals(55L, merged.records.first().videoPosition)
        assertEquals(200L, merged.records.first().updatedAt)
    }

    @Test
    fun `delta 中较旧的记录不得覆盖 base`() {
        // 回归 #9：过期 delta 不得把新记录改回旧值
        val base = file(records = listOf(record("a", position = 55, updatedAt = 200)))
        val staleDelta = file(deviceId = "b", records = listOf(record("a", position = 10, updatedAt = 100)))

        val merged = SnapshotMerger.merge(base, staleDelta)

        assertEquals(1, merged.records.size)
        assertEquals(55L, merged.records.first().videoPosition)
        assertEquals(200L, merged.records.first().updatedAt)
    }

    @Test
    fun `updatedAt 相同时以 delta 为准`() {
        val base = file(records = listOf(record("a", position = 10, updatedAt = 100)))
        val delta = file(deviceId = "b", records = listOf(record("a", position = 20, updatedAt = 100)))

        val merged = SnapshotMerger.merge(base, delta)

        assertEquals(20L, merged.records.first().videoPosition)
    }

    @Test
    fun `delta 中的新记录被并入 base`() {
        val base = file(records = listOf(record("a", position = 10, updatedAt = 100)))
        val delta = file(deviceId = "b", records = listOf(record("b", position = 30, updatedAt = 150)))

        val merged = SnapshotMerger.merge(base, delta)

        assertEquals(2, merged.records.size)
        assertEquals(listOf("a", "b"), merged.records.map { it.key })
    }

    // ==================== 墓碑合并 ====================

    @Test
    fun `墓碑按 deletedAt 取新`() {
        val base = file(deletes = listOf(SyncDelete(key = "a", deletedAt = 100)))
        val delta = file(deviceId = "b", deletes = listOf(SyncDelete(key = "a", deletedAt = 300)))

        val merged = SnapshotMerger.merge(base, delta)

        assertEquals(300L, merged.deletes.single().deletedAt)
    }

    @Test
    fun `较早的墓碑不得覆盖较新的墓碑`() {
        val base = file(deletes = listOf(SyncDelete(key = "a", deletedAt = 300)))
        val staleDelta = file(deviceId = "b", deletes = listOf(SyncDelete(key = "a", deletedAt = 100)))

        val merged = SnapshotMerger.merge(base, staleDelta)

        assertEquals(300L, merged.deletes.single().deletedAt)
    }

    // ==================== 游标与时间戳 ====================

    @Test
    fun `updatedAt 取 base 与 delta 的较大值`() {
        // delta 必须带内容，否则会走「空增量直接返回 base」的短路，测不到 maxOf 分支
        val base = file(updatedAt = 500)
        val delta = file(
            deviceId = "b",
            updatedAt = 200,
            records = listOf(record("a", position = 1, updatedAt = 10)),
        )

        assertEquals(500L, SnapshotMerger.merge(base, delta).updatedAt)
    }

    @Test
    fun `updatedAt 会纳入 delta 内记录的最大时间戳`() {
        val base = file(updatedAt = 100)
        val delta = file(
            deviceId = "b",
            updatedAt = 100,
            records = listOf(record("a", position = 1, updatedAt = 900)),
        )

        assertEquals(900L, SnapshotMerger.merge(base, delta).updatedAt)
    }

    @Test
    fun `lastSyncedAt 取较大值`() {
        val base = file(lastSyncedAt = 1000)
        val delta = file(
            deviceId = "b",
            lastSyncedAt = 4000,
            records = listOf(record("a", position = 1, updatedAt = 10)),
        )

        assertEquals(4000L, SnapshotMerger.merge(base, delta).lastSyncedAt)
    }

    @Test
    fun `空增量不推进 base 的 lastSyncedAt`() {
        // 短路行为：空 delta 不含任何信息，原样返回 base
        val base = file(lastSyncedAt = 1000)
        val emptyDelta = file(deviceId = "b", lastSyncedAt = 4000)

        assertEquals(1000L, SnapshotMerger.merge(base, emptyDelta).lastSyncedAt)
    }

    @Test
    fun `合并结果按 key 排序`() {
        val base = file(records = listOf(record("c", 1, 10), record("a", 1, 10)))
        val delta = file(deviceId = "b", records = listOf(record("b", 1, 20)))

        val merged = SnapshotMerger.merge(base, delta)

        assertEquals(listOf("a", "b", "c"), merged.records.map { it.key })
    }

    // ==================== fixtures ====================

    private fun file(
        deviceId: String = "device-a",
        updatedAt: Long = 0,
        records: List<SyncRecord> = emptyList(),
        deletes: List<SyncDelete> = emptyList(),
        lastSyncedAt: Long = 0,
    ) = PlayHistorySyncFile(
        deviceId = deviceId,
        updatedAt = updatedAt,
        records = records,
        deletes = deletes,
        lastSyncedAt = lastSyncedAt,
    )

    private fun record(key: String, position: Long, updatedAt: Long) = SyncRecord(
        key = key,
        baseUrl = "http://host/dav",
        uniqueKey = "uk-$key",
        storageId = 1,
        videoName = "$key.mkv",
        url = "http://host/dav/Movies/$key.mkv",
        mediaType = "video",
        videoPosition = position,
        videoDuration = 3_600_000L,
        playTime = updatedAt,
        httpHeader = null,
        storagePath = "Movies/$key.mkv",
        updatedAt = updatedAt,
    )
}
