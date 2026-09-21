package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
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
 * 简化缓存管理设计：
 *
 * - **动态扫描**：不硬编码缓存子目录，改为扫描 [Context.getCacheDir] 下所有子目录 + 文件，
 *   按目录名聚合展示。不硬编码缓存类型枚举，待实际功能落地后
 *   再按需补充命名映射（如 subtitle/play/cover/screenshot）。
 * - **全部清理**：递归删除 cacheDir 下内容（不删除 cacheDir 本身），但跳过下方两类目录。
 * - **按项清理**：删除指定子目录或文件。
 *
 * 不涉及 externalCacheDir（v2 未使用外部缓存目录）。
 *
 * ## 排除规则（缺陷修复）
 *
 * 原实现无差别 `cacheDir.listFiles()?.forEach { it.deleteRecursively() }`，会连带删掉两类
 * **不属于本页面管辖**的目录，造成静默数据丢失与运行时状态不一致：
 *
 * 1. [APP_DATA_DIRS] —— `download/` 是 [com.nichx.niplayer.storage.download.DownloadManager]
 *    存放**已下载成品**的目标目录（`<cache>/download/<fileName>`）。删掉它并不会同步
 *    `download_task` 表，任务仍显示 COMPLETED 且 `downloadedBytes` 不变 → 记录与磁盘不一致，
 *    用户以为文件还在。它属于应用数据而非缓存，故既不统计也不清理。
 * 2. [PROTECTED_CACHE_DIRS] —— `exo_media_cache/` 由进程级 media3 `SimpleCache` 单例独占
 *    （内部索引 + 目录锁）。直接删除其文件会让 SimpleCache 的内存索引与磁盘内容不一致。
 *    它仍是缓存，因此**照常展示占用**，但不可用「删文件」的方式清理（[CacheItem.isProtected]），
 *    清理须走 SimpleCache 自身的 API（见 `PlayerModule.provideMediaCache` 的说明）。
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

    /** 清理指定缓存项（子目录或文件）。受保护项不处理。 */
    fun clearCache(item: CacheItem) {
        if (item.isProtected) {
            _uiState.update {
                it.copy(toastMessage = context.getString(R.string.cache_manager_protected_hint))
            }
            return
        }
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
            _uiState.update { it.copy(toastMessage = context.getString(R.string.cache_cleared_item, item.displayName)) }
        }
    }

    /**
     * 清理全部缓存。
     *
     * 跳过 [APP_DATA_DIRS]（非缓存的应用数据）与 [PROTECTED_CACHE_DIRS]（需经自身 API 清理），
     * 其余目录与文件照旧递归删除。
     */
    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name in APP_DATA_DIRS || file.name in PROTECTED_CACHE_DIRS) return@forEach
                    file.deleteRecursively()
                }
            }
            refresh()
            _uiState.update { it.copy(toastMessage = context.getString(R.string.cache_cleared_all)) }
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
            .filter { it.name !in APP_DATA_DIRS }
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
                    isProtected = file.name in PROTECTED_CACHE_DIRS,
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    private fun friendlyName(dirName: String): String =
        CACHE_DIR_NAME_RES[dirName]?.let { context.getString(it) } ?: dirName

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
    /**
     * 该目录属于缓存但**不可用「直接删除文件」的方式清理**（须经其自身 API）。
     * 为 true 时 UI 不提供单项清理入口。
     */
    val isProtected: Boolean = false,
)

/**
 * 不属于「缓存」的应用数据目录：不统计、不清理，由对应功能模块自行管理。
 *
 * `download` = [com.nichx.niplayer.storage.download.DownloadManager] 的下载成品目录。
 * 清理它必须同步 `download_task` 表状态，否则会出现「任务显示已完成但文件已消失」的不一致，
 * 因此这里直接交给下载管理页处理，不在缓存页暴露。
 */
private val APP_DATA_DIRS = setOf("download")

/**
 * 属于缓存、但必须经其自身 API 清理的目录（直接删文件会破坏其内存索引）。
 *
 * `exo_media_cache` 由 media3 `SimpleCache` 单例独占；名字取自
 * [com.nichx.niplayer.player.kernel.di.PlayerModule.EXO_MEDIA_CACHE_DIR]，避免两处字符串漂移。
 */
private val PROTECTED_CACHE_DIRS = setOf(
    com.nichx.niplayer.player.kernel.di.PlayerModule.EXO_MEDIA_CACHE_DIR
)

/** 缓存目录名 → 友好名资源 ID 映射。未知目录保持原名。 */
private val CACHE_DIR_NAME_RES = mapOf(
    "video_cover" to R.string.cache_type_video_cover,
    "audio_cover" to R.string.cache_type_audio_cover,
    "image_thumb" to R.string.cache_type_image_thumb,
    "seek_preview" to R.string.cache_type_seek_preview,
    "subtitle" to R.string.cache_type_subtitle,
    com.nichx.niplayer.player.kernel.di.PlayerModule.EXO_MEDIA_CACHE_DIR to R.string.cache_type_player_cache,
)

/** 缓存管理 UI 状态。 */
data class CacheManagerUiState(
    val items: List<CacheItem> = emptyList(),
    val totalSizeBytes: Long = 0L,
    val totalFileCount: Int = 0,
    val isLoading: Boolean = false,
    val toastMessage: String? = null,
)
