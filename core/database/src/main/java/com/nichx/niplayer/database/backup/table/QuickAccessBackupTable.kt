package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

class QuickAccessBackupTable @Inject constructor(
    db: NiplayerDatabase,
    moshi: Moshi,
    private val dao: QuickAccessDao,
) : BackupTable<QuickAccessEntity>(
    db = db,
    moshi = moshi,
    entityClass = QuickAccessEntity::class.java,
    displayName = "快速访问",
) {
    override val key = "quickAccesses"

    override suspend fun queryAll() = dao.getAll()

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun insertAll(rows: List<QuickAccessEntity>) = dao.insertAll(rows)
}
