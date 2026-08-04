package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.entity.ExtendFolderEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

class ExtendFolderBackupTable @Inject constructor(
    db: NiplayerDatabase,
    moshi: Moshi,
    private val dao: ExtendFolderDao,
) : BackupTable<ExtendFolderEntity>(
    db = db,
    moshi = moshi,
    entityClass = ExtendFolderEntity::class.java,
    displayName = "扩展目录",
) {
    override val key = "extendFolders"

    override suspend fun queryAll(): List<ExtendFolderEntity> = dao.getAll()

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun insertAll(rows: List<ExtendFolderEntity>) = dao.insert(*rows.toTypedArray())
}
