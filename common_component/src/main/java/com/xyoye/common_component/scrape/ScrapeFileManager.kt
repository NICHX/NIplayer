package com.xyoye.common_component.scrape

import android.util.Log
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ScrapeFileManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun saveNfo(
        storage: Storage,
        entityPath: String,
        movieName: String,
        nfoContent: String,
        mediaType: String
    ): Boolean = withContext(Dispatchers.IO) {
        val nfoFileName = if (mediaType == "movie") {
            "${movieName}.nfo"
        } else {
            "tvshow.nfo"
        }
        val saveDir = extractSaveDirectory(entityPath, mediaType)
        val nfoPath = normalizePath(saveDir, nfoFileName)
        storage.saveFile(nfoPath, nfoContent.toByteArray(Charsets.UTF_8))
    }

    suspend fun savePoster(
        storage: Storage,
        entityPath: String,
        movieName: String,
        posterPath: String?,
        mediaType: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (posterPath == null) return@withContext false

        val imageUrl = TmdbRepository().buildImageUrl(posterPath, "w780") ?: return@withContext false
        val imageData = downloadImage(imageUrl) ?: return@withContext false

        val saveDir = extractSaveDirectory(entityPath, mediaType)
        val posterFileName = if (mediaType == "movie") "${movieName}-poster.jpg" else "poster.jpg"
        val folderFileName = if (mediaType == "movie") "${movieName}-folder.jpg" else "folder.jpg"

        val result1 = storage.saveFile(normalizePath(saveDir, posterFileName), imageData)
        val result2 = storage.saveFile(normalizePath(saveDir, folderFileName), imageData)

        result1 && result2
    }

    suspend fun saveBackdrop(
        storage: Storage,
        entityPath: String,
        movieName: String,
        backdropPath: String?,
        mediaType: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (backdropPath == null) return@withContext false

        val imageUrl = TmdbRepository().buildImageUrl(backdropPath, "w1280") ?: return@withContext false
        val imageData = downloadImage(imageUrl) ?: return@withContext false

        val saveDir = extractSaveDirectory(entityPath, mediaType)
        val fileName = if (mediaType == "movie") "${movieName}-fanart.jpg" else "fanart.jpg"
        storage.saveFile(normalizePath(saveDir, fileName), imageData)
    }

    suspend fun readNfo(
        storage: Storage,
        entityPath: String,
        movieName: String,
        mediaType: String
    ): String? = withContext(Dispatchers.IO) {
        val nfoFileName = if (mediaType == "movie") {
            "${movieName}.nfo"
        } else {
            "tvshow.nfo"
        }
        val saveDir = extractSaveDirectory(entityPath, mediaType)
        val nfoPath = normalizePath(saveDir, nfoFileName)

        if (!storage.fileExists(nfoPath)) return@withContext null

        try {
            val nfoFile = storage.pathFile(nfoPath, false) ?: return@withContext null
            val inputStream = storage.openFile(nfoFile) ?: return@withContext null
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e("ScrapeFileManager", "readNfo failed: $nfoPath", e)
            null
        }
    }

    private fun extractSaveDirectory(entityPath: String, mediaType: String): String {
        val trimmed = entityPath.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        val parentDir = if (lastSlash > 0) trimmed.substring(0, lastSlash) else trimmed
        if (mediaType == "tv") {
            val folderName = trimmed.substringAfterLast('/')
            return if (SeasonExtractor.startsWithSeasonFormat(folderName)) {
                parentDir
            } else {
                val lastDot = folderName.lastIndexOf('.')
                val folderLooksLikeFile = lastDot > 0 && folderName.length - lastDot in 2..5
                if (folderLooksLikeFile) {
                    parentDir
                } else {
                    trimmed
                }
            }
        }
        return parentDir
    }

    private fun downloadImage(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ScrapeFileManager", "downloadImage failed: $url", e)
            null
        }
    }

    private fun normalizePath(folderPath: String, fileName: String): String {
        val normalized = folderPath.trimEnd('/')
        return "$normalized/$fileName"
    }
}
