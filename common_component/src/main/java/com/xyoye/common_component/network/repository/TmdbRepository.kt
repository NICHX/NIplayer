package com.xyoye.common_component.network.repository

import android.util.Log
import com.xyoye.common_component.network.service.TmdbApiService
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.entity.TmdbMediaDetail
import com.xyoye.data_component.entity.TmdbSearchItem
import com.xyoye.data_component.entity.TmdbSearchResponse
import com.xyoye.data_component.entity.TmdbSeasonDetail
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class TmdbRepository {

    companion object {
        const val TMDB_BASE_URL = "https://api.tmdb.org/3/"
        const val TMDB_IMG_DOMAIN = "https://image.tmdb.org"
        private const val TAG = "TmdbRepository"
    }

    private val tmdbApiService: TmdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(MoshiConverterFactory.create(JsonHelper.MO_SHI))
            .build()
            .create(TmdbApiService::class.java)
    }

    suspend fun search(
        query: String,
        year: String?,
        type: String,
        apiKey: String
    ): TmdbSearchResponse {
        return if (type == "movie") {
            tmdbApiService.searchMovie(query, year, apiKey = apiKey)
        } else {
            tmdbApiService.searchTv(query, year, apiKey = apiKey)
        }
    }

    suspend fun searchMulti(query: String, apiKey: String): TmdbSearchResponse {
        return tmdbApiService.searchMulti(query, apiKey = apiKey)
    }

    suspend fun getSeasonDetail(tvId: Int, seasonNumber: Int, apiKey: String): TmdbSeasonDetail {
        return tmdbApiService.getSeasonDetail(tvId, seasonNumber, apiKey = apiKey)
    }

    suspend fun getMediaDetail(tmdbId: Int, type: String, apiKey: String): TmdbMediaDetail {
        return if (type == "movie") {
            tmdbApiService.getMovieDetail(tmdbId, apiKey = apiKey)
        } else {
            tmdbApiService.getTvDetail(tmdbId, apiKey = apiKey)
        }
    }

    suspend fun searchWithFallback(
        query: String,
        year: String?,
        apiKey: String
    ): Pair<String, TmdbSearchResponse> {
        Log.d(TAG, "searchWithFallback: query=$query, year=$year")
        val cleanQuery = query.trim()

        if (year != null) {
            try {
                val result = tmdbApiService.searchMulti(cleanQuery, "zh-CN", apiKey = apiKey)
                val matched = result.results?.firstOrNull {
                    it.media_type == "movie" || it.media_type == "tv"
                }
                if (matched != null) {
                    Log.d(TAG, "Found result with year: ${matched.title ?: matched.name}")
                    return (matched.media_type ?: "movie") to result
                }
            } catch (_: Exception) { }
        }

        try {
            val result = tmdbApiService.searchMulti(cleanQuery, "zh-CN", apiKey = apiKey)
            val matched = result.results?.firstOrNull {
                it.media_type == "movie" || it.media_type == "tv"
            }
            if (matched != null) {
                Log.d(TAG, "Found result without year: ${matched.title ?: matched.name}")
                return (matched.media_type ?: "movie") to result
            }
        } catch (_: Exception) { }

        val titleWithoutYear = cleanQuery.replace(Regex("""[\(\[（]?(19\d{2}|20\d{2})[\)\]）]?"""), "").trim()
        if (titleWithoutYear.isNotEmpty() && titleWithoutYear != cleanQuery) {
            try {
                val result = tmdbApiService.searchMulti(titleWithoutYear, "zh-CN", apiKey = apiKey)
                val matched = result.results?.firstOrNull {
                    it.media_type == "movie" || it.media_type == "tv"
                }
                if (matched != null) {
                    Log.d(TAG, "Found result with cleaned title: ${matched.title ?: matched.name}")
                    return (matched.media_type ?: "movie") to result
                }
            } catch (_: Exception) { }
        }

        try {
            val result = tmdbApiService.searchMulti(cleanQuery, "en-US", apiKey = apiKey)
            val matched = result.results?.firstOrNull {
                it.media_type == "movie" || it.media_type == "tv"
            }
            if (matched != null) {
                Log.d(TAG, "Found result with en-US: ${matched.title ?: matched.name}")
                return (matched.media_type ?: "movie") to result
            }
        } catch (_: Exception) { }

        return "movie" to TmdbSearchResponse(results = emptyList())
    }

    fun findBestMatch(results: List<TmdbSearchItem>?, query: String, preferredType: String?): TmdbSearchItem? {
        if (results.isNullOrEmpty()) return null

        val candidates = if (preferredType != null) {
            val exact = results.filter { it.media_type == preferredType }
            if (exact.isNotEmpty()) exact else results
        } else {
            results
        }

        return candidates.firstOrNull { item ->
            val name = item.name ?: item.title ?: ""
            name.equals(query, ignoreCase = true)
        } ?: candidates.firstOrNull { item ->
            val name = item.name ?: item.title ?: ""
            name.contains(query, ignoreCase = true) || query.contains(name, ignoreCase = true)
        } ?: candidates.firstOrNull { item ->
            item.media_type == "tv"
        } ?: candidates.firstOrNull { item ->
            item.media_type == "movie"
        } ?: results.firstOrNull()
    }

    fun buildImageUrl(path: String?, width: String = "w300_and_h450_bestv2"): String? {
        return path?.let { "$TMDB_IMG_DOMAIN/t/p/$width$it" }
    }

    suspend fun testConnection(apiKey: String): String {
        return try {
            val response = tmdbApiService.searchMulti("test", "zh-CN", 1, apiKey)
            if (response.results.isNullOrEmpty()) {
                "连接成功，但未找到匹配结果\n（API密钥有效，搜索语法正常）"
            } else {
                "连接成功！\n匹配到 ${response.results.size} 个结果\n示例：${response.results.firstOrNull()?.title ?: response.results.firstOrNull()?.name}"
            }
        } catch (e: java.net.UnknownHostException) {
            "DNS解析失败：无法访问 api.tmdb.org\n请检查网络连接或VPN设置"
        } catch (e: java.net.SocketTimeoutException) {
            "连接超时：10秒内未收到响应\n请检查网络速度或代理配置"
        } catch (e: java.io.IOException) {
            "网络错误：${e.message}\n请检查网络连接"
        } catch (e: Exception) {
            val message = e.message ?: "未知错误"
            when {
                message.contains("401", ignoreCase = true) -> "API密钥验证失败 (401)\n请在TMDB官网重新申请API密钥"
                message.contains("403", ignoreCase = true) -> "API密钥被拒绝 (403)\n请检查密钥是否有效"
                message.contains("404", ignoreCase = true) -> "API端点不存在 (404)\n请联系开发者"
                message.contains("Invalid API key", ignoreCase = true) -> "API密钥无效！\n请在 https://www.themoviedb.org/settings/api 申请正确的v3 API密钥"
                else -> "连接失败：$message"
            }
        }
    }
}
