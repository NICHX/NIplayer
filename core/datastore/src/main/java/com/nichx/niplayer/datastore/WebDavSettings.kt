package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WebDAV 服务器共享配置（MMKV + StateFlow）。
 *
 * 备份与恢复、播放历史云同步共用同一台 WebDAV 服务器：
 * 在「备份与同步」页的 WebDAV 服务器卡片中选择后写入此处，
 * 两侧功能都读取该配置，避免出现两套服务器选择漂移。
 */
object WebDavSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_LIBRARY_ID = "webdav_library_id"

    private const val UNSELECTED = -1

    private val _libraryIdFlow = MutableStateFlow(currentLibraryId())
    /** 当前所选 WebDAV 存储源 id 的 StateFlow，写入时自动更新。 */
    val libraryIdFlow: StateFlow<Int> = _libraryIdFlow.asStateFlow()

    /** 当前所选 WebDAV 存储源 id，-1 表示未选择。 */
    val libraryId: Int
        get() = _libraryIdFlow.value

    /** 设置所选 WebDAV 存储源 id，立即持久化并通知 StateFlow。 */
    fun setLibraryId(libraryId: Int) {
        mmkv.encode(KEY_LIBRARY_ID, libraryId)
        _libraryIdFlow.value = libraryId
    }

    private fun currentLibraryId(): Int = mmkv.decodeInt(KEY_LIBRARY_ID, UNSELECTED)
}
