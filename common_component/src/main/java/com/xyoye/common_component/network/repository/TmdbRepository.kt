package com.xyoye.common_component.network.repository

import android.util.Log
import com.xyoye.common_component.network.service.TmdbApiService
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.entity.TmdbMediaDetail
import com.xyoye.data_component.entity.TmdbSearchItem
import com.xyoye.data_component.entity.TmdbSearchResponse
import com.xyoye.data_component.entity.TmdbSeasonDetail
import okhttp3.Dns
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class TmdbRepository {

    companion object {
        const val TMDB_BASE_URL = "https://api.tmdb.org/3/"
        const val TMDB_IMG_DOMAIN = "https://image.tmdb.org"
        private const val TAG = "TmdbRepository"
    }

    private val tmdbApiService: TmdbApiService by lazy {
        val tmdbDnsIps = listOf(
            "3.169.231.119",
            "108.156.91.119",
            "108.156.91.128"
        )
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .dns(object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            if (hostname != "api.themoviedb.org") {
                                return Dns.SYSTEM.lookup(hostname)
                            }
                            val systemIps = try {
                                Dns.SYSTEM.lookup(hostname)
                            } catch (e: Exception) {
                                Log.w(TAG, "System DNS failed for $hostname, fallback to hardcoded IPs")
                                emptyList()
                            }
                            val hardcodedIps = tmdbDnsIps.mapNotNull { ip ->
                                runCatching { InetAddress.getByName(ip) }.getOrNull()
                            }
                            val allIps = (systemIps + hardcodedIps).distinct()
                            Log.d(TAG, "DNS $hostname: ${allIps.size} addresses (${systemIps.size} system + ${hardcodedIps.size} hardcoded)")
                            return allIps.ifEmpty { Dns.SYSTEM.lookup(hostname) }
                        }
                    })
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

    private val searchLanguages = listOf("zh-CN", "en-US")

    suspend fun searchWithFallback(
        query: String,
        year: String?,
        apiKey: String,
        preferredType: String? = null
    ): Pair<String, TmdbSearchResponse> {
        Log.d(TAG, "searchWithFallback: query=$query, year=$year, preferredType=$preferredType")
        val cleanQuery = query.trim()

        // 1) 根据 preferredType 直接搜索（movie 或 tv），尝试多种语言
        if (preferredType != null) {
            for (lang in searchLanguages) {
                try {
                    val result = if (preferredType == "movie") {
                        tmdbApiService.searchMovie(cleanQuery, year, lang, apiKey = apiKey)
                    } else {
                        tmdbApiService.searchTv(cleanQuery, year, lang, apiKey = apiKey)
                    }
                    if (result.results?.isNotEmpty() == true) {
                        Log.d(TAG, "Type-specific($preferredType, lang=$lang) found ${result.results.size} results")
                        return preferredType to result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Type-specific($preferredType, lang=$lang) failed: ${e.message}")
                }
            }
        }

        // 2) searchMulti 尝试多种语言
        for (lang in searchLanguages) {
            try {
                val result = tmdbApiService.searchMulti(cleanQuery, lang, apiKey = apiKey)
                val matched = result.results?.firstOrNull {
                    it.media_type == "movie" || it.media_type == "tv"
                }
                if (matched != null) {
                    Log.d(TAG, "Multi-search(lang=$lang) found: ${matched.title ?: matched.name}")
                    return (matched.media_type ?: "movie") to result
                }
                Log.d(TAG, "Multi-search(lang=$lang): no results")
            } catch (e: Exception) {
                Log.w(TAG, "Multi-search(lang=$lang) failed: ${e.message}")
            }
        }

        // 3) 去除年份后再搜（多语言）
        val titleWithoutYear = cleanQuery
            .replace(Regex("""[\(\[（]?(19\d{2}|20\d{2})[\)\]）]?"""), "")
            .trim()
        if (titleWithoutYear.isNotEmpty() && titleWithoutYear != cleanQuery) {
            for (lang in searchLanguages) {
                try {
                    val result = tmdbApiService.searchMulti(titleWithoutYear, lang, apiKey = apiKey)
                    val matched = result.results?.firstOrNull {
                        it.media_type == "movie" || it.media_type == "tv"
                    }
                    if (matched != null) {
                        Log.d(TAG, "No-year search(lang=$lang) found: ${matched.title ?: matched.name}")
                        return (matched.media_type ?: "movie") to result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No-year search(lang=$lang) failed: ${e.message}")
                }
            }
        }

        // 4) 如果标题很长，尝试截取前几个单词（避免过长的文件名导致搜索无结果）
        val words = cleanQuery.split(Regex("""[\s,.\-]+""")).filter { it.length >= 3 }
        if (words.size > 3) {
            val shortQuery = words.take(3).joinToString(" ")
            if (shortQuery.length >= 3) {
                try {
                    val result = tmdbApiService.searchMulti(shortQuery, "en-US", apiKey = apiKey)
                    val matched = result.results?.firstOrNull {
                        it.media_type == "movie" || it.media_type == "tv"
                    }
                    if (matched != null) {
                        Log.d(TAG, "Short query search found: ${matched.title ?: matched.name}")
                        return (matched.media_type ?: "movie") to result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Short query search failed: ${e.message}")
                }
            }
        }

        Log.w(TAG, "All search attempts failed for query=$cleanQuery, year=$year")
        return "movie" to TmdbSearchResponse(results = emptyList())
    }

    fun findBestMatch(results: List<TmdbSearchItem>?, query: String, preferredType: String?): TmdbSearchItem? {
        if (results.isNullOrEmpty()) return null

        // 1) 精确类型 + 精确名称匹配
        if (preferredType != null) {
            val exactTypeExactName = results.firstOrNull { item ->
                item.media_type == preferredType && item.name.equals(query, ignoreCase = true) ||
                    item.media_type == preferredType && (item.title ?: "").equals(query, ignoreCase = true)
            }
            if (exactTypeExactName != null) return exactTypeExactName
        }

        // 2) 任意类型 + 精确名称匹配
        val anyTypeExactName = results.firstOrNull { item ->
            val name = item.name ?: item.title ?: ""
            name.equals(query, ignoreCase = true)
        }
        if (anyTypeExactName != null) return anyTypeExactName

        // 3) 精确类型 + 包含匹配
        if (preferredType != null) {
            val exactTypePartial = results.firstOrNull { item ->
                val name = item.name ?: item.title ?: ""
                item.media_type == preferredType && (name.contains(query, ignoreCase = true) || query.contains(name, ignoreCase = true))
            }
            if (exactTypePartial != null) return exactTypePartial
        }

        // 4) 任意类型 + 包含匹配
        val anyTypePartial = results.firstOrNull { item ->
            val name = item.name ?: item.title ?: ""
            name.contains(query, ignoreCase = true) || query.contains(name, ignoreCase = true)
        }
        if (anyTypePartial != null) return anyTypePartial

        // 5) 过滤出 preferredType 的结果，取第一个
        if (preferredType != null) {
            val typeResults = results.filter { it.media_type == preferredType }
            if (typeResults.isNotEmpty()) {
                Log.d(TAG, "No name match, returning first $preferredType result: ${typeResults.first().title ?: typeResults.first().name}")
                return typeResults.first()
            }
        }

        // 6) 最后手段：返回第一个符合 movie/tv 的结果
        val firstValid = results.firstOrNull { it.media_type == "movie" || it.media_type == "tv" }
        if (firstValid != null) {
            Log.d(TAG, "Last resort: returning first valid result: ${firstValid.title ?: firstValid.name}")
            return firstValid
        }

        return null
    }

    fun buildImageUrl(path: String?, width: String = "w300_and_h450_bestv2"): String? {
        return path?.let {
            if (it.startsWith("http://") || it.startsWith("https://")) {
                it
            } else {
                "$TMDB_IMG_DOMAIN/t/p/$width$it"
            }
        }
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
