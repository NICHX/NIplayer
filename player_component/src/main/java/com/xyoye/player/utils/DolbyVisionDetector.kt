package com.xyoye.player.utils

import android.media.MediaExtractor
import android.media.MediaFormat

object DolbyVisionDetector {

    fun isDolbyVision(url: String, headers: Map<String, String>? = null): Boolean {
        val extractor = MediaExtractor()
        try {
            if (headers != null && headers.isNotEmpty()) {
                extractor.setDataSource(url, headers)
            } else {
                extractor.setDataSource(url)
            }

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.equals("video/dolby-vision", ignoreCase = true)) {
                    return true
                }
            }
        } catch (_: Exception) {
        } finally {
            extractor.release()
        }
        return false
    }
}
