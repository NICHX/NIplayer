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

    /**
     * 写入删除 tombstone（_replace 语义）：同一 key 已存在则用新删除时间覆盖。
     *
     * 用于删除传播路径（PlayHistorySyncDeleter / 播放历史列表删除）：当同一记录被删除后重扫
     * 再删等情况，需刷新为最新删除时间，否则旧 tombstone 时间早于重新写入记录的 updatedAt，
     * 远端将无法据此删除（记录"复活"）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SyncDeleteLogEntity)

    @Query("UPDATE sync_delete_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM sync_delete_log WHERE synced = 1")
    suspend fun deleteSynced()

    @Query("DELETE FROM sync_delete_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sync_delete_log WHERE synced = 0")
    suspend fun countUnsynced(): Int
}
