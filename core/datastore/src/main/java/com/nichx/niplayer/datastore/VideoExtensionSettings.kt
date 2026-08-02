package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 视频扩展名白名单配置（MMKV），迁移自旧仓库 `VideoExtension`。
 *
 * 用于本地扫描（MediaStore + 扩展目录）时识别视频文件。音频/图片/字幕扩展名
 * 仍硬编码在 [com.nichx.niplayer.feature.home.MediaFileTypes]，仅视频扩展名
 * 支持用户配置。
 *
 * 旧仓库通过 `@MMKVKotlinClass` 注解生成 `VideoExtension` 对象，v2 改为手动
 * MMKV 读写，避免编译期注解处理器依赖。
 *
 * 持久化格式：逗号分隔的小写扩展名字符串（如 "mp4,mkv,avi"），不含点号。
 */
object VideoExtensionSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_SUPPORT_VIDEO_EXTENSION = "support_video_extension"

    /** 默认支持的视频扩展名（小写，不含点号）。 */
    private val DEFAULT_EXTENSIONS = listOf(
        "3gp", "asf", "asx", "avi", "dat", "flv", "m2ts", "m3u8", "m4s",
        "m4v", "mkv", "mov", "mp4", "mpe", "mpeg", "mpg", "rm", "rmvb",
        "vob", "wmv",
    )

    /** 当前支持的扩展名列表（小写）。 */
    private val extensions: List<String>
        get() {
            val raw = mmkv.decodeString(KEY_SUPPORT_VIDEO_EXTENSION, null)
            return raw?.split(",")
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?: DEFAULT_EXTENSIONS
        }

    /** 逗号分隔的扩展名字符串（用于 UI 展示与编辑），如 "mp4,mkv,avi"。 */
    var supportText: String
        get() = extensions.joinToString(",")
        set(value) {
            val normalized = value.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            if (normalized.isNotEmpty()) {
                mmkv.encode(KEY_SUPPORT_VIDEO_EXTENSION, normalized.joinToString(","))
            }
        }

    /** 判断文件路径是否为支持的视频文件。 */
    fun isVideoFile(filePath: String): Boolean {
        val dotIndex = filePath.lastIndexOf('.')
        if (dotIndex < 0 || dotIndex == filePath.length - 1) return false
        val ext = filePath.substring(dotIndex + 1).lowercase()
        return ext in extensions
    }

    /** 重置为默认扩展名列表。 */
    fun resetDefault() {
        mmkv.encode(KEY_SUPPORT_VIDEO_EXTENSION, DEFAULT_EXTENSIONS.joinToString(","))
    }
}
