package com.xyoye.common_component.scrape

enum class MediaType(val dbValue: String) {
    MOVIE("movie"),
    TV_SHOW("tv"),
    VARIETY("tv"),
    DOCUMENTARY("movie"),
    CONCERT("movie"),
    ANIME("tv"),
    OTHER("movie");

    companion object {
        fun fromDbValue(value: String): MediaType {
            return values().find { it.dbValue == value } ?: OTHER
        }
    }
}