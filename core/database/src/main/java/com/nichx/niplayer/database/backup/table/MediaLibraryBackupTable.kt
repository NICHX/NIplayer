package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

class MediaLibraryBackupTable @Inject constructor(
    db: NiplayerDatabase,
    moshi: Moshi,
    private val dao: MediaLibraryDao,
) : BackupTable<MediaLibraryEntity>(
    db = db,
    moshi = moshi,
    entityClass = MediaLibraryEntity::class.java,
    displayName = "存储源",
) {
    override val key = "mediaLibraries"

    override suspend fun queryAll() = dao.getAllSuspend()

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun insertAll(rows: List<MediaLibraryEntity>) = dao.insertAll(rows)
}
