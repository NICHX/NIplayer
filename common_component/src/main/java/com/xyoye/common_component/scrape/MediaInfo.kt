package com.xyoye.common_component.scrape

import com.xyoye.data_component.entity.ScrapeMediaEntity

data class MediaInfo(
    val title: String,
    val displayTitle: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val type: MediaType = MediaType.MOVIE,
    val originalFilename: String = "",
    val tmdbId: String? = null
) {
    val isMovie: Boolean get() = type == MediaType.MOVIE
}

data class ScrapeSeriesCluster(
    val seriesKey: String,
    val items: List<ScrapeMediaEntity>,
    val mediaInfo: MediaInfo
)