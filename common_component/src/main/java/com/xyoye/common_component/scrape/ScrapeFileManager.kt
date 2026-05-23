package com.xyoye.common_component.scrape

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
        folderPath: String,
        nfoContent: String,
        mediaType: String
    ): Boolean = withContext(Dispatchers.IO) {
        val nfoFileName = if (mediaType == "movie") "movie.nfo" else "tvshow.nfo"
        val nfoPath = normalizePath(folderPath, nfoFileName)
        storage.saveFile(nfoPath, nfoContent.toByteArray(Charsets.UTF_8))
    }

    suspend fun savePoster(
        storage: Storage,
        folderPath: String,
        posterPath: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (posterPath == null) return@withContext false

        val imageUrl = TmdbRepository().buildImageUrl(posterPath, "w780") ?: return@withContext false
        val imageData = downloadImage(imageUrl) ?: return@withContext false

        val posterFilePath = normalizePath(folderPath, "poster.jpg")
        val folderFilePath = normalizePath(folderPath, "folder.jpg")

        val result1 = storage.saveFile(posterFilePath, imageData)
        val result2 = storage.saveFile(folderFilePath, imageData)

        result1 && result2
    }

    suspend fun saveBackdrop(
        storage: Storage,
        folderPath: String,
        backdropPath: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (backdropPath == null) return@withContext false

        val imageUrl = TmdbRepository().buildImageUrl(backdropPath, "w1280") ?: return@withContext false
        val imageData = downloadImage(imageUrl) ?: return@withContext false

        val filePath = normalizePath(folderPath, "fanart.jpg")
        storage.saveFile(filePath, imageData)
    }

    suspend fun readNfo(
        storage: Storage,
        folderPath: String,
        mediaType: String
    ): String? = withContext(Dispatchers.IO) {
        val nfoFileName = if (mediaType == "movie") "movie.nfo" else "tvshow.nfo"
        val nfoPath = normalizePath(folderPath, nfoFileName)

        if (!storage.fileExists(nfoPath)) return@withContext null

        try {
            val nfoFile = storage.pathFile(nfoPath, false) ?: return@withContext null
            val inputStream = storage.openFile(nfoFile) ?: return@withContext null
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            null
        }
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
            null
        }
    }

    private fun normalizePath(folderPath: String, fileName: String): String {
        val normalized = folderPath.trimEnd('/')
        return "$normalized/$fileName"
    }
}
