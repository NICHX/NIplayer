package com.xyoye.data_component.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episode",
    foreignKeys = [
        ForeignKey(
            entity = ScrapeMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["media_id"]),
        Index(value = ["media_id", "season_number", "episode_number"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "media_id")
    val mediaId: Int,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int = 1,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,

    @ColumnInfo(name = "overview")
    val overview: String? = null,

    @ColumnInfo(name = "still_url")
    val stillUrl: String? = null,

    @ColumnInfo(name = "duration")
    val duration: Long = 0,

    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis()
)