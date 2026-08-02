package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.bean.FolderBean
import com.nichx.niplayer.database.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 本地视频扫描结果 Dao，迁移自旧仓库 VideoDao。
 * `getAllFolder` 由 LiveData 改为 Flow（参见方案硬约束：Coroutines + StateFlow 全面替代 LiveData）。
 */
@Dao
interface VideoDao {

    @Query("SELECT * FROM video")
    suspend fun getAll(): MutableList<VideoEntity>

    @Query("SELECT * FROM video WHERE folder_path = (:folderPath) AND filter = 0")
    suspend fun getVideoInFolder(folderPath: String): List<VideoEntity>

    @Query("SELECT * FROM video WHERE file_path = (:filePath)")
    suspend fun getVideo(filePath: String): VideoEntity?

    @Query("SELECT * FROM video WHERE folder_path = (SELECT folder_path FROM video WHERE file_path = (:filePath))")
    suspend fun getFolderVideoByFilePath(filePath: String): MutableList<VideoEntity>

    @Query("SELECT * FROM video WHERE folder_path = (:folderPath)")
    suspend fun getVideoInFolderSuspend(folderPath: String): MutableList<VideoEntity>

    @Query(
        "SELECT video.folder_path, COUNT(*) AS file_count, filter_table.filter " +
                "FROM video " +
                "LEFT JOIN ( SELECT folder_path, filter FROM video WHERE filter = (:isFilter) GROUP BY folder_path) AS filter_table " +
                "ON filter_table.folder_path = video.folder_path " +
                "GROUP BY video.folder_path"
    )
    fun getAllFolder(isFilter: Boolean = true): Flow<MutableList<FolderBean>>

    @Query(
        "SELECT video.folder_path, COUNT(*) AS file_count, video.filter " +
                "FROM video " +
                "LEFT JOIN (SELECT folder_path FROM video WHERE filter = (:notFilter) GROUP BY folder_path) AS filter_table " +
                "ON filter_table.folder_path = video.folder_path WHERE filter_table.folder_path IS NULL " +
                "GROUP BY video.folder_path"
    )
    suspend fun getFolderByFilter(notFilter: Boolean = true): MutableList<FolderBean>

    @Query("SELECT * FROM video WHERE filter = 0 AND file_path LIKE (:keyword)")
    suspend fun searchVideo(keyword: String): List<VideoEntity>

    @Query("SELECT * FROM video WHERE file_path = (:filePath)")
    suspend fun findVideoByPath(filePath: String): VideoEntity?

    @Query("SELECT * FROM video WHERE filter = 0 AND folder_path = (:folderPath) AND file_path LIKE (:keyword)")
    suspend fun searchVideoInFolder(keyword: String, folderPath: String?): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: VideoEntity)

    @Query("DELETE FROM video WHERE file_path = (:filePath)")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM video WHERE file_path in (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    /** 删除指定目录前缀下的所有视频（用于移除扩展目录时清理该目录下的扫描结果）。 */
    @Query("DELETE FROM video WHERE file_path LIKE (:folderPath) || '/%'")
    suspend fun deleteByPathPrefix(folderPath: String)

    @Query("DELETE FROM video WHERE extend = (:isExtend)")
    suspend fun deleteExtend(isExtend: Boolean = true)

    @Query("DELETE FROM video")
    suspend fun deleteAll()

    @Query("UPDATE video SET subtitle_path = (:subtitlePath) WHERE file_path = (:filePath)")
    suspend fun updateSubtitle(filePath: String, subtitlePath: String?)

    @Query("UPDATE video SET filter = (:filter) WHERE folder_path = (:folderPath)")
    suspend fun updateFolderFilter(filter: Boolean, folderPath: String)

    @Query("UPDATE video SET video_duration = (:duration) WHERE file_path = (:filePath)")
    suspend fun updateDuration(duration: Long, filePath: String)
}
