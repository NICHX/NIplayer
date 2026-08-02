package com.nichx.niplayer.feature.home.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 缓存管理 ViewModel。
 *
 * 替代旧仓库 `CacheManagerViewModel`（user_component/ui/activities/cache_manager/），
 * v2 简化设计：
 *
 * - **动态扫描**：不硬编码缓存子目录，改为扫描 [Context.getCacheDir] 下所有子目录 + 文件，
 *   按目录名聚合展示。旧仓库用 [CacheType] 枚举固定 5 种缓存类型，v2 等实际功能落地后
 *   再按需补充命名映射（如 subtitle/play/cover/screenshot）。
 * - **全部清理**：递归删除 cacheDir 下所有内容（不删除 cacheDir 本身）。
 * - **按项清理**：删除指定子目录或文件。
 *
 * 不涉及 externalCacheDir（v2 未使用外部缓存目录）。
 */
@HiltViewModel
class CacheManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CacheManagerUiState(isLoading = true))
    val uiState: StateFlow<CacheManagerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 重新扫描缓存目录。 */
    fun refresh() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { scanCacheDir() }
            val totalSize = items.sumOf { it.sizeBytes }
            _uiState.update {
                CacheManagerUiState(
                    items = items,
                    totalSizeBytes = totalSize,
                    totalFileCount = items.sumOf { it.fileCount },
                    isLoading = false,
                )
            }
        }
    }

    /** 清理指定缓存项（子目录或文件）。 */
    fun clearCache(item: CacheItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val target = File(context.cacheDir, item.name)
                if (target.isDirectory) {
                    target.deleteRecursively()
                } else {
                    target.delete()
                }
            }
            refresh()
            _uiState.update { it.copy(toastMessage = "已清理：${item.displayName}") }
        }
    }

    /** 清理全部缓存。 */
    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
            refresh()
            _uiState.update { it.copy(toastMessage = "已清理全部缓存") }
        }
    }

    /** 消费 Toast 消息（UI 层显示后调用）。 */
    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun scanCacheDir(): List<CacheItem> {
        val cacheDir = context.cacheDir
        val files = cacheDir.listFiles() ?: return emptyList()
        return files
            .map { file ->
                val (size, count) = if (file.isDirectory) {
                    calculateDirSize(file)
                } else {
                    file.length() to 1
                }
                CacheItem(
                    name = file.name,
                    displayName = friendlyName(file.name),
                    sizeBytes = size,
                    fileCount = count,
                    isDirectory = file.isDirectory,
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    private fun friendlyName(dirName: String): String = CACHE_DIR_NAMES[dirName] ?: dirName

    private fun calculateDirSize(dir: File): Pair<Long, Int> {
        var size = 0L
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                size += f.length()
                count++
            }
        }
        return size to count
    }
}

/** 缓存项。 */
data class CacheItem(
    val name: String,
    val displayName: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val isDirectory: Boolean,
)

/** 缓存目录名 → 友好名映射。未知目录保持原名。 */
private val CACHE_DIR_NAMES = mapOf(
    "video_cover" to "视频缩略图",
    "audio_cover" to "音频封面",
    "image_thumb" to "图片缩略图",
    "seek_preview" to "进度预览缓存",
    "subtitle" to "字幕缓存",
)

/** 缓存管理 UI 状态。 */
data class CacheManagerUiState(
    val items: List<CacheItem> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val totalFileCount: Int = 0,
    val isLoading: Boolean = false,
    val toastMessage: String? = null,
)
