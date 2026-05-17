package com.xyoye.player_component.utils

import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.annotation.RequiresApi
import com.xyoye.common_component.config.UserConfig
import com.xyoye.common_component.config.PlayHistorySyncConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.extension.resumeWhenAlive
import com.xyoye.common_component.network.repository.AnimeRepository
import com.xyoye.common_component.network.repository.ResourceRepository
import com.xyoye.common_component.source.base.BaseVideoSource
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.common_component.utils.MediaUtils
import com.xyoye.common_component.utils.PathHelper
import com.xyoye.common_component.utils.SupervisorScope
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.player.surface.InterSurfaceView
import com.xyoye.player_component.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.Date

/**
 * Created by xyoye on 2022/1/15.
 */

object PlayRecorder {
    private const val HEIGHT = 150f

    fun recordProgress(source: BaseVideoSource, position: Long, duration: Long) {
        SupervisorScope.IO.launch {
            val uniqueKey = source.getUniqueKey()
            val storageId = source.getStorageId()
            val storagePath = source.getStoragePath()
            val historyDao = DatabaseManager.instance.getPlayHistoryDao()

            val existingHistory = when {
                storagePath != null && storagePath.isNotEmpty() && storageId > 0 ->
                    historyDao.getPlayHistoryByPath(storagePath, storageId)
                uniqueKey.isNotEmpty() && storageId > 0 ->
                    historyDao.getPlayHistory(uniqueKey, storageId)
                else -> null
            }

            if (existingHistory != null) {
                historyDao.update(existingHistory.copy(
                    videoName = source.getVideoTitle(),
                    url = source.getVideoUrl(),
                    videoPosition = position,
                    videoDuration = duration,
                    playTime = Date(),
                    subtitlePath = source.getSubtitlePath(),
                    httpHeader = JsonHelper.toJson(source.getHttpHeader()),
                    storagePath = source.getStoragePath(),
                    audioPath = source.getAudioPath()
                ))
            } else {
                val history = PlayHistoryEntity(
                    0,
                    source.getVideoTitle(),
                    source.getVideoUrl(),
                    source.getMediaType(),
                    position,
                    duration,
                    Date(),
                    null,
                    null,
                    source.getSubtitlePath(),
                    null,
                    -1,
                    JsonHelper.toJson(source.getHttpHeader()),
                    uniqueKey,
                    source.getStoragePath(),
                    storageId,
                    source.getAudioPath()
                )
                historyDao.insert(history)
            }

            // 上报剧集播放到云端
            recordToCloud(source)

            // 触发播放记录同步
            triggerSyncIfNeeded(source.getMediaType())

            //部分视频无法获取到视频时长，播放后再更新时长
            if (source.getMediaType() == MediaType.LOCAL_STORAGE) {
                DatabaseManager.instance
                    .getVideoDao()
                    .updateDuration(duration, source.getVideoUrl())
            }
        }
    }

    fun recordImage(key: String, renderView: InterSurfaceView?) {
        val view = renderView?.getView()
            ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        SupervisorScope.IO.launch {
            val bitmap = try {
                generateRenderImage(view)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } ?: return@launch

            val bitmapFile = File(PathHelper.getVideoCoverDirectory(), key)
            MediaUtils.saveImage(bitmapFile, bitmap)
            bitmap.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun generateRenderImage(view: View, imageSize: Point? = null): Bitmap? {
        when (view) {
            is SurfaceView -> {
                return recordSurfaceView(view, imageSize)
            }

            is TextureView -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && view.surfaceTexture != null) {
                    return recordTextureView(view, imageSize)
                }
            }

            is VLCVideoLayout -> {
                val textureView = view.findViewById<TextureView>(R.id.texture_video)
                val surfaceView = view.findViewById<SurfaceView>(R.id.surface_video)
                if (textureView != null && textureView.surfaceTexture != null) {
                    return recordTextureView(textureView, imageSize)
                } else if (surfaceView != null) {
                    return recordSurfaceView(surfaceView, imageSize)
                }
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun recordSurfaceView(
        surfaceView: SurfaceView,
        imageSize: Point?
    ) = suspendCancellableCoroutine {
        val recordBitmap = createBitmap(surfaceView, imageSize)
        if (recordBitmap == null) {
            it.resumeWhenAlive(null)
            return@suspendCancellableCoroutine
        }

        val surface = surfaceView.holder.surface
        if (surface.isValid.not()) {
            it.resumeWhenAlive(null)
            return@suspendCancellableCoroutine
        }

        PixelCopy.request(surface, recordBitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                it.resumeWhenAlive(recordBitmap)
            } else {
                it.resumeWhenAlive(null)
            }
        }, Handler(Looper.getMainLooper()))
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun recordTextureView(
        textureView: TextureView,
        imageSize: Point?
    ) = suspendCancellableCoroutine {
        val recordBitmap = createBitmap(textureView, imageSize)
        if (recordBitmap == null) {
            it.resumeWhenAlive(null)
            return@suspendCancellableCoroutine
        }

        val surfaceTexture = textureView.surfaceTexture
        if (surfaceTexture == null) {
            it.resumeWhenAlive(null)
            return@suspendCancellableCoroutine
        }

        val surface = Surface(textureView.surfaceTexture)
        if (surface.isValid.not()) {
            it.resumeWhenAlive(null)
            return@suspendCancellableCoroutine
        }

        PixelCopy.request(surface, recordBitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                it.resumeWhenAlive(recordBitmap)
            } else {
                it.resumeWhenAlive(null)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun createBitmap(view: View, imageSize: Point?): Bitmap? {
        if (imageSize != null && imageSize.x > 0 && imageSize.y > 0) {
            return Bitmap.createBitmap(
                imageSize.x,
                imageSize.y,
                Bitmap.Config.ARGB_8888
            )
        }

        if (view.width <= 0 || view.height <= 0) {
            return null
        }

        val height: Float
        val width: Float
        if (view.height > view.width) {
            width = HEIGHT
            height = width / view.width * view.height
        } else {
            height = HEIGHT
            width = height / view.height * view.width
        }

        return Bitmap.createBitmap(
            width.toInt(),
            height.toInt(),
            Bitmap.Config.RGB_565
        )
    }

    /**
     * 上报剧集播放记录到云端
     */
    private suspend fun recordToCloud(videoSource: BaseVideoSource) {
        // 弹幕功能已移除，云端上报功能也已移除
    }

    private fun triggerSyncIfNeeded(mediaType: MediaType) {
        if (!PlayHistorySyncConfig.enabled) return
        val syncTypes = setOf(
            MediaType.SMB_SERVER,
            MediaType.FTP_SERVER,
            MediaType.WEBDAV_SERVER,
            MediaType.ALSIT_STORAGE
        )
        if (mediaType !in syncTypes) return
        SupervisorScope.IO.launch {
            try {
                com.xyoye.common_component.utils.PlayHistorySyncManager.sync()
            } catch (_: Exception) {
            }
        }
    }
}