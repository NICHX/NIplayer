package com.xyoye.common_component.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xyoye.data_component.entity.TmdbSyncQueueEntity
import com.xyoye.data_component.entity.TmdbSyncQueueEntity.State

@Dao
interface TmdbSyncQueueDao {

    @Query("SELECT * FROM tmdb_sync_queue WHERE state = (:stateStr) ORDER BY priority DESC, update_time ASC LIMIT 1")
    suspend fun getNextPending(stateStr: String): TmdbSyncQueueEntity?

    @Query("SELECT * FROM tmdb_sync_queue WHERE state = 'PENDING' ORDER BY priority DESC, update_time ASC LIMIT (:limit)")
    suspend fun getPendingBatch(limit: Int): List<TmdbSyncQueueEntity>

    @Query("SELECT COUNT(*) FROM tmdb_sync_queue WHERE state = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TmdbSyncQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TmdbSyncQueueEntity>)

    @Query("UPDATE tmdb_sync_queue SET state = (:newState), update_time = (:updateTime) WHERE key = (:key)")
    suspend fun updateState(key: String, newState: String, updateTime: Long = System.currentTimeMillis())

    @Query("UPDATE tmdb_sync_queue SET state = (:newState), attempt_count = attempt_count + 1, last_error = (:lastError), update_time = (:updateTime) WHERE key = (:key)")
    suspend fun markFailed(key: String, newState: String, lastError: String?, updateTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM tmdb_sync_queue")
    suspend fun clearAll()

    @Query("DELETE FROM tmdb_sync_queue WHERE state = 'DONE'")
    suspend fun clearDone()

    @Query("DELETE FROM tmdb_sync_queue WHERE key = (:key)")
    suspend fun deleteByKey(key: String)

    @Query("SELECT state FROM tmdb_sync_queue WHERE key = (:key) LIMIT 1")
    suspend fun getStateByKey(key: String): String?

    suspend fun isPendingOrRunning(key: String): Boolean {
        val state = getStateByKey(key) ?: return false
        return state == State.PENDING.name || state == State.RUNNING.name
    }
}