package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.AudioMatchEntity

/**
 * 音频匹配结果 Dao。
 *
 * `file_key` 上有唯一索引，[upsert] 用 REPLACE 策略写入，因此同一首歌只会有一条记录。
 */
@Dao
interface AudioMatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AudioMatchEntity)

    @Query("SELECT * FROM audio_match WHERE file_key = (:fileKey)")
    suspend fun getByFileKey(fileKey: String): AudioMatchEntity?

    /**
     * 取用户锁定过的记录。
     *
     * 自动匹配流程在覆盖前应先查这个 —— 有锁定记录时必须尊重用户的选择。
     */
    @Query("SELECT * FROM audio_match WHERE file_key = (:fileKey) AND locked = 1")
    suspend fun getLockedByFileKey(fileKey: String): AudioMatchEntity?

    @Query("SELECT * FROM audio_match")
    suspend fun getAll(): List<AudioMatchEntity>

    @Query("SELECT COUNT(*) FROM audio_match")
    suspend fun count(): Int

    @Query("DELETE FROM audio_match WHERE file_key = (:fileKey)")
    suspend fun deleteByFileKey(fileKey: String)

    @Query("DELETE FROM audio_match")
    suspend fun deleteAll()
}
