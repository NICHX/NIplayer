package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.QuickAccessEntity
import kotlinx.coroutines.flow.Flow

/**
 * 快速访问书签 Dao。
 *
 * 查询返回 [Flow]（参见硬约束：Coroutines + StateFlow 全面替代 LiveData）。
 * 唯一约束 `(library_id, storage_path)` 由 [QuickAccessEntity] 索引保证，
 * 重复添加同一项时 [insert] 的 REPLACE 策略覆写旧记录（刷新 addTime）。
 *
 * 排序：列表按 [QuickAccessEntity.sortIndex] 升序展示，支持用户拖拽自定义顺序。
 * 新增项 [insert] 时应先调用 [getMaxSortIndex] 取当前最大值 +1 赋给 sortIndex。
 */
@Dao
interface QuickAccessDao {

    @Query("SELECT * FROM quick_access ORDER BY sort_index ASC, add_time DESC")
    fun getAllFlow(): Flow<List<QuickAccessEntity>>

    @Query("SELECT * FROM quick_access ORDER BY sort_index ASC, add_time DESC")
    suspend fun getAll(): List<QuickAccessEntity>

    @Query("SELECT * FROM quick_access WHERE library_id = (:libraryId) ORDER BY sort_index ASC, add_time DESC")
    fun getByLibraryFlow(libraryId: Int): Flow<List<QuickAccessEntity>>

    @Query("SELECT * FROM quick_access ORDER BY sort_index ASC, add_time DESC LIMIT (:limit)")
    fun getRecentFlow(limit: Int): Flow<List<QuickAccessEntity>>

    /**
     * 按名称关键词搜索快速访问书签（首页搜索）。
     *
     * 使用 LIKE 子串匹配，按用户排序顺序返回，限制 [limit] 条。
     */
    @Query(
        "SELECT * FROM quick_access WHERE name LIKE '%' || :keyword || '%' " +
            "ORDER BY sort_index ASC, add_time DESC LIMIT :limit"
    )
    suspend fun searchByKeyword(keyword: String, limit: Int = 50): List<QuickAccessEntity>

    @Query("SELECT * FROM quick_access WHERE library_id = (:libraryId) AND storage_path = (:storagePath)")
    suspend fun get(libraryId: Int, storagePath: String): QuickAccessEntity?

    @Query("SELECT MAX(sort_index) FROM quick_access")
    suspend fun getMaxSortIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QuickAccessEntity)

    /** 批量插入，用于恢复导入。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<QuickAccessEntity>)

    /**
     * 批量刷新排序序号。拖拽完成后按新顺序逐项传入 (id, newSortIndex)，
     * 一次性提交事务，避免列表抖动。
     */
    @Query("UPDATE quick_access SET sort_index = (:sortIndex) WHERE id = (:id)")
    suspend fun updateOrder(id: Int, sortIndex: Int)

    @Query("DELETE FROM quick_access WHERE id = (:id)")
    suspend fun delete(id: Int)

    @Query("DELETE FROM quick_access WHERE library_id = (:libraryId) AND storage_path = (:storagePath)")
    suspend fun delete(libraryId: Int, storagePath: String)

    @Query("DELETE FROM quick_access WHERE library_id = (:libraryId)")
    suspend fun deleteByLibrary(libraryId: Int)

    /** 清空全表，用于恢复前清库。 */
    @Query("DELETE FROM quick_access")
    suspend fun deleteAll()

    /** 获取自 [sinceTimestamp] 之后新增或更新的记录（按 updated_at 升序）。用于增量同步。 */
    @Query("SELECT * FROM quick_access WHERE updated_at > :sinceTimestamp ORDER BY updated_at ASC")
    suspend fun getChangesSince(sinceTimestamp: Long): List<QuickAccessEntity>

    /** 获取当前最大 updated_at，用于增量同步游标跟踪。 */
    @Query("SELECT COALESCE(MAX(updated_at), 0) FROM quick_access")
    suspend fun getMaxUpdatedAt(): Long
}
