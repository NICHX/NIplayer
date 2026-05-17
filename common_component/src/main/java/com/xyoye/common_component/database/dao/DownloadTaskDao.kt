package com.xyoye.common_component.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xyoye.data_component.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_task ORDER BY create_time DESC")
    fun getAllFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task ORDER BY create_time DESC")
    suspend fun getAll(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun getById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_task WHERE unique_key = :uniqueKey AND storage_id = :storageId")
    suspend fun getByUniqueKey(uniqueKey: String, storageId: Int): DownloadTaskEntity?

    @Query("SELECT COUNT(*) FROM download_task WHERE state IN (:states)")
    fun countByStatesFlow(states: List<Int>): Flow<Int>

    @Query("SELECT * FROM download_task WHERE state IN (:states) ORDER BY create_time DESC")
    suspend fun getByStates(states: List<Int>): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity): Long

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("UPDATE download_task SET downloaded_bytes = :downloadedBytes, state = :state WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedBytes: Long, state: Int)

    @Query("UPDATE download_task SET total_bytes = :totalBytes WHERE id = :id")
    suspend fun updateTotalBytes(id: Long, totalBytes: Long)

    @Query("UPDATE download_task SET state = :state, error_message = :errorMessage WHERE id = :id")
    suspend fun updateState(id: Long, state: Int, errorMessage: String? = null)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_task WHERE state = :state")
    suspend fun deleteByState(state: Int)

    @Query("DELETE FROM download_task")
    suspend fun deleteAll()
}
