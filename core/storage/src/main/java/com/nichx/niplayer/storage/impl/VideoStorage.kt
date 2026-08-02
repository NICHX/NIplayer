package com.nichx.niplayer.storage.impl

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.nichx.niplayer.database.bean.FolderBean
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.entity.VideoEntity
import com.nichx.niplayer.storage.AbstractStorage
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.scanner.VideoScanner
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * [Storage] 的本地视频库实现，对应 [com.nichx.niplayer.database.enums.MediaType.LOCAL_STORAGE]。
 *
 * 替代旧仓库 `common_component/storage/impl/VideoStorage.kt`。
 *
 * 与网络存储（SMB/WebDAV）的关键差异：
 * - **数据来源**：Room `video` 表（缓存 MediaStore 扫描结果），而非实时网络请求
 * - **listFiles**：根目录返回 [FolderBean] 聚合的文件夹列表，子目录返回 [VideoEntity] 列表
 * - **createPlayUrl**：[VideoStorageFile.fileId] > 0 时返回 `content://media/external/video/media/{id}`
 *   （media3 ContentDataSource 直接播放，兼容 Android 11+ 分区存储）；否则返回 `file://{filePath}`
 * - **自动扫描**：首次 listFiles 根目录且 video 表为空时，触发 [VideoScanner.scan]
 *
 * 系统项特性：对应 [MediaLibraryEntity] 由 [com.nichx.niplayer.NiApplication] 启动时自动插入，
 * url 固定为 [MediaStore.Video.Media.EXTERNAL_CONTENT_URI]，UI 层不可删除。
 */
class VideoStorage(
    private val context: Context,
    library: MediaLibraryEntity,
    private val videoDao: VideoDao,
    private val scanner: VideoScanner,
) : AbstractStorage(library) {

    override suspend fun listFiles(directory: StorageFile): List<StorageFile> {
        if (isRoot(directory) && videoDao.getAll().isEmpty()) {
            scanner.scan()
        }
        return if (isRoot(directory)) {
            videoDao.getFolderByFilter().map { it.toStorageFile() }
        } else {
            val folderPath = (directory as? VideoStorageFile)?.filePath ?: directory.path
            videoDao.getVideoInFolder(folderPath).map { it.toStorageFile() }
        }
    }

    override suspend fun openInputStream(file: StorageFile): InputStream {
        val vsf = file as? VideoStorageFile
            ?: throw FileNotFoundException("Not a VideoStorageFile: ${file.path}")
        return if (vsf.fileId > 0) {
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                vsf.fileId,
            )
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open input stream: ${vsf.filePath}")
        } else {
            FileInputStream(vsf.filePath)
        }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? {
        // 快速路径：file 来自 listFiles() 时为 VideoStorageFile，直接用其 fileId/filePath
        val vsf = file as? VideoStorageFile
        if (vsf != null) {
            return if (vsf.fileId > 0) {
                ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    vsf.fileId,
                ).toString()
            } else {
                "file://${vsf.filePath}"
            }
        }
        // 兜底路径：file 为虚拟 StorageFile（仅含 path/name），来自
        // PlayStarter.startFromHistory / startFromQuickAccess（首页英雄卡、最近播放、
        // 播放历史、快速访问入口）与 PlayerViewModel.playAtIndex（切集重建源）。
        // 通过 file.path 查询 video 表还原 fileId，避免 createPlayUrl 返回 null 触发
        // NxMediaSource.DataSource 分支（LocalStorage 未实现 StorageDataSource 模式，
        // 会导致播放失败）。
        val filePath = file.path.ifEmpty { return null }
        val video = videoDao.getVideo(filePath)
        return if (video != null && video.fileId > 0) {
            ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                video.fileId,
            ).toString()
        } else {
            "file://$filePath"
        }
    }

    override suspend fun fileExists(path: String): Boolean = File(path).exists()

    override suspend fun deleteFile(file: StorageFile): Boolean = false

    override suspend fun testConnection(): Boolean = true

    private fun isRoot(file: StorageFile): Boolean =
        file === StorageFactory.ROOT || file.path.isEmpty()

    private fun FolderBean.toStorageFile() = VideoStorageFile(
        path = folderPath,
        name = File(folderPath).name.ifEmpty { folderPath },
        isDirectory = true,
        filePath = folderPath,
    )

    private fun VideoEntity.toStorageFile() = VideoStorageFile(
        path = filePath,
        name = File(filePath).name,
        isDirectory = false,
        length = fileLength,
        filePath = filePath,
        fileId = fileId,
    )
}
