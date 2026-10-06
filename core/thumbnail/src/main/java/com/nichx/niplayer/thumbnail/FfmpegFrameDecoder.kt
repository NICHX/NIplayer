package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.util.Log

/**
 * ffmpeg 取帧（JNI）：把远程 [MediaDataSource] 桥接给 libavformat/libavcodec，软解一帧。
 *
 * 覆盖常见容器（matroska、mov(mp4)、mpegts、avi、flv…），走索引 seek 读取量小，且**不占用**
 * 系统 [android.media.MediaMetadataRetriever] 的全局取帧资源池。
 *
 * 命名：早期仅用于 MKV（两跳 SeekHead→Cues 索引），故原名 `MkvFfmpegDecoder`；现为通用 ffmpeg 取帧器。
 */
internal object FfmpegFrameDecoder {

    private const val TAG = "FfmpegFrameDecoder"

    private val available: Boolean = try {
        System.loadLibrary("ffmpegJNI")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "libffmpegJNI unavailable: ${t.message}")
        false
    }

    val isAvailable: Boolean get() = available

    fun decodeFirstFrame(
        source: MediaDataSource,
        targetWidth: Int,
        startMs: Long = -1L,
        fraction: Double = -1.0,
    ): Bitmap? {
        if (!available) return null
        return try {
            val raw = nativeDecode(source, targetWidth, startMs, fraction) ?: return null
            if (raw.size < 3) return null
            val width = raw[0]
            val height = raw[1]
            val count = width * height
            if (width <= 0 || height <= 0 || raw.size < count + 2) return null
            val pixels = IntArray(count)
            System.arraycopy(raw, 2, pixels, 0, count)
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed: ${t.message}")
            null
        }
    }

    private external fun nativeDecode(
        source: MediaDataSource,
        targetWidth: Int,
        startMs: Long,
        fraction: Double,
    ): IntArray?
}
