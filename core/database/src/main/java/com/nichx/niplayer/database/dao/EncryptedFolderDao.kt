package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nichx.niplayer.database.entity.EncryptedFolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 加密文件夹 Dao。
 *
 * 提供按存储源 + 文件夹路径的增删查，以及：
 * - [getByStorageIdFlow]：文件浏览页锁定角标流查询（响应式，加密配置变更后 UI 自动刷新）
 * - [getByStorageId]：前缀匹配判定（历史抑制 / 进入目录拦截）
 * - [deleteByStorageId]：存储源删除级联清理
 */
@Dao
interface EncryptedFolderDao {

    @Query("SELECT * FROM encrypted_folder WHERE storage_id = :storageId")
    fun getByStorageIdFlow(storageId: Int): Flow<List<EncryptedFolderEntity>>

    @Query("SELECT * FROM encrypted_folder")
    suspend fun getAll(): List<EncryptedFolderEntity>

    @Query("SELECT * FROM encrypted_folder WHERE storage_id = :storageId")
    suspend fun getByStorageId(storageId: Int): List<EncryptedFolderEntity>

    @Query("SELECT * FROM encrypted_folder WHERE storage_id = :storageId AND folder_path = :folderPath LIMIT 1")
    suspend fun getByPath(storageId: Int, folderPath: String): EncryptedFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EncryptedFolderEntity): Long

    @Update
    suspend fun update(entity: EncryptedFolderEntity)

    @Query("DELETE FROM encrypted_folder WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM encrypted_folder WHERE storage_id = :storageId")
    suspend fun deleteByStorageId(storageId: Int)

    /** 清空全表，用于恢复导入前清库。 */
    @Query("DELETE FROM encrypted_folder")
    suspend fun deleteAll()
}
