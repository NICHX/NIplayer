package com.nichx.niplayer.thumbnail

import java.security.MessageDigest
import java.util.Locale


internal fun md5(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    // Locale.ROOT：十六进制摘要必须与区域无关
    return bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
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
