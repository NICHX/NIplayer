package com.xyoye.common_component.storage.download

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.utils.IOUtils
import com.xyoye.common_component.utils.PathHelper
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object DownloadManager {

    private const val MAX_CONCURRENT = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _taskCount = MutableStateFlow(0)
    val taskCount: StateFlow<Int> = _taskCount

    private val processingJobs = ConcurrentHashMap<Long, Job>()
    private var processingLoop: Job? = null

    private val _allTasks = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())
    val allTasks: StateFlow<List<DownloadTaskEntity>> = _allTasks

    init {
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().getAllFlow().collect { tasks ->
                _allTasks.value = tasks
                _taskCount.value = tasks.size
            }
        }
        startProcessingLoop()
    }

    private fun startProcessingLoop() {
        processingLoop = scope.launch {
            while (true) {
                val dao = DatabaseManager.instance.getDownloadTaskDao()
                val activeCount = processingJobs.size
                val waitingTasks = dao.getByStates(
                    listOf(DownloadState.WAITING)
                ).sortedBy { it.id }

                if (waitingTasks.isEmpty() || activeCount >= MAX_CONCURRENT) {
                    delay(1000)
                    continue
                }

                val slotsAvailable = MAX_CONCURRENT - activeCount
                val toProcess = waitingTasks.take(slotsAvailable)

                for (task in toProcess) {
                    if (processingJobs.containsKey(task.id)) continue
                    val job = scope.launch {
                        processTask(task)
                    }
                    processingJobs[task.id] = job
                    job.invokeOnCompletion {
                        processingJobs.remove(task.id)
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
            val existing = dao.getByUniqueKey(uniqueKey, storageId)
            if (existing != null) {
                if (existing.state == DownloadState.COMPLETED || existing.state == DownloadState.CANCELLED || existing.state == DownloadState.FAILED) {
                    dao.deleteById(existing.id)
                } else {
                    return@launch
                }
            }

            val activeTasks = dao.getByStates(
                listOf(DownloadState.WAITING, DownloadState.DOWNLOADING)
            ).sortedBy { it.id }
            if (activeTasks.size >= MAX_CONCURRENT) {
                val oldest = activeTasks.first()
                if (oldest.state == DownloadState.WAITING) {
                    processingJobs[oldest.id]?.cancel()
                    dao.updateState(oldest.id, DownloadState.PAUSED)
                }
            }

            val task = DownloadTaskEntity(
                storageId = storageId,
                fileName = fileName,
                filePath = filePath,
                uniqueKey = uniqueKey,
                totalBytes = totalBytes,
                state = DownloadState.WAITING,
                targetStorageUrl = targetStorageUrl,
                targetStorageName = targetStorageName
            )
            dao.insert(task)
        }
    }

    fun pauseTask(taskId: Long) {
        processingJobs[taskId]?.cancel()
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
        processingJobs[taskId]?.cancel()
        scope.launch {
            val dao = DatabaseManager.instance.getDownloadTaskDao()
            val task = dao.getById(taskId) ?: return@launch
            if (task.targetStorageUrl != null) {
                cleanUpSafFile(task)
            } else {
                val downloadDir = File(PathHelper.getCachePath(), "download")
                val targetFile = File(downloadDir, task.fileName)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
            dao.updateState(taskId, DownloadState.CANCELLED)
        }
    }

    fun removeCompletedTasks() {
        scope.launch {
            DatabaseManager.instance.getDownloadTaskDao().deleteByState(DownloadState.COMPLETED)
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

    private suspend fun cleanUpSafFile(task: DownloadTaskEntity) {
        try {
            val context = BaseApplication.getAppContext()
            val treeUri = Uri.parse(task.targetStorageUrl)
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
            val targetDoc = treeDoc.findFile(task.fileName) ?: return
            targetDoc.delete()
        } catch (_: Exception) { }
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
        } catch (e: Exception) {
            null
        }

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

        val offset = task.downloadedBytes

        val inputStream = if (offset > 0) {
            storage.openFile(storageFile, offset)
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
                processToSaf(task, inputStream, offset, totalBytes, dao)
            } else {
                processToCache(task, inputStream, offset, totalBytes, dao)
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                e.printStackTrace()
                dao.updateState(task.id, DownloadState.FAILED, e.message)
            }
        } finally {
            IOUtils.closeIO(inputStream)
            storage.close()
        }
    }

    private suspend fun processToCache(
        task: DownloadTaskEntity,
        inputStream: java.io.InputStream,
        offset: Long,
        totalBytes: Long,
        dao: com.xyoye.common_component.database.dao.DownloadTaskDao
    ) {
        val downloadDir = File(PathHelper.getCachePath(), "download")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val targetFile = File(downloadDir, task.fileName)

        var outputStream: BufferedOutputStream? = null
        try {
            if (offset > 0) {
                if (!targetFile.exists()) {
                    targetFile.createNewFile()
                }
            } else {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                targetFile.createNewFile()
            }

            outputStream = BufferedOutputStream(
                if (offset > 0) FileOutputStream(targetFile, true)
                else FileOutputStream(targetFile, false)
            )

            val buffer = ByteArray(512 * 1024)
            var len: Int
            var totalRead = offset
            var updateCounter = 0
            while (inputStream.read(buffer).also { len = it } != -1) {
                if (!processingJobs.containsKey(task.id)) {
                    dao.updateProgress(task.id, totalRead, DownloadState.PAUSED)
                    return
                }
                outputStream.write(buffer, 0, len)
                totalRead += len
                updateCounter++
                if (updateCounter >= 3) {
                    dao.updateProgress(task.id, totalRead, DownloadState.DOWNLOADING)
                    updateCounter = 0
                }
            }
            outputStream.flush()

            if (processingJobs.containsKey(task.id)) {
                dao.updateProgress(task.id, totalRead, DownloadState.COMPLETED)
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                if (targetFile.exists()) targetFile.delete()
                throw e
            }
        } finally {
            IOUtils.closeIO(outputStream)
        }
    }

    private suspend fun processToSaf(
        task: DownloadTaskEntity,
        inputStream: java.io.InputStream,
        offset: Long,
        totalBytes: Long,
        dao: com.xyoye.common_component.database.dao.DownloadTaskDao
    ) {
        val context = BaseApplication.getAppContext()
        val treeUri = Uri.parse(task.targetStorageUrl)
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: run {
                dao.updateState(task.id, DownloadState.FAILED, "无法访问目标存储")
                return
            }

        var targetDoc = treeDoc.findFile(task.fileName)
        if (targetDoc == null) {
            targetDoc = treeDoc.createFile("application/octet-stream", task.fileName)
        }
        if (targetDoc == null) {
            dao.updateState(task.id, DownloadState.FAILED, "无法在目标存储创建文件")
            return
        }

        val outputStream = if (offset > 0) {
            context.contentResolver.openOutputStream(targetDoc.uri, "wa")
        } else {
            context.contentResolver.openOutputStream(targetDoc.uri, "w")
        }

        if (outputStream == null) {
            dao.updateState(task.id, DownloadState.FAILED, "无法写入目标存储")
            return
        }

        try {
            val buffer = ByteArray(512 * 1024)
            var len: Int
            var totalRead = offset
            var updateCounter = 0
            val bufferedOut = BufferedOutputStream(outputStream)
            while (inputStream.read(buffer).also { len = it } != -1) {
                if (!processingJobs.containsKey(task.id)) {
                    dao.updateProgress(task.id, totalRead, DownloadState.PAUSED)
                    return
                }
                bufferedOut.write(buffer, 0, len)
                totalRead += len
                updateCounter++
                if (updateCounter >= 3) {
                    dao.updateProgress(task.id, totalRead, DownloadState.DOWNLOADING)
                    updateCounter = 0
                }
            }
            bufferedOut.flush()

            if (processingJobs.containsKey(task.id)) {
                dao.updateProgress(task.id, totalRead, DownloadState.COMPLETED)
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                targetDoc.delete()
                throw e
            }
        } finally {
            IOUtils.closeIO(outputStream)
        }
    }
}
