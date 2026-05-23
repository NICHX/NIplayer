package com.xyoye.common_component.utils

import android.media.MediaMetadataRetriever
import android.os.Build
import com.xyoye.data_component.bean.StorageFileInfo

object MediaMetadataExtractor {

    private const val METADATA_KEY_VIDEO_CODEC = 33
    private const val METADATA_KEY_AUDIO_CODEC = 32
    private const val METADATA_KEY_COLOR_PRIMARIES = 34
    private const val METADATA_KEY_TRANSFER_CHARACTERISTICS = 35

    fun extractFromRetriever(retriever: MediaMetadataRetriever, base: StorageFileInfo): StorageFileInfo {
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
        val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
        val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

        val audioCodecStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(METADATA_KEY_AUDIO_CODEC)
        } else null
        val videoCodecStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(METADATA_KEY_VIDEO_CODEC)
        } else null
        val colorPrimaries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(METADATA_KEY_COLOR_PRIMARIES)
        } else null
        val transferChar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            retriever.extractMetadata(METADATA_KEY_TRANSFER_CHARACTERISTICS)
        } else null

        val width = widthStr?.toIntOrNull() ?: 0
        val height = heightStr?.toIntOrNull() ?: 0
        val rotation = rotationStr?.toIntOrNull() ?: 0
        val totalBitrate = bitrateStr?.toLongOrNull() ?: 0

        val effectiveWidth: Int
        val effectiveHeight: Int
        if (rotation == 90 || rotation == 270) {
            effectiveWidth = height
            effectiveHeight = width
        } else {
            effectiveWidth = width
            effectiveHeight = height
        }

        val videoBitrate = if (base.isVideo && totalBitrate > 0 && base.isAudio.not()) {
            totalBitrate
        } else {
            0
        }

        return base.copy(
            videoWidth = effectiveWidth,
            videoHeight = effectiveHeight,
            durationMs = durationStr?.toLongOrNull() ?: 0,
            bitrate = totalBitrate,
            videoCodec = mimeType?.ifEmpty { null },
            audioCodec = audioCodecStr?.ifEmpty { null },
            frameRate = frameRateStr?.ifEmpty { null },
            sampleRate = sampleRateStr?.toIntOrNull() ?: 0,
            rotation = rotation,
            videoBitrate = videoBitrate,
            videoCodecName = videoCodecStr?.ifEmpty { null },
            audioCodecName = audioCodecStr?.ifEmpty { null },
            audioSampleRate = sampleRateStr?.toIntOrNull() ?: 0,
            mimeType = mimeType?.ifEmpty { null },
            colorPrimaries = colorPrimaries?.ifEmpty { null },
            transferCharacteristics = transferChar?.ifEmpty { null }
        )
    }

    fun extractFromLocalPath(path: String, base: StorageFileInfo): StorageFileInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            extractFromRetriever(retriever, base)
        } catch (e: Exception) {
            e.printStackTrace()
            base
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun extractFromUrl(url: String, headers: Map<String, String>?, base: StorageFileInfo): StorageFileInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers ?: emptyMap())
            extractFromRetriever(retriever, base)
        } catch (e: Exception) {
            e.printStackTrace()
            base
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
