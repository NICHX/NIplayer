package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.ExtendFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 扩展文件夹 Dao，迁移自旧仓库 ExtendFolderDao。
 */
@Dao
interface ExtendFolderDao {

    @Query("SELECT * FROM extend_folder")
    suspend fun getAll(): MutableList<ExtendFolderEntity>

    /** 观察所有扩展文件夹（用于扫描管理页 UI 自动刷新）。 */
    @Query("SELECT * FROM extend_folder ORDER BY folder_path")
    fun observeAll(): Flow<List<ExtendFolderEntity>>

    @Query("SELECT * FROM extend_folder WHERE folder_path = (:folderPath)")
    suspend fun getByFolderPath(folderPath: String): ExtendFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entity: ExtendFolderEntity)

    @Query("DELETE FROM extend_folder WHERE folder_path = (:folderPath)")
    suspend fun delete(folderPath: String)

    /** 清空全表，用于恢复前清库。 */
    @Query("DELETE FROM extend_folder")
    suspend fun deleteAll()

    /** 获取自 [sinceTimestamp] 之后新增或更新的记录（按 updated_at 升序）。用于增量同步。 */
    @Query("SELECT * FROM extend_folder WHERE updated_at > :sinceTimestamp ORDER BY updated_at ASC")
    suspend fun getChangesSince(sinceTimestamp: Long): List<ExtendFolderEntity>

    /** 获取当前最大 updated_at，用于增量同步游标跟踪。 */
    @Query("SELECT COALESCE(MAX(updated_at), 0) FROM extend_folder")
    suspend fun getMaxUpdatedAt(): Long
}
