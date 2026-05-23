package com.xyoye.common_component.storage.download

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.utils.IOUtils
import com.xyoye.common_component.utils.PathHelper
import com.xyoye.common_component.utils.SafPathResolver
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

object DownloadManager {

    private const val MAX_CONCURRENT = 3
    private const val BUFFER_SIZE = 4 * 1024 * 1024
    private const val DB_FLUSH_INTERVAL_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _allTasks = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())
    val allTasks: StateFlow<List<DownloadTaskEntity>> = _allTasks

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    init {
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().getAllFlow().collect { tasks ->
                _allTasks.value = tasks
            }
        }
        startDispatchLoop()
    }

    private fun startDispatchLoop() {
        scope.launch {
            while (true) {
                val activeCount = activeJobs.size
                if (activeCount >= MAX_CONCURRENT) {
                    delay(1000)
                    continue
                }
                val waitingTasks = DatabaseManager.instance.getDownloadTaskDao()
                    .getByStates(listOf(DownloadState.WAITING))
                    .sortedBy { it.id }
                if (waitingTasks.isEmpty()) {
                    delay(1000)
                    continue
                }
                for (task in waitingTasks.take(MAX_CONCURRENT - activeCount)) {
                    if (activeJobs.containsKey(task.id)) continue
                    val job = scope.launch {
                        processTask(task)
                    }
                    activeJobs[task.id] = job
                    job.invokeOnCompletion {
                        activeJobs.remove(task.id)
                    }
                }
                delay(500)
            }
        }
    }

    fun addTask(
        storageId: Int,
        filePath: String,
        fileName: String,
        uniqueKey: String,
        totalBytes: Long,
        targetStorageUrl: String? = null,
        targetStorageName: String? = null
    ) {
        scope.launch {
            val dao = DatabaseManager.instance.getDownloadTaskDao()
            val existing = dao.getByUniqueKeyAndTarget(uniqueKey, storageId, targetStorageUrl)
            if (existing != null) {
                if (existing.state in listOf(DownloadState.COMPLETED, DownloadState.CANCELLED, DownloadState.FAILED)) {
                    dao.deleteById(existing.id)
                } else {
                    return@launch
                }
            }

            val activeTasks = dao.getByStates(listOf(DownloadState.WAITING, DownloadState.DOWNLOADING))
                .sortedBy { it.id }
            if (activeTasks.size >= MAX_CONCURRENT) {
                val oldest = activeTasks.first()
                if (oldest.state == DownloadState.WAITING) {
                    activeJobs[oldest.id]?.cancel()
                    dao.updateState(oldest.id, DownloadState.PAUSED)
                }
            }

            dao.insert(
                DownloadTaskEntity(
                    storageId = storageId,
                    fileName = fileName,
                    filePath = filePath,
                    uniqueKey = uniqueKey,
                    totalBytes = totalBytes,
                    state = DownloadState.WAITING,
                    targetStorageUrl = targetStorageUrl,
                    targetStorageName = targetStorageName
                )
            )
        }
    }

    fun pauseTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().updateState(taskId, DownloadState.PAUSED)
        }
    }

    fun resumeTask(taskId: Long) {
        scope.launch {
            val dao = DatabaseManager.instance.getDownloadTaskDao()
            val task = dao.getById(taskId) ?: return@launch
            if (task.state != DownloadState.PAUSED) return@launch
            dao.updateState(taskId, DownloadState.WAITING)
        }
    }

    fun cancelTask(taskId: Long) {
        activeJobs[taskId]?.cancel()
        scope.launch {
            val dao = DatabaseManager.instance.getDownloadTaskDao()
            val task = dao.getById(taskId) ?: return@launch
            val storageUrl = task.targetStorageUrl
            if (storageUrl != null) {
                val context = BaseApplication.getAppContext()
                val directFile = SafPathResolver.resolveTargetFile(context, storageUrl, task.fileName)
                if (directFile != null && directFile.exists() && directFile.delete()) {
                } else {
                    try {
                        val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(storageUrl))
                        treeDoc?.findFile(task.fileName)?.delete()
                    } catch (_: Exception) { }
                }
            } else {
                File(PathHelper.getCachePath(), "download/${task.fileName}").delete()
            }
            dao.updateState(taskId, DownloadState.CANCELLED)
        }
    }

    fun deleteTask(taskId: Long) {
        cancelTask(taskId)
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().deleteById(taskId)
        }
    }

    fun clearRecord(taskId: Long) {
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().deleteById(taskId)
        }
    }

    fun removeCompletedTasks() {
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().deleteByState(DownloadState.COMPLETED)
        }
    }

    fun retryTask(taskId: Long) {
        scope.launch {
            val dao = DatabaseManager.instance.getDownloadTaskDao()
            val task = dao.getById(taskId) ?: return@launch
            if (task.state != DownloadState.FAILED) return@launch
            dao.updateProgress(taskId, 0, DownloadState.WAITING)
        }
    }

    private suspend fun processTask(task: DownloadTaskEntity) {
        val dao = DatabaseManager.instance.getDownloadTaskDao()
        val library = DatabaseManager.instance.getMediaLibraryDao().getById(task.storageId)
            ?: run {
                dao.updateState(task.id, DownloadState.FAILED, "存储源已删除")
                return
            }

        val storage = StorageFactory.createStorage(library)
            ?: run {
                dao.updateState(task.id, DownloadState.FAILED, "无法创建存储连接")
                return
            }

        val storageFile = try {
            storage.pathFile(task.filePath, isDirectory = false)
        } catch (_: Exception) { null }

        if (storageFile == null) {
            dao.updateState(task.id, DownloadState.FAILED, "找不到文件")
            storage.close()
            return
        }

        var totalBytes = task.totalBytes
        if (totalBytes <= 0) {
            val actualLength = storageFile.fileLength()
            if (actualLength > 0) {
                totalBytes = actualLength
                dao.updateTotalBytes(task.id, actualLength)
            }
        }

        dao.updateState(task.id, DownloadState.DOWNLOADING)

        var actualOffset = task.downloadedBytes
        val inputStream = if (actualOffset > 0) {
            val stream = storage.openFile(storageFile, actualOffset)
            if (stream == null) {
                val fullStream = storage.openFile(storageFile)
                if (fullStream != null) {
                    actualOffset = 0
                    dao.updateProgress(task.id, 0, DownloadState.DOWNLOADING)
                }
                fullStream
            } else {
                stream
            }
        } else {
            storage.openFile(storageFile)
        }

        if (inputStream == null) {
            dao.updateState(task.id, DownloadState.FAILED, "无法读取文件")
            storage.close()
            return
        }

        try {
            if (task.targetStorageUrl != null) {
                processToSaf(task, inputStream, actualOffset, totalBytes)
            } else {
                processToCache(task, inputStream, actualOffset, totalBytes)
            }
        } catch (_: CancellationException) { } finally {
            IOUtils.closeIO(inputStream)
            storage.close()
        }
    }

    private suspend fun processToSaf(
        task: DownloadTaskEntity,
        inputStream: InputStream,
        offset: Long,
        totalBytes: Long
    ) {
        val context = BaseApplication.getAppContext()
        val storageUrl = task.targetStorageUrl ?: throw Exception("目标存储路径为空")

        val directFile = SafPathResolver.resolveTargetFile(context, storageUrl, task.fileName)
        if (directFile != null) {
            try {
                val fos = if (offset > 0) FileOutputStream(directFile, true) else FileOutputStream(directFile)
                fos.use { writeLoop(task.id, inputStream, fos, offset, totalBytes) }
                return
            } catch (_: Exception) { }
        }

        val treeDoc = DocumentFile.fromTreeUri(context, Uri.parse(storageUrl))
            ?: throw Exception("无法访问目标存储")
        var targetDoc = treeDoc.findFile(task.fileName)
            ?: treeDoc.createFile("application/octet-stream", task.fileName)
            ?: throw Exception("无法在目标存储创建文件")

        val pfd = context.contentResolver.openFileDescriptor(targetDoc.uri, if (offset > 0) "rw" else "w")
            ?: throw Exception("无法打开文件描述符")
        pfd.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                if (offset > 0) fos.channel.position(offset)
                writeLoop(task.id, inputStream, fos, offset, totalBytes)
            }
        }
    }

    private suspend fun processToCache(
        task: DownloadTaskEntity,
        inputStream: InputStream,
        offset: Long,
        totalBytes: Long
    ) {
        val targetFile = File(PathHelper.getCachePath(), "download/${task.fileName}")
        targetFile.parentFile?.mkdirs()
        if (offset > 0) {
            if (!targetFile.exists()) targetFile.createNewFile()
        } else {
            targetFile.delete()
            targetFile.createNewFile()
        }
        try {
            FileOutputStream(targetFile, offset > 0).use { fos ->
                writeLoop(task.id, inputStream, fos, offset, totalBytes)
            }
        } catch (e: Exception) {
            if (e !is CancellationException && targetFile.exists()) targetFile.delete()
            throw e
        }
    }

    private suspend fun writeLoop(
        taskId: Long,
        inputStream: InputStream,
        outputStream: OutputStream,
        offset: Long,
        totalBytes: Long
    ) {
        val dao = DatabaseManager.instance.getDownloadTaskDao()
        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = offset
        var lastFlushTime = System.currentTimeMillis()

        while (true) {
            val len = inputStream.read(buffer)
            if (len == -1) break
            outputStream.write(buffer, 0, len)
            totalRead += len

            val now = System.currentTimeMillis()
            if (now - lastFlushTime >= DB_FLUSH_INTERVAL_MS) {
                dao.updateProgress(taskId, totalRead, DownloadState.DOWNLOADING)
                lastFlushTime = now
            }
        }
        outputStream.flush()

        _allTasks.value = _allTasks.value.map {
            if (it.id == taskId) it.copy(downloadedBytes = totalRead, state = DownloadState.COMPLETED) else it
        }
        dao.updateProgress(taskId, totalRead, DownloadState.COMPLETED)
    }
}