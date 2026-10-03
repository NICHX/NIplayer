package com.nichx.niplayer.thumbnail

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** 十六进制字符表：替代 `String.format("%02x", b)` 逐字节格式化（热路径，避免每次创建 Formatter）。 */
private val HEX_CHARS = "0123456789abcdef".toCharArray()

/**
 * 线程隔离的 [MessageDigest]。
 *
 * MD5 实例非线程安全，而 [MessageDigest.getInstance] 每次调用都有 provider 查找与对象分配开销；
 * 缩略图缓存键计算在批量生成时是热路径，故用 [ThreadLocal] 复用实例。
 */
private val MD5_DIGEST: ThreadLocal<MessageDigest> =
    ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

/**
 * 摘要结果记忆化缓存。
 *
 * 同一 `(storageId, filePath)` 在一次缩略图流程中被多次求摘要（缓存命中检查 → 生成 → 上传），
 * 记忆化可消除重复计算。映射纯函数、无失效问题；超过 [MD5_CACHE_MAX] 时整体清空以限制内存。
 */
private val MD5_CACHE = ConcurrentHashMap<String, String>(256)
private const val MD5_CACHE_MAX = 4096

internal fun md5(input: String): String {
    MD5_CACHE[input]?.let { return it }
    val digest = MD5_DIGEST.get()
    // digest(byte[]) 内部会 reset，无需显式 reset。
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    val hex = CharArray(bytes.size * 2)
    var i = 0
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        hex[i++] = HEX_CHARS[v ushr 4]
        hex[i++] = HEX_CHARS[v and 0x0F]
    }
    val result = String(hex)
    if (MD5_CACHE.size >= MD5_CACHE_MAX) MD5_CACHE.clear()
    MD5_CACHE[input] = result
    return result
}

/** 构造视频所在目录下的 `.thumb/` 子目录路径。 */
internal fun buildThumbDirPath(videoPath: String): String {
    val dirPath = videoPath.substringBeforeLast('/', "")
    return if (dirPath.isEmpty()) ".thumb" else "$dirPath/.thumb"
}

/** 构造音频所在目录下的 `.cover/` 子目录路径。 */
internal fun buildCoverDirPath(audioPath: String): String {
    val dirPath = audioPath.substringBeforeLast('/', "")
    return if (dirPath.isEmpty()) ".cover" else "$dirPath/.cover"
}
