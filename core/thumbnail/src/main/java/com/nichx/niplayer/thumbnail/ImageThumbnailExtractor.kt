package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.CancellationException
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
                val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, IMAGE_THUMB_MAX)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inJustDecodeBounds = false
                    // 缩略图输出为 JPEG（无 alpha），RGB_565 减半像素内存并加快编码
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                try {
                    stream.reset()
                    BitmapFactory.decodeStream(stream, null, opts)
                } catch (e: Exception) {
                    // mark 失效（图片头部/元数据超过缓冲区导致 reset 抛 IOException）：
                    // 退回"重新打开流"的单次解码，避免整张图缩略图生成失败
                    Log.w(TAG, "mark invalidated, reopening stream: ${e.message}")
                    decodeWithFreshStream(storage, file, sampleSize)
                }
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

    /** 重新打开输入流按 [sampleSize] 直接解码（mark/reset 不可用时的回退路径）。 */
    private suspend fun decodeWithFreshStream(
        storage: Storage,
        file: StorageFile,
        sampleSize: Int,
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return try {
            storage.openInputStream(file).use { ins ->
                BufferedInputStream(ins, BUFFER_SIZE).use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fresh stream decode failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "ImageThumbnailExtractor"
        const val IMAGE_THUMB_MAX = 480
        const val BUFFER_SIZE = 64 * 1024
    }
}
