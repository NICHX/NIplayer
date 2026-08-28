package com.nichx.niplayer.feature.home.history

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.SyncConflictDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.SyncConflictEntity
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity
import com.nichx.niplayer.database.isSyncableBase
import com.nichx.niplayer.database.syncKey
import com.nichx.niplayer.datastore.PlayHistorySyncConfig
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.PlayStartResult
import com.nichx.niplayer.feature.home.PlayStarter
import com.nichx.niplayer.sync.PlayHistorySyncManager
import com.nichx.niplayer.sync.SyncUiState
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.thumbnail.RemoteThumbnailRequest
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.Date

/**
 * 播放历史列表页 ViewModel。
 *
 * 对应旧仓库 `PlayHistoryActivity`（XML + RecyclerView），改为 Compose + Flow 响应式列表。
 *
 * 功能：
 * - [histories] 订阅全量播放历史（[PlayHistoryDao.getAll] → Flow，WhileSubscribed(5000)）
 * - [thumbnailUrls] 历史记录缩略图映射（url → 本地缓存路径）
 * - [resumePlay] 续播：委托 [PlayStarter.startFromHistory] 构造 PlaybackRequest 并写入 Holder
 * - [deleteHistory] 删除单条历史
 * - [clearAll] 清空全部历史
 *
 * @param playHistoryDao 播放历史 Dao
 * @param playStarter 续播封装（@Singleton，从 PlayHistoryEntity 恢复播放链路）
 */
@HiltViewModel
class PlayHistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playHistoryDao: PlayHistoryDao,
    private val videoDao: VideoDao,
    private val playStarter: PlayStarter,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
    private val thumbnailManager: ThumbnailManager,
    private val syncDeleteLogDao: SyncDeleteLogDao,
    private val syncConflictDao: SyncConflictDao,
    private val syncManager: PlayHistorySyncManager,
) : ViewModel() {

    private val historiesFlow = playHistoryDao.getAllFlow()
    private val shieldedPathsFlow = videoDao.getAllFolder()

    /** 全量播放历史（已过滤屏蔽目录），按 play_time 倒序。 */
    val histories: StateFlow<List<PlayHistoryEntity>> = combine(
        historiesFlow, shieldedPathsFlow,
    ) { histories, folders ->
        val shieldedPaths = folders.filter { it.isFilter }.map { it.folderPath }.toSet()
        if (shieldedPaths.isEmpty()) histories
        else histories.filter { h ->
            h.storagePath == null || shieldedPaths.none { prefix ->
                h.storagePath!!.startsWith(prefix)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    /** 视频播放历史（已过滤屏蔽目录）。 */
    val videoHistories: StateFlow<List<PlayHistoryEntity>> = combine(
        histories, shieldedPathsFlow,
    ) { plays, _ ->
        plays.filter { !MediaFileTypes.isAudioFile(it.videoName) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    /** 音频播放历史（已过滤屏蔽目录）。 */
    val audioHistories: StateFlow<List<PlayHistoryEntity>> = combine(
        histories, shieldedPathsFlow,
    ) { plays, _ ->
        plays.filter { MediaFileTypes.isAudioFile(it.videoName) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    /** 缩略图路径映射：播放记录的 url → 本地缓存路径。 */
    private val _thumbnailUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val thumbnailUrls: StateFlow<Map<String, String>> = _thumbnailUrls.asStateFlow()

    /** 数据是否已就绪（避免首帧空列表误显示空状态，触发骨架屏）。 */
    val dataReady: StateFlow<Boolean> = combine(
        historiesFlow, shieldedPathsFlow,
    ) { _, _ -> true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private val _events = MutableSharedFlow<PlayHistoryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlayHistoryEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            histories.collect { items ->
                val currentUrls = items.map { it.url }.toSet()
                if (_thumbnailUrls.value.keys.any { it !in currentUrls }) {
                    _thumbnailUrls.value = _thumbnailUrls.value.filterKeys { it in currentUrls }
                }

                // 先扫描本地缓存（视频 + 音频），已存在的缩略图立即可用
                val uncached = items.filter { it.url !in _thumbnailUrls.value }
                    .filter { it.storageId != null && !it.storagePath.isNullOrEmpty() }
                val cached = uncached.mapNotNull { item ->
                    val path = if (MediaFileTypes.isAudioFile(item.videoName)) {
                        thumbnailManager.getCachedAudioCoverPath(
                            item.storageId!!, item.storagePath!!
                        )
                    } else {
                        thumbnailManager.getCachedThumbnailPath(
                            item.storageId!!, item.storagePath!!
                        )
                    }
                    if (path != null) item.url to path else null
                }.toMap()
                if (cached.isNotEmpty()) {
                    _thumbnailUrls.update { it + cached }
                }

                // 仅对无本地缓存的项走远程生成
                generateRemoteThumbnails(items, _thumbnailUrls.value)
            }
        }

        // 缩略图更新（如退出播放后保存最后一帧）后刷新对应条目，
        // 追加 ?t=timestamp 触发 Compose 重组并绕过 Coil 内存缓存
        viewModelScope.launch {
            thumbnailManager.thumbnailUpdated.collect { updatedPath ->
                val cleanPath = updatedPath.substringBefore("?t=")
                val entry = _thumbnailUrls.value.entries.find {
                    it.value.substringBefore("?t=") == cleanPath
                }
                if (entry != null) {
                    _thumbnailUrls.update {
                        it + (entry.key to "$cleanPath?t=${System.currentTimeMillis()}")
                    }
                }
            }
        }
    }

    /**
     * 为历史记录生成缩略图。
     *
     * BUG-H4 修复：委托 [ThumbnailManager.generateRemoteThumbnails] 批量生成，
     * 按 storageId 分组，每组创建 Storage 实例后传入。ViewModel 仅负责实体转换
     * 与 URL→path 映射 emit，不再持有预加载 / Semaphore / 视频音频分流逻辑。
     *
     * BUG-T-m7 修复：用 batchAccumulator + 节流 flush 合并 onLoaded 回调，
     * 避免 100 个缓存命中 = 100 次 StateFlow emit + 100 次 Map 全量拷贝。
     * flusher 协程每 250ms 批量提交一次，函数返回前最后 flush 一次确保不丢结果。
     */
    private suspend fun generateRemoteThumbnails(
        items: List<PlayHistoryEntity>,
        existingThumbnails: Map<String, String>,
    ) {
        val pending = items.filter {
            it.storageId != null && !it.storagePath.isNullOrEmpty() && it.url !in existingThumbnails
        }
        if (pending.isEmpty()) return

        val byStorage = pending.groupBy { it.storageId!! }
        // BUG-T-m7 修复：批量合并 onLoaded 回调，减少 StateFlow emit 次数
        val batchAccumulator = java.util.Collections.synchronizedMap(mutableMapOf<String, String>())
        coroutineScope {
            val flusher = launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flushThumbnailBatch(batchAccumulator)
                }
            }
            try {
                for ((sid, group) in byStorage) {
                    try {
                        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(sid) } ?: continue
                        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) } ?: continue
                        // BUG-01 修复：缩略图批量生成完成后关闭 Storage，避免 SMB/WebDAV 连接泄漏。
                        try {
                            val requests = group.map { item ->
                                RemoteThumbnailRequest(
                                    storageId = sid,
                                    filePath = item.storagePath ?: "",
                                    fileName = item.videoName,
                                    url = item.url,
                                    isAudio = MediaFileTypes.isAudioFile(item.videoName),
                                )
                            }
                            thumbnailManager.generateRemoteThumbnails(storage, requests) { url, thumbPath ->
                                batchAccumulator[url] = thumbPath
                            }
                        } finally {
                            storage.close()
                        }
                    } catch (_: Exception) { continue }
                }
            } finally {
                flusher.cancel()
                // 最后 flush 一次，确保所有结果都已提交
                flushThumbnailBatch(batchAccumulator)
            }
        }
    }

    /**
     * 批量提交累积的缩略图结果到 [_thumbnailUrls]。
     *
     * BUG-T-m7 修复：通过快照 + clear 原子化取出累积结果，一次性 update 到 StateFlow，
     * 避免每次 onLoaded 都触发 emit + Map 全量拷贝。
     */
    private fun flushThumbnailBatch(accumulator: MutableMap<String, String>) {
        val batch = synchronized(accumulator) {
            if (accumulator.isEmpty()) return@synchronized null
            val snapshot = accumulator.toMap()
            accumulator.clear()
            snapshot
        } ?: return
        if (batch.isNotEmpty()) {
            _thumbnailUrls.update { it + batch }
        }
    }

    /** 续播指定历史记录。 */
    fun resumePlay(history: PlayHistoryEntity) {
        viewModelScope.launch {
            when (val result = playStarter.startFromHistory(history)) {
                is PlayStartResult.Success ->
                    _events.tryEmit(PlayHistoryEvent.NavigateToPlayer)

                is PlayStartResult.Error ->
                    _events.tryEmit(PlayHistoryEvent.ShowError(result.message))
            }
        }
    }

    /** 删除单条历史，删除前记录同步 tombstone。 */
    fun deleteHistory(id: Int) {
        viewModelScope.launch {
            val entity = playHistoryDao.getById(id) ?: return@launch
            recordDeleteTombstone(entity)
            playHistoryDao.delete(id)
        }
    }

    /** 清空全部历史，逐条记录同步 tombstone。 */
    fun clearAll() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            playHistoryDao.getAll().forEach { entity -> recordDeleteTombstone(entity, now) }
            playHistoryDao.deleteAll()
        }
    }

    /**
     * 为删除写云同步 tombstone。
     *
     * BUG-B：record_key 必须是设备无关的 syncKey（归一化存储地址 + 存储内相对路径），而非
     * 本地 storageId+uniqueKey，否则其他设备无法匹配到同一条记录。仅全身可同步的存储
     *（SMB / WebDAV / Other）参与；用 REPLACE 刷新时间，避免"删除→重扫→再删"时旧 tombstone 过旧。
     */
    private suspend fun recordDeleteTombstone(
        entity: PlayHistoryEntity,
        deletedAt: Long = System.currentTimeMillis(),
    ) {
        val sid = entity.storageId ?: return
        val path = entity.storagePath ?: return
        val library = mediaLibraryDao.getById(sid) ?: return
        if (!library.mediaType.isSyncableBase()) return
        syncDeleteLogDao.insertOrReplace(
            SyncDeleteLogEntity(
                tableName = TABLE_PLAY_HISTORY,
                recordKey = syncKey(library.url, path),
                deletedAt = deletedAt,
            ),
        )
    }

    /** 云同步状态（驱动 TopBar 同步按钮指示器）。 */
    val syncState: StateFlow<SyncUiState> = syncManager.state

    /** 云同步配置（是否启用，决定 TopBar 是否显示同步按钮）。 */
    val syncConfig: StateFlow<PlayHistorySyncConfig> = PlayHistorySyncSettings.flow

    /** 手动触发一次云同步。 */
    fun syncNow() {
        viewModelScope.launch {
            syncManager.sync()
        }
    }

    /** 未解决的同步冲突列表（供播放历史页冲突提示）。 */
    val conflicts: StateFlow<List<SyncConflictEntity>> = syncConflictDao.getUnresolvedFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * 解决冲突：保留本机版本。
     *
     * 将冲突现场中的本机快照（播放位置 / 时长 / 播放时间）写回 play_history 记录，
     * 并刷新 updated_at，下次同步时会以新版本传播到其他设备。
     */
    fun resolveConflictKeepLocal(conflict: SyncConflictEntity) {
        viewModelScope.launch {
            conflict.storageId?.let { sid ->
                val record = playHistoryDao.getPlayHistory(conflict.uniqueKey, sid)
                if (record != null) {
                    record.videoPosition = conflict.localVideoPosition
                    record.videoDuration = conflict.localVideoDuration
                    record.playTime = Date(conflict.localPlayTime)
                    record.updatedAt = System.currentTimeMillis()
                    playHistoryDao.update(record)
                }
            }
            syncConflictDao.delete(conflict)
            _events.tryEmit(PlayHistoryEvent.Toast(context.getString(R.string.play_history_keep_local)))
        }
    }

    /**
     * 解决冲突：保留云端版本。
     *
     * 记录当前已处于 LWW 胜者（远端）状态，仅清除冲突记录。
     */
    fun resolveConflictKeepRemote(conflict: SyncConflictEntity) {
        viewModelScope.launch {
            syncConflictDao.delete(conflict)
            _events.tryEmit(PlayHistoryEvent.Toast(context.getString(R.string.play_history_keep_remote)))
        }
    }

    private companion object {
        /** 批量提交缩略图结果的间隔（ms）。降低 StateFlow emit 次数，减少 Compose 重组。 */
        const val FLUSH_INTERVAL_MS = 250L

        const val TABLE_PLAY_HISTORY = "play_history"
    }
}

/** 一次性事件（导航、错误提示、Toast），由 [PlayHistoryScreen] collect。 */
sealed class PlayHistoryEvent {
    /** 播放请求已就绪，导航到播放页。 */
    object NavigateToPlayer : PlayHistoryEvent()

    /** 恢复播放失败，显示错误提示。 */
    data class ShowError(val message: String) : PlayHistoryEvent()

    /** 显示 Toast 信息。 */
    data class Toast(val message: String) : PlayHistoryEvent()
}
