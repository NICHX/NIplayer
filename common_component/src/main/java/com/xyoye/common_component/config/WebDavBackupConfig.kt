package com.xyoye.common_component.config

import com.tencent.mmkv.MMKV

object WebDavBackupConfig {
    private val mmkv = MMKV.defaultMMKV()

    var enabled: Boolean
        get() = mmkv.decodeBool("webdav_backup_enabled", false)
        set(value) { mmkv.encode("webdav_backup_enabled", value) }

    var serverMode: String
        get() = mmkv.decodeString("webdav_backup_server_mode", "custom") ?: "custom"
        set(value) { mmkv.encode("webdav_backup_server_mode", value) }

    var customUrl: String
        get() = mmkv.decodeString("webdav_backup_url", "") ?: ""
        set(value) { mmkv.encode("webdav_backup_url", value) }

    var customAccount: String
        get() = mmkv.decodeString("webdav_backup_account", "") ?: ""
        set(value) { mmkv.encode("webdav_backup_account", value) }

    var customPassword: String
        get() = mmkv.decodeString("webdav_backup_password", "") ?: ""
        set(value) { mmkv.encode("webdav_backup_password", value) }

    var existingServerId: Int
        get() = mmkv.decodeInt("webdav_backup_server_id", 0)
        set(value) { mmkv.encode("webdav_backup_server_id", value) }

    var directory: String
        get() = mmkv.decodeString("webdav_backup_directory", "/NIplayer_backup") ?: "/NIplayer_backup"
        set(value) { mmkv.encode("webdav_backup_directory", value) }

    var keepCount: Int
        get() = mmkv.decodeInt("webdav_backup_keep_count", 3)
        set(value) { mmkv.encode("webdav_backup_keep_count", value) }
}