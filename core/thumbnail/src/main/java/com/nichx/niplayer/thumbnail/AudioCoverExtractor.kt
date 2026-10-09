package com.nichx.niplayer.thumbnail

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.CancellationException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 音频封面提取：内嵌封面 / 同级目录封面 / 远程头部扫描。
 *
 * 把旧 [ThumbnailManager] 中 7 个音频封面相关私有方法收拢到一处；命中后写入调用方给定的
 * [cacheFile]，无封面时标记 `no_cover` 以避免重复扫描。
 */
internal class AudioCoverExtractor(
    private val context: Context,
    private val store: ThumbnailStore,
) {

    /**
     * 提取 [file] 的封面并写入 [cacheFile]。
     *
     * @return true 表示成功写入；false 表示无封面（已标记 no_cover）
     */
    suspend fun extract(storage: Storage, storageId: Int, file: StorageFile, cacheFile: File): Boolean {
        val url = storage.createPlayUrl(file)

        // 1. 本地文件：内嵌封面
        if (url != null && (url.startsWith("file") || url.startsWith("content"))) {
            if (extractEmbeddedFromUrl(context, url, cacheFile)) return true
        }

        // 2. 同级目录 cover.jpg / folder.jpg 等常见封面（目录级结果按目录缓存）
        if (findDirectoryCover(storage, file, cacheFile)) return true

        // 3. 头部读取内嵌封面（远程文件）
        val viaHeader = if (url != null && url.startsWith("http", ignoreCase = true)) {
            val headers = storage.getPlayHeaders()
            if (headers.isNotEmpty()) extractEmbeddedFromHeader(storage, file, cacheFile) else CoverOutcome.ABSENT
        } else {
            extractEmbeddedFromHeader(storage, file, cacheFile)
        }
        when (viaHeader) {
            CoverOutcome.FOUND -> return true
            // 读取过程出错（网络/IO）：不落 no_cover，下次浏览可重试
            CoverOutcome.FAILED -> return false
            CoverOutcome.ABSENT -> {}
        }

        // 4. 确认无封面 → 标记 no_cover，避免下次重复扫描
        store.markNoCover(storageId, file.path)
        return false
    }

    /** 清空目录级封面探测缓存（清空缩略图缓存时调用）。 */
    fun clearDirCoverCache() {
        dirCoverCache.clear()
    }

    private suspend fun extractEmbeddedFromHeader(storage: Storage, file: StorageFile, cacheFile: File): CoverOutcome {
        val first = readHeader(storage, file, HEADER_READ_LIMIT) ?: return CoverOutcome.FAILED
        if (first.isEmpty()) return CoverOutcome.ABSENT

        // 1) FLAC：自行解析 PICTURE 块。
        //    部分平台（如 MIUI）的 MediaMetadataRetriever 对 FLAC 内嵌封面直接失败
        //    （日志 getEmbeddedPicture failed），因此不能依赖平台 extractor。
        if (isFlac(first)) return extractFlacCover(first, storage, file, cacheFile)

        // 2) 其它格式：交给平台 retriever
        val firstOutcome = extractEmbeddedFromDataSource(byteArrayDataSource(first), cacheFile)
        if (firstOutcome == CoverOutcome.FOUND) return CoverOutcome.FOUND

        // 3) ID3v2 标签可能超出 2MB：按 syncsafe 长度补读一次
        val required = requiredId3Bytes(first) ?: return firstOutcome
        if (required <= first.size || required > MAX_METADATA_BYTES) return firstOutcome

        val bigger = readHeader(storage, file, required.toInt().coerceAtMost(MAX_METADATA_BYTES))
            ?: return CoverOutcome.FAILED
        return extractEmbeddedFromDataSource(byteArrayDataSource(bigger), cacheFile)
    }

    private fun isFlac(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()

    /**
     * FLAC 封面提取：扫描元数据块定位 PICTURE，取出图片字节直接解码落盘。
     *
     * 按需补读：首块字节不足包含完整 PICTURE 时，按块末尾偏移再读一次（最多 [MAX_SCAN_ROUNDS] 轮）。
     */
    private suspend fun extractFlacCover(
        first: ByteArray,
        storage: Storage,
        file: StorageFile,
        cacheFile: File,
    ): CoverOutcome {
        var bytes = first
        var rounds = 0
        while (rounds++ < MAX_SCAN_ROUNDS) {
            when (val scan = scanFlacMetadata(bytes)) {
                is FlacScan.Picture -> {
                    val picture = flacPictureBytes(bytes, scan.start, scan.end)
                    return if (picture != null) decodeAndWrite(picture, cacheFile) else CoverOutcome.FAILED
                }

                FlacScan.NoPicture -> return CoverOutcome.ABSENT

                is FlacScan.NeedMore -> {
                    val target = scan.targetBytes
                    if (target <= bytes.size || target > MAX_METADATA_BYTES) return CoverOutcome.FAILED
                    bytes = readHeader(storage, file, target) ?: return CoverOutcome.FAILED
                }
            }
        }
        return CoverOutcome.FAILED
    }

    /** 把图片字节解码、缩放并写入缓存（按目标宽度降采样解码，避免大图 OOM）。 */
    private fun decodeAndWrite(picture: ByteArray, cacheFile: File): CoverOutcome {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, ThumbnailManager.MAX_WIDTH)
            }
            val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
                ?: return CoverOutcome.ABSENT
            val scaled = scaleToMaxWidth(bitmap, ThumbnailManager.MAX_WIDTH)
            if (scaled !== bitmap) bitmap.recycle()
            store.writeJpeg(cacheFile, scaled)
            CoverOutcome.FOUND
        } catch (e: Exception) {
            Log.w(TAG, "decodeAndWrite failed: ${e.message}")
            CoverOutcome.FAILED
        }
    }

    /** 以 [maxWidth] 为目标计算 2 的幂降采样比例，避免整幅大图进入内存。 */
    private fun computeInSampleSize(width: Int, height: Int, maxWidth: Int): Int {
        if (width <= 0 || height <= 0 || maxWidth <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxWidth) sample *= 2
        return sample
    }

    private suspend fun readHeader(storage: Storage, file: StorageFile, maxBytes: Int): ByteArray? = try {
        storage.readFileBytes(file, maxBytes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "header read failed (maxBytes=$maxBytes): ${e.message}")
        null
    }

    private fun byteArrayDataSource(bytes: ByteArray): MediaDataSource = object : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size, bytes.size - position.toInt())
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() {}
    }

    /**
     * ID3v2（MP3 等）：按 syncsafe 长度推算"包含完整标签（含 APIC 封面）所需字节数"。
     * 非 ID3 或长度非法返回 null。
     */
    private fun requiredId3Bytes(bytes: ByteArray): Long? {
        if (bytes.size < 3) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }
        return id3TagEndOffset(bytes)
    }

    private suspend fun findDirectoryCover(storage: Storage, file: StorageFile, cacheFile: File): Boolean {
        val dirPath = file.path.substringBeforeLast('/', "")
        // 目录级候选（cover/folder/album）与文件名无关，按目录缓存，避免同目录每个音频重复探测
        val dirCoverPath = resolveDirLevelCover(storage, dirPath)
        if (dirCoverPath != null && copyCoverToCache(storage, dirCoverPath, cacheFile)) return true

        // 文件级候选：{文件名去扩展名}.jpg/.jpeg/.png（逐文件探测）
        //
        // 必须先 fileExists 再打开：SMB 下 jcifs 以「只读」模式打开不存在的路径时会带上
        // O_CREAT（SmbRandomAccessFile 的 "r" 模式），直接在服务器上生成 0 字节幽灵文件。
        // 目录级候选走 resolveDirLevelCover 已自带存在性判断，此处是此前遗漏的一处。
        val nameWithoutExt = file.name.substringBeforeLast(".")
        for (ext in COVER_EXTS) {
            val candidate = "$nameWithoutExt$ext"
            val coverPath = if (dirPath.isEmpty()) candidate else "$dirPath/$candidate"
            val exists = try {
                storage.fileExists(coverPath)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (exists && copyCoverToCache(storage, coverPath, cacheFile)) return true
        }
        return false
    }

    private suspend fun resolveDirLevelCover(storage: Storage, dirPath: String): String? {
        val now = System.currentTimeMillis()
        val cacheKey = "${storage.library.id}/$dirPath"
        dirCoverCache[cacheKey]?.let { entry ->
            if (entry.expireAt > now) return entry.path
        }
        var found: String? = null
        outer@ for (base in DIR_COVER_BASES) {
            for (ext in COVER_EXTS) {
                val candidate = "$base$ext"
                val coverPath = if (dirPath.isEmpty()) candidate else "$dirPath/$candidate"
                val exists = try {
                    storage.fileExists(coverPath)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (exists) {
                    found = coverPath
                    break@outer
                }
            }
        }
        dirCoverCache[cacheKey] = DirCoverEntry(now + DIR_COVER_TTL_MS, found)
        return found
    }

    private suspend fun copyCoverToCache(storage: Storage, coverPath: String, cacheFile: File): Boolean {
        return try {
            val coverFile = object : AbstractStorageFile(
                path = coverPath,
                name = coverPath.substringAfterLast('/'),
                isDirectory = false,
            ) {}
            val input = storage.openInputStream(coverFile)
            cacheFile.parentFile?.mkdirs()
            input.use { ins ->
                BufferedOutputStream(FileOutputStream(cacheFile), BUFFER_SIZE).use { out ->
                    ins.copyTo(out)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) {
                true
            } else {
                cacheFile.delete()
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cacheFile.delete()
            false
        }
    }

    private fun extractEmbeddedFromUrl(context: Context, url: String, cacheFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(url))
            extractEmbeddedPicture(retriever, cacheFile) == CoverOutcome.FOUND
        } catch (e: Exception) {
            Log.w(TAG, "embedded(url) failed: ${e.message}", e)
            false
        } finally {
            releaseQuietly(retriever)
        }
    }

    private fun extractEmbeddedFromDataSource(dataSource: MediaDataSource, cacheFile: File): CoverOutcome {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            extractEmbeddedPicture(retriever, cacheFile)
        } catch (e: Exception) {
            Log.w(TAG, "embedded(dataSource) failed: ${e.message}", e)
            CoverOutcome.FAILED
        } finally {
            releaseQuietly(retriever)
            try {
                dataSource.close()
            } catch (e: Exception) {
                Log.w(TAG, "dataSource.close() failed: ${e.message}")
            }
        }
    }

    private fun extractEmbeddedPicture(retriever: MediaMetadataRetriever, cacheFile: File): CoverOutcome {
        return try {
            val pictureData = retriever.embeddedPicture ?: return CoverOutcome.ABSENT
            decodeAndWrite(pictureData, cacheFile)
        } catch (e: Exception) {
            Log.w(TAG, "extractEmbeddedPicture failed: ${e.message}")
            CoverOutcome.FAILED
        }
    }

    /** 封面提取结果：命中 / 确认无封面 / 读取失败（可重试）。 */
    private enum class CoverOutcome { FOUND, ABSENT, FAILED }

    private fun releaseQuietly(retriever: MediaMetadataRetriever) {
        try {
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "retriever.release() failed: ${e.message}")
        }
    }

    private data class DirCoverEntry(val expireAt: Long, val path: String?)

    private val dirCoverCache = ConcurrentHashMap<String, DirCoverEntry>()

    private companion object {
        const val TAG = "AudioCoverExtractor"

        /** 头部读取方式提取封面时的最大读取字节数（2MB）。 */
        const val HEADER_READ_LIMIT = 2 * 1024 * 1024

        /** 元数据（含内嵌封面）允许补读的最大字节数，防止异常文件导致超大读取。 */
        const val MAX_METADATA_BYTES = 32 * 1024 * 1024

        /** FLAC 自解析封面时的最大补读轮数。 */
        const val MAX_SCAN_ROUNDS = 4
        const val BUFFER_SIZE = 64 * 1024
        const val DIR_COVER_TTL_MS = 10 * 60 * 1000L
        val DIR_COVER_BASES = listOf("cover", "folder", "album")
        val COVER_EXTS = listOf(".jpg", ".jpeg", ".png")
    }
}

/** FLAC 元数据扫描结果。 */
internal sealed interface FlacScan {
    /** 找到 PICTURE 块，其数据区间为 `[start, end)`。 */
    data class Picture(val start: Int, val end: Int) : FlacScan

    /** 元数据已扫描完且无 PICTURE。 */
    object NoPicture : FlacScan

    /** 需要读取更多字节才能判定；[targetBytes] 为所需总字节数。 */
    data class NeedMore(val targetBytes: Int) : FlacScan
}

/**
 * FLAC：扫描元数据块定位 PICTURE（类型 6）。
 *
 * 块头固定 4 字节：1 字节（最后块标志 `<<7` | 类型）+ 3 字节大端长度。
 * 仅依据**已读字节**判断，不依赖整个文件。
 */
internal fun scanFlacMetadata(bytes: ByteArray): FlacScan {
    if (bytes.size < 4) return FlacScan.NeedMore(bytes.size + 64)
    var offset = 4
    while (offset + 4 <= bytes.size) {
        val b0 = bytes[offset].toInt() and 0xFF
        val last = (b0 and 0x80) != 0
        val type = b0 and 0x7F
        val len = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        val dataStart = offset + 4
        val dataEnd = dataStart + len
        if (type == 6) {
            return if (dataEnd <= bytes.size) FlacScan.Picture(dataStart, dataEnd) else FlacScan.NeedMore(dataEnd)
        }
        if (last) return FlacScan.NoPicture
        if (dataEnd > bytes.size) return FlacScan.NeedMore(dataEnd)
        offset = dataEnd
    }
    // 需要更多字节才能读到下一个块头
    return FlacScan.NeedMore(bytes.size + 64)
}

/**
 * 从 FLAC PICTURE 块数据区间 `[start, end)` 中取出图片字节。
 *
 * 结构：4B 类型 + 4B mime 长度 + mime + 4B 描述长度 + 描述 +
 * 4B 宽 + 4B 高 + 4B 位深 + 4B 颜色数 + 4B 数据长度 + 图片数据。越界 / 非法返回 null。
 */
internal fun flacPictureBytes(bytes: ByteArray, start: Int, end: Int): ByteArray? {
    var off = start
    fun readInt(): Int? {
        if (off + 4 > end) return null
        val v = ((bytes[off].toInt() and 0xFF) shl 24) or
            ((bytes[off + 1].toInt() and 0xFF) shl 16) or
            ((bytes[off + 2].toInt() and 0xFF) shl 8) or
            (bytes[off + 3].toInt() and 0xFF)
        off += 4
        return v
    }
    readInt() ?: return null // 图片类型
    val mimeLen = readInt() ?: return null
    if (mimeLen < 0 || off + mimeLen > end) return null
    off += mimeLen
    val descLen = readInt() ?: return null
    if (descLen < 0 || off + descLen > end) return null
    off += descLen
    repeat(4) { if (readInt() == null) return null } // 宽 / 高 / 位深 / 颜色数
    val dataLen = readInt() ?: return null
    if (dataLen <= 0 || off + dataLen > end) return null
    return bytes.copyOfRange(off, off + dataLen)
}

/** ID3v2：按 syncsafe 长度返回整个标签的末尾偏移（含内嵌 APIC 封面）；非法 / 字节不足返回 null。 */
internal fun id3TagEndOffset(bytes: ByteArray): Long? {
    if (bytes.size < 10) return null
    var size = 0L
    for (i in 6..9) {
        val raw = bytes[i].toInt()
        if ((raw and 0x80) != 0) return null
        size = (size shl 7) or (raw and 0x7F).toLong()
    }
    val footer = if ((bytes[5].toInt() and 0x10) != 0) 10L else 0L
    return 10L + size + footer
}
