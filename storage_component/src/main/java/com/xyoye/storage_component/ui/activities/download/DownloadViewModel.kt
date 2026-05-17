package com.xyoye.storage_component.ui.activities.download

import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.storage.download.DownloadManager
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel : BaseViewModel() {

    val allTasks: StateFlow<List<DownloadTaskEntity>> = DownloadManager.allTasks

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    fun pauseTask(taskId: Long) {
        DownloadManager.pauseTask(taskId)
    }

    fun resumeTask(taskId: Long) {
        DownloadManager.resumeTask(taskId)
    }

    fun cancelTask(taskId: Long) {
        DownloadManager.cancelTask(taskId)
    }

    fun deleteTask(taskId: Long) {
        DownloadManager.deleteTask(taskId)
    }

    fun clearRecord(taskId: Long) {
        DownloadManager.clearRecord(taskId)
    }

    fun removeCompleted() {
        DownloadManager.removeCompletedTasks()
    }

    fun clearFailed() {
        viewModelScope.launch {
            val dao = com.xyoye.common_component.database.DatabaseManager.instance.getDownloadTaskDao()
            dao.deleteByState(DownloadState.FAILED)
            dao.deleteByState(DownloadState.CANCELLED)
        }
    }
}
