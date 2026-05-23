package com.xyoye.data_component.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tmdb_sync_queue",
    indices = [
        Index(value = ["state"]),
        Index(value = ["task_type"]),
        Index(value = ["update_time"])
    ]
)
data class TmdbSyncQueueEntity(
    @PrimaryKey val key: String,

    @ColumnInfo(name = "tmdb_id")
    val tmdbId: Int,

    @ColumnInfo(name = "task_type")
    val taskType: TaskType,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int? = null,

    @ColumnInfo(name = "priority")
    val priority: Int = 0,

    @ColumnInfo(name = "state")
    val state: State = State.PENDING,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis()
) {
    enum class TaskType {
        MEDIA, SEASON_EPISODES
    }

    enum class State {
        PENDING, RUNNING, DONE, FAILED
    }

    companion object {
        fun mediaKey(tmdbId: Int): String = "media:$tmdbId"
        fun seasonKey(tmdbId: Int, seasonNumber: Int): String = "season:$tmdbId:$seasonNumber"
    }
}