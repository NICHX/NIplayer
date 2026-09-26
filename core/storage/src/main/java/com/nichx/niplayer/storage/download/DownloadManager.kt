package com.nichx.niplayer.storage.download

import android.content.Context
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.database.entity.DownloadTaskEntity
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.R
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载管理引擎。
 *
 * 以 Hilt @Singleton 提供，通过构造注入 [StorageFactory] / [DownloadTaskDao] / [MediaLibraryDao]。
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

    /**
     * [addTask] 去重串行化锁。
     *
     * `download_task` 表的 `unique_key` **没有唯一索引**，且 `insert` 使用自增主键 +
     * `OnConflictStrategy.REPLACE`，重复的 `unique_key` 不会触发冲突、不会报错，只会多出一行。
     * 而 [addTask] 是 query-then-insert（先查重再插入），批量添加时各协程并发进入会互相看不到
     * 对方尚未提交的插入，从而产生重复任务，进而导致两个 [processTask] 并发写同一目标文件。
     * 此处用互斥锁把「查重 + 插入」变成原子操作。
     */
    private val addTaskMutex = Mutex()

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
            // 去重必须原子：查重与插入之间不能插入其它 addTask（见 [addTaskMutex] 说明）
            addTaskMutex.withLock {
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
                        return@withLock
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
    }

    /** 暂停任务：取消协程 + 置 PAUSED（保留已下载文件供续传）。 */
    fun pauseTask(taskId: Long) {
        val job = activeJobs[taskId]
        job?.cancel()
        scope.launch {
            // 先落 PAUSED 保证 UI 立即响应
            downloadTaskDao.updateState(taskId, DownloadState.PAUSED)
            // 等协程真正退出后再落一次：processTask 在取消生效**之前**已发起的那次
            // updateProgress(..., DOWNLOADING) 仍可能落库（挂起调用的取消检查发生在调用入口，
            // 已通过检查的调用会继续完成），从而覆盖上面的 PAUSED。若此时进程被杀，
            // 任务会永久停留在 DOWNLOADING，而调度循环只拾取 WAITING → 无法恢复。
            job?.join()
            downloadTaskDao.updateState(taskId, DownloadState.PAUSED)
        }
    }

    /** 恢复任务：仅 PAUSED 态可恢复，置 WAITING 后调度循环自动接管。 */
    fun resumeTask(taskId: Long) {
        scope.launch {
            val task = downloadTaskDao.getById(taskId) ?: return@launch
            if (task.state != DownloadState.PAUSED) return@launch
            downloadTaskDao.updateState(taskId, DownloadState.WAITING)
        }
    }

    /**
     * 取消任务：置 CANCELLED + 等待活跃协程退出 + 删除已下载文件。
     *
     * **关于「状态与文件的先后顺序」**：原实现是「删文件 → 置 CANCELLED」，若进程恰好在这两步
     * 之间死亡，会留下「任务停在 DOWNLOADING 但文件已消失」的僵死状态 —— 调度循环只拾取
     * WAITING，该任务永远无法恢复。现在把状态写入提到删除之前，并等活跃协程完全退出后再删，
     * 由 [DownloadManagerCancelRaceTest] 锁定该顺序（已用变异测试确认该断言可捕获反序）。
     *
     * **关于 [processTask] 的 catch**：它虽以 `cancellingTasks.remove()` 区分「取消/暂停」，
     * 但**其状态写入实际不会生效** —— Room 的 suspend DAO 内部走 `withContext(...)`
     * （`DBUtil.android.kt`），而 `withContext` 会 `ensureActive()`，在已取消的协程里调用会直接
     * 抛 CancellationException。因此终态实际只由本方法与 [pauseTask] 各自（未取消的）协程写入。
     * 标记仍按原语义保留，是为了让 [processTask] 的分支判断保持可读、并在未来若该写入路径
     * 变为有效时仍然正确。
     */
    fun cancelTask(taskId: Long) {
        cancellingTasks[taskId] = true
        val job = activeJobs[taskId]
        job?.cancel()
        scope.launch {
            // 已完成的任务不再取消：避免误删用户已下载完成的文件
            if (downloadTaskDao.getById(taskId)?.state == DownloadState.COMPLETED) {
                cancellingTasks.remove(taskId)
                return@launch
            }
            // 先落 CANCELLED：调度循环只查 WAITING，可确保尚未启动的任务不会被拾取；
            // 同时保证「状态已终结」先于「文件被删除」，避免进程在两者之间死亡时
            // 留下「任务停在 DOWNLOADING 但文件已消失」的僵死状态（调度循环只拾取 WAITING）。
            // 该顺序由 DownloadManagerCancelRaceTest 锁定（已用变异测试确认可捕获反序）。
            downloadTaskDao.updateState(taskId, DownloadState.CANCELLED)
            // 等活跃协程完全退出，消除「文件已删但协程仍在写入」的窗口
            job?.join()
            val task = downloadTaskDao.getById(taskId)
            // 等待期间下载可能刚好完成（用户点取消与下载收尾同时发生）→ 保留成品
            if (task != null && task.state != DownloadState.COMPLETED) {
                deleteTaskFile(task)
                // 再落一次：processTask 若在取消前已进入，可能用 DOWNLOADING 覆盖首次写入
                downloadTaskDao.updateState(taskId, DownloadState.CANCELLED)
            }
            cancellingTasks.remove(taskId)
        }
    }

    /** 删除任务：取消协程 + 删除文件 + 删除数据库记录。 */
    fun deleteTask(taskId: Long) {
        cancellingTasks[taskId] = true
        val job = activeJobs[taskId]
        job?.cancel()
        scope.launch {
            // 与 [cancelTask] 一致：先等协程退出再删文件，避免「文件已删但仍在写入」的窗口
            job?.join()
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
    // 任务边界的**有意**吞掉取消：取消/暂停在此被转换成任务的终态（CANCELLED / PAUSED），
    // 而不是向上传播 —— 这是与 cancelTask / pauseTask 配合的既定竞态设计，已由
    // DownloadManagerCancelRaceTest 变异验证。按规则改成「首条语句重抛」会丢掉
    // cancellingTasks.remove(...) 等非 suspend 副作用，故此处显式豁免。
    @Suppress("SuspendFunSwallowedCancellation")
    private suspend fun processTask(task: DownloadTaskEntity) {
        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(task.storageId) }
            ?: run {
                downloadTaskDao.updateState(task.id, DownloadState.FAILED, context.getString(R.string.download_error_storage_deleted))
                return
            }

        val storage = storageFactory.createOrNull(library)
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

        // totalBytes 未知（<=0）时无法计算百分比，但下载仍可进行。
        // WebDAV/SMB 的 StorageFile.length 在 listFiles 时已填充，正常情况不会为 0。
        val totalBytes = task.totalBytes

        downloadTaskDao.updateState(task.id, DownloadState.DOWNLOADING)

        // 断点续传：优先用 offset 重载打开流；不支持时回退到完整下载
        var actualOffset = task.downloadedBytes
        // 续传前必须校验本地文件的实际长度与记录的偏移一致。二者不一致时（上次 flush 未落盘、
        // 进程被杀、文件被手动改动或删除、取消竞态遗留），append 模式会从文件真实末尾继续追加，
        // 写入位置与远程 offset 错位，产出的文件从错位点起全是垃圾数据且仍被标记 COMPLETED。
        // 此处直接丢弃本地残片、重置偏移从 0 重下（本地残片无复用价值，重新下载才安全）。
        val localFile = localTargetFile(task)
        if (DownloadPolicy.shouldResetResumeOffset(actualOffset, localFile?.length() ?: 0L)) {
            localFile?.delete()
            actualOffset = 0L
            downloadTaskDao.updateProgress(task.id, 0, DownloadState.DOWNLOADING)
        }
        val inputStream: InputStream = try {
            if (actualOffset > 0) {
                val offsetStream = try {
                    storage.openInputStream(storageFile, actualOffset)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
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
            // 音频下载完成后，顺带下载同目录同名 .lrc 歌词（受开关控制，best-effort）
            downloadSiblingLrc(task, storage)
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
            _taskProgress.update { it.toMutableMap().apply { remove(task.id) } }
        }
        // storage 清理**移出 finally**：finally 里的 suspend 调用在协程取消时会被直接跳过（资源泄漏），
        // 而 detekt 只接受「finally 里裸 withContext(NonCancellable)」，那样又无法吞掉 close 失败。
        // 上面两个 catch 均不重抛，故此处必然执行。
        withContext(NonCancellable) { storage.close() }
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
        try {
            // 兜底：上游 [processTask] 已校验「本地文件长度 == offset」，此处再防御一次。
            // 不一致时拒绝追加写入并抛错（由 processTask 置 FAILED），而不是静默写出错位文件。
            if (offset > 0 && targetFile.length() != offset) {
                throw IOException(context.getString(R.string.download_error_resume_mismatch))
            }
            if (offset == 0L) {
                targetFile.delete()
                targetFile.createNewFile()
            }
            FileOutputStream(targetFile, offset > 0).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use {
                    pipelinedWriteLoop(task.id, it, inputStream, offset, totalBytes)
                }
            }
        } catch (e: CancellationException) {
            // 取消时立即重抛：不留半成品（原实现靠 `e !is CancellationException` 短路达到同样效果）
            throw e
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
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
        try {
            // 兜底：与 [processToCache] 一致，拒绝以错误的追加位置写出损坏文件
            if (offset > 0 && targetFile.length() != offset) {
                throw IOException(context.getString(R.string.download_error_resume_mismatch))
            }
            if (offset == 0L) {
                targetFile.delete()
                targetFile.createNewFile()
            }
            FileOutputStream(targetFile, offset > 0).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use {
                    pipelinedWriteLoop(task.id, it, inputStream, offset, totalBytes)
                }
            }
        } catch (e: CancellationException) {
            // 取消时立即重抛：不留半成品（原实现靠 `e !is CancellationException` 短路达到同样效果）
            throw e
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            throw e
        }
    }

    /**
     * 音频下载完成后，顺带下载远程同目录同名 `.lrc` 歌词到目标目录（best-effort）。
     *
     * 受 [DownloadSettings.downloadLrcWithAudio] 开关控制；远程无 `.lrc`、非音频文件
     * 或任意失败均静默忽略，不影响主文件下载结果。
     */
    private suspend fun downloadSiblingLrc(task: DownloadTaskEntity, storage: Storage) {
        if (!DownloadSettings.downloadLrcWithAudio) return
        if (!isAudioFile(task.fileName)) return
        val baseName = task.fileName.substringBeforeLast('.')
        if (baseName.isBlank() || baseName == task.fileName) return

        val dirPath = task.filePath.substringBeforeLast('/')
        val remoteLrcPath = if (dirPath == task.filePath) "$baseName.lrc" else "$dirPath/$baseName.lrc"
        val lrcName = "$baseName.lrc"
        val remoteLrc = object : AbstractStorageFile(
            path = remoteLrcPath,
            name = lrcName,
            isDirectory = false,
            length = 0,
        ) {}

        try {
            if (!storage.fileExists(remoteLrcPath)) return
            storage.openInputStream(remoteLrc)?.use { input ->
                val targetDir = when {
                    task.targetStorageUrl == null -> File(context.cacheDir, "download")
                    task.targetStorageUrl!!.startsWith("file://") -> File(task.targetStorageUrl!!.removePrefix("file://"))
                    else -> return
                }
                targetDir.mkdirs()
                FileOutputStream(File(targetDir, lrcName)).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val len = input.read(buffer)
                        if (len == -1) break
                        fos.write(buffer, 0, len)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 歌词下载失败不影响主文件结果
        }
    }

    /**
     * 音频文件扩展名判断。
     *
     * A1 架构修复（2026-09-21）：原在此本地复制一份扩展名表（注释自称「与 :player:kernel
     * MediaFileTypes 保持一致」），属重复定义。扩展名表已下移到 :core:common，改为直接委托。
     */
    private fun isAudioFile(name: String): Boolean = MediaFileTypes.isAudioFile(name)

    /**
     * 写入循环：读输入流 → 写输出流，节流刷新进度。
     *
     * - **StateFlow 进度**：每 [PROGRESS_EMIT_INTERVAL_MS] 更新一次 [taskProgress]（UI 用）
     * - **DB 进度**：每 [DB_FLUSH_INTERVAL_MS] 或 [FLUSH_BYTE_THRESHOLD] 刷新一次（断电恢复用）
     * - **流 flush**：仅按 [STREAM_FLUSH_BYTE_THRESHOLD] 字节阈值触发，不再与 DB 写入绑定，
     *   避免 fsync 停顿拖慢下载吞吐（SAF content:// 路径尤其明显）
     * - **完成**：flush 输出流 → 校验 `totalRead >= totalBytes`（未知长度时跳过）→
     *   DB 置 COMPLETED。短读（提前 EOF）改判 FAILED 并抛 [IOException]，不再伪装成功
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
        // 提前 EOF（连接中断 / 服务端 Range 响应不完整 / 文件被截断）不得判定为完成。
        // 原实现只要流返回 -1 就置 COMPLETED，会把截断的文件伪装成下载成功，用户无法感知。
        // 此处置 FAILED 并抛错：错误文案由 processTask 的 catch 补上；本地残片由
        // processToCache / processToDirectPath 的 catch 清理，重试时经长度校验从 0 重新下载。
        if (!DownloadPolicy.isFullyDownloaded(totalRead, totalBytes)) {
            downloadTaskDao.updateProgress(taskId, totalRead, DownloadState.FAILED)
            throw IOException(context.getString(R.string.download_error_incomplete))
        }
        downloadTaskDao.updateProgress(taskId, totalRead, DownloadState.COMPLETED)
    }

    /**
     * 解析任务对应的本地目标文件。
     *
     * 规则与写入路径（[processToCache] / [processToDirectPath]）严格一致，供续传长度校验与删除复用：
     * - `targetStorageUrl == null`（缓存模式）：`<cache>/download/<fileName>`
     * - `file://` 前缀（直 path 模式）：`<dirPath>/<fileName>`
     * - 其它（遗留 content:// 等不支持的目标）：返回 null
     */
    private fun localTargetFile(task: DownloadTaskEntity): File? {
        val storageUrl = task.targetStorageUrl
        return when {
            storageUrl == null -> File(context.cacheDir, "download/${task.fileName}")
            storageUrl.startsWith("file://") -> File(storageUrl.removePrefix("file://"), task.fileName)
            else -> null
        }
    }

    /**
     * 删除任务已下载的文件。
     *
     * 按目标模式分发（见 [localTargetFile]）：缓存模式删 `<cache>/download/<fileName>`，
     * 直 path 模式删对应文件；不支持的目标不做任何操作。
     */
    private fun deleteTaskFile(task: DownloadTaskEntity) {
        localTargetFile(task)?.takeIf { it.exists() }?.delete()
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
