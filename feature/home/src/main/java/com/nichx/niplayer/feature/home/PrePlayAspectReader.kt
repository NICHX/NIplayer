package com.nichx.niplayer.feature.home

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.thumbnail.ThumbnailManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 播放前横竖屏预读（无状态共享工具）。
 *
 * 读取视频显示宽高比（width/height，已含旋转校正），供"自动方向"模式在进入播放器前
 * 直接锁定横/竖屏，避免"先横屏再旋转"。多级回退：
 * 1. **本地缩略图缓存**：缩略图是视频帧，其宽高比即视频显示比例（MediaMetadataRetriever
 *    取帧已应用旋转元数据），且为本地 jpg 纯文件 IO，几乎零开销；
 * 2. **MediaMetadataRetriever**：读视频编码分辨率 + 旋转元数据（API 28+），本地/远程均支持。
 *
 * 供文件浏览（[StorageFileViewModel]）与首页历史 / 快速访问（[HistoryStartProvider]）复用。
 * 任一环节失败返回 null，由播放页回退到等 media3 videoSize 后再定方向。
 *
 * 采用普通 `object` 而非 Hilt 注入类型：[context] / [thumbnailManager] 由调用方传入，
 * 避免将本工具作为构造入参引入而增加模块间的注入接线。
 */
object PrePlayAspectReader {

    /** 根据存储源与文件读取视频显示宽高比；失败或超时返回 null。 */
    suspend fun read(
        context: Context,
        thumbnailManager: ThumbnailManager,
        storage: Storage,
        libraryId: Int,
        file: StorageFile,
    ): Float? = withTimeoutOrNull(PRE_PLAY_ASPECT_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            // 1) 优先本地缩略图缓存（纯本地 IO，不触发网络）
            thumbnailManager.getCachedThumbnailPath(libraryId, file.path)?.let { path ->
                readImageAspectRatio(File(path))?.let { return@withContext it }
            }
            // 2) 回退 MediaMetadataRetriever（本地/远程）
            readVideoAspectRatio(context, storage, file)
        }
    }

    /** 读取本地图片文件的宽高比（inJustDecodeBounds，不加载像素）。失败返回 null。 */
    private fun readImageAspectRatio(file: File): Float? = try {
        if (!file.exists() || file.length() == 0L) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            opts.outWidth.toFloat() / opts.outHeight
        } else null
    } catch (_: Exception) {
        null
    }

    /**
     * 用 MediaMetadataRetriever 读取视频分辨率并计算显示宽高比。
     *
     * 数据源选择复用缩略图生成逻辑：Local/WebDAV 的播放 URL 直接交给 retriever，
     * SMB 通过 [Storage.openMediaDataSource] 提供随机读。竖拍视频按旋转元数据
     * （API 28+ 的 METADATA_KEY_VIDEO_ROTATION）交换宽高，与 media3 videoSize.aspectRatio
     * 语义一致。
     */
    private suspend fun readVideoAspectRatio(context: Context, s: Storage, file: StorageFile): Float? {
        var retriever: MediaMetadataRetriever? = null
        var dataSource: MediaDataSource? = null
        try {
            val url = s.createPlayUrl(file)
            retriever = MediaMetadataRetriever()
            when {
                url != null && (url.startsWith("file", ignoreCase = true) || url.startsWith("content", ignoreCase = true)) ->
                    retriever.setDataSource(context, Uri.parse(url))
                url != null && url.startsWith("http", ignoreCase = true) ->
                    retriever.setDataSource(url, s.getPlayHeaders())
                else -> {
                    dataSource = s.openMediaDataSource(file) ?: return null
                    retriever.setDataSource(dataSource)
                }
            }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (width <= 0 || height <= 0) return null
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } else 0
            return if (rotation % 180 == 0) width.toFloat() / height else height.toFloat() / width
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { retriever?.release() }
            runCatching { dataSource?.close() }
        }
    }

    private const val PRE_PLAY_ASPECT_TIMEOUT_MS = 3000L
}