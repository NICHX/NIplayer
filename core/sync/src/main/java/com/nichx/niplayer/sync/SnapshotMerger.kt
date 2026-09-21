package com.nichx.niplayer.sync

/**
 * 云端快照合并（纯函数，无 IO、无 Android 依赖，便于单元测试）。
 *
 * 从 [PlayHistorySyncManager] 抽出：合并规则原本是该类的私有成员，而宿主类依赖 `Context` +
 * 5 个 DAO，导致这条数据丢失关键路径无法被单元测试覆盖。抽出后可用纯 JVM 测试锁定
 * 「过期 delta 不得覆盖新记录」这一不变式。
 */
internal object SnapshotMerger {

    /**
     * 把 delta（记录 upsert + 新增墓碑）合并进 base，还原为完整快照。
     *
     * 合并规则统一为**按时间取新**：记录比 [SyncRecord.updatedAt]，墓碑比 [SyncDelete.deletedAt]。
     *
     * 原实现对记录是无条件 `records[key] = it` 覆盖，一旦 delta 相对 base 已过期（典型场景：
     * 压缩时旧 delta 删除失败、残留 delta 被反复叠加到新 base 上）就会用旧记录覆盖新记录，
     * 造成播放进度回滚。现与墓碑分支保持对称。
     *
     * @param base 设备全量快照
     * @param delta 增量快照；null 或空增量时原样返回 [base]
     */
    fun merge(base: PlayHistorySyncFile, delta: PlayHistorySyncFile?): PlayHistorySyncFile {
        if (delta == null || (delta.records.isEmpty() && delta.deletes.isEmpty())) return base

        val records = base.records.associateBy { it.key }.toMutableMap()
        delta.records.forEach { r ->
            val old = records[r.key]
            // 时间戳相同时取 delta（同一逻辑轮次内 delta 视为较后写入）
            if (old == null || r.updatedAt >= old.updatedAt) records[r.key] = r
        }

        val deletes = base.deletes.associateBy { it.key }.toMutableMap()
        delta.deletes.forEach { d ->
            val old = deletes[d.key]
            if (old == null || d.deletedAt > old.deletedAt) deletes[d.key] = d
        }

        val deltaUpdatedAt = maxOf(
            delta.updatedAt,
            delta.records.maxOfOrNull { it.updatedAt } ?: 0,
            delta.deletes.maxOfOrNull { it.deletedAt } ?: 0,
        )
        return base.copy(
            records = records.values.sortedBy { it.key },
            deletes = deletes.values.sortedBy { it.key },
            updatedAt = maxOf(base.updatedAt, deltaUpdatedAt),
            lastSyncedAt = maxOf(base.lastSyncedAt, delta.lastSyncedAt),
        )
    }
}
