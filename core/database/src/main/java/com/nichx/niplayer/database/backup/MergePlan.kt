package com.nichx.niplayer.database.backup

import com.nichx.niplayer.common.backup.RestoreMode

/**
 * 单条对账动作。
 *
 * @param backupRow 备份中的原始行（用于登记 id 映射；其中 id 是源设备的）
 * @param localRow 准备落库的行（insert 时为 id=0；update 时已带上本机 id）
 * @param localId 目标行在本机的 id；insert 时未知，先置 0
 */
internal data class MergeAction<E>(
    val backupRow: E,
    val localRow: E,
    val localId: Int,
)

/**
 * 对账计划：按业务身份把备份行分成「新增 / 更新 / 删除」三类。
 *
 * 这是恢复正确性的核心：**身份来自业务自然键，而不是自增 id**。
 * 因此跨设备恢复不会因为 id 相同而覆盖无关数据，也不会因为 id 不同而重复插入。
 */
internal data class MergePlan<E>(
    val inserts: List<MergeAction<E>>,
    val updates: List<MergeAction<E>>,
    val deletes: List<E>,
)

/**
 * 生成对账计划（纯函数，无 IO，可单测）。
 *
 * - [RestoreMode.MERGE]：只增改，不删本机独有数据；
 * - [RestoreMode.REPLACE]：增改后删除备份中不存在的本机行，实现「镜像到备份状态」，
 *   同时**保留本机已有行的 id**，避免播放历史等未备份数据失去引用。
 *
 * @param identity 业务身份（必须稳定且跨设备一致，如 `url|mediaType`）
 * @param idOf 读取本机行 id；无自增 id 的表返回 0
 * @param withLocalId 把本机 id 写到待落库行（无 id 的表原样返回）
 * @param onDuplicate 备份内部出现重复业务键时回调（保留首条）
 */
internal fun <E> planMerge(
    backupRows: List<E>,
    localRows: List<E>,
    mode: RestoreMode,
    identity: (E) -> String,
    idOf: (E) -> Int,
    withLocalId: (E, Int) -> E,
    onDuplicate: (String) -> Unit = {},
): MergePlan<E> {
    // 本机行按业务键索引；被匹配到的会从 map 移除，剩下的就是「本机独有」
    val unmatchedLocal = localRows.associateBy(identity).toMutableMap()
    val inserts = mutableListOf<MergeAction<E>>()
    val updates = mutableListOf<MergeAction<E>>()
    val seen = mutableSetOf<String>()

    for (backup in backupRows) {
        val key = identity(backup)
        if (!seen.add(key)) {
            onDuplicate(key)
            continue
        }
        val existing = unmatchedLocal.remove(key)
        if (existing == null) {
            inserts += MergeAction(
                backupRow = backup,
                localRow = withLocalId(backup, 0),
                localId = 0,
            )
        } else {
            val localId = idOf(existing)
            updates += MergeAction(
                backupRow = backup,
                localRow = withLocalId(backup, localId),
                localId = localId,
            )
        }
    }

    val deletes = if (mode == RestoreMode.REPLACE) unmatchedLocal.values.toList() else emptyList()
    return MergePlan(inserts, updates, deletes)
}
