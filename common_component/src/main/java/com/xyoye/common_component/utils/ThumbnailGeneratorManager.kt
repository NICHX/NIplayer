package com.xyoye.common_component.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.config.ThumbnailConfig
import com.xyoye.common_component.config.ThumbnailServerConfig
import com.xyoye.common_component.extension.toAudioCoverFile
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.helper.FtpPlayServer
import com.xyoye.common_component.storage.file.helper.SmbPlayServer
import com.xyoye.common_component.storage.impl.FtpStorage
import com.xyoye.common_component.storage.impl.SmbStorage
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object ThumbnailGeneratorManager {
    private const val THUMBNAIL_MAX_WIDTH = 320
    private const val BATCH_SIZE = 4
    private const val MAX_RETRY_COUNT = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val currentTasks = ConcurrentHashMap.newKeySet<String>()

    private val retryCountMap = ConcurrentHashMap<String, Int>()

    private val nonRetryableFailures = ConcurrentHashMap.newKeySet<String>()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val pendingFiles = LinkedBlockingQueue<StorageFile>()

    private var isProcessing = false

    private var isPaused = false

    private val storageMutexMap = ConcurrentHashMap<String, Mutex>()

    private val thumbFileLookup = mutableMapOf<String, StorageFile>()
    private val existingDotThumbKeys = ConcurrentHashMap.newKeySet<String>()

    interface ThumbnailCallback {
        fun onThumbnailGenerated(file: StorageFile)
    }

    private var thumbnailCallback: ThumbnailCallback? = null

    fun setThumbnailCallback(callback: ThumbnailCallback?) {
        thumbnailCallback = callback
    }

    suspend fun preloadExistingThumbs(allFiles: List<StorageFile>, storage: Storage) {
        if (storage.library.id > 0 && !ThumbnailServerConfig.isServerThumbnailEnabled(storage.library.id)) return
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

            allFiles
                .filter { it.isVideoFile() }
                .forEach { videoFile ->
                    launch {
                        val dotThumbPath = buildDotThumbPath(videoFile) ?: return@launch
                        val coverFile = videoFile.uniqueKey().toCoverFile() ?: return@launch
                        if (coverFile.exists() && coverFile.length() > 0) return@launch

                        try {
                            if (isLocalStorage(videoFile)) {
                                val localFile = File(dotThumbPath)
                                if (localFile.exists() && localFile.isFile && localFile.length() > 0) {
                                    coverFile.parentFile?.mkdirs()
                                    FileInputStream(localFile).use { input ->
                                        FileOutputStream(coverFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            } else if (storage.fileExists(dotThumbPath)) {
                                val thumbFile = videoFile.storage.pathFile(dotThumbPath, false)
                                if (thumbFile != null) {
                                    videoFile.storage.openFile(thumbFile)?.use { input ->
                                        coverFile.parentFile?.mkdirs()
                                        FileOutputStream(coverFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
        }
    }

    suspend fun preloadCoverPaths(allFiles: List<StorageFile>) {
        preloadCoverFileCache(allFiles)
    }

    private suspend fun preloadCoverFileCache(allFiles: List<StorageFile>) = withContext(Dispatchers.IO) {
        for (file in allFiles) {
            val uniqueKey = file.uniqueKey()
            if (uniqueKey.isEmpty()) continue
            if (ThumbnailMemoryCache.getCoverPath(uniqueKey) != null) continue
            val coverFile = if (file.isAudioFile()) {
                uniqueKey.toAudioCoverFile()
            } else {
                uniqueKey.toCoverFile()
            } ?: continue
            if (coverFile.exists() && coverFile.length() > 0) {
                ThumbnailMemoryCache.putCoverPath(uniqueKey, coverFile.absolutePath)
            }
            if (file.isAudioFile() && AudioMetadataCache.get(uniqueKey) == null) {
                AudioMetadataCache.loadFromDisk(uniqueKey)?.let {
                    AudioMetadataCache.put(uniqueKey, it)
                }
            }
        }
    }

    private fun clearCoverFileCache() {
        ThumbnailMemoryCache.clearCoverPathCache()
    }

    private suspend fun preloadDotThumbExistence(allFiles: List<StorageFile>, storage: Storage) = withContext(Dispatchers.IO) {
        val videoFiles = allFiles.filter { it.isVideoFile() && !isLocalStorage(it) }
        if (videoFiles.isEmpty()) return@withContext
        coroutineScope {
            videoFiles.forEach { file ->
                launch {
                    val dotThumbPath = buildDotThumbPath(file) ?: return@launch
                    try {
                        if (storage.fileExists(dotThumbPath)) {
                            existingDotThumbKeys.add(file.uniqueKey())
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun startGenerateThumbnails(files: List<StorageFile>, storage: Storage, priorityKeys: Set<String> = emptySet(), allFiles: List<StorageFile>? = null) {
        if (!ThumbnailConfig.isGenerateThumbnail()) return

        if (storage.library.id > 0 && !ThumbnailServerConfig.isServerThumbnailEnabled(storage.library.id)) return

        synchronized(pendingFiles) {
            pendingFiles.clear()
            retryCountMap.clear()
            clearCoverFileCache()

            thumbFileLookup.clear()
            existingDotThumbKeys.clear()
            nonRetryableFailures.clear()
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
                if (isLocalStorage(file) && !file.isAudioFile()) return@filter false
                val isTargetType = (file.isVideoFile() && ThumbnailConfig.isGenerateForVideo()) ||
                        (file.isImageFile() && ThumbnailConfig.isGenerateForImage()) ||
                        (file.isAudioFile() && ThumbnailConfig.isGenerateForAudio())
                isTargetType && !hasCachedThumbnail(file)
            }
            val (priority, others) = filteredFiles.partition { it.uniqueKey() in priorityKeys }
            priority.forEach { pendingFiles.offer(it) }
            others.forEach { pendingFiles.offer(it) }
        }

        if (allFiles != null) {
            scope.launch {
                preloadDotThumbExistence(allFiles, storage)
            }
        }
    }

    fun pauseGenerateThumbnails() {
        isPaused = true
    }

    fun resumeGenerateThumbnails() {
        isPaused = false
        if (!isProcessing && pendingFiles.isNotEmpty()) {
            processNextBatch()
        }
    }

    fun continueGenerateThumbnails() {
        if (isProcessing) return
        if (isPaused) return
        if (pendingFiles.isNotEmpty()) {
            processNextBatch()
        }
    }

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
        val coverFile = if (file.isAudioFile()) {
            file.uniqueKey().toAudioCoverFile()
        } else {
            file.uniqueKey().toCoverFile()
        }
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
            if (sameDirFile.exists() && sameDirFile.isFile && sameDirFile.length() > 0) {
                return true
            }
            if (!isLocalStorage(file) && file.uniqueKey() in existingDotThumbKeys) {
                return true
            }
        }
        return false
    }

    private fun processNextBatch() {
        if (isProcessing || pendingFiles.isEmpty() || isPaused) return

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
                    try {
                        val fileTimeout = when {
                            file.isVideoFile() -> 30_000L
                            else -> 15_000L
                        }
                        withTimeout(fileTimeout) {
                            if (hasCachedThumbnail(file)) return@withTimeout true
                            generateThumbnailForFile(file)
                        }
                    } catch (e: Exception) {
                        DDLog.e("ThumbnailGenerator", "缩略图生成超时或失败: ${file.fileName()}")
                        false
                    }
                }
            }.awaitAll()

            results.forEachIndexed { index, success ->
                if (!success) {
                    val file = filesToProcess[index]
                    if (file.uniqueKey() in nonRetryableFailures) {
                        nonRetryableFailures.remove(file.uniqueKey())
                        retryCountMap.remove(file.uniqueKey())
                        return@forEachIndexed
                    }
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
            if (!isPaused) {
                processNextBatch()
            }
        }
    }

    private suspend fun generateThumbnailForFile(file: StorageFile): Boolean {
        val uniqueKey = file.uniqueKey()

        if (currentTasks.contains(uniqueKey)) return false

        currentTasks.add(uniqueKey)
        var thumbnailGenerated = false

        try {
            if (file.isVideoFile() && !ThumbnailConfig.isGenerateForVideo()) return false
            if (file.isImageFile() && !ThumbnailConfig.isGenerateForImage()) return false
            if (file.isAudioFile() && !ThumbnailConfig.isGenerateForAudio()) return false

            val coverFile = if (file.isAudioFile()) {
                uniqueKey.toAudioCoverFile() ?: return false
            } else {
                uniqueKey.toCoverFile() ?: return false
            }

            if (coverFile.exists() && coverFile.length() > 0) return false

            if (file.isVideoFile()) {
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
                val mediaType = file.storage.library.mediaType
                if (mediaType == MediaType.SMB_SERVER || mediaType == MediaType.FTP_SERVER) {
                    val libraryId = file.storage.library.id.toString()
                    val mutex = storageMutexMap.getOrPut(libraryId) { Mutex() }
                    thumbnailGenerated = mutex.withLock {
                        generateImageThumbnail(file, coverFile)
                    }
                } else {
                    thumbnailGenerated = generateImageThumbnail(file, coverFile)
                }
            } else if (file.isAudioFile()) {
                val mediaType = file.storage.library.mediaType
                if (mediaType == MediaType.SMB_SERVER || mediaType == MediaType.FTP_SERVER) {
                    val libraryId = file.storage.library.id.toString()
                    val mutex = storageMutexMap.getOrPut(libraryId) { Mutex() }
                    thumbnailGenerated = mutex.withLock {
                        generateAudioThumbnail(file, coverFile)
                    }
                } else {
                    thumbnailGenerated = generateAudioThumbnail(file, coverFile)
                }
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

    private suspend fun generateVideoThumbnail(file: StorageFile, coverFile: File): Boolean = withContext(Dispatchers.IO) {
        if (tryCustomThumbnail(file, coverFile)) return@withContext true

        var success = false
        var usedFallback = false

        try {
            val source = file.storage.createPlayUrl(file) ?: return@withContext false
            val playUri = Uri.parse(source)

            val retriever = MediaMetadataRetriever()
            try {
                if (playUri.scheme == "content") {
                    retriever.setDataSource(BaseApplication.getAppContext(), playUri)
                } else {
                    run {
                        val headers = file.storage.getNetworkHeaders()
                        if (headers != null) {
                            try {
                                retriever.setDataSource(source, headers)
                            } catch (_: Exception) {
                                if (usedFallback) return@run
                                retriever.release()
                                usedFallback = true
                                return@run
                            }
                        } else {
                            retriever.setDataSource(source)
                        }
                    }
                    if (usedFallback) return@withContext fallbackVideoThumbnail(file, coverFile)

                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    if (durationMs > 0 && durationMs < 5_000) return@withContext false
                    val targetTimeUs = if (durationMs > 0) durationMs * 100 else 0L

                    val positionsToTry = buildList {
                        add(targetTimeUs)
                        if (durationMs > 0) add(durationMs * 500)
                        add(0L)
                    }
                    var rawBitmap: Bitmap? = null
                    for (pos in positionsToTry) {
                        rawBitmap = retriever.getFrameAtTime(pos, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (rawBitmap != null) break
                    }
                    if (rawBitmap == null) {
                        nonRetryableFailures.add(file.uniqueKey())
                        return@withContext false
                    }

                    val bitmap = resizeBitmap(rawBitmap, THUMBNAIL_MAX_WIDTH)
                    if (bitmap != rawBitmap) rawBitmap.recycle()

                    val thumbBytes = toJpegBytes(bitmap)
                    success = saveBitmapToFile(bitmap, coverFile, file.uniqueKey())
                    bitmap.recycle()

                    if (success && !isLocalStorage(file)) {
                        val dotThumbPath = buildDotThumbPath(file)
                        if (dotThumbPath != null) {
                            val dotThumbDir = getDirPath(dotThumbPath)
                            if (file.storage.createDirectory(dotThumbDir)) {
                                file.storage.saveFile(dotThumbPath, thumbBytes)
                            }
                        }
                    }
                }
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "视频缩略图生成失败: ${file.fileName()}", e)
        } finally {
            cleanupPlayUrl(file)
        }
        if (usedFallback) return@withContext fallbackVideoThumbnail(file, coverFile)
        return@withContext success
    }

    private suspend fun fallbackVideoThumbnail(file: StorageFile, coverFile: File): Boolean = withContext(Dispatchers.IO) {
        if (tryCustomThumbnail(file, coverFile)) return@withContext true

        val fileInfo = file.storage.fileInfo(file) ?: return@withContext false
        val fileSize = fileInfo.fileSize
        if (fileSize <= 0 || fileSize > 50 * 1024 * 1024) return@withContext false

        try {
            val inputStream = file.storage.openFile(file) ?: return@withContext false
            val videoBytes = inputStream.use { it.readBytes() }
            if (videoBytes.isEmpty()) return@withContext false

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(object : MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position >= videoBytes.size) return -1
                        val count = minOf(size, videoBytes.size - position.toInt())
                        System.arraycopy(videoBytes, position.toInt(), buffer, offset, count)
                        return count
                    }

                    override fun getSize(): Long = videoBytes.size.toLong()
                    override fun close() {}
                })

                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durationMs > 0 && durationMs < 5_000) return@withContext false
                val targetTimeUs = if (durationMs > 0) durationMs * 100 else 0L

                val positionsToTry = buildList {
                    add(targetTimeUs)
                    if (durationMs > 0) add(durationMs * 500)
                    add(0L)
                }
                var rawBitmap: Bitmap? = null
                for (pos in positionsToTry) {
                    rawBitmap = retriever.getFrameAtTime(pos, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (rawBitmap != null) break
                }
                if (rawBitmap == null) return@withContext false

                val bitmap = resizeBitmap(rawBitmap, THUMBNAIL_MAX_WIDTH)
                if (bitmap != rawBitmap) rawBitmap.recycle()

                val thumbBytes = toJpegBytes(bitmap)
                val saved = saveBitmapToFile(bitmap, coverFile, file.uniqueKey())
                bitmap.recycle()

                if (saved && !isLocalStorage(file)) {
                    val dotThumbPath = buildDotThumbPath(file)
                    if (dotThumbPath != null) {
                        val dotThumbDir = getDirPath(dotThumbPath)
                        if (file.storage.createDirectory(dotThumbDir)) {
                            file.storage.saveFile(dotThumbPath, thumbBytes)
                        }
                    }
                }

                return@withContext saved
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "视频缩略图生成fallback失败: ${file.fileName()}", e)
            return@withContext false
        }
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

        val dotThumbPath = buildDotThumbPath(file)
        if (dotThumbPath != null) {
            if (isLocalStorage(file)) {
                try {
                    val localFile = File(dotThumbPath)
                    if (localFile.exists() && localFile.isFile) {
                        FileInputStream(localFile).use { inputStream ->
                            return decodeAndSaveThumbnail(inputStream, file, coverFile)
                        }
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    if (file.storage.fileExists(dotThumbPath)) {
                        val thumbFile = file.storage.pathFile(dotThumbPath, false)
                        if (thumbFile != null) {
                            file.storage.openFile(thumbFile)?.use { inputStream ->
                                return decodeAndSaveThumbnail(inputStream, file, coverFile)
                            }
                        }
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

    private fun buildCustomThumbPath(file: StorageFile): String? {
        val fileName = getFileNameNoExtension(file.fileName()).takeIf { it.isNotEmpty() } ?: return null
        val dirPath = getDirPath(file.storagePath()).takeIf { it.isNotEmpty() } ?: return null
        return "$dirPath/$fileName-thumb.jpg"
    }

    private fun buildDotThumbPath(file: StorageFile): String? {
        val fileName = getFileNameNoExtension(file.fileName()).takeIf { it.isNotEmpty() } ?: return null
        val dirPath = getDirPath(file.storagePath()).takeIf { it.isNotEmpty() } ?: return null
        return "$dirPath/.thumb/$fileName-thumb.jpg"
    }

    private fun isLocalStorage(file: StorageFile): Boolean {
        val mediaType = file.storage.library.mediaType
        return mediaType == MediaType.LOCAL_STORAGE ||
                mediaType == MediaType.OTHER_STORAGE ||
                mediaType == MediaType.EXTERNAL_STORAGE
    }

    private fun decodeAndSaveThumbnail(inputStream: InputStream, file: StorageFile, coverFile: File): Boolean {
        val bitmap = BitmapFactory.decodeStream(BufferedInputStream(inputStream)) ?: return false
        val scaledBitmap = resizeBitmap(bitmap, THUMBNAIL_MAX_WIDTH)
        if (scaledBitmap != bitmap) bitmap.recycle()
        val success = saveBitmapToFile(scaledBitmap, coverFile, file.uniqueKey())
        scaledBitmap.recycle()
        return success
    }

    private suspend fun generateImageThumbnail(file: StorageFile, coverFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val headers = file.storage.getNetworkHeaders()
            val bitmap = if (isLocalStorage(file)) {
                Glide.with(BaseApplication.getAppContext())
                    .asBitmap()
                    .load(File(file.storagePath()))
                    .override(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_WIDTH)
                    .apply(RequestOptions().apply {
                        format(DecodeFormat.PREFER_RGB_565)
                        skipMemoryCache(true)
                        diskCacheStrategy(DiskCacheStrategy.NONE)
                    })
                    .submit()
                    .get(15_000, TimeUnit.MILLISECONDS) ?: return@withContext false
            } else if (headers != null && headers.isNotEmpty()) {
                val inputStream = file.storage.openFile(file) ?: return@withContext false
                inputStream.use { stream ->
                    BitmapFactory.decodeStream(BufferedInputStream(stream))?.let { rawBitmap ->
                        resizeBitmap(rawBitmap, THUMBNAIL_MAX_WIDTH)
                    } ?: return@withContext false
                }
            } else {
                val source = file.storage.createPlayUrl(file) ?: return@withContext false
                Glide.with(BaseApplication.getAppContext())
                    .asBitmap()
                    .load(source)
                    .override(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_WIDTH)
                    .apply(RequestOptions().apply {
                        format(DecodeFormat.PREFER_RGB_565)
                        skipMemoryCache(true)
                        diskCacheStrategy(DiskCacheStrategy.NONE)
                    })
                    .submit()
                    .get(15_000, TimeUnit.MILLISECONDS) ?: return@withContext false
            }

            val success = saveBitmapToFile(bitmap, coverFile, file.uniqueKey())
            bitmap.recycle()
            return@withContext success
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "图片缩略图生成失败: ${file.fileName()}", e)
            return@withContext false
        }
    }

    private suspend fun generateAudioThumbnail(file: StorageFile, coverFile: File): Boolean = withContext(Dispatchers.IO) {
        val mediaRetriever = MediaMetadataRetriever()
        var success = false

        try {
            val playUrl = file.storage.createPlayUrl(file) ?: return@withContext false
            val playUri = Uri.parse(playUrl)
            if (playUri.scheme == "content") {
                mediaRetriever.setDataSource(BaseApplication.getAppContext(), playUri)
            } else {
                val headers = file.storage.getNetworkHeaders()
                if (headers != null) mediaRetriever.setDataSource(playUrl, headers)
                else mediaRetriever.setDataSource(playUrl)
            }

            val artist = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val albumArtist = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val title = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val duration = mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            if (duration > 0 && duration < 60_000) {
                nonRetryableFailures.add(file.uniqueKey())
                return@withContext false
            }

            val resolvedArtist = artist?.takeIf { it.isNotEmpty() }
                ?: albumArtist?.takeIf { it.isNotEmpty() }
                ?: ""
            val metadata = AudioMetadata(
                artist = resolvedArtist,
                title = title ?: "",
                duration = duration
            )
            AudioMetadataCache.put(file.uniqueKey(), metadata)
            AudioMetadataCache.saveToDisk(file.uniqueKey(), metadata)

            val picture = mediaRetriever.embeddedPicture
            if (picture == null) {
                nonRetryableFailures.add(file.uniqueKey())
                return@withContext false
            }
            val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
            if (bitmap == null) {
                nonRetryableFailures.add(file.uniqueKey())
                return@withContext false
            }

            val scaledBitmap = resizeBitmap(bitmap, THUMBNAIL_MAX_WIDTH)
            if (scaledBitmap != bitmap) bitmap.recycle()

            success = saveBitmapToFile(scaledBitmap, coverFile, file.uniqueKey(), quality = 100)
            scaledBitmap.recycle()
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "音频封面提取失败: ${file.fileName()}", e)
        } finally {
            try { mediaRetriever.release() } catch (_: Exception) {}
            cleanupPlayUrl(file)
        }
        return@withContext success
    }

    private fun cleanupPlayUrl(file: StorageFile) {
        val storage = file.storage
        val urlPath = "/" + file.uniqueKey()
        when (storage) {
            is SmbStorage -> SmbPlayServer.getInstance().removePlayUrl(urlPath)
            is FtpStorage -> FtpPlayServer.getInstance().removePlayUrl(urlPath)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth) return bitmap
        val scale = maxWidth.toFloat() / width
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File, uniqueKey: String? = null, quality: Int = 60): Boolean {
        var fos: FileOutputStream? = null
        var success = false
        try {
            file.parentFile?.mkdirs()

            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
            success = true
        } catch (e: Exception) {
            DDLog.e("ThumbnailGenerator", "保存缩略图失败", e)
            if (file.exists()) file.delete()
        } finally {
            IOUtils.closeIO(fos)
        }
        if (success && uniqueKey != null) {
            ThumbnailMemoryCache.putCoverPath(uniqueKey, file.absolutePath)
        }
        return success
    }

    private fun toJpegBytes(bitmap: Bitmap, quality: Int = 60): ByteArray {
        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            return bos.toByteArray()
        }
    }

    suspend fun ensureThumbnail(file: StorageFile): Boolean {
        if (!ThumbnailConfig.isGenerateThumbnail()) return false
        if (file.storage.library.id > 0 && !ThumbnailServerConfig.isServerThumbnailEnabled(file.storage.library.id)) return false

        val uniqueKey = file.uniqueKey()
        if (uniqueKey.isEmpty()) return false

        val coverFile = if (file.isAudioFile()) {
            uniqueKey.toAudioCoverFile()
        } else {
            uniqueKey.toCoverFile()
        }
        if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
            ThumbnailMemoryCache.putCoverPath(uniqueKey, coverFile.absolutePath)
            return true
        }

        return generateThumbnailForFile(file)
    }

    suspend fun saveBitmapToServer(file: StorageFile, bitmap: Bitmap) {
        if (isLocalStorage(file)) return
        val thumbBytes = toJpegBytes(bitmap)

        val dotThumbPath = buildDotThumbPath(file)
        if (dotThumbPath != null) {
            val dotThumbDir = getDirPath(dotThumbPath)
            if (!file.storage.createDirectory(dotThumbDir)) return
            file.storage.saveFile(dotThumbPath, thumbBytes)
        }
    }

    fun clearPendingTasks() {
        synchronized(pendingFiles) { pendingFiles.clear() }
        currentTasks.clear()
        retryCountMap.clear()
        nonRetryableFailures.clear()
        existingDotThumbKeys.clear()
        storageMutexMap.clear()
        ThumbnailMemoryCache.clear()
        isProcessing = false
        _isGenerating.value = false
    }
}
