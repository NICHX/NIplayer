package com.xyoye.data_component.entity

data class TmdbSearchResponse(
    val page: Int = 0,
    val results: List<TmdbSearchItem> = emptyList(),
    val total_results: Int = 0,
    val total_pages: Int = 0
)

data class TmdbSearchItem(
    val id: Int = 0,
    val name: String? = null,
    val title: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0,
    val genre_ids: List<Int> = emptyList(),
    val overview: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val media_type: String? = null
)

data class TmdbSeasonDetail(
    val id: Int = 0,
    val name: String = "",
    val poster_path: String? = null,
    val air_date: String? = null,
    val episodes: List<TmdbEpisode> = emptyList()
)

data class TmdbEpisode(
    val id: Int = 0,
    val name: String = "",
    val episode_number: Int = 0,
    val still_path: String? = null,
    val overview: String? = null
)

data class TmdbMediaDetail(
    val id: Int = 0,
    val name: String? = null,
    val title: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0,
    val genres: List<TmdbGenre> = emptyList(),
    val overview: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val number_of_seasons: Int = 1,
    val number_of_episodes: Int = 0
)

data class TmdbGenre(
    val id: Int = 0,
    val name: String = ""
)
