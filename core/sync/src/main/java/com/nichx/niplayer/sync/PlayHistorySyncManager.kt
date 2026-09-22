package com.nichx.niplayer.sync

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.isSyncableBase
import com.nichx.niplayer.database.normalizeBaseUrl
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.datastore.WebDavSettings
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.WebDavHttpException
import com.squareup.moshi.JsonAdapter
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
 * 播放历史 WebDAV 云同步核心（协议 v5：单文件共享快照）。
 *
 * ## 模型
 *
 * 云端**只有一个文件** `NIplayer_backup/sync/play_history.json`（见 [FILE_NAME]），内容是
 * 所有候选折叠后的胜者集合（见 [PlayHistorySyncFile]）。
 *
 * ## 算法
 *
 * 一次同步 = 读云端文件 → 折叠（本机记录 + 本机墓碑账本 + 云端内容 + 旧协议遗留墓碑）→
 * 条件写回 → 读回校验；冲突或被抢占则重读重算重写（有界）。
 *
 * ## 为什么可以这么简单
 *
 * 从设计与实现中**刻意去掉**了以下机制，它们各自的失效模式正是历史缺陷的主要来源：
 * - 每设备一个文件的拓扑（多设备枚举、自读自己文件、N 份冗余副本、废弃设备文件残留）
 * - 墓碑的"待发布 / 已发布 / 吸收自对端"三分状态（现在本机墓碑账本永久保留、整体参与折叠）
 * - 冲突表与冲突界面（一次 max 折叠已给出确定性结果，仅丢失几秒进度）
 * - 时钟偏移校正（过度校正会静默丢弃真正更新的记录）
 * - 墓碑过期清理与废弃设备清理（离线设备复活、云端状态被误删）
 *
 * 播放历史是小数据（数千条 × 约 200B），全量读写的代价远低于上述复杂度。合并算法**幂等**
 * （重复执行同一状态结果不变）且**对称**（两端对同一对候选判定一致），因此中途失败只需下一轮
 * 重试即可收敛。
 */
@Singleton
class PlayHistorySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playHistoryDao: PlayHistoryDao,
    private val syncDeleteLogDao: SyncDeleteLogDao,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
) {

    private val _state = MutableStateFlow<SyncUiState>(SyncUiState.Idle)

    /** 同步状态 StateFlow，驱动设置页卡片与历史页 TopBar 指示器。 */
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private val mutex = Mutex()

    private val syncFileAdapter: JsonAdapter<PlayHistorySyncFile> by lazy {
        Moshi.Builder().build().adapter(PlayHistorySyncFile::class.java)
    }

    /**
     * 执行一次完整同步（读 + 折叠 + 条件写回）。
     *
     * @param auto 自动同步（启动 / 播放器退出）受 [MIN_AUTO_INTERVAL_MS] 时间防抖；手动同步不受限。
     * @return 是否同步成功
     */
    suspend fun sync(auto: Boolean = false): Boolean = mutex.withLock {
        when {
            // 未开启时静默跳过：UI 此时不显示任何同步入口，写一条"失败"只会污染上次同步结果
            // （自动同步只看 autoSync，用户完全可能关掉总开关却留着它）
            !PlayHistorySyncSettings.enabled -> false
            WebDavSettings.libraryId < 0 -> reportFailure(R.string.sync_error_no_server_selected)
            auto && !autoSyncDue() -> false
            else -> runSync(WebDavSettings.libraryId)
        }
    }

    /** 自动同步的时间防抖：距离上次成功同步不足 [MIN_AUTO_INTERVAL_MS] 时跳过。 */
    private fun autoSyncDue(): Boolean =
        System.currentTimeMillis() - PlayHistorySyncSettings.lastSyncedAt >= MIN_AUTO_INTERVAL_MS

    private suspend fun runSync(libraryId: Int): Boolean {
        // deviceId 已不参与同步文件命名（单文件协议），但仍是备份文件名的设备标识。
        PlayHistorySyncSettings.ensureDeviceId()
        _state.value = SyncUiState.Syncing
        return try {
            withContext(Dispatchers.IO) { doSync(libraryId) }
            reportSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "播放历史同步失败", e)
            reportFailure(e.toUserMessage(context))
        }
    }

    private suspend fun doSync(libraryId: Int) {
        val library = mediaLibraryDao.getById(libraryId)
            ?: throw IllegalStateException(context.getString(R.string.sync_error_server_not_found))
        val storage = storageFactory.create(library)
            ?: throw IllegalStateException(context.getString(R.string.sync_error_cannot_connect))

        verifyConnection(storage)
        ensureSyncDirectory(storage)

        val local = loadLocalState()
        val legacy = readLegacyFiles(storage)
        val plan = SyncPublisher(
            readRemote = { storage.readFile(FILE_PATH) },
            writeRemote = { data, precondition -> storage.writeFile(FILE_PATH, data, precondition) },
            adapter = syncFileAdapter,
        ).publish(local, legacy.tombstones)

        applyPlan(plan)
        // 墓碑已随本次写回发布到共享文件，旧文件不再承载唯一副本，可以清理。
        deleteLegacyFiles(storage, legacy.removable)
        PlayHistorySyncSettings.lastSyncedAt = System.currentTimeMillis()
    }

    /** 本机侧候选：可同步存储的地址映射 + 全部播放历史 + 本机墓碑账本。 */
    private suspend fun loadLocalState(): SyncMerge.LocalState {
        val normalizedBaseUrlByStorageId = HashMap<Int, String>()
        val storageIdByNormalizedBaseUrl = HashMap<String, Int>()
        for (library in mediaLibraryDao.getAllSuspend()) {
            if (!library.mediaType.isSyncableBase()) continue
            val normalized = normalizeBaseUrl(library.url)
            normalizedBaseUrlByStorageId[library.id] = normalized
            storageIdByNormalizedBaseUrl.putIfAbsent(normalized, library.id)
        }
        return SyncMerge.LocalState(
            records = playHistoryDao.getAll(),
            tombstones = syncDeleteLogDao.getAll().map { SyncDelete(it.recordKey, it.deletedAt) },
            storageIdByNormalizedBaseUrl = storageIdByNormalizedBaseUrl,
            normalizedBaseUrlByStorageId = normalizedBaseUrlByStorageId,
        )
    }

    /** 把折叠结果落到本地数据库。 */
    private suspend fun applyPlan(plan: SyncMerge.MergePlan) {
        plan.deletes.forEach { playHistoryDao.delete(it) }

        plan.updates.forEach { update ->
            SyncMerge.applyRemote(update.entity, update.record)
            playHistoryDao.update(update.entity)
        }

        plan.inserts.forEach { insert ->
            // insert 是 OnConflictStrategy.IGNORE：唯一索引冲突会被静默丢弃，因此先确认不存在
            // 再插，避免"内存认为插入成功、库里其实没有"的假象被后续轮次写回云端。
            if (playHistoryDao.getPlayHistory(insert.uniqueKey, insert.storageId) == null) {
                playHistoryDao.insert(insert.record.toEntity(insert.storageId, insert.uniqueKey))
            }
        }
    }

    // ==================== 旧协议遗留文件 ====================

    /** 旧协议遗留文件的读取结果：[removable] 为可清理的文件，[tombstones] 为已吸收的墓碑来源。 */
    private data class LegacyFiles(
        val removable: List<StorageFile>,
        val tombstones: List<PlayHistorySyncFile>,
    )

    /**
     * 读取 v4 每设备文件（`play_history_<deviceId>.json`，含其 v3 增量文件），**只吸收其中的墓碑**。
     *
     * 为什么只吸收墓碑：记录可以从各设备本地 DB 重建（设备升级后首次同步会重新发布），而旧协议
     * 发布墓碑后本地队列就被清空 —— 墓碑只存在于旧文件里。删除发生时若有设备离线，它本地仍
     * 持有那条记录（它从未删过），此时唯一能拦住"复活"的信息就是这个墓碑。
     *
     * 因此也**不能直接删除**旧文件：先吸收进本次折叠、随共享文件发布出去，再清理。
     * 解析失败的文件（例如 v3 增量格式）保持原样不删，避免丢掉无法解读的墓碑。
     */
    private suspend fun readLegacyFiles(storage: Storage): LegacyFiles {
        val entries = try {
            storage.listFiles(syncDirFile())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "列举同步目录失败，跳过遗留文件处理: ${e.message}")
            return LegacyFiles(emptyList(), emptyList())
        }

        val legacyEntries = entries.filter { !it.isDirectory && it.name.isLegacySyncFile() }
        val absorbed = ArrayList<PlayHistorySyncFile>()
        val removable = ArrayList<StorageFile>()
        for (entry in legacyEntries) {
            val file = readLegacyTombstones(storage, entry) ?: continue
            absorbed += file
            removable += entry
        }
        return LegacyFiles(removable, absorbed)
    }

    /** 读取单个遗留文件并只保留其墓碑；不存在或无法解析时返回 null（保持原文件不动）。 */
    private suspend fun readLegacyTombstones(storage: Storage, entry: StorageFile): PlayHistorySyncFile? {
        val remote = try {
            storage.readFile(entry.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "读取遗留同步文件失败 ${entry.name}: ${e.message}")
            return null
        } ?: return null

        return try {
            syncFileAdapter.fromJson(remote.data.toString(Charsets.UTF_8))
                ?.let { PlayHistorySyncFile(deletes = it.deletes) }
        } catch (e: Exception) {
            Log.w(TAG, "遗留同步文件无法解析，保留不删 ${entry.name}: ${e.message}")
            null
        }
    }

    /** best-effort 删除已吸收的遗留文件；失败仅告警，下轮重试。 */
    private suspend fun deleteLegacyFiles(storage: Storage, entries: List<StorageFile>) {
        for (entry in entries) {
            try {
                if (storage.deleteFile(entry)) {
                    Log.i(TAG, "已清理遗留同步文件 ${entry.name}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "清理遗留同步文件失败 ${entry.name}: ${e.message}")
            }
        }
    }

    // ==================== 传输层辅助 ====================

    /** 连接预检：给出友好的 HTTP / 网络错误提示（始终执行，不再区分手动/自动）。 */
    private suspend fun verifyConnection(storage: Storage) {
        try {
            storage.testConnection()
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebDavHttpException) {
            throw IllegalStateException(context.getString(e.friendlyMessageRes, e.code))
        } catch (e: Exception) {
            throw IllegalStateException(
                context.getString(
                    R.string.sync_error_connect_failed,
                    e.message ?: context.getString(R.string.sync_error_network),
                ),
            )
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

    /** sync 子目录的 [StorageFile] 引用。 */
    private fun syncDirFile(): StorageFile =
        object : AbstractStorageFile(SYNC_SUB_DIR, SYNC_SUB_DIR, true) {}

    private fun reportSuccess(): Boolean {
        val message = context.getString(R.string.sync_success)
        _state.value = SyncUiState.Done(true, message)
        PlayHistorySyncSettings.recordSyncResult(true, message)
        return true
    }

    private fun reportFailure(@StringRes resId: Int): Boolean = reportFailure(context.getString(resId))

    private fun reportFailure(message: String): Boolean {
        _state.value = SyncUiState.Done(false, message)
        PlayHistorySyncSettings.recordSyncResult(false, message)
        return false
    }

    private fun String.isLegacySyncFile(): Boolean =
        this != FILE_NAME && startsWith(LEGACY_DEVICE_PREFIX) && endsWith(JSON_SUFFIX)

    private companion object {
        private const val TAG = "PlayHistorySync"

        /** 复用备份目录，同步文件位于其 sync 子目录（不新增一级目录）。 */
        const val SYNC_ROOT_DIR = "NIplayer_backup"
        const val SYNC_SUB_DIR = "NIplayer_backup/sync"

        /** 云端共享文件（协议 v5）。 */
        const val FILE_NAME = "play_history.json"
        const val FILE_PATH = "$SYNC_SUB_DIR/$FILE_NAME"

        /** v4 每设备文件前缀（其 v3 增量文件同前缀）。 */
        const val LEGACY_DEVICE_PREFIX = "play_history_"
        const val JSON_SUFFIX = ".json"

        /** 自动同步最小间隔（ms）：60 秒时间防抖。 */
        const val MIN_AUTO_INTERVAL_MS = 60 * 1000L
    }
}

/** 将异常转为面向用户的中文提示。 */
private fun Throwable.toUserMessage(context: Context): String = when (this) {
    is WebDavHttpException -> context.getString(friendlyMessageRes, code)
    is SyncPublishException.CorruptRemoteFile -> context.getString(R.string.sync_error_read_remote_file)
    is SyncPublishException.Conflict -> context.getString(R.string.sync_error_conflict)
    is SyncPublishException.UploadFailed -> context.getString(R.string.sync_error_upload_failed)
    is IllegalStateException -> message ?: context.getString(R.string.sync_error_operation_failed)
    else -> message ?: toString()
}
