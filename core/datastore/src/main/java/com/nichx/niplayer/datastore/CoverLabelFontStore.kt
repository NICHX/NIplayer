package com.nichx.niplayer.datastore

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 自定义字体路径的可观察状态：设置页写入后，播放页无需重启即生效。 */
private val fontPathState = MutableStateFlow(PlayerSettings.coverLabelFontPath)

/**
 * 「生成封面」上文件名所用字体的存储。
 *
 * 应用**不内置**字体：CJK 手写体即使子集化到常用字也有近 3MB，为一个占位封面不值当。
 * 默认使用系统字体；用户可在「设置 → 音频播放器设置 → 外观」里自选一个字体文件，
 * 选中后会**复制进应用私有目录**——既不依赖外部 URI 的长期授权，也不会因用户删掉原文件而失效。
 *
 * 放在 :core:datastore 是因为写入方（设置页在 :feature:home）与读取方（:feature:player）
 * 互不依赖，只有本模块对两边都可见。
 */
object CoverLabelFontStore {

    private const val DIR_NAME = "cover_label_font"
    private const val FILE_NAME = "custom_font"

    /** 当前自定义字体路径；空串表示未设置（用系统默认字体）。 */
    val fontPath: StateFlow<String> = fontPathState.asStateFlow()

    /** 当前可用的自定义字体文件；未设置、已被删除或为空文件时返回 null。 */
    fun file(): File? {
        val path = PlayerSettings.coverLabelFontPath
        if (path.isEmpty()) return null
        return File(path).takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * 应用（或清除）自定义字体。
     *
     * @param uri 用户选中的字体文件；传 null 表示恢复系统字体。
     * @return 是否成功。失败（读取失败 / 不是字体文件 / 磁盘不可写）时**不改动**已有设置。
     */
    fun apply(context: Context, uri: Uri?): Boolean {
        val target = File(File(context.filesDir, DIR_NAME), FILE_NAME)
        if (uri == null) {
            target.delete()
            persist(path = "", displayName = "")
            return true
        }
        // 先写临时文件、校验通过再落位：避免中途失败留下半个坏字体
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        val copied = runCatching {
            temp.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)

        if (!copied || !temp.looksLikeFont()) {
            temp.delete()
            return false
        }
        val placed = runCatching {
            temp.copyTo(target, overwrite = true).exists()
        }.getOrDefault(false)
        temp.delete()
        if (!placed) return false

        persist(path = target.absolutePath, displayName = context.displayNameOf(uri))
        return true
    }

    private fun persist(path: String, displayName: String) {
        PlayerSettings.coverLabelFontPath = path
        PlayerSettings.coverLabelFontName = displayName
        fontPathState.value = path
    }
}

/** 只认 TTF / OTF / TTC 三种文件头，避免用户随手选到别的文件后静默失败。 */
private fun File.looksLikeFont(): Boolean = runCatching {
    val head = ByteArray(4)
    inputStream().use { if (it.read(head) < 4) return false }
    val tag = String(head, Charsets.US_ASCII)
    tag == "OTTO" || tag == "true" || tag == "ttcf" ||
        (head[0] == 0x00.toByte() && head[1] == 0x01.toByte())
}.getOrDefault(false)

/** 取用户所选文件的显示名（仅用于设置页展示）；拿不到时返回空串。 */
private fun Context.displayNameOf(uri: Uri): String {
    val cursor: Cursor = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    }.getOrNull() ?: return ""
    return cursor.use {
        if (it.moveToFirst()) it.getString(0).orEmpty() else ""
    }
}
