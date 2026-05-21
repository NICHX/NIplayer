package com.xyoye.player_component.audio.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.xyoye.common_component.extension.toAudioCoverFile
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
        val cachedMetadata = AudioMetadataCache.get(uniqueKey)
        val cachedCoverPath = cachedMetadata?.coverPath ?: ThumbnailMemoryCache.getCoverPath(uniqueKey)

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
            val durationStr = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            val resolvedTitle = title?.takeIf { it.isNotEmpty() } 
                ?: fileName.substringBeforeLast(".").takeIf { it.isNotEmpty() }
                ?: "未知标题"
            val resolvedArtist = artist?.takeIf { it.isNotEmpty() }
                ?: albumArtist?.takeIf { it.isNotEmpty() }
                ?: ""

            val embeddedPicture = mediaRetriever.embeddedPicture
            val localCoverPath = if (embeddedPicture == null || embeddedPicture.isEmpty()) {
                findLocalCoverFile(uri, fileName)
            } else null

            var coverPath: String? = cachedCoverPath ?: localCoverPath
            
            if (coverPath == null && embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                coverPath = saveCoverToFile(context, uniqueKey, embeddedPicture)
            }

            if (coverPath != null) {
                ThumbnailMemoryCache.putCoverPath(uniqueKey, coverPath)
            }

            val metadata = AudioMetadata(
                artist = resolvedArtist,
                title = resolvedTitle,
                duration = duration,
                coverPath = coverPath
            )
            AudioMetadataCache.put(uniqueKey, metadata)
            AudioMetadataCache.saveToDisk(uniqueKey, metadata)

            AudioMetadataResult(
                title = resolvedTitle,
                artist = resolvedArtist,
                duration = duration,
                coverBytes = embeddedPicture,
                coverPath = coverPath
            )
        } catch (e: Exception) {
            DDLog.e("AudioMetadataLoader", "加载音频元数据失败: $uri", e)
            val fallbackTitle = fileName.substringBeforeLast(".").takeIf { it.isNotEmpty() } ?: "未知标题"
            AudioMetadataResult(
                title = cachedMetadata?.title?.takeIf { it.isNotEmpty() } ?: fallbackTitle,
                artist = cachedMetadata?.artist?.takeIf { it.isNotEmpty() },
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
            val coverFile = uniqueKey.toAudioCoverFile()
            if (coverFile == null) {
                val fallbackFile = File(context.filesDir, "audio_covers/$uniqueKey.jpg")
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
        val cleanUri = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri

        if (cleanUri.startsWith("/")) {
            return findLocalCoverInDirectory(cleanUri, fileName)
        }

        if (cleanUri.startsWith("http://") || cleanUri.startsWith("https://")) {
            return findNetworkCoverFile(cleanUri, fileName)
        }

        return null
    }

    private fun findLocalCoverInDirectory(localPath: String, fileName: String): String? {
        try {
            val audioFile = File(localPath)
            val parentDir = audioFile.parentFile ?: return null
            val nameWithoutExt = audioFile.nameWithoutExtension

            return findCoverInDirectory(parentDir, nameWithoutExt)
        } catch (_: Exception) {}
        return null
    }

    private fun findCoverInDirectory(directory: File, nameWithoutExt: String?): String? {
        val coverPatterns = listOfNotNull(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "album.jpg", "album.jpeg", "album.png",
            if (nameWithoutExt != null) "${nameWithoutExt}.jpg" else null,
            if (nameWithoutExt != null) "${nameWithoutExt}.jpeg" else null,
            if (nameWithoutExt != null) "${nameWithoutExt}.png" else null,
            "Cover.jpg", "Cover.jpeg", "Cover.png",
            "Folder.jpg", "Folder.jpeg", "Folder.png"
        )

        for (pattern in coverPatterns) {
            val coverFile = File(directory, pattern)
            if (coverFile.exists() && coverFile.length() > 1024) {
                return coverFile.absolutePath
            }
        }
        return null
    }

    private fun findNetworkCoverFile(uri: String, fileName: String): String? {
        try {
            val parsedUri = Uri.parse(uri)
            val pathSegments = parsedUri.pathSegments ?: return null
            if (pathSegments.isEmpty()) return null

            val fileNameFromUri = pathSegments.last()
            val directoryPath = pathSegments.dropLast(1)

            val nameWithoutExt = fileNameFromUri.substringBeforeLast(".", "")
            val parentPath = directoryPath.joinToString("/")

            val scheme = parsedUri.scheme ?: return null
            val host = parsedUri.host ?: return null
            val port = if (parsedUri.port > 0) ":${parsedUri.port}" else ""

            val parentUrl = "$scheme://$host$port/$parentPath"

            val coverPatterns = listOfNotNull(
                "cover.jpg", "cover.jpeg", "cover.png",
                "folder.jpg", "folder.jpeg", "folder.png",
                "album.jpg", "album.jpeg", "album.png",
                "${nameWithoutExt}.jpg",
                "${nameWithoutExt}.jpeg",
                "${nameWithoutExt}.png",
                "Cover.jpg", "Cover.jpeg", "Cover.png",
                "Folder.jpg", "Folder.jpeg", "Folder.png"
            )

            for (pattern in coverPatterns) {
                val coverUrl = "$parentUrl/$pattern"
                if (isUrlAccessible(coverUrl)) {
                    return coverUrl
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isUrlAccessible(url: String): Boolean {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    fun getCachedMetadata(uniqueKey: String): AudioMetadata? {
        return AudioMetadataCache.get(uniqueKey)
    }

    fun getCachedCoverPath(uniqueKey: String): String? {
        val metadataCoverPath = AudioMetadataCache.get(uniqueKey)?.coverPath
        if (metadataCoverPath != null) {
            ThumbnailMemoryCache.putCoverPath(uniqueKey, metadataCoverPath)
            return metadataCoverPath
        }
        return ThumbnailMemoryCache.getCoverPath(uniqueKey) ?: run {
            val coverFile = uniqueKey.toAudioCoverFile()
            if (coverFile?.exists() == true) {
                ThumbnailMemoryCache.putCoverPath(uniqueKey, coverFile.absolutePath)
                coverFile.absolutePath
            } else null
        }
    }
}
