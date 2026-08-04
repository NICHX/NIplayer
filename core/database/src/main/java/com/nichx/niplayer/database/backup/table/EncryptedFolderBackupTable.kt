package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.entity.EncryptedFolderEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

class EncryptedFolderBackupTable @Inject constructor(
    db: NiplayerDatabase,
    moshi: Moshi,
    private val dao: EncryptedFolderDao,
) : BackupTable<EncryptedFolderEntity>(
    db = db,
    moshi = moshi,
    entityClass = EncryptedFolderEntity::class.java,
    displayName = "加密文件夹",
) {
    override val key = "encryptedFolders"

    override suspend fun queryAll() = dao.getAll()

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun insertAll(rows: List<EncryptedFolderEntity>) = rows.forEach { dao.insert(it) }
}
