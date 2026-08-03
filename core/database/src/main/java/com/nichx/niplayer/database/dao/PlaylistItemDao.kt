package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {

    @Query(
        """
        SELECT * FROM playlist_item
        WHERE playlist_id = :playlistId
        ORDER BY sort_order ASC, id ASC
        """
    )
    fun getByPlaylistFlow(playlistId: Int): Flow<List<PlaylistItemEntity>>

    @Query(
        """
        SELECT * FROM playlist_item
        WHERE playlist_id = :playlistId
        ORDER BY sort_order ASC, id ASC
        """
    )
    suspend fun getByPlaylist(playlistId: Int): List<PlaylistItemEntity>

    /** 追加条目：已存在（playlist_id, file_path）的自动去重，返回插入后的行 id（-1 表示重复跳过）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PlaylistItemEntity>): List<Long>

    @Update
    suspend fun update(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_item WHERE id = :itemId")
    suspend fun deleteById(itemId: Int)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Int)

    /** 每个歌单的首个条目（封面生成用）。 */
    @Query(
        """
        SELECT * FROM playlist_item WHERE id IN (
            SELECT MIN(id) FROM playlist_item GROUP BY playlist_id
        )
        """
    )
    suspend fun getFirstItemPerPlaylist(): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun countByPlaylist(playlistId: Int): Int

    /** 事务内追加：从当前条目数起分配 sort_order，重复项自动跳过。返回实际插入条数。 */
    @Transaction
    suspend fun addItems(playlistId: Int, items: List<PlaylistItemEntity>): Int {
        val startOrder = countByPlaylist(playlistId)
        val results = insertAll(
            items.mapIndexed { index, item ->
                item.copy(playlistId = playlistId, sortOrder = startOrder + index)
            }
        )
        return results.count { it > 0 }
    }

    /** 事务内整批替换条目顺序（拖拽排序落盘）。 */
    @Transaction
    suspend fun persistOrder(ordered: List<PlaylistItemEntity>) {
        ordered.forEachIndexed { index, item ->
            if (item.sortOrder != index) update(item.copy(sortOrder = index))
        }
    }
}
