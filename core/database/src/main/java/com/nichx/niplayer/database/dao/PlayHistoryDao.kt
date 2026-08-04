package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import com.nichx.niplayer.database.converter.MediaTypeConverter
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 历史记录与媒体库 JOIN 投影（M-31 修复）。
 *
 * 用于 UI 需要同时显示 storage 显示名（如"NAS-Video"、"本地存储"）的场景，
 * 避免在列表渲染路径上对每条历史记录单独查询 [com.nichx.niplayer.database
 * .entity.MediaLibraryEntity] 形成 N+1。
 *
 * 当前 [PlayHistoryScreen] 不显示 storageName，此投影作为 ready-to-use API，
 * 未来 UI 扩展（如分组按存储源展示、历史项展示来源图标）可直接使用，无需改 DAO。
 *
 * @param history 完整历史实体
 * @param storageName 媒体库显示名（来自 [com.nichx.niplayer.database.entity.MediaLibraryEntity.displayName]），
 *        storageId 为 null 时此字段为 null
 */
data class PlayHistoryWithStorage(
    @androidx.room.Embedded val history: PlayHistoryEntity,
    val storageName: String?,
)

/**
 * 按媒体类型聚合的播放统计（F-20）。
 *
 * @param mediaType 媒体类型（VIDEO / AUDIO / IMAGE）
 * @param count 该类型的播放记录数
 * @param totalPositionMs 该类型所有记录的累计播放位置（ms），近似总观看时长
 * @param totalDurationMs 该类型所有记录的累计总时长（ms）
 */
data class MediaTypeStat(
    val mediaType: String,
    val count: Int,
    val totalPositionMs: Long,
    val totalDurationMs: Long,
)

/**
 * 按存储源聚合的播放统计（F-20）。
 *
 * @param storageId 存储源 id
 * @param storageName 存储源显示名（LEFT JOIN media_library）
 * @param count 该存储源的播放记录数
 * @param totalPositionMs 累计播放位置（ms）
 */
data class StorageStat(
    val storageId: Int,
    val storageName: String?,
    val count: Int,
    val totalPositionMs: Long,
)

/**
 * 播放历史 Dao，迁移自旧仓库 PlayHistoryDao。
 * 修正旧方法名拼写 `gitLastPlay` / `gitStorageLastPlay` → `getLastPlay` / `getStorageLastPlay`
 * （参见方案 1.4.4 历史遗留清理风格）。
 *
 * BUG-H6 修复：所有展示用查询（getAll / getAllFlow / getRecentFlow / getSingleMediaType /
 * getLastPlay / getStorageLastPlay / getByMediaTypes / getModifiedSince）统一添加
 * `AND storage_id IS NOT NULL` 过滤，避免 storageId=null 的记录（直链播放、quick access
 * 写入的本地记录等）出现在列表中无法续播（[PlayHistoryViewModel.resumePlay] 对 storageId=null
 * 直接返回 Error，用户看到列表项点击无响应会困惑）。
 */
@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history WHERE url != '' AND storage_id IS NOT NULL ORDER BY play_time DESC")
    suspend fun getAll(): List<PlayHistoryEntity>

    /** 获取自 [sinceId] 之后新增或更新的记录（按 id 升序）。用于增量同步。 */
    @Query("SELECT * FROM play_history WHERE id > :sinceId AND storage_id IS NOT NULL ORDER BY id ASC")
    suspend fun getChangesSince(sinceId: Int): List<PlayHistoryEntity>

    /** 获取自 [sinceId] 之后新增或更新的记录（按 updated_at 升序）。用于增量同步捕获已有记录的更新。 */
    @Query("SELECT * FROM play_history WHERE updated_at > :sinceTimestamp AND storage_id IS NOT NULL ORDER BY updated_at ASC")
    suspend fun getChangesSinceTimestamp(sinceTimestamp: Long): List<PlayHistoryEntity>

    /** 获取当前最大 id，用于增量同步游标跟踪。 */
    @Query("SELECT MAX(id) FROM play_history")
    suspend fun getMaxId(): Int?

    /** 获取当前最大 updated_at，用于增量同步时间游标跟踪。 */
    @Query("SELECT MAX(updated_at) FROM play_history")
    suspend fun getMaxUpdatedAt(): Long?

    /**
     * 全量播放历史（响应式），按 [playTime] 倒序。
     *
     * 供播放历史列表页使用，删除/清空后 UI 自动刷新。
     */
    @Query("SELECT * FROM play_history WHERE url != '' AND storage_id IS NOT NULL ORDER BY play_time DESC")
    fun getAllFlow(): Flow<List<PlayHistoryEntity>>

    /**
     * 最近播放列表（响应式），按 [playTime] 倒序，限制 [limit] 条。
     *
     * 供首页"最近播放"卡片使用，避免一次性 [getAll] 加载全量历史。
     */
    @Query("SELECT * FROM play_history WHERE url != '' AND storage_id IS NOT NULL ORDER BY play_time DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<PlayHistoryEntity>>

    /**
     * 按文件名关键词搜索播放历史（首页搜索）。
     *
     * 使用 LIKE 子串匹配（SQLite 对 ASCII 不区分大小写），按播放时间倒序，限制 [limit] 条
     * 避免大历史库下全量扫描返回过多结果。
     */
    @Query(
        "SELECT * FROM play_history WHERE url != '' AND storage_id IS NOT NULL " +
            "AND video_name LIKE '%' || :keyword || '%' " +
            "ORDER BY play_time DESC LIMIT :limit"
    )
    suspend fun searchByKeyword(keyword: String, limit: Int = 50): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_history WHERE url != '' AND storage_id IS NOT NULL AND media_type = (:mediaType) ORDER BY play_time DESC")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getSingleMediaType(mediaType: MediaType): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_history WHERE media_type IN (:mediaTypes) AND url != '' AND storage_id IS NOT NULL ORDER BY play_time DESC LIMIT 1")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getLastPlay(vararg mediaTypes: MediaType): PlayHistoryEntity?

    @Query("SELECT * FROM play_history WHERE storage_id = (:storageId) AND url != '' ORDER BY play_time DESC LIMIT 1")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getStorageLastPlay(storageId: Int): PlayHistoryEntity?

    @Query("SELECT * FROM play_history WHERE unique_key = (:uniqueKey) AND media_type = (:mediaType)")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getPlayHistory(uniqueKey: String, mediaType: MediaType): PlayHistoryEntity?

    @Query("SELECT * FROM play_history WHERE unique_key = (:uniqueKey) AND storage_id = (:storageId)")
    suspend fun getPlayHistory(uniqueKey: String, storageId: Int): PlayHistoryEntity?

    @Query("SELECT * FROM play_history WHERE storage_path = (:storagePath) AND storage_id = (:storageId) LIMIT 1")
    suspend fun getPlayHistoryByPath(storagePath: String, storageId: Int): PlayHistoryEntity?

    @Query("SELECT * FROM play_history WHERE unique_key = (:uniqueKey) AND storage_id = (:storageId)")
    fun getPlayHistoryFlow(uniqueKey: String, storageId: Int): Flow<PlayHistoryEntity?>

    /**
     * 插入播放历史记录。
     *
     * BUG-H1 修复：改用 [OnConflictStrategy.IGNORE]，避免 REPLACE 策略在
     * (unique_key, storage_id) 冲突时删除旧记录并插入新记录导致 id 变更。
     *
     * 调用方（[com.nichx.niplayer.feature.player.PlayerViewModel]）均采用
     * 先 query 再 update/insert 的 upsert 模式，不依赖 insert 的 REPLACE 副作用。
     * IGNORE 作为安全网，防止并发场景下意外删除已有记录。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vararg entities: PlayHistoryEntity)

    @Update
    suspend fun update(entity: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE id = (:id)")
    suspend fun delete(id: Int)

    /** 按 id 查询单条历史（供删除前记录同步 tombstone）。 */
    @Query("SELECT * FROM play_history WHERE id = (:id)")
    suspend fun getById(id: Int): PlayHistoryEntity?

    /**
     * 删除指定目录前缀下的播放历史（用于屏蔽目录时清理已播记录）。
     *
     * M-30 修复：[prefix] 中的 `%` / `_` / `\` 在 LIKE 模式下是特殊字符，未转义时
     * 会匹配到非预期记录。例如 storagePath = ` Movies\2024` 含 `_`（实际场景：
     * 用户自定义目录含下划线、Windows 路径分隔符 `\`），原 SQL `LIKE :prefix || '%'`
     * 会把 `_` 当通配符匹配任意单字符、`%` 当通配符匹配任意串。
     *
     * 现在通过嵌套 [REPLACE] 在 SQL 内部完成转义（调用方无需感知）：
     * 1. `\` → `\\`（先转义 escape 字符自身）
     * 2. `%` → `\%`
     * 3. `_` → `\_`
     * 配合 `ESCAPE '\'` 子句，转义后的 prefix 在 LIKE 模式下作为字面量匹配。
     *
     * 性能：REPLACE 嵌套对每个 storage_path 字段调用 3 次 REPLACE，storage_path 已
     * 有索引（unique_key 复合索引）时仍走全表扫描（LIKE 前缀匹配无法用索引）。
     * 此方法仅在屏蔽目录时调用（低频），全表扫描可接受。
     */
    @Query("DELETE FROM play_history WHERE storage_path LIKE REPLACE(REPLACE(REPLACE(:prefix, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_') || '%' ESCAPE '\\'")
    suspend fun deleteByStoragePathPrefix(prefix: String)

    /**
     * 删除指定存储源下目录前缀的播放历史（文件夹设置为加密时清理存量历史）。
     *
     * 与 [deleteByStoragePathPrefix] 相同的 LIKE 转义逻辑，追加 [storageId] 条件，
     * 避免误删其他存储源同路径前缀的记录。
     */
    @Query("DELETE FROM play_history WHERE storage_id = :storageId AND storage_path LIKE REPLACE(REPLACE(REPLACE(:prefix, '\\\\', '\\\\\\\\'), '%', '\\\\%'), '_', '\\\\_') || '%' ESCAPE '\\'")
    suspend fun deleteByStoragePathPrefixAndStorageId(storageId: Int, prefix: String)

    @Query("DELETE FROM play_history WHERE storage_id = (:storageId)")
    suspend fun deleteByStorageId(storageId: Int)

    @Query("DELETE FROM play_history WHERE media_type IN (:mediaType)")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun deleteTypeAll(mediaType: List<MediaType>)

    @Query("DELETE FROM play_history")
    suspend fun deleteAll()

    @Query("UPDATE play_history SET subtitle_path = (:subtitlePath) WHERE unique_key = (:uniqueKey) AND storage_id = (:storageId)")
    suspend fun updateSubtitle(uniqueKey: String, storageId: Int, subtitlePath: String?)

    @Query("UPDATE play_history SET audio_path = (:audioPath) WHERE unique_key = (:uniqueKey) AND storage_id = (:storageId)")
    suspend fun updateAudio(uniqueKey: String, storageId: Int, audioPath: String?)

    @Query("SELECT * FROM play_history WHERE media_type IN (:mediaTypes) AND url != '' AND storage_id IS NOT NULL AND play_time > :sinceTimestamp ORDER BY play_time DESC")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getModifiedSince(mediaTypes: List<MediaType>, sinceTimestamp: Long): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_history WHERE media_type IN (:mediaTypes) AND url != '' AND storage_id IS NOT NULL ORDER BY play_time DESC")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getByMediaTypes(mediaTypes: List<MediaType>): List<PlayHistoryEntity>

    /**
     * W-N13 修复：原子 upsert，避免并发场景下 query-then-update/insert 窗口期。
     *
     * 原实现（PlayerViewModel.saveProgressInternal）：
     * ```kotlin
     * val existing = getPlayHistory(uniqueKey, storageId)  // 步骤 1
     * if (existing != null) update(existing)              // 步骤 2
     * else insert(entity)                                  // 步骤 2'
     * ```
     * 两个协程并发时，都可能在步骤 1 查到 existing==null，都走步骤 2' insert。
     * 唯一索引 + IGNORE 让第二个 insert 静默失败，**第二条的进度数据丢失**。
     *
     * 此方法用 [@Transaction][Transaction] 包裹 query+update/insert，Room 在数据库
     * 层加事务锁串行化，确保并发协程不会同时读到 existing==null。
     *
     * BUG-24 保护：existing != null 且 newPosition <= 0 时直接 return，不覆盖已有进度
     * （针对 onCleared 时 player Error 状态导致 position=0 的兜底场景）。
     *
     * @param uniqueKey 业务唯一键（`${library.id}:${file.path}`）
     * @param storageId 存储源 id
     * @param newPosition 新播放位置（ms），<=0 时不覆盖已有进度
     * @param newDuration 新总时长（ms）
     * @param newPlayTime 新播放时间
     * @param newEntity existing==null 时用于 insert 的完整实体（调用方构造）
     */
    @Transaction
    suspend fun upsertProgress(
        uniqueKey: String,
        storageId: Int,
        newPosition: Long,
        newDuration: Long,
        newPlayTime: Date,
        newEntity: PlayHistoryEntity,
    ) {
        val existing = getPlayHistory(uniqueKey, storageId)
        if (existing != null) {
            // BUG-24：position <= 0 视为无效（player Error/Idle），不覆盖已有进度
            if (newPosition <= 0) return
            existing.videoPosition = newPosition
            existing.videoDuration = newDuration
            existing.playTime = newPlayTime
            // 刷新 updated_at 供增量同步（play_history 云同步）
            existing.updatedAt = System.currentTimeMillis()
            update(existing)
        } else {
            insert(newEntity)
        }
    }

    /**
     * C-04 修复：原子 upsert 播放开始记录。
     *
     * 与 [upsertProgress] 对称，封装 [recordPlayStart][com.nichx.niplayer.feature.player
     * .PlayerViewModel.recordPlayStart] 的 query-then-update/insert 模式。
     *
     * 语义：
     * - existing != null：刷新 [playTime]（刷新"最近播放"排序），**不覆盖** videoPosition
     *   （BUG-H3：避免未播放时用 startPositionMs 覆盖已有进度）；
     *   更新 [playlistId] 为本次播放来源（歌单播放时记录歌单 ID，文件夹播放时置 null）
     * - existing == null：用 [newEntity] insert（videoPosition=startPositionMs 作为初始值）
     *
     * @Transaction 保证并发安全，避免两个协程同时查到 existing==null 都走 insert 路径
     * 导致第二个 insert 因唯一索引冲突被 IGNORE 静默丢弃。
     *
     * @param uniqueKey 业务唯一键
     * @param storageId 存储源 id
     * @param newPlayTime 新播放时间
     * @param newEntity existing==null 时用于 insert 的完整实体
     */
    @Transaction
    suspend fun upsertPlayStart(
        uniqueKey: String,
        storageId: Int,
        newPlayTime: Date,
        newEntity: PlayHistoryEntity,
    ) {
        val existing = getPlayHistory(uniqueKey, storageId)
        if (existing != null) {
            // BUG-H3：仅更新 playTime 刷新排序，不覆盖 videoPosition
            existing.playTime = newPlayTime
            // 刷新 updated_at 供增量同步（play_history 云同步）
            existing.updatedAt = System.currentTimeMillis()
            // 更新来源歌单：从歌单播放时记录 playlistId，从文件夹播放时清除（null）
            existing.playlistId = newEntity.playlistId
            update(existing)
        } else {
            insert(newEntity)
        }
    }

    /**
     * M-31 修复：JOIN 查询历史 + 媒体库显示名（响应式）。
     *
     * 用于 UI 同时显示历史记录与来源存储名，避免每条记录单独 [getById] 形成的 N+1。
     * 用 LEFT JOIN 保证 storageId 为 null 的记录仍返回（storageName=null）。
     *
     * 与 [getAllFlow] 对称，仅多返回 storageName 字段。当 UI 不需要 storageName 时
     * 仍应使用 [getAllFlow]（少一次 JOIN，性能更优）。
     */
    @Query(
        "SELECT h.*, l.display_name AS storageName FROM play_history h " +
            "LEFT JOIN media_library l ON h.storage_id = l.id " +
            "WHERE h.url != '' AND h.storage_id IS NOT NULL " +
            "ORDER BY h.play_time DESC"
    )
    fun getAllWithStorageFlow(): Flow<List<PlayHistoryWithStorage>>

    /**
     * M-31 修复：JOIN 查询最近 [limit] 条历史 + 媒体库显示名（响应式）。
     *
     * 与 [getRecentFlow] 对称，用于首页"最近播放"卡片未来扩展显示来源。
     */
    @Query(
        "SELECT h.*, l.display_name AS storageName FROM play_history h " +
            "LEFT JOIN media_library l ON h.storage_id = l.id " +
            "WHERE h.url != '' AND h.storage_id IS NOT NULL " +
            "ORDER BY h.play_time DESC LIMIT :limit"
    )
    fun getRecentWithStorageFlow(limit: Int): Flow<List<PlayHistoryWithStorage>>

    // ==================== F-20 播放统计聚合查询 ====================

    /** 总播放记录数。 */
    @Query("SELECT COUNT(*) FROM play_history WHERE url != '' AND storage_id IS NOT NULL")
    fun getTotalPlayCountFlow(): Flow<Int>

    /** 累计观看时长（ms），取 SUM(video_position) 近似。 */
    @Query("SELECT COALESCE(SUM(video_position), 0) FROM play_history WHERE url != '' AND storage_id IS NOT NULL")
    fun getTotalWatchTimeFlow(): Flow<Long>

    /** 按媒体类型聚合统计。 */
    @Query(
        "SELECT media_type AS mediaType, COUNT(*) AS count, " +
            "COALESCE(SUM(video_position), 0) AS totalPositionMs, " +
            "COALESCE(SUM(video_duration), 0) AS totalDurationMs " +
            "FROM play_history WHERE url != '' AND storage_id IS NOT NULL " +
            "GROUP BY media_type ORDER BY count DESC"
    )
    fun getMediaTypeStatsFlow(): Flow<List<MediaTypeStat>>

    /** 按存储源聚合统计（JOIN media_library 取显示名）。 */
    @Query(
        "SELECT h.storage_id AS storageId, l.display_name AS storageName, " +
            "COUNT(*) AS count, COALESCE(SUM(h.video_position), 0) AS totalPositionMs " +
            "FROM play_history h LEFT JOIN media_library l ON h.storage_id = l.id " +
            "WHERE h.url != '' AND h.storage_id IS NOT NULL " +
            "GROUP BY h.storage_id ORDER BY count DESC"
    )
    fun getStorageStatsFlow(): Flow<List<StorageStat>>

    /** 近 N 天的播放记录数。 */
    @Query("SELECT COUNT(*) FROM play_history WHERE url != '' AND storage_id IS NOT NULL AND play_time > :sinceTimestamp")
    fun getRecentPlayCountFlow(sinceTimestamp: Long): Flow<Int>

    /** 近 N 天的累计观看时长（ms）。 */
    @Query("SELECT COALESCE(SUM(video_position), 0) FROM play_history WHERE url != '' AND storage_id IS NOT NULL AND play_time > :sinceTimestamp")
    fun getRecentWatchTimeFlow(sinceTimestamp: Long): Flow<Long>

    /** 观看时长 Top N（按 video_position 降序）。 */
    @Query("SELECT * FROM play_history WHERE url != '' AND storage_id IS NOT NULL AND video_position > 0 ORDER BY video_position DESC LIMIT :limit")
    fun getTopWatchedFlow(limit: Int): Flow<List<PlayHistoryEntity>>
}
