package com.xyoye.common_component.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * 缩略图生成管理器
 * 用于为网络存储的视频和图片文件生成缩略图并缓存
 */
object ThumbnailGeneratorManager {
    // 缩略图最大宽度
    private const val THUMBNAIL_MAX_WIDTH = 320
    // 单次处理的文件数量
    private const val BATCH_SIZE = 6
    // 文件处理之间的延迟（毫秒）
    private const val PROCESS_DELAY_MS = 50L
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 当前正在生成缩略图的任务（确保同时只有一个任务）
    private val currentTasks = ConcurrentHashMap.newKeySet<String>()
    
    // 是否正在处理缩略图生成
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    // 待处理的文件队列
    private val pendingFiles = LinkedBlockingQueue<StorageFile>()
    
    // 处理中的标志
    private var isProcessing = false

    // 缩略图生成完成回调
    interface ThumbnailCallback {
        fun onThumbnailGenerated(file: StorageFile)
    }
    
    private var thumbnailCallback: ThumbnailCallback? = null
    
    fun setThumbnailCallback(callback: ThumbnailCallback?) {
        thumbnailCallback = callback
    }

    /**
     * 判断是否为网络存储类型
     */
    private fun isNetworkStorage(storage: Storage): Boolean {
        return when (storage.library.mediaType) {
            MediaType.FTP_SERVER,
            MediaType.WEBDAV_SERVER,
            MediaType.SMB_SERVER,
            MediaType.REMOTE_STORAGE,
            MediaType.ALSIT_STORAGE -> true
            else -> false
        }
    }

    /**
     * 开始为文件列表生成缩略图
     * @param files 文件列表
     * @param storage 所属存储
     */
    fun startGenerateThumbnails(files: List<StorageFile>, storage: Storage) {
        if (!isNetworkStorage(storage)) {
            return
        }

        // 清空队列并添加新文件
        synchronized(pendingFiles) {
            pendingFiles.clear()
            files.filter { 
                (it.isVideoFile() || it.isImageFile()) && !hasCachedThumbnail(it) 
            }.forEach { pendingFiles.offer(it) }
        }

        // 开始处理
        processNextBatch()
    }

    /**
     * 继续生成缩略图
     */
    fun continueGenerateThumbnails(startIndex: Int) {
        // 如果正在处理，直接返回
        if (isProcessing) {
            return
        }
        // 只有队列还有文件时才继续处理
        if (pendingFiles.isNotEmpty()) {
            processNextBatch()
        }
    }

    /**
     * 检查文件是否已经有缓存的缩略图
     */
    private fun hasCachedThumbnail(file: StorageFile): Boolean {
        val coverFile = file.uniqueKey().toCoverFile()
        return coverFile != null && coverFile.exists() && coverFile.length() > 0
    }

    /**
     * 处理下一批缩略图生成任务
     */
    private fun processNextBatch() {
        // 如果正在处理或队列为空，直接返回
        if (isProcessing || pendingFiles.isEmpty()) {
            return
        }

        // 取出一批文件
        val filesToProcess = mutableListOf<StorageFile>()
        synchronized(pendingFiles) {
            repeat(BATCH_SIZE) {
                pendingFiles.poll()?.let { filesToProcess.add(it) }
            }
        }

        if (filesToProcess.isEmpty()) {
            _isGenerating.value = false
            return
        }

        isProcessing = true
        _isGenerating.value = true

        scope.launch {
            filesToProcess.forEachIndexed { index, file ->
                // 快速失败检查：如果已经有缓存了，跳过
                if (hasCachedThumbnail(file)) {
                    return@forEachIndexed
                }
                
                // 如果不是第一个文件，添加延迟防止卡顿
                if (index > 0) {
                    delay(PROCESS_DELAY_MS)
                }
                
                generateThumbnailForFile(file)
                
                // 如果还有文件，继续处理下一批
                if (index == filesToProcess.size - 1) {
                    isProcessing = false
                    processNextBatch()
                }
            }
        }
    }

    /**
     * 为单个文件生成缩略图
     */
    private suspend fun generateThumbnailForFile(file: StorageFile) {
        val uniqueKey = file.uniqueKey()

        // 避免重复处理
        if (currentTasks.contains(uniqueKey)) {
            return
        }

        currentTasks.add(uniqueKey)
        var thumbnailGenerated = false

        try {
            val coverFile = uniqueKey.toCoverFile() ?: return

            // 再次检查是否有缓存
            if (coverFile.exists() && coverFile.length() > 0) {
                return
            }

            if (file.isVideoFile()) {
                thumbnailGenerated = generateVideoThumbnail(file, coverFile)
            } else if (file.isImageFile()) {
                thumbnailGenerated = generateImageThumbnail(file, coverFile)
            }
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "生成缩略图失败: ${file.fileName()}", e)
        } finally {
            currentTasks.remove(uniqueKey)
            // 如果缩略图生成成功，通知回调
            if (thumbnailGenerated) {
                withContext(Dispatchers.Main) {
                    thumbnailCallback?.onThumbnailGenerated(file)
                }
            }
        }
    }

    /**
     * 为视频文件生成缩略图
     */
    private suspend fun generateVideoThumbnail(file: StorageFile, coverFile: File): Boolean = withContext(Dispatchers.IO) {
        val mediaRetriever = MediaMetadataRetriever()
        var success = false

        try {
            // 获取播放URL
            val playUrl = file.storage.createPlayUrl(file) ?: return@withContext false

            // 设置数据源
            val headers = file.storage.getNetworkHeaders()
            if (headers != null) {
                mediaRetriever.setDataSource(playUrl, headers)
            } else {
                mediaRetriever.setDataSource(playUrl)
            }

            // 获取视频第一个关键帧作为缩略图
            val bitmap = mediaRetriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext false

            // 缩放到固定宽度，减少存储和后续加载开销
            val scaledBitmap = resizeBitmap(bitmap, THUMBNAIL_MAX_WIDTH)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }

            // 保存缩略图
            success = saveBitmapToFile(scaledBitmap, coverFile)
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "视频缩略图生成失败: ${file.fileName()}", e)
        } finally {
            try {
                mediaRetriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        return@withContext success
    }

    /**
     * 为图片文件生成缩略图
     */
    private suspend fun generateImageThumbnail(file: StorageFile, coverFile: File): Boolean = withContext(Dispatchers.IO) {
        var success = false
        var inputStream: java.io.InputStream? = null

        try {
            // 获取图片的输入流
            inputStream = file.storage.openFile(file) ?: return@withContext false

            // 先解码原尺寸获取图片信息
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            // 关闭流并重新打开
            IOUtils.closeIO(inputStream)
            inputStream = file.storage.openFile(file) ?: return@withContext false

            // 计算缩放比例
            val reqWidth = 400
            val reqHeight = 400
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            // 解码缩略图
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return@withContext false

            // 保存缩略图
            success = saveBitmapToFile(bitmap, coverFile)
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "图片缩略图生成失败: ${file.fileName()}", e)
        } finally {
            IOUtils.closeIO(inputStream)
        }
        return@withContext success
    }
    
    /**
     * 计算 Bitmap 缩放比例
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 将 Bitmap 等比缩放到指定宽度
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth) return bitmap
        val scale = maxWidth.toFloat() / width
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    /**
     * 将 Bitmap 保存到文件
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        var fos: FileOutputStream? = null
        var success = false
        try {
            // 确保目录存在
            file.parentFile?.mkdirs()

            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, fos)
            fos.flush()
            success = true
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "保存缩略图失败", e)
            // 如果保存失败，删除可能部分写入的文件
            if (file.exists()) {
                file.delete()
            }
        } finally {
            IOUtils.closeIO(fos)
        }
        return success
    }

    /**
     * 清除所有待处理任务
     */
    fun clearPendingTasks() {
        synchronized(pendingFiles) {
            pendingFiles.clear()
        }
        currentTasks.clear()
        isProcessing = false
        _isGenerating.value = false
    }
}
