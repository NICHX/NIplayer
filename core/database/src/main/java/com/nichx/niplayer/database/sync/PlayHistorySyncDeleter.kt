package com.nichx.niplayer.database.sync

import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity
import com.nichx.niplayer.database.isSyncableBase
import com.nichx.niplayer.database.normalizeBaseUrl
import com.nichx.niplayer.database.syncKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放历史删除 + 云同步 tombstone 的统一入口。
 *
 * 供所有"删除历史但并非从播放历史列表页发起"的路径复用（移除存储源 / 屏蔽目录 / 目录加密 /
 * 删除媒体库），保证删除在云同步里正确传播：
 *   1) 先为每条将被删除且属于可同步存储（SMB/WebDAV/Other）的记录写一条删除 tombstone，
 *      其 record_key 为设备无关的 [syncKey]（归一化存储地址 + 存储内相对路径）；
 *   2) 再执行真正的删除。
 *
 * tombstone 用 [SyncDeleteLogDao.insertOrReplace]，同一 record_key 已存在则以新删除时间覆盖，
 * 避免"删除→重扫→再删"时旧 tombstone 时间过旧导致远端记录复活。
 *
 * 位于 :core:database 而非 :core:sync：目录加密删除（EncryptedFolderManager）在 :core:database
 * 内，为避免 :core:database 反向依赖 :core:sync 而选择放于此处，复用 RecordKeys 中的 syncKey。
 */
@Singleton
class PlayHistorySyncDeleter @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val syncDeleteLogDao: SyncDeleteLogDao,
) {

    /** 删除某存储源全部播放历史（移除存储源 / 删除媒体库）。 */
    suspend fun deleteByStorageId(storageId: Int) {
        val baseUrl = normalizedBaseUrlByStorageId(storageId)
        val now = System.currentTimeMillis()
        if (baseUrl != null) {
            playHistoryDao.getByStorageId(storageId).forEach { recordTombstone(it, baseUrl, now) }
        }
        playHistoryDao.deleteByStorageId(storageId)
    }

    /** 删除目录前缀匹配的播放历史（屏蔽目录）。 */
    suspend fun deleteByStoragePathPrefix(prefix: String) {
        val baseUrlByLibrary = mediaLibraryDao.getAllSuspend()
            .filter { it.mediaType.isSyncableBase() }
            .associate { it.id to normalizeBaseUrl(it.url) }
        val now = System.currentTimeMillis()
        playHistoryDao.getByStoragePathPrefix(prefix).forEach { entity ->
            val baseUrl = entity.storageId?.let { baseUrlByLibrary[it] } ?: return@forEach
            recordTombstone(entity, baseUrl, now)
        }
        playHistoryDao.deleteByStoragePathPrefix(prefix)
    }

    /** 删除指定存储源下目录前缀的播放历史（目录加密）。 */
    suspend fun deleteByStoragePathPrefixAndStorageId(storageId: Int, prefix: String) {
        val baseUrl = normalizedBaseUrlByStorageId(storageId)
        val now = System.currentTimeMillis()
        if (baseUrl != null) {
            playHistoryDao.getByStoragePathPrefixAndStorageId(storageId, prefix).forEach {
                recordTombstone(it, baseUrl, now)
            }
        }
        playHistoryDao.deleteByStoragePathPrefixAndStorageId(storageId, prefix)
    }

    /** 仅当存储可同步且存在地址时返回归一化 baseUrl，否则返回 null（本地/异常数据不写 tombstone）。 */
    private suspend fun normalizedBaseUrlByStorageId(storageId: Int): String? {
        val lib = mediaLibraryDao.getById(storageId) ?: return null
        if (!lib.mediaType.isSyncableBase()) return null
        return normalizeBaseUrl(lib.url)
    }

    private suspend fun recordTombstone(entity: PlayHistoryEntity, baseUrl: String, deletedAt: Long) {
        val path = entity.storagePath ?: return
        syncDeleteLogDao.insertOrReplace(
            SyncDeleteLogEntity(
                tableName = TABLE_PLAY_HISTORY,
                recordKey = syncKey(baseUrl, path),
                deletedAt = deletedAt,
                synced = false,
            ),
        )
    }

    private companion object {
        const val TABLE_PLAY_HISTORY = "play_history"
    }
}