package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 播放历史云同步配置（MMKV + StateFlow）。
 *
 * 配置项：
 * - [enabled]：总开关。在「备份与同步」页选择 WebDAV 服务器后自动启用，也可手动关闭
 * - [autoSync]：自动同步（应用启动 / 播放器退出后触发）
 * - [deviceId]：本设备唯一标识（首次启用生成 UUID）。**不随备份恢复**，跨设备恢复后重新生成，
 *   避免两台设备共享同一 deviceId 导致云端文件互相覆盖
 * - [lastSyncedAt]：增量同步游标（仅全部同步成功后推进）
 * - 上次同步结果（时间/成功/消息），供 UI 展示
 */
object PlayHistorySyncSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_ENABLED = "play_history_sync_enabled"
    private const val KEY_AUTO_SYNC = "play_history_sync_auto"
    private const val KEY_DEVICE_ID = "play_history_sync_device_id"
    private const val KEY_LAST_SYNCED_AT = "play_history_sync_last_synced_at"
    private const val KEY_LAST_SYNC_TIME = "play_history_sync_last_time"
    private const val KEY_LAST_SYNC_SUCCESS = "play_history_sync_last_success"
    private const val KEY_LAST_SYNC_MESSAGE = "play_history_sync_last_message"

    private val _flow = MutableStateFlow(loadConfig())

    /** 同步配置 StateFlow，写入时自动更新。 */
    val flow: StateFlow<PlayHistorySyncConfig> = _flow.asStateFlow()

    /** 云同步总开关，默认关闭。 */
    var enabled: Boolean
        get() = _flow.value.enabled
        set(value) {
            _flow.value = _flow.value.copy(enabled = value)
            persist(_flow.value)
        }

    /** 自动同步开关（应用启动 / 播放器退出后触发），默认关闭。 */
    var autoSync: Boolean
        get() = _flow.value.autoSync
        set(value) {
            _flow.value = _flow.value.copy(autoSync = value)
            persist(_flow.value)
        }

    /** 本设备唯一标识，首次启用时生成 UUID。 */
    val deviceId: String
        get() = _flow.value.deviceId

    /** 增量同步游标。 */
    var lastSyncedAt: Long
        get() = _flow.value.lastSyncedAt
        set(value) {
            _flow.value = _flow.value.copy(lastSyncedAt = value)
            persist(_flow.value)
        }

    /** 上次同步时间（ms），0 表示从未同步。 */
    val lastSyncTime: Long
        get() = _flow.value.lastSyncTime

    /** 上次同步是否成功。 */
    val lastSyncSuccess: Boolean
        get() = _flow.value.lastSyncSuccess

    /** 上次同步结果消息（失败时含用户可读错误）。 */
    val lastSyncMessage: String
        get() = _flow.value.lastSyncMessage

    /** 记录一次同步结果，供 UI 展示。 */
    fun recordSyncResult(success: Boolean, message: String) {
        _flow.value = _flow.value.copy(
            lastSyncTime = System.currentTimeMillis(),
            lastSyncSuccess = success,
            lastSyncMessage = message,
        )
        persist(_flow.value)
    }

    private const val KEY_REMOTE_MTIME_PREFIX = "play_history_sync_remote_mtime_"
    private const val KEY_REMOTE_LENGTH_PREFIX = "play_history_sync_remote_len_"
    private const val KEY_REMOTE_SYNCED_AT_PREFIX = "play_history_sync_remote_synced_at_"

    /**
     * 读取远端设备文件的元数据快照（增量拉取跳过 / 废弃设备判定用）。
     *
     * 上次成功同步后记录的 [RemoteFileMeta]；未记录（首次同步 / 新设备）返回 null。
     * 直接操作 MMKV（非 UI 配置，不进 StateFlow 快照）。
     */
    fun getRemoteFileMeta(fileName: String): RemoteFileMeta? {
        val mtime = mmkv.decodeLong(KEY_REMOTE_MTIME_PREFIX + fileName, 0)
        val length = mmkv.decodeLong(KEY_REMOTE_LENGTH_PREFIX + fileName, 0)
        val syncedAt = mmkv.decodeLong(KEY_REMOTE_SYNCED_AT_PREFIX + fileName, 0)
        if (mtime <= 0 && length <= 0 && syncedAt <= 0) return null
        return RemoteFileMeta(mtime, length, syncedAt)
    }

    /** 记录远端设备文件元数据快照（成功拉取解析后调用）。 */
    fun setRemoteFileMeta(fileName: String, mtime: Long, length: Long, syncedAt: Long) {
        mmkv.encode(KEY_REMOTE_MTIME_PREFIX + fileName, mtime)
        mmkv.encode(KEY_REMOTE_LENGTH_PREFIX + fileName, length)
        mmkv.encode(KEY_REMOTE_SYNCED_AT_PREFIX + fileName, syncedAt)
    }

    /** 清除远端设备文件元数据快照（文件被判定废弃删除时调用）。 */
    fun clearRemoteFileMeta(fileName: String) {
        mmkv.encode(KEY_REMOTE_MTIME_PREFIX + fileName, 0L)
        mmkv.encode(KEY_REMOTE_LENGTH_PREFIX + fileName, 0L)
        mmkv.encode(KEY_REMOTE_SYNCED_AT_PREFIX + fileName, 0L)
    }

    /** 确保存在设备标识，缺失时生成 UUID 并持久化。 */
    fun ensureDeviceId() {
        if (_flow.value.deviceId.isBlank()) {
            _flow.value = _flow.value.copy(deviceId = UUID.randomUUID().toString())
            persist(_flow.value)
        }
    }

    /** 恢复后重新生成设备标识（避免与旧设备共享云端文件），保留其余同步配置。 */
    fun resetDeviceId() {
        _flow.value = _flow.value.copy(deviceId = UUID.randomUUID().toString())
        persist(_flow.value)
    }

    private fun loadConfig(): PlayHistorySyncConfig {
        val config = PlayHistorySyncConfig(
            enabled = mmkv.decodeBool(KEY_ENABLED, false),
            autoSync = mmkv.decodeBool(KEY_AUTO_SYNC, false),
            deviceId = mmkv.decodeString(KEY_DEVICE_ID, "") ?: "",
            lastSyncedAt = mmkv.decodeLong(KEY_LAST_SYNCED_AT, 0),
            lastSyncTime = mmkv.decodeLong(KEY_LAST_SYNC_TIME, 0),
            lastSyncSuccess = mmkv.decodeBool(KEY_LAST_SYNC_SUCCESS, true),
            lastSyncMessage = mmkv.decodeString(KEY_LAST_SYNC_MESSAGE, "") ?: "",
        )
        return config
    }

    private fun persist(config: PlayHistorySyncConfig) {
        mmkv.encode(KEY_ENABLED, config.enabled)
        mmkv.encode(KEY_AUTO_SYNC, config.autoSync)
        if (config.deviceId.isNotBlank()) {
            mmkv.encode(KEY_DEVICE_ID, config.deviceId)
        }
        mmkv.encode(KEY_LAST_SYNCED_AT, config.lastSyncedAt)
        mmkv.encode(KEY_LAST_SYNC_TIME, config.lastSyncTime)
        mmkv.encode(KEY_LAST_SYNC_SUCCESS, config.lastSyncSuccess)
        mmkv.encode(KEY_LAST_SYNC_MESSAGE, config.lastSyncMessage)
    }
}

/** 播放历史云同步配置快照。 */
data class PlayHistorySyncConfig(
    val enabled: Boolean,
    val autoSync: Boolean,
    val deviceId: String,
    val lastSyncedAt: Long,
    val lastSyncTime: Long,
    val lastSyncSuccess: Boolean,
    val lastSyncMessage: String,
)

/** 远端设备文件的元数据快照（增量拉取跳过 / 废弃设备判定用）。 */
data class RemoteFileMeta(
    val mtime: Long,
    val length: Long,
    /** 该设备文件内记录的最后同步时间（心跳），0 表示旧格式文件。 */
    val syncedAt: Long,
)
