package com.xyoye.common_component.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.xyoye.data_component.entity.ScrapeMediaEntity

@Dao
interface ScrapeMediaDao {

    @Query("SELECT * FROM scrape_media ORDER BY update_time DESC")
    fun getAll(): LiveData<MutableList<ScrapeMediaEntity>>

    @Query("SELECT * FROM scrape_media WHERE media_type = (:mediaType) ORDER BY update_time DESC")
    fun getByMediaType(mediaType: String): LiveData<MutableList<ScrapeMediaEntity>>

    @Query("SELECT * FROM scrape_media WHERE media_type = (:mediaType) ORDER BY update_time DESC")
    suspend fun getByMediaTypeSuspend(mediaType: String): MutableList<ScrapeMediaEntity>

    @Query("SELECT * FROM scrape_media WHERE id = (:id)")
    suspend fun getById(id: Int): ScrapeMediaEntity?

    @Query("SELECT * FROM scrape_media WHERE path = (:path) AND media_type = (:mediaType)")
    suspend fun getByPath(path: String, mediaType: String): ScrapeMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: ScrapeMediaEntity)

    @Update
    suspend fun update(vararg entities: ScrapeMediaEntity)

    @Query("DELETE FROM scrape_media WHERE id = (:id)")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM scrape_media WHERE media_type = (:mediaType)")
    suspend fun deleteByMediaType(mediaType: String)
}
