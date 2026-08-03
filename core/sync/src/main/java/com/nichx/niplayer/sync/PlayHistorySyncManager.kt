package com.nichx.niplayer.sync

import android.util.Log
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.datastore.WebDavSettings
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.WebDavHttpException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 同步 UI 状态（待机 / 运行中 / 结果），供设置页卡片与历史页 TopBar 指示器共用。 */
sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object Syncing : SyncUiState
    data class Done(val success: Boolean, val message: String) : SyncUiState
}

/**
 * 播放历史 WebDAV 云同步核心。
 *
 * 云端协议见 [PlayHistorySyncFile]：每设备一个 JSON 文件，全量快照 + 删除 tombstone。
 * 同步采用 push（本设备文件覆盖上传）→ pull（各远端文件合并）→ 吸收（tombstone 传播）：
 * - 记录冲突：按 updated_at last-write-wins
 * - 删除传播：tombstone（key + deletedAt），其他设备拉取时删除 updatedAt <= deletedAt 的记录，
 *   并把 tombstone 吸收进自己的文件继续传播，防止记录"复活"
 *
 * 同步状态（[state]）供「备份与同步」页卡片与播放历史页 TopBar 指示器共用，
 * 上次同步结果持久化在 [PlayHistorySyncSettings]。
 */
@Singleton
class PlayHistorySyncManager @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
    private val syncDeleteLogDao: SyncDeleteLogDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
) {

    private val _state = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    /** 同步状态 StateFlow，驱动设置页卡片与历史页 TopBar 指示器。 */
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private val mutex = Mutex()

    private val syncFileAdapter: com.squareup.moshi.JsonAdapter<PlayHistorySyncFile> by lazy {
        Moshi.Builder().build().adapter(PlayHistorySyncFile::class.java)
    }

    /** 当前是否正在同步。 */
    val isSyncing: Boolean
        get() = _state.value is SyncUiState.Syncing

    /** 清除完成态，让 UI 指示器回到待机（用于结果短暂展示后消退）。 */
    fun dismissResult() {
        if (_state.value is SyncUiState.Done) {
            _state.value = SyncUiState.Idle
        }
    }

    /**
     * 执行一次完整同步（push → pull → 吸收）。
     *
     * 前置条件：同步开关已启用、已选择 WebDAV 服务器。
     * [auto] 为 true 时（启动 / 播放器退出自动触发）受最小间隔防抖限制，避免高频同步。
     * 失败时保留游标，下次重试（上传/合并均幂等）。
     *
     * @return 是否同步成功
     */
    suspend fun sync(auto: Boolean = false): Boolean {
        val enabled = PlayHistorySyncSettings.enabled
        val libraryId = WebDavSettings.libraryId
        if (!enabled) {
            return false
        }
        if (libraryId < 0) {
            recordResult(false, "未选择 WebDAV 服务器")
            return false
        }
        // 自动同步最小间隔防抖（手动同步不受限制）
        if (auto && System.currentTimeMillis() - PlayHistorySyncSettings.lastSyncTime < MIN_AUTO_INTERVAL_MS) {
            return false
        }
        PlayHistorySyncSettings.ensureDeviceId()

        return mutex.withLock {
            _state.value = SyncUiState.Syncing
            try {
                doSync(libraryId)
                _state.value = SyncUiState.Done(true, "同步成功")
                recordResult(true, "同步成功")
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "播放历史同步失败", e)
                val message = e.toUserMessage()
                _state.value = SyncUiState.Done(false, message)
                recordResult(false, message)
                false
            }
        }
    }

    private suspend fun doSync(libraryId: Int) {
        val library = mediaLibraryDao.getById(libraryId)
            ?: throw IllegalStateException("未找到所选 WebDAV 服务器")
        val deviceId = PlayHistorySyncSettings.deviceId
        val fileName = "play_history_$deviceId.json"

        withContext(Dispatchers.IO) {
            val storage = storageFactory.create(library)
                ?: throw IllegalStateException("无法连接 WebDAV 服务器")
            verifyConnection(storage)

            // 本地当前全量记录（key -> entity）
            val localEntities = playHistoryDao.getAll()
                .filter { it.storageId != null }
                .associateBy { recordKey(it.storageId!!, it.uniqueKey) }
                .toMutableMap()

            // 本设备文件（不存在则视为空）
            var localFile = readDeviceFile(storage, fileName) ?: PlayHistorySyncFile(deviceId = deviceId)

            // 1) push 前置：把本地未同步的删除 tombstone 吸收进本设备文件，并剔除被命中记录
            val unsyncedDeletes = syncDeleteLogDao.getUnsyncedDeletes()
            if (unsyncedDeletes.isNotEmpty()) {
                val tombstoneMap = mutableMapOf<String, Long>()
                localFile.deletes.forEach { tombstoneMap[it.key] = it.deletedAt }
                unsyncedDeletes.forEach { log ->
                    val old = tombstoneMap[log.recordKey]
                    if (old == null || log.deletedAt > old) {
                        tombstoneMap[log.recordKey] = log.deletedAt
                    }
                }
                localFile = localFile.copy(
                    deletes = tombstoneMap.map { (k, v) -> SyncDelete(k, v) }.sortedBy { it.key },
                )
            }
            // 剔除本地文件中被 tombstone 命中的记录（本地已删或远端已删）
            val tombstoneByKey = localFile.deletes.associateBy { it.key }
            localFile = localFile.copy(
                records = localFile.records.filter { record ->
                    val tomb = tombstoneByKey[record.key]
                    tomb == null || record.updatedAt > tomb.deletedAt
                },
            )

            // 2) pull：合并各远端设备文件（跳过自己）
            var maxRemoteUpdatedAt = 0L
            val remoteFiles = listSyncFiles(storage, deviceId)
            for (remoteFile in remoteFiles) {
                val remote = readDeviceFile(storage, remoteFile.name)
                    ?: continue
                maxRemoteUpdatedAt = maxOf(maxRemoteUpdatedAt, remote.updatedAt)

                // a. 应用远端 tombstone：删除本地 updatedAt <= deletedAt 的记录
                for (del in remote.deletes) {
                    localEntities.remove(del.key)?.let { entity ->
                        if (entity.updatedAt <= del.deletedAt) {
                            playHistoryDao.delete(entity.id)
                            // 标记已同步，避免删除回传（本设备文件 deletes 中已含该 tombstone）
                            syncDeleteLogDao.insert(
                                SyncDeleteLogEntity(
                                    tableName = TABLE_PLAY_HISTORY,
                                    recordKey = del.key,
                                    deletedAt = del.deletedAt,
                                    synced = true,
                                ),
                            )
                        }
                    }
                    // 吸收 tombstone 进本设备文件，继续传播
                    val old = localFile.deletes.firstOrNull { it.key == del.key }
                    if (old == null || del.deletedAt > old.deletedAt) {
                        localFile = localFile.copy(
                            deletes = (localFile.deletes.filterNot { it.key == del.key } + del).sortedBy { it.key },
                        )
                    }
                }

                // b. 合并远端记录：last-write-wins
                val localTombstones = localFile.deletes.associateBy { it.key }
                for (record in remote.records) {
                    val local = localEntities[record.key]
                    when {
                        local == null -> {
                            // 被 tombstone 命中则不复活
                            val tomb = localTombstones[record.key]
                            if (tomb == null || record.updatedAt > tomb.deletedAt) {
                                val entity = record.toEntity()
                                playHistoryDao.insert(entity)
                                localEntities[record.key] = entity
                            }
                        }
                        record.updatedAt > local.updatedAt -> {
                            local.applyRemote(record)
                            playHistoryDao.update(local)
                            localEntities[record.key] = local
                        }
                    }
                }
                maxRemoteUpdatedAt = maxOf(maxRemoteUpdatedAt, remote.records.maxOfOrNull { it.updatedAt } ?: 0)
            }

            // 3) 写回本设备文件：当前本地全量记录 + 吸收的 tombstone
            val mergedRecords = localEntities.values
                .mapNotNull { it.toSyncRecord() }
                .sortedBy { it.key }
            val maxLocalUpdatedAt = mergedRecords.maxOfOrNull { it.updatedAt } ?: 0
            localFile = localFile.copy(
                records = mergedRecords,
                updatedAt = maxOf(maxLocalUpdatedAt, maxRemoteUpdatedAt, localFile.updatedAt),
            )
            saveDeviceFile(storage, fileName, localFile)

            // 4) 推进游标并清理已同步的删除日志
            PlayHistorySyncSettings.lastSyncedAt = maxOf(
                PlayHistorySyncSettings.lastSyncedAt,
                localFile.updatedAt,
            )
            val syncedIds = unsyncedDeletes.map { it.id }
            if (syncedIds.isNotEmpty()) {
                syncDeleteLogDao.markAsSynced(syncedIds)
                syncDeleteLogDao.deleteSynced()
            }
        }
    }

    private suspend fun verifyConnection(storage: Storage) {
        try {
            storage.testConnection()
        } catch (e: WebDavHttpException) {
            throw IllegalStateException(e.friendlyMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("无法连接服务器: ${e.message ?: "网络错误"}")
        }
    }

    /** 读取某设备文件，不存在或解析失败返回 null（目录尚不存在等）。 */
    private suspend fun readDeviceFile(storage: Storage, fileName: String): PlayHistorySyncFile? {
        return try {
            val file = object : AbstractStorageFile(
                path = "$SYNC_SUB_DIR/$fileName",
                name = fileName,
                isDirectory = false,
            ) {}
            val json = storage.openInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
            syncFileAdapter.fromJson(json)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** 列出 sync 子目录下的远端设备文件（跳过自己）。 */
    private suspend fun listSyncFiles(storage: Storage, deviceId: String): List<StorageFile> {
        return try {
            ensureSyncDirectory(storage)
            val dir = object : AbstractStorageFile(SYNC_SUB_DIR, SYNC_SUB_DIR, true) {}
            storage.listFiles(dir).filter { file ->
                !file.isDirectory &&
                    file.name.startsWith("play_history_") &&
                    file.name.endsWith(".json") &&
                    file.name != "play_history_$deviceId.json"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 确保 sync 子目录存在（MKCOL 单级，需逐级创建）。 */
    private suspend fun ensureSyncDirectory(storage: Storage) {
        if (!storage.createDirectory(SYNC_ROOT_DIR)) {
            throw IllegalStateException("无法创建同步目录 $SYNC_ROOT_DIR")
        }
        if (!storage.createDirectory(SYNC_SUB_DIR)) {
            throw IllegalStateException("无法创建同步目录 $SYNC_SUB_DIR")
        }
    }

    /** 覆盖上传本设备文件。 */
    private suspend fun saveDeviceFile(storage: Storage, fileName: String, file: PlayHistorySyncFile) {
        ensureSyncDirectory(storage)
        val ok = storage.saveFile(
            "$SYNC_SUB_DIR/$fileName",
            syncFileAdapter.toJson(file).toByteArray(Charsets.UTF_8),
        )
        if (!ok) throw IllegalStateException("上传失败，请检查服务器配置")
    }

    private fun recordResult(success: Boolean, message: String) {
        PlayHistorySyncSettings.recordSyncResult(success, message)
    }

    private fun PlayHistoryEntity.applyRemote(remote: SyncRecord) {
        // videoName / url / mediaType 为 val 不更新（记录标识字段，跨设备不变）
        videoPosition = remote.videoPosition
        videoDuration = remote.videoDuration
        playTime = java.util.Date(remote.playTime)
        httpHeader = remote.httpHeader
        storagePath = remote.storagePath
        updatedAt = remote.updatedAt
    }

    companion object {
        private const val TAG = "PlayHistorySync"

        /** 复用备份目录，同步文件位于其 sync 子目录（不新增一级目录）。 */
        const val SYNC_ROOT_DIR = "NIplayer_backup"
        const val SYNC_SUB_DIR = "NIplayer_backup/sync"

        /** 自动同步最小间隔（ms）：5 分钟防抖。 */
        private const val MIN_AUTO_INTERVAL_MS = 5 * 60 * 1000L

        private const val TABLE_PLAY_HISTORY = "play_history"
    }
}

/** 将异常转为面向用户的中文提示。 */
private fun Throwable.toUserMessage(): String = when (this) {
    is WebDavHttpException -> friendlyMessage
    is IllegalStateException -> message ?: "操作失败"
    else -> message ?: toString()
}
