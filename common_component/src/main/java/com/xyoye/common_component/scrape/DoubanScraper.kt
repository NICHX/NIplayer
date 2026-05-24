package com.xyoye.common_component.scrape

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DoubanScrapeResult(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Float?,
    val genres: List<String>,
    val isMovie: Boolean,
    val overview: String?,
    val directors: List<String>,
    val casts: List<String>
)

class DoubanScraper {

    companion object {
        private const val TAG = "DoubanScraper"
        private const val SEARCH_URL = "https://movie.douban.com/j/subject_suggest"
        private const val FRODO_API_BASE = "https://frodo.douban.com/api/v2"
        private const val FRODO_API_KEY = "0ab215a8b19779392a2a746f153f51e3"

        private val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val FRODO_UA = "api-client/1 com.douban.frodo/6.42.2(194)"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(title: String, year: Int? = null): DoubanScrapeResult? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode(title, "UTF-8")
            val url = "$SEARCH_URL?q=$query"
            Log.d(TAG, "Searching: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "https://movie.douban.com/")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for search: $title")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) {
                Log.d(TAG, "No search results for: $title")
                return@withContext null
            }

            var bestMatch: JSONObject? = null
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val itemYear = item.optString("year", "").toIntOrNull()

                if (year != null && itemYear == year) {
                    bestMatch = item
                    break
                }
                if (bestMatch == null) {
                    bestMatch = item
                }
            }

            bestMatch?.let { parseSearchItem(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for: $title", e)
            null
        }
    }

    suspend fun getDetail(doubanId: String): DoubanScrapeResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://movie.douban.com/subject/$doubanId/"
            Log.d(TAG, "Fetching detail: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "https://www.douban.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for detail: $doubanId")
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            parseDetailHtml(doubanId, html)
        } catch (e: Exception) {
            Log.e(TAG, "Detail fetch failed for: $doubanId", e)
            null
        }
    }

    suspend fun searchWithDetail(title: String, year: Int? = null): DoubanScrapeResult? {
        val searchResult = search(title, year) ?: return null
        if (searchResult.overview == null || searchResult.genres.isEmpty()) {
            val detail = getDetail(searchResult.id)
            if (detail != null) {
                return searchResult.copy(
                    overview = detail.overview ?: searchResult.overview,
                    genres = if (detail.genres.isNotEmpty()) detail.genres else searchResult.genres,
                    directors = detail.directors,
                    casts = detail.casts,
                    backdropUrl = detail.backdropUrl ?: searchResult.backdropUrl,
                    rating = detail.rating ?: searchResult.rating,
                    originalTitle = detail.originalTitle ?: searchResult.originalTitle
                )
            }
        }
        return searchResult
    }

    private fun parseSearchItem(json: JSONObject): DoubanScrapeResult {
        val type = json.optString("type", "movie")
        val isMovie = type == "movie"

        return DoubanScrapeResult(
            id = json.optString("id", ""),
            title = json.optString("title", ""),
            originalTitle = json.optString("original_title", null),
            posterUrl = json.optString("img", null),
            backdropUrl = null,
            year = json.optString("year", "").toIntOrNull(),
            rating = json.optString("rating", "").toFloatOrNull(),
            genres = emptyList(),
            isMovie = isMovie,
            overview = null,
            directors = emptyList(),
            casts = emptyList()
        )
    }

    private fun parseDetailHtml(doubanId: String, html: String): DoubanScrapeResult? {
        val jsonLdPattern = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE
        )
        val jsonLdMatch = jsonLdPattern.find(html)

        if (jsonLdMatch == null) {
            Log.w(TAG, "No JSON-LD found for: $doubanId")
            return null
        }

        return try {
            val jsonObj = JSONObject(jsonLdMatch.groupValues[1])

            val title = jsonObj.optString("name", "")
            val directors = mutableListOf<String>()
            val casts = mutableListOf<String>()

            val directorArr = jsonObj.optJSONArray("director")
            if (directorArr != null) {
                for (i in 0 until minOf(directorArr.length(), 5)) {
                    val name = directorArr.getJSONObject(i)
                        .optString("name", "")
                        .split(" ").first()
                        .trim()
                    if (name.isNotBlank()) directors.add(name)
                }
            }

            val actorArr = jsonObj.optJSONArray("actor")
            if (actorArr != null) {
                for (i in 0 until minOf(actorArr.length(), 10)) {
                    val name = actorArr.getJSONObject(i)
                        .optString("name", "")
                        .split(" ").first()
                        .trim()
                    if (name.isNotBlank()) casts.add(name)
                }
            }

            val ratingStr = jsonObj.optString("rating", null)
                ?: Regex("""[<]span[^>]*class=["']rating_num["'][^>]*>([\d.]+)[<]""")
                    .find(html)?.groupValues?.getOrNull(1)
            val rating = ratingStr?.toFloatOrNull()

            val genres = mutableListOf<String>()
            val genreStr = jsonObj.optString("genre", null)
            if (genreStr != null) {
                if (genreStr.startsWith("[")) {
                    try {
                        val genreArr = JSONArray(genreStr)
                        for (i in 0 until genreArr.length()) {
                            genres.add(genreArr.getString(i))
                        }
                    } catch (_: Exception) {
                        genres.add(genreStr.trim('[', ']', '"'))
                    }
                } else {
                    genres.addAll(genreStr.split(",").map { it.trim() })
                }
            }

            val description = jsonObj.optString("description", null)
                ?: Regex("""[<]span[^>]*property=["']v:summary["'][^>]*>([\s\S]*?)[<]/span>""")
                    .find(html)?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.replace(Regex("<[^>]*>"), "")
                    ?.trim()

            DoubanScrapeResult(
                id = doubanId,
                title = title,
                originalTitle = jsonObj.optString("alternateName", null),
                posterUrl = jsonObj.optString("image", null),
                backdropUrl = null,
                year = jsonObj.optString("datePublished", "")
                    .take(4).toIntOrNull(),
                rating = rating,
                genres = genres,
                isMovie = jsonObj.optString("@type", "Movie") == "Movie",
                overview = description,
                directors = directors,
                casts = casts
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse detail HTML for: $doubanId", e)
            null
        }
    }
}