package com.nichx.niplayer.feature.home

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.thumbnail.ThumbnailManager
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
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

    /**
     * 阻塞元数据读取专用线程池（有界、守护线程）。
     *
     * [MediaMetadataRetriever] 读远程（SMB）元数据是**阻塞 JNI 调用**，协程取消无法中断它：
     * 直接在调用协程里跑，超时形同虚设，点击播放会一直停在「识别方向中…」。
     * 因此把该工作投递到本线程池，只对「等待结果」施加超时。
     */
    private val executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "preplay-aspect").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    /**
     * 根据存储源与文件读取视频显示宽高比；失败或超时返回 null。
     *
     * 超时会**立即返回 null**（不再等待阻塞调用跑完），由播放页回退到 media3 videoSize 判定方向，
     * 从而保证点击播放后 UI 不会被远程元数据读取长期卡住。
     */
    suspend fun read(
        context: Context,
        thumbnailManager: ThumbnailManager,
        storage: Storage,
        libraryId: Int,
        file: StorageFile,
    ): Float? {
        val startedAt = SystemClock.elapsedRealtime()

        // 1) 优先本地缩略图缓存（纯本地 IO，不触发网络，通常 <1ms）
        val cached = withContext(Dispatchers.IO) {
            thumbnailManager.getCachedThumbnailPath(libraryId, file.path)?.let { path ->
                readImageAspectRatio(File(path))
            }
        }
        if (cached != null) {
            Log.d(TAG, "aspect cache-hit: ${file.name} = $cached ${SystemClock.elapsedRealtime() - startedAt}ms")
            return cached
        }

        // 2) 回退 MediaMetadataRetriever（本地/远程）。工作挂在独立 scope 上，超时只放弃「等待」，
        //    被放弃的任务自行跑完并在 finally 中释放 retriever / dataSource。
        val deferred = scope.async { readVideoAspectRatio(context, storage, file) }
        val aspect = withTimeoutOrNull(PRE_PLAY_ASPECT_TIMEOUT_MS) { deferred.await() }
        if (aspect == null) deferred.cancel()
        Log.d(
            TAG,
            "aspect retriever: ${file.name} = $aspect ${SystemClock.elapsedRealtime() - startedAt}ms" +
                if (aspect == null) " (timeout/失败，交由播放器判定)" else "",
        )
        return aspect
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { retriever?.release() }
            runCatching { dataSource?.close() }
        }
    }

    private const val TAG = "PrePlayAspectReader"

    private const val PRE_PLAY_ASPECT_TIMEOUT_MS = 3000L
}
