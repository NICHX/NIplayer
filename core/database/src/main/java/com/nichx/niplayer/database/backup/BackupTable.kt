package com.nichx.niplayer.database.backup

import com.nichx.niplayer.common.backup.DatabaseBackupItem
import com.nichx.niplayer.common.backup.RestoreContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * 数据库表备份项泛型基类。
 *
 * 恢复语义为「按业务身份对账」而非「按自增 id 覆盖」：
 * 子类提供 [identity] / [insert] / [delete] 三个核心方法，即可获得跨设备安全的合并能力。
 *
 * - **MERGE**：备份与唯一业务键相同的本机行 → 用本机 id 更新；不同 → 新增；本机独有保留
 * - **REPLACE**：在 MERGE 基础上删除备份中不存在的本机行（镜像语义），但保留本机 id
 *
 * **事务边界**：本类不再自行开启事务，由 BackupManager 把所有表放进同一个 Room 事务，
 * 保证「全部成功或全部回滚」。
 *
 * @param E Entity 类型，需为 @JsonClass(generateAdapter = true)
 */
abstract class BackupTable<E : Any>(
    private val moshi: Moshi,
    private val entityClass: Class<E>,
    /** 表的显示名，用于摘要与告警（如 "存储源"、"快速访问"）。 */
    private val displayName: String,
) : DatabaseBackupItem {

    /** 全量查询，用于导出快照与对账。 */
    protected abstract suspend fun queryAll(): List<E>

    /**
     * 按 REPLACE 策略写入一行，返回本机行 id（无自增 id 的表返回 0）。
     *
     * 更新已有行时，[row] 已带有本机 id，插入后 id 保持不变。
     */
    protected abstract suspend fun insert(row: E): Int

    /** 删除本机行（REPLACE 镜像模式清理备份中不存在的项）。 */
    protected abstract suspend fun delete(row: E)

    /** 业务身份：必须稳定、跨设备一致（如 `url|mediaType`、`libraryId|path`）。 */
    protected abstract fun identity(row: E): String

    /** 本机行 id；无自增 id 的表返回 0。 */
    protected open fun idOf(row: E): Int = 0

    /** 把本机 id 写到待落库行；无自增 id 的表原样返回。 */
    protected open fun withLocalId(row: E, localId: Int): E = row

    /**
     * 依赖项解析：把备份中的引用 id（如 libraryId）换成本机 id。
     * 返回 null 表示引用目标不存在，本行会被跳过并记录告警。
     */
    protected open fun remap(row: E, ctx: RestoreContext): E? = row

    /** 落库后回调（存储源用它登记 `备份id → 本机id` 映射）。 */
    protected open fun onRestored(backupRow: E, localId: Int, ctx: RestoreContext) = Unit

    private val adapter by lazy {
        moshi.adapter<List<E>>(
            Types.newParameterizedType(List::class.java, entityClass),
        )
    }

    final override suspend fun snapshot(): Any? {
        val rows = queryAll()
        if (rows.isEmpty()) return null
        return adapter.toJsonValue(rows)
    }

    final override fun validate(data: Any?) {
        if (data != null) decode(data)
    }

    final override suspend fun restore(data: Any?, ctx: RestoreContext) {
        val rows = decode(data) ?: return
        if (rows.isEmpty()) return

        // 先解析引用（库 id 映射），再按业务键对账
        val remapped = rows.mapNotNull { remap(it, ctx) }
        if (remapped.isEmpty()) return

        val plan = planMerge(
            backupRows = remapped,
            localRows = queryAll(),
            mode = ctx.mode,
            identity = ::identity,
            idOf = ::idOf,
            withLocalId = ::withLocalId,
        ) { key ->
            ctx.warn("$displayName: 备份内存在重复项（$key），已跳过")
        }

        for (action in plan.inserts) {
            val localId = insert(action.localRow)
            onRestored(action.backupRow, localId, ctx)
        }
        for (action in plan.updates) {
            insert(action.localRow)
            onRestored(action.backupRow, action.localId, ctx)
        }
        for (row in plan.deletes) {
            delete(row)
        }
    }

    final override fun describe(data: Any?): String? {
        val rows = decode(data) ?: return null
        if (rows.isEmpty()) return null
        return "$displayName: ${rows.size} 条"
    }

    private fun decode(data: Any?): List<E>? {
        if (data == null) return null
        return adapter.fromJsonValue(data)
    }
}
