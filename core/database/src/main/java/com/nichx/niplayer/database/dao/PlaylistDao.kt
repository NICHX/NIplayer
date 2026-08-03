package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nichx.niplayer.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/** 歌单列表查询投影：歌单 + 条目数量（封面卡片显示用）。 */
data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val itemCount: Int,
)

@Dao
interface PlaylistDao {

    /** 全量歌单（含条目数），按最近更新倒序。 */
    @Query(
        """
        SELECT playlist.*,
               (SELECT COUNT(*) FROM playlist_item
                WHERE playlist_item.playlist_id = playlist.id) AS itemCount
        FROM playlist
        ORDER BY playlist.updated_at DESC, playlist.id DESC
        """
    )
    fun getAllWithCountFlow(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlist ORDER BY updated_at DESC, id DESC")
    fun getAllFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    suspend fun getById(playlistId: Int): PlaylistEntity?

    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    fun getByIdFlow(playlistId: Int): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deleteById(playlistId: Int)

    @Query("UPDATE playlist SET updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun touch(playlistId: Int, updatedAt: Long)
}
