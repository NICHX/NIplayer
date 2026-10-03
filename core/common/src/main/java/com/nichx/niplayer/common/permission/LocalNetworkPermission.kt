package com.nichx.niplayer.common.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 局域网访问权限（Local Network Protection, LNP）助手。
 *
 * ## 背景
 *
 * Android 16 引入「本地网络保护」，**Android 17 起对 `targetSdk >= 37` 的应用强制生效**：
 * 访问局域网地址（SMB 445 / WebDAV 等）的 TCP/UDP 流量在获授权前会被网络栈直接拦截。
 * 官方行为：TCP 连接表现为**超时**、UDP 为 `EPERM` —— 与「地址填错 / 服务器不可达 / 凭据错误」
 * 在界面上无法区分，是「用户反馈无法连接局域网 SMB」的根因。
 *
 * ## 权限随版本变化
 *
 * - **Android 17（API 37+）**：必须声明并在运行时申请 [Manifest.permission.ACCESS_LOCAL_NETWORK]，
 *   否则局域网默认被屏蔽；
 * - **Android 16（API 36）**：LNP 为 opt-in，测试期使用同组权限
 *   [Manifest.permission.NEARBY_WIFI_DEVICES]（manifest 已声明且限制到 API 36，
 *   用户可在系统设置「附近的设备」中授予）；
 * - **Android 15 及以下**：局域网默认开放，无需任何权限。
 *
 * Android 16 的 opt-in 是否开启无法在运行时可靠探测，故 [requiredPermission] 只在 Android 17+
 * 返回强制权限；16 及以下由 manifest 声明 + [openAppSettings] 引导兜底。
 */
object LocalNetworkPermission {

    /**
     * 当前系统上访问局域网**必须**在运行时申请的权限；无需授权时返回 null。
     */
    fun requiredPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            Manifest.permission.ACCESS_LOCAL_NETWORK
        } else {
            null
        }

    /** 当前系统是否需要该运行时权限（Android 17+）。 */
    fun isRequired(): Boolean = requiredPermission() != null

    /** 是否已获授权；无需授权的版本恒为 true。 */
    fun isGranted(context: Context): Boolean {
        val permission = requiredPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** 跳转到本应用的系统设置详情页，供用户手动开启「本地网络 / 附近的设备」权限。 */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
