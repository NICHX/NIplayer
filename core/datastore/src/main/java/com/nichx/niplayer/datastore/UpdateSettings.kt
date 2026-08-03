package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 应用更新设置持久化（MMKV）。
 *
 * 维护两类状态：
 * - 自动检查节流时间戳：启动自动检查不超过 [AUTO_CHECK_INTERVAL_MS] 一次，
 *   避免每次冷启动都命中 GitHub API（无鉴权 60 次/小时/IP 的限制）。
 * - 待安装更新包记录：进程被杀后重启时，据此恢复"下载完成，是否安装"提示。
 */
object UpdateSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_LAST_AUTO_CHECK = "update_last_auto_check_ts"
    private const val KEY_DOWNLOADED_VERSION = "update_downloaded_version"
    private const val KEY_DOWNLOADED_FILE_NAME = "update_downloaded_file_name"

    /** 自动检查最小间隔：24 小时。 */
    const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 上次自动检查时间戳（毫秒），0 表示从未检查。 */
    fun getLastAutoCheckTimestamp(): Long = mmkv.decodeLong(KEY_LAST_AUTO_CHECK, 0L)

    /** 记录本次自动检查时间戳。 */
    fun setLastAutoCheckTimestamp(timestamp: Long) {
        mmkv.encode(KEY_LAST_AUTO_CHECK, timestamp)
    }

    /** 最近一次下载的更新包版本（"2.0.2"），空串表示无待安装更新。 */
    fun getDownloadedVersion(): String = mmkv.decodeString(KEY_DOWNLOADED_VERSION, "").orEmpty()

    /** 最近一次下载的更新包文件名（如 "NIplayer-v2.0.2.apk"）。 */
    fun getDownloadedFileName(): String = mmkv.decodeString(KEY_DOWNLOADED_FILE_NAME, "").orEmpty()

    /** 记录待安装的更新包（下载入队时写入，下载失败/安装后清除）。 */
    fun setDownloadedApk(version: String, fileName: String) {
        mmkv.encode(KEY_DOWNLOADED_VERSION, version)
        mmkv.encode(KEY_DOWNLOADED_FILE_NAME, fileName)
    }

    /** 清除待安装更新包记录。 */
    fun clearDownloadedApk() {
        mmkv.encode(KEY_DOWNLOADED_VERSION, "")
        mmkv.encode(KEY_DOWNLOADED_FILE_NAME, "")
    }
}
