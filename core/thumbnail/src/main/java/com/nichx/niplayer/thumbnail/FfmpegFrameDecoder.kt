package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.util.Log

/**
 * 软解结果。
 *
 * @param bitmap 解码出的 BGRA 位图
 * @param hdrKind 帧的 HDR 类型（[HDR_KIND_NONE] / [HDR_KIND_PQ] / [HDR_KIND_HLG]）——
 *   ffmpeg 直解出的是原始码值、无系统 tone map，调用方需据此做色调映射补偿，
 *   否则 HDR 片源的预览/缩略图会明显发暗
 */
internal data class FfmpegFrame(val bitmap: Bitmap, val hdrKind: Int)

/** JNI 回传的 HDR 类型标记（与 native 侧 `color_trc` 判定一致）。 */
internal const val HDR_KIND_NONE = 0

/** HDR10 / PQ（SMPTE ST 2084）。 */
internal const val HDR_KIND_PQ = 1

/** HLG（ARIB STD-B67）。 */
internal const val HDR_KIND_HLG = 2

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

    /**
     * 软解一帧。
     *
     * @param startMs 目标时间点（ms，>=0 生效）；与 [fraction] 二选一
     * @param fraction 目标位置占时长的比例（>0 生效）；与 [startMs] 二选一
     * @param pickNearest true = 取"第一个不早于目标时刻"的帧（拖动预览，精确到帧）；
     *   false = 在目标时刻之后的一小段窗口里取最亮帧（缩略图，跳过黑帧）
     */
    fun decodeFirstFrame(
        source: MediaDataSource,
        targetWidth: Int,
        startMs: Long = -1L,
        fraction: Double = -1.0,
        pickNearest: Boolean = false,
    ): FfmpegFrame? {
        if (!available) return null
        return try {
            val raw = nativeDecode(source, targetWidth, startMs, fraction, if (pickNearest) 1 else 0)
                ?: return null
            // 头部 3 个 int：宽、高、HDR 类型；其后为 BGRA 像素
            if (raw.size < 4) return null
            val width = raw[0]
            val height = raw[1]
            val hdrKind = raw[2]
            val count = width * height
            if (width <= 0 || height <= 0 || raw.size < count + 3) return null
            val pixels = IntArray(count)
            System.arraycopy(raw, 3, pixels, 0, count)
            FfmpegFrame(
                bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888),
                hdrKind = hdrKind,
            )
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
        pickNearest: Int,
    ): IntArray?
}
