package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import java.io.BufferedInputStream
import java.io.File

/**
 * 图片缩略图提取：单次流式解码（[BufferedInputStream] mark/reset）避免对 SMB 等协议两次打开流。
 */
internal class ImageThumbnailExtractor(private val store: ThumbnailStore) {

    /** 解码 [file] 并写入 [cacheFile]；成功返回 true。 */
    suspend fun extract(storage: Storage, file: StorageFile, cacheFile: File): Boolean {
        val rawInput = storage.openInputStream(file)
        val bitmap = rawInput.use { raw ->
            BufferedInputStream(raw, BUFFER_SIZE).use { stream ->
                // Phase 1：仅读取宽高，不分配像素内存
                stream.mark(BUFFER_SIZE)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null

                // Phase 2：reset 回起点，带 inSampleSize 解码
                stream.reset()
                val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, IMAGE_THUMB_MAX)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inJustDecodeBounds = false
                    // 缩略图输出为 JPEG（无 alpha），RGB_565 减半像素内存并加快编码
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } ?: return false

        return try {
            store.writeJpeg(cacheFile, bitmap, ThumbnailManager.IMAGE_JPEG_QUALITY)
            true
        } catch (e: Exception) {
            Log.w(TAG, "write image thumbnail failed: ${e.message}")
            false
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "ImageThumbnailExtractor"
        const val IMAGE_THUMB_MAX = 480
        const val BUFFER_SIZE = 64 * 1024
    }
}
