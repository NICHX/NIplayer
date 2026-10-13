package com.nichx.niplayer.thumbnail

import android.content.Context
import android.graphics.Bitmap
import com.nichx.niplayer.storage.StorageFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 缩略图本地缓存存取。
 *
 * 统一管理三个缓存目录（`video_cover` / `audio_cover` / `image_thumb`）、
 * 缓存文件命名（`MD5("$storageId-$filePath").jpg`）、JPEG 落盘、容量淘汰与清理逻辑。
 *
 * 与旧 [ThumbnailManager] 的差别：把原先散落在管理器里的缓存相关私有方法集中到此，
 * 并把「每个目录一份节流时间戳」的静态表改为实例字段（生命周期与单例一致，语义不变）。
 */
internal class ThumbnailStore(context: Context) {

    val videoDir = File(context.cacheDir, "video_cover")
    val audioDir = File(context.cacheDir, "audio_cover")
    val imageDir = File(context.cacheDir, "image_thumb")

    /** 每个缓存目录上次执行容量淘汰的时间戳（节流，避免批量生成时逐张全量扫描）。 */
    private val lastTrimAt = ConcurrentHashMap<String, AtomicLong>()

    /** 指定缓存目录下该文件的缓存文件（不检查是否存在）。 */
    fun fileFor(dir: File, storageId: Int, filePath: String): File =
        File(dir, "${md5("$storageId-$filePath")}.jpg")

    /** 命中本地缓存则返回其绝对路径，否则 null。纯文件系统检查，可在主线程调用。 */
    fun cachedPath(dir: File, storageId: Int, filePath: String): String? {
        val file = fileFor(dir, storageId, filePath)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private fun noCoverMarker(storageId: Int, filePath: String): File =
        File(audioDir, "${md5("$storageId-$filePath")}.no_cover")

    /**
     * 音频封面「源版本」记录：`MD5(...).ver`，内容为源文件的**大小**。
     *
     * 生成封面（或确认无封面）时写入，用于判断本地缓存是否仍对应当前音频文件 ——
     * 只按路径命名的缓存无法感知「就地更新了内嵌封面 / 换掉了目录封面」这类外部改动。
     *
     * 用「大小」而非修改时间作版本：各调用方都能拿到 size（文件列表 / 播放列表 / 虚拟文件），
     * 比较口径一致；若混用「有时间/没时间」两种口径会互相判为过期而反复重算。
     */
    private fun audioVersionMarker(storageId: Int, filePath: String): File =
        File(audioDir, "${md5("$storageId-$filePath")}.ver")

    /** 记录本次生成封面时源音频的大小；未知（<=0）时不写。 */
    fun writeAudioCoverVersion(storageId: Int, filePath: String, length: Long) {
        if (length <= 0L) return
        try {
            val file = audioVersionMarker(storageId, filePath)
            file.parentFile?.mkdirs()
            file.writeText("$length")
        } catch (_: Exception) {
        }
    }

    private fun readAudioCoverVersion(storageId: Int, filePath: String): Long? = try {
        audioVersionMarker(storageId, filePath).takeIf { it.isFile }
            ?.readText()?.trim()?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    /** 该音频是否已确认无内嵌封面（避免反复扫描）。 */
    fun hasNoCover(storageId: Int, filePath: String): Boolean = noCoverMarker(storageId, filePath).exists()

    /** 标记该音频无内嵌封面。 */
    fun markNoCover(storageId: Int, filePath: String) {
        try {
            noCoverMarker(storageId, filePath).createNewFile()
        } catch (_: Exception) {
        }
    }

    /**
     * 本地音频封面是否已过期（源文件在生成之后被改过）。
     *
     * 判据优先级：
     * 1. 有版本记录 → 比源文件大小（与时钟无关）；
     * 2. 无记录（本功能上线前的旧缓存）→ 退化为「源文件修改时间 vs 缓存写入时间」，带
     *    [MTIME_SLACK_MS] 容差（写入版本记录后即不再走这条）；
     * 3. 源版本未知（size <= 0）且无修改时间 → 视为新鲜（沿用旧行为）。
     */
    fun isAudioCoverStale(storageId: Int, filePath: String, length: Long, lastModified: Long): Boolean {
        if (length > 0L) {
            val recorded = readAudioCoverVersion(storageId, filePath)
            if (recorded != null) return recorded != length
        }
        return legacyStaleByMtime(storageId, filePath, lastModified)
    }

    /** 无版本记录时的兜底：源文件修改时间明显晚于缓存写入时间即视为过期。 */
    private fun legacyStaleByMtime(storageId: Int, filePath: String, lastModified: Long): Boolean {
        if (lastModified <= 0L) return false
        val cacheTime = maxOf(
            fileFor(audioDir, storageId, filePath).takeIf { it.isFile }?.lastModified() ?: 0L,
            noCoverMarker(storageId, filePath).takeIf { it.isFile }?.lastModified() ?: 0L,
        )
        if (cacheTime <= 0L) return false
        return lastModified > cacheTime + MTIME_SLACK_MS
    }

    /** 丢弃某音频的本地封面缓存（封面文件 + no_cover 标记 + 版本记录），用于源文件更新后重算。 */
    fun clearAudioCover(storageId: Int, filePath: String) {
        fileFor(audioDir, storageId, filePath).delete()
        noCoverMarker(storageId, filePath).delete()
        audioVersionMarker(storageId, filePath).delete()
    }

    /** 把 [bitmap] 以 JPEG 写入 [target]（自动建父目录 + 缓冲写），返回落盘后的绝对路径。 */
    fun writeJpeg(
        target: File,
        bitmap: Bitmap,
        quality: Int = ThumbnailManager.JPEG_QUALITY,
    ): String {
        target.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(target), BUFFER_SIZE).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return target.absolutePath
    }

    /**
     * 缓存目录容量淘汰：超过 [ThumbnailManager.MAX_CACHE_BYTES] 时按 lastModified 淘汰最旧文件。
     *
     * 节流：同一目录两次扫描间隔不少于 [ThumbnailManager.CACHE_TRIM_INTERVAL_MS]，并用 CAS 保证
     * 同一窗口内只有一个协程真正进入扫描。
     */
    fun trimIfNeeded(dir: File) {
        val holder = lastTrimAt.computeIfAbsent(dir.path) { AtomicLong(0L) }
        val now = System.currentTimeMillis()
        val prev = holder.get()
        if (now - prev < ThumbnailManager.CACHE_TRIM_INTERVAL_MS) return
        if (!holder.compareAndSet(prev, now)) return

        val files = dir.listFiles()?.toMutableList() ?: return
        if (files.isEmpty()) return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= ThumbnailManager.MAX_CACHE_BYTES) return
        files.sortBy { it.lastModified() }
        val iter = files.iterator()
        while (iter.hasNext() && totalSize > ThumbnailManager.MAX_CACHE_BYTES) {
            val f = iter.next()
            val size = f.length()
            if (f.delete()) totalSize -= size
        }
    }

    /** 清空全部缩略图缓存目录并重建。 */
    fun clearAll() {
        listOf(videoDir, audioDir, imageDir).forEach { dir ->
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }

    /**
     * 细粒度清理：仅删除指定文件对应的视频/音频/图片缓存与 no_cover 标记，不影响其他文件。
     */
    fun clearFor(storageId: Int, files: List<StorageFile>) {
        for (file in files) {
            val key = "${md5("$storageId-${file.path}")}.jpg"
            File(videoDir, key).delete()
            File(audioDir, key).delete()
            File(imageDir, key).delete()
            val baseKey = md5("$storageId-${file.path}")
            File(audioDir, "$baseKey.no_cover").delete()
            File(audioDir, "$baseKey.ver").delete()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024

        /** 无版本记录时，「源文件修改时间 vs 缓存写入时间」判定所留的容差（防时钟偏差误判）。 */
        const val MTIME_SLACK_MS = 5 * 60 * 1000L
    }
}
