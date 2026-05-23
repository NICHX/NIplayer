package com.xyoye.data_component.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scrape_media",
    indices = [Index(value = ["path", "media_type"], unique = true)]
)
data class ScrapeMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "poster")
    val poster: String? = null,

    @ColumnInfo(name = "backdrop")
    val backdrop: String? = null,

    @ColumnInfo(name = "tmdb_id")
    val tmdbId: Int? = null,

    @ColumnInfo(name = "genre_ids")
    val genreIds: String = "[]",

    @ColumnInfo(name = "vote_average")
    val voteAverage: Double = 0.0,

    @ColumnInfo(name = "release_time")
    val releaseTime: String? = null,

    @ColumnInfo(name = "overview")
    val overview: String? = null,

    @ColumnInfo(name = "season")
    val season: String = "1",

    @ColumnInfo(name = "source_json")
    val sourceJson: String = "[]",

    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "play_key")
    val playKey: String? = null
)
