package com.nichx.niplayer.storage.download

import android.content.Context
import android.net.Uri
import com.nichx.niplayer.database.dao.UploadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.database.entity.UploadTaskEntity
import com.nichx.niplayer.storage.StorageFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 上传管理引擎（本地文件 → 远程存储）。
 *
 * 与 [DownloadManager] 双引擎并行：本类专门负责上传，二者由统一的"传输管理"入口聚合展示。
 *
 * 核心职责：
 * - **App 级作用域**：任务在 [scope]（SupervisorJob + IO）调度，**切出上传触发页面后仍继续执行**，
 *   重新进入页面通过 [UploadTaskDao.getAllFlow] 恢复进度；
 * - **进度上报**：底层 [Storage.uploadFile] 实时回调，写盘节流更新 [taskProgress]（UI 用）
 *   与 DB（500ms 节流）；[taskSpeeds] 提供实时速度（bytes/sec，1s 采样窗口）；
 * - **暂停/恢复**：暂停取消协程并置 PAUSED（保留 uploadedBytes）；恢复置 WAITING 后由调度循环
 *   接管，已上传部分通过 `offset` 走协议级断点续传（SMB 支持，WebDAV 从头重传）；
 * - **取消语义**：用户取消 → CANCELLED；
 * - **完成事件**：通过 [completions] 发出，供 UI 层转成全局 Snackbar 通知（"已上传 XX"）。
 */
@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageFactory: StorageFactory,
    private val uploadTaskDao: UploadTaskDao,
    private val mediaLibraryDao: MediaLibraryDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 实时已上传字节（taskId → uploadedBytes），独立于 DB 持久化，用于进度条。 */
    private val _taskProgress = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val taskProgress: StateFlow<Map<Long, Long>> = _taskProgress.asStateFlow()

    /** 实时上传速度（taskId → bytes/sec，1s 采样窗口），用于 UI 速度显示。 */
    private val _taskSpeeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val taskSpeeds: StateFlow<Map<Long, Long>> = _taskSpeeds.asStateFlow()

    /** 活跃上传任务数（WAITING + DOWNLOADING），用于文件浏览页角标 / 多选上传提示。 */
    val activeUploadCount: StateFlow<Int> = uploadTaskDao
        .countByStatesFlow(listOf(DownloadState.WAITING, DownloadState.DOWNLOADING))
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** 全部上传任务列表（UI 渲染），按创建时间倒序。 */
    val tasks: StateFlow<List<UploadTaskEntity>> = uploadTaskDao
        .getAllFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 完成/失败的上传任务事件，UI 层 collect 后转全局消息通知。 */
    private val _completions = MutableSharedFlow<UploadTaskEntity>(extraBufferCapacity = 16)
    val completions: SharedFlow<UploadTaskEntity> = _completions.asSharedFlow()

    /** 活跃上传任务协程。 */
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /** 用户主动取消的任务集合（用于 CancellationException 分支）。 */
    private val cancellingTasks = ConcurrentHashMap<Long, Boolean>()

    /** 用户主动暂停的任务集合（用于 CancellationException 分支 → PAUSED）。 */
    private val pausingTasks = ConcurrentHashMap<Long, Boolean>()

    /** 每任务速度估计器。 */
    private val speedTrackers = ConcurrentHashMap<Long, SpeedTracker>()

    init {
        startDispatchLoop()
    }

    /** 调度循环：轮询 WAITING 任务，在并发限额内启动 [processTask]。 */
    private fun startDispatchLoop() {
        scope.launch {
            while (true) {
                if (activeJobs.size >= MAX_CONCURRENT) {
                    delay(500)
                    continue
                }
                val waiting = uploadTaskDao.getByStates(listOf(DownloadState.WAITING))
                    .sortedBy { it.id }
                if (waiting.isEmpty()) {
                    delay(500)
                    continue
                }
                for (task in waiting.take(MAX_CONCURRENT - activeJobs.size)) {
                    if (activeJobs.containsKey(task.id)) continue
                    val job = scope.launch { processTask(task) }
                    activeJobs[task.id] = job
                    job.invokeOnCompletion { activeJobs.remove(task.id) }
                }
                delay(200)
            }
        }
    }

    /**
     * 添加上传任务。
     *
     * @param storageId 目标存储源 ID
     * @param storageName 目标存储源显示名
     * @param fileName 文件名
     * @param remotePath 远程目标路径（相对存储库根，含文件名）
     * @param sourceUri 本地源文件 Uri（SAF content://）
     * @param totalBytes 文件总字节数（未知传 -1）
     * @return 新任务 id
     */
    suspend fun enqueue(
        storageId: Int,
        storageName: String,
        fileName: String,
        remotePath: String,
        sourceUri: String,
        totalBytes: Long,
    ): Long {
        val task = UploadTaskEntity(
            storageId = storageId,
            storageName = storageName,
            fileName = fileName,
            remotePath = remotePath,
            sourceUri = sourceUri,
            totalBytes = if (totalBytes > 0) totalBytes else 0,
        )
        return uploadTaskDao.insert(task)
    }

    /** 取消任务（用户主动）。 */
    fun cancel(taskId: Long) {
        val job = activeJobs[taskId]
        if (job == null || !job.isActive) {
            // WAITING / PAUSED 等无活跃协程：直接置 CANCELLED
            scope.launch { uploadTaskDao.updateState(taskId, DownloadState.CANCELLED, null) }
        } else {
            cancellingTasks[taskId] = true
            job.cancel()
        }
    }

    /** 暂停任务：中断传输并置 PAUSED（保留 uploadedBytes，恢复时断点续传）。 */
    fun pause(taskId: Long) {
        val job = activeJobs[taskId]
        if (job == null || !job.isActive) {
            scope.launch { uploadTaskDao.updateState(taskId, DownloadState.PAUSED, null) }
        } else {
            pausingTasks[taskId] = true
            job.cancel()
        }
    }

    /** 恢复任务：仅 PAUSED 态可恢复，置 WAITING 后由调度循环自动接管。 */
    fun resume(taskId: Long) {
        scope.launch {
            val task = uploadTaskDao.getById(taskId) ?: return@launch
            if (task.state != DownloadState.PAUSED) return@launch
            uploadTaskDao.updateState(taskId, DownloadState.WAITING, null)
        }
    }

    /** 删除任务记录（同时中断活跃传输）。 */
    suspend fun delete(taskId: Long) {
        activeJobs[taskId]?.let { job ->
            cancellingTasks[taskId] = true
            job.cancel()
        }
        speedTrackers.remove(taskId)
        uploadTaskDao.deleteById(taskId)
    }

    /** 清除所有已完成任务。 */
    suspend fun clearCompleted() {
        uploadTaskDao.deleteByState(DownloadState.COMPLETED)
    }

    /** 清除所有失败任务。 */
    suspend fun clearFailed() {
        uploadTaskDao.deleteByState(DownloadState.FAILED)
    }

    private suspend fun processTask(task: UploadTaskEntity) {
        val storageId = task.storageId
        var lastProgressEmit = 0L
        var lastDbWrite = 0L
        // 防竞态：调度读取 WAITING 列表与 launch 之间用户可能已取消/暂停，重读状态校验
        val current = uploadTaskDao.getById(task.id) ?: return
        if (current.state != DownloadState.WAITING) return
        val effectiveTask = current
        speedTrackers[task.id] = SpeedTracker()
        try {
            val library = mediaLibraryDao.getById(storageId)
                ?: throw IllegalStateException("存储源不存在: $storageId")
            val storage = storageFactory.create(library)
                ?: throw IllegalStateException("存储源不支持上传: $storageId")

            uploadTaskDao.updateState(task.id, DownloadState.DOWNLOADING, null)
            // 断点续传：仅当已上传部分在 (0, totalBytes) 内才从该偏移继续，否则从头传
            val resumeOffset = if (effectiveTask.uploadedBytes in 1 until effectiveTask.totalBytes) effectiveTask.uploadedBytes else 0L
            if (resumeOffset > 0) {
                uploadTaskDao.updateProgress(task.id, resumeOffset, DownloadState.DOWNLOADING)
            }
            _taskProgress.update { it + (task.id to resumeOffset) }

            // 注意：不能再用 runCatching 包裹传输，否则会吞掉 CancellationException，
            // 导致暂停/取消无法终止阻塞 IO（旧实现取消后仍会上传到完成）。
            val ok = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(Uri.parse(effectiveTask.sourceUri))
                    ?: return@withContext false
                storage.uploadFile(
                    remotePath = effectiveTask.remotePath,
                    inputStream = input,
                    totalBytes = effectiveTask.totalBytes,
                    offset = resumeOffset,
                    onProgress = { bytes ->
                        val now = System.currentTimeMillis()
                        if (now - lastProgressEmit >= PROGRESS_EMIT_INTERVAL_MS) {
                            _taskProgress.update { it + (task.id to bytes) }
                            lastProgressEmit = now
                        }
                        speedTrackers[task.id]?.let { tracker ->
                            val speed = tracker.update(bytes, now)
                            if (speed > 0) {
                                _taskSpeeds.update { it + (task.id to speed) }
                            }
                        }
                        if (now - lastDbWrite >= DB_THROTTLE_MS) {
                            lastDbWrite = now
                            scope.launch {
                                uploadTaskDao.updateProgress(task.id, bytes, DownloadState.DOWNLOADING)
                            }
                        }
                    },
                )
            }

            if (ok) {
                uploadTaskDao.updateProgress(task.id, effectiveTask.totalBytes.coerceAtLeast(0L), DownloadState.COMPLETED)
                _taskProgress.update { it - task.id }
                _taskSpeeds.update { it - task.id }
                speedTrackers.remove(task.id)
                _completions.tryEmit(task.copy(state = DownloadState.COMPLETED))
            } else {
                uploadTaskDao.updateState(task.id, DownloadState.FAILED, "upload failed")
                _taskProgress.update { it - task.id }
                _taskSpeeds.update { it - task.id }
                speedTrackers.remove(task.id)
                _completions.tryEmit(task.copy(state = DownloadState.FAILED))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            when {
                cancellingTasks.remove(task.id) == true -> {
                    uploadTaskDao.updateState(task.id, DownloadState.CANCELLED, null)
                }
                pausingTasks.remove(task.id) == true -> {
                    // 保留 uploadedBytes（DB 节流写盘），供恢复时断点续传
                    uploadTaskDao.updateState(task.id, DownloadState.PAUSED, null)
                }
                else -> {
                    uploadTaskDao.updateState(task.id, DownloadState.FAILED, "cancelled/unexpected")
                }
            }
            _taskProgress.update { it - task.id }
            _taskSpeeds.update { it - task.id }
            speedTrackers.remove(task.id)
        } catch (e: Exception) {
            uploadTaskDao.updateState(task.id, DownloadState.FAILED, e.message)
            _taskProgress.update { it - task.id }
            _taskSpeeds.update { it - task.id }
            speedTrackers.remove(task.id)
            _completions.tryEmit(task.copy(state = DownloadState.FAILED))
        } finally {
            cancellingTasks.remove(task.id)
            pausingTasks.remove(task.id)
        }
    }

    private companion object {
        const val MAX_CONCURRENT = 3
        const val DB_THROTTLE_MS = 500L
        const val PROGRESS_EMIT_INTERVAL_MS = 200L
    }
}

/** 每任务速度估计器：每 [SAMPLE_MS] 计算一次瞬时速度（bytes/sec）。 */
private class SpeedTracker {
    private var lastBytes = 0L
    private var lastTime = 0L
    private var speed = 0L

    fun update(bytes: Long, now: Long): Long {
        if (lastTime == 0L) {
            lastBytes = bytes
            lastTime = now
            return speed
        }
        val dt = now - lastTime
        if (dt >= SAMPLE_MS) {
            val delta = bytes - lastBytes
            speed = if (delta >= 0) delta * 1000 / dt else 0L
            lastBytes = bytes
            lastTime = now
        }
        return speed
    }

    private companion object {
        const val SAMPLE_MS = 1000L
    }
}