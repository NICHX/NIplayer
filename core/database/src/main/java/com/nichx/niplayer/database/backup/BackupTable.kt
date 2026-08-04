package com.nichx.niplayer.database.backup

import androidx.room.withTransaction
import com.nichx.niplayer.database.NiplayerDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * 数据库表备份项泛型基类。
 *
 * 子类只需提供 [queryAll] / [clearAll] / [insertAll] 三个方法委托给具体 Dao，
 * 即可获得导出/恢复能力。新增一张表只需新建一个子类 + Hilt @IntoSet 注册，
 * 无需改动 BackupManager，也无需让 Dao 依赖 backup 包。
 *
 * 恢复模式：
 * - REPLACE：deleteAll 后 insertAll（还原语义）
 * - MERGE：直接 insertAll（依赖 REPLACE 策略按主键覆盖，本机独有的保留）
 *
 * @param E Entity 类型，需为 @JsonClass(generateAdapter = true)
 */
abstract class BackupTable<E : Any>(
    private val db: NiplayerDatabase,
    private val moshi: Moshi,
    private val entityClass: Class<E>,
    /** 表的显示名，用于摘要（如 "存储源"、"快速访问"）。 */
    private val displayName: String,
) : BackupItem {

    /** 全量查询，用于导出快照。 */
    protected abstract suspend fun queryAll(): List<E>

    /** 清空全表，用于 REPLACE 模式恢复前清库。 */
    protected abstract suspend fun clearAll()

    /** 批量插入（REPLACE 策略），用于恢复导入。 */
    protected abstract suspend fun insertAll(rows: List<E>)

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

    final override suspend fun restore(data: Any?, mode: RestoreMode) {
        if (data == null) return
        @Suppress("UNCHECKED_CAST")
        val rows = adapter.fromJsonValue(data) ?: return
        if (rows.isEmpty()) return
        db.withTransaction {
            if (mode == RestoreMode.REPLACE) clearAll()
            insertAll(rows)
        }
    }

    final override fun describe(data: Any?): String? {
        if (data == null) return null
        @Suppress("UNCHECKED_CAST")
        val rows = adapter.fromJsonValue(data) ?: return null
        if (rows.isEmpty()) return null
        return "$displayName: ${rows.size} 条"
    }
}
