package com.nichx.niplayer.feature.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.entity.UploadTaskEntity
import com.nichx.niplayer.storage.download.UploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 传输管理·上传 tab 的视图项。 */
data class UploadItemUi(
    val task: UploadTaskEntity,
    /** 0..1；总大小未知时为 -1。 */
    val progress: Float,
    /** 实时已上传字节（节流 DB 值可滞后于此）。 */
    val uploadedBytes: Long,
)

/**
 * 传输管理·上传 tab 的 ViewModel。
 *
 * 直接消费 [UploadManager]（App 级作用域后台调度，任务持久化于 Room），
 * 组合任务列表 + 实时字节数 → UI 进度视图项。
 */
@HiltViewModel
class UploadManagerViewModel @Inject constructor(
    private val uploadManager: UploadManager,
) : ViewModel() {

    val uploads: StateFlow<List<UploadItemUi>> = combine(
        uploadManager.tasks,
        uploadManager.taskProgress,
    ) { tasks, progress ->
        tasks.map { t ->
            val uploaded = progress[t.id] ?: t.uploadedBytes
            val p = if (t.totalBytes > 0) {
                (uploaded.toFloat() / t.totalBytes).coerceIn(0f, 1f)
            } else -1f
            UploadItemUi(t, p, uploaded)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(taskId: Long) = uploadManager.cancel(taskId)

    fun delete(taskId: Long) = viewModelScope.launch { uploadManager.delete(taskId) }

    fun clearCompleted() = viewModelScope.launch { uploadManager.clearCompleted() }

    fun clearFailed() = viewModelScope.launch { uploadManager.clearFailed() }
}