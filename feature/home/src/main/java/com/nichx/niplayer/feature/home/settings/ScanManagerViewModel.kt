package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.bean.FolderBean
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.ExtendFolderEntity
import com.nichx.niplayer.database.sync.PlayHistorySyncDeleter
import com.nichx.niplayer.storage.scanner.VideoScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 扫描管理 ViewModel。
 *
 * 替代旧仓库 `ScanExtendFragmentViewModel` + `ScanFilterFragmentViewModel`（两个子 Fragment
 * 各自的 ViewModel），v2 合并为单一 ViewModel + Tab UI。
 *
 * 职责：
 * - **扩展目录管理**：添加（扫描入库）/ 删除（清理该目录视频 + 重新扫描剩余目录）
 * - **屏蔽目录管理**：按 folder_path 批量切换 filter 字段
 * - **视频扩展名配置**：由 UI 层直接读写 [com.nichx.niplayer.datastore.VideoExtensionSettings]
 *
 * 扩展目录添加流程：
 * 1. 校验路径存在且可读
 * 2. [VideoScanner.scanExtendFolder] 扫描该目录（File 递归遍历 + MediaMetadataRetriever 提取时长）
 * 3. 插入 [ExtendFolderEntity] 到 extend_folder 表
 * 4. 视频已由 VideoScanner 增量同步到 video 表
 */
@HiltViewModel
class ScanManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extendFolderDao: ExtendFolderDao,
    private val videoDao: VideoDao,
    private val syncDeleter: PlayHistorySyncDeleter,
    private val scanner: VideoScanner,
) : ViewModel() {

    /** 扩展目录列表（自动响应 DB 变更）。 */
    private val extendFoldersFlow = extendFolderDao.observeAll()

    /** 屏蔽目录列表（getAllFolder Flow，聚合 folder_path + file_count + filter）。 */
    private val foldersFlow = videoDao.getAllFolder()

    /** UI 状态：合并两个 Flow + 一次性消息。 */
    val uiState: StateFlow<ScanManagerUiState> = combine(
        extendFoldersFlow,
        foldersFlow,
    ) { extendFolders, folders ->
        ScanManagerUiState(
            extendFolders = extendFolders,
            filterFolders = folders,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScanManagerUiState(isLoading = true),
    )

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    /**
     * 添加扩展目录。
     *
     * @param folderPath 目录绝对路径
     */
    fun addExtendFolder(folderPath: String) {
        val path = folderPath.trim()
        if (path.isEmpty()) {
            _toastMessage.update { context.getString(R.string.scan_manager_path_empty) }
            return
        }
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            _toastMessage.update { context.getString(R.string.scan_manager_path_invalid) }
            return
        }
        if (!folder.canRead()) {
            _toastMessage.update { context.getString(R.string.scan_manager_path_unreadable) }
            return
        }

        viewModelScope.launch {
            // 先扫描目录，获取视频数量
            val count = scanner.scanExtendFolder(path)
            if (count == 0) {
                _toastMessage.update { context.getString(R.string.scan_manager_no_video) }
                return@launch
            }
            // 插入 extend_folder 记录
            extendFolderDao.insert(ExtendFolderEntity(folderPath = path, childCount = count))
            _toastMessage.update { context.getString(R.string.scan_manager_added, count) }
        }
    }

    /** 删除扩展目录：清理该目录视频 + 从 extend_folder 表删除 + 重新扫描剩余目录。 */
    fun removeExtendFolder(entity: ExtendFolderEntity) {
        viewModelScope.launch {
            scanner.removeExtendFolder(entity.folderPath)
            _toastMessage.update { context.getString(R.string.scan_manager_removed, entity.folderPath) }
        }
    }

    /** 切换目录屏蔽状态。filter=true 表示屏蔽（不在普通列表显示）。 */
    fun toggleFolderFilter(folder: FolderBean) {
        viewModelScope.launch {
            val newFilter = !folder.isFilter
            videoDao.updateFolderFilter(newFilter, folder.folderPath)
            if (newFilter) {
                syncDeleter.deleteByStoragePathPrefix(folder.folderPath)
            }
        }
    }

    /** 消费 Toast 消息。 */
    fun consumeToast() {
        _toastMessage.update { null }
    }
}

/** 扫描管理 UI 状态。 */
data class ScanManagerUiState(
    val extendFolders: List<ExtendFolderEntity> = emptyList(),
    val filterFolders: List<FolderBean> = emptyList(),
    val isLoading: Boolean = false,
)
