package com.xyoye.player_component.audio.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.utils.AudioMetadata
import com.xyoye.common_component.utils.AudioMetadataCache
import com.xyoye.common_component.utils.DDLog
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

data class AudioMetadataResult(
    val title: String?,
    val artist: String?,
    val duration: Long,
    val coverBytes: ByteArray?,
    val coverPath: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioMetadataResult
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (duration != other.duration) return false
        if (coverBytes != null) {
            if (other.coverBytes == null) return false
            if (!coverBytes.contentEquals(other.coverBytes)) return false
        } else if (other.coverBytes != null) return false
        if (coverPath != other.coverPath) return false
        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + duration.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + (coverPath?.hashCode() ?: 0)
        return result
    }
}

object AudioMetadataLoader {

    suspend fun loadMetadata(
        context: Context,
        uniqueKey: String,
        uri: String,
        fileName: String = ""
    ): AudioMetadataResult = withContext(Dispatchers.IO) {
        val cacheKey = uniqueKey
        
        val cachedMetadata = AudioMetadataCache.get(cacheKey)
        val cachedCoverPath = ThumbnailMemoryCache.getCoverPath(cacheKey)
        
        if (cachedMetadata != null && cachedCoverPath != null) {
            return@withContext AudioMetadataResult(
                title = cachedMetadata.title.takeIf { it.isNotEmpty() },
                artist = cachedMetadata.artist.takeIf { it.isNotEmpty() },
                duration = cachedMetadata.duration,
                coverBytes = null,
                coverPath = cachedCoverPath
            )
        }

        var mediaRetriever: MediaMetadataRetriever? = null
        try {
            mediaRetriever = MediaMetadataRetriever()
            
            val parsedUri = Uri.parse(uri)
            if (parsedUri.scheme == "content") {
                mediaRetriever.setDataSource(context, parsedUri)
            } else {
                val cleanUri = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
                if (cleanUri.startsWith("/")) {
                    mediaRetriever.setDataSource(cleanUri)
                } else {
                    mediaRetriever.setDataSource(uri)
                }
            }

            val title = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val albumArtist = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            val resolvedTitle = title?.takeIf { it.isNotEmpty() } 
                ?: fileName.substringBeforeLast(".").takeIf { it.isNotEmpty() }
                ?: "未知标题"
            val resolvedArtist = artist?.takeIf { it.isNotEmpty() }
                ?: albumArtist?.takeIf { it.isNotEmpty() }
                ?: ""

            val metadata = AudioMetadata(
                artist = resolvedArtist,
                title = resolvedTitle,
                duration = duration
            )
            AudioMetadataCache.put(cacheKey, metadata)
            AudioMetadataCache.saveToDisk(cacheKey, metadata)

            val embeddedPicture = mediaRetriever.embeddedPicture
            var coverPath: String? = cachedCoverPath
            
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                if (coverPath == null) {
                    coverPath = saveCoverToFile(context, cacheKey, embeddedPicture)
                }
                if (coverPath != null) {
                    ThumbnailMemoryCache.putCoverPath(cacheKey, coverPath)
                }
            } else {
                val localCoverPath = findLocalCoverFile(uri, fileName)
                if (localCoverPath != null) {
                    coverPath = localCoverPath
                    ThumbnailMemoryCache.putCoverPath(cacheKey, localCoverPath)
                }
            }

            AudioMetadataResult(
                title = resolvedTitle,
                artist = resolvedArtist,
                duration = duration,
                coverBytes = embeddedPicture,
                coverPath = coverPath
            )
        } catch (e: Exception) {
            DDLog.e("AudioMetadataLoader", "加载音频元数据失败: $uri", e)
            AudioMetadataResult(
                title = fileName.substringBeforeLast(".").takeIf { it.isNotEmpty() } ?: "未知标题",
                artist = null,
                duration = cachedMetadata?.duration ?: 0L,
                coverBytes = null,
                coverPath = cachedCoverPath
            )
        } finally {
            try {
                mediaRetriever?.release()
            } catch (_: Exception) {}
        }
    }

    private fun saveCoverToFile(context: Context, uniqueKey: String, coverBytes: ByteArray): String? {
        return try {
            val coverFile = uniqueKey.toCoverFile()
            if (coverFile == null) {
                val fallbackFile = File(context.filesDir, "covers/$uniqueKey.jpg")
                fallbackFile.parentFile?.mkdirs()
                FileOutputStream(fallbackFile).use { fos ->
                    ByteArrayInputStream(coverBytes).copyTo(fos)
                }
                return fallbackFile.absolutePath
            }
            
            coverFile.parentFile?.mkdirs()
            FileOutputStream(coverFile).use { fos ->
                ByteArrayInputStream(coverBytes).copyTo(fos)
            }
            coverFile.absolutePath
        } catch (e: Exception) {
            DDLog.e("AudioMetadataLoader", "保存封面失败: $uniqueKey", e)
            null
        }
    }

    private fun findLocalCoverFile(uri: String, fileName: String): String? {
        try {
            val cleanUri = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
            if (!cleanUri.startsWith("/")) return null
            
            val audioFile = File(cleanUri)
            val parentDir = audioFile.parentFile ?: return null
            val nameWithoutExt = audioFile.nameWithoutExtension
            
            val coverPatterns = listOf(
                "cover.jpg", "cover.jpeg", "cover.png",
                "folder.jpg", "folder.jpeg", "folder.png",
                "album.jpg", "album.jpeg", "album.png",
                "${nameWithoutExt}.jpg", "${nameWithoutExt}.jpeg", "${nameWithoutExt}.png",
                "Cover.jpg", "Cover.jpeg", "Cover.png",
                "Folder.jpg", "Folder.jpeg", "Folder.png"
            )
            
            for (pattern in coverPatterns) {
                val coverFile = File(parentDir, pattern)
                if (coverFile.exists() && coverFile.length() > 1024) {
                    return coverFile.absolutePath
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getCachedMetadata(uniqueKey: String): AudioMetadata? {
        return AudioMetadataCache.get(uniqueKey)
    }

    fun getCachedCoverPath(uniqueKey: String): String? {
        return ThumbnailMemoryCache.getCoverPath(uniqueKey) ?: run {
            val coverFile = uniqueKey.toCoverFile()
            if (coverFile?.exists() == true) {
                ThumbnailMemoryCache.putCoverPath(uniqueKey, coverFile.absolutePath)
                coverFile.absolutePath
            } else null
        }
    }
}
