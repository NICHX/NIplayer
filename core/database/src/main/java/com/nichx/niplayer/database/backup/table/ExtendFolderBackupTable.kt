package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.entity.ExtendFolderEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

/**
 * 扩展目录备份项。
 *
 * 业务身份就是 `folderPath`（表主键），无需 id 映射。
 */
class ExtendFolderBackupTable @Inject constructor(
    moshi: Moshi,
    private val dao: ExtendFolderDao,
) : BackupTable<ExtendFolderEntity>(
    moshi = moshi,
    entityClass = ExtendFolderEntity::class.java,
    displayName = "扩展目录",
) {
    override val key = "extendFolders"

    override val restoreOrder = 10

    override suspend fun queryAll(): List<ExtendFolderEntity> = dao.getAll()

    override suspend fun insert(row: ExtendFolderEntity): Int {
        dao.insert(row)
        return 0
    }

    override suspend fun delete(row: ExtendFolderEntity) = dao.delete(row.folderPath)

    override fun identity(row: ExtendFolderEntity): String = row.folderPath
}
