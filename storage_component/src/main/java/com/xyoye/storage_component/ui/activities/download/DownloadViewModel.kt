package com.xyoye.storage_component.ui.activities.download

import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.storage.download.DownloadManager
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class DownloadTaskDisplay(
    val task: DownloadTaskEntity,
    val speed: String = "",
    val eta: String = ""
)

sealed class DownloadGroupedItem {
    data class Section(val title: String, val count: Int) : DownloadGroupedItem()
    data class Task(val display: DownloadTaskDisplay) : DownloadGroupedItem()
}

class DownloadViewModel : BaseViewModel() {

    private val _taskSpeeds = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _taskEtas = MutableStateFlow<Map<Long, String>>(emptyMap())

    val displayItems: StateFlow<List<DownloadGroupedItem>> by lazy {
        combine(
            DownloadManager.allTasks,
            _taskSpeeds,
            _taskEtas
        ) { tasks, speeds, etas ->
            val displays = tasks.map { task ->
                DownloadTaskDisplay(
                    task = task,
                    speed = speeds[task.id] ?: "",
                    eta = etas[task.id] ?: ""
                )
            }
            groupByState(displays)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private val speedSamples = mutableMapOf<Long, MutableList<SpeedSample>>()

    init {
        viewModelScope.launch {
            DownloadManager.allTasks.collect { tasks ->
                val now = System.currentTimeMillis()
                for (task in tasks) {
                    if (task.state == DownloadState.DOWNLOADING && task.totalBytes > 0) {
                        val samples = speedSamples.getOrPut(task.id) { mutableListOf() }
                        samples.add(SpeedSample(task.downloadedBytes, now))
                        while (samples.size > 5) samples.removeAt(0)

                        if (samples.size >= 2) {
                            val first = samples.first()
                            val last = samples.last()
                            val elapsed = last.timeMs - first.timeMs
                            if (elapsed > 0) {
                                val bytesPerSec = ((last.bytes - first.bytes) * 1000L) / elapsed
                                _taskSpeeds.value = _taskSpeeds.value + (task.id to formatSpeed(bytesPerSec))

                                val remaining = task.totalBytes - task.downloadedBytes
                                if (bytesPerSec > 0) {
                                    val etaSecs = remaining / bytesPerSec
                                    _taskEtas.value = _taskEtas.value + (task.id to formatEta(etaSecs))
                                }
                            }
                        }
                    } else {
                        speedSamples.remove(task.id)
                        _taskSpeeds.value = _taskSpeeds.value - task.id
                        _taskEtas.value = _taskEtas.value - task.id
                    }
                }
            }
        }
    }

    fun pauseTask(taskId: Long) = DownloadManager.pauseTask(taskId)

    fun resumeTask(taskId: Long) = DownloadManager.resumeTask(taskId)

    fun cancelTask(taskId: Long) = DownloadManager.cancelTask(taskId)

    fun deleteTask(taskId: Long) = DownloadManager.deleteTask(taskId)

    fun clearRecord(taskId: Long) = DownloadManager.clearRecord(taskId)

    fun retryTask(taskId: Long) = DownloadManager.retryTask(taskId)

    fun removeCompleted() = DownloadManager.removeCompletedTasks()

    fun retryAllFailed() {
        viewModelScope.launch {
            val dao = com.xyoye.common_component.database.DatabaseManager.instance.getDownloadTaskDao()
            withContext(Dispatchers.IO) {
                val failed = dao.getByStates(listOf(DownloadState.FAILED))
                for (task in failed) {
                    dao.updateProgress(task.id, 0, DownloadState.WAITING)
                }
            }
        }
    }

    fun clearFailed() {
        viewModelScope.launch {
            val dao = com.xyoye.common_component.database.DatabaseManager.instance.getDownloadTaskDao()
            withContext(Dispatchers.IO) {
                dao.deleteByState(DownloadState.FAILED)
                dao.deleteByState(DownloadState.CANCELLED)
            }
        }
    }

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

    private data class SpeedSample(val bytes: Long, val timeMs: Long)
}