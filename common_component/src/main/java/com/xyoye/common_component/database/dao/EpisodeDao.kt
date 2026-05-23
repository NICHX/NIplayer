package com.xyoye.common_component.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xyoye.data_component.entity.EpisodeEntity

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episode WHERE media_id = (:mediaId) ORDER BY season_number, episode_number")
    suspend fun getEpisodesByMediaId(mediaId: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episode WHERE media_id = (:mediaId) AND season_number = (:seasonNumber) ORDER BY episode_number")
    suspend fun getEpisodesBySeason(mediaId: Int, seasonNumber: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episode WHERE id = (:id)")
    suspend fun getEpisodeById(id: Int): EpisodeEntity?

    @Query("SELECT COUNT(*) FROM episode WHERE media_id = (:mediaId)")
    suspend fun getEpisodeCountByMediaId(mediaId: Int): Int

    @Query("SELECT DISTINCT season_number FROM episode WHERE media_id = (:mediaId) ORDER BY season_number")
    suspend fun getSeasonsByMediaId(mediaId: Int): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg episodes: EpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM episode WHERE media_id = (:mediaId)")
    suspend fun deleteEpisodesByMediaId(mediaId: Int)

    @Query("DELETE FROM episode WHERE id = (:id)")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM episode WHERE file_path = (:filePath) LIMIT 1")
    suspend fun getEpisodeByFilePath(filePath: String): EpisodeEntity?
}