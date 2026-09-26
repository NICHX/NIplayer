package com.nichx.niplayer.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 共享存储原生访问权限助手。
 *
 * 传输管理的下载目录使用**原生直写**（绝对路径 + File），替代 SAF。
 * 需要的前提权限：
 * - Android 11（API 30）及以上 → 「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）
 * - Android 10（API 29）及以下 → 旧版 WRITE_EXTERNAL_STORAGE 运行时授权
 */
object StorageAccess {

    /** Android 11+ 是否已授予「所有文件访问权限」。 */
    fun hasAllFilesAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /**
     * Android 10- 是否已授予旧版 WRITE_EXTERNAL_STORAGE 运行时权限。
     *
     * ⚠️ 当前恒为 false：Manifest 中该权限声明带 `maxSdkVersion="28"`（API 29 上根本不声明），
     * 且应用从未发起过该权限的运行时请求。保留此函数是为了表达能力与后续可能接入的权限链路，
     * 实际可用性由 [isNativeDownloadSupported] 判定。
     */
    fun hasLegacyWriteAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 当前设备是否允许对共享存储做原生写入。 */
    fun canWriteSharedStorage(context: Context): Boolean =
        hasAllFilesAccess(context) || hasLegacyWriteAccess(context)

    /**
     * 当前系统版本是否支持「原生直写共享存储」的下载方式。
     *
     * 该方式依赖 Android 11（API 30）起可用的「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）。
     * Android 10 及以下需要旧版 [Manifest.permission.WRITE_EXTERNAL_STORAGE] 运行时权限，
     * 而本应用未接入该权限链路（[openAllFilesAccessSettings] 在这些版本上是空操作）。
     *
     * 调用方应先用本函数判定能力：不支持的版本上**如实告知用户**，
     * 而不是弹一个「点了没反应」的授权框。
     */
    fun isNativeDownloadSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * 打开系统授权页：
     * - Android 11+ 打开「所有文件访问权限」设置页
     * - Android 10- 由调用方发起 WRITE_EXTERNAL_STORAGE 运行时请求
     */
    fun openAllFilesAccessSettings(context: Context) {
        if (!hasAllFilesAccess(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            context.startActivity(intent)
        }
    }
}
