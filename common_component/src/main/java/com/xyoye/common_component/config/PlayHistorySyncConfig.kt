package com.xyoye.common_component.config

import com.tencent.mmkv.MMKV

object PlayHistorySyncConfig {
    private val mmkv = MMKV.defaultMMKV()

    var enabled: Boolean
        get() = mmkv.decodeBool("play_history_sync_enabled", false)
        set(value) { mmkv.encode("play_history_sync_enabled", value) }

    var lastSyncTime: Long
        get() = mmkv.decodeLong("play_history_sync_last_time", 0L)
        set(value) { mmkv.encode("play_history_sync_last_time", value) }

    var deviceId: String
        get() = mmkv.decodeString("play_history_sync_device_id", "") ?: ""
        set(value) { mmkv.encode("play_history_sync_device_id", value) }

    fun ensureDeviceId(): String {
        val id = deviceId
        if (id.isNotEmpty()) return id
        val newId = java.util.UUID.randomUUID().toString()
        deviceId = newId
        return newId
    }
}
