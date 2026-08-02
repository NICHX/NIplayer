package com.nichx.niplayer.storage.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.VideoEntity
import com.nichx.niplayer.datastore.VideoExtensionSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地视频扫描器：合并系统 MediaStore 与用户扩展目录，增量同步到 [video][VideoDao] 表。
 *
 * 迁移自旧仓库 `MediaResolver`（MediaStore 查询）+ `VideoScan`（扩展目录 File 递归遍历）
 * + `VideoStorage.deepRefresh`（合并去重 + 增量同步）。
 *
 * 数据来源：
 * 1. **MediaStore**（[queryMediaStore]）：系统索引的视频，`isExtend=false`，`fileId` 为
 *    MediaStore `_ID`（> 0 时 [com.nichx.niplayer.storage.impl.VideoStorage.createPlayUrl]
 *    返回 `content://` URI）
 * 2. **扩展目录**（[scanExtendFolders]）：用户手动添加的扫描目录，用 `File.listFiles()`
 *    递归遍历，`isExtend=true`，`fileId=0`（播放时走 `file://` URI）
 *
 * 合并去重：以 `filePath` 为唯一键，当同一文件同时被两个来源扫到时，**MediaStore 优先**
 * （`isExtend=false`），与旧仓库 `distinctBy` 取最后一个的行为一致。
 *
 * 增量同步：删除 DB 中已不存在的记录，插入新发现的记录，已存在的记录不更新（保留
 * `filter` / `subtitle_path` 等用户字段）。
 *
 * 视频扩展名识别：扩展目录扫描使用 [VideoExtensionSettings]（用户可配置），
 * MediaStore 查询不需要扩展名过滤（系统已按 video mime type 索引）。
 *
 * 触发时机：[com.nichx.niplayer.storage.impl.VideoStorage] 首次 listFiles 根目录
 * 且 video 表为空时自动触发，或扫描管理页手动触发。
 */
@Singleton
class VideoScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val extendFolderDao: ExtendFolderDao,
) {

    /** 执行全量扫描（MediaStore + 扩展目录）并同步到 DB，返回合并后的视频列表。 */
    suspend fun scan(): List<VideoEntity> = withContext(Dispatchers.IO) {
        val mediaStoreVideos = queryMediaStore()
        val extendVideos = scanExtendFolders()
        val merged = mergeAndDeduplicate(mediaStoreVideos, extendVideos)
        syncToDatabase(merged)
        merged
    }

    /**
     * 扫描指定扩展目录并同步到 DB。
     *
     * 用于扫描管理页添加新扩展目录时的即时入库：只扫描新目录 + MediaStore，
     * 合并去重后增量同步。
     *
     * @return 新目录中扫描到的视频数量
     */
    suspend fun scanExtendFolder(folderPath: String): Int = withContext(Dispatchers.IO) {
        val extendVideos = traverseFolder(File(folderPath))
        val mediaStoreVideos = queryMediaStore()
        val merged = mergeAndDeduplicate(mediaStoreVideos, extendVideos)
        syncToDatabase(merged)
        extendVideos.size
    }

    /**
     * 删除指定扩展目录：从 extend_folder 表删除，并删除该目录下所有 isExtend 视频，
     * 然后重新全量扫描（其他扩展目录 + MediaStore 的视频会重新入库）。
     */
    suspend fun removeExtendFolder(folderPath: String) = withContext(Dispatchers.IO) {
        extendFolderDao.delete(folderPath)
        videoDao.deleteByPathPrefix(folderPath)
        // 重新扫描剩余的扩展目录 + MediaStore
        val mediaStoreVideos = queryMediaStore()
        val remainingExtendVideos = scanExtendFolders()
        val merged = mergeAndDeduplicate(mediaStoreVideos, remainingExtendVideos)
        syncToDatabase(merged)
    }

    /** 查询系统 MediaStore.Video，返回 isExtend=false 的 VideoEntity 列表。 */
    private fun queryMediaStore(): List<VideoEntity> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
        )
        val videos = mutableListOf<VideoEntity>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val fileId = cursor.getLong(idCol)
                val filePath = cursor.getString(dataCol) ?: continue
                val folderPath = File(filePath).parent ?: continue
                videos.add(
                    VideoEntity(
                        fileId = fileId,
                        filePath = filePath,
                        folderPath = folderPath,
                        fileLength = cursor.getLong(sizeCol),
                        videoDuration = cursor.getLong(durationCol),
                        isExtend = false,
                    )
                )
            }
        }
        return videos
    }

    /** 遍历所有扩展目录（extend_folder 表），返回 isExtend=true 的 VideoEntity 列表。 */
    private suspend fun scanExtendFolders(): List<VideoEntity> {
        val extendFolders = extendFolderDao.getAll()
        return extendFolders.flatMap { traverseFolder(File(it.folderPath)) }
    }

    /**
     * 递归遍历文件夹，收集视频文件。
     *
     * 迁移自旧仓库 `VideoScan.traverse`。用 [VideoExtensionSettings.isVideoFile] 判断
     * 扩展名，时长通过 [MediaMetadataRetriever] 提取（异常时返回 0）。
     */
    private fun traverseFolder(folder: File): List<VideoEntity> {
        if (!folder.exists() || !folder.canRead()) return emptyList()
        val results = mutableListOf<VideoEntity>()
        folder.walkTopDown().forEach { file ->
            if (file.isFile && VideoExtensionSettings.isVideoFile(file.absolutePath)) {
                results.add(
                    VideoEntity(
                        fileId = 0,
                        filePath = file.absolutePath,
                        folderPath = file.parentFile?.absolutePath.orEmpty(),
                        videoDuration = getVideoDuration(file),
                        fileLength = file.length(),
                        isExtend = true,
                    )
                )
            }
        }
        return results
    }

    /** 用 MediaMetadataRetriever 提取视频时长（毫秒），异常返回 0。 */
    private fun getVideoDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 合并 MediaStore 与扩展目录扫描结果，以 filePath 去重。
     *
     * MediaStore 优先：同一文件同时被两个来源扫到时，保留 MediaStore 的（isExtend=false，
     * fileId>0，播放走 content:// URI 更可靠）。
     */
    private fun mergeAndDeduplicate(
        mediaStoreVideos: List<VideoEntity>,
        extendVideos: List<VideoEntity>,
    ): List<VideoEntity> {
        val mediaStorePaths = mediaStoreVideos.map { it.filePath }.toSet()
        val filteredExtend = extendVideos.filter { it.filePath !in mediaStorePaths }
        return mediaStoreVideos + filteredExtend
    }

    /** 增量同步：删除 DB 中已不存在的记录，插入新发现的记录。 */
    private suspend fun syncToDatabase(scanned: List<VideoEntity>) {
        val existing = videoDao.getAll()
        val existingPaths = existing.map { it.filePath }.toSet()
        val scannedPaths = scanned.map { it.filePath }.toSet()

        val toDelete = existing.mapNotNull { v ->
            v.filePath.takeIf { it !in scannedPaths }
        }
        if (toDelete.isNotEmpty()) videoDao.deleteByPaths(toDelete)

        val toInsert = scanned.filter { it.filePath !in existingPaths }
        if (toInsert.isNotEmpty()) videoDao.insert(*toInsert.toTypedArray())
    }
}
