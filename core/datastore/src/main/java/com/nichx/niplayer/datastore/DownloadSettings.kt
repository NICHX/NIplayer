package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载目录设置。
 *
 * 存储为共享存储上的**绝对路径**（如 `/storage/emulated/0/Download/NIplayer`），
 * 配合「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）直接以原生 File 写入，
 * 避免 SAF（content://）经 ContentProvider 虚拟层写入导致的性能损耗。
 *
 * 目标存储 URL 统一由 [downloadDirTargetUrl] 转换为 `file://` 形式前传，
 * DownloadManager 据此走原生磁盘直写分支。
 */
object DownloadSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_DOWNLOAD_DIR_PATH = "download_dir_path"
    private const val KEY_DOWNLOAD_DIR_NAME = "download_dir_name"

    private val _downloadDirFlow = MutableStateFlow(loadDownloadDir())

    val downloadDirFlow: StateFlow<DownloadDirInfo> = _downloadDirFlow.asStateFlow()

    var downloadDirPath: String
        get() = _downloadDirFlow.value.path
        set(value) {
            mmkv.encode(KEY_DOWNLOAD_DIR_PATH, value)
            _downloadDirFlow.value = _downloadDirFlow.value.copy(path = value)
        }

    var downloadDirName: String
        get() = _downloadDirFlow.value.name
        set(value) {
            mmkv.encode(KEY_DOWNLOAD_DIR_NAME, value)
            _downloadDirFlow.value = _downloadDirFlow.value.copy(name = value)
        }

    val isDownloadDirSet: Boolean
        get() = downloadDirPath.isNotBlank()

    /** 下载目标存储 URL（`file://` 形式）；未设置时返回 null。 */
    val downloadDirTargetUrl: String?
        get() = if (isDownloadDirSet) "file://$downloadDirPath" else null

    fun setDownloadDir(path: String, name: String) {
        downloadDirPath = path
        downloadDirName = name
    }

    fun clearDownloadDir() {
        mmkv.remove(KEY_DOWNLOAD_DIR_PATH)
        mmkv.remove(KEY_DOWNLOAD_DIR_NAME)
        _downloadDirFlow.value = DownloadDirInfo()
    }

    private fun loadDownloadDir(): DownloadDirInfo {
        val path = mmkv.decodeString(KEY_DOWNLOAD_DIR_PATH, "") ?: ""
        val name = mmkv.decodeString(KEY_DOWNLOAD_DIR_NAME, "") ?: ""
        return DownloadDirInfo(path, name)
    }
}

data class DownloadDirInfo(
    val path: String = "",
    val name: String = "",
)