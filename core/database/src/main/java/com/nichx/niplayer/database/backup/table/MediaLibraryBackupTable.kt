package com.nichx.niplayer.database.backup.table

import com.nichx.niplayer.common.backup.RestoreContext
import com.nichx.niplayer.database.backup.BackupTable
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.normalizeBaseUrl
import com.nichx.niplayer.database.sync.PlayHistorySyncDeleter
import com.squareup.moshi.Moshi
import javax.inject.Inject

/**
 * 存储源备份项。
 *
 * 业务身份：`归一化 url | mediaType`（对应 `media_library` 的 unique 索引），
 * 因此跨设备恢复时不会因为自增 id 相同而覆盖无关存储源。
 */
class MediaLibraryBackupTable @Inject constructor(
    moshi: Moshi,
    private val dao: MediaLibraryDao,
    private val quickAccessDao: QuickAccessDao,
    private val encryptedFolderDao: EncryptedFolderDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val syncDeleter: PlayHistorySyncDeleter,
) : BackupTable<MediaLibraryEntity>(
    moshi = moshi,
    entityClass = MediaLibraryEntity::class.java,
    displayName = "存储源",
) {
    override val key = "mediaLibraries"

    /** 必须最先恢复：后续表的 libraryId / storageId 依赖它产出的 id 映射。 */
    override val restoreOrder = 0

    override suspend fun queryAll() = dao.getAllSuspend()

    override suspend fun insert(row: MediaLibraryEntity): Int =
        dao.insertReturningId(row).toInt()

    /** 镜像删除：按现有「删除媒体库」的级联范围清理关联数据。 */
    override suspend fun delete(row: MediaLibraryEntity) {
        syncDeleter.deleteByStorageId(row.id)
        quickAccessDao.deleteByLibrary(row.id)
        encryptedFolderDao.deleteByStorageId(row.id)
        downloadTaskDao.deleteByStorageId(row.id)
        dao.deleteById(row.id)
    }

    override fun identity(row: MediaLibraryEntity): String =
        "${normalizeBaseUrl(row.url)}|${row.mediaType.value}"

    override fun idOf(row: MediaLibraryEntity): Int = row.id

    override fun withLocalId(row: MediaLibraryEntity, localId: Int): MediaLibraryEntity =
        row.copy(id = localId)

    override fun onRestored(backupRow: MediaLibraryEntity, localId: Int, ctx: RestoreContext) {
        ctx.registerLibraryId(backupRow.id, localId)
    }
}
