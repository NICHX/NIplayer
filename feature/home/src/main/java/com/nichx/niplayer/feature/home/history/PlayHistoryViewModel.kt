package com.nichx.niplayer.feature.home.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity
import com.nichx.niplayer.datastore.PlayHistorySyncConfig
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.PlayStarter
import com.nichx.niplayer.sync.PlayHistorySyncManager
import com.nichx.niplayer.sync.SyncUiState
import com.nichx.niplayer.sync.recordKey
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.thumbnail.RemoteThumbnailRequest
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val playHistoryDao: PlayHistoryDao,
    private val videoDao: VideoDao,
    private val playStarter: PlayStarter,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
    private val thumbnailManager: ThumbnailManager,
    private val syncDeleteLogDao: SyncDeleteLogDao,
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
                is PlayStarter.StartResult.Success ->
                    _events.tryEmit(PlayHistoryEvent.NavigateToPlayer)

                is PlayStarter.StartResult.Error ->
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
            playHistoryDao.getAll().forEach { entity ->
                entity.storageId?.let { sid ->
                    syncDeleteLogDao.insert(
                        SyncDeleteLogEntity(
                            tableName = TABLE_PLAY_HISTORY,
                            recordKey = recordKey(sid, entity.uniqueKey),
                            deletedAt = now,
                        ),
                    )
                }
            }
            playHistoryDao.deleteAll()
        }
    }

    private suspend fun recordDeleteTombstone(entity: PlayHistoryEntity) {
        entity.storageId?.let { sid ->
            syncDeleteLogDao.insert(
                SyncDeleteLogEntity(
                    tableName = TABLE_PLAY_HISTORY,
                    recordKey = recordKey(sid, entity.uniqueKey),
                    deletedAt = System.currentTimeMillis(),
                ),
            )
        }
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

    /** 清除同步结果指示（短暂展示后消退）。 */
    fun dismissSyncResult() {
        syncManager.dismissResult()
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
