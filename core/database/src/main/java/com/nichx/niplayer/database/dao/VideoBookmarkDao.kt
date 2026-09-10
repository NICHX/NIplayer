package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * 视频书签 DAO（F-19）。
 */
@Dao
interface VideoBookmarkDao {

    /** 响应式查询指定视频的所有书签，按位置升序。 */
    @Query(
        "SELECT * FROM video_bookmark WHERE unique_key = :uniqueKey " +
            "AND (storage_id IS :storageId OR (storage_id IS NULL AND :storageId IS NULL)) " +
            "ORDER BY position_ms ASC"
    )
    fun getBookmarksFlow(uniqueKey: String, storageId: Int?): Flow<List<VideoBookmarkEntity>>

    /** 一次性查询指定视频的所有书签。 */
    @Query(
        "SELECT * FROM video_bookmark WHERE unique_key = :uniqueKey " +
            "AND (storage_id IS :storageId OR (storage_id IS NULL AND :storageId IS NULL)) " +
            "ORDER BY position_ms ASC"
    )
    suspend fun getBookmarks(uniqueKey: String, storageId: Int?): List<VideoBookmarkEntity>

    /** 全量查询（suspend），用于备份导出。 */
    @Query("SELECT * FROM video_bookmark ORDER BY id ASC")
    suspend fun getAll(): List<VideoBookmarkEntity>

    /** 插入书签，冲突（同位置）时替换。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: VideoBookmarkEntity): Long

    /** 批量插入，用于恢复导入。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VideoBookmarkEntity>)

    /** 按 id 删除书签。 */
    @Query("DELETE FROM video_bookmark WHERE id = :id")
    suspend fun delete(id: Int)

    /** 按视频和位置删除书签。 */
    @Query(
        "DELETE FROM video_bookmark WHERE unique_key = :uniqueKey " +
            "AND (storage_id IS :storageId OR (storage_id IS NULL AND :storageId IS NULL)) " +
            "AND position_ms = :positionMs"
    )
    suspend fun deleteByPosition(uniqueKey: String, storageId: Int?, positionMs: Long)

    /** 删除指定视频的所有书签（视频被删除时调用）。 */
    @Query(
        "DELETE FROM video_bookmark WHERE unique_key = :uniqueKey " +
            "AND (storage_id IS :storageId OR (storage_id IS NULL AND :storageId IS NULL))"
    )
    suspend fun deleteAllByVideo(uniqueKey: String, storageId: Int?)

    /** 删除指定存储源的所有书签。 */
    @Query("DELETE FROM video_bookmark WHERE storage_id = :storageId")
    suspend fun deleteByStorageId(storageId: Int)

    /** 清空全表，用于恢复前清库。 */
    @Query("DELETE FROM video_bookmark")
    suspend fun deleteAll()
}
