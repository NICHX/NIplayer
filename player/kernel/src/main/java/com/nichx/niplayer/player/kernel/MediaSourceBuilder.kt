package com.nichx.niplayer.player.kernel

import android.net.Uri
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.datasource.StorageDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [NxMediaSource] 构造工具。
 *
 * 桥接 :core:storage 的 [Storage] / [StorageFile] 与 :player:kernel 的 [NxMediaSource]。
 * 供文件浏览页（StorageFileViewModel）与播放列表连播（PlayerViewModel.playAtIndex）复用。
 *
 * 分流规则（按 [Storage.createPlayUrl] 返回值）：
 * - 非 null HTTP(S) URL（WebDAV）→ [NxMediaSource.Http]（携带 [Storage.getPlayHeaders] 认证头）
 * - 非 null 本地 URI（DocumentFile content://）→ [NxMediaSource.Local]
 * - null（SMB）→ [NxMediaSource.DataSource]（[StorageDataSource.Factory] 注入 media3）
 */
object MediaSourceBuilder {

    /**
     * 从 [Storage] + [StorageFile] 构造 [NxMediaSource]。
     *
     * IO 操作（[Storage.createPlayUrl]）切换到 [Dispatchers.IO]。
     *
     * BUG-P5 修复：DataSource 分支的 URI 从 `file.path`（相对路径，如 `films/movie.mkv`）
     * 改为绝对 URI `niplayer-storage://{storageId}/{path}`，让 media3 识别为合法 URI，
     * 避免 MediaSession 元数据显示异常及潜在内部兼容性问题。
     * DataSource.Factory 仍绑定真实 [Storage] + [StorageFile]，播放路径不受影响。
     */
    suspend fun buildMediaSource(
        storage: Storage,
        file: StorageFile,
        mediaId: String = "",
    ): NxMediaSource {
        val playUrl = withContext(Dispatchers.IO) { storage.createPlayUrl(file) }
        return when {
            playUrl != null && playUrl.startsWith("http", ignoreCase = true) ->
                // W-C3 修复：传递 trustAllCertificates，让播放器为自签证书 WebDAV 派生 trust-all client。
                // W-N7 修复：传递 mediaId 让 media3 MediaItem.mediaId 与应用层 uniqueKey 一致。
                NxMediaSource.Http(
                    uri = Uri.parse(playUrl),
                    mediaId = mediaId,
                    headers = storage.getPlayHeaders(),
                    trustAllCertificates = storage.trustAllCertificates,
                )

            playUrl != null ->
                NxMediaSource.Local(Uri.parse(playUrl), mediaId)

            else -> {
                // BUG-P5 修复：构造带 scheme 的绝对 URI，避免 media3 把相对路径误判
                val path = file.path.ifEmpty { file.name }.removePrefix("/")
                val absoluteUri = Uri.parse("niplayer-storage://${storage.library.id}/$path")
                // BUG-19+23 修复：将 storage 引用随 NxMediaSource 传递给 PlayerViewModel，
                // 由 PlayerViewModel 在切换或 onCleared 时统一关闭，避免播放/切集期间
                // 创建的 Storage 永不关闭导致 SMB 连接泄漏。
                NxMediaSource.DataSource(
                    factory = StorageDataSource.Factory(storage, file),
                    uri = absoluteUri,
                    mediaId = mediaId,
                    storage = storage,
                )
            }
        }
    }

    /**
     * 构造虚拟 [StorageFile]（仅含 path / name / fileSize），用于播放列表连播场景：
     * [PlaylistItem] 仅持有 filePath / fileName / fileSize，重建播放源时无需真实文件元信息，
     * 只需 path 供 [Storage.createPlayUrl] 或 [StorageDataSource.Factory] 定位文件。
     *
     * BUG-26 修复：增加 [fileSize] 参数（默认 0 保持向后兼容）。
     * 切集/历史恢复时传入真实 size，使 [StorageDataSource.bytesRemaining] 返回真实值，
     * media3 可立即显示进度条与总时长，无需等待 Extractor 解析 duration。
     */
    fun createVirtualFile(path: String, name: String, fileSize: Long = 0L): StorageFile =
        object : AbstractStorageFile(
            path = path,
            name = name,
            isDirectory = false,
            length = fileSize,
        ) {}
}
