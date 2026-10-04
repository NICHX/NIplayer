package com.nichx.niplayer.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.SmbMediaDataSource
import com.nichx.niplayer.storage.impl.WebDavMediaDataSource
import kotlinx.coroutines.CancellationException

/**
 * 视频取帧结果。
 *
 * 比 [ThumbnailResult] 更细：区分「已解码的帧」与「失败原因」，把「落盘」留给调用方，
 * 避免取帧与缓存写盘耦合在一个巨大方法里。
 */
internal sealed interface FrameExtraction {
    /** 解码成功（已按 [ThumbnailManager.MAX_WIDTH] 缩放）。 */
    data class Ok(val bitmap: Bitmap) : FrameExtraction

    /** 临时失败（IO/解码错误），可重试。 */
    data object Failed : FrameExtraction

    /** 永久失败（WebDAV 401/403 等凭证错误），不建议重试。 */
    data object PermanentFailure : FrameExtraction
}

/**
 * 视频缩略图取帧：负责「选数据源 → 选帧位置 → 解码 → HDR 补偿 → 缩放」。
 *
 * 把旧 [ThumbnailManager] 中 5 个 `generateFromUrl/FromDataSource/...At` + 两个
 * `extractAndSaveFrame*` 收敛为「一个数据源策略 + 一次取帧」，不再与落盘纠缠。
 */
internal class VideoFrameExtractor(private val context: Context) {

    /** 按用户配置的取帧位置策略 [positionKey] 取帧。 */
    suspend fun extract(
        storage: Storage,
        file: StorageFile,
        positionKey: String,
        skipDurationCheck: Boolean,
    ): FrameExtraction {
        val startedAt = SystemClock.elapsedRealtime()

        // 远程 MKV 优先走「自解析头部 + MediaCodec」路径：它不依赖容器索引、读取量固定（~16MB），
        // 而 MediaMetadataRetriever 在无可用索引的容器上会从第 0 字节顺序扫全文件（实测 2034MB/集、70s）。
        // 提到最前可避免先白烧一次读取预算；此路径失败才回退到 Retriever。
        if (file.name.endsWith(".mkv", ignoreCase = true) &&
            storage.library.mediaType != MediaType.LOCAL_STORAGE
        ) {
            val bitmap = extractMkvFirstFrame(storage, file, positionKey)
            if (bitmap != null) {
                val scaled = scaleToMaxWidth(bitmap, ThumbnailManager.MAX_WIDTH)
                if (scaled !== bitmap) bitmap.recycle()
                Log.d(
                    TAG,
                    "extract ok: ${file.name} ${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                return FrameExtraction.Ok(scaled)
            }
        }

        val (extraction, dataSource) = withRetriever(storage, file) { retriever, _ ->
            val positions = framePositionsFor(retriever, positionKey, skipDurationCheck)
            readFrame(retriever, positions)
        }

        // 读取预算耗尽：说明这个容器没有取帧器可用的索引，按时间点取帧会退化成全文件扫描。
        // MKV 已在方法开头走过自解析头部路径，这里不再重试，直接判永久失败（上层记「不重试」）。
        if ((dataSource as? SmbMediaDataSource)?.budgetExceeded == true) {
            Log.w(TAG, "read budget exceeded, skip without retry: ${file.name}")
            val result = FrameExtraction.PermanentFailure
            logExtraction(file, result, dataSource, startedAt)
            return result
        }

        val result = resolvePermanentFailure(extraction, dataSource)
        logExtraction(file, result, dataSource, startedAt)
        return result
    }

    private suspend fun extractMkvFirstFrame(
        storage: Storage,
        file: StorageFile,
        positionKey: String,
    ): Bitmap? {
        if (MkvFfmpegDecoder.isAvailable) {
            val source = try {
                storage.openMediaDataSource(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "openMediaDataSource failed: ${e.message}")
                null
            }
            if (source != null) {
                val (startMs, fraction) = mkvPosition(positionKey)
                val bitmap = decodeMkvWithFfmpeg(source, startMs, fraction)
                if (bitmap != null) return bitmap
            }
        }
        return MkvFirstFrameExtractor.extract(storage, file)
    }

    private fun mkvPosition(positionKey: String): Pair<Long, Double> = when (positionKey) {
        "10pct" -> -1L to 0.1
        "50pct" -> -1L to 0.5
        else -> 5000L to -1.0
    }

    private fun decodeMkvWithFfmpeg(source: MediaDataSource, startMs: Long, fraction: Double): Bitmap? {
        return try {
            MkvFfmpegDecoder.decodeFirstFrame(source, ThumbnailManager.MAX_WIDTH, startMs, fraction)
        } catch (e: Exception) {
            Log.w(TAG, "ffmpeg mkv decode failed: ${e.message}")
            null
        } finally {
            try {
                source.close()
            } catch (_: Exception) {
            }
        }
    }

    /** 永久失败识别：仅 WebDAV MediaDataSource 能给出 HTTP 错误码（401/403 不应重试）。 */
    private fun resolvePermanentFailure(
        extraction: FrameExtraction,
        dataSource: MediaDataSource?,
    ): FrameExtraction = if (extraction is FrameExtraction.Failed &&
        dataSource is WebDavMediaDataSource &&
        dataSource.lastHttpErrorCode in setOf(401, 403)
    ) {
        Log.w(TAG, "WebDAV permanent failure (HTTP ${dataSource.lastHttpErrorCode})")
        FrameExtraction.PermanentFailure
    } else {
        extraction
    }

    /** 在精确位置 [positionMs] 取帧（退出播放时用于覆盖缩略图）。 */
    suspend fun extractAt(
        storage: Storage,
        file: StorageFile,
        positionMs: Long,
        skipDurationCheck: Boolean,
    ): FrameExtraction {
        val startedAt = SystemClock.elapsedRealtime()
        val (extraction, dataSource) = withRetriever(storage, file) { retriever, _ ->
            val positions = framePositionsAt(retriever, positionMs, skipDurationCheck)
            readFrame(retriever, positions)
        }
        logExtraction(file, extraction, dataSource, startedAt)
        return extraction
    }

    /**
     * 记录一次取帧的耗时、数据源类型与结果。
     *
     * 失败此前完全无日志，无法区分「取帧失败」与「远程读取太慢」；此处补齐，
     * 成功用 [Log.d]、失败用 [Log.w]，便于用 `adb logcat -s VideoFrameExtractor` 定位。
     */
    private fun logExtraction(
        file: StorageFile,
        result: FrameExtraction,
        dataSource: MediaDataSource?,
        startedAt: Long,
    ) {
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        val source = dataSource?.javaClass?.simpleName ?: "url"
        when (result) {
            is FrameExtraction.Ok -> Log.d(TAG, "extract ok: ${file.name} src=$source ${elapsedMs}ms")
            FrameExtraction.Failed -> Log.w(TAG, "extract failed: ${file.name} src=$source ${elapsedMs}ms")
            FrameExtraction.PermanentFailure -> Log.w(TAG, "extract permanent-failure: ${file.name} src=$source ${elapsedMs}ms")
        }
    }

    /**
     * 建立合适的数据源并执行 [block]，返回 (取帧结果, 实际使用的 MediaDataSource 或 null)。
     *
     * 数据源选择（与旧实现一致）：
     * - file/content URL：`setDataSource(context, uri)`
     * - http URL：非自签证书时优先 URL+Headers（系统 HTTP 栈更稳），失败回退 MediaDataSource；
     *   自签证书（`trustAllCertificates`）直接走 MediaDataSource（URL+Headers 必失败）
     * - 其它（SMB 等）：MediaDataSource
     */
    private suspend fun withRetriever(
        storage: Storage,
        file: StorageFile,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val url = storage.createPlayUrl(file)
        return when {
            url != null && (url.startsWith("file") || url.startsWith("content")) ->
                useUrlUri(url, block)

            url != null && url.startsWith("http", ignoreCase = true) ->
                useHttp(storage, file, url, block)

            else ->
                useDataSource(storage, file, block)
        }
    }

    private fun useUrlUri(
        url: String,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(url))
            block(retriever, null) to null
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource(uri) failed: ${e.message}")
            FrameExtraction.Failed to null
        } finally {
            releaseQuietly(retriever)
        }
    }

    private suspend fun useHttp(
        storage: Storage,
        file: StorageFile,
        url: String,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        // W-M6：自签 HTTPS 下 URL+Headers 走系统栈必失败，跳过直接走 MediaDataSource
        if (!storage.trustAllCertificates) {
            val headers = storage.getPlayHeaders()
            if (headers.isNotEmpty()) {
                val viaHeaders = useUrlHeaders(url, headers, block)
                if (viaHeaders.first is FrameExtraction.Ok) return viaHeaders
            }
        }
        val dataSource = storage.openMediaDataSource(file)
        return if (dataSource != null) {
            useDataSourceInstance(dataSource, block)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(url))
                block(retriever, null) to null
            } catch (e: Exception) {
                Log.w(TAG, "fallback setDataSource(uri) failed: ${e.message}")
                FrameExtraction.Failed to null
            } finally {
                releaseQuietly(retriever)
            }
        }
    }

    private fun useUrlHeaders(
        url: String,
        headers: Map<String, String>,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers)
            block(retriever, null) to null
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource(url, headers) failed: ${e.message}")
            FrameExtraction.Failed to null
        } finally {
            releaseQuietly(retriever)
        }
    }

    private suspend fun useDataSource(
        storage: Storage,
        file: StorageFile,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val dataSource = storage.openMediaDataSource(file)
        if (dataSource == null) {
            Log.w(TAG, "openMediaDataSource returned null for ${file.path}")
            return FrameExtraction.Failed to null
        }
        return useDataSourceInstance(dataSource, block)
    }

    private fun useDataSourceInstance(
        dataSource: MediaDataSource,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            block(retriever, dataSource) to dataSource
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource(MediaDataSource) failed: ${e.message}")
            FrameExtraction.Failed to dataSource
        } finally {
            releaseQuietly(retriever)
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseQuietly(retriever: MediaMetadataRetriever) {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "VideoFrameExtractor"
    }
}

// ---------- 位置选择与解码（顶层纯函数，便于复用与测试） ----------

/**
 * 计算按 [positionKey] 的候选取帧位置（毫秒）。
 *
 * - 短视频（durationMs < [ThumbnailManager.MIN_DURATION_MS]）且未跳过检查 → 取第一个关键帧
 * - 否则用户配置位置优先，再回退 10% / 50%
 * - duration 不可用 → 退化为默认位置
 */
internal fun framePositionsFor(
    retriever: MediaMetadataRetriever,
    positionKey: String,
    skipDurationCheck: Boolean,
): List<Long> {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    val isShort = !skipDurationCheck && durationMs != null && durationMs < ThumbnailManager.MIN_DURATION_MS
    val positions = mutableListOf<Long>()
    if (isShort) {
        positions.add(0L)
        return positions
    }
    positions.add(
        if (durationMs != null) calculateFramePositionMs(durationMs, positionKey) else DEFAULT_FRAME_MS
    )
    if (durationMs != null) {
        val tenPct = (durationMs * 0.1).toLong()
        val fiftyPct = (durationMs * 0.5).toLong()
        if (tenPct !in positions) positions.add(tenPct)
        if (fiftyPct !in positions) positions.add(fiftyPct)
    }
    return positions
}

/** 计算显式 [positionMs] 的候选取帧位置（毫秒），短视频改为第一个关键帧。 */
internal fun framePositionsAt(
    retriever: MediaMetadataRetriever,
    positionMs: Long,
    skipDurationCheck: Boolean,
): List<Long> {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    val isShort = !skipDurationCheck && durationMs != null && durationMs < ThumbnailManager.MIN_DURATION_MS
    if (isShort) return listOf(0L)
    return mutableListOf(positionMs).also {
        if (durationMs != null) {
            val tenPct = (durationMs * 0.1).toLong()
            val fiftyPct = (durationMs * 0.5).toLong()
            if (tenPct !in it) it.add(tenPct)
            if (fiftyPct !in it) it.add(fiftyPct)
        }
    }
}

/** 依次尝试 [positions]，返回第一个成功解码并完成 HDR 补偿、缩放的帧；全部失败返回 [FrameExtraction.Failed]。 */
internal fun readFrame(retriever: MediaMetadataRetriever, positions: List<Long>): FrameExtraction {
    val isHdr = isHdrVideo(retriever)
    var frame: Bitmap? = null
    for (posMs in positions) {
        frame = retriever.frameAt(posMs * 1000L, ThumbnailManager.MAX_WIDTH)
        if (frame != null) break
    }
    if (frame == null) return FrameExtraction.Failed
    val compensated = applyHdrCompensationIfNeeded(frame, isHdr)
    val scaled = scaleToMaxWidth(compensated, ThumbnailManager.MAX_WIDTH)
    // 缩放到新实例后回收补偿/原始帧，避免内存累积
    if (scaled !== compensated) compensated.recycle()
    return FrameExtraction.Ok(scaled)
}

/** 等比缩放到 [maxWidth]，宽度不超时不复制、直接返回 [src]（不回收 [src]，中间产物由调用方回收）。 */
internal fun scaleToMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
    if (src.width <= maxWidth) return src
    val ratio = maxWidth.toFloat() / src.width
    val newHeight = (src.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, maxWidth, newHeight, true)
}

/** 计算 [inSampleSize]：2 的幂倍，使原图至少一边不超 [maxDimension]。 */
internal fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
    var sampleSize = 1
    while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * 按接近 [maxWidth] 的目标尺寸解码指定时间点的帧。
 *
 * API 27+ 且视频无旋转（0/180）时优先 [MediaMetadataRetriever.getScaledFrameAtTime] 直接降采样解码，
 * 避免先解出全分辨率帧再缩小；其余情况回退 [MediaMetadataRetriever.getFrameAtTime]。
 */
internal fun MediaMetadataRetriever.frameAt(positionUs: Long, maxWidth: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 0 || rotation == 180) {
            val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0) {
                val ratio = if (width > maxWidth) maxWidth.toFloat() / width else 1f
                val dstWidth = (width * ratio).toInt().coerceAtLeast(1)
                val dstHeight = (height * ratio).toInt().coerceAtLeast(1)
                try {
                    return getScaledFrameAtTime(
                        positionUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        dstWidth,
                        dstHeight,
                    )
                } catch (_: Exception) {
                    // 回退到 getFrameAtTime
                }
            }
        }
    }
    return try {
        getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    }
}

// ---------- HDR 色调映射 ----------

/** 检测视频是否为 HDR 编码（PQ/HDR10/Dolby Vision 或 HLG）。 */
internal fun isHdrVideo(retriever: MediaMetadataRetriever): Boolean {
    val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
        ?: return false
    return transfer == ThumbnailManager.COLOR_TRANSFER_SMPTE_ST_2084 || transfer == ThumbnailManager.COLOR_TRANSFER_HLG
}

/**
 * HDR 软件色调映射补偿（仅 API < 34 使用）：线性增益 + [ColorMatrixColorFilter]，避免逐像素循环。
 */
internal fun applyHdrToneMapCompensation(src: Bitmap): Bitmap {
    val gain = ThumbnailManager.HDR_TONE_MAP_GAIN
    val config = src.config ?: Bitmap.Config.ARGB_8888
    val result = Bitmap.createBitmap(src.width, src.height, config)
    val canvas = Canvas(result)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    gain, 0f, 0f, 0f, 0f,
                    0f, gain, 0f, 0f, 0f,
                    0f, 0f, gain, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
}

/** 按需做 HDR 补偿：API 34+ 系统已 tone map，直接返回；否则对 HDR 帧补偿。 */
internal fun applyHdrCompensationIfNeeded(src: Bitmap, isHdr: Boolean): Bitmap {
    if (!isHdr) return src
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return src
    val mapped = applyHdrToneMapCompensation(src)
    if (mapped !== src) src.recycle()
    return mapped
}
