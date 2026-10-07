package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nichx.niplayer.database.enums.MediaType
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * 播放历史表。
 *
 * `is_last_play` 字段不持久化，仅在内存中标记最后一次播放项。
 */
@Entity(
    tableName = "play_history",
    indices = [Index(value = ["unique_key", "storage_id"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "video_name")
    val videoName: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,

    @ColumnInfo(name = "video_position")
    var videoPosition: Long = 0,

    @ColumnInfo(name = "video_duration")
    var videoDuration: Long = 0,

    @ColumnInfo(name = "play_time")
    var playTime: Date = Date(),

    @ColumnInfo(name = "subtitle_path")
    var subtitlePath: String? = null,

    @ColumnInfo(name = "torrent_path")
    var torrentPath: String? = null,

    @ColumnInfo(name = "torrent_index")
    var torrentIndex: Int = -1,

    @ColumnInfo(name = "http_header")
    var httpHeader: String? = null,

    @ColumnInfo(name = "unique_key")
    var uniqueKey: String = "",

    @ColumnInfo(name = "storage_path")
    var storagePath: String? = null,

    @ColumnInfo(name = "storage_id")
    var storageId: Int? = null,

    /** 来源歌单 ID（可空）。从歌单播放时记录，恢复播放时据此还原歌单播放列表。 */
    @ColumnInfo(name = "playlist_id")
    var playlistId: Int? = null,

    @ColumnInfo(name = "audio_path")
    var audioPath: String? = null,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
) {
    @androidx.room.Ignore
    var isLastPlay: Boolean = false
}

/** 距结尾小于该值时视为已播完（毫秒），续播时从头开始。 */
private const val COMPLETION_MARGIN_MS = 2_000L

/**
 * 音频记录/续播播放进度的最小总时长（毫秒）。
 *
 * 普通歌曲（典型 3~5 分钟）短于该阈值，一律不记录也不续播，避免切歌时从中间开始；
 * 有声书/播客等长音频达到该阈值即正常断点续播。
 */
const val AUDIO_PROGRESS_MIN_DURATION_MS = 5 * 60 * 1000L

/**
 * 计算恢复播放的起始位置。
 *
 * 曲目播放完成时进度会记录为接近时长（100%），若直接用它续播会立即 seek 到结尾并再次结束；
 * 因此已播完（进度 ≥ 时长 - [COMPLETION_MARGIN_MS]）的曲目应从头播放。
 * 时长未知（0）时不做归一化，保持原始进度。
 */
fun PlayHistoryEntity.resumeStartPositionMs(): Long {
    val duration = videoDuration
    return if (duration > 0 && videoPosition >= duration - COMPLETION_MARGIN_MS) 0L else videoPosition
}

/**
 * 计算恢复播放的起始位置（带音频策略）。
 *
 * 视频始终按 [resumeStartPositionMs] 续播；音频仅在 [rememberAudioProgress] 开启、
 * 且总时长不小于 [audioMinDurationMs] 时才续播，否则一律从头播放，
 * 避免普通歌曲切歌时从中间开始。
 *
 * @param isAudio 是否为音频
 * @param rememberAudioProgress 音频进度记录/续播总开关（对应设置项）
 * @param audioMinDurationMs 音频进度门槛（毫秒），默认 [AUDIO_PROGRESS_MIN_DURATION_MS]，可由用户自定义
 */
fun PlayHistoryEntity.resumeStartPositionMs(
    isAudio: Boolean,
    rememberAudioProgress: Boolean = true,
    audioMinDurationMs: Long = AUDIO_PROGRESS_MIN_DURATION_MS,
): Long {
    if (isAudio && (!rememberAudioProgress || videoDuration < audioMinDurationMs)) {
        return 0L
    }
    return resumeStartPositionMs()
}
