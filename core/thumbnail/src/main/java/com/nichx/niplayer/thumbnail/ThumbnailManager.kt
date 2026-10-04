package com.nichx.niplayer.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缩略图生成与缓存门面（重写版）。
 *
 * 本类只保留「公开 API + 编排 + 并发控制」三件事；具体逻辑下沉到单一职责的协作者：
 * - 缓存存取/淘汰/清理 → [ThumbnailStore]
 * - 视频取帧 → [VideoFrameExtractor]
 * - 音频封面 → [AudioCoverExtractor]
 * - 图片缩略图 → [ImageThumbnailExtractor]
 * - 服务端 `.thumb`/`.cover` 同步 → [RemoteThumbnailSync]
 *
 * 与旧实现的差异（简化）：
 * - 移除失败冷却 TTL 表、`mutexMap` 逐键清理；
 * - 并发去重改为固定大小的**条带锁池**（按 key 哈希取锁，有界、无需清理）；
 * - 取帧并发按 storageId 统一收口到 [extractionGates]（见该字段说明）。
 * - 缓存文件名 `MD5("$storageId-$filePath").jpg` 与 `.thumb`/`.cover` 命名约定**保持不变**。
 *
 * 双层缓存：
 * 1. 本地：`cacheDir/{video_cover,audio_cover,image_thumb}/`，文件名 = MD5(storageId-filePath).jpg
 * 2. 服务端：`{文件目录}/.thumb/{名}-thumb.jpg`（视频）/ `.cover/{名}-cover.jpg`（音频）
 */
@Singleton
class ThumbnailManager @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val store = ThumbnailStore(context)
    private val videoExtractor = VideoFrameExtractor(context)
    private val audioExtractor = AudioCoverExtractor(context, store)
    private val imageExtractor = ImageThumbnailExtractor(store)
    private val remoteSync = RemoteThumbnailSync(store)

    /** 缩略图更新事件：保存缩略图后发出对应缓存文件路径（供 UI 追加 `?t=` 绕过内存缓存）。 */
    private val _thumbnailUpdated = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val thumbnailUpdated: SharedFlow<String> = _thumbnailUpdated.asSharedFlow()

    /**
     * 固定大小的条带锁池：按缓存文件名哈希取锁，保证同一文件的生成被串行化（防重），
     * 同时避免旧实现「每个文件一个 Mutex + 事后清理」的复杂性与内存累积。
     */
    private val lockStripes = Array(LOCK_STRIPES) { Mutex() }

    private fun lockFor(key: String): Mutex = lockStripes[(key.hashCode() and Int.MAX_VALUE) % LOCK_STRIPES]

    /**
     * 按存储源统一的取帧闸门。
     *
     * 文件浏览（[com.nichx.niplayer.feature.home.library.StorageFileViewModel]）与首页/历史/快速访问
     * （[generateRemoteThumbnails]）是两条独立管线，各自按 [Storage.thumbnailConcurrency] 限流；
     * 二者同时运行时并发取帧数会翻倍。对 SMB 而言每个取帧任务还会向共享读线程池申请多条通道，
     * 叠加后会把线程池与链路全部挤满，表现为「一直转圈」。
     *
     * 这里按 storageId 收口，保证同一存储源真正同时取帧的任务数不超过其建议并发值，与线程池容量对齐。
     */
    private val extractionGates = ConcurrentHashMap<Int, Semaphore>()

    private fun extractionGate(storageId: Int, permits: Int): Semaphore =
        extractionGates.computeIfAbsent(storageId) { Semaphore(permits.coerceAtLeast(1)) }

    // ---------- 视频缩略图 ----------

    /**
     * 生成视频缩略图（纯本地生成 + 缓存，不含上传）。
     *
     * @param positionKey 取帧位置策略 key（见 [ThumbnailSettings.framePositionKey]）
     */
    suspend fun generateThumbnail(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
        positionKey: String = DEFAULT_POSITION_KEY,
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        require(MediaFileTypes.isVideoFile(file.name)) {
            "generateThumbnail 要求视频文件，收到 ${file.name}"
        }
        val cacheFile = store.fileFor(store.videoDir, storageId, file.path)
        if (cacheFile.exists()) return@withContext ThumbnailResult.Success(cacheFile.absolutePath)

        val startedAt = SystemClock.elapsedRealtime()
        val result = lockFor(cacheFile.name).withLock {
            if (cacheFile.exists()) return@withLock ThumbnailResult.Success(cacheFile.absolutePath)
            val skipDurationCheck = storage.library.mediaType == MediaType.LOCAL_STORAGE
            extractionGate(storageId, storage.thumbnailConcurrency).withPermit {
                when (val extraction = videoExtractor.extract(storage, file, positionKey, skipDurationCheck)) {
                    is FrameExtraction.Ok -> ThumbnailResult.Success(
                        store.writeJpeg(cacheFile, extraction.bitmap),
                    )
                    FrameExtraction.Failed -> ThumbnailResult.Failed
                    FrameExtraction.PermanentFailure -> ThumbnailResult.PermanentFailure
                }
            }
        }
        store.trimIfNeeded(store.videoDir)
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (result is ThumbnailResult.Success) {
            Log.d(TAG, "generateThumbnail ok: ${file.name} ${elapsedMs}ms")
        } else {
            // 失败/过短此前无日志，无法与「远程读取太慢」区分，此处补齐便于 adb 定位。
            Log.w(TAG, "generateThumbnail failed: ${file.name} result=$result ${elapsedMs}ms")
        }
        result
    }

    /**
     * 在精确位置 [positionMs] 生成缩略图并覆盖本地缓存（退出播放时以最后播放帧更新）。
     *
     * 先写临时文件，取帧成功后才原子覆盖正式缓存；失败保留旧图。
     */
    suspend fun generateThumbnailAtMs(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
        positionMs: Long,
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        require(MediaFileTypes.isVideoFile(file.name)) {
            "generateThumbnailAtMs 要求视频文件，收到 ${file.name}"
        }
        val cacheFile = store.fileFor(store.videoDir, storageId, file.path)
        val tmpFile = File(store.videoDir, "${cacheFile.nameWithoutExtension}.tmp.jpg")

        // 落临时文件 + 原子覆盖均在锁内完成，避免同一文件并发时 tmp 被彼此覆盖/改名
        val result = lockFor(cacheFile.name).withLock {
            val skipDurationCheck = storage.library.mediaType == MediaType.LOCAL_STORAGE
            when (val extraction = videoExtractor.extractAt(storage, file, positionMs, skipDurationCheck)) {
                is FrameExtraction.Ok -> {
                    store.writeJpeg(tmpFile, extraction.bitmap)
                    if (tmpFile.renameTo(cacheFile)) {
                        ThumbnailResult.Success(cacheFile.absolutePath)
                    } else {
                        tmpFile.delete()
                        ThumbnailResult.Failed
                    }
                }
                FrameExtraction.Failed -> {
                    tmpFile.delete()
                    ThumbnailResult.Failed
                }
                FrameExtraction.PermanentFailure -> {
                    tmpFile.delete()
                    ThumbnailResult.PermanentFailure
                }
            }
        }
        store.trimIfNeeded(store.videoDir)
        if (result is ThumbnailResult.Success) _thumbnailUpdated.tryEmit(cacheFile.absolutePath)
        result
    }

    /**
     * 保存调用方已解码的 Bitmap 为缩略图（退出播放时 PixelCopy 抓帧）。
     *
     * @param isHdr 防御性参数：HDR 播放的抓帧一般已被调用方拦截，正常流程不会走到这里
     */
    suspend fun saveThumbnailFromBitmap(
        storageId: Int,
        file: StorageFile,
        bitmap: Bitmap,
        isHdr: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        require(MediaFileTypes.isVideoFile(file.name)) {
            "saveThumbnailFromBitmap 要求视频文件，收到 ${file.name}"
        }
        val cacheFile = store.fileFor(store.videoDir, storageId, file.path)
        try {
            val compensated = if (isHdr) applyHdrToneMapCompensation(bitmap) else bitmap
            val scaled = scaleToMaxWidth(compensated, MAX_WIDTH)
            store.writeJpeg(cacheFile, scaled)
            // 回收语义：原 bitmap 归调用方 GC 管理不可回收；仅回收本方法产生/缩放的副本
            if (scaled !== compensated) scaled.recycle()
            if (compensated !== bitmap) compensated.recycle()
            store.trimIfNeeded(store.videoDir)
            _thumbnailUpdated.tryEmit(cacheFile.absolutePath)
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "saveThumbnailFromBitmap failed: ${e.message}")
            null
        }
    }

    // ---------- 音频封面 ----------

    /** 生成音频封面（内嵌 / 目录 / 头部扫描）；无封面返回 null 并标记 no_cover。 */
    suspend fun generateAudioCover(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
    ): String? = withContext(Dispatchers.IO) {
        require(MediaFileTypes.isAudioFile(file.name)) {
            "generateAudioCover 要求音频文件，收到 ${file.name}"
        }
        val cacheFile = store.fileFor(store.audioDir, storageId, file.path)
        if (cacheFile.exists()) return@withContext cacheFile.absolutePath

        val result = lockFor(cacheFile.name).withLock {
            if (cacheFile.exists()) return@withLock cacheFile.absolutePath
            extractionGate(storageId, storage.thumbnailConcurrency).withPermit {
                if (audioExtractor.extract(storage, storageId, file, cacheFile)) cacheFile.absolutePath else null
            }
        }
        store.trimIfNeeded(store.audioDir)
        result
    }

    // ---------- 图片缩略图 ----------

    /** 生成图片缩略图（单次流式降采样解码）。 */
    suspend fun generateImageThumbnail(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
    ): String? = withContext(Dispatchers.IO) {
        require(MediaFileTypes.isImageFile(file.name)) {
            "generateImageThumbnail 要求图片文件，收到 ${file.name}"
        }
        val cacheFile = store.fileFor(store.imageDir, storageId, file.path)
        if (cacheFile.exists()) return@withContext cacheFile.absolutePath

        val ok = lockFor(cacheFile.name).withLock {
            if (cacheFile.exists()) return@withLock true
            extractionGate(storageId, storage.thumbnailConcurrency).withPermit {
                imageExtractor.extract(storage, file, cacheFile)
            }
        }
        store.trimIfNeeded(store.imageDir)
        if (ok) cacheFile.absolutePath else null
    }

    // ---------- 远程批量生成 ----------

    /**
     * 为远程存储的文件批量生成缩略图（视频取帧 / 音频封面）。
     *
     * 流程（受 [ThumbnailSettings.effectiveMode] 门控）：
     * - OFF：全部跳过
     * - 仅播放后生成：只 preload 服务端已有缓存，跳过浏览时批量取帧
     * - 全部生成：preload 后用 [Semaphore] 并发生成剩余项，成功项回写服务端
     */
    suspend fun generateRemoteThumbnails(
        storage: Storage,
        requests: List<RemoteThumbnailRequest>,
        onLoaded: (url: String, thumbPath: String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!ThumbnailSettings.generateThumbnail) return@withContext
        if (requests.isEmpty()) return@withContext
        val storageId = storage.library.id

        val mode = ThumbnailSettings.effectiveMode(storageId)
        if (mode == ThumbnailGenerationMode.OFF) return@withContext
        val browseGenerationAllowed = mode == ThumbnailGenerationMode.ALL

        if (ThumbnailSettings.generateForVideo) {
            generateRemoteVideos(
                storage, storageId, requests.filter { !it.isAudio && !it.isImage }, browseGenerationAllowed, onLoaded,
            )
        }
        if (ThumbnailSettings.generateForImage) {
            generateRemoteImages(storage, storageId, requests.filter { it.isImage }, browseGenerationAllowed, onLoaded)
        }
        if (ThumbnailSettings.generateForAudio) {
            generateRemoteAudios(storage, storageId, requests.filter { it.isAudio }, browseGenerationAllowed, onLoaded)
        }
    }

    private suspend fun generateRemoteVideos(
        storage: Storage,
        storageId: Int,
        group: List<RemoteThumbnailRequest>,
        browseGenerationAllowed: Boolean,
        onLoaded: (url: String, thumbPath: String) -> Unit,
    ) {
        if (group.isEmpty()) return
        val videoFiles = group.map { it.toStorageFile() }
        val urlByFilePath = group.associate { it.filePath to it.url }
        val loaded = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        remoteSync.preloadThumbnails(storage, storageId, videoFiles, onLoaded = { filePath, thumbPath ->
            urlByFilePath[filePath]?.let { url -> if (loaded.add(url)) onLoaded(url, thumbPath) }
        })

        val remaining = group.filter { it.url !in loaded }
        if (!browseGenerationAllowed || remaining.isEmpty()) return
        val semaphore = Semaphore(minOf(storage.thumbnailConcurrency, remaining.size))
        val successFiles = java.util.Collections.synchronizedList(mutableListOf<StorageFile>())
        coroutineScope {
            for (req in remaining) {
                launch {
                    semaphore.withPermit {
                        try {
                            val file = req.toStorageFile()
                            val result = generateThumbnail(storage, storageId, file, ThumbnailSettings.framePositionKey)
                            if (result is ThumbnailResult.Success) {
                                onLoaded(req.url, result.path)
                                successFiles.add(file)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "generateRemoteThumbnails video failed: ${e.message}", e)
                        }
                    }
                }
            }
        }
        uploadInParallel(storage, successFiles) { remoteSync.uploadThumbnail(storage, it) }
    }

    /**
     * 为远程图片批量生成缩略图（本地缓存查看 + 本地降采样解码，无服务端同步）。
     *
     * 图片无 `.thumb/` 服务端缓存约定，因此仅：命中本地缓存回填 + 浏览允许时并发生成。
     */
    private suspend fun generateRemoteImages(
        storage: Storage,
        storageId: Int,
        group: List<RemoteThumbnailRequest>,
        browseGenerationAllowed: Boolean,
        onLoaded: (url: String, thumbPath: String) -> Unit,
    ) {
        if (group.isEmpty()) return
        for (req in group) {
            val cached = store.cachedPath(store.imageDir, storageId, req.filePath)
            if (cached != null) onLoaded(req.url, cached)
        }
        if (!browseGenerationAllowed) return
        val remaining = group.filter { store.cachedPath(store.imageDir, storageId, it.filePath) == null }
        if (remaining.isEmpty()) return
        val semaphore = Semaphore(minOf(storage.thumbnailConcurrency, remaining.size))
        coroutineScope {
            for (req in remaining) {
                launch {
                    semaphore.withPermit {
                        try {
                            val file = req.toStorageFile()
                            val path = generateImageThumbnail(storage, storageId, file)
                            if (path != null) onLoaded(req.url, path)
                        } catch (e: Exception) {
                            Log.w(TAG, "generateRemoteThumbnails image failed: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun generateRemoteAudios(
        storage: Storage,
        storageId: Int,
        group: List<RemoteThumbnailRequest>,
        browseGenerationAllowed: Boolean,
        onLoaded: (url: String, thumbPath: String) -> Unit,
    ) {
        if (group.isEmpty()) return
        val audioFiles = group.map { it.toStorageFile() }
        val urlByFilePath = group.associate { it.filePath to it.url }
        val loaded = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        remoteSync.preloadAudioCovers(storage, storageId, audioFiles, onLoaded = { filePath, coverPath ->
            urlByFilePath[filePath]?.let { url -> if (loaded.add(url)) onLoaded(url, coverPath) }
        })

        val remaining = group.filter { it.url !in loaded }
        if (!browseGenerationAllowed || remaining.isEmpty()) return
        val semaphore = Semaphore(minOf(storage.thumbnailConcurrency, remaining.size))
        val successFiles = java.util.Collections.synchronizedList(mutableListOf<StorageFile>())
        coroutineScope {
            for (req in remaining) {
                launch {
                    semaphore.withPermit {
                        try {
                            val file = req.toStorageFile()
                            val path = generateAudioCover(storage, storageId, file)
                            if (path != null) {
                                onLoaded(req.url, path)
                                successFiles.add(file)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "generateRemoteThumbnails audio failed: ${e.message}", e)
                        }
                    }
                }
            }
        }
        uploadInParallel(storage, successFiles) { remoteSync.uploadAudioCover(storage, it) }
    }

    private suspend fun uploadInParallel(
        storage: Storage,
        files: List<StorageFile>,
        upload: suspend (StorageFile) -> Unit,
    ) {
        if (!ThumbnailSettings.effectiveWriteBack(storage.library.id) || files.isEmpty()) return
        val semaphore = Semaphore(minOf(storage.thumbnailConcurrency, files.size))
        coroutineScope {
            for (file in files) {
                launch {
                    semaphore.withPermit {
                        try {
                            upload(file)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "upload failed: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    private fun RemoteThumbnailRequest.toStorageFile(): StorageFile =
        object : AbstractStorageFile(path = filePath, name = fileName, isDirectory = false) {}

    // ---------- 预加载 / 上传 / 服务端同步（委托） ----------

    suspend fun preloadThumbnails(
        storage: Storage,
        storageId: Int,
        files: List<StorageFile>,
        onLoaded: (String, String) -> Unit,
        sameDirFiles: List<StorageFile>? = null,
    ) = remoteSync.preloadThumbnails(storage, storageId, files, onLoaded, sameDirFiles)

    suspend fun preloadAudioCovers(
        storage: Storage,
        storageId: Int,
        files: List<StorageFile>,
        onLoaded: (String, String) -> Unit,
    ) = remoteSync.preloadAudioCovers(storage, storageId, files, onLoaded)

    suspend fun uploadThumbnail(storage: Storage, file: StorageFile) = remoteSync.uploadThumbnail(storage, file)

    suspend fun uploadAudioCover(storage: Storage, file: StorageFile) = remoteSync.uploadAudioCover(storage, file)

    suspend fun deleteThumbnailsForVideo(storage: Storage, storageId: Int, file: StorageFile) =
        remoteSync.deleteThumbnailsForVideo(storage, storageId, file)

    suspend fun deleteServerThumbnail(storage: Storage, file: StorageFile) =
        remoteSync.deleteServerThumbnail(storage, file)

    suspend fun renameServerThumbnail(storage: Storage, oldFile: StorageFile, newFileName: String) =
        remoteSync.renameServerThumbnail(storage, oldFile, newFileName)

    // ---------- 缓存查询 / 清理 ----------

    /** 本地视频缩略图缓存路径（存在则返回，否则 null）。 */
    fun getCachedThumbnailPath(storageId: Int, filePath: String): String? =
        store.cachedPath(store.videoDir, storageId, filePath)

    /** 本地音频封面缓存路径（存在则返回，否则 null）。 */
    fun getCachedAudioCoverPath(storageId: Int, filePath: String): String? =
        store.cachedPath(store.audioDir, storageId, filePath)

    /** 本地图片缩略图缓存路径（存在则返回，否则 null）。 */
    fun getCachedImageThumbnailPath(storageId: Int, filePath: String): String? =
        store.cachedPath(store.imageDir, storageId, filePath)

    /** 该音频是否已确认无内嵌封面。 */
    fun hasNoCover(storageId: Int, filePath: String): Boolean = store.hasNoCover(storageId, filePath)

    /** 清空全部缩略图缓存。 */
    fun clearCache() {
        store.clearAll()
        audioExtractor.clearDirCoverCache()
    }

    /** 按 storageId + 文件列表细粒度清理缓存（不影响其他文件）。 */
    fun clearCache(storageId: Int, files: List<StorageFile>) {
        store.clearFor(storageId, files)
    }

    internal companion object {
        const val TAG = "ThumbnailManager"

        /** 缩略图最大宽度（px）。480 在常见列表宽度下足够清晰。 */
        const val MAX_WIDTH = 480

        /** JPEG 压缩质量。90 显著减少 artifacts。 */
        const val JPEG_QUALITY = 90

        /** 图片缩略图 JPEG 质量。480px 小图 82 与 90 视觉几乎无差，编码更快。 */
        const val IMAGE_JPEG_QUALITY = 82

        /** 短视频阈值：小于此时长（未跳过检查）取第一个关键帧。 */
        const val MIN_DURATION_MS = 15000L

        /** 单个缓存目录大小上限（200MB），超出按 lastModified 淘汰最旧。 */
        const val MAX_CACHE_BYTES = 200L * 1024 * 1024

        /** 同一缓存目录两次容量淘汰扫描的最小间隔（10s）。 */
        const val CACHE_TRIM_INTERVAL_MS = 10_000L

        /** HDR 软件色调映射线性增益（HLG/PQ → SDR 近似）。 */
        const val HDR_TONE_MAP_GAIN = 3.0f

        /** ColorTransfer = SMPTE ST 2084（PQ，HDR10 / Dolby Vision）。 */
        const val COLOR_TRANSFER_SMPTE_ST_2084 = 7

        /** ColorTransfer = ARIB STD-B67（HLG）。 */
        const val COLOR_TRANSFER_HLG = 18

        /** 条带锁池大小。 */
        private const val LOCK_STRIPES = 64
    }
}

/**
 * 根据 [ThumbnailSettings] 的配置计算取帧位置（毫秒），始终落在 `[0, durationMs]`。
 */
fun calculateFramePositionMs(durationMs: Long, positionKey: String): Long {
    val frameMs = when (positionKey) {
        "5s" -> 5000L
        "10pct" -> (durationMs * 0.1).toLong()
        "50pct" -> (durationMs * 0.5).toLong()
        else -> 5000L
    }
    val upper = durationMs.coerceAtLeast(0L)
    return frameMs.coerceIn(0L, upper)
}

/** 默认取帧位置（第 5 秒）。 */
const val DEFAULT_FRAME_MS = 5000L

/** 默认取帧位置策略 key。 */
const val DEFAULT_POSITION_KEY = "5s"
