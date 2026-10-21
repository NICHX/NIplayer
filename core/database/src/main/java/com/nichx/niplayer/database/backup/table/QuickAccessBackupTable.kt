package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.common.backup.RestoreContext
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

/**
 * 快速访问备份项。
 *
 * 业务身份：`本机存储源 id | storagePath`（对应 unique 索引）。
 * 备份里的 libraryId 先经 [RestoreContext] 映射成本机 id，再参与对账。
 */
class QuickAccessBackupTable @Inject constructor(
    moshi: Moshi,
    private val dao: QuickAccessDao,
) : BackupTable<QuickAccessEntity>(
    moshi = moshi,
    entityClass = QuickAccessEntity::class.java,
    displayName = "快速访问",
) {
    override val key = "quickAccesses"

    /** 依赖存储源已恢复并登记 id 映射。 */
    override val restoreOrder = 30

    override suspend fun queryAll() = dao.getAll()

    override suspend fun insert(row: QuickAccessEntity): Int {
        dao.insert(row)
        return 0
    }

    override suspend fun delete(row: QuickAccessEntity) = dao.delete(row.id)

    override fun identity(row: QuickAccessEntity): String =
        "${row.libraryId}|${row.storagePath}"

    override fun idOf(row: QuickAccessEntity): Int = row.id

    override fun withLocalId(row: QuickAccessEntity, localId: Int): QuickAccessEntity =
        row.copy(id = localId)

    override fun remap(row: QuickAccessEntity, ctx: RestoreContext): QuickAccessEntity? {
        val localId = ctx.mapLibraryId(row.libraryId)
        if (localId == null) {
            ctx.warn("快速访问「${row.name}」：存储源 #${row.libraryId} 不在备份中，已跳过")
            return null
        }
        return row.copy(libraryId = localId)
    }
}
