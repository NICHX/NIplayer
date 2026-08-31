package com.nichx.niplayer.storage.download

import android.content.Context
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.database.entity.DownloadTaskEntity
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.R
import com.nichx.niplayer.storage.StorageFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载管理引擎。
 *
 * 替代旧仓库 `common_component/storage/download/DownloadManager.kt` 的 object 单例，
 * 改为 Hilt @Singleton，通过构造注入 [StorageFactory] / [DownloadTaskDao] / [MediaLibraryDao]。
 *
 * 核心职责：
 * - **调度循环**：每 200ms 轮询 WAITING 任务，最多 [MAX_CONCURRENT] 个并发下载
 * - **三种目标模式**：缓存目录（targetStorageUrl=null）/ 直 path（file://）/ SAF（content://）
 * - **断点续传**：优先使用 [com.nichx.niplayer.storage.Storage.openInputStream] 的 offset 重载；
 *   不支持时回退到完整下载（offset=0）
 * - **节流进度**：StateFlow 200ms / DB 500ms / 8MB 阈值刷新，避免频繁 IO 和 UI 重组
 * - **取消语义**：[cancellingTasks] 区分用户取消（→ CANCELLED + 删文件）与暂停（→ PAUSED，保留文件）
 *
 * UI 层（DownloadManagerViewModel）通过 [DownloadTaskDao.getAllFlow] 获取任务列表，
 * 通过 [taskProgress] 获取实时下载字节数，二者 combine 后计算进度/速度/ETA。
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageFactory: StorageFactory,
    private val downloadTaskDao: DownloadTaskDao,
    private val mediaLibraryDao: MediaLibraryDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 实时下载字节数（taskId → downloadedBytes），用于 UI 进度展示，独立于 DB 持久化。 */
    private val _taskProgress = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val taskProgress: StateFlow<Map<Long, Long>> = _taskProgress.asStateFlow()

    /** 活跃下载任务数（WAITING + DOWNLOADING），用于文件浏览页角标显示。 */
    val activeDownloadCount: StateFlow<Int> = downloadTaskDao
        .countByStatesFlow(listOf(DownloadState.WAITING, DownloadState.DOWNLOADING))
        .stateIn(scope, SharingStarted.Eagerly, 0)

    /** 活跃下载任务协程。 */
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /** 用户主动取消（区别于暂停）的任务集合，用于 CancellationException 处理分支。 */
    private val cancellingTasks = ConcurrentHashMap<Long, Boolean>()

    init {
        startDispatchLoop()
    }

    /**
     * 调度循环：轮询 WAITING 任务，在并发限额内启动 [processTask]。
     *
     * 无 WAITING 任务时空转等待，避免 CPU 空耗。
     */
    private fun startDispatchLoop() {
        scope.launch {
            while (true) {
                val activeCount = activeJobs.size
                if (activeCount >= MAX_CONCURRENT) {
                    delay(500)
                    continue
                }
                val waitingTasks = downloadTaskDao.getByStates(listOf(DownloadState.WAITING))
                    .sortedBy { it.id }
                if (waitingTasks.isEmpty()) {
                    delay(500)
                    continue
                }
                for (task in waitingTasks.take(MAX_CONCURRENT - activeCount)) {
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
     * 添加下载任务。
     *
     * 去重：按 uniqueKey + storageId + targetStorageUrl 查找已有任务，
     * 若已存在且处于活跃态（WAITING/DOWNLOADING/PAUSED）则忽略；
     * 已结束态（COMPLETED/CANCELLED/FAILED）则删除旧记录后重新插入。
     */
    fun addTask(
        storageId: Int,
        filePath: String,
        fileName: String,
        uniqueKey: String,
        totalBytes: Long,
        targetStorageUrl: String? = null,
        targetStorageName: String? = null,
    ) {
        scope.launch {
            val existing = downloadTaskDao.getByUniqueKeyAndTarget(uniqueKey, storageId, targetStorageUrl)
            if (existing != null) {
                if (existing.state in listOf(
                        DownloadState.COMPLETED,
                        DownloadState.CANCELLED,
                        DownloadState.FAILED,
                    )
                ) {
                    downloadTaskDao.deleteById(existing.id)
                } else {
                    return@launch
                }
            }
            downloadTaskDao.insert(
                DownloadTaskEntity(
                    storageId = storageId,
                    fileName = fileName,
                    filePath = filePath,
                    uniqueKey = uniqueKey,
                    totalBytes = totalBytes,
                    state = DownloadState.WAITING,
                    targetStorageUrl = targetStorageUrl,
                    targetStorageName = targetStorageName,
                )
            )
        }
    }

    /** 暂停任务：取消协程 + 置 PAUSED（保留已下载文件供续传）。 */
    fun pauseTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        scope.launch { downloadTaskDao.updateState(taskId, DownloadState.PAUSED) }
    }

    /** 恢复任务：仅 PAUSED 态可恢复，置 WAITING 后调度循环自动接管。 */
    fun resumeTask(taskId: Long) {
        scope.launch {
            val task = downloadTaskDao.getById(taskId) ?: return@launch
            if (task.state != DownloadState.PAUSED) return@launch
            downloadTaskDao.updateState(taskId, DownloadState.WAITING)
        }
    }

    /** 取消任务：取消协程 + 删除已下载文件 + 置 CANCELLED。 */
    fun cancelTask(taskId: Long) {
        cancellingTasks[taskId] = true
        activeJobs[taskId]?.cancel()
        scope.launch {
            val task = downloadTaskDao.getById(taskId)
            if (task != null) deleteTaskFile(task)
            downloadTaskDao.updateState(taskId, DownloadState.CANCELLED)
            cancellingTasks.remove(taskId)
        }
    }

    /** 删除任务：取消协程 + 删除文件 + 删除数据库记录。 */
    fun deleteTask(taskId: Long) {
        cancellingTasks[taskId] = true
        activeJobs[taskId]?.cancel()
        scope.launch {
            val task = downloadTaskDao.getById(taskId)
            if (task != null) deleteTaskFile(task)
            downloadTaskDao.deleteById(taskId)
            cancellingTasks.remove(taskId)
        }
    }

    /** 仅清除任务记录（不删文件），用于已完成/已取消任务的列表清理。 */
    fun clearRecord(taskId: Long) {
        scope.launch { downloadTaskDao.deleteById(taskId) }
    }

    /** 清除所有已完成任务记录（不删文件）。 */
    fun removeCompletedTasks() {
        scope.launch { downloadTaskDao.deleteByState(DownloadState.COMPLETED) }
    }

    /** 重试失败任务：重置进度为 0 + 置 WAITING。 */
    fun retryTask(taskId: Long) {
        scope.launch {
            val task = downloadTaskDao.getById(taskId) ?: return@launch
            if (task.state != DownloadState.FAILED) return@launch
            downloadTaskDao.updateProgress(taskId, 0, DownloadState.WAITING)
        }
    }

    /** 重试所有失败任务。 */
    fun retryAllFailed() {
        scope.launch {
            val failed = downloadTaskDao.getByStates(listOf(DownloadState.FAILED))
            for (task in failed) {
                downloadTaskDao.updateProgress(task.id, 0, DownloadState.WAITING)
            }
        }
    }

    /** 清除所有失败 + 已取消任务记录。 */
    fun clearFailed() {
        scope.launch {
            downloadTaskDao.deleteByState(DownloadState.FAILED)
            downloadTaskDao.deleteByState(DownloadState.CANCELLED)
        }
    }

    /**
     * 处理单个下载任务。
     *
     * 流程：创建 Storage → 构造 StorageFile → 断点续传打开流 → 按目标模式写入 → 完成/失败/取消。
     *
     * 异常处理：
     * - [CancellationException]：区分取消（CANCELLED + 删文件）与暂停（PAUSED，保留文件）
     * - 其他异常：置 FAILED + 记录错误信息
     */
    private suspend fun processTask(task: DownloadTaskEntity) {
        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(task.storageId) }
            ?: run {
                downloadTaskDao.updateState(task.id, DownloadState.FAILED, context.getString(R.string.download_error_storage_deleted))
                return
            }

        val storage = storageFactory.create(library)
            ?: run {
                downloadTaskDao.updateState(task.id, DownloadState.FAILED, context.getString(R.string.download_error_storage_connect))
                return
            }

        // 构造 StorageFile：下载只需要 path，openInputStream 内部按 path 解析远程资源
        val storageFile = object : AbstractStorageFile(
            path = task.filePath,
            name = task.fileName,
            isDirectory = false,
            length = task.totalBytes,
        ) {}

        var totalBytes = task.totalBytes
        if (totalBytes <= 0) {
            // totalBytes 未知时无法计算百分比，但下载仍可进行
            // WebDAV/SMB 的 StorageFile.length 在 listFiles 时已填充，正常情况不会为 0
        }

        downloadTaskDao.updateState(task.id, DownloadState.DOWNLOADING)

        // 断点续传：优先用 offset 重载打开流；不支持时回退到完整下载
        var actualOffset = task.downloadedBytes
        val inputStream: InputStream = try {
            if (actualOffset > 0) {
                val offsetStream = try {
                    storage.openInputStream(storageFile, actualOffset)
                } catch (_: Exception) { null }
                if (offsetStream != null) {
                    offsetStream
                } else {
                    // 不支持续传，从头下载
                    actualOffset = 0
                    downloadTaskDao.updateProgress(task.id, 0, DownloadState.DOWNLOADING)
                    storage.openInputStream(storageFile)
                }
            } else {
                storage.openInputStream(storageFile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            downloadTaskDao.updateState(task.id, DownloadState.FAILED, e.message ?: context.getString(R.string.download_error_open_stream))
            storage.close()
            return
        }

        try {
            val storageUrl = task.targetStorageUrl
            when {
                storageUrl == null -> processToCache(task, inputStream, actualOffset, totalBytes)
                storageUrl.startsWith("file://") -> processToDirectPath(task, inputStream, actualOffset, totalBytes)
                // 下载目录已统一为 file:// 绝对路径（原生直写），遗留的 content:// 目标不受支持
                else -> throw Exception(context.getString(R.string.download_error_target_unsupported))
            }
        } catch (e: CancellationException) {
            if (cancellingTasks.remove(task.id) != null) {
                downloadTaskDao.updateState(task.id, DownloadState.CANCELLED)
            } else {
                downloadTaskDao.updateState(task.id, DownloadState.PAUSED)
            }
        } catch (e: Exception) {
            downloadTaskDao.updateState(task.id, DownloadState.FAILED, e.message ?: context.getString(R.string.download_error_failed))
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            try { storage.close() } catch (_: Exception) {}
            _taskProgress.update { it.toMutableMap().apply { remove(task.id) } }
        }
    }

    /** 下载到应用缓存目录 `<cache>/download/<fileName>`。 */
    private suspend fun processToCache(
        task: DownloadTaskEntity,
        inputStream: InputStream,
        offset: Long,
        totalBytes: Long,
    ) {
        val targetFile = File(context.cacheDir, "download/${task.fileName}")
        targetFile.parentFile?.mkdirs()
        if (offset > 0) {
            if (!targetFile.exists()) targetFile.createNewFile()
        } else {
            targetFile.delete()
            targetFile.createNewFile()
        }
        try {
            FileOutputStream(targetFile, offset > 0).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use {
                    pipelinedWriteLoop(task.id, it, inputStream, offset, totalBytes)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException && targetFile.exists()) targetFile.delete()
            throw e
        }
    }

    /** 下载到直 path（targetStorageUrl 以 file:// 开头，原生直写共享存储）。 */
    private suspend fun processToDirectPath(
        task: DownloadTaskEntity,
        inputStream: InputStream,
        offset: Long,
        totalBytes: Long,
    ) {
        val dirPath = task.targetStorageUrl!!.removePrefix("file://")
        val targetFile = File(dirPath, task.fileName)
        targetFile.parentFile?.mkdirs()
        if (offset > 0) {
            if (!targetFile.exists()) targetFile.createNewFile()
        } else {
            targetFile.delete()
            targetFile.createNewFile()
        }
        try {
            FileOutputStream(targetFile, offset > 0).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use {
                    pipelinedWriteLoop(task.id, it, inputStream, offset, totalBytes)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException && targetFile.exists()) targetFile.delete()
            throw e
        }
    }

    /**
     * 写入循环：读输入流 → 写输出流，节流刷新进度。
     *
     * - **StateFlow 进度**：每 [PROGRESS_EMIT_INTERVAL_MS] 更新一次 [taskProgress]（UI 用）
     * - **DB 进度**：每 [DB_FLUSH_INTERVAL_MS] 或 [FLUSH_BYTE_THRESHOLD] 刷新一次（断电恢复用）
     * - **流 flush**：仅按 [STREAM_FLUSH_BYTE_THRESHOLD] 字节阈值触发，不再与 DB 写入绑定，
     *   避免 fsync 停顿拖慢下载吞吐（SAF content:// 路径尤其明显）
     * - **完成**：flush 输出流 + DB 置 COMPLETED + 移除 taskProgress 条目
     */
    private suspend fun pipelinedWriteLoop(
        taskId: Long,
        outputStream: OutputStream,
        inputStream: InputStream,
        offset: Long,
        totalBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = offset
        var lastFlushTime = System.currentTimeMillis()
        var lastProgressEmitTime = System.currentTimeMillis()
        var bytesSinceLastFlush = 0L
        var bytesSinceLastStreamFlush = 0L

        while (true) {
            val len = inputStream.read(buffer)
            if (len == -1) break
            outputStream.write(buffer, 0, len)
            totalRead += len
            bytesSinceLastFlush += len
            bytesSinceLastStreamFlush += len

            val now = System.currentTimeMillis()
            if (now - lastProgressEmitTime >= PROGRESS_EMIT_INTERVAL_MS) {
                _taskProgress.update {
                    it.toMutableMap().apply {
                        this[taskId] = totalRead
                    }
                }
                lastProgressEmitTime = now
            }

            // DB 进度写入：断电恢复用，不触发流 flush
            if (now - lastFlushTime >= DB_FLUSH_INTERVAL_MS || bytesSinceLastFlush >= FLUSH_BYTE_THRESHOLD) {
                downloadTaskDao.updateProgress(taskId, totalRead, DownloadState.DOWNLOADING)
                lastFlushTime = now
                bytesSinceLastFlush = 0L
            }

            // 流 flush：仅按字节阈值触发，降低 fsync 频率提升吞吐
            if (bytesSinceLastStreamFlush >= STREAM_FLUSH_BYTE_THRESHOLD) {
                outputStream.flush()
                bytesSinceLastStreamFlush = 0L
            }
        }
        outputStream.flush()
        downloadTaskDao.updateProgress(taskId, totalRead, DownloadState.COMPLETED)
    }

    /**
     * 删除任务已下载的文件。
     *
     * 按目标模式分发：
     * - null（缓存）：删 `<cache>/download/<fileName>`
     * - file://：删直 path 文件
     */
    private fun deleteTaskFile(task: DownloadTaskEntity) {
        val storageUrl = task.targetStorageUrl
        when {
            storageUrl == null -> {
                File(context.cacheDir, "download/${task.fileName}").delete()
            }
            else -> {
                val dirPath = storageUrl.removePrefix("file://")
                File(dirPath, task.fileName).takeIf { it.exists() }?.delete()
            }
        }
    }

    private companion object {
        const val MAX_CONCURRENT = 6
        const val BUFFER_SIZE = 1024 * 1024
        const val DB_FLUSH_INTERVAL_MS = 500L
        const val PROGRESS_EMIT_INTERVAL_MS = 200L
        const val FLUSH_BYTE_THRESHOLD = 8 * 1024 * 1024L
        // 流 flush 字节阈值：每 32MB 才触发一次 fsync，降低停顿频率提升吞吐
        const val STREAM_FLUSH_BYTE_THRESHOLD = 32 * 1024 * 1024L
    }
}
