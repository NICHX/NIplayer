package com.nichx.niplayer.datastore

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * 在线匹配（歌词 / 封面）缓存目录的统一出入口。
 *
 * 缓存目录命名统一定义在此，audio / 设置页均通过本对象读写与清理，避免目录路径
 * 在多模块间漂移。matchKey 约定与 [OnlineMatchBlacklist] 一致：
 * - 远程 / 媒体库文件：`sid:<storageId>:<路径>`
 * - 本地文件：`local:<uri 或路径>`
 */
object OnlineMatchCache {

    /** 在线歌词缓存目录（relative to context.cacheDir）。 */
    private const val LRC_DIR = "lrc_cache"

    /** 在线匹配封面缓存目录（仅 `api_` 前缀的为在线获取，可清理）。 */
    private const val COVER_DIR = "audio_covers"

    /** 单首歌曲的在线歌词缓存文件。 */
    fun lrcFile(context: Context, matchKey: String): File =
        File(File(context.cacheDir, LRC_DIR), "${md5(matchKey)}.lrc")

    fun readLrc(context: Context, matchKey: String): String? = try {
        val file = lrcFile(context, matchKey)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }

    fun saveLrc(context: Context, matchKey: String, content: String) {
        try {
            lrcFile(context, matchKey).parentFile?.mkdirs()
            lrcFile(context, matchKey).writeText(content)
        } catch (_: Exception) {
        }
    }

    /** 仅删除单首歌曲的在线歌词缓存（清空显示，保留程序内其它曲目缓存）。 */
    fun removeLrc(context: Context, matchKey: String) {
        try {
            lrcFile(context, matchKey).delete()
        } catch (_: Exception) {
        }
    }

    /**
     * 清空全部在线匹配数据：删除所有在线歌词缓存 + 在线封面缓存，并清空匹配忽略列表。
     * 保留本地媒体内嵌封面缓存（`local_audio_*`），避免误删正常封面后重复提取。
     */
    fun clearAll(context: Context) {
        try {
            File(context.cacheDir, LRC_DIR).deleteRecursively()
        } catch (_: Exception) {
        }
        try {
            File(context.cacheDir, COVER_DIR).listFiles()
                ?.filter { it.name.startsWith("api_") }?.forEach { it.delete() }
        } catch (_: Exception) {
        }
        OnlineMatchBlacklist.clear()
    }

    /** 字符串 MD5，用于缓存文件名。与播放器封面缓存共用同一实现保证一致性。 */
    fun md5(input: String): String = try {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        // Locale.ROOT：十六进制摘要必须与区域无关，否则土耳其语等区域会改变字母大小写映射
        bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    } catch (_: Exception) {
        input.hashCode().toUInt().toString(16)
    }
}
