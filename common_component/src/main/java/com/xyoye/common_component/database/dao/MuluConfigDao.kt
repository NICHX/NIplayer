package com.xyoye.common_component.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.xyoye.data_component.entity.MuluConfigEntity

@Dao
interface MuluConfigDao {

    @Query("SELECT * FROM scrape_mulu_config ORDER BY id ASC")
    fun getAll(): LiveData<MutableList<MuluConfigEntity>>

    @Query("SELECT * FROM scrape_mulu_config ORDER BY id ASC")
    suspend fun getAllSuspend(): MutableList<MuluConfigEntity>

    @Query("SELECT * FROM scrape_mulu_config WHERE mulu_type = (:muluType)")
    fun getByMuluType(muluType: String): LiveData<MutableList<MuluConfigEntity>>

    @Query("SELECT * FROM scrape_mulu_config WHERE mulu_type = (:muluType)")
    suspend fun getByMuluTypeSuspend(muluType: String): MutableList<MuluConfigEntity>

    @Query("SELECT * FROM scrape_mulu_config WHERE media_library_id = (:libraryId)")
    suspend fun getByLibraryId(libraryId: Int): List<MuluConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: MuluConfigEntity)

    @Query("DELETE FROM scrape_mulu_config WHERE id = (:id)")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM scrape_mulu_config WHERE media_library_id = (:libraryId)")
    suspend fun deleteByLibraryId(libraryId: Int)
}
