package com.nichx.niplayer.sync

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.database.syncKey
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * 播放历史云同步的云端文件协议。
 *
 * 每设备一个 JSON 文件 `play_history_<deviceId>.json`，存放于
 * `NIplayer_backup/sync/` 子目录（复用备份目录，不新增一级目录）。
 *
 * 文件内容 = 本设备持有的"权威快照"（全量 records + 本设备发起的删除 tombstone），
 * 同步时整文件覆盖上传，拉取后按记录级 last-write-wins 合并。
 *
 * [version] 用于协议演进；[lastSyncedAt] 记录本设备最后一次成功上传时间，
 * 作为废弃设备判定的心跳（活动设备至少每 24h 强制上传一次保持心跳）。
 */

/** 云端每设备同步文件。 */
@JsonClass(generateAdapter = true)
data class PlayHistorySyncFile(
    val deviceId: String,
    /** 协议版本，供未来协议演进判定。 */
    val version: Int = 1,
    /** 本文件内记录的最大 updated_at（合并基线 / 游标）。 */
    val updatedAt: Long = 0,
    /** 本设备当前持有的全部播放历史记录。 */
    val records: List<SyncRecord> = emptyList(),
    /** 本设备发起的删除 tombstone（key -> 删除时间）。 */
    val deletes: List<SyncDelete> = emptyList(),
    /** 本设备最后一次成功上传时间（ms），0 表示旧格式文件（未写心跳）。 */
    val lastSyncedAt: Long = 0,
)

/** 单条播放历史记录（不含 DB 自增 id 与跨设备无意义的 subtitle/audio/torrent 字段）。 */
@JsonClass(generateAdapter = true)
data class SyncRecord(
    /** 设备无关的同步键：`归一化存储地址 + 存储内相对路径`（见 [syncKey]），用于跨设备匹配。 */
    val key: String,
    /**
     * 生成 [key] 时使用的存储地址（原始，为适配不同写法可由用户保证一致）。
     * 供拉取端据其匹配本机是否有相同地址的存储库，从而把远端记录落成本地可续播的历史行。
     */
    val baseUrl: String?,
    val uniqueKey: String,
    val storageId: Int,
    val videoName: String,
    val url: String,
    val mediaType: String,
    val videoPosition: Long,
    val videoDuration: Long,
    val playTime: Long,
    val httpHeader: String?,
    val storagePath: String?,
    val updatedAt: Long,
)

/** 删除 tombstone：某设备在某时刻删除了某条记录。 */
@JsonClass(generateAdapter = true)
data class SyncDelete(
    val key: String,
    val deletedAt: Long,
)

/**
 * 由本地实体构造云端记录；storageId / storagePath / baseUrl 任一缺失（异常或本地数据）时返回 null。
 *
 * @param baseUrl 该记录所属存储库的地址，用于生成设备无关的 [syncKey]。
 */
fun PlayHistoryEntity.toSyncRecord(baseUrl: String?): SyncRecord? {
    val sid = storageId ?: return null
    val path = storagePath ?: return null
    val base = baseUrl ?: return null
    return SyncRecord(
        key = syncKey(base, path),
        baseUrl = baseUrl,
        uniqueKey = uniqueKey,
        storageId = sid,
        videoName = videoName,
        url = url,
        mediaType = mediaType.value,
        videoPosition = videoPosition,
        videoDuration = videoDuration,
        playTime = playTime.time,
        httpHeader = httpHeader,
        storagePath = storagePath,
        updatedAt = updatedAt,
    )
}

/** 云端记录转回本地实体。 */
fun SyncRecord.toEntity(): PlayHistoryEntity = PlayHistoryEntity(
    videoName = videoName,
    url = url,
    mediaType = MediaType.fromValue(mediaType),
    videoPosition = videoPosition,
    videoDuration = videoDuration,
    playTime = Date(playTime),
    httpHeader = httpHeader,
    uniqueKey = uniqueKey,
    storagePath = storagePath,
    storageId = storageId,
    updatedAt = updatedAt,
)
