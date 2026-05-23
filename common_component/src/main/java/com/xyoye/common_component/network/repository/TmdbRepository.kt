package com.xyoye.common_component.network.repository

import com.xyoye.common_component.network.service.TmdbApiService
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.entity.TmdbMediaDetail
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

    fun buildImageUrl(path: String?, width: String = "w300_and_h450_bestv2"): String? {
        return path?.let { "$TMDB_IMG_DOMAIN/t/p/$width$it" }
    }
}
