package com.xyoye.common_component.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.config.ThumbnailConfig
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
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
 * 用于为所有存储类型（本地、SMB、FTP、WebDav 等）的视图片文件生成缩略图并缓存
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

    // 用于序列化 SMB 和 FTP 缩略图生成的互斥锁（按媒体库ID隔离），
    // 防止并发访问单例的 SmbPlayServer / FtpPlayServer 导致状态冲突
    private val storageMutexMap = ConcurrentHashMap<String, Mutex>()

    // Bitmap 复用池，减少GC压力
    private val bitmapPool = LinkedList<Bitmap>()

    // 同目录下已存在的 -thumb.jpg 文件查找表，key 为视频名（不含扩展名）
    private val thumbFileLookup = mutableMapOf<String, StorageFile>()
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
     * 预加载现有缩略图到本地缓存
     * 将目录中已存在的 -thumb.jpg 文件复制到缓存目录
     * 确保 fileCover() 能立即找到缓存，避免显示默认图标
     */
    suspend fun preloadExistingThumbs(allFiles: List<StorageFile>, storage: Storage) {
        coroutineScope {
            allFiles
                .filter { it.fileName().endsWith("-thumb.jpg") }
                .forEach { thumbFile ->
                    launch {
                        val videoName = thumbFile.fileName().removeSuffix("-thumb.jpg")
                        if (videoName.isEmpty()) return@launch
                        val videoFile = allFiles.find {
                            getFileNameNoExtension(it.fileName()) == videoName
                        } ?: return@launch
                        val coverFile = videoFile.uniqueKey().toCoverFile() ?: return@launch
                        if (coverFile.exists() && coverFile.length() > 0) return@launch
                        try {
                            storage.openFile(thumbFile)?.use { input ->
                                coverFile.parentFile?.mkdirs()
                                FileOutputStream(coverFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
        }
    }

    /**
     * 开始为文件列表生成缩略图
     * @param files 文件列表
     * @param storage 所属存储
     * @param priorityKeys 优先处理的文件唯一键集合（当前屏幕可见项）
     */
    fun startGenerateThumbnails(files: List<StorageFile>, storage: Storage, priorityKeys: Set<String> = emptySet(), allFiles: List<StorageFile>? = null) {
        if (!ThumbnailConfig.isGenerateThumbnail()) return

        synchronized(pendingFiles) {
            pendingFiles.clear()
            retryCountMap.clear()

            thumbFileLookup.clear()
            if (allFiles != null) {
                for (f in allFiles) {
                    val name = f.fileName()
                    if (name.endsWith("-thumb.jpg")) {
                        val videoName = name.removeSuffix("-thumb.jpg")
                        thumbFileLookup[videoName] = f
                    }
                }
            }

            val filteredFiles = files.filter { file ->
                val isTargetType = (file.isVideoFile() && ThumbnailConfig.isGenerateForVideo()) ||
                        (file.isImageFile() && ThumbnailConfig.isGenerateForImage())
                isTargetType && !hasCachedThumbnail(file)
            }
            val (priority, others) = filteredFiles.partition { it.uniqueKey() in priorityKeys }
            priority.forEach { pendingFiles.offer(it) }
            others.forEach { pendingFiles.offer(it) }
        }

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

    private fun hasCachedThumbnail(file: StorageFile): Boolean {
        val coverFile = file.uniqueKey().toCoverFile()
        if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
            return true
        }
        if (file.isVideoFile()) {
            val videoName = getFileNameNoExtension(file.fileName())
            if (videoName.isNotEmpty() && videoName in thumbFileLookup) {
                return true
            }
            val thumbPath = buildCustomThumbPath(file) ?: return false
            val sameDirFile = File(thumbPath)
            return sameDirFile.exists() && sameDirFile.isFile && sameDirFile.length() > 0
        }
        return false
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
            if (file.isVideoFile() && !ThumbnailConfig.isGenerateForVideo()) return false
            if (file.isImageFile() && !ThumbnailConfig.isGenerateForImage()) return false

            val coverFile = uniqueKey.toCoverFile() ?: return false

            if (coverFile.exists() && coverFile.length() > 0) {
                return false
            }

            if (file.isVideoFile()) {
                // SMB/FTP 使用单例服务器，需要序列化访问，防止并发冲突
                val mediaType = file.storage.library.mediaType
                if (mediaType == MediaType.SMB_SERVER || mediaType == MediaType.FTP_SERVER) {
                    val libraryId = file.storage.library.id.toString()
                    val mutex = storageMutexMap.getOrPut(libraryId) { Mutex() }
                    thumbnailGenerated = mutex.withLock {
                        generateVideoThumbnail(file, coverFile)
                    }
                } else {
                    thumbnailGenerated = generateVideoThumbnail(file, coverFile)
                }
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
        if (tryCustomThumbnail(file, coverFile)) {
            return@withContext true
        }

        val mediaRetriever = MediaMetadataRetriever()
        var success = false

        try {
            // 获取播放URL
            val playUrl = file.storage.createPlayUrl(file) ?: return@withContext false

            // 设置数据源，支持 content:// URI
            val playUri = Uri.parse(playUrl)
            if (playUri.scheme == "content") {
                mediaRetriever.setDataSource(BaseApplication.getAppContext(), playUri)
            } else {
                val headers = file.storage.getNetworkHeaders()
                if (headers != null) {
                    mediaRetriever.setDataSource(playUrl, headers)
                } else {
                    mediaRetriever.setDataSource(playUrl)
                }
            }

            // 获取视频时长，取10%时间点附近的帧作为缩略图（更易识别视频内容）
            val durationMs = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val targetTimeUs = if (durationMs > 0) durationMs * 100 else 0L
            val bitmap = mediaRetriever.getFrameAtTime(
                targetTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext false

            // 缩放到固定宽度，减少存储和后续加载开销
            val scaledBitmap = resizeBitmap(bitmap, THUMBNAIL_MAX_WIDTH)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }

            val thumbBytes = toJpegBytes(scaledBitmap)

            if (ThumbnailConfig.isSaveInSameDir() && file.isVideoFile()) {
                val sameDirPath = buildCustomThumbPath(file)
                if (sameDirPath != null) {
                    val remoteSaved = file.storage.saveFile(sameDirPath, thumbBytes)
                    if (!remoteSaved) {
                        val fallbackFile = File(sameDirPath)
                        success = saveBitmapToFile(scaledBitmap, fallbackFile) || saveBitmapToFile(scaledBitmap, coverFile)
                    } else {
                        success = true
                    }
                }
            }
            if (!success) {
                success = saveBitmapToFile(scaledBitmap, coverFile)
            }
            if (success) {
                ThumbnailMemoryCache.put(file.uniqueKey(), scaledBitmap)
            } else {
                recycleBitmapToPool(scaledBitmap)
            }
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

    private suspend fun tryCustomThumbnail(file: StorageFile, coverFile: File): Boolean {
        if (!file.isVideoFile()) return false

        val videoName = getFileNameNoExtension(file.fileName())

        if (videoName.isNotEmpty()) {
            val thumbFile = thumbFileLookup[videoName]
            if (thumbFile != null) {
                try {
                    file.storage.openFile(thumbFile)?.use { inputStream ->
                        return decodeAndSaveThumbnail(inputStream, file, coverFile)
                    }
                } catch (_: Exception) {}
            }
        }

        val thumbPath = buildCustomThumbPath(file) ?: return false

        try {
            val localFile = File(thumbPath)
            if (localFile.exists() && localFile.isFile) {
                FileInputStream(localFile).use { inputStream ->
                    return decodeAndSaveThumbnail(inputStream, file, coverFile)
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * 构建自定义缩略图文件路径
     * 使用 storagePath() 以确保 SMB 等存储包含共享名等必要信息
     */
    private fun buildCustomThumbPath(file: StorageFile): String? {
        val fileName = getFileNameNoExtension(file.fileName()).takeIf { it.isNotEmpty() } ?: return null
        val dirPath = getDirPath(file.storagePath()).takeIf { it.isNotEmpty() } ?: return null
        return "$dirPath/$fileName-thumb.jpg"
    }

    /**
     * 解码并保存自定义缩略图
     */
    private fun decodeAndSaveThumbnail(inputStream: InputStream, file: StorageFile, coverFile: File): Boolean {
        val bitmap = BitmapFactory.decodeStream(BufferedInputStream(inputStream)) ?: return false
        val scaledBitmap = resizeBitmap(bitmap, THUMBNAIL_MAX_WIDTH)
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        val success = saveBitmapToFile(scaledBitmap, coverFile)
        if (success) {
            ThumbnailMemoryCache.put(file.uniqueKey(), scaledBitmap)
            return true
        }
        recycleBitmapToPool(scaledBitmap)
        return false
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
            if (success) {
                ThumbnailMemoryCache.put(file.uniqueKey(), bitmap)
            } else {
                recycleBitmapToPool(bitmap)
            }
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

    private fun toJpegBytes(bitmap: Bitmap): ByteArray {
        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, bos)
            return bos.toByteArray()
        }
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
        storageMutexMap.clear()
        clearBitmapPool()
        ThumbnailMemoryCache.clear()
        isProcessing = false
        _isGenerating.value = false
    }
}
