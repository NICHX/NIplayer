package com.nichx.niplayer.datastore

import android.net.Uri
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DownloadSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_DOWNLOAD_DIR_URI = "download_dir_uri"
    private const val KEY_DOWNLOAD_DIR_NAME = "download_dir_name"

    private val _downloadDirFlow = MutableStateFlow(loadDownloadDir())

    val downloadDirFlow: StateFlow<DownloadDirInfo> = _downloadDirFlow.asStateFlow()

    var downloadDirUri: String
        get() = _downloadDirFlow.value.uri
        set(value) {
            mmkv.encode(KEY_DOWNLOAD_DIR_URI, value)
            _downloadDirFlow.value = _downloadDirFlow.value.copy(uri = value)
        }

    var downloadDirName: String
        get() = _downloadDirFlow.value.name
        set(value) {
            mmkv.encode(KEY_DOWNLOAD_DIR_NAME, value)
            _downloadDirFlow.value = _downloadDirFlow.value.copy(name = value)
        }

    val isDownloadDirSet: Boolean
        get() = downloadDirUri.isNotBlank()

    fun setDownloadDir(uri: String, name: String) {
        downloadDirUri = uri
        downloadDirName = name
    }

    fun clearDownloadDir() {
        mmkv.remove(KEY_DOWNLOAD_DIR_URI)
        mmkv.remove(KEY_DOWNLOAD_DIR_NAME)
        _downloadDirFlow.value = DownloadDirInfo()
    }

    private fun loadDownloadDir(): DownloadDirInfo {
        val uri = mmkv.decodeString(KEY_DOWNLOAD_DIR_URI, "") ?: ""
        val name = mmkv.decodeString(KEY_DOWNLOAD_DIR_NAME, "") ?: ""
        return DownloadDirInfo(uri, name)
    }
}

data class DownloadDirInfo(
    val uri: String = "",
    val name: String = "",
)
