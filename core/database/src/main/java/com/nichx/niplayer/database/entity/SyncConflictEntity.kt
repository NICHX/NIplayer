package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放历史云同步冲突记录。
 *
 * 当同一记录在两台设备上于冲突窗口内（约 10 秒）各自修改（play_time / 播放位置等
 * 合并字段不同）时，LWW 只保留时间戳较新的一方，被覆盖的一方的数据存入本表，
 * 避免静默丢数据，供用户在冲突列表中选择保留哪一版本。
 *
 * 同一 record_key 重复冲突时按 REPLACE 更新（保留最新一次冲突现场）。
 */
@Entity(
    tableName = "sync_conflict",
    indices = [Index(value = ["record_key"], unique = true)]
)
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    /** 记录业务键（storageId + uniqueKey）。 */
    @ColumnInfo(name = "record_key")
    var recordKey: String,

    @ColumnInfo(name = "storage_id")
    var storageId: Int?,

    @ColumnInfo(name = "unique_key")
    var uniqueKey: String,

    @ColumnInfo(name = "video_name")
    var videoName: String,

    /** 本机（LWW 败者或胜者）现场：播放位置 / 时长 / 更新时间。 */
    @ColumnInfo(name = "local_video_position")
    var localVideoPosition: Long,

    @ColumnInfo(name = "local_video_duration")
    var localVideoDuration: Long,

    @ColumnInfo(name = "local_updated_at")
    var localUpdatedAt: Long,

    /** 本机版本的播放时间（列表排序 / 展示用）。 */
    @ColumnInfo(name = "local_play_time")
    var localPlayTime: Long,

    /** 远端现场。 */
    @ColumnInfo(name = "remote_video_position")
    var remoteVideoPosition: Long,

    @ColumnInfo(name = "remote_video_duration")
    var remoteVideoDuration: Long,

    @ColumnInfo(name = "remote_updated_at")
    var remoteUpdatedAt: Long,

    /** 是否已解决（解决后由 UI 删除该行）。 */
    @ColumnInfo(name = "resolved")
    var resolved: Boolean = false,

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis(),
)
