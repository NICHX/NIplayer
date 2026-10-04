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
 * 统一管理四个缓存目录（`video_cover` / `audio_cover` / `image_thumb` / `seek_preview`）、
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

    /** 该音频是否已确认无内嵌封面（避免反复扫描）。 */
    fun hasNoCover(storageId: Int, filePath: String): Boolean = noCoverMarker(storageId, filePath).exists()

    /** 标记该音频无内嵌封面。 */
    fun markNoCover(storageId: Int, filePath: String) {
        try {
            noCoverMarker(storageId, filePath).createNewFile()
        } catch (_: Exception) {
        }
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
        }
    }

    /** 删除指定音频的封面缓存与全部标记。 */
    fun clearAudioCover(storageId: Int, filePath: String) {
        val baseKey = md5("$storageId-$filePath")
        listOf(
            "$baseKey.jpg",
            "$baseKey.no_cover",
            "$baseKey.no_cover_api",
            "$baseKey.api_cover",
            "$baseKey.tmp.jpg",
        ).forEach { suffix ->
            runCatching { File(audioDir, suffix).delete() }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
