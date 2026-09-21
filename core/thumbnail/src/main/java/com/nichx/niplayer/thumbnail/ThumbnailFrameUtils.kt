package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.io.FileOutputStream


// ---------- 生成 ----------

/**
 * 检测视频是否为 HDR 编码。
 *
 * 通过 [MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER] 判断：
 * - [ThumbnailManager.COLOR_TRANSFER_SMPTE_ST_2084] (7) = SMPTE ST 2084 (PQ, HDR10 / Dolby Vision)
 * - [ThumbnailManager.COLOR_TRANSFER_HLG] (18) = ARIB STD-B67 (HLG)
 *
 * 在 API < 34 上，[MediaMetadataRetriever.getFrameAtTime] 对 HDR 视频取帧不做
 * tone mapping，返回的 Bitmap 像素值按 SDR 解读会严重偏暗、色彩失真。
 *
 * @return true 表示 HDR 视频需做软件色调映射补偿
 */
internal fun isHdrVideo(retriever: MediaMetadataRetriever): Boolean {
    val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
        ?.toIntOrNull() ?: return false
    return transfer == ThumbnailManager.COLOR_TRANSFER_SMPTE_ST_2084 || transfer == ThumbnailManager.COLOR_TRANSFER_HLG
}

/**
 * HDR 软件色调映射补偿（仅 API < 34 使用）。
 *
 * 背景：API < 34 的 [MediaMetadataRetriever.getFrameAtTime] 不对 HDR 内容做 tone mapping，
 * 取出的 Bitmap 是 HDR 像素值但被按 SDR 显示，整体偏暗、色彩失真。
 *
 * 补偿策略：
 * - 线性增益（[ThumbnailManager.HDR_TONE_MAP_GAIN]）：HLG/PQ 编码亮度空间映射到 SDR 显示空间的近似
 * - 用 [ColorMatrixColorFilter] 实现，避免 Kotlin 逐像素循环
 *
 * 注意：此为简化补偿，远不如 API 34+ 系统级 tone mapping 准确，但显著改善可用性。
 * 完整色调映射需基于 PQ/HLG OETF 反变换 + BT.1886 gamma，依赖硬件解码器输出
 * 10-bit 数据；在 MediaMetadataRetriever 已量化为 8-bit 后无法准确还原，只能做近似。
 *
 * @param src 原始 HDR 帧 Bitmap
 * @return 补偿后的 SDR Bitmap
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

/**
 * 对取出的视频帧按需做 HDR 软件色调映射补偿。
 *
 * - API 34+：系统自动 tone map HDR→SDR，直接返回原帧
 * - API < 34 且 [isHdr]：调用 [applyHdrToneMapCompensation] 做线性增益补偿
 * - 否则：直接返回原帧
 *
 * @param src 取出的帧
 * @param isHdr 调用方通过 [isHdrVideo] 预检测结果（避免循环内重复检测）
 * @return 补偿后的 Bitmap（可能是原 src 或新创建的副本）
 */
internal fun applyHdrCompensationIfNeeded(src: Bitmap, isHdr: Boolean): Bitmap {
    if (!isHdr) return src
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return src
    val mapped = applyHdrToneMapCompensation(src)
    if (mapped !== src) src.recycle()
    return mapped
}

/**
 * 从已 setDataSource 的 [MediaMetadataRetriever] 提取帧并保存到 [cacheFile]，
 * 直接使用 [positionMs] 作为取帧位置，取帧失败时 fallback 到 10%/50% 位置。
 *
 * 短视频（durationMs < 15s）改为取第一个关键帧（0ms）生成缩略图；
 * 本地视频（skipDurationCheck）仍按传入位置取帧。
 *
 * HDR 补偿：Dolby Vision / HDR10 / HLG 视频在 API < 34 上 getFrameAtTime 不做
 * tone mapping，调用 [applyHdrCompensationIfNeeded] 做线性增益补偿。
 */
internal fun extractAndSaveFrameAt(
    retriever: MediaMetadataRetriever,
    cacheFile: File,
    positionMs: Long,
    skipDurationCheck: Boolean = false,
): ThumbnailResult {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull()
    // 短视频（<15s）不再返回 TooShort，改为取第一个关键帧（0ms）生成缩略图；
    // 本地视频（skipDurationCheck）不受此规则影响，始终按正常位置取帧
    val isShort = !skipDurationCheck && durationMs != null && durationMs < ThumbnailManager.MIN_DURATION_MS
    val fallbackPositions = if (isShort) {
        mutableListOf(0L)
    } else {
        mutableListOf(positionMs).also {
            if (durationMs != null) {
                val tenPct = (durationMs * 0.1).toLong()
                val fiftyPct = (durationMs * 0.5).toLong()
                if (tenPct !in it) it.add(tenPct)
                if (fiftyPct !in it) it.add(fiftyPct)
            }
        }
    }

    // HDR 检测一次性完成，避免 fallback 循环内重复调用 extractMetadata
    val isHdr = isHdrVideo(retriever)

    var frame: Bitmap? = null
    for (posMs in fallbackPositions) {
        frame = try {
            retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        }
        if (frame != null) break
    }
    if (frame == null) return ThumbnailResult.Failed

    // HDR 软件色调映射补偿（API 34+ 系统自动处理）
    frame = applyHdrCompensationIfNeeded(frame, isHdr)

    val scaled = scaleToMaxWidth(frame, ThumbnailManager.MAX_WIDTH)
    cacheFile.parentFile?.mkdirs()
    FileOutputStream(cacheFile).use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, ThumbnailManager.JPEG_QUALITY, out)
    }
    return ThumbnailResult.Success(cacheFile.absolutePath)
}

/**
 * 从已 setDataSource 的 [MediaMetadataRetriever] 提取帧并保存到 [cacheFile]。
 *
 * 帧位置策略：根据 [positionKey] 通过 [calculateFramePositionMs] 计算，
 * fallback 到 duration*0.1 / duration*0.5（ms → us，OPTION_CLOSEST_SYNC）。
 * 短视频（durationMs < 15s）改为取第一个关键帧（0ms）生成缩略图；
 * 本地视频（skipDurationCheck）仍按用户配置位置取帧。
 *
 * HDR 补偿：Dolby Vision / HDR10 / HLG 视频在 API < 34 上 getFrameAtTime 不做
 * tone mapping，调用 [applyHdrCompensationIfNeeded] 做线性增益补偿。
 */
internal fun extractAndSaveFrame(
    retriever: MediaMetadataRetriever,
    cacheFile: File,
    positionKey: String,
    skipDurationCheck: Boolean = false,
): ThumbnailResult {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        ?.toLongOrNull()
    // 短视频（<15s）不再返回 TooShort，改为取第一个关键帧（0ms）生成缩略图；
    // 本地视频（skipDurationCheck）不受此规则影响，始终按用户配置位置取帧
    val isShort = !skipDurationCheck && durationMs != null && durationMs < ThumbnailManager.MIN_DURATION_MS
    val framePositionMs = when {
        isShort -> 0L // 短视频取第一个关键帧
        durationMs != null -> calculateFramePositionMs(durationMs, positionKey)
        else -> {
            // duration 不可用时（如某些远程协议），对绝对位置仍遵循用户设置
            DEFAULT_FRAME_MS
        }
    }
    // 优先用户配置的取帧位置，失败则 fallback 到 10%/50% 位置
    val fallbackPositions = mutableListOf(framePositionMs)
    if (durationMs != null) {
        val tenPct = (durationMs * 0.1).toLong()
        val fiftyPct = (durationMs * 0.5).toLong()
        if (tenPct !in fallbackPositions) fallbackPositions.add(tenPct)
        if (fiftyPct !in fallbackPositions) fallbackPositions.add(fiftyPct)
    }

    // HDR 检测一次性完成，避免 fallback 循环内重复调用 extractMetadata
    val isHdr = isHdrVideo(retriever)

    var frame: Bitmap? = null
    for (posMs in fallbackPositions) {
        frame = try {
            retriever.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        }
        if (frame != null) break
    }
    if (frame == null) return ThumbnailResult.Failed

    // HDR 软件色调映射补偿（API 34+ 系统自动处理）
    frame = applyHdrCompensationIfNeeded(frame, isHdr)

    val scaled = scaleToMaxWidth(frame, ThumbnailManager.MAX_WIDTH)
    cacheFile.parentFile?.mkdirs()
    FileOutputStream(cacheFile).use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, ThumbnailManager.JPEG_QUALITY, out)
    }
    return ThumbnailResult.Success(cacheFile.absolutePath)
}

// ---------- 工具 ----------

/** 等比缩放到 [maxWidth]，宽度不超时不复制。 */
internal fun scaleToMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
    if (src.width <= maxWidth) return src
    val ratio = maxWidth.toFloat() / src.width
    val newHeight = (src.height * ratio).toInt()
    return Bitmap.createScaledBitmap(src, maxWidth, newHeight, true).also {
        if (it !== src) src.recycle()
    }
}

/** 计算 [inSampleSize]：2 的幂倍，使原图至少一边不超 [maxDimension]。 */
internal fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
    var sampleSize = 1
    while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}
