package com.nichx.niplayer.sync

import android.content.Context
import android.util.Log
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.SyncConflictDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.SyncConflictEntity
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity
import com.nichx.niplayer.database.isSyncableBase
import com.nichx.niplayer.database.normalizeBaseUrl
import com.nichx.niplayer.database.syncKey
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.datastore.WebDavSettings
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.WebDavHttpException
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 增量优化：
 * - 增量拉取：按 (lastModified, length) 指纹跳过未变化的远端设备文件
 * - 增量上传：记录/墓碑均未变化且心跳未过期时跳过整文件上传
 * - 墓碑 GC：超过 [TOMBSTONE_RETENTION_MS]（30 天）的 tombstone 视为已传播，写回时清除
 * - 废弃设备清理：超 [STALE_DEVICE_MS]（90 天）未同步的远端设备文件删除
 * - 时钟防护：按远端文件 lastModified（服务器时间）估算该设备时钟偏移，比较前校正，
 *   缓解跨设备时钟偏差导致的误删 / 误覆盖
 * - 冲突感知：两端在 [CONFLICT_WINDOW_MS] 内同时修改且内容不同 → 写入 sync_conflict 表，
 *   LWW 仍按时间戳决出胜者，败者数据保留供用户选择
 *
 * 同步状态（[state]）供「备份与同步」页卡片与播放历史页 TopBar 指示器共用，
 * 上次同步结果持久化在 [PlayHistorySyncSettings]。
 */
@Singleton
class PlayHistorySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playHistoryDao: PlayHistoryDao,
    private val syncDeleteLogDao: SyncDeleteLogDao,
    private val syncConflictDao: SyncConflictDao,
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

    /**
     * 执行一次完整同步（push → pull → 吸收）。
     *
     * 前置条件：同步开关已启用、已选择 WebDAV 服务器。
     * [auto] 为 true 时（启动拉取 / 播放器退出推送）受最小间隔防抖限制；但本地有待推送
     * 内容（记录变更或未同步墓碑）时跳过防抖，保证进度与删除尽快传播，防抖只约束无变更的纯拉取。
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
            recordResult(false, context.getString(R.string.sync_error_no_server_selected))
            return false
        }
        // 自动同步最小间隔防抖（手动同步不受限制）。
        // 基准取 lastSyncedAt（仅同步成功后推进）：失败不推迟下次重试。
        // 内容感知：本地有待推送内容（记录 updatedAt > 游标 或 未同步墓碑）时跳过防抖，
        // 保证播放进度与删除尽快传播；防抖只约束"无变更的纯拉取"，避免启动等低频场景重复拉取。
        val hasPendingLocalChanges =
            playHistoryDao.getChangesSinceTimestamp(PlayHistorySyncSettings.lastSyncedAt).isNotEmpty() ||
                syncDeleteLogDao.countUnsynced() > 0
        if (auto && !hasPendingLocalChanges &&
            System.currentTimeMillis() - PlayHistorySyncSettings.lastSyncedAt < MIN_AUTO_INTERVAL_MS
        ) {
            return false
        }
        PlayHistorySyncSettings.ensureDeviceId()

        return mutex.withLock {
            _state.value = SyncUiState.Syncing
            try {
                val conflictCount = doSync(libraryId)
                val message = if (conflictCount > 0) {
                    context.getString(R.string.sync_success_with_conflicts, conflictCount)
                } else {
                    context.getString(R.string.sync_success)
                }
                _state.value = SyncUiState.Done(true, message)
                recordResult(true, message)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "播放历史同步失败", e)
                val message = e.toUserMessage(context)
                _state.value = SyncUiState.Done(false, message)
                recordResult(false, message)
                false
            }
        }
    }

    private suspend fun doSync(libraryId: Int): Int {
        val library = mediaLibraryDao.getById(libraryId)
            ?: throw IllegalStateException(context.getString(R.string.sync_error_server_not_found))
        val deviceId = PlayHistorySyncSettings.deviceId
        val fileName = "play_history_$deviceId.json"

        return withContext(Dispatchers.IO) {
            val storage = storageFactory.create(library)
                ?: throw IllegalStateException(context.getString(R.string.sync_error_cannot_connect))
            verifyConnection(storage)
            val now = System.currentTimeMillis()
            var conflictCount = 0

            // BUG-B：同步身份必须是设备无关的。旧实现用 storageId+uniqueKey(含本地 library.id)
            // 作 key，两端指向同一存储/文件时算出的 key 不同，导致同步永远合并不上。
            // 现改为 syncKey = 归一化存储地址 + 存储内相对路径，两端用相同地址配置即可对齐。
            val libraries = mediaLibraryDao.getAllSuspend()
            val baseUrlByStorageId = HashMap<Int, String>()
            val syncableStorageIds = HashSet<Int>()
            // 归一化地址 -> 本机 storageId（供把远端记录匹配回本地可续播的存储）
            val storageIdByNormalizedBaseUrl = HashMap<String, Int>()
            for (lib in libraries) {
                if (lib.mediaType.isSyncableBase()) {
                    val norm = normalizeBaseUrl(lib.url)
                    syncableStorageIds += lib.id
                    baseUrlByStorageId[lib.id] = norm
                    if (!storageIdByNormalizedBaseUrl.containsKey(norm)) {
                        storageIdByNormalizedBaseUrl[norm] = lib.id
                    }
                }
            }

            // 本地当前全量可同步记录（syncKey -> entity）；本地存储（LOCAL/EXTERNAL/QUICK_ACCESS）
            // 不参与同步；storageId/storagePath 缺失或存储已删除的记录亦跳过。
            val localEntities = playHistoryDao.getAll()
                .mapNotNull { entity ->
                    val sid = entity.storageId ?: return@mapNotNull null
                    if (sid !in syncableStorageIds) return@mapNotNull null
                    val path = entity.storagePath ?: return@mapNotNull null
                    val base = baseUrlByStorageId[sid] ?: return@mapNotNull null
                    syncKey(base, path) to entity
                }
                .toMap()
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

            // 2) pull：合并各远端设备文件（跳过自己），按指纹跳过未变化的文件
            var maxRemoteUpdatedAt = 0L
            val remoteFiles = listSyncFiles(storage, deviceId)
            for (remoteFile in remoteFiles) {
                // 增量拉取：与上次成功同步记录的 (mtime, length) 一致则跳过下载解析。
                // mtime 粒度粗（如 WebDAV 1s）时可能跳过一次中间版本，但 LWW 合并单调收敛，仅延迟不丢最终态
                val meta = PlayHistorySyncSettings.getRemoteFileMeta(remoteFile.name)
                if (meta != null && meta.mtime > 0 &&
                    meta.mtime == remoteFile.lastModified && meta.length == remoteFile.length
                ) {
                    continue
                }

                val remote = readDeviceFile(storage, remoteFile.name)
                    ?: continue
                maxRemoteUpdatedAt = maxOf(maxRemoteUpdatedAt, remote.updatedAt)
                PlayHistorySyncSettings.setRemoteFileMeta(
                    remoteFile.name,
                    remoteFile.lastModified,
                    remoteFile.length,
                    remote.lastSyncedAt,
                )

                // P2-3 时钟防护：文件 lastModified 是服务器时间（该设备上次上传时刻）。
                // 若文件内 updatedAt 明显晚于服务器时间，说明该设备时钟偏快，
                // 偏移量 ≈ updatedAt - lastModified。比较前将该设备时间戳统一减掉偏移量，
                // 避免"偏快设备"的删除 / 覆盖误压本机真实更新的数据（本机视为与服务器时间对齐）
                val remoteSkew = (remote.updatedAt - remoteFile.lastModified).coerceAtLeast(0L)

                // a. 应用远端 tombstone：删除本地 updatedAt <= 校正后 deletedAt 的记录
                for (del in remote.deletes) {
                    val effDeletedAt = (del.deletedAt - remoteSkew).coerceAtLeast(0L)
                    // BUG 2：仅在记录确实被删除时才移出 compressed 快照。若本地记录比 tombstone
                    // 新（LWW 下应保留），不能从 localEntities 移除，否则记录会从本设备写回的文件
                    // （mergedRecords）中消失，造成下一轮前云端缺失该记录、合并结果延迟收敛。
                    localEntities[del.key]?.let { entity ->
                        if (entity.updatedAt <= effDeletedAt) {
                            localEntities.remove(del.key)
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
                    // 吸收 tombstone 进本设备文件，继续传播（保留原始时间戳，跨设备传播依赖它）
                    val old = localFile.deletes.firstOrNull { it.key == del.key }
                    if (old == null || del.deletedAt > old.deletedAt) {
                        localFile = localFile.copy(
                            deletes = (localFile.deletes.filterNot { it.key == del.key } + del).sortedBy { it.key },
                        )
                    }
                }

                // b. 合并远端记录：last-write-wins（时间戳经时钟校正后比较）
                val localTombstones = localFile.deletes.associateBy { it.key }
                for (record in remote.records) {
                    val effUpdatedAt = (record.updatedAt - remoteSkew).coerceAtLeast(0L)
                    val local = localEntities[record.key]
                    when {
                        local == null -> {
                            // 被 tombstone 命中则不复活
                            val tomb = localTombstones[record.key]
                            if (tomb == null || effUpdatedAt > tomb.deletedAt) {
                                // BUG-B：本机无此记录。仅当存在"归一化地址"与本机存储匹配时才落盘为
                                // 本地可续播的历史（用本机 storageId 构造 uniqueKey）；本机没有对应存储
                                // 的远端记录跳过，不制造无主且无法续播的悬空行。
                                val localBase = record.baseUrl?.let { normalizeBaseUrl(it) }
                                val matchedStorageId = localBase?.let { storageIdByNormalizedBaseUrl[it] }
                                val remotePath = record.storagePath
                                if (matchedStorageId != null && remotePath != null) {
                                    val entity = record.toEntity().apply {
                                        storageId = matchedStorageId
                                        uniqueKey = "$matchedStorageId:$remotePath"
                                    }
                                    // IGNORE 防唯一键并发冲突；insert 保留远端 updatedAt（供后续 LWW）
                                    playHistoryDao.insert(entity)
                                    localEntities[record.key] = entity
                                }
                            }
                        }
                        else -> {
                            // P2-2 冲突感知：两端在冲突窗口内各自修改且合并字段不同 → 记录冲突现场
                            if (kotlin.math.abs(effUpdatedAt - local.updatedAt) <= CONFLICT_WINDOW_MS &&
                                conflictsWith(local, record)
                            ) {
                                conflictCount++
                                syncConflictDao.insert(
                                    SyncConflictEntity(
                                        recordKey = record.key,
                                        storageId = record.storageId,
                                        uniqueKey = record.uniqueKey,
                                        videoName = record.videoName,
                                        localVideoPosition = local.videoPosition,
                                        localVideoDuration = local.videoDuration,
                                        localUpdatedAt = local.updatedAt,
                                        localPlayTime = local.playTime.time,
                                        remoteVideoPosition = record.videoPosition,
                                        remoteVideoDuration = record.videoDuration,
                                        remoteUpdatedAt = record.updatedAt,
                                        createdAt = now,
                                    ),
                                )
                            }
                            if (effUpdatedAt > local.updatedAt) {
                                local.applyRemote(record)
                                playHistoryDao.update(local)
                                localEntities[record.key] = local
                            }
                        }
                    }
                }
                maxRemoteUpdatedAt = maxOf(maxRemoteUpdatedAt, remote.records.maxOfOrNull { it.updatedAt } ?: 0)
            }

            // 3) 墓碑 GC：清掉已超过保留期的 tombstone（视为已传播到所有设备），止住云端文件膨胀。
            //    代价：离线超过保留期的设备重连后可能复活已删记录（业界标准取舍，同 Cassandra gc_grace_seconds）
            val gcCutoff = now - TOMBSTONE_RETENTION_MS
            val keptDeletes = localFile.deletes.filter { it.deletedAt > gcCutoff }

            // 4) 写回本设备文件：当前本地全量记录 + 保留的 tombstone
            val mergedRecords = localEntities.values
                .mapNotNull { it.toSyncRecord(baseUrlByStorageId[it.storageId]) }
                .sortedBy { it.key }
            val maxLocalUpdatedAt = mergedRecords.maxOfOrNull { it.updatedAt } ?: 0
            val maxTombstoneDeletedAt = keptDeletes.maxOfOrNull { it.deletedAt } ?: 0
            val newFile = PlayHistorySyncFile(
                deviceId = deviceId,
                version = PROTOCOL_VERSION,
                updatedAt = maxOf(maxLocalUpdatedAt, maxRemoteUpdatedAt, maxTombstoneDeletedAt, localFile.updatedAt),
                records = mergedRecords,
                deletes = keptDeletes.sortedBy { it.key },
                lastSyncedAt = now,
            )

            // 增量上传：记录与墓碑均未变化且心跳未过期时跳过上传，避免全量重传。
            // 心跳保证活动设备文件至少每 HEARTBEAT_INTERVAL_MS 更新一次，供废弃设备判定
            val heartbeatExpired = now - localFile.lastSyncedAt >= HEARTBEAT_INTERVAL_MS
            val contentChanged = newFile.records != localFile.records || newFile.deletes != localFile.deletes
            if (contentChanged || heartbeatExpired) {
                saveDeviceFile(storage, fileName, newFile)
            }

            // 5) 废弃设备文件清理：超 90 天未同步的远端设备文件删除。
            //    旧格式文件（无心跳）回退用文件 lastModified 判定；无法判定（两者均 0）则跳过
            for (remoteFile in remoteFiles) {
                val remoteMeta = PlayHistorySyncSettings.getRemoteFileMeta(remoteFile.name)
                val heartbeat = if (remoteMeta != null && remoteMeta.syncedAt > 0) {
                    remoteMeta.syncedAt
                } else {
                    remoteFile.lastModified
                }
                if (heartbeat > 0 && now - heartbeat > STALE_DEVICE_MS) {
                    storage.deleteFile(remoteFile)
                    PlayHistorySyncSettings.clearRemoteFileMeta(remoteFile.name)
                    Log.i(TAG, "清理废弃设备文件 ${remoteFile.name}")
                }
            }

            // 6) 推进游标并清理已同步的删除日志
            // BUG 4 修复：远端设备时钟偏快时，其记录 updatedAt 可能晚于本机 now，若直接照单全收
            // 推高游标，会让 sync() 的防抖判断 `now - lastSyncedAt < MIN` 恒成立、且
            // getChangesSinceTimestamp(lastSyncedAt) 恒查不到本机新编辑，自动同步被永久误拦。
            // 把游标钳制在 now 内：此类"未来"记录会一直被视为待推送（防抖失效），但内容比对
            // （contentChanged）会在 doSync 内兜底保证最终上传，仅为多触发而非丢数据。
            PlayHistorySyncSettings.lastSyncedAt = maxOf(
                PlayHistorySyncSettings.lastSyncedAt,
                minOf(newFile.updatedAt, now),
            )
            val syncedIds = unsyncedDeletes.map { it.id }
            if (syncedIds.isNotEmpty()) {
                syncDeleteLogDao.markAsSynced(syncedIds)
            }
            // BUG 5 修复：无条件清理已同步日志。deleteSynced() 只删 synced=1 的行，幂等：
            // 除回收本次开始时 unsyncedDeletes 标记的行外，也能回收 pull 阶段由远端 tombstone
            // 删除记录时写入的 synced=true 行（此前仅在本处有空列表时才跳过清理，导致其累积）。
            syncDeleteLogDao.deleteSynced()
            conflictCount
        }
    }

    private suspend fun verifyConnection(storage: Storage) {
        try {
            storage.testConnection()
        } catch (e: WebDavHttpException) {
            throw IllegalStateException(context.getString(e.friendlyMessageRes, e.code))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(
                context.getString(
                    R.string.sync_error_connect_failed,
                    e.message ?: context.getString(R.string.sync_error_network),
                ),
            )
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
            throw IllegalStateException(context.getString(R.string.sync_error_create_dir_failed, SYNC_ROOT_DIR))
        }
        if (!storage.createDirectory(SYNC_SUB_DIR)) {
            throw IllegalStateException(context.getString(R.string.sync_error_create_dir_failed, SYNC_SUB_DIR))
        }
    }

    /** 覆盖上传本设备文件，上传后读回校验防静默损坏。 */
    private suspend fun saveDeviceFile(storage: Storage, fileName: String, file: PlayHistorySyncFile) {
        ensureSyncDirectory(storage)
        val json = syncFileAdapter.toJson(file)
        val ok = storage.saveFile(
            "$SYNC_SUB_DIR/$fileName",
            json.toByteArray(Charsets.UTF_8),
        )
        if (!ok) throw IllegalStateException(context.getString(R.string.sync_error_upload_failed))

        // P2-1 上传校验：读回比对内容（Moshi 序列化顺序确定，全等比较可信）。
        // 读回失败仅告警不阻断——瞬时网络抖动不应把一次成功上传标记为失败
        try {
            val fileRef = object : AbstractStorageFile(
                path = "$SYNC_SUB_DIR/$fileName",
                name = fileName,
                isDirectory = false,
            ) {}
            val readBack = storage.openInputStream(fileRef).use { it.readBytes().toString(Charsets.UTF_8) }
            if (readBack != json) {
                throw IllegalStateException(context.getString(R.string.sync_error_upload_mismatch))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "上传校验读取失败: ${e.message}")
        }
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

    /** 两端记录的合并字段是否存在实质差异（仅比较会在合并时被覆盖的字段）。 */
    private fun conflictsWith(local: PlayHistoryEntity, remote: SyncRecord): Boolean =
        local.videoPosition != remote.videoPosition ||
            local.videoDuration != remote.videoDuration ||
            local.playTime.time != remote.playTime ||
            // BUG 3 修复：applyRemote 也会覆盖 httpHeader / storagePath，若不在冲突检测范围内，
            // 败者一方的请求头 / 路径会在 LWW 下被静默丢弃且不提示用户。
            local.httpHeader != remote.httpHeader ||
            local.storagePath != remote.storagePath

    companion object {
        private const val TAG = "PlayHistorySync"

        /** 复用备份目录，同步文件位于其 sync 子目录（不新增一级目录）。 */
        const val SYNC_ROOT_DIR = "NIplayer_backup"
        const val SYNC_SUB_DIR = "NIplayer_backup/sync"

        /** 自动同步最小间隔（ms）：60 秒防抖。 */
        private const val MIN_AUTO_INTERVAL_MS = 60 * 1000L

        /** 墓碑保留期（ms）：30 天。超过后视为已传播到所有活动设备，可安全清除。 */
        private const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        /** 心跳间隔（ms）：24 小时。活动设备即使无数据变化也强制上传一次，保持文件心跳。 */
        private const val HEARTBEAT_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** 废弃设备判定阈值（ms）：90 天未同步视为废弃，同步时清理其云端文件。 */
        private const val STALE_DEVICE_MS = 90L * 24 * 60 * 60 * 1000

        /** 冲突判定窗口（ms）：两端更新时间差在 10 秒内视为并发修改。 */
        private const val CONFLICT_WINDOW_MS = 10 * 1000L

        /** 云端文件协议版本。 */
        private const val PROTOCOL_VERSION = 2

        private const val TABLE_PLAY_HISTORY = "play_history"
    }
}

/** 将异常转为面向用户的中文提示。 */
private fun Throwable.toUserMessage(context: Context): String = when (this) {
    is WebDavHttpException -> context.getString(friendlyMessageRes, code)
    is IllegalStateException -> message ?: context.getString(R.string.sync_error_operation_failed)
    else -> message ?: toString()
}
