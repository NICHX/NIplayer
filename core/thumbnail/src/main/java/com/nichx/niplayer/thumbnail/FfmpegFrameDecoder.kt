package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

/**
 * 软解结果。
 *
 * @param bitmap 解码出的 BGRA 位图
 * @param hdrKind 帧的 HDR 类型（[HDR_KIND_NONE] / [HDR_KIND_PQ] / [HDR_KIND_HLG]）——
 *   ffmpeg 直解出的是原始码值、无系统 tone map，调用方需据此做色调映射补偿，
 *   否则 HDR 片源的预览/缩略图会明显发暗
 */
internal data class FfmpegFrame(val bitmap: Bitmap, val hdrKind: Int)

/** 一次取帧的结果：成功给 [frame]，失败给 [error]（见 `FfmpegFrameDecoder.ERR_*`）。 */
internal data class FfmpegDecodeResult(val frame: FfmpegFrame?, val error: Int) {

    /** 该文件确实解不出（容器/编码器不识别）：可记入「ffmpeg 不支持」名单，后续直接走 retriever。 */
    val isUnsupported: Boolean get() = error == FfmpegFrameDecoder.ERR_UNSUPPORTED

    /** 被主动取消（用户松手 / 关闭会话）：不代表文件有问题，不得据此拉黑。 */
    val isCancelled: Boolean get() = error == FfmpegFrameDecoder.ERR_CANCELLED
}

/** [FfmpegFrameDecoder.openSession] 的结果。 */
internal data class FfmpegSessionOpen(val session: FfmpegFrameDecoder.Session?, val error: Int)

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
 * 两种用法：
 * - [decodeFirstFrame]：一次性取帧（缩略图、退出播放时覆盖缩略图）；
 * - [openSession] + [Session.decode]：**常驻会话**，把 `avformat_open_input` / `find_stream_info`
 *   这些一次性成本摊到多次取帧上，供拖动进度条的实时预览高频取帧使用。
 *
 * 命名：早期仅用于 MKV（两跳 SeekHead→Cues 索引），故原名 `MkvFfmpegDecoder`；现为通用 ffmpeg 取帧器。
 */
internal object FfmpegFrameDecoder {

    private const val TAG = "FfmpegFrameDecoder"

    /** 解码成功（或无错）。 */
    const val ERR_NONE = 0

    /** 该文件确实解不出（容器/编码器不识别、或解码器全线拒收）：可记入「不支持」名单。 */
    const val ERR_UNSUPPORTED = 1

    /** 被主动取消：不得据此判定文件不支持。 */
    const val ERR_CANCELLED = 2

    /** IO/临时失败（打开成功但没解出帧）：可重试，不拉黑。 */
    const val ERR_IO = 3

    private val available: Boolean = try {
        System.loadLibrary("ffmpegJNI")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "libffmpegJNI unavailable: ${t.message}")
        false
    }

    val isAvailable: Boolean get() = available

    /**
     * 一次性取帧。
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
    ): FfmpegDecodeResult {
        if (!available) return FfmpegDecodeResult(null, ERR_UNSUPPORTED)
        val errorOut = IntArray(1)
        val raw = try {
            nativeDecode(source, targetWidth, startMs, fraction, if (pickNearest) 1 else 0, errorOut)
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed: ${t.message}")
            null
        }
        return FfmpegDecodeResult(toFrame(raw), errorOut[0])
    }

    /**
     * 打开常驻取帧会话；失败时 [FfmpegSessionOpen.error] 给出原因。
     *
     * 会话持有全局引用（MediaDataSource + 读缓冲），用完必须 [Session.close]，否则泄漏。
     */
    fun openSession(source: MediaDataSource, targetWidth: Int): FfmpegSessionOpen {
        if (!available) return FfmpegSessionOpen(null, ERR_UNSUPPORTED)
        val errorOut = IntArray(1)
        val handle = try {
            nativeOpenSession(source, targetWidth, errorOut)
        } catch (t: Throwable) {
            Log.w(TAG, "open session failed: ${t.message}")
            0L
        }
        return if (handle > 0L) {
            FfmpegSessionOpen(Session(handle), ERR_NONE)
        } else {
            FfmpegSessionOpen(null, errorOut[0].takeIf { it != ERR_NONE } ?: ERR_UNSUPPORTED)
        }
    }

    /**
     * 常驻取帧会话：复用 avio/解封装/解码器，多次取帧只做 seek + decode。
     *
     * 线程安全由 native 侧保证（解码持互斥锁，[cancel] 走原子标志不阻塞）。
     */
    class Session internal constructor(handle: Long) : Closeable {

        private val handleRef = AtomicLong(handle)

        /** 在 [startMs] 处解一帧（精确到帧）。 */
        fun decode(startMs: Long, pickNearest: Boolean = true): FfmpegDecodeResult {
            val handle = handleRef.get()
            // 会话已关闭（换片/预览结束）：按取消上报。绝不能用「不支持」——
            // 上层会把「不支持」当成该文件 ffmpeg 解不出，从而把它整个拉黑。
            if (handle <= 0L) return FfmpegDecodeResult(null, ERR_CANCELLED)
            val errorOut = IntArray(1)
            val raw = try {
                nativeSessionDecode(handle, startMs, -1.0, if (pickNearest) 1 else 0, errorOut)
            } catch (t: Throwable) {
                Log.w(TAG, "session decode failed: ${t.message}")
                null
            }
            return FfmpegDecodeResult(toFrame(raw), errorOut[0])
        }

        /**
         * 打断正在进行的解码（非阻塞）。
         *
         * 用于「松手即放弃当前帧」：正在解的那一帧会尽快收手，把解码资源让给真实播放，
         * 而不是继续跑完（远程高码率文件一帧可能要数百毫秒到数秒）。
         */
        fun cancel() {
            val handle = handleRef.get()
            if (handle <= 0L) return
            nativeSessionCancel(handle)
        }

        override fun close() {
            val handle = handleRef.getAndSet(0L)
            if (handle > 0L) nativeSessionClose(handle)
        }
    }

    /** 头部 3 个 int（宽、高、HDR 类型）+ BGRA 像素 → [FfmpegFrame]。 */
    private fun toFrame(raw: IntArray?): FfmpegFrame? {
        if (raw == null || raw.size < 4) return null
        val width = raw[0]
        val height = raw[1]
        val hdrKind = raw[2]
        val count = width * height
        if (width <= 0 || height <= 0 || raw.size < count + 3) return null
        val pixels = IntArray(count)
        System.arraycopy(raw, 3, pixels, 0, count)
        return FfmpegFrame(
            bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888),
            hdrKind = hdrKind,
        )
    }

    private external fun nativeDecode(
        source: MediaDataSource,
        targetWidth: Int,
        startMs: Long,
        fraction: Double,
        pickNearest: Int,
        errorOut: IntArray,
    ): IntArray?

    private external fun nativeOpenSession(
        source: MediaDataSource,
        targetWidth: Int,
        errorOut: IntArray,
    ): Long

    private external fun nativeSessionDecode(
        handle: Long,
        startMs: Long,
        fraction: Double,
        pickNearest: Int,
        errorOut: IntArray,
    ): IntArray?

    private external fun nativeSessionCancel(handle: Long)

    private external fun nativeSessionClose(handle: Long)
}
