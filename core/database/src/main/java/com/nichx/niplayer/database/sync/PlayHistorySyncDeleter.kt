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
 * 云同步的墓碑是本机**永久账本**（[SyncDeleteLogDao] 中的行不再在发布后被清空）：只要墓碑还在，
 * 晚联网的设备就不会把已删记录"复活"。因此删除历史必须经过本类，保证墓碑与删除动作一起发生。
 *
 * 两类调用方：
 *  1. **历史列表页**的删除 / 清空 —— 自己落库，只需先写墓碑（用 [tombstoneFor]）；
 *  2. **非历史页发起**的删除（移除存储源 / 屏蔽目录 / 目录加密 / 删除媒体库）—— 先写墓碑再删，
 *     由本类的 deleteBy* 方法一次完成。
 *
 * 墓碑的 record_key 使用设备无关的 [syncKey]（归一化存储地址 + 存储内相对路径），本地自增的
 * storageId / uniqueKey 跨设备无意义。只有可同步的远端存储（SMB/WebDAV/Other）才写墓碑：
 * LOCAL / EXTERNAL / QUICK_ACCESS 的同路径不代表同一文件。
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

    /**
     * 为一条记录写删除墓碑（**不删除记录本身**）。
     *
     * 供"自己负责落库"的调用方复用（历史列表页的单条删除 / 清空），保证墓碑写入逻辑与
     * [deleteByStorageId] 等路径完全一致。
     */
    suspend fun tombstoneFor(entity: PlayHistoryEntity, deletedAt: Long = System.currentTimeMillis()) {
        tombstoneFor(listOf(entity), deletedAt)
    }

    /** 为多条记录批量写墓碑（存储库地址映射只查一次）。 */
    suspend fun tombstoneFor(entities: List<PlayHistoryEntity>, deletedAt: Long = System.currentTimeMillis()) {
        val baseUrlByLibrary = mediaLibraryDao.getAllSuspend()
            .filter { it.mediaType.isSyncableBase() }
            .associate { it.id to normalizeBaseUrl(it.url) }
        entities.forEach { entity ->
            val baseUrl = entity.storageId?.let { baseUrlByLibrary[it] } ?: return@forEach
            recordTombstone(entity, baseUrl, deletedAt)
        }
    }

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
            ),
        )
    }

    private companion object {
        const val TABLE_PLAY_HISTORY = "play_history"
    }
}
