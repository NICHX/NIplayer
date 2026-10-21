package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.common.backup.RestoreContext
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.entity.EncryptedFolderEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

/**
 * 加密文件夹备份项。
 *
 * 业务身份：`本机存储源 id | folderPath`（对应 unique 索引）。
 * 备份里的 storageId 先经 [RestoreContext] 映射成本机 id，避免跨设备指错存储源。
 */
class EncryptedFolderBackupTable @Inject constructor(
    moshi: Moshi,
    private val dao: EncryptedFolderDao,
) : BackupTable<EncryptedFolderEntity>(
    moshi = moshi,
    entityClass = EncryptedFolderEntity::class.java,
    displayName = "加密文件夹",
) {
    override val key = "encryptedFolders"

    override val restoreOrder = 20

    override suspend fun queryAll() = dao.getAll()

    override suspend fun insert(row: EncryptedFolderEntity): Int = dao.insert(row).toInt()

    override suspend fun delete(row: EncryptedFolderEntity) = dao.delete(row.id)

    override fun identity(row: EncryptedFolderEntity): String =
        "${row.storageId}|${row.folderPath}"

    override fun idOf(row: EncryptedFolderEntity): Int = row.id

    override fun withLocalId(row: EncryptedFolderEntity, localId: Int): EncryptedFolderEntity =
        row.copy(id = localId)

    override fun remap(row: EncryptedFolderEntity, ctx: RestoreContext): EncryptedFolderEntity? {
        val localId = ctx.mapLibraryId(row.storageId)
        if (localId == null) {
            ctx.warn("加密文件夹「${row.folderPath}」：存储源 #${row.storageId} 不在备份中，已跳过")
            return null
        }
        return row.copy(storageId = localId)
    }
}
