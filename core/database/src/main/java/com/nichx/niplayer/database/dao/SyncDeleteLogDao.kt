package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity

@Dao
interface SyncDeleteLogDao {

    @Query("SELECT * FROM sync_delete_log WHERE synced = 0 ORDER BY deleted_at ASC")
    suspend fun getUnsyncedDeletes(): List<SyncDeleteLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SyncDeleteLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SyncDeleteLogEntity>)

    @Query("UPDATE sync_delete_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM sync_delete_log WHERE synced = 1")
    suspend fun deleteSynced()

    @Query("DELETE FROM sync_delete_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sync_delete_log WHERE synced = 0")
    suspend fun countUnsynced(): Int
}
