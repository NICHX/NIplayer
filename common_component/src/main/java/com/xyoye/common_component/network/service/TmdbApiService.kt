package com.xyoye.common_component.network.service

import com.xyoye.data_component.entity.TmdbMediaDetail
import com.xyoye.data_component.entity.TmdbSearchResponse
import com.xyoye.data_component.entity.TmdbSeasonDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("primary_release_year") year: String? = null,
        @Query("language") language: String = "zh-CN",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("first_air_date_year") year: String? = null,
        @Query("language") language: String = "zh-CN",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "zh-CN",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetail(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String = "zh-CN",
        @Query("api_key") apiKey: String
    ): TmdbSeasonDetail

    @GET("tv/{tv_id}")
    suspend fun getTvDetail(
        @Path("tv_id") tvId: Int,
        @Query("language") language: String = "zh-CN",
        @Query("api_key") apiKey: String
    ): TmdbMediaDetail

    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Int,
        @Query("language") language: String = "zh-CN",
        @Query("api_key") apiKey: String
    ): TmdbMediaDetail
}
