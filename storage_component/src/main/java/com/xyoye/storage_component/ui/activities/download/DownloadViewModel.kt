package com.xyoye.storage_component.ui.activities.download

import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.storage.download.DownloadManager
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadTaskDisplay(
    val task: DownloadTaskEntity,
    val speed: String = "",
    val eta: String = "",
    val progress: Int = 0,
    val downloadedBytes: Long = task.downloadedBytes
) {
    companion object {
        fun from(task: DownloadTaskEntity, liveBytes: Long = task.downloadedBytes, speed: String = "", eta: String = ""): DownloadTaskDisplay {
            val progress = if (task.totalBytes > 0) {
                ((liveBytes.toDouble() / task.totalBytes) * 100).toInt().coerceIn(0, 100)
            } else 0
            return DownloadTaskDisplay(
                task = task,
                speed = speed,
                eta = eta,
                progress = progress,
                downloadedBytes = liveBytes
            )
        }
    }
}

sealed class DownloadGroupedItem {
    data class Section(val title: String, val count: Int) : DownloadGroupedItem()
    data class Task(val display: DownloadTaskDisplay) : DownloadGroupedItem()
}

class DownloadViewModel : BaseViewModel() {

    private data class SpeedInfo(
        val bytesPerSec: Long,
        val formattedSpeed: String,
        val formattedEta: String
    )

    private data class SpeedSample(val bytes: Long, val timeMs: Long)

    private val speedSamples = mutableMapOf<Long, MutableList<SpeedSample>>()
    private var speedCalculationJob: Job? = null

    private val _taskSpeeds = MutableStateFlow<Map<Long, SpeedInfo>>(emptyMap())

    val displayItems: StateFlow<List<DownloadGroupedItem>> by lazy {
        combine(
            DownloadManager.allTasks,
            DownloadManager.taskProgress,
            _taskSpeeds
        ) { tasks, progressMap, speeds ->
            val displays = tasks.map { task ->
                val liveBytes = progressMap[task.id] ?: task.downloadedBytes
                val speedInfo = speeds[task.id]
                DownloadTaskDisplay.from(
                    task = task,
                    liveBytes = liveBytes,
                    speed = speedInfo?.formattedSpeed ?: "",
                    eta = speedInfo?.formattedEta ?: ""
                )
            }
            groupByState(displays)
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    }

    init {
        startSpeedCalculation()
    }

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
        val tasks = DownloadManager.allTasks.value
        val progressMap = DownloadManager.taskProgress.value
        val now = System.currentTimeMillis()
        val newSpeeds = mutableMapOf<Long, SpeedInfo>()

        for (task in tasks) {
            if (task.state == DownloadState.DOWNLOADING && task.totalBytes > 0) {
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
                            val remaining = task.totalBytes - liveBytes
                            val etaSecs = if (bytesPerSec > 0) remaining / bytesPerSec else 0
                            newSpeeds[task.id] = SpeedInfo(
                                bytesPerSec,
                                formatSpeed(bytesPerSec),
                                formatEta(etaSecs)
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

    fun pauseTask(taskId: Long) = DownloadManager.pauseTask(taskId)

    fun resumeTask(taskId: Long) = DownloadManager.resumeTask(taskId)

    fun cancelTask(taskId: Long) = DownloadManager.cancelTask(taskId)

    fun deleteTask(taskId: Long) = DownloadManager.deleteTask(taskId)

    fun clearRecord(taskId: Long) = DownloadManager.clearRecord(taskId)

    fun retryTask(taskId: Long) = DownloadManager.retryTask(taskId)

    fun removeCompleted() = DownloadManager.removeCompletedTasks()

    fun retryAllFailed() = DownloadManager.retryAllFailed()

    fun clearFailed() = DownloadManager.clearFailed()

    private fun groupByState(displays: List<DownloadTaskDisplay>): List<DownloadGroupedItem> {
        val sections = mutableListOf<DownloadGroupedItem>()

        val active = displays.filter {
            it.task.state == DownloadState.DOWNLOADING || it.task.state == DownloadState.WAITING
        }.sortedBy { it.task.id }
        if (active.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section("下载中", active.size))
            sections.addAll(active.map { DownloadGroupedItem.Task(it) })
        }

        val paused = displays.filter { it.task.state == DownloadState.PAUSED }.sortedBy { it.task.id }
        if (paused.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section("已暂停", paused.size))
            sections.addAll(paused.map { DownloadGroupedItem.Task(it) })
        }

        val completed = displays.filter { it.task.state == DownloadState.COMPLETED }.sortedByDescending { it.task.id }
        if (completed.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section("已完成", completed.size))
            sections.addAll(completed.map { DownloadGroupedItem.Task(it) })
        }

        val failed = displays.filter { it.task.state == DownloadState.FAILED }.sortedByDescending { it.task.id }
        if (failed.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section("失败", failed.size))
            sections.addAll(failed.map { DownloadGroupedItem.Task(it) })
        }

        val cancelled = displays.filter { it.task.state == DownloadState.CANCELLED }.sortedByDescending { it.task.id }
        if (cancelled.isNotEmpty()) {
            sections.add(DownloadGroupedItem.Section("已取消", cancelled.size))
            sections.addAll(cancelled.map { DownloadGroupedItem.Task(it) })
        }

        return sections
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1000 * 1000 -> "${"%.1f".format(bytesPerSec / (1000.0 * 1000.0))} MB/s"
            bytesPerSec >= 1000 -> "${bytesPerSec / 1000} KB/s"
            else -> "${bytesPerSec} B/s"
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}时${m}分${s}秒"
            m > 0 -> "${m}分${s}秒"
            else -> "${s}秒"
        }
    }
}
