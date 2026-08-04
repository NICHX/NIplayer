package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {

    @Query("SELECT * FROM sync_conflict WHERE resolved = 0 ORDER BY created_at DESC")
    fun getUnresolvedFlow(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflict WHERE resolved = 0 ORDER BY created_at DESC")
    suspend fun getUnresolved(): List<SyncConflictEntity>

    @Query("SELECT COUNT(*) FROM sync_conflict WHERE resolved = 0")
    suspend fun countUnresolved(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncConflictEntity)

    @Delete
    suspend fun delete(entity: SyncConflictEntity)

    @Query("DELETE FROM sync_conflict WHERE resolved = 1")
    suspend fun deleteResolved()

    @Query("DELETE FROM sync_conflict")
    suspend fun deleteAll()
}
