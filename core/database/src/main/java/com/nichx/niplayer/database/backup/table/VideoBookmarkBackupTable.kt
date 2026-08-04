package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.VideoBookmarkDao
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.squareup.moshi.Moshi
import javax.inject.Inject

class VideoBookmarkBackupTable @Inject constructor(
    db: NiplayerDatabase,
    moshi: Moshi,
    private val dao: VideoBookmarkDao,
) : BackupTable<VideoBookmarkEntity>(
    db = db,
    moshi = moshi,
    entityClass = VideoBookmarkEntity::class.java,
    displayName = "视频书签",
) {
    override val key = "videoBookmarks"

    override suspend fun queryAll() = dao.getAll()

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun insertAll(rows: List<VideoBookmarkEntity>) = dao.insertAll(rows)
}
