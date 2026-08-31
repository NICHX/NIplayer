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

    /** Android 10- 是否已授予旧版 WRITE_EXTERNAL_STORAGE 运行时权限。 */
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