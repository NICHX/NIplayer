package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nichx.niplayer.database.entity.PlaylistEntity
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

    /** 全量查询（suspend），用于备份导出。 */
    @Query("SELECT * FROM playlist_item ORDER BY playlist_id ASC, sort_order ASC, id ASC")
    suspend fun getAll(): List<PlaylistItemEntity>

    /** 追加条目：已存在（playlist_id, file_path）的自动去重，返回插入后的行 id（-1 表示重复跳过）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PlaylistItemEntity>): List<Long>

    @Update
    suspend fun update(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_item WHERE id = :itemId")
    suspend fun deleteById(itemId: Int)

    /** 按 id 批量查询（保持传入顺序，供跨歌单移动/复制使用）。 */
    @Query("SELECT * FROM playlist_item WHERE id IN (:itemIds)")
    suspend fun getByIds(itemIds: List<Int>): List<PlaylistItemEntity>

    @Query("DELETE FROM playlist_item WHERE id IN (:itemIds)")
    suspend fun deleteByIds(itemIds: List<Int>)

    /** 事务内复制歌单：新建歌单 + 全量复制条目，返回新歌单 id。 */
    @Transaction
    suspend fun duplicatePlaylist(sourceId: Int, newName: String, playlistDao: PlaylistDao): Int {
        val newId = playlistDao.insert(PlaylistEntity(name = newName)).toInt()
        val source = getByPlaylist(sourceId)
        addItems(newId, source)
        return newId
    }

    /** 事务内合并歌单：将 sourceId 的条目追加到 targetId（重复项自动跳过），返回实际新增条数。 */
    @Transaction
    suspend fun mergeInto(sourceId: Int, targetId: Int, playlistDao: PlaylistDao): Int {
        val source = getByPlaylist(sourceId)
        val added = addItems(targetId, source)
        playlistDao.touch(targetId, System.currentTimeMillis())
        return added
    }

    /** 事务内批量复制条目到目标歌单（重复项自动跳过），返回实际新增条数。 */
    @Transaction
    suspend fun copyItemsTo(targetId: Int, itemIds: List<Int>, playlistDao: PlaylistDao): Int {
        val source = getByIds(itemIds)
        val added = addItems(targetId, source)
        playlistDao.touch(targetId, System.currentTimeMillis())
        return added
    }

    /** 事务内批量移动条目到目标歌单（源歌单删除，目标重复项自动跳过），返回实际新增条数。 */
    @Transaction
    suspend fun moveItemsTo(
        targetId: Int,
        itemIds: List<Int>,
        sourcePlaylistId: Int,
        playlistDao: PlaylistDao,
    ): Int {
        val source = getByIds(itemIds)
        val added = addItems(targetId, source)
        deleteByIds(itemIds)
        playlistDao.touch(targetId, System.currentTimeMillis())
        playlistDao.touch(sourcePlaylistId, System.currentTimeMillis())
        return added
    }

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Int)

    /** 清空全表，用于恢复前清库。 */
    @Query("DELETE FROM playlist_item")
    suspend fun deleteAll()

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
