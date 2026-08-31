package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.database.entity.DownloadTaskEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.DownloadDirInfo
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.imageviewer.ImageViewerRequest
import com.nichx.niplayer.feature.home.imageviewer.ImageViewerRequestHolder
import com.nichx.niplayer.player.kernel.HistoryDescriptor
import com.nichx.niplayer.player.kernel.NxMediaSource
import com.nichx.niplayer.player.kernel.PlaybackRequest
import com.nichx.niplayer.player.kernel.PlaybackRequestHolder
import com.nichx.niplayer.storage.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import javax.inject.Inject

/**
 * 下载管理 ViewModel。
 *
 * 替代旧仓库 `storage_component/ui/activities/download/DownloadViewModel.kt`。
 *
 * 数据流：
 * - [displayItems] = combine([downloadTaskDao.getAllFlow], [DownloadManager.taskProgress], [_taskSpeeds])
 *   → 计算每任务进度/速度/ETA → 按状态分组（下载中/已暂停/已完成/失败/已取消）
 *
 * 速度计算：
 * - 每 300ms 采样一次 [DownloadManager.taskProgress]，维护 3 秒滑动窗口
 * - 窗口内首末样本差值 / 时间差 = bytesPerSec
 * - ETA = 剩余字节 / bytesPerSec
 */
@HiltViewModel
class DownloadManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadTaskDao: DownloadTaskDao,
    private val downloadManager: DownloadManager,
    private val mediaLibraryDao: MediaLibraryDao,
    private val playbackRequestHolder: PlaybackRequestHolder,
    private val imageViewerRequestHolder: ImageViewerRequestHolder,
) : ViewModel() {

    private data class SpeedInfo(
        val formattedSpeed: String,
        val formattedEta: String,
    )

    private data class SpeedSample(val bytes: Long, val timeMs: Long)

    private val speedSamples = mutableMapOf<Long, MutableList<SpeedSample>>()
    private var speedCalculationJob: Job? = null

    private val _taskSpeeds = MutableStateFlow<Map<Long, SpeedInfo>>(emptyMap())

    /** 导航事件流，由 Screen 层 collect 后执行相应导航。 */
    private val _navigationEvent = MutableSharedFlow<DownloadNavigationEvent>(extraBufferCapacity = 4)
    val navigationEvent: SharedFlow<DownloadNavigationEvent> = _navigationEvent.asSharedFlow()

    /** 下载目录信息。 */
    val downloadDirInfo: StateFlow<DownloadDirInfo> = DownloadSettings.downloadDirFlow

    /** 音频下载时是否顺带下载同目录 .lrc 歌词。 */
    val downloadLrcWithAudio: StateFlow<Boolean> = DownloadSettings.downloadLrcWithAudioFlow

    /** 更新「音频下载时顺带下载同目录 .lrc」开关。 */
    fun setDownloadLrcWithAudio(enabled: Boolean) {
        DownloadSettings.downloadLrcWithAudio = enabled
    }

    /** 下载目录是否已设置。 */
    val isDownloadDirSet: Boolean get() = DownloadSettings.isDownloadDirSet

    /**
     * 分组展示列表：按状态分 Section + Task，UI 用 LazyColumn 渲染。
     *
     * 顺序：下载中 → 已暂停 → 已完成 → 失败 → 已取消。
     */
    val displayItems: StateFlow<List<DownloadGroupedItem>> = combine(
        downloadTaskDao.getAllFlow(),
        downloadManager.taskProgress,
        _taskSpeeds,
    ) { tasks, progressMap, speeds ->
        val displays = tasks.map { task ->
            val liveBytes = progressMap[task.id] ?: task.downloadedBytes
            val speedInfo = speeds[task.id]
            DownloadTaskDisplay.from(
                task = task,
                liveBytes = liveBytes,
                speed = speedInfo?.formattedSpeed ?: "",
                eta = speedInfo?.formattedEta ?: "",
            )
        }
        groupByState(displays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        startSpeedCalculation()
    }

    /**
     * 速度计算循环：每 300ms 采样活跃下载任务的实时字节数，
     * 维护 3 秒滑动窗口计算 bytesPerSec + ETA。
     */
    private fun startSpeedCalculation() {
        speedCalculationJob?.cancel()
        speedCalculationJob = viewModelScope.launch {
            while (true) {
                calculateSpeeds()
                delay(300)
            }
        }
    }

    private suspend fun calculateSpeeds() = withContext(Dispatchers.Default) {
        val tasks = downloadTaskDao.getAll()
        val progressMap = downloadManager.taskProgress.value
        val now = System.currentTimeMillis()
        val newSpeeds = mutableMapOf<Long, SpeedInfo>()

        for (task in tasks) {
            if (task.state == DownloadState.DOWNLOADING) {
                val liveBytes = progressMap[task.id] ?: task.downloadedBytes
                val samples = speedSamples.getOrPut(task.id) { mutableListOf() }
                samples.add(SpeedSample(liveBytes, now))

                val cutoffTime = now - 3000
                while (samples.size > 1 && samples.first().timeMs < cutoffTime) {
                    samples.removeAt(0)
                }

                if (samples.size >= 2) {
                    val first = samples.first()
                    val last = samples.last()
                    val elapsed = last.timeMs - first.timeMs

                    if (elapsed >= 500) {
                        val bytesPerSec = ((last.bytes - first.bytes) * 1000L) / elapsed
                        if (bytesPerSec >= 0) {
                            val eta = if (task.totalBytes > 0) {
                                val remaining = task.totalBytes - liveBytes
                                formatEta(if (bytesPerSec > 0) remaining / bytesPerSec else 0)
                            } else ""
                            newSpeeds[task.id] = SpeedInfo(
                                formatSpeed(bytesPerSec),
                                eta,
                            )
                        }
                    }
                }
            } else {
                speedSamples.remove(task.id)
            }
        }

        _taskSpeeds.value = newSpeeds
    }

    // ---- 下载目录管理 ----

    /**
     * 设置下载目录（共享存储绝对路径）。
     *
     * 原生直写共享存储（免 SAF），需已授予「所有文件访问权限」，
     * 由 [com.nichx.niplayer.storage.StorageAccess] 校验与引导授权。
     */
    fun setDownloadDir(path: String, dirName: String) {
        DownloadSettings.setDownloadDir(path, dirName)
    }

    /**
     * 清除下载目录设置。
     */
    fun clearDownloadDir() {
        DownloadSettings.clearDownloadDir()
    }

    // ---- 打开下载文件 ----

    /**
     * 打开已下载完成的文件。
     *
     * - 视频文件 → 通过 [PlaybackRequestHolder] 在本应用内播放
     * - 音频文件 → 通过 [PlaybackRequestHolder] 在本应用内播放
     * - 图片文件 → 通过 [ImageViewerRequestHolder] 在本应用内查看
     * - 其他文件 → 调用系统 Intent
     */
    fun openDownloadFile(task: DownloadTaskEntity) {
        if (task.state != DownloadState.COMPLETED) return
        val fileName = task.fileName

        when {
            MediaFileTypes.isVideoFile(fileName) -> openVideoInApp(task)
            MediaFileTypes.isAudioFile(fileName) -> openAudioInApp(task)
            MediaFileTypes.isImageFile(fileName) -> openImageInApp(task)
            else -> openWithSystemIntent(task)
        }
    }

    private fun openVideoInApp(task: DownloadTaskEntity) {
        val uri = resolveFileUri(task) ?: return
        val source = NxMediaSource.Local(uri = uri, mediaId = task.fileName)
        playbackRequestHolder.set(
            PlaybackRequest(
                source = source,
                title = task.fileName,
                isAudio = false,
            )
        )
        viewModelScope.launch {
            _navigationEvent.emit(DownloadNavigationEvent.NavigateToPlayer(isAudio = false))
        }
    }

    private fun openAudioInApp(task: DownloadTaskEntity) {
        val uri = resolveFileUri(task) ?: return
        val source = NxMediaSource.Local(uri = uri, mediaId = task.fileName)
        val localPath = resolveLocalFilePath(task)
        playbackRequestHolder.set(
            PlaybackRequest(
                source = source,
                title = task.fileName,
                isAudio = true,
                // 携带本地文件路径作为 history.storagePath：AudioPlaybackManager 据此
                // 加载同目录 .lrc 歌词，并通过 content URI 提取内嵌封面。storageId=null，
                // recordPlayStart/进度保存内部跳过落库，不影响播放历史。
                history = localPath?.let { path ->
                    HistoryDescriptor(
                        uniqueKey = "local_download:$path",
                        url = path,
                        mediaTypeValue = MediaType.LOCAL_STORAGE.value,
                        storageId = null,
                        storagePath = path,
                        fileSize = task.totalBytes,
                    )
                },
            )
        )
        viewModelScope.launch {
            _navigationEvent.emit(DownloadNavigationEvent.NavigateToPlayer(isAudio = true))
        }
    }

    private fun openImageInApp(task: DownloadTaskEntity) {
        val storageUrl = task.targetStorageUrl
        if (storageUrl.isNullOrBlank() || storageUrl.startsWith("file://")) {
            openWithSystemIntent(task)
            return
        }
        viewModelScope.launch {
            val library = withContext(Dispatchers.IO) {
                mediaLibraryDao.getByUrl(storageUrl, MediaType.EXTERNAL_STORAGE)
            }
            if (library != null) {
                imageViewerRequestHolder.set(
                    ImageViewerRequest(
                        storageId = library.id,
                        directoryPath = "",
                        initialFilePath = task.fileName,
                    )
                )
                _navigationEvent.emit(DownloadNavigationEvent.NavigateToImageViewer)
            } else {
                openWithSystemIntent(task)
            }
        }
    }

    private fun openWithSystemIntent(task: DownloadTaskEntity) {
        val uri = resolveFileUri(task) ?: return
        val mimeType = getMimeType(task.fileName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun resolveFileUri(task: DownloadTaskEntity): Uri? {
        val storageUrl = task.targetStorageUrl
        return when {
            storageUrl == null -> {
                val file = File(context.cacheDir, "download/${task.fileName}")
                if (file.exists()) FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                ) else null
            }
            storageUrl.startsWith("file://") -> {
                val dirPath = storageUrl.removePrefix("file://")
                val file = File(dirPath, task.fileName)
                if (file.exists()) FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                ) else null
            }
            else -> null
        }
    }

    /**
     * 解析已下载文件的真实本地路径（供歌词同目录查找等使用），不存在返回 null。
     * 与 [resolveFileUri] 的路径推导保持一致：优先下载目录，回退应用缓存。
     */
    private fun resolveLocalFilePath(task: DownloadTaskEntity): String? {
        val storageUrl = task.targetStorageUrl
        return when {
            storageUrl == null -> {
                val file = File(context.cacheDir, "download/${task.fileName}")
                if (file.exists()) file.absolutePath else null
            }
            storageUrl.startsWith("file://") -> {
                val file = File(storageUrl.removePrefix("file://"), task.fileName)
                if (file.exists()) file.absolutePath else null
            }
            else -> null
        }
    }

    private fun getMimeType(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return "*/*"
        val ext = fileName.substring(dot + 1).lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: "*/*"
    }

    // ---- 任务操作（委托 DownloadManager）----

    fun pauseTask(taskId: Long) = downloadManager.pauseTask(taskId)

    fun resumeTask(taskId: Long) = downloadManager.resumeTask(taskId)

    fun cancelTask(taskId: Long) = downloadManager.cancelTask(taskId)

    fun deleteTask(taskId: Long) = downloadManager.deleteTask(taskId)

    fun clearRecord(taskId: Long) = downloadManager.clearRecord(taskId)

    fun retryTask(taskId: Long) = downloadManager.retryTask(taskId)

    fun removeCompleted() = downloadManager.removeCompletedTasks()

    fun retryAllFailed() = downloadManager.retryAllFailed()

    fun clearFailed() = downloadManager.clearFailed()

    // ---- 分组逻辑 ----

    private fun groupByState(displays: List<DownloadTaskDisplay>): List<DownloadGroupedItem> {
        val sections = mutableListOf<DownloadGroupedItem>()

        val active = displays.filter {
            it.task.state == DownloadState.DOWNLOADING || it.task.state == DownloadState.WAITING
        }.sortedBy { it.task.id }
        if (active.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section(context.getString(R.string.download_state_downloading), active.size))
            sections.addAll(active.map { DownloadGroupedItem.Task(it) })
        }

        val paused = displays.filter { it.task.state == DownloadState.PAUSED }.sortedBy { it.task.id }
        if (paused.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section(context.getString(R.string.download_state_paused), paused.size))
            sections.addAll(paused.map { DownloadGroupedItem.Task(it) })
        }

        val completed = displays.filter { it.task.state == DownloadState.COMPLETED }
            .sortedByDescending { it.task.id }
        if (completed.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section(context.getString(R.string.download_state_completed), completed.size))
            sections.addAll(completed.map { DownloadGroupedItem.Task(it) })
        }

        val failed = displays.filter { it.task.state == DownloadState.FAILED }
            .sortedByDescending { it.task.id }
        if (failed.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section(context.getString(R.string.download_state_failed), failed.size))
            sections.addAll(failed.map { DownloadGroupedItem.Task(it) })
        }

        val cancelled = displays.filter { it.task.state == DownloadState.CANCELLED }
            .sortedByDescending { it.task.id }
        if (cancelled.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section(context.getString(R.string.download_state_cancelled), cancelled.size))
            sections.addAll(cancelled.map { DownloadGroupedItem.Task(it) })
        }

        return sections
    }

    private fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec >= 1000 * 1000 -> "${"%.1f".format(bytesPerSec / (1000.0 * 1000.0))} MB/s"
        bytesPerSec >= 1000 -> "${bytesPerSec / 1000} KB/s"
        else -> "${bytesPerSec} B/s"
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> String.format(Locale.ROOT, context.getString(R.string.download_eta_hms), h, m, s)
            m > 0 -> String.format(Locale.ROOT, context.getString(R.string.download_eta_ms), m, s)
            else -> String.format(Locale.ROOT, context.getString(R.string.download_eta_s), s)
        }
    }
}

/** 导航事件，由 Screen 层 collect 后执行相应导航操作。 */
sealed class DownloadNavigationEvent {
    /** 导航到播放页（视频/音频），携带 isAudio 用于直接分流。 */
    data class NavigateToPlayer(val isAudio: Boolean) : DownloadNavigationEvent()

    /** 导航到图片查看页。 */
    data object NavigateToImageViewer : DownloadNavigationEvent()
}

/** 单个下载任务的展示数据，包含实时进度、速度、ETA。 */
data class DownloadTaskDisplay(
    val task: DownloadTaskEntity,
    val speed: String = "",
    val eta: String = "",
    val progress: Int = 0,
    val downloadedBytes: Long = task.downloadedBytes,
) {
    companion object {
        fun from(
            task: DownloadTaskEntity,
            liveBytes: Long = task.downloadedBytes,
            speed: String = "",
            eta: String = "",
        ): DownloadTaskDisplay {
            val progress = if (task.totalBytes > 0) {
                ((liveBytes.toDouble() / task.totalBytes) * 100).toInt().coerceIn(0, 100)
            } else 0
            return DownloadTaskDisplay(
                task = task,
                speed = speed,
                eta = eta,
                progress = progress,
                downloadedBytes = liveBytes,
            )
        }
    }
}

/** 分组列表项：分区标题或任务卡片。 */
sealed class DownloadGroupedItem {
    data class Section(val title: String, val count: Int) : DownloadGroupedItem()
    data class Task(val display: DownloadTaskDisplay) : DownloadGroupedItem()
}
