package com.nichx.niplayer.feature.player

import com.nichx.niplayer.datastore.LrcApiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicMetadataService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchLyrics(
        title: String,
        artist: String = "",
        album: String = "",
        path: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiUrl = LrcApiSettings.apiUrl
        if (apiUrl.isEmpty()) {
            return@withContext Result.failure(Exception("API地址未设置"))
        }

        try {
            val params = mutableListOf<String>()

            if (title.isNotEmpty()) {
                params.add("title=${java.net.URLEncoder.encode(title, "UTF-8")}")
            }
            if (artist.isNotEmpty()) {
                params.add("artist=${java.net.URLEncoder.encode(artist, "UTF-8")}")
            }
            if (album.isNotEmpty() && album != "[Unknown Album]") {
                params.add("album=${java.net.URLEncoder.encode(album, "UTF-8")}")
            }
            if (path.isNotEmpty()) {
                params.add("path=${java.net.URLEncoder.encode(path, "UTF-8")}")
            }

            val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
            val url = "$apiUrl/lyrics$queryString"

            android.util.Log.i("MusicMetadataService", "请求歌词: $url")
            android.util.Log.i("MusicMetadataService", "请求参数: title=$title, artist=$artist, album=$album")

            val requestBuilder = Request.Builder().url(url).get()
                .header("Accept", "text/html, application/json, text/plain, */*")

            val apiAuth = LrcApiSettings.apiAuth
            if (apiAuth.isNotEmpty()) {
                requestBuilder.header("Authorization", apiAuth)
                requestBuilder.header("Authentication", apiAuth)
                android.util.Log.i("MusicMetadataService", "使用Authorization header")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            android.util.Log.i("MusicMetadataService", "响应码: ${response.code}, Content-Type: ${response.header("Content-Type")}")

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.isNotEmpty()) {
                    android.util.Log.i("MusicMetadataService", "歌词获取成功，长度: ${body.length} 字符")
                    Result.success(body)
                } else {
                    android.util.Log.w("MusicMetadataService", "歌词为空")
                    Result.failure(Exception("歌词为空"))
                }
            } else {
                val errorMessage = "HTTP ${response.code}"
                android.util.Log.e("MusicMetadataService", "歌词请求失败: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicMetadataService", "歌词请求异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCover(
        title: String,
        artist: String = "",
        album: String = "",
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val apiUrl = LrcApiSettings.apiUrl
        if (apiUrl.isEmpty()) {
            return@withContext Result.failure(Exception("API地址未设置"))
        }

        try {
            val params = mutableListOf<String>()

            if (title.isNotEmpty()) {
                params.add("title=${java.net.URLEncoder.encode(title, "UTF-8")}")
            }
            if (artist.isNotEmpty()) {
                params.add("artist=${java.net.URLEncoder.encode(artist, "UTF-8")}")
            }
            if (album.isNotEmpty() && album != "[Unknown Album]") {
                params.add("album=${java.net.URLEncoder.encode(album, "UTF-8")}")
            }

            val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
            val url = "$apiUrl/cover$queryString"

            android.util.Log.i("MusicMetadataService", "请求封面: $url")
            android.util.Log.i("MusicMetadataService", "请求参数: title=$title, artist=$artist, album=$album")

            val requestBuilder = Request.Builder().url(url).get()
                .header("Accept", "image/*")

            val apiAuth = LrcApiSettings.apiAuth
            if (apiAuth.isNotEmpty()) {
                requestBuilder.header("Authorization", apiAuth)
                requestBuilder.header("Authentication", apiAuth)
                android.util.Log.i("MusicMetadataService", "使用Authorization header")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            android.util.Log.i("MusicMetadataService", "响应码: ${response.code}, Content-Type: ${response.header("Content-Type")}")

            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (bytes.isNotEmpty()) {
                    android.util.Log.i("MusicMetadataService", "封面获取成功，大小: ${bytes.size} bytes")
                    Result.success(bytes)
                } else {
                    android.util.Log.w("MusicMetadataService", "封面为空")
                    Result.failure(Exception("封面为空"))
                }
            } else {
                val errorMessage = "HTTP ${response.code}"
                android.util.Log.e("MusicMetadataService", "封面请求失败: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicMetadataService", "封面请求异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isConfigured(): Boolean = LrcApiSettings.isConfigured
}
