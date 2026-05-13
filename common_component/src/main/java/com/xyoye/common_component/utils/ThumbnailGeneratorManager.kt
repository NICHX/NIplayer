package com.xyoye.common_component.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.LinkedList
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
    // 最大重试次数
    private const val MAX_RETRY_COUNT = 2
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 当前正在生成缩略图的任务（确保同时只有一个任务）
    private val currentTasks = ConcurrentHashMap.newKeySet<String>()
    
    // 文件生成失败的重试次数
    private val retryCountMap = ConcurrentHashMap<String, Int>()
    
    // 是否正在处理缩略图生成
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    // 待处理的文件队列
    private val pendingFiles = LinkedBlockingQueue<StorageFile>()
    
    // 处理中的标志
    private var isProcessing = false

    // Bitmap 复用池，减少GC压力
    private val bitmapPool = LinkedList<Bitmap>()
    private const val BITMAP_POOL_MAX_SIZE = 8

    private fun obtainReusableBitmap(width: Int, height: Int): Bitmap? {
        synchronized(bitmapPool) {
            val iterator = bitmapPool.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate.isRecycled) {
                    iterator.remove()
                    continue
                }
                if (candidate.width >= width && candidate.height >= height) {
                    iterator.remove()
                    return candidate
                }
            }
            return null
        }
    }

    private fun recycleBitmapToPool(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.isMutable.not()) return
        synchronized(bitmapPool) {
            if (bitmapPool.size < BITMAP_POOL_MAX_SIZE) {
                bitmapPool.addLast(bitmap)
            } else {
                bitmap.recycle()
            }
        }
    }

    private fun clearBitmapPool() {
        synchronized(bitmapPool) {
            bitmapPool.forEach { it.recycle() }
            bitmapPool.clear()
        }
    }

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
     * @param priorityKeys 优先处理的文件唯一键集合（当前屏幕可见项）
     */
    fun startGenerateThumbnails(files: List<StorageFile>, storage: Storage, priorityKeys: Set<String> = emptySet()) {
        if (!isNetworkStorage(storage)) {
            return
        }

        // 清空队列并添加新文件
        synchronized(pendingFiles) {
            pendingFiles.clear()
            retryCountMap.clear()
            val filteredFiles = files.filter { 
                (it.isVideoFile() || it.isImageFile()) && !hasCachedThumbnail(it) 
            }
            // 优先处理屏幕可见项
            val (priority, others) = filteredFiles.partition { it.uniqueKey() in priorityKeys }
            priority.forEach { pendingFiles.offer(it) }
            others.forEach { pendingFiles.offer(it) }
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
     * 重新排列待处理队列，将指定文件移到队首优先处理
     * @param priorityKeys 需要优先处理的文件唯一键集合
     */
    fun reprioritize(priorityKeys: Set<String>) {
        if (priorityKeys.isEmpty()) return
        synchronized(pendingFiles) {
            if (pendingFiles.isEmpty()) return
            val remaining = mutableListOf<StorageFile>()
            pendingFiles.drainTo(remaining)
            val (priority, others) = remaining.partition { it.uniqueKey() in priorityKeys }
            priority.forEach { pendingFiles.offer(it) }
            others.forEach { pendingFiles.offer(it) }
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
            val results = filesToProcess.map { file ->
                async {
                    if (hasCachedThumbnail(file)) return@async true
                    generateThumbnailForFile(file)
                }
            }.awaitAll()

            results.forEachIndexed { index, success ->
                if (!success) {
                    val file = filesToProcess[index]
                    val retryCount = retryCountMap.merge(file.uniqueKey(), 1) { old, _ -> old + 1 } ?: 1
                    if (retryCount <= MAX_RETRY_COUNT) {
                        synchronized(pendingFiles) {
                            pendingFiles.offer(file)
                        }
                    } else {
                        retryCountMap.remove(file.uniqueKey())
                        DDLog.e("ThumbnailGenerator", "缩略图生成已重试${MAX_RETRY_COUNT}次，放弃: ${file.fileName()}")
                    }
                }
            }

            isProcessing = false
            processNextBatch()
        }
    }

    /**
     * 为单个文件生成缩略图
     */
    private suspend fun generateThumbnailForFile(file: StorageFile): Boolean {
        val uniqueKey = file.uniqueKey()

        if (currentTasks.contains(uniqueKey)) {
            return false
        }

        currentTasks.add(uniqueKey)
        var thumbnailGenerated = false

        try {
            val coverFile = uniqueKey.toCoverFile() ?: return false

            if (coverFile.exists() && coverFile.length() > 0) {
                return false
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
            if (thumbnailGenerated) {
                retryCountMap.remove(uniqueKey)
                withContext(Dispatchers.Main) {
                    thumbnailCallback?.onThumbnailGenerated(file)
                }
            }
        }
        return thumbnailGenerated
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
            recycleBitmapToPool(scaledBitmap)
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
            inputStream = file.storage.openFile(file) ?: return@withContext false
            val buffered = java.io.BufferedInputStream(inputStream)
            buffered.mark(256 * 1024)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(buffered, null, options)

            var decodeStream: java.io.InputStream = buffered
            try {
                buffered.reset()
            } catch (e: java.io.IOException) {
                IOUtils.closeIO(inputStream)
                inputStream = file.storage.openFile(file) ?: return@withContext false
                decodeStream = inputStream
            }

            options.inSampleSize = calculateInSampleSize(options, 400, 400)
            options.inJustDecodeBounds = false

            val reusable = obtainReusableBitmap(
                options.outWidth / options.inSampleSize,
                options.outHeight / options.inSampleSize
            )
            if (reusable != null) {
                options.inBitmap = reusable
            }

            val bitmap = BitmapFactory.decodeStream(decodeStream, null, options) ?: return@withContext false

            success = saveBitmapToFile(bitmap, coverFile)
            recycleBitmapToPool(bitmap)
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, fos)
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
        retryCountMap.clear()
        clearBitmapPool()
        isProcessing = false
        _isGenerating.value = false
    }
}
