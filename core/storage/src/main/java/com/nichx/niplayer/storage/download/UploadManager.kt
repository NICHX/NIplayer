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
 * - **进度上报**：底层 [Storage.uploadFile] 经 [CountingInputStream] 实时回调，写盘节流更新
 *   [taskProgress]（UI 用）与 DB（500ms 节流）；
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

    /** 活跃上传任务数（WAITING + TRANSFERRING），用于文件浏览页角标 / 多选上传提示。 */
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
        cancellingTasks[taskId] = true
        activeJobs[taskId]?.cancel()
        scope.launch {
            uploadTaskDao.updateState(taskId, DownloadState.CANCELLED, null)
        }
    }

    /** 删除任务记录。 */
    suspend fun delete(taskId: Long) {
        activeJobs[taskId]?.cancel()
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
        var lastDbWrite = 0L
        try {
            val library = mediaLibraryDao.getById(storageId)
                ?: throw IllegalStateException("存储源不存在: $storageId")
            val storage = storageFactory.create(library)
                ?: throw IllegalStateException("存储源不支持上传: $storageId")

            uploadTaskDao.updateState(task.id, DownloadState.DOWNLOADING, null)
            _taskProgress.update { it + (task.id to (task.uploadedBytes.takeIf { b -> b > 0 } ?: 0L)) }

            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(Uri.parse(task.sourceUri))
                    if (input == null) {
                        false
                    } else {
                        storage.uploadFile(
                            remotePath = task.remotePath,
                            inputStream = input,
                            totalBytes = task.totalBytes,
                            onProgress = { bytes ->
                                _taskProgress.update { it + (task.id to bytes) }
                                val now = System.currentTimeMillis()
                                if (now - lastDbWrite >= DB_THROTTLE_MS) {
                                    lastDbWrite = now
                                    scope.launch {
                                        uploadTaskDao.updateProgress(task.id, bytes, DownloadState.DOWNLOADING)
                                    }
                                }
                            },
                        )
                    }
                }.getOrDefault(false)
            }

            if (ok) {
                uploadTaskDao.updateProgress(task.id, task.totalBytes.coerceAtLeast(0L), DownloadState.COMPLETED)
                _taskProgress.update { it - task.id }
                _completions.tryEmit(task.copy(state = DownloadState.COMPLETED))
            } else {
                uploadTaskDao.updateState(task.id, DownloadState.FAILED, "upload failed")
                _taskProgress.update { it - task.id }
                _completions.tryEmit(task.copy(state = DownloadState.FAILED))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (cancellingTasks.remove(task.id) == true) {
                uploadTaskDao.updateState(task.id, DownloadState.CANCELLED, null)
                _taskProgress.update { it - task.id }
            } else {
                uploadTaskDao.updateState(task.id, DownloadState.FAILED, "cancelled/unexpected")
                _taskProgress.update { it - task.id }
            }
        } catch (e: Exception) {
            uploadTaskDao.updateState(task.id, DownloadState.FAILED, e.message)
            _taskProgress.update { it - task.id }
            _completions.tryEmit(task.copy(state = DownloadState.FAILED))
        } finally {
            cancellingTasks.remove(task.id)
        }
    }

    private companion object {
        const val MAX_CONCURRENT = 3
        const val DB_THROTTLE_MS = 500L
    }
}