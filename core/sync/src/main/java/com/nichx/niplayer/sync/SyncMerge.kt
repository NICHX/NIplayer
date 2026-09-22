package com.nichx.niplayer.sync

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.normalizeBaseUrl
import java.util.Date

/**
 * 播放历史同步的**合并判定**（纯函数，无 IO / 无 Android 依赖，便于单元测试）。
 *
 * ## 单一规则
 *
 * 候选只有两种：**记录**（某个 key 在某时刻的内容）与**墓碑**（某个 key 在某时刻被删除）。
 * 两者用同一个[全序][Order]比较，同时间戳时墓碑胜出。于是"谁赢谁输"只剩一次 max
 * 折叠，"墓碑是否杀记录""墓碑之后的重播是否放行""墓碑是否已被更新的记录覆盖"全部
 * 由这一次比较导出，不再需要各自的分支。
 *
 * ## 为什么平局必须两端一致
 *
 * 排序键全部由内容派生（不含设备标识），因此**任意两台设备对同一对候选算出同一胜者**。
 * 若平局判定不对称（例如"相等则本地赢"），两端会各自判定自己赢，永远无法收敛。
 *
 * ## 输出
 *
 * [MergePlan.file] 是**折叠后的完整胜者集合**，即云端要写入的内容。它包含本机匹配不到
 * 存储库、因而落不了本地 DB 的外来记录 —— 单文件协议下这些记录必须原样保留，否则一台
 * 没配置某存储库的设备一同步就会把它们抹掉。
 */
internal object SyncMerge {

    /** 候选类型权重：同一 timestamp 下墓碑（1）胜过记录（0）。 */
    private const val KIND_RECORD = 0
    private const val KIND_TOMBSTONE = 1

    /**
     * 合并中的全序排序键。
     *
     * 先比 [timestamp]（记录为 `updated_at`，墓碑为 `deleted_at`），再比 [kindRank]
     * （同时间戳时墓碑胜出，等价于旧实现的 `updatedAt <= deletedAt` 判删），最后按
     * [videoPosition] / [playTime] / [url] 依次打破记录的平局 —— 这些字段两端都持有且
     * 内容相同，因此全序在两端一致。
     */
    data class Order(
        val timestamp: Long,
        val kindRank: Int,
        val videoPosition: Long,
        val playTime: Long,
        val url: String,
    ) : Comparable<Order> {
        override fun compareTo(other: Order): Int {
            timestamp.compareTo(other.timestamp).let { if (it != 0) return it }
            kindRank.compareTo(other.kindRank).let { if (it != 0) return it }
            videoPosition.compareTo(other.videoPosition).let { if (it != 0) return it }
            playTime.compareTo(other.playTime).let { if (it != 0) return it }
            return url.compareTo(other.url)
        }
    }

    /** 构造记录候选的排序键。 */
    fun recordOrder(timestamp: Long, videoPosition: Long, playTime: Long, url: String): Order =
        Order(timestamp, KIND_RECORD, videoPosition, playTime, url)

    /** 构造墓碑候选的排序键。 */
    fun tombstoneOrder(deletedAt: Long): Order =
        Order(deletedAt, KIND_TOMBSTONE, 0L, 0L, "")

    /** 本地实体的排序键。 */
    fun orderOf(entity: PlayHistoryEntity): Order =
        recordOrder(entity.updatedAt, entity.videoPosition, entity.playTime.time, entity.url)

    /** 云端记录的排序键。 */
    fun orderOf(record: SyncRecord): Order =
        recordOrder(record.updatedAt, record.videoPosition, record.playTime, record.url)

    /**
     * 用远端记录覆盖本地记录的可变字段。
     *
     * `videoName` / `url` / `mediaType` / `uniqueKey` 是记录标识（跨设备不变），不覆盖。
     */
    fun applyRemote(entity: PlayHistoryEntity, record: SyncRecord) {
        entity.videoPosition = record.videoPosition
        entity.videoDuration = record.videoDuration
        entity.playTime = Date(record.playTime)
        entity.httpHeader = record.httpHeader
        entity.updatedAt = record.updatedAt
    }

    /** 本机侧的全部候选与存储库映射。 */
    data class LocalState(
        val records: List<PlayHistoryEntity>,
        val tombstones: List<SyncDelete>,
        /** 归一化存储地址 → 本机存储库 id。 */
        val storageIdByNormalizedBaseUrl: Map<String, Int>,
        /** 本机存储库 id → 归一化存储地址。 */
        val normalizedBaseUrlByStorageId: Map<Int, String>,
    )

    /** 按远端记录在本机新建（存储库已匹配）。 */
    data class Insert(val record: SyncRecord, val storageId: Int, val uniqueKey: String)

    /** 用远端记录覆盖本机记录。 */
    data class Update(val entity: PlayHistoryEntity, val record: SyncRecord)

    /** 一次同步的完整计划。 */
    data class MergePlan(
        /** 要落库的新建记录。 */
        val inserts: List<Insert> = emptyList(),
        /** 要落库的更新记录。 */
        val updates: List<Update> = emptyList(),
        /** 要删除的本机主键。 */
        val deletes: List<Int> = emptyList(),
        /** 要写回云端的完整胜者集合。 */
        val file: PlayHistorySyncFile = PlayHistorySyncFile(),
    )

    /**
     * 计算一次同步的计划。
     *
     * @param remotes 全部远端候选来源：云端共享文件（不存在时为空）+ 旧协议遗留文件（只取墓碑）。
     *   **读取失败必须由调用方处理成异常，而不是省略该来源** —— 否则那些墓碑会从写回内容里消失。
     */
    fun plan(local: LocalState, remotes: List<PlayHistorySyncFile>): MergePlan {
        val localEntries = localEntries(local)
        val localTombstones = local.tombstones.associateBy { it.key }
        val remoteRecords = remoteRecordsByKey(remotes)
        val remoteTombstones = remoteTombstonesByKey(remotes)

        val inserts = ArrayList<Insert>()
        val updates = ArrayList<Update>()
        val deletes = ArrayList<Int>()
        val records = ArrayList<SyncRecord>()
        val tombstones = ArrayList<SyncDelete>()

        val keys = unionKeys(localEntries, localTombstones, remoteRecords, remoteTombstones)
        for (key in keys) {
            val localEntry = localEntries[key]
            val remoteRecord = remoteRecords[key]
            val deletedAt = maxDeletedAt(localTombstones[key], remoteTombstones[key])

            when (val winner = winner(localEntry?.record, remoteRecord, deletedAt)) {
                null -> Unit

                is Winner.Deleted -> {
                    tombstones += SyncDelete(key, winner.deletedAt)
                    localEntry?.let { deletes += it.entity.id }
                }

                is Winner.Kept -> {
                    records += winner.record
                    remoteAction(localEntry, remoteRecord, local)?.let { action ->
                        action.insert?.let { inserts += it }
                        action.update?.let { updates += it }
                    }
                }
            }
        }

        return MergePlan(
            inserts = inserts,
            updates = updates,
            deletes = deletes,
            file = PlayHistorySyncFile(
                records = records.sortedBy { it.key },
                deletes = tombstones.sortedBy { it.key },
            ),
        )
    }

    /**
     * 记录胜出时，远端记录需要对本机 DB 做什么。
     *
     * 远端记录不新于本机记录（含完全等价）时返回 null —— 平局不写库，避免两端反复互为更新。
     */
    private fun remoteAction(
        localEntry: LocalEntry?,
        remoteRecord: SyncRecord?,
        local: LocalState,
    ): RemoteAction? {
        if (remoteRecord == null) return null
        val localOrder = localEntry?.record?.let { orderOf(it) }
        if (localOrder != null && orderOf(remoteRecord) <= localOrder) return null

        val entity = localEntry?.entity
        return if (entity == null) {
            RemoteAction(insert = matchedInsert(remoteRecord, local))
        } else {
            RemoteAction(update = Update(entity, remoteRecord))
        }
    }

    /**
     * 判定某个 key 的胜者；两侧都没有可用内容时返回 null。
     *
     * 墓碑与记录共用同一全序，因此"删除 vs 重播"由一次比较决定：墓碑时间戳不早于任何记录
     * 候选时删记录，否则记录（重播）胜出并保留。
     */
    private fun winner(local: SyncRecord?, remote: SyncRecord?, deletedAt: Long?): Winner? {
        val recordOrder = maxOrder(local?.let { orderOf(it) }, remote?.let { orderOf(it) })
        if (deletedAt != null) {
            val tombOrder = tombstoneOrder(deletedAt)
            if (recordOrder == null || tombOrder >= recordOrder) return Winner.Deleted(deletedAt)
        }
        return preferredRecord(local, remote)?.let { Winner.Kept(it) }
    }

    /**
     * 记录胜出时选用哪一侧的内容。
     *
     * 远端不旧于本地时选远端（含完全等价）—— 两端因此写出同一份文件，稳态下不再产生写请求。
     */
    private fun preferredRecord(local: SyncRecord?, remote: SyncRecord?): SyncRecord? {
        val localOrder = local?.let { orderOf(it) } ?: return remote
        val remoteOrder = remote?.let { orderOf(it) } ?: return local
        return if (remoteOrder >= localOrder) remote else local
    }

    /** 远端记录按 `baseUrl` 匹配本机存储库，用于在本机新建。 */
    private fun matchedInsert(record: SyncRecord, local: LocalState): Insert? {
        val normalized = record.baseUrl?.let { normalizeBaseUrl(it) } ?: return null
        val storageId = local.storageIdByNormalizedBaseUrl[normalized] ?: return null
        val path = record.storagePath ?: return null
        return Insert(record, storageId, "$storageId:$path")
    }

    /** 汇总全部远端来源的记录候选，同一 key 取最新的一条。 */
    private fun remoteRecordsByKey(remotes: List<PlayHistorySyncFile>): Map<String, SyncRecord> {
        val records = LinkedHashMap<String, SyncRecord>()
        for (source in remotes) {
            for (record in source.records) {
                val existing = records[record.key]
                if (existing == null || orderOf(record) > orderOf(existing)) records[record.key] = record
            }
        }
        return records
    }

    /** 汇总全部远端来源的墓碑候选，同一 key 取删除时间最晚的一条。 */
    private fun remoteTombstonesByKey(remotes: List<PlayHistorySyncFile>): Map<String, SyncDelete> {
        val tombstones = LinkedHashMap<String, SyncDelete>()
        for (source in remotes) {
            for (delete in source.deletes) {
                val existing = tombstones[delete.key]
                if (existing == null || delete.deletedAt > existing.deletedAt) tombstones[delete.key] = delete
            }
        }
        return tombstones
    }

    /** 本机记录 → 云端记录投影，并按其同步键索引；无法投影的（缺 storageId/path/地址）跳过。 */
    private fun localEntries(local: LocalState): Map<String, LocalEntry> {
        val entries = LinkedHashMap<String, LocalEntry>()
        for (entity in local.records) {
            val storageId = entity.storageId ?: continue
            val baseUrl = local.normalizedBaseUrlByStorageId[storageId] ?: continue
            val record = entity.toSyncRecord(baseUrl) ?: continue
            entries[record.key] = LocalEntry(entity, record)
        }
        return entries
    }

    private fun maxOrder(first: Order?, second: Order?): Order? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private fun maxDeletedAt(local: SyncDelete?, remote: SyncDelete?): Long? = when {
        local == null -> remote?.deletedAt
        remote == null -> local.deletedAt
        else -> maxOf(local.deletedAt, remote.deletedAt)
    }

    private fun unionKeys(vararg sources: Map<String, *>): Set<String> {
        val keys = LinkedHashSet<String>()
        sources.forEach { keys += it.keys }
        return keys
    }

    /** 本机记录与其云端投影。 */
    private data class LocalEntry(val entity: PlayHistoryEntity, val record: SyncRecord)

    /** 远端记录要落库的动作；两者互斥且都可为 null（本机没有对应存储库时不落库）。 */
    private data class RemoteAction(val insert: Insert? = null, val update: Update? = null)

    /** 某个 key 的判定结果。 */
    private sealed interface Winner {
        data class Kept(val record: SyncRecord) : Winner
        data class Deleted(val deletedAt: Long) : Winner
    }
}
