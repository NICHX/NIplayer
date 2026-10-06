package com.nichx.niplayer.common.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * 「调用其他应用打开 / 分享」本地文件的通用工具。
 *
 * 供文件浏览页（:feature:home）与播放器（:feature:player）复用：把应用持有的本地媒体
 * 交给系统选择器，由其他应用打开（[buildOpenWithIntent]）或分享（[buildShareIntent]）。
 *
 * 仅支持**本地**可读源：
 * - `content://`（本地媒体库 / SAF / FileProvider）直接透传
 * - `file://` 经 [FileProvider] 转成 `content://`（Android 7+ 禁止暴露裸 file:// URI）
 * - 其余（`http(s)://` 等远程源、SMB 的无 URI 数据源）不支持，返回 null
 *
 * 优点/缺点：不对共享 URI 做持久化授权（不调用 takePersistableUriPermission），
 * 接收方在本次任务存活期内可读；媒体文件通常即开即用，符合预期。
 */
object ExternalMediaShare {

    /** 依据文件名推断 MIME 类型；无法识别时按媒体类别回退（视频/音频/图片），最终为通配类型。 */
    fun mimeTypeOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot in 1 until fileName.length - 1) {
            val ext = fileName.substring(dot + 1).lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when {
            MediaFileTypes.isVideoFile(fileName) -> "video/*"
            MediaFileTypes.isAudioFile(fileName) -> "audio/*"
            MediaFileTypes.isImageFile(fileName) -> "image/*"
            else -> "*/*"
        }
    }

    /**
     * 构造「用其他应用打开」的 Intent（`ACTION_VIEW` + 选择器）。
     * 源不受支持（远程/无本地路径）时返回 null。
     */
    fun buildOpenWithIntent(context: Context, uri: Uri, mimeType: String): Intent? {
        val shareable = toShareableUri(context, uri) ?: return null
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareable, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(view, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 构造「分享到其他应用」的 Intent（`ACTION_SEND` + 系统分享面板）。
     * 源不受支持（远程/无本地路径）时返回 null。
     */
    fun buildShareIntent(context: Context, uri: Uri, mimeType: String): Intent? {
        val shareable = toShareableUri(context, uri) ?: return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareable)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 便捷入口：构造并启动「用其他应用打开」，返回是否成功拉起。
     * 失败（不支持 / 无匹配应用 / 权限异常）返回 false，调用方据此提示用户。
     */
    fun launchOpenWith(context: Context, uri: Uri, mimeType: String): Boolean =
        launch(context, buildOpenWithIntent(context, uri, mimeType))

    /** 便捷入口：构造并启动「分享」，返回是否成功拉起。 */
    fun launchShare(context: Context, uri: Uri, mimeType: String): Boolean =
        launch(context, buildShareIntent(context, uri, mimeType))

    private fun launch(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        // ViewModel 场景传入的是 ApplicationContext，必须补 NEW_TASK 才能启动 Activity
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 将本地 Uri 规整为可对外授予读取权限的 Uri：
     * - `content://` 直接返回
     * - `file://` 经 [FileProvider]（authority=`${packageName}.fileprovider`）转换
     * - 其它 scheme 返回 null
     */
    private fun toShareableUri(context: Context, uri: Uri): Uri? =
        when (uri.scheme?.lowercase()) {
            "content" -> uri
            "file" -> uri.path?.let { path ->
                runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(path),
                    )
                }.getOrNull()
            }
            else -> null
        }
}
