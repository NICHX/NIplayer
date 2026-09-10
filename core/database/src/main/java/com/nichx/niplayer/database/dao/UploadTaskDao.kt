package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nichx.niplayer.database.entity.UploadTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * 上传任务 Dao，与下载任务 [DownloadTaskDao] 对齐。
 */
@Dao
interface UploadTaskDao {

    @Query("SELECT * FROM upload_task ORDER BY create_time DESC")
    fun getAllFlow(): Flow<List<UploadTaskEntity>>

    @Query("SELECT * FROM upload_task WHERE id = :id")
    suspend fun getById(id: Long): UploadTaskEntity?

    @Query("SELECT * FROM upload_task WHERE state IN (:states) ORDER BY create_time DESC")
    suspend fun getByStates(states: List<Int>): List<UploadTaskEntity>

    @Query("SELECT COUNT(*) FROM upload_task WHERE state IN (:states)")
    fun countByStatesFlow(states: List<Int>): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: UploadTaskEntity): Long

    @Update
    suspend fun update(task: UploadTaskEntity)

    @Query("UPDATE upload_task SET uploaded_bytes = :uploadedBytes, state = :state WHERE id = :id")
    suspend fun updateProgress(id: Long, uploadedBytes: Long, state: Int)

    @Query("UPDATE upload_task SET total_bytes = :totalBytes WHERE id = :id")
    suspend fun updateTotalBytes(id: Long, totalBytes: Long)

    @Query("UPDATE upload_task SET state = :state, error_message = :errorMessage WHERE id = :id")
    suspend fun updateState(id: Long, state: Int, errorMessage: String? = null)

    @Query("DELETE FROM upload_task WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM upload_task WHERE storage_id = :storageId")
    suspend fun deleteByStorageId(storageId: Int)

    @Query("DELETE FROM upload_task WHERE state = :state")
    suspend fun deleteByState(state: Int)
}