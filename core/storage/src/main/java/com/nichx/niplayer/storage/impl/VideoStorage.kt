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
        // 本地视频库自动重扫：DB 为空时全量扫描；非空时按节流间隔做轻量 MediaStore
        // 增量刷新，使新下载/被移除的视频在打开文件夹时自动同步（无需手动刷新）。
        val dbEmpty = videoDao.getAll().isEmpty()
        when {
            dbEmpty -> scanner.scan()
            shouldAutoRefresh() -> scanner.refreshMediaStore()
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

    /**
     * 删除本地视频文件（仅文件，不含本地视频库的虚拟文件夹）。
     *
     * 双路径删除：
     * 1. **MediaStore**：`fileId > 0` 时通过 contentResolver 删除系统索引项（分区存储下可删
     *    应用自有/可写媒体）
     * 2. **物理文件**：MediaStore 删除失败或无 fileId（扩展目录/应用自有文件）时直接 `File.delete()`
     *
     * 删除成功后同步清除 `video` 表记录，避免留幽灵条目。目录一律返回 false。
     */
    override suspend fun deleteFile(file: StorageFile): Boolean {
        if (file.isDirectory) return false
        val path = (file as? VideoStorageFile)?.filePath ?: file.path
        if (path.isEmpty()) return false

        var deleted = false
        val vsf = file as? VideoStorageFile
        if (vsf != null && vsf.fileId > 0) {
            val uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                vsf.fileId,
            )
            try {
                deleted = context.contentResolver.delete(uri, null, null) > 0
            } catch (_: Exception) {
                deleted = false
            }
        }
        if (!deleted) {
            deleted = runCatching { File(path).delete() }.getOrDefault(false)
        }
        if (deleted) {
            runCatching { videoDao.deleteByPath(path) }
        }
        return deleted
    }

    /**
     * 系统 MediaStore 授权删除（createDeleteRequest）成功后的终结处理：
     * 清除这些路径对应的 `video` 表记录，避免残留幽灵条目。
     */
    suspend fun finalizeMediaDelete(paths: List<String>) {
        val valid = paths.filter { it.isNotEmpty() }
        if (valid.isNotEmpty()) {
            runCatching { videoDao.deleteByPaths(valid) }
        }
    }

    /**
     * 强制增量重扫（下拉刷新用）：重置节流并立即执行 [VideoScanner.refreshMediaStore]，
     * 使新下载的视频在下拉刷新时立即可见（不受自动重扫的节流间隔限制）。
     */
    suspend fun forceRefresh() {
        lastAutoRefreshAt = System.currentTimeMillis()
        scanner.refreshMediaStore()
    }

    override suspend fun testConnection(): Boolean = true

    private fun isRoot(file: StorageFile): Boolean =
        file === StorageFactory.ROOT || file.path.isEmpty()

    /** 自动重扫节流：距上次刷新不足阈值时跳过，避免频繁打开目录触发重扫造成卡顿。 */
    private var lastAutoRefreshAt = 0L
    private fun shouldAutoRefresh(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshAt < AUTO_REFRESH_INTERVAL_MS) return false
        lastAutoRefreshAt = now
        return true
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }

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
