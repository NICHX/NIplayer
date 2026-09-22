package com.nichx.niplayer.sync

import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.database.syncKey
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * 播放历史云同步的云端文件协议。
 *
 * 云端只有一个共享文件 `NIplayer_backup/sync/play_history.json`（见
 * `PlayHistorySyncManager.FILE_NAME`），位于备份目录的 `sync/` 子目录下（复用
 * `NIplayer_backup/`，不新增一级目录）。
 *
 * ## 全量快照 = 折叠后的胜者集合
 *
 * 文件内容是**所有已知候选折叠后的结果**：每个 syncKey 恰好留下一条最终结果 —— 要么是
 * 一条记录，要么是一条删除墓碑。它**不是**"某台设备持有的记录"。
 *
 * 这一点决定了写回语义：本机匹配不到存储库、因而落不了本地 DB 的外来记录，**仍然必须
 * 原样写回文件**。否则一台没配置某存储库的设备一同步，就会把别的设备发布的记录抹掉。
 *
 * 每次同步都是"读 → 折叠 → 写回"，不做增量 delta、不做远端指纹跳过：播放历史是小数据
 * （数千条 × 约 200B ≈ 数百 KB），全量读写的代价远低于维护 delta/压缩/指纹一致性的复杂度，
 * 而后者正是历史上多数同步缺陷的来源。
 *
 * ## 墓碑保留
 *
 * [deletes] 从不做过期清理：只要墓碑还在，晚联网的设备就不会把已删记录"复活"。代价是
 * 文件随删除次数单调增长。
 *
 * [version] 仅供协议演进判定（5 = 单文件共享快照）。
 */
@JsonClass(generateAdapter = true)
data class PlayHistorySyncFile(
    /** 协议版本：5 起为单文件共享快照（4 及以前是每设备一个文件）。 */
    val version: Int = CURRENT_PROTOCOL_VERSION,
    /** 各 key 折叠后的胜者记录。 */
    val records: List<SyncRecord> = emptyList(),
    /** 各 key 折叠后的胜者墓碑。 */
    val deletes: List<SyncDelete> = emptyList(),
)

/** 单条播放历史记录（不含 DB 自增 id 与跨设备无意义的 subtitle/audio/torrent 字段）。 */
@JsonClass(generateAdapter = true)
data class SyncRecord(
    /** 设备无关的同步键：`归一化存储地址 + 存储内相对路径`（见 [syncKey]），用于跨设备匹配。 */
    val key: String,
    /**
     * 生成 [key] 时使用的存储地址（原始）。供拉取端据此匹配本机是否有相同地址的存储库，
     * 从而把远端记录落成本地可续播的历史行。
     */
    val baseUrl: String?,
    val videoName: String,
    val url: String,
    val mediaType: String,
    val videoPosition: Long,
    val videoDuration: Long,
    val playTime: Long,
    val httpHeader: String?,
    val storagePath: String?,
    /** 记录最后修改时间（ms）。跨设备合并的唯一 LWW 依据。 */
    val updatedAt: Long,
)

/** 删除墓碑：某设备在某时刻删除了某条记录。 */
@JsonClass(generateAdapter = true)
data class SyncDelete(
    val key: String,
    val deletedAt: Long,
)

/** 当前云文件协议版本。 */
const val CURRENT_PROTOCOL_VERSION = 5

/** 既无记录也无墓碑：没有任何内容需要发布（此时不必在云端创建一个空文件）。 */
internal fun PlayHistorySyncFile.isEmpty(): Boolean = records.isEmpty() && deletes.isEmpty()

/**
 * 由本地实体构造云端记录；storageId / storagePath / baseUrl 任一缺失（异常或本地数据）时返回 null。
 *
 * @param baseUrl 该记录所属存储库的地址，用于生成设备无关的 [syncKey]。
 */
fun PlayHistoryEntity.toSyncRecord(baseUrl: String?): SyncRecord? {
    storageId ?: return null
    val path = storagePath ?: return null
    val base = baseUrl ?: return null
    return SyncRecord(
        key = syncKey(base, path),
        baseUrl = baseUrl,
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

/**
 * 云端记录转回本地实体。
 *
 * [storageId] / [uniqueKey] 由调用方按本机匹配到的存储库构造（远端携带的 id 与
 * uniqueKey 是设备本地的，跨设备无意义）。
 */
fun SyncRecord.toEntity(storageId: Int, uniqueKey: String): PlayHistoryEntity = PlayHistoryEntity(
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
