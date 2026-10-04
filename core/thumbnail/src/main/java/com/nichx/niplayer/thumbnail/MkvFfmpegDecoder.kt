package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.util.Log

internal object MkvFfmpegDecoder {

    private const val TAG = "MkvFfmpegDecoder"

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
