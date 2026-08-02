package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import com.nichx.niplayer.database.converter.MediaTypeConverter
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import kotlinx.coroutines.flow.Flow

/**
 * 媒体库 Dao，迁移自旧仓库 MediaLibraryDao。
 * `getAll` / `getByMediaType` 由 LiveData 改为 Flow（参见方案硬约束：Coroutines + StateFlow 全面替代 LiveData）。
 */
@Dao
interface MediaLibraryDao {

    @Query("SELECT * FROM media_library ORDER BY id ASC")
    fun getAll(): Flow<MutableList<MediaLibraryEntity>>

    @Query("SELECT * FROM media_library WHERE media_type = (:mediaType)")
    @TypeConverters(MediaTypeConverter::class)
    fun getByMediaType(mediaType: MediaType): Flow<MediaLibraryEntity?>

    @Query("SELECT * FROM media_library WHERE id = (:libraryId)")
    suspend fun getById(libraryId: Int): MediaLibraryEntity?

    @Query("SELECT * FROM media_library WHERE media_type = (:mediaType)")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getByMediaTypeSuspend(mediaType: MediaType): MutableList<MediaLibraryEntity>

    @Query("SELECT * FROM media_library WHERE url = (:url) AND media_type = (:mediaType)")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun getByUrl(url: String, mediaType: MediaType): MediaLibraryEntity?

    /** 全量查询（suspend），用于备份导出。 */
    @Query("SELECT * FROM media_library ORDER BY id ASC")
    suspend fun getAllSuspend(): List<MediaLibraryEntity>

    /**
     * 按显示名关键词搜索存储源（首页搜索）。
     *
     * 使用 LIKE 子串匹配，按 id 升序返回，限制 [limit] 条。
     */
    @Query(
        "SELECT * FROM media_library WHERE display_name LIKE '%' || :keyword || '%' " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun searchByKeyword(keyword: String, limit: Int = 20): List<MediaLibraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entity: MediaLibraryEntity)

    /** 批量插入，用于恢复导入。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MediaLibraryEntity>)

    @Query("DELETE FROM media_library WHERE url = (:url) AND media_type = (:mediaType)")
    @TypeConverters(MediaTypeConverter::class)
    suspend fun delete(url: String, mediaType: MediaType)

    @Query("DELETE FROM media_library WHERE id = (:id)")
    suspend fun deleteById(id: Int)

    /** 清空全表，用于恢复前清库。 */
    @Query("DELETE FROM media_library")
    suspend fun deleteAll()

    /** 获取自 [sinceTimestamp] 之后新增或更新的记录（按 updated_at 升序）。用于增量同步。 */
    @Query("SELECT * FROM media_library WHERE updated_at > :sinceTimestamp ORDER BY updated_at ASC")
    suspend fun getChangesSince(sinceTimestamp: Long): List<MediaLibraryEntity>

    /** 获取当前最大 updated_at，用于增量同步游标跟踪。 */
    @Query("SELECT COALESCE(MAX(updated_at), 0) FROM media_library")
    suspend fun getMaxUpdatedAt(): Long
}
