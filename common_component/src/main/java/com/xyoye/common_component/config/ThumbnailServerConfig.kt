package com.xyoye.common_component.config

import com.tencent.mmkv.MMKV

object ThumbnailServerConfig {
    private const val KEY_PREFIX = "server_thumbnail_enabled_"

    private val mmkv by lazy { MMKV.defaultMMKV() }

    fun isServerThumbnailEnabled(libraryId: Int): Boolean {
        return mmkv.decodeBool("$KEY_PREFIX$libraryId", true)
    }

    fun putServerThumbnailEnabled(libraryId: Int, enabled: Boolean) {
        mmkv.encode("$KEY_PREFIX$libraryId", enabled)
    }
}
