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

        val firstOutcome = extractEmbeddedFromDataSource(byteArrayDataSource(first), cacheFile)
        if (firstOutcome == CoverOutcome.FOUND) return CoverOutcome.FOUND

        // 首次只读了前 2MB：若内嵌封面（常见于大尺寸 FLAC 封面）跨越 2MB 就会被截断，
        // 需按"包含完整封面所需的字节数"再精确补读一次；能确认无封面则直接返回，避免多余读取。
        val required = requiredMetadataBytes(first) ?: return firstOutcome
        if (required < 0L) return CoverOutcome.ABSENT
        if (required <= first.size || required > MAX_METADATA_BYTES) return firstOutcome

        val bigger = readHeader(storage, file, required.toInt().coerceAtMost(MAX_METADATA_BYTES))
            ?: return CoverOutcome.FAILED
        return extractEmbeddedFromDataSource(byteArrayDataSource(bigger), cacheFile)
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
     * 依据已读头部推算"包含完整内嵌封面所需的最小字节数"。
     *
     * - 返回 > 0：需要的总字节数（含封面块）；
     * - 返回 -1：已能确认元数据扫描完毕且无封面；
     * - 返回 null：无法判定（非已知格式 / 字节不足），调用方维持既有 2MB 行为。
     */
    private fun requiredMetadataBytes(bytes: ByteArray): Long? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte() -> flacRequiredBytes(bytes)

            bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte() -> id3RequiredBytes(bytes)

            else -> null
        }
    }

    /** FLAC：扫描元数据块，遇到 PICTURE 返回其末尾偏移；确认无 PICTURE 返回 -1；不确定返回 null。 */
    private fun flacRequiredBytes(bytes: ByteArray): Long? = flacPictureEndOffset(bytes)

    /** ID3v2：按 syncsafe 长度返回整个标签的末尾偏移（含内嵌 APIC 封面）。 */
    private fun id3RequiredBytes(bytes: ByteArray): Long? = id3TagEndOffset(bytes)

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
            val bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.size) ?: return CoverOutcome.ABSENT
            val scaled = scaleToMaxWidth(bitmap, ThumbnailManager.MAX_WIDTH)
            if (scaled !== bitmap) bitmap.recycle()
            store.writeJpeg(cacheFile, scaled)
            CoverOutcome.FOUND
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
        const val BUFFER_SIZE = 64 * 1024
        const val DIR_COVER_TTL_MS = 10 * 60 * 1000L
        val DIR_COVER_BASES = listOf("cover", "folder", "album")
        val COVER_EXTS = listOf(".jpg", ".jpeg", ".png")
    }
}

/**
 * FLAC：扫描元数据块，遇到 PICTURE（类型 6）返回其末尾偏移；确认扫描完且无 PICTURE 返回 -1；
 * 无法判定（字节不足 / 非法）返回 null。
 *
 * 块头固定 4 字节：1 字节（最后块标志 `<<7` | 类型）+ 3 字节大端长度。
 * 仅依据**已读字节**判断，不依赖整个文件。
 */
internal fun flacPictureEndOffset(bytes: ByteArray): Long? {
    var offset = 4L
    while (offset + 4 <= bytes.size) {
        val i = offset.toInt()
        val b0 = bytes[i].toInt() and 0xFF
        val last = (b0 and 0x80) != 0
        val type = b0 and 0x7F
        val len = ((bytes[i + 1].toInt() and 0xFF) shl 16) or
            ((bytes[i + 2].toInt() and 0xFF) shl 8) or
            (bytes[i + 3].toInt() and 0xFF)
        val blockEnd = offset + 4 + len
        if (type == 6) return blockEnd
        if (last) return -1L
        if (blockEnd > bytes.size) return null
        offset = blockEnd
    }
    return null
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
