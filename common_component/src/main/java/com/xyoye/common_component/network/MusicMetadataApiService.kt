package com.xyoye.common_component.network

import com.xyoye.common_component.config.MusicMetadataConfig
import com.xyoye.common_component.utils.DDLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicMetadataApiService {

    suspend fun fetchLyrics(
        title: String,
        artist: String = "",
        album: String = "",
        path: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiUrl = MusicMetadataConfig.getApiUrl().orEmpty()
        val apiAuth = MusicMetadataConfig.getApiAuth().orEmpty()

        if (apiUrl.isEmpty()) {
            return@withContext Result.failure(Exception("API地址未设置"))
        }

        try {
            val params = mutableListOf<String>()
            
            if (title.isNotEmpty()) {
                params.add("title=${URLEncoder.encode(title, "UTF-8")}")
            }
            if (artist.isNotEmpty()) {
                params.add("artist=${URLEncoder.encode(artist, "UTF-8")}")
            }
            if (album.isNotEmpty() && album != "[Unknown Album]") {
                params.add("album=${URLEncoder.encode(album, "UTF-8")}")
            }
            if (path.isNotEmpty()) {
                params.add("path=${URLEncoder.encode(path, "UTF-8")}")
            }
            
            val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
            val url = "$apiUrl/lyrics$queryString"

            DDLog.i("MusicMetadataApi", "请求歌词: $url")
            DDLog.i("MusicMetadataApi", "请求参数: title=$title, artist=$artist, album=$album")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "text/html, application/json, text/plain, */*")
            connection.instanceFollowRedirects = true

            if (apiAuth.isNotEmpty()) {
                connection.setRequestProperty("Authorization", apiAuth)
                connection.setRequestProperty("Authentication", apiAuth)
                DDLog.i("MusicMetadataApi", "使用Authorization header")
            }

            val responseCode = connection.responseCode
            val contentType = connection.contentType ?: ""

            DDLog.i("MusicMetadataApi", "响应码: $responseCode, Content-Type: $contentType")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = reader.readText()
                reader.close()
                connection.disconnect()

                if (response.isNotEmpty()) {
                    DDLog.i("MusicMetadataApi", "歌词获取成功，长度: ${response.length} 字符")
                    Result.success(response)
                } else {
                    DDLog.w("MusicMetadataApi", "歌词为空")
                    Result.failure(Exception("歌词为空"))
                }
            } else {
                val errorMessage = "HTTP $responseCode"
                DDLog.e("MusicMetadataApi", "歌词请求失败: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            DDLog.e("MusicMetadataApi", "歌词请求异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCover(
        title: String,
        artist: String = "",
        album: String = ""
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val apiUrl = MusicMetadataConfig.getApiUrl().orEmpty()
        val apiAuth = MusicMetadataConfig.getApiAuth().orEmpty()

        if (apiUrl.isEmpty()) {
            return@withContext Result.failure(Exception("API地址未设置"))
        }

        try {
            val params = mutableListOf<String>()
            
            if (title.isNotEmpty()) {
                params.add("title=${URLEncoder.encode(title, "UTF-8")}")
            }
            if (artist.isNotEmpty()) {
                params.add("artist=${URLEncoder.encode(artist, "UTF-8")}")
            }
            if (album.isNotEmpty() && album != "[Unknown Album]") {
                params.add("album=${URLEncoder.encode(album, "UTF-8")}")
            }
            
            val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
            val url = "$apiUrl/cover$queryString"

            DDLog.i("MusicMetadataApi", "请求封面: $url")
            DDLog.i("MusicMetadataApi", "请求参数: title=$title, artist=$artist, album=$album")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "image/*")
            connection.instanceFollowRedirects = true

            if (apiAuth.isNotEmpty()) {
                connection.setRequestProperty("Authorization", apiAuth)
                connection.setRequestProperty("Authentication", apiAuth)
                DDLog.i("MusicMetadataApi", "使用Authorization header")
            }

            val responseCode = connection.responseCode
            val contentType = connection.contentType ?: ""

            DDLog.i("MusicMetadataApi", "响应码: $responseCode, Content-Type: $contentType")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val imageBytes = inputStream.readBytes()
                inputStream.close()
                connection.disconnect()

                if (imageBytes.isNotEmpty()) {
                    DDLog.i("MusicMetadataApi", "封面获取成功，大小: ${imageBytes.size} bytes")
                    Result.success(imageBytes)
                } else {
                    DDLog.w("MusicMetadataApi", "封面为空")
                    Result.failure(Exception("封面为空"))
                }
            } else {
                val errorMessage = "HTTP $responseCode"
                DDLog.e("MusicMetadataApi", "封面请求失败: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            DDLog.e("MusicMetadataApi", "封面请求异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isApiConfigured(): Boolean {
        val apiUrl = MusicMetadataConfig.getApiUrl().orEmpty()
        return apiUrl.isNotEmpty()
    }
}
