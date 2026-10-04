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
            if (headers.isNotEmpty()) extractEmbeddedFromHeader(storage, file, cacheFile) else false
        } else {
            extractEmbeddedFromHeader(storage, file, cacheFile)
        }
        if (viaHeader) return true

        // 4. 均失败 → 标记 no_cover，避免下次重复尝试
        store.markNoCover(storageId, file.path)
        return false
    }

    /** 清空目录级封面探测缓存（清空缩略图缓存时调用）。 */
    fun clearDirCoverCache() {
        dirCoverCache.clear()
    }

    private suspend fun extractEmbeddedFromHeader(storage: Storage, file: StorageFile, cacheFile: File): Boolean {
        val headerBytes = try {
            storage.readFileBytes(file, HEADER_READ_LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "header read failed: ${e.message}")
            null
        } ?: return false

        val dataSource = object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position >= headerBytes.size) return -1
                val count = minOf(size, headerBytes.size - position.toInt())
                System.arraycopy(headerBytes, position.toInt(), buffer, offset, count)
                return count
            }

            override fun getSize(): Long = headerBytes.size.toLong()

            override fun close() {}
        }
        return extractEmbeddedFromDataSource(dataSource, cacheFile)
    }

    private suspend fun findDirectoryCover(storage: Storage, file: StorageFile, cacheFile: File): Boolean {
        val dirPath = file.path.substringBeforeLast('/', "")
        // 目录级候选（cover/folder/album）与文件名无关，按目录缓存，避免同目录每个音频重复探测
        val dirCoverPath = resolveDirLevelCover(storage, dirPath)
        if (dirCoverPath != null && copyCoverToCache(storage, dirCoverPath, cacheFile)) return true

        // 文件级候选：{文件名去扩展名}.jpg/.jpeg/.png（逐文件探测）
        val nameWithoutExt = file.name.substringBeforeLast(".")
        for (ext in COVER_EXTS) {
            val candidate = "$nameWithoutExt$ext"
            val coverPath = if (dirPath.isEmpty()) candidate else "$dirPath/$candidate"
            if (copyCoverToCache(storage, coverPath, cacheFile)) return true
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
            extractEmbeddedPicture(retriever, cacheFile)
        } catch (e: Exception) {
            Log.w(TAG, "embedded(url) failed: ${e.message}", e)
            false
        } finally {
            releaseQuietly(retriever)
        }
    }

    private fun extractEmbeddedFromDataSource(dataSource: MediaDataSource, cacheFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            extractEmbeddedPicture(retriever, cacheFile)
        } catch (e: Exception) {
            Log.w(TAG, "embedded(dataSource) failed: ${e.message}", e)
            false
        } finally {
            releaseQuietly(retriever)
            try {
                dataSource.close()
            } catch (e: Exception) {
                Log.w(TAG, "dataSource.close() failed: ${e.message}")
            }
        }
    }

    private fun extractEmbeddedPicture(retriever: MediaMetadataRetriever, cacheFile: File): Boolean {
        return try {
            val pictureData = retriever.embeddedPicture ?: return false
            val bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.size) ?: return false
            val scaled = scaleToMaxWidth(bitmap, ThumbnailManager.MAX_WIDTH)
            store.writeJpeg(cacheFile, scaled)
            true
        } catch (e: Exception) {
            Log.w(TAG, "extractEmbeddedPicture failed: ${e.message}")
            false
        }
    }

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
        const val BUFFER_SIZE = 64 * 1024
        const val DIR_COVER_TTL_MS = 10 * 60 * 1000L
        val DIR_COVER_BASES = listOf("cover", "folder", "album")
        val COVER_EXTS = listOf(".jpg", ".jpeg", ".png")
    }
}
