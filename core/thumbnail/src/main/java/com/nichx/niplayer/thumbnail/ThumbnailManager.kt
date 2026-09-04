package com.nichx.niplayer.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.LrcApiSettings
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.player.kernel.MediaFileTypes
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.WebDavMediaDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/** 缩略图生成结果。 */
sealed class ThumbnailResult {
    /** 生成成功，path 为本地 JPEG 文件绝对路径。 */
    data class Success(val path: String) : ThumbnailResult()
    /** 历史遗留结果类型，当前业务不再生产：短视频（< [ThumbnailManager.MIN_DURATION_MS]）
     *  改为取第一个关键帧生成缩略图，仅保留以兼容旧调用方对 when 的穷尽性检查。 */
    data object TooShort : ThumbnailResult()
    /** 生成失败（IO 错误、解码失败等）。 */
    data object Failed : ThumbnailResult()
    /**
     * 永久失败（W-M9 修复）：401/403 凭证错误等不可重试场景。
     *
     * 调用方应将其加入"不重试"集合（如 [_tooShortPaths]），避免每次刷新都无谓重试。
     */
    data object PermanentFailure : ThumbnailResult()
}

/**
 * 远程缩略图生成请求。
 *
 * 抽象输入，使 [ThumbnailManager.generateRemoteThumbnails] 不依赖 PlayHistoryEntity，
 * HomeTabViewModel / PlayHistoryViewModel 各自负责实体转换。
 *
 * @param storageId 媒体库 id
 * @param filePath 文件在存储中的路径
 * @param fileName 文件名（含扩展名）
 * @param url 作为回调 key 返回给调用方，通常为 PlayHistoryEntity.url
 * @param isAudio 是否为音频文件（true 走音频封面流程，false 走视频取帧流程）
 */
data class RemoteThumbnailRequest(
    val storageId: Int,
    val filePath: String,
    val fileName: String,
    val url: String,
    val isAudio: Boolean,
)

/**
 * 视频缩略图生成器。
 *
 * 设计要点：
 * - **双层缓存**：
 *   1. 服务端缓存：缩略图上传到 `{视频目录}/.thumb/{视频名去扩展名}-thumb.jpg`，
 *      下次浏览同一目录时 [preloadThumbnails] 并发下载到本地，避免重复生成
 *   2. 本地缓存：`cacheDir/video_cover/`，文件名 = MD5("$storageId-$filePath").jpg
 * - **协议适配**：
 *   - Local / WebDAV / DocumentFile：[Storage.createPlayUrl] 返回 http/file/content URL →
 *     [MediaMetadataRetriever.setDataSource] 通过 URL 取帧
 *   - SMB：[Storage.createPlayUrl] 返回 null → [Storage.openMediaDataSource] 返回
 *     [MediaDataSource] → [MediaMetadataRetriever.setDataSource] 通过随机读取取帧
 * - **帧位置策略**：优先取第 5 秒（避开片头 logo，短视频友好），
 *   fallback 到 duration*0.1 / duration*0.5（ms → us，OPTION_CLOSEST_SYNC），
 *   短视频（durationMs < 15s）改为取第一个关键帧。本地视频跳过时长检查，始终取帧。
 * - **缩放**：按 maxWidth=480px 等比缩放，JPEG quality=90 落盘。
 * - **HDR 色调映射**：Dolby Vision / HDR10 / HLG 等 HDR 视频在 API < 34 上
 *   [MediaMetadataRetriever.getFrameAtTime] 不做 tone mapping，返回的 Bitmap 像素值
 *   按 SDR 解读会严重偏暗、色彩失真。通过 [METADATA_KEY_COLOR_TRANSFER][MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER]
 *   检测 HDR，对 HDR 帧用 [ColorMatrix] 做线性增益补偿；API 34+ 由系统自动处理。
 * - **防重**：per-key [Mutex] 避免同一文件并发重复生成。
 */
@Singleton
class ThumbnailManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val cacheDir = File(context.cacheDir, "video_cover")
    private val audioCacheDir = File(context.cacheDir, "audio_cover")
    private val imageCacheDir = File(context.cacheDir, "image_thumb")
    private val seekCacheDir = File(context.cacheDir, "seek_preview")

    /** lrcapi 音乐元数据 HTTP 客户端。 */
    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 缩略图更新事件：保存缩略图后发出对应缓存文件路径。
     *
     * 首页 ViewModel 订阅此流，收到更新后给路径追加 `?t=timestamp` 以触发
     * Compose 重组并绕过 Coil 内存缓存（[NiVideoThumbnail] 用完整字符串作 memoryCacheKey）。
     */
    private val _thumbnailUpdated = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val thumbnailUpdated: SharedFlow<String> = _thumbnailUpdated.asSharedFlow()

    // BUG-5 修复：改用 ConcurrentHashMap.computeIfAbsent，移除手动 synchronized 锁
    // W-N5 修复：生成完成后用 computeIfPresent 移除已无持有者的 Mutex，
    // 避免 mutexMap 长期累积（每视频一个 Mutex，1 万文件约 480KB）。
    // computeIfPresent 在 value==mutex 且当前无锁（tryLock 成功）时移除；
    // 若仍有其他协程在等待锁则保留（tryLock 返回 false 不移除）。
    private val mutexMap = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private fun getMutex(key: String): Mutex = mutexMap.computeIfAbsent(key) { Mutex() }

    /**
     * 释放 [mutexMap] 中已无持有者的 [Mutex]。
     *
     * 在 [generateThumbnail] 的 [Mutex.withLock] finally 后调用：
     * - 若当前 Mutex 无其他协程等待（tryLock 成功）→ 移除并 unlock
     * - 若仍有协程等待 → 保留 Mutex，待最后一个持有者退出时清理
     *
     * 注意：tryLock + unlock 必须成对，否则会破坏 Mutex 状态。
     * computeIfPresent 保证原子性：在 lambda 内 tryLock 成功才移除。
     */
    private fun releaseMutexIfIdle(key: String, mutex: Mutex) {
        mutexMap.computeIfPresent(key) { _, m ->
            if (m === mutex && m.tryLock()) {
                m.unlock()
                null  // 移除
            } else {
                m  // 保留
            }
        }
    }

    /**
     * 生成视频缩略图。
     *
     * 纯本地生成 + 缓存，不包含上传。上传由 [uploadThumbnail] 单独异步调用，
     * 避免上传（比生成慢）阻塞并发生成槽位。
     *
     * @param storage 存储协议实现，用于获取可播放 URL 或 MediaDataSource
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param file 目标视频文件
     * @param positionKey 取帧位置策略 key，对应 [com.nichx.niplayer.datastore.ThumbnailSettings.framePositionKey]。
     *   取值 "5s"（默认）、"10pct"、"50pct"。
     * @return [ThumbnailResult]，UI 层据此区分"成功显示图"/"太短显示 <15s"/"失败显示占位图标"
     */
    suspend fun generateThumbnail(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
        positionKey: String = DEFAULT_POSITION_KEY,
    ): ThumbnailResult = withContext(Dispatchers.IO) {
            // ARCH-3 修复：fail-fast 防止误传音频/图片文件（getFrameAtTime 对音频静默返回 null）
            require(MediaFileTypes.isVideoFile(file.name)) {
                "generateThumbnail 要求视频文件，收到 ${file.name}"
            }
            val cacheFile = File(cacheDir, "${md5("$storageId-${file.path}")}.jpg")

            // 本地缓存命中直接返回
            if (cacheFile.exists()) return@withContext ThumbnailResult.Success(cacheFile.absolutePath)

            // per-key Mutex 防重
            // BUG-08 修复：用 withLock 替代 lock + finally { unlock }，避免协程在
            // 等待锁时被取消导致 finally 调用 unlock() 抛 IllegalStateException（替换
            // 原始 CancellationException，破坏结构化并发）
            // BUG-08 补充：withLock 内部用 return@withLock 替代 return@withContext，
            // 使 trimCacheIfNeeded 在所有路径（含缓存命中）下都执行，保持原 finally 语义。
            val mutex = getMutex(cacheFile.name)
            val result = mutex.withLock {
                // double-check：持锁后可能已被其他协程生成
                if (cacheFile.exists()) return@withLock ThumbnailResult.Success(cacheFile.absolutePath)

                // BUG-T-m9 修复：本地视频跳过 <15s 时长检查，始终生成缩略图
                val skipDurationCheck = storage.library.mediaType == MediaType.LOCAL_STORAGE

                val url = storage.createPlayUrl(file)
                if (url != null && (url.startsWith("file") || url.startsWith("content"))
                ) {
                    // Local / DocumentFile：通过 URL 取帧
                    generateFromUrl(url, cacheFile, positionKey, skipDurationCheck)
                } else if (url != null && url.startsWith("http", ignoreCase = true)) {
                    // WebDAV / HTTP URL：优先使用 URL + Headers 取帧（Android 内建 HTTP 栈
                    // 比自定义 MediaDataSource 更稳定），回退到 MediaDataSource
                    //
                    // W-M6 修复：自签 HTTPS 证书场景（storage.trustAllCertificates=true）下，
                    // MediaMetadataRetriever.setDataSource(url, headers) 走系统 HTTP 栈，
                    // 不使用 WebDavStorage 内部的 trust-all SSL 配置，URL+Headers 路径必失败。
                    // 此时跳过 URL+Headers，直接走 MediaDataSource（用 WebDavStorage 的 trust-all
                    // client 发 Range 请求），避免每个视频都先发一次必失败的请求。
                    if (!storage.trustAllCertificates) {
                        val headers = storage.getPlayHeaders()
                        if (headers.isNotEmpty()) {
                            val r = generateFromUrl(url, headers, cacheFile, positionKey, skipDurationCheck)
                            if (r is ThumbnailResult.Success) return@withLock r
                        }
                    }
                    val dataSource = storage.openMediaDataSource(file)
                    if (dataSource != null) {
                        generateFromDataSource(dataSource, cacheFile, positionKey, skipDurationCheck)
                    } else {
                        generateFromUrl(url, cacheFile, positionKey, skipDurationCheck)
                    }
                } else {
                    // SMB：通过 MediaDataSource 随机读取取帧
                    Log.d(TAG, "Generating thumbnail via MediaDataSource for ${file.path} (size=${file.length})")
                    val dataSource = storage.openMediaDataSource(file)
                    if (dataSource == null) {
                        Log.w(TAG, "openMediaDataSource returned null for ${file.path}")
                        return@withLock ThumbnailResult.Failed
                    }
                    generateFromDataSource(dataSource, cacheFile, positionKey, skipDurationCheck)
                }
            }
            // BUG-T7 修复：生成后检查缓存目录大小，超出阈值时淘汰最旧文件
            trimCacheIfNeeded(cacheDir)
            // W-N5 修复：生成完成后尝试清理 mutexMap 中的空闲 Mutex，避免长期累积
            releaseMutexIfIdle(cacheFile.name, mutex)
            result
        }

    /**
     * 上传已生成的缩略图到服务端 `{视频目录}/.thumb/{视频去扩展名}-thumb.jpg`。
     *
     * 此方法由调用方在生成完成后异步触发（fire-and-forget），不阻塞并发生成。
     *
     * - 仅对非本地存储执行（[Storage.saveFile] 返回 false 表示不支持，自动跳过）
     * - 上传失败不影响本地缩略图显示，仅记录日志
     *
     * BUG-T6 修复（撤销）：原 BUG-T6 改为 `{完整文件名}-thumb.jpg` 以避免同名异扩展名
     * 互相覆盖，但这破坏了与刮削工具的命名约定兼容（tinyMediaManager / Kodi / Emby
     * 等都用 `{name去扩展名}-thumb.jpg`），导致用户已刮削的缩略图无法被 preloadThumbnails
     * 复用，且会在 .thumb/ 目录下生成第二份 `{name}.mp4-thumb.jpg` 缩略图。
     *
     * 现改回 `{name去扩展名}-thumb.jpg`，与刮削工具约定一致。同名异扩展名视频共享同一
     * 缩略图是可接受的（刮削工具本身也这样工作），且符合 NIplayer 选用 -thumb.jpg
     * 后缀的初衷（复用刮削工具已生成的高质量缩略图）。
     *
     * @param storage 存储协议实现
     * @param file 目标视频文件（用于计算 .thumb/ 路径和文件名）
     */
    suspend fun uploadThumbnail(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        // BUG-7 修复：方法自身检查 saveInSameDir，避免调用方遗漏导致绕过用户设置
        // （每存储源按 effectiveWriteBack 生效，存储源覆盖优先于全局开关）
        if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return@withContext
        val storageId = storage.library.id
        val cacheFile = File(cacheDir, "${md5("$storageId-${file.path}")}.jpg")
        if (!cacheFile.exists()) return@withContext
        try {
            val thumbDirPath = buildThumbDirPath(file.path)
            storage.createDirectory(thumbDirPath)
            val bytes = cacheFile.readBytes()
            // 用视频去扩展名命名，与刮削工具约定一致（如 movie.mp4 → movie-thumb.jpg）
            val videoBaseName = file.name.substringBeforeLast('.')
            val thumbPath = "$thumbDirPath/$videoBaseName-thumb.jpg"
            // BUG-T-C1 修复：上传前检查服务端是否已存在同名缩略图，
            // 已存在则跳过上传，避免覆盖刮削工具生成的高质量缩略图或别设备已上传的缓存
            if (storage.fileExists(thumbPath)) {
                Log.d(TAG, "uploadThumbnail skip: 服务端已存在 $thumbPath")
                return@withContext
            }
            val success = storage.saveFile(thumbPath, bytes)
            if (!success) {
                Log.d(TAG, "saveFile returned false for $thumbPath（本地存储跳过上传）")
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadThumbnail failed: ${e.message}")
        }
    }

    /**
     * 预加载服务端已生成的缩略图到本地缓存。
     *
     * 浏览目录时调用：按优先级检查两处服务端缩略图，命中则下载到本地 `video_cover/`
     * 缓存目录，后续 [generateThumbnail] 直接命中本地缓存：
     *
     * 1. `.thumb/` 子目录下的 `{完整文件名}-thumb.jpg`（NIplayer 自管理缓存）
     * 2. 视频同目录下的 `{完整文件名}-thumb.jpg`（刮削工具复用，命名约定一致）
     *
     * BUG-T-M6 修复：原实现仅扫描 `.thumb/` 子目录，未扫描视频同目录的
     * `{name}-thumb.jpg`。NIplayer 选用 `-thumb.jpg` 后缀的初衷即为复用刮削工具
     * 已生成的高质量缩略图，但扫描路径遗漏导致此设计意图完全未生效。
     *
     * BUG-T-M3 修复：原实现仅取 `pending.first()` 所在目录的 `.thumb/`，假设所有
     * pending 文件在同一目录。`StorageFileViewModel` 调用方满足此假设，但
     * `generateRemoteThumbnails` 调用时 pending 来自播放历史（按 storageId 分组），
     * 同一 storage 的播放历史可能跨多个目录，导致除第一个目录外的视频即使服务端
     * 已有缓存也不会被预加载，被迫走实时取帧。现按目录分组，每组独立扫描
     * `.thumb/` 和同目录刮削图。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param files 目录下的视频文件列表
     * @param onLoaded 每下载完成一个缩略图时回调（path = 视频文件路径，thumbPath = 本地缓存路径）
     * @param sameDirFiles 调用方已有的视频所在目录文件列表（含 `{name}-thumb.jpg`）。
     *        仅适用于单目录调用方 [StorageFileViewModel]，多目录场景（generateRemoteThumbnails）
     *        不传，方法内部按目录分组并主动 listFiles 各目录。
     */
    suspend fun preloadThumbnails(
        storage: Storage,
        storageId: Int,
        files: List<StorageFile>,
        onLoaded: (String, String) -> Unit,
        sameDirFiles: List<StorageFile>? = null,
    ) = withContext(Dispatchers.IO) {
        // BUG-2 修复：信任调用方已按视频类型过滤，移除内部 isVideoFile 重复过滤
        // 原实现用本地 VIDEO_EXTENSIONS 过滤，与 MediaFileTypes 不一致
        // （.vob/.f4v/.m2ts 在 MediaFileTypes 中是视频，在 ThumbnailManager 中不是），
        // 导致这些文件即使服务端 .thumb/ 已有缓存也不会被预加载
        val videoFiles = files.filter { !it.isDirectory }
        if (videoFiles.isEmpty()) return@withContext

        // 检查本地缓存，已命中的直接回调
        val pending = mutableListOf<StorageFile>()
        for (file in videoFiles) {
            val cacheFile = File(cacheDir, "${md5("$storageId-${file.path}")}.jpg")
            if (cacheFile.exists()) {
                onLoaded(file.path, cacheFile.absolutePath)
            } else {
                pending.add(file)
            }
        }
        if (pending.isEmpty()) return@withContext

        // BUG-T-M3 修复：按目录分组预加载，支持 pending 跨多个目录的场景
        // （generateRemoteThumbnails 调用时 pending 来自播放历史，可能跨多目录）
        // 同目录文件共享一次 listFiles 结果，避免重复网络请求
        val byDir: Map<String, List<StorageFile>> = pending.groupBy { it.path.substringBeforeLast('/', "") }

        // 单目录调用方传入的 sameDirFiles 仅对该目录生效
        val sameDirFilesForGroup: (String) -> List<StorageFile>? = { dirPath ->
            if (sameDirFiles != null && byDir.size == 1) sameDirFiles else null
        }

        val concurrency = minOf(storage.thumbnailConcurrency, pending.size)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            for ((dirPath, filesInDir) in byDir) {
                // 第 1 优先级：检查服务端 .thumb/ 子目录
                // BUG-T-M6 修复：listFiles 失败改为 emptyList（而非 return@withContext），
                // 让第 2 优先级同目录扫描继续尝试，同时缓解 BUG-T-C1 路径 A
                val thumbDirPath = if (dirPath.isEmpty()) ".thumb" else "$dirPath/.thumb"
                val thumbFiles = try {
                    storage.listFiles(ThumbDirFile(thumbDirPath))
                } catch (e: Exception) {
                    emptyList()
                }

                // 建立 {视频去扩展名} → 缩略图StorageFile 映射
                // 命名格式：{视频去扩展名}-thumb.jpg，提取 {视频去扩展名}
                // 与 uploadThumbnail / 刮削工具命名约定一致
                val thumbMap = mutableMapOf<String, StorageFile>()
                for (tf in thumbFiles) {
                    val name = tf.name.removeSuffix("-thumb.jpg").removeSuffix("-thumb.jpeg")
                    if (name.isNotEmpty()) {
                        thumbMap[name] = tf
                    }
                }

                // 第 2 优先级：视频同目录的 {name}-thumb.jpg（复用刮削工具缩略图）
                // BUG-T-M6 修复：优先使用调用方传入的 sameDirFiles（零额外网络请求）；
                // 未传入时主动 listFiles 视频所在目录
                val dirFiles = sameDirFilesForGroup(dirPath) ?: try {
                    storage.listFiles(
                        if (dirPath.isEmpty()) StorageFactory.ROOT
                        else object : AbstractStorageFile(
                            path = dirPath,
                            name = "",
                            isDirectory = true,
                        ) {}
                    )
                } catch (e: Exception) {
                    emptyList()
                }

                // 建立同目录刮削图映射，匹配 key 与 .thumb/ 子目录一致（视频去扩展名）
                val sameDirThumbMap = mutableMapOf<String, StorageFile>()
                for (f in dirFiles) {
                    if (f.isDirectory || f.length == 0L) continue
                    val name = f.name.removeSuffix("-thumb.jpg").removeSuffix("-thumb.jpeg")
                    // 确保确实有 -thumb 后缀（removeSuffix 后字符串变化才算命中）
                    if (name != f.name && name.isNotEmpty()) {
                        sameDirThumbMap[name] = f
                    }
                }

                // 顺带惰性清理服务端孤立缩略图：复用本次已列出（零额外网络）的 .thumb/ 与主目录
                // 清单，删除 basename 已无对应视频的孤儿（如被移动/重命名/删除后残留）
                cleanUpOrphanThumbs(storage, dirFiles, thumbFiles)

                // 并发下载：优先用 .thumb/，未命中用同目录 {name}-thumb.jpg
                for (file in filesInDir) {
                    // 用视频去扩展名匹配（与 uploadThumbnail / 刮削工具命名约定一致）
                    val videoBaseName = file.name.substringBeforeLast('.')
                    val source = thumbMap[videoBaseName]        // .thumb/{name去扩展名}-thumb.jpg
                        ?: sameDirThumbMap[videoBaseName]       // 同目录 {name去扩展名}-thumb.jpg
                        ?: continue
                    launch {
                        semaphore.withPermit {
                            try {
                                val path = downloadThumbnail(storage, storageId, file, source)
                                if (path != null) onLoaded(file.path, path)
                            } catch (e: Exception) {
                                // 单个下载失败不影响其他
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 惰性清理服务端孤立缩略图（[preloadThumbnails] 浏览目录时调用）。
     *
     * 对比 `.thumb/` 内容与该目录主文件清单，删除 basename 已无对应视频文件的缩略图。
     * 复用已有列表，**零额外网络往返**；在非写回（[ThumbnailSettings.effectiveWriteBack]）
     * 模式下不清理（避免替用户管理未授权的服务端缩略图）。单个删除失败不影响其余。
     *
     * @param storage 存储协议实现
     * @param dirFiles 目录主文件清单（含视频/图片/其他）
     * @param thumbFiles `.thumb/` 目录内容
     */
    private suspend fun cleanUpOrphanThumbs(
        storage: Storage,
        dirFiles: List<StorageFile>,
        thumbFiles: List<StorageFile>,
    ) {
        try {
            if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return
            // 主目录内有效"源文件"的 basename 集合：排除目录与缩略图/封面自身（避免把
            // {name}-thumb.jpg 当成 {name-thumb}-thumb.jpg 的源）
            val validBaseNames = dirFiles.mapNotNull { f ->
                if (f.isDirectory) return@mapNotNull null
                val n = f.name
                if (n.endsWith("-thumb.jpg") || n.endsWith("-thumb.jpeg") ||
                    n.endsWith("-cover.jpg") || n.endsWith("-cover.jpeg")
                ) null else n.substringBeforeLast('.')
            }.toHashSet()
            if (validBaseNames.isEmpty()) return
            for (tf in thumbFiles) {
                val matched = tf.name.removeSuffix("-thumb.jpg").removeSuffix("-thumb.jpeg")
                // 非缩略图命名或 removeSuffix 无变化（无 -thumb 后缀）的项不处理
                if (matched.isEmpty() || matched == tf.name) continue
                if (matched !in validBaseNames) {
                    runCatching { storage.deleteFile(tf) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanUpOrphanThumbs failed: ${e.message}")
        }
    }

    /**
     * 返回缓存文件路径（不检查是否存在），供 UI 层判断是否需要异步生成。
     */
    fun getThumbnailPath(storageId: Int, filePath: String): String {
        return File(cacheDir, "${md5("$storageId-$filePath")}.jpg").absolutePath
    }

    /**
     * 检查本地缓存中是否存在指定文件的缩略图。
     *
     * 纯本地文件系统检查（不涉及网络 IO），可在主线程安全调用。
     * 用于 ViewModel 首次订阅时快速填充已有缩略图，避免走完整生成流程。
     *
     * @return 存在时返回本地缓存路径，不存在返回 null
     */
    fun getCachedThumbnailPath(storageId: Int, filePath: String): String? {
        val cacheFile = File(cacheDir, "${md5("$storageId-$filePath")}.jpg")
        return if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
    }

    /**
     * 检查本地缓存中是否存在指定音频文件的封面图。
     *
     * 纯本地文件系统检查（不涉及网络 IO），可在主线程安全调用。
     *
     * @return 存在时返回本地缓存路径，不存在返回 null
     */
    fun getCachedAudioCoverPath(storageId: Int, filePath: String): String? {
        val cacheFile = File(audioCacheDir, "${md5("$storageId-$filePath")}.jpg")
        return if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
    }

    /**
     * 检查指定音频文件是否已确认无内嵌封面。
     *
     * 纯本地文件系统检查（不涉及网络 IO），可在主线程安全调用。
     * 用于避免反复扫描无封面文件。
     */
    fun hasNoCover(storageId: Int, filePath: String): Boolean {
        return File(audioCacheDir, "${md5("$storageId-$filePath")}.no_cover").exists()
    }

    /**
     * 标记指定音频文件无内嵌封面，后续扫描跳过此文件。
     */
    fun markNoCover(storageId: Int, filePath: String) {
        try {
            File(audioCacheDir, "${md5("$storageId-$filePath")}.no_cover").createNewFile()
        } catch (_: Exception) {}
    }

    /**
     * 检查指定音频文件是否已通过 API 尝试过但仍无封面。
     *
     * 与 [hasNoCover] 配合使用：hasNoCover 为 true 但 hasApiNoCover 为 false 时，
     * 说明该文件在 API 配置前已被标记，应放行让 API 再试一次。
     */
    fun hasApiNoCover(storageId: Int, filePath: String): Boolean {
        return File(audioCacheDir, "${md5("$storageId-$filePath")}.no_cover_api").exists()
    }

    /**
     * 标记指定音频文件通过 API 尝试后仍无封面。后续扫描同时跳过此文件。
     */
    fun markApiNoCover(storageId: Int, filePath: String) {
        try {
            File(audioCacheDir, "${md5("$storageId-$filePath")}.no_cover_api").createNewFile()
        } catch (_: Exception) {}
    }

    /**
     * 通过 lrcapi 远程获取音频封面。
     *
     * 在本地提取（内嵌封面、目录封面、头部读取）均失败时调用。
     * 成功时保存到本地缓存，后续走缓存路径。
     *
     * @param storageId 媒体库 id，用于清除 no_cover 标记
     * @param file 目标音频文件
     * @param cacheFile 本地缓存文件（已按 MD5 命名）
     * @return 本地缓存路径，或 null 表示 API 未配置或获取失败
     */
    private suspend fun fetchAudioCoverFromApi(
        storageId: Int,
        file: StorageFile,
        cacheFile: File,
    ): String? = withContext(Dispatchers.IO) {
        if (!LrcApiSettings.isConfigured) return@withContext null
        val apiUrl = LrcApiSettings.apiUrl
        if (apiUrl.isEmpty()) return@withContext null

        val nameWithoutExt = file.name.substringBeforeLast('.')
        if (nameWithoutExt.isEmpty()) return@withContext null

        var apiAttempted = false
        try {
            val params = "title=${java.net.URLEncoder.encode(nameWithoutExt, "UTF-8")}"
            val url = "$apiUrl/cover?$params"

            Log.i(TAG, "fetchAudioCoverFromApi: $url")

            val requestBuilder = Request.Builder().url(url).get()
                .header("Accept", "image/*")

            val apiAuth = LrcApiSettings.apiAuth
            if (apiAuth.isNotEmpty()) {
                requestBuilder.header("Authorization", apiAuth)
                requestBuilder.header("Authentication", apiAuth)
            }

            val response = apiClient.newCall(requestBuilder.build()).execute()
            apiAttempted = true
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: return@withContext null
                if (bytes.isNotEmpty()) {
                    cacheFile.parentFile?.mkdirs()
                    FileOutputStream(cacheFile).use { out ->
                        out.write(bytes)
                    }
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        Log.i(TAG, "API封面获取成功: $nameWithoutExt, 大小: ${bytes.size} bytes")
                        // 清除可能存在的 no_cover / no_cover_api 标记，刷新后可见新封面
                        val baseKey = md5("$storageId-${file.path}")
                        File(audioCacheDir, "${baseKey}.no_cover").delete()
                        File(audioCacheDir, "${baseKey}.no_cover_api").delete()
                        return@withContext cacheFile.absolutePath
                    }
                }
            } else {
                Log.w(TAG, "API封面请求失败: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "API封面请求异常: ${e.message}")
            apiAttempted = true
        }
        // 实际尝试了 API 但失败 → 标记 no_cover_api，避免下次重复请求
        if (apiAttempted) {
            markApiNoCover(storageId, file.path)
        }
        return@withContext null
    }

    /**
     * 检查本地缓存中是否存在指定图片文件的缩略图。
     *
     * BUG-T-m4 修复：新增此方法，与 [getCachedThumbnailPath] / [getCachedAudioCoverPath] 对称，
     * 供 [StorageFileViewModel.generateThumbnailUrls] 图片组先扫描本地缓存再决定是否生成。
     */
    fun getCachedImageThumbnailPath(storageId: Int, filePath: String): String? {
        val cacheFile = File(imageCacheDir, "${md5("$storageId-$filePath")}.jpg")
        return if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
    }

    /**
     * 批量检查本地缓存，返回已缓存项的映射。
     *
     * @param items (storageId, filePath) 列表
     * @return (filePath → 本地缓存路径) 的映射
     */
    fun getCachedThumbnailPaths(items: List<Pair<Int, String>>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((storageId, filePath) in items) {
            val path = getCachedThumbnailPath(storageId, filePath)
            if (path != null) {
                result[filePath] = path
            }
        }
        return result
    }

    /**
     * 批量检查音频封面本地缓存，返回已缓存项的映射。
     *
     * 与 [getCachedThumbnailPaths] 对称，用于 [com.nichx.niplayer.feature.home.history
     * .PlayHistoryViewModel] / [com.nichx.niplayer.feature.home.home.HomeTabViewModel]
     * 批量加载历史/最近播放列表的音频封面。
     *
     * @param items (storageId, filePath) 列表
     * @return (filePath → 本地缓存路径) 的映射
     */
    fun getCachedAudioCoverPaths(items: List<Pair<Int, String>>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((storageId, filePath) in items) {
            val path = getCachedAudioCoverPath(storageId, filePath)
            if (path != null) {
                result[filePath] = path
            }
        }
        return result
    }

    /**
     * 在精确位置 [positionMs] 生成缩略图并覆盖本地缓存。
     *
     * 与 [generateThumbnail] 的区别：
     * - 删除已存在的缓存文件，强制重新生成
     * - 直接使用 [positionMs] 作为取帧位置，不经过计算
     * - 取帧失败时 fallback 到 10%/50% 位置
     *
     * 主要用于退出播放时以最后播放帧更新缩略图。
     */
    suspend fun generateThumbnailAtMs(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
        positionMs: Long,
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        // ARCH-3 修复：fail-fast，防止误传音频文件（FIX-1 的根因之一）
        require(MediaFileTypes.isVideoFile(file.name)) {
            "generateThumbnailAtMs 要求视频文件，收到 ${file.name}"
        }
        val cacheFile = File(cacheDir, "${md5("$storageId-${file.path}")}.jpg")
        // R2 修复：不再先删除旧缓存。改为先写临时文件，取帧成功后才原子覆盖正式缓存；
        // 失败/超时时清理临时文件并保留旧图，避免远程取帧失败导致浏览时已生成的
        // 缩略图被删除（UI 退回占位符）。tmp 文件残留由 trimCacheIfNeeded 兜底清理。
        val tmpFile = File(cacheDir, "${cacheFile.nameWithoutExtension}.tmp.jpg")

        // BUG-08 修复：用 withLock 替代 lock + finally { unlock }
        // BUG-08 补充：withLock 内用 return@withLock，trimCacheIfNeeded 在所有路径执行。
        val mutex = getMutex(cacheFile.name)
        val rawResult = mutex.withLock {
            // BUG-T-m9 修复：本地视频跳过 <15s 时长检查，始终生成缩略图
            val skipDurationCheck = storage.library.mediaType == MediaType.LOCAL_STORAGE

            val url = storage.createPlayUrl(file)
            if (url != null && (url.startsWith("file") || url.startsWith("content"))) {
                generateFromUrlAt(url, tmpFile, positionMs, skipDurationCheck)
            } else if (url != null && url.startsWith("http", ignoreCase = true)) {
                // R3 修复：与 generateThumbnail 的 W-M6 门控对称——自签 HTTPS 证书场景
                // （storage.trustAllCertificates=true）下 URL+Headers 走系统 HTTP 栈必失败，
                // 直接跳过该路径走 MediaDataSource，避免退出播放时每次先发一次必失败请求
                if (!storage.trustAllCertificates) {
                    val headers = storage.getPlayHeaders()
                    if (headers.isNotEmpty()) {
                        val r = generateFromUrlAt(url, headers, tmpFile, positionMs, skipDurationCheck)
                        if (r is ThumbnailResult.Success) return@withLock r
                    }
                }
                val dataSource = storage.openMediaDataSource(file)
                if (dataSource != null) {
                    generateFromDataSourceAt(dataSource, tmpFile, positionMs, skipDurationCheck)
                } else {
                    generateFromUrlAt(url, tmpFile, positionMs, skipDurationCheck)
                }
            } else {
                val dataSource = storage.openMediaDataSource(file)
                if (dataSource == null) return@withLock ThumbnailResult.Failed
                generateFromDataSourceAt(dataSource, tmpFile, positionMs, skipDurationCheck)
            }
        }
        // R2 修复：成功 → 临时文件原子覆盖正式缓存（路径更新为正式缓存路径）；
        // 失败 → 清理临时文件，保留旧图
        val result = when (rawResult) {
            is ThumbnailResult.Success -> {
                if (tmpFile.exists()) {
                    if (tmpFile.renameTo(cacheFile)) {
                        ThumbnailResult.Success(cacheFile.absolutePath)
                    } else {
                        // rename 失败（同目录极少发生）：清理临时文件，返回失败保留旧图
                        tmpFile.delete()
                        ThumbnailResult.Failed
                    }
                } else {
                    // 未写临时文件（取帧成功但文件已存在等边界），直接使用返回结果
                    rawResult
                }
            }
            else -> {
                tmpFile.delete()
                rawResult
            }
        }
        // BUG-T7 修复：生成后检查缓存目录大小，超出阈值时淘汰最旧文件
        trimCacheIfNeeded(cacheDir)
        // W-N5 修复：清理空闲 Mutex
        releaseMutexIfIdle(cacheFile.name, mutex)
        if (result is ThumbnailResult.Success) {
            _thumbnailUpdated.tryEmit(cacheFile.absolutePath)
        }
        result
    }

    /**
     * 将已生成的 Bitmap 保存为缩略图（覆盖本地缓存）。
     *
     * 主要用于退出播放时通过 PixelCopy 截取 SurfaceView 当前帧作为缩略图，
     * 比 [generateThumbnailAtMs] 更可靠（直接取渲染输出，不依赖 MediaMetadataRetriever 网络读取）。
     *
     * @param isHdr 当前播放媒体是否为 HDR（Dolby Vision / HDR10 / HLG）。
     *   实测 PixelCopy 从 10-bit HDR SurfaceView 抓帧在部分设备上返回损坏数据
     *   （白屏 + 品红块），调用方 [PlayerViewModel.shouldCaptureThumbnailOnExit] 已
     *   对 HDR 播放拦截（跳过抓帧，改走 [generateThumbnailAtMs]），因此正常流程中
     *   HDR 不会走到本方法；保留此参数仅为防御性（若未来有非 PixelCopy 的 HDR
     *   bitmap 来源，按 [applyHdrToneMapCompensation] 补偿）。
     * @return 本地缓存路径，或 null 表示保存失败
     */
    suspend fun saveThumbnailFromBitmap(
        storageId: Int,
        file: StorageFile,
        bitmap: Bitmap,
        isHdr: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        // ARCH-3 修复：fail-fast，防止误传音频文件（PixelCopy 截图只对视频 SurfaceView 有意义）
        require(MediaFileTypes.isVideoFile(file.name)) {
            "saveThumbnailFromBitmap 要求视频文件，收到 ${file.name}"
        }
        val cacheFile = File(cacheDir, "${md5("$storageId-${file.path}")}.jpg")
        cacheFile.parentFile?.mkdirs()
        try {
            // HDR 补偿（防御性，正常流程 HDR 已被拦截）：对原始 HDR 像素做线性增益，
            // 避免按 sRGB 保存偏暗。注意：不能回收原 bitmap —— 调用方 lastFrameBitmap
            // 由 GC 管理（BUG-P4 竞态保护）。
            val compensated = if (isHdr) applyHdrToneMapCompensation(bitmap) else bitmap
            val scaled = scaleToMaxWidth(compensated, MAX_WIDTH)
            FileOutputStream(cacheFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            // 回收语义（原 bitmap 由调用方 GC 管理，BUG-P4 竞态保护，不可回收）：
            // - 发生过缩放：scaleToMaxWidth 已回收 compensated，这里只需回收 scaled 副本
            // - 未缩放且做了 HDR 补偿：补偿副本即最终图，用完回收
            // - 未缩放未补偿：compensated == bitmap，不回收
            if (scaled !== compensated) {
                scaled.recycle()
            } else if (compensated !== bitmap) {
                compensated.recycle()
            }
            // BUG-T7 修复：保存后检查缓存目录大小，超出阈值时淘汰最旧文件
            trimCacheIfNeeded(cacheDir)
            _thumbnailUpdated.tryEmit(cacheFile.absolutePath)
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "saveThumbnailFromBitmap failed: ${e.message}")
            null
        }
    }

    /**
     * 清空所有缩略图缓存目录。
     *
     * BUG-6 修复：原实现用 `File.delete()` 对非空目录返回 false（不抛异常），
     * 改用 `deleteRecursively()` 递归删除后 `mkdirs()` 重建，保证目录可用。
     */
    fun clearCache() {
        listOf(cacheDir, audioCacheDir, imageCacheDir, seekCacheDir).forEach { dir ->
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }

    /**
     * BUG-T-M4 修复：按 storageId + 文件路径列表细粒度清理缩略图缓存。
     *
     * 仅删除指定文件对应的本地缓存，不影响其他存储源、其他目录、播放历史页等已缓存的缩略图。
     * 用于 [StorageFileViewModel.refreshThumbnails] 替代全量 [clearCache]，避免用户在某个
     * SMB 目录点"刷新缩略图"时清空全应用缩略图导致批量重新生成。
     *
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param files 待清理缓存的文件列表（视频/音频/图片混合，方法内部按缓存命名规则删除）
     */
    fun clearCache(storageId: Int, files: List<StorageFile>) {
        for (file in files) {
            // video_cover / audio_cover / image_thumb 三个缓存目录均按 MD5("$storageId-$filePath").jpg 命名
            val key = "${md5("$storageId-${file.path}")}.jpg"
            File(cacheDir, key).delete()
            File(audioCacheDir, key).delete()
            File(imageCacheDir, key).delete()
            // 同时清理 no_cover / no_cover_api 标记
            val baseKey = md5("$storageId-${file.path}")
            File(audioCacheDir, "${baseKey}.no_cover").delete()
            File(audioCacheDir, "${baseKey}.no_cover_api").delete()
        }
    }

    /**
     * 删除视频文件对应的"软件生成"缩略图残留。
     *
     * 用户删除视频文件后调用，清理两处由本应用生成的缩略图：
     * 1. 本地缓存（`video_cover/` 下的 MD5 缓存文件）
     * 2. 服务端 `.thumb/` 子目录下由 [uploadThumbnail] 上传的 `{视频去扩展名}-thumb.jpg`
     *
     * 注意：不会删除视频同目录下由刮削工具（Kodi/Emby/tinyMediaManager 等）生成的
     * `{name}-thumb.jpg` —— 那颗属于用户原有资源，删除视频不应连带删除。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param file 被删除的视频文件
     */
    suspend fun deleteThumbnailsForVideo(storage: Storage, storageId: Int, file: StorageFile) {
        withContext(Dispatchers.IO) {
            // 1. 清本地缓存（video_cover + no_cover 标记）
            clearCache(storageId, listOf(file))
            // 2. 删除服务端 .thumb/ 下由本应用上传的缩略图（仅限 .thumb/，不动同目录用户原图）
            try {
                if (!file.isDirectory && MediaFileTypes.isVideoFile(file.name)) {
                    val thumbPath = "${buildThumbDirPath(file.path)}/${file.name.substringBeforeLast('.')}-thumb.jpg"
                    if (storage.fileExists(thumbPath)) {
                        storage.deleteFile(
                            object : AbstractStorageFile(
                                path = thumbPath,
                                name = thumbPath.substringAfterLast('/'),
                                isDirectory = false,
                            ) {}
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "deleteThumbnailsForVideo failed: ${e.message}")
            }
        }
    }

    /**
     * 缓存目录大小限制（BUG-T7 修复）。
     *
     * 单目录上限 [MAX_CACHE_BYTES]（200MB），超出时按 lastModified 淘汰最旧文件。
     * 仅覆盖 `video_cover` / `audio_cover` / `image_thumb` 三个无限制目录；
     * `seek_preview` 已有 `SEEK_CACHE_MAX_FILES = 20` 限制不在此处理。
     *
     * 触发时机：每次生成新缩略图后调用 [trimCacheIfNeeded]，
     * 避免长期使用后缓存膨胀到数百 MB 需用户手动清理。
     */
    private fun trimCacheIfNeeded(dir: File) {
        val files = dir.listFiles()?.toMutableList() ?: return
        if (files.isEmpty()) return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_BYTES) return
        // 按 lastModified 升序（最旧在前），依次删除直至总大小低于阈值
        files.sortBy { it.lastModified() }
        val iter = files.iterator()
        while (iter.hasNext() && totalSize > MAX_CACHE_BYTES) {
            val f = iter.next()
            val size = f.length()
            if (f.delete()) {
                totalSize -= size
            }
        }
    }

    // ---------- 生成 ----------

    /**
     * 检测视频是否为 HDR 编码。
     *
     * 通过 [MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER] 判断：
     * - [COLOR_TRANSFER_SMPTE_ST_2084] (7) = SMPTE ST 2084 (PQ, HDR10 / Dolby Vision)
     * - [COLOR_TRANSFER_HLG] (18) = ARIB STD-B67 (HLG)
     *
     * 在 API < 34 上，[MediaMetadataRetriever.getFrameAtTime] 对 HDR 视频取帧不做
     * tone mapping，返回的 Bitmap 像素值按 SDR 解读会严重偏暗、色彩失真。
     *
     * @return true 表示 HDR 视频需做软件色调映射补偿
     */
    private fun isHdrVideo(retriever: MediaMetadataRetriever): Boolean {
        val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
            ?.toIntOrNull() ?: return false
        return transfer == COLOR_TRANSFER_SMPTE_ST_2084 || transfer == COLOR_TRANSFER_HLG
    }

    /**
     * HDR 软件色调映射补偿（仅 API < 34 使用）。
     *
     * 背景：API < 34 的 [MediaMetadataRetriever.getFrameAtTime] 不对 HDR 内容做 tone mapping，
     * 取出的 Bitmap 是 HDR 像素值但被按 SDR 显示，整体偏暗、色彩失真。
     *
     * 补偿策略：
     * - 线性增益（[HDR_TONE_MAP_GAIN]）：HLG/PQ 编码亮度空间映射到 SDR 显示空间的近似
     * - 用 [ColorMatrixColorFilter] 实现，避免 Kotlin 逐像素循环
     *
     * 注意：此为简化补偿，远不如 API 34+ 系统级 tone mapping 准确，但显著改善可用性。
     * 完整色调映射需基于 PQ/HLG OETF 反变换 + BT.1886 gamma，依赖硬件解码器输出
     * 10-bit 数据；在 MediaMetadataRetriever 已量化为 8-bit 后无法准确还原，只能做近似。
     *
     * @param src 原始 HDR 帧 Bitmap
     * @return 补偿后的 SDR Bitmap
     */
    private fun applyHdrToneMapCompensation(src: Bitmap): Bitmap {
        val gain = HDR_TONE_MAP_GAIN
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
    private fun applyHdrCompensationIfNeeded(src: Bitmap, isHdr: Boolean): Bitmap {
        if (!isHdr) return src
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return src
        val mapped = applyHdrToneMapCompensation(src)
        if (mapped !== src) src.recycle()
        return mapped
    }

    private fun generateFromUrl(url: String, cacheFile: File, positionKey: String, skipDurationCheck: Boolean = false): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(url))
            extractAndSaveFrame(retriever, cacheFile, positionKey, skipDurationCheck)
        } catch (e: Exception) {
            Log.w(TAG, "generateFromUrl failed: ${e.message}")
            ThumbnailResult.Failed
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun generateFromUrl(url: String, headers: Map<String, String>, cacheFile: File, positionKey: String, skipDurationCheck: Boolean = false): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers)
            extractAndSaveFrame(retriever, cacheFile, positionKey, skipDurationCheck)
        } catch (e: Exception) {
            Log.w(TAG, "generateFromUrl with headers failed: ${e.message}")
            ThumbnailResult.Failed
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun generateFromDataSource(dataSource: MediaDataSource, cacheFile: File, positionKey: String, skipDurationCheck: Boolean = false): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        var success = false
        var result: ThumbnailResult = ThumbnailResult.Failed
        try {
            retriever.setDataSource(dataSource)
            result = extractAndSaveFrame(retriever, cacheFile, positionKey, skipDurationCheck)
            success = result is ThumbnailResult.Success
            when (result) {
                is ThumbnailResult.Success -> Log.d(TAG, "Thumbnail generated: ${cacheFile.name}")
                is ThumbnailResult.TooShort -> Log.d(TAG, "Video too short, skipped: ${cacheFile.name}")
                is ThumbnailResult.Failed -> Log.w(TAG, "extractAndSaveFrame failed for ${cacheFile.name}")
                is ThumbnailResult.PermanentFailure -> {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "generateFromDataSource failed: ${e.message}")
            result = ThumbnailResult.Failed
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { dataSource.close() } catch (_: Exception) {}
        }
        // W-M9 修复：仅当取帧失败时，检查 WebDavMediaDataSource 的 HTTP 错误码。
        // 若为 401/403 凭证错误，升级为 PermanentFailure，让调用方加入"不重试"集合，
        // 避免每次刷新都无谓重试（凭据未变，必再失败）。
        if (!success && result is ThumbnailResult.Failed
            && dataSource is WebDavMediaDataSource
            && dataSource.lastHttpErrorCode in setOf(401, 403)
        ) {
            Log.w(TAG, "generateFromDataSource: permanent failure (HTTP ${dataSource.lastHttpErrorCode}) for ${cacheFile.name}")
            return ThumbnailResult.PermanentFailure
        }
        return result
    }

    // ---------- 精确位置取帧（退出播放时使用） ----------

    private fun generateFromUrlAt(url: String, cacheFile: File, positionMs: Long, skipDurationCheck: Boolean = false): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(url))
            extractAndSaveFrameAt(retriever, cacheFile, positionMs, skipDurationCheck)
        } catch (e: Exception) {
            Log.w(TAG, "generateFromUrlAt failed: ${e.message}")
            ThumbnailResult.Failed
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun generateFromUrlAt(url: String, headers: Map<String, String>, cacheFile: File, positionMs: Long, skipDurationCheck: Boolean = false): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers)
            extractAndSaveFrameAt(retriever, cacheFile, positionMs, skipDurationCheck)
        } catch (e: Exception) {
            Log.w(TAG, "generateFromUrlAt with headers failed: ${e.message}")
            ThumbnailResult.Failed
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun generateFromDataSourceAt(dataSource: MediaDataSource, cacheFile: File, positionMs: Long, skipDurationCheck: Boolean = false): ThumbnailResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            val result = extractAndSaveFrameAt(retriever, cacheFile, positionMs, skipDurationCheck)
            when (result) {
                is ThumbnailResult.Success -> Log.d(TAG, "Thumbnail overwritten at ${positionMs}ms: ${cacheFile.name}")
                is ThumbnailResult.TooShort -> Log.d(TAG, "Video too short, skipped: ${cacheFile.name}")
                is ThumbnailResult.Failed -> Log.w(TAG, "extractAndSaveFrameAt failed for ${cacheFile.name}")
                // PermanentFailure 由上层 generateFromDataSource 根据 HTTP 错误码判定，
                // extractAndSaveFrameAt 本身不会返回此值，此处仅满足 when 穷尽性。
                is ThumbnailResult.PermanentFailure -> Unit
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "generateFromDataSourceAt failed: ${e.message}")
            ThumbnailResult.Failed
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { dataSource.close() } catch (_: Exception) {}
        }
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
    private fun extractAndSaveFrameAt(
        retriever: MediaMetadataRetriever,
        cacheFile: File,
        positionMs: Long,
        skipDurationCheck: Boolean = false,
    ): ThumbnailResult {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
        // 短视频（<15s）不再返回 TooShort，改为取第一个关键帧（0ms）生成缩略图；
        // 本地视频（skipDurationCheck）不受此规则影响，始终按正常位置取帧
        val isShort = !skipDurationCheck && durationMs != null && durationMs < MIN_DURATION_MS
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

        val scaled = scaleToMaxWidth(frame, MAX_WIDTH)
        cacheFile.parentFile?.mkdirs()
        FileOutputStream(cacheFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
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
    private fun extractAndSaveFrame(
        retriever: MediaMetadataRetriever,
        cacheFile: File,
        positionKey: String,
        skipDurationCheck: Boolean = false,
    ): ThumbnailResult {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
        // 短视频（<15s）不再返回 TooShort，改为取第一个关键帧（0ms）生成缩略图；
        // 本地视频（skipDurationCheck）不受此规则影响，始终按用户配置位置取帧
        val isShort = !skipDurationCheck && durationMs != null && durationMs < MIN_DURATION_MS
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

        val scaled = scaleToMaxWidth(frame, MAX_WIDTH)
        cacheFile.parentFile?.mkdirs()
        FileOutputStream(cacheFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return ThumbnailResult.Success(cacheFile.absolutePath)
    }

    // ---------- 服务端缓存 ----------

    /**
     * 下载服务端缩略图到本地缓存。
     *
     * @return 本地缓存路径，或 null 表示下载失败
     */
    private suspend fun downloadThumbnail(
        storage: Storage,
        storageId: Int,
        videoFile: StorageFile,
        thumbFile: StorageFile,
    ): String? {
        val cacheFile = File(cacheDir, "${md5("$storageId-${videoFile.path}")}.jpg")
        if (cacheFile.exists()) return cacheFile.absolutePath

        return try {
            val input = storage.openInputStream(thumbFile)
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                input.use { it.copyTo(out) }
            }
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "downloadThumbnail failed for ${videoFile.name}: ${e.message}")
            // 下载失败删除可能损坏的半成品文件
            cacheFile.delete()
            null
        }
    }

    // ---------- 工具 ----------

    /** 等比缩放到 [maxWidth]，宽度不超时不复制。 */
    private fun scaleToMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width
        val newHeight = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, maxWidth, newHeight, true).also {
            if (it !== src) src.recycle()
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 构造视频所在目录下的 `.thumb/` 子目录路径。 */
    private fun buildThumbDirPath(videoPath: String): String {
        val dirPath = videoPath.substringBeforeLast('/', "")
        return if (dirPath.isEmpty()) ".thumb" else "$dirPath/.thumb"
    }

    /**
     * 移动/删除视频后同步删除其服务端缩略图（best-effort）。
     *
     * 视频被移动到其他目录（或删除）时，原目录 `.thumb/{视频去扩展名}-thumb.jpg` 不再有
     * 对应视频，成为孤儿。此方法将旧目录的服务端缩略图当场删掉，避免残留；
     * 新目录的缩略图由下次浏览时按需生成并上传。
     *
     * 全程 best-effort：不做 exists 前置探测（省一次远程往返，也避免探测误判导致跳过删除），
     * 忽略写回开关（孤儿清理与"生成时是否回写"无关）。删除失败仅记录日志不影响主流程。
     *
     * @param storage 存储协议实现
     * @param file 已移动/删除前的视频文件（用其旧路径计算 .thumb/ 与文件名）
     */
    suspend fun deleteServerThumbnail(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        val thumbName = "${file.name.substringBeforeLast('.')}-thumb.jpg"
        val thumbPath = buildThumbDirPath(file.path) + "/$thumbName"
        try {
            storage.deleteFile(
                object : AbstractStorageFile(
                    path = thumbPath,
                    name = thumbName,
                    isDirectory = false,
                ) {},
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "deleteServerThumbnail failed: path=$thumbPath storage=${storage.library.id}, ${e.message}")
        }
    }

    /**
     * 重命名视频后同步重命名其服务端缩略图（best-effort）。
     *
     * 名称 `{视频名}-thumb.jpg` 跟随去扩展名命名，视频只改扩展名时缩略图名不变（等价于 no-op）；
     * 改动主名时把 `{旧主名}-thumb.jpg` 改名为 `{新主名}-thumb.jpg`，保留原缩略图避免删除后
     * 重新生成。若目标已存在（刮削工具/其他设备已上传），保留现状不覆盖。
     *
     * 受写回设置门控；失败仅记录日志。
     *
     * @param storage 存储协议实现
     * @param oldFile 重命名前的视频文件
     * @param newFileName 重命名后的完整文件名（含扩展名）
     */
    suspend fun renameServerThumbnail(storage: Storage, oldFile: StorageFile, newFileName: String) = withContext(Dispatchers.IO) {
        if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return@withContext
        try {
            val thumbDir = buildThumbDirPath(oldFile.path)
            val oldThumbPath = "$thumbDir/${oldFile.name.substringBeforeLast('.')}-thumb.jpg"
            val newBasename = newFileName.substringBeforeLast('.')
            val newThumbPath = "$thumbDir/$newBasename-thumb.jpg"
            if (oldThumbPath == newThumbPath) return@withContext
            if (!storage.fileExists(oldThumbPath)) return@withContext
            // 目标已存在（可能来自刮削工具/别的设备），保留现状
            if (storage.fileExists(newThumbPath)) return@withContext
            storage.rename(
                object : AbstractStorageFile(
                    path = oldThumbPath,
                    name = "${oldFile.name.substringBeforeLast('.')}-thumb.jpg",
                    isDirectory = false,
                ) {},
                "$newBasename-thumb.jpg",
            )
        } catch (e: Exception) {
            Log.w(TAG, "renameServerThumbnail failed: ${e.message}")
        }
    }

    // ---------- 音频封面 ----------

    /**
     * 上传已生成的音频封面到服务端 `{音频目录}/.cover/{音频完整文件名}-cover.jpg`。
     *
     * BUG-4 修复：与 [uploadThumbnail] 对称，使音频封面支持跨设备复用。
     * - 受 [ThumbnailSettings.saveInSameDir] 控制
     * - 仅对非本地存储执行（[Storage.saveFile] 返回 false 自动跳过）
     * - 失败不影响本地封面显示
     *
     * BUG-T6 修复：与 [uploadThumbnail] 同步，命名改为 `{完整文件名}-cover.jpg`，
     * 避免同名异扩展名音频（如 `theme.mp3` 与 `theme.flac`）互相覆盖。
     */
    suspend fun uploadAudioCover(storage: Storage, file: StorageFile) = withContext(Dispatchers.IO) {
        if (!ThumbnailSettings.effectiveWriteBack(storage.library.id)) return@withContext
        val storageId = storage.library.id
        val cacheFile = File(audioCacheDir, "${md5("$storageId-${file.path}")}.jpg")
        if (!cacheFile.exists()) return@withContext
        try {
            val coverDirPath = buildCoverDirPath(file.path)
            storage.createDirectory(coverDirPath)
            val bytes = cacheFile.readBytes()
            // BUG-T6 修复：用完整文件名（含扩展名）避免同名异扩展名冲突
            val coverPath = "$coverDirPath/${file.name}-cover.jpg"
            // BUG-T-C1 修复：上传前检查服务端是否已存在同名封面，
            // 已存在则跳过上传，避免覆盖别设备已上传的缓存
            if (storage.fileExists(coverPath)) {
                Log.d(TAG, "uploadAudioCover skip: 服务端已存在 $coverPath")
                return@withContext
            }
            val success = storage.saveFile(coverPath, bytes)
            if (!success) {
                Log.d(TAG, "saveFile returned false for $coverPath（本地存储跳过上传）")
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadAudioCover failed: ${e.message}")
        }
    }

    /**
     * 预加载服务端已生成的音频封面到本地缓存。
     *
     * BUG-4 修复：与 [preloadThumbnails] 对称，检查 `.cover/` 子目录，
     * 对已有的 `-cover.jpg` 文件下载到本地 `audio_cover/` 缓存目录。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param files 目录下的音频文件列表
     * @param onLoaded 每下载完成一个封面时回调（path = 音频文件路径，coverPath = 本地缓存路径）
     */
    suspend fun preloadAudioCovers(
        storage: Storage,
        storageId: Int,
        files: List<StorageFile>,
        onLoaded: (String, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val audioFiles = files.filter { !it.isDirectory }
        if (audioFiles.isEmpty()) return@withContext

        val pending = mutableListOf<StorageFile>()
        for (file in audioFiles) {
            val cacheFile = File(audioCacheDir, "${md5("$storageId-${file.path}")}.jpg")
            if (cacheFile.exists()) {
                onLoaded(file.path, cacheFile.absolutePath)
            } else {
                pending.add(file)
            }
        }
        if (pending.isEmpty()) return@withContext

        // BUG-T-M3 修复（对称修复）：按目录分组预加载，支持 pending 跨多个目录的场景
        // （generateRemoteThumbnails 调用时 pending 来自播放历史，可能跨多目录）
        val byDir: Map<String, List<StorageFile>> = pending.groupBy { it.path.substringBeforeLast('/', "") }

        // v1 启发：先检查每目录下 cover.jpg / folder.jpg 等常见封面文件，
        // 命中后一次复制到目录下所有音频的缓存，避免逐个全量读取。
        val loadedFromDirCover = mutableSetOf<String>()
        for ((dirPath, filesInDir) in byDir) {
            val dirCandidates = listOf(
                "cover.jpg", "cover.jpeg", "cover.png",
                "folder.jpg", "folder.jpeg", "folder.png",
                "album.jpg", "album.jpeg", "album.png",
            )
            val matchedCandidate = dirCandidates.firstOrNull { candidate ->
                val checkPath = if (dirPath.isEmpty()) candidate else "$dirPath/$candidate"
                try { storage.fileExists(checkPath) } catch (_: Exception) { false }
            } ?: continue

            val coverPath = if (dirPath.isEmpty()) matchedCandidate else "$dirPath/$matchedCandidate"
            val coverStorageFile = object : AbstractStorageFile(
                path = coverPath,
                name = matchedCandidate,
                isDirectory = false,
            ) {}
            val coverBytes = try {
                storage.openInputStream(coverStorageFile)?.use { it.readBytes() }
            } catch (_: Exception) { null } ?: continue

            for (file in filesInDir) {
                val cacheFile = File(audioCacheDir, "${md5("$storageId-${file.path}")}.jpg")
                cacheFile.parentFile?.mkdirs()
                try {
                    FileOutputStream(cacheFile).use { it.write(coverBytes) }
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        onLoaded(file.path, cacheFile.absolutePath)
                        loadedFromDirCover.add(file.path)
                    }
                } catch (_: Exception) {}
            }
        }
        pending.removeAll { loadedFromDirCover.contains(it.path) }
        if (pending.isEmpty()) return@withContext

        val concurrency = minOf(storage.thumbnailConcurrency, pending.size)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            for ((dirPath, filesInDir) in byDir) {
                // 跳过已被目录封面覆盖的目录
                if (filesInDir.all { it.path in loadedFromDirCover }) continue
                val coverDirPath = if (dirPath.isEmpty()) ".cover" else "$dirPath/.cover"
                // BUG-T-M3 对称修复：listFiles 失败改为 emptyList（而非 return@withContext），
                // 让其他目录继续尝试
                val coverFiles = try {
                    storage.listFiles(CoverDirFile(coverDirPath))
                } catch (e: Exception) {
                    emptyList()
                }

                val coverMap = mutableMapOf<String, StorageFile>()
                for (cf in coverFiles) {
                    val name = cf.name.removeSuffix("-cover.jpg").removeSuffix("-cover.jpeg")
                    if (name.isNotEmpty()) {
                        coverMap[name] = cf
                    }
                }

                for (file in filesInDir) {
                    // BUG-T6 修复：用完整文件名（含扩展名）匹配，与 uploadAudioCover 命名一致
                    val coverFile = coverMap[file.name] ?: continue
                    launch {
                        semaphore.withPermit {
                            try {
                                val path = downloadAudioCover(storage, storageId, file, coverFile)
                                if (path != null) onLoaded(file.path, path)
                            } catch (e: Exception) {
                                // m-12 修复：原 `catch (_: Exception) {}` 完全静默
                                Log.w(TAG, "preloadAudioCovers downloadAudioCover failed: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 下载服务端音频封面到本地缓存。 */
    private suspend fun downloadAudioCover(
        storage: Storage,
        storageId: Int,
        audioFile: StorageFile,
        coverFile: StorageFile,
    ): String? {
        val cacheFile = File(audioCacheDir, "${md5("$storageId-${audioFile.path}")}.jpg")
        if (cacheFile.exists()) return cacheFile.absolutePath

        return try {
            val input = storage.openInputStream(coverFile)
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                input.use { it.copyTo(out) }
            }
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "downloadAudioCover failed for ${audioFile.name}: ${e.message}")
            cacheFile.delete()
            null
        }
    }

    /** 构造音频所在目录下的 `.cover/` 子目录路径。 */
    private fun buildCoverDirPath(audioPath: String): String {
        val dirPath = audioPath.substringBeforeLast('/', "")
        return if (dirPath.isEmpty()) ".cover" else "$dirPath/.cover"
    }

    /**
     * 通过仅读取远程音频文件头部来提取内嵌封面。
     *
     * 对 WebDAV / SMB 等远程文件，[MediaMetadataRetriever.setDataSource] 会下载整个文件。
     * 而大多数音频文件的嵌入封面位于文件头部的 ID3v2 / FLAC METADATA_BLOCK_PICTURE /
     * MP4 covr 中，只需读取前 [HEADER_READ_LIMIT] 字节即可提取封面，避免全量下载。
     * 失败时返回 null，由调用方回退到全文件提取。
     *
     * @param storage 存储协议实现
     * @param file 目标音频文件
     * @param cacheFile 本地缓存文件（已按 MD5 命名）
     * @return 本地缓存路径，或 null 表示头部未提取到封面
     */
    private suspend fun extractAudioCoverFromHeader(
        storage: Storage,
        file: StorageFile,
        cacheFile: File,
    ): String? {
        val headerBytes = try {
            storage.readFileBytes(file, HEADER_READ_LIMIT)
        } catch (e: Exception) {
            Log.w(TAG, "extractAudioCoverFromHeader read failed: ${e.message}")
            null
        } ?: return null

        val dataSource = object : MediaDataSource() {
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position >= headerBytes.size) return -1
                val count = minOf(size, headerBytes.size - position.toInt())
                System.arraycopy(headerBytes, position.toInt(), buffer, offset, count)
                return count
            }
            override fun getSize(): Long = headerBytes.size.toLong()
            override fun close() {}
        }

        return extractAudioCoverFromDataSource(dataSource, cacheFile)
    }



    /**
     * 检查音频文件同级目录下是否存在常见的封面图片文件。
     *
     * v1 启发：在调用 [MediaMetadataRetriever.embeddedPicture] 全量读取远程音频
     * 文件之前，先检查同级目录下是否有自然存在的封面文件（如 `cover.jpg`、
     * `folder.jpg`、`{文件名}.jpg` 等）。对 SMB/WebDAV 而言只需一次轻量的
     * [Storage.fileExists] 元数据查询，远快于传输整个音频文件。
     *
     * 命中后将封面复制到 `audio_cover/` 缓存目录，后续走本地缓存路径。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param file 目标音频文件
     * @param cacheFile 本地缓存文件（已按 MD5 命名）
     * @return 本地缓存路径，或 null 表示未找到目录封面
     */
    private suspend fun findDirectoryCover(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
        cacheFile: File,
    ): String? {
        val dirPath = file.path.substringBeforeLast('/', "")
        val nameWithoutExt = file.name.substringBeforeLast(".")

        val candidates = listOf(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "album.jpg", "album.jpeg", "album.png",
            "${nameWithoutExt}.jpg", "${nameWithoutExt}.jpeg", "${nameWithoutExt}.png",
        )

        for (candidate in candidates) {
            val coverPath = if (dirPath.isEmpty()) candidate else "$dirPath/$candidate"
            try {
                if (storage.fileExists(coverPath)) {
                    cacheFile.parentFile?.mkdirs()
                    val coverFile = object : AbstractStorageFile(
                        path = coverPath,
                        name = candidate,
                        isDirectory = false,
                    ) {}
                    storage.openInputStream(coverFile)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        return cacheFile.absolutePath
                    }
                }
            } catch (_: Exception) {
                // 单个候选文件失败不影响其他候选
            }
        }
        return null
    }

    /**
     * 提取音频文件的嵌入封面图。
     *
     * 通过 [MediaMetadataRetriever.embeddedPicture] 提取音频文件内嵌的专辑封面，
     * 缩放后缓存到 `audio_cover/` 目录。无内嵌封面时返回 null。
     *
     * **协议适配**：
     * - Local / WebDAV：优先 URL → 回退 MediaDataSource
     * - SMB：MediaDataSource
     *
     * **注意**：每个 [MediaMetadataRetriever] 实例只绑定一种数据源（URL 或
     * MediaDataSource），切换前先 release 旧实例，避免 Android HTTP 连接泄露
     * 导致 [NullPointerException]。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param file 目标音频文件
     * @return 本地缓存路径，或 null 表示无封面/提取失败
     */
    suspend fun generateAudioCover(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
    ): String? = withContext(Dispatchers.IO) {
        // ARCH-3 修复：fail-fast，防止误传视频文件（部分 MP4 有封面但语义错误，且会绕过视频缩略图流程）
        require(MediaFileTypes.isAudioFile(file.name)) {
            "generateAudioCover 要求音频文件，收到 ${file.name}"
        }
        val cacheFile = File(audioCacheDir, "${md5("$storageId-${file.path}")}.jpg")
        if (cacheFile.exists()) return@withContext cacheFile.absolutePath

        // BUG-08 修复：用 withLock 替代 lock + finally { unlock }
        // BUG-08 补充：withLock 内用 return@withLock，trimCacheIfNeeded 在所有路径执行。
        val mutex = getMutex(cacheFile.name)
        val result = mutex.withLock {
            if (cacheFile.exists()) return@withLock cacheFile.absolutePath

            val url = storage.createPlayUrl(file)

            // 1. 本地文件：尝试提取内嵌封面
            if (url != null && (url.startsWith("file") || url.startsWith("content"))) {
                val embedded = extractAudioCoverFromUrl(context, url, cacheFile)
                if (embedded != null) return@withLock embedded
            }

            // 2. 同级目录 cover.jpg / folder.jpg 等常见封面文件
            val dirCover = findDirectoryCover(storage, storageId, file, cacheFile)
            if (dirCover != null) return@withLock dirCover

            // 3. 头部读取内嵌封面（远程文件）
            val viaHeader: String? = if (url != null && url.startsWith("http", ignoreCase = true)) {
                val headers = storage.getPlayHeaders()
                if (headers.isNotEmpty()) {
                    extractAudioCoverFromHeader(storage, file, cacheFile)
                } else null
            } else {
                extractAudioCoverFromHeader(storage, file, cacheFile)
            }
            if (viaHeader != null) return@withLock viaHeader

            // 4. API 远程获取封面（本地提取均失败时回退）
            val apiCover = fetchAudioCoverFromApi(storageId, file, cacheFile)
            if (apiCover != null) return@withLock apiCover

            // 5. 所有方式均失败，标记 no_cover 避免下次重复尝试
            markNoCover(storageId, file.path)
            return@withLock null
        }
        // BUG-T7 修复：生成后检查缓存目录大小，超出阈值时淘汰最旧文件
        trimCacheIfNeeded(audioCacheDir)
        // W-N5 修复：清理空闲 Mutex
        releaseMutexIfIdle(cacheFile.name, mutex)
        result
    }

    // ---------- 批量远程生成 ----------

    /**
     * 为远程存储的文件批量生成缩略图。
     *
     * BUG-H4 修复：提取 HomeTabViewModel / PlayHistoryViewModel 中重复的私有
     * `generateRemoteThumbnails` 方法到此公共入口，消除约 80 行重复代码。
     *
     * BUG-H5 修复：音频组同样使用 [Semaphore] 控制并发（原实现无限制 launch，
     * 大量音频文件同时打开 MediaDataSource 导致 SMB file handle 暴涨）。
     *
     * 流程（按存储源生效策略 [ThumbnailSettings.effectiveMode] 门控）：
     * - 关闭：跳过全部，仅使用已有缓存
     * - 仅播放后生成：只执行 preload（服务端已有缓存复用），跳过批量取帧/提取，
     *   缩略图在播放退出后由播放路径补生成，规避浏览时批量读取导致的网盘封控
     * - 全部生成（默认）：
     *   - 视频组：先 [preloadThumbnails] 预加载服务端 `.thumb/` 缓存，
     *     剩余项用 [Semaphore] 并发调用 [generateThumbnail] 取帧，
     *     生成成功后同步等待 [uploadThumbnail] 上传到服务端（BUG-T-M2 修复）
     *   - 音频组：先 [preloadAudioCovers] 预加载服务端 `.cover/` 缓存（BUG-T-M2 修复），
     *     剩余项用 [Semaphore] 并发调用 [generateAudioCover] 提取内嵌专辑封面，
     *     生成成功后同步等待 [uploadAudioCover] 上传到服务端（BUG-T-M2 修复）
     *
     * R6 修复：取帧与上传均以 [coroutineScope] 结构化并发等待完成后再返回，
     * 消除原 fire-and-forget（`withContext` 不等待其子协程）导致的两个问题：
     * 1) 调用方 `finally` 提前 [Storage.close] 后，迟到协程的 SMB/WebDAV 取帧失败；
     * 2) 晚到的 [onLoaded] 写入 batchAccumulator 后 flusher 已取消，本次会话首刷不显示。
     *
     * R7 修复：失败项（过短 / 401 / 403 / 临时网络错误 / 无内嵌封面）写入进程内
     * TTL 冷却表，冷却期内跳过重复生成，避免每次刷新列表都对同一文件重复远程读取。
     *
     * @param storage 存储协议实现（调用方负责按 storageId 分组并创建实例）
     * @param requests 同一存储源的缩略图请求列表
     * @param onLoaded 每生成一个缩略图时回调（url → 本地缓存路径）
     */
    suspend fun generateRemoteThumbnails(
        storage: Storage,
        requests: List<RemoteThumbnailRequest>,
        onLoaded: (url: String, thumbPath: String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!ThumbnailSettings.generateThumbnail) return@withContext
        if (requests.isEmpty()) return@withContext
        val storageId = storage.library.id

        // 存储源生效策略检查：关闭模式跳过全部（含 preload）；
        // "仅播放后生成"模式仅 preload 服务端已有缓存，跳过批量取帧（浏览时避免大量文件读取）
        val mode = ThumbnailSettings.effectiveMode(storageId)
        val browseGenerationAllowed = mode == ThumbnailGenerationMode.ALL
        if (mode == ThumbnailGenerationMode.OFF) return@withContext

        // R7 修复：过滤近期失败项（401/403 永久失败、<15s 过短、临时网络错误等），
        // 避免每次刷新列表都对同一失败文件重复发起远程读取（重试风暴与网盘封控风险）。
        // 失败标记为进程内 TTL 内存表，到期后自动恢复重试；清理缓存后手动刷新亦会恢复。
        val pendingRequests = requests.filter { !hasRecentFailure(storageId, it) }
        if (pendingRequests.isEmpty()) return@withContext

        // ---- 视频缩略图 ----
        if (ThumbnailSettings.generateForVideo) {
            val videoGroup = pendingRequests.filter { !it.isAudio }
            if (videoGroup.isNotEmpty()) {
                // Step 1: 预加载服务端 .thumb/ 缩略图
                val videoFiles = videoGroup.map { req ->
                    object : AbstractStorageFile(
                        path = req.filePath,
                        name = req.fileName,
                        isDirectory = false,
                    ) {}
                }
                val loaded = java.util.Collections.synchronizedSet(mutableSetOf<String>())
                preloadThumbnails(storage, storageId, videoFiles, onLoaded = { filePath, thumbPath ->
                    val matched = videoGroup.find { it.filePath == filePath }
                    if (matched != null && loaded.add(matched.url)) {
                        onLoaded(matched.url, thumbPath)
                    }
                })

                // Step 2: 对剩余项实时取帧（仅"全部生成"模式；"仅播放后生成"模式
                // 跳过浏览时批量取帧，缩略图在播放退出后由播放路径补生成）
                val remaining = videoGroup.filter { it.url !in loaded }
                if (browseGenerationAllowed && remaining.isNotEmpty()) {
                    val concurrency = minOf(storage.thumbnailConcurrency, remaining.size)
                    val semaphore = Semaphore(concurrency)
                    // BUG-T-M2 修复：收集生成成功的 file 用于上传
                    val successFiles = java.util.Collections.synchronizedList(mutableListOf<StorageFile>())
                    // R6 修复：coroutineScope 结构化并发——等待本组全部生成完成后再返回。
                    // 原 launch 直接挂在 withContext 的 block scope 下（withContext 不等待
                    // 子协程），导致：1) 调用方 finally 提前 storage.close()，迟到协程取帧
                    // 失败（SMB session / WebDAV 连接已断）；2) 晚到的 onLoaded 写入
                    // batchAccumulator 后 flusher 已 cancel，本次 UI 会话首刷不显示。
                    coroutineScope {
                        for (req in remaining) {
                            launch {
                                semaphore.withPermit {
                                    try {
                                        val file = object : AbstractStorageFile(
                                            path = req.filePath,
                                            name = req.fileName,
                                            isDirectory = false,
                                        ) {}
                                        when (val result = generateThumbnail(
                                            storage, storageId, file,
                                            positionKey = ThumbnailSettings.framePositionKey,
                                        )) {
                                            is ThumbnailResult.Success -> {
                                                onLoaded(req.url, result.path)
                                                successFiles.add(file)
                                            }
                                            // R7 修复：失败分类标记，TTL 内跳过重复重试。
                                            // 过短/401/403 为确定性失败，长 TTL；其余临时失败短 TTL
                                            is ThumbnailResult.TooShort ->
                                                markFailure(storageId, req, FAILURE_RETRY_LONG_MS)
                                            is ThumbnailResult.PermanentFailure ->
                                                markFailure(storageId, req, FAILURE_RETRY_LONG_MS)
                                            is ThumbnailResult.Failed ->
                                                markFailure(storageId, req, FAILURE_RETRY_SHORT_MS)
                                        }
                                    } catch (e: Exception) {
                                        // m-12 修复：原 `catch (_: Exception) {}` 完全静默
                                        Log.w(TAG, "generateRemoteThumbnails video generate failed: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    }

                    // BUG-T-M2 修复：Step 3 - 上传新生成的缩略图到服务端 .thumb/
                    // uploadThumbnail 内部已应用 BUG-T-C1 fileExists 检查，不覆盖服务端已有文件
                    // R6 修复：coroutineScope 等待上传完成，原因同 Step 2（storage 生命周期归调用方）
                    if (ThumbnailSettings.effectiveWriteBack(storage.library.id) && successFiles.isNotEmpty()) {
                        val uploadConcurrency = minOf(storage.thumbnailConcurrency, successFiles.size)
                        val uploadSemaphore = Semaphore(uploadConcurrency)
                        coroutineScope {
                            for (file in successFiles) {
                                launch {
                                    uploadSemaphore.withPermit {
                                        try {
                                            uploadThumbnail(storage, file)
                                        } catch (e: Exception) {
                                            // m-12 修复：原 `catch (_: Exception) {}` 完全静默
                                            Log.w(TAG, "generateRemoteThumbnails uploadThumbnail failed: ${e.message}", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 音频封面 ----
        if (ThumbnailSettings.generateForAudio) {
            val audioGroup = pendingRequests.filter { it.isAudio }
            if (audioGroup.isNotEmpty()) {
                // BUG-T-M2 修复：Step 1 - 预加载服务端 .cover/ 封面缓存
                val audioFiles = audioGroup.map { req ->
                    object : AbstractStorageFile(
                        path = req.filePath,
                        name = req.fileName,
                        isDirectory = false,
                    ) {}
                }
                val loaded = java.util.Collections.synchronizedSet(mutableSetOf<String>())
                preloadAudioCovers(storage, storageId, audioFiles, onLoaded = { filePath, coverPath ->
                    val matched = audioGroup.find { it.filePath == filePath }
                    if (matched != null && loaded.add(matched.url)) {
                        onLoaded(matched.url, coverPath)
                    }
                })

                // Step 2: 对剩余项实时提取内嵌封面（仅"全部生成"模式，同视频组）
                val remaining = audioGroup.filter { it.url !in loaded }
                if (browseGenerationAllowed && remaining.isNotEmpty()) {
                    // BUG-H5 修复：音频组也用 Semaphore 控制并发，
                    // 避免大量音频文件同时打开 MediaDataSource 导致 SMB file handle 暴涨
                    val concurrency = minOf(storage.thumbnailConcurrency, remaining.size)
                    val semaphore = Semaphore(concurrency)
                    // BUG-T-M2 修复：收集生成成功的 file 用于上传
                    val successFiles = java.util.Collections.synchronizedList(mutableListOf<StorageFile>())
                    // R6 修复：coroutineScope 结构化并发，等待本组全部完成（原因同视频组）
                    coroutineScope {
                        for (req in remaining) {
                            launch {
                                semaphore.withPermit {
                                    try {
                                        val file = object : AbstractStorageFile(
                                            path = req.filePath,
                                            name = req.fileName,
                                            isDirectory = false,
                                        ) {}
                                        val path = generateAudioCover(storage, storageId, file)
                                        if (path != null) {
                                            onLoaded(req.url, path)
                                            successFiles.add(file)
                                        } else {
                                            // R7 修复：提取失败标记短 TTL，避免无封面音频每次刷新重复读取
                                            markFailure(storageId, req, FAILURE_RETRY_SHORT_MS)
                                        }
                                    } catch (e: Exception) {
                                        // m-12 修复：原 `catch (_: Exception) {}` 完全静默
                                        Log.w(TAG, "generateRemoteThumbnails audio generate failed: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    }

                    // BUG-T-M2 修复：Step 3 - 上传新生成的封面到服务端 .cover/
                    // uploadAudioCover 内部已应用 BUG-T-C1 fileExists 检查
                    // R6 修复：coroutineScope 等待上传完成（原因同视频组）
                    if (ThumbnailSettings.effectiveWriteBack(storage.library.id) && successFiles.isNotEmpty()) {
                        val uploadConcurrency = minOf(storage.thumbnailConcurrency, successFiles.size)
                        val uploadSemaphore = Semaphore(uploadConcurrency)
                        coroutineScope {
                            for (file in successFiles) {
                                launch {
                                    uploadSemaphore.withPermit {
                                        try {
                                            uploadAudioCover(storage, file)
                                        } catch (e: Exception) {
                                            // m-12 修复：原 `catch (_: Exception) {}` 完全静默
                                            Log.w(TAG, "generateRemoteThumbnails uploadAudioCover failed: ${e.message}", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 图片缩略图 ----------

    /**
     * 生成图片文件的缩略图。
     *
     * 通过 [Storage.openInputStream] 读取原始图片字节，使用 [BitmapFactory.Options.inSampleSize]
     * 降采样到最大宽高 [IMAGE_THUMB_MAX]px，JPEG quality=80 缓存到 `image_thumb/` 目录。
     *
     * 适用于所有存储协议（Local/SAF/WebDAV/SMB）。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param file 目标图片文件
     * @return 本地缓存路径，或 null 表示解码失败
     */
    suspend fun generateImageThumbnail(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
    ): String? = withContext(Dispatchers.IO) {
        // ARCH-3 修复：fail-fast，防止误传视频/音频文件（BitmapFactory 对视频首帧也能解码，但语义错误）
        require(MediaFileTypes.isImageFile(file.name)) {
            "generateImageThumbnail 要求图片文件，收到 ${file.name}"
        }
        val cacheFile = File(imageCacheDir, "${md5("$storageId-${file.path}")}.jpg")
        if (cacheFile.exists()) return@withContext cacheFile.absolutePath

        // BUG-08 修复：用 withLock 替代 lock + finally { unlock }
        val mutex = getMutex(cacheFile.name)
        // BUG-T-M5 修复：trimCacheIfNeeded / releaseMutexIfIdle 移到 withLock 外部，
        // 与 generateThumbnail / generateAudioCover 等方法对齐，
        // 确保缓存命中、解码失败、生成成功所有路径都执行缓存清理和 Mutex 清理
        val result = mutex.withLock {
            if (cacheFile.exists()) return@withLock cacheFile.absolutePath

            // 单次流式解码：通过 BufferedInputStream mark/reset 避免两次打开 SMB 流。
            //
            // 原两阶段方案需打开两次 InputStream 分别读取边界和解码，
            // 因为 InputStream 不支持 reset。对 SMB 而言，每次 openInputStream 都会创建
            // SmbParallelInputStream（含 4 个预读线程和 SMB File handle），
            // 两次打开导致 8 个预读线程的创建/销毁开销，以及多余的 SMB 文件打开往返。
            //
            // 优化：用 BufferedInputStream（64KB 缓冲区）包裹单次打开的 InputStream，
            // mark 后读取边界（仅读 JPEG 头几 KB），reset 回到起点后正式解码，
            // 将两次 openInputStream 减为一次，SMB 预读线程的缓冲数据也可被解码阶段复用。
            //
            // 注意：BufferedInputStream.markSupported() 返回 true，
            // BitmapFactory.decodeStream 不关闭流，use 块结束时自动关闭。
            val rawInput = storage.openInputStream(file)
            val bitmap = rawInput.use { raw ->
                BufferedInputStream(raw, BUFFER_SIZE).use { stream ->
                    // Phase 1: 仅读取图片宽高，不分配像素内存
                    stream.mark(BUFFER_SIZE)
                    val boundsOpts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@use null

                    // Phase 2: reset 到流起点，带 inSampleSize 解码
                    stream.reset()
                    val sampleSize = computeInSampleSize(
                        boundsOpts.outWidth, boundsOpts.outHeight, IMAGE_THUMB_MAX,
                    )
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inJustDecodeBounds = false
                    }
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            } ?: return@withLock null

            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            cacheFile.absolutePath
        }
        // BUG-T7 修复：生成后检查缓存目录大小，超出阈值时淘汰最旧文件
        // BUG-T-M5 修复：移到 withLock 外部，所有路径都执行
        trimCacheIfNeeded(imageCacheDir)
        // W-N5 修复：清理空闲 Mutex
        releaseMutexIfIdle(cacheFile.name, mutex)
        result
    }

    /** 计算 [inSampleSize]：2 的幂倍，使原图至少一边不超 [maxDimension]。 */
    private fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    // ---------- 音频封面辅助 ----------

    private fun extractAudioCoverFromUrl(context: Context, url: String, cacheFile: File): String? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(url))
            return extractEmbeddedPicture(retriever, cacheFile)
        } catch (e: Exception) {
            // m-12 修复：原 `catch (_: Exception)` 静默吞掉，远程音频封面失败无法排查
            Log.w(TAG, "extractAudioCoverFromUrl(context,url) failed: ${e.message}", e)
            return null
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Log.w(TAG, "retriever.release() failed in extractAudioCoverFromUrl(context,url): ${e.message}")
            }
        }
    }

    private fun extractAudioCoverFromUrl(url: String, headers: Map<String, String>, cacheFile: File): String? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, headers)
            return extractEmbeddedPicture(retriever, cacheFile)
        } catch (e: Exception) {
            // m-12 修复：原 `catch (_: Exception)` 静默吞掉
            Log.w(TAG, "extractAudioCoverFromUrl(url,headers) failed: ${e.message}", e)
            return null
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Log.w(TAG, "retriever.release() failed in extractAudioCoverFromUrl(url,headers): ${e.message}")
            }
        }
    }

    private fun extractAudioCoverFromDataSource(dataSource: MediaDataSource, cacheFile: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            extractEmbeddedPicture(retriever, cacheFile)
        } catch (e: Exception) {
            // m-12 修复：原 `catch (_: Exception)` 静默吞掉
            Log.w(TAG, "extractAudioCoverFromDataSource failed: ${e.message}", e)
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Log.w(TAG, "retriever.release() failed in extractAudioCoverFromDataSource: ${e.message}")
            }
            try { dataSource.close() } catch (e: Exception) {
                Log.w(TAG, "dataSource.close() failed in extractAudioCoverFromDataSource: ${e.message}")
            }
        }
    }

    private fun extractEmbeddedPicture(retriever: MediaMetadataRetriever, cacheFile: File): String? {
        // BUG-12 修复：部分损坏的 FLAC/OGG 在 embeddedPicture 调用时抛 RuntimeException，
        // 上层 try-catch 仅记录 "generateAudioCover failed" 不区分失败阶段，
        // 此处单独捕获并记录精确日志，便于排查音频格式兼容性问题
        return try {
            val pictureData = retriever.embeddedPicture ?: return null
            val bitmap = BitmapFactory.decodeByteArray(pictureData, 0, pictureData.size) ?: return null
            val scaled = scaleToMaxWidth(bitmap, MAX_WIDTH)
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "extractEmbeddedPicture failed: ${e.message}")
            null
        }
    }

    // ---------- 进度条帧预览 ----------

    /**
     * 生成视频指定时间位置的帧预览图。
     *
     * 用于播放器拖动进度条时显示帧预览浮层。缓存到 `seek_preview/` 目录，
     * 自动限制缓存文件数不超过 [SEEK_CACHE_MAX_FILES]。
     *
     * 协议适配同 [generateThumbnail]：Local/WebDAV 走 URL，SMB 走 MediaDataSource。
     *
     * @param storage 存储协议实现
     * @param storageId 媒体库 id，参与本地缓存 key
     * @param file 目标视频文件
     * @param positionMs 取帧位置（毫秒）
     * @return 本地缓存路径，或 null 表示取帧失败
     */
    suspend fun generateThumbnailAt(
        storage: Storage,
        storageId: Int,
        file: StorageFile,
        positionMs: Long,
    ): String? = withContext(Dispatchers.IO) {
        // ARCH-3 修复：fail-fast，防止误传音频文件（音频无视频帧，getFrameAtTime 必然返回 null）
        require(MediaFileTypes.isVideoFile(file.name)) {
            "generateThumbnailAt 要求视频文件，收到 ${file.name}"
        }
        val cacheKey = "${md5("$storageId-${file.path}")}_$positionMs"
        val cacheFile = File(seekCacheDir, "${cacheKey}.jpg")
        if (cacheFile.exists()) return@withContext cacheFile.absolutePath

        // BUG-08 修复：用 withLock 替代 lock + finally { unlock }
        // M-22 修复：把所有 return@withContext 改为 return@withLock，
        // 用 val result 接收返回值，确保 withLock 块外的 releaseMutexIfIdle 在所有路径执行。
        val mutex = getMutex(cacheFile.name)
        val result = mutex.withLock {
            if (cacheFile.exists()) return@withLock cacheFile.absolutePath

            var retriever: MediaMetadataRetriever? = MediaMetadataRetriever()
            try {
                val url = storage.createPlayUrl(file)
                if (url != null && (url.startsWith("file") || url.startsWith("content"))) {
                    retriever!!.setDataSource(context, Uri.parse(url))
                } else if (url != null && url.startsWith("http", ignoreCase = true)) {
                    val headers = storage.getPlayHeaders()
                    var urlHeadersSucceeded = false
                    if (headers.isNotEmpty()) {
                        try {
                            retriever!!.setDataSource(url, headers)
                            var frame = retriever.getFrameAtTime(
                                positionMs * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            )
                            if (frame != null) {
                                // HDR 软件色调映射补偿（API 34+ 系统自动处理）
                                frame = applyHdrCompensationIfNeeded(frame, isHdrVideo(retriever!!))
                                val scaled = scaleToMaxWidth(frame, MAX_WIDTH)
                                cacheFile.parentFile?.mkdirs()
                                FileOutputStream(cacheFile).use { out ->
                                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                                }
                                cleanupSeekCache()
                                return@withLock cacheFile.absolutePath
                            }
                            urlHeadersSucceeded = true
                        } catch (_: Exception) { }
                    }
                    // BUG-T-m1 修复：MediaMetadataRetriever 文档说明每个实例只绑定一种数据源，
                    // 切换前应 release 旧实例。原实现直接在同一个 retriever 上 setDataSource
                    // 第二次（MediaDataSource 或 URL），可能引发异常、NPE 或取帧失败。
                    // 现回退时 release 旧 retriever 并置 null（外层 finally 据 null 判断），
                    // 创建新实例绑定新数据源，最后将引用赋回 retriever 供外层 finally 统一释放。
                    if (!urlHeadersSucceeded) {
                        try { retriever!!.release() } catch (e: Exception) {
                            // m-12 修复：原静默吞掉，便于排查 retriever.release 异常
                            Log.w(TAG, "generateThumbnailAt retriever.release failed: ${e.message}")
                        }
                        retriever = null
                    }
                    val retriever2 = MediaMetadataRetriever()
                    retriever = retriever2
                    val dataSource = storage.openMediaDataSource(file)
                    if (dataSource != null) {
                        retriever2.setDataSource(dataSource)
                    } else {
                        retriever2.setDataSource(context, Uri.parse(url))
                    }
                    var frame = retriever2.getFrameAtTime(
                        positionMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    ) ?: return@withLock null

                    // HDR 软件色调映射补偿（API 34+ 系统自动处理）
                    frame = applyHdrCompensationIfNeeded(frame, isHdrVideo(retriever2))

                    val scaled = scaleToMaxWidth(frame, MAX_WIDTH)
                    cacheFile.parentFile?.mkdirs()
                    FileOutputStream(cacheFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }

                    cleanupSeekCache()
                    return@withLock cacheFile.absolutePath
                } else {
                    val dataSource = storage.openMediaDataSource(file)
                    if (dataSource == null) return@withLock null
                    retriever!!.setDataSource(dataSource)
                }

                var frame = retriever!!.getFrameAtTime(
                    positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: return@withLock null

                // HDR 软件色调映射补偿（API 34+ 系统自动处理）
                frame = applyHdrCompensationIfNeeded(frame, isHdrVideo(retriever!!))

                val scaled = scaleToMaxWidth(frame, MAX_WIDTH)
                cacheFile.parentFile?.mkdirs()
                FileOutputStream(cacheFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }

                cleanupSeekCache()
                cacheFile.absolutePath
            } finally {
                // BUG-T-m1 修复：retriever 可能在 http 回退分支已被 release 并置 null，
                // 此处只释放仍存在的实例，避免二次 release 抛 IllegalStateException
                try { retriever?.release() } catch (e: Exception) {
                    // m-12 修复：原静默吞掉，便于排查二次 release 异常
                    Log.w(TAG, "generateThumbnailAt outer retriever.release failed: ${e.message}")
                }
            }
        }
        // M-22 修复：seek 路径的 mutex key 含 _${positionMs}，项数随 seek 操作无限膨胀。
        // 与其他 generate 方法对齐，withLock 后调用 releaseMutexIfIdle 清理空闲 Mutex。
        releaseMutexIfIdle(cacheFile.name, mutex)
        result
    }

    /** 限制 seek 预览缓存文件数，超出时删除最早的。 */
    private fun cleanupSeekCache() {
        val files = seekCacheDir.listFiles() ?: return
        if (files.size <= SEEK_CACHE_MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - SEEK_CACHE_MAX_FILES)
            .forEach { it.delete() }
    }

    private companion object {
        const val TAG = "ThumbnailManager"
        /** 缩略图最大宽度（px）。480 在常见列表宽度（160-200dp @ xxhdpi）下足够清晰。 */
        const val MAX_WIDTH = 480
        /** 图片缩略图最大边长（px）。 */
        const val IMAGE_THUMB_MAX = 480
        /** JPEG 压缩质量。90 显著减少 artifacts，文件大小约比 80 增加 30-50%。 */
        const val JPEG_QUALITY = 90
        /**
         * BufferedInputStream 缓冲区 / mark 限制大小。
         *
         * 图片缩略图单次流解码时用于包裹 SMB InputStream，mark 后读取 JPEG 头（几 KB），
         * reset 回到起点后重新解码。JPEG 边界读取仅需 ~1KB，64KB 余量充足。
         */
        private const val BUFFER_SIZE = 64 * 1024
        const val MIN_DURATION_MS = 15000L
        const val SEEK_CACHE_MAX_FILES = 20
        /** 头部读取方式提取封面时的最大读取字节数（2MB），
         *  覆盖绝大多数含内嵌封面的 ID3v2 / FLAC 头。 */
        private const val HEADER_READ_LIMIT = 2 * 1024 * 1024
        /**
         * 单个缩略图缓存目录大小上限（BUG-T7 修复）。
         * 200MB 覆盖约 600-800 张缩略图（按平均 300KB/张估算），
         * 超出时按 lastModified 淘汰最旧文件。
         */
        const val MAX_CACHE_BYTES = 200L * 1024 * 1024
        /** HDR 软件色调映射线性增益（HLG/PQ → SDR 近似）。 */
        const val HDR_TONE_MAP_GAIN = 3.0f
        /** ColorTransfer = SMPTE ST 2084 (PQ, HDR10 / Dolby Vision)。 */
        const val COLOR_TRANSFER_SMPTE_ST_2084 = 7
        /** ColorTransfer = ARIB STD-B67 (HLG)。 */
        const val COLOR_TRANSFER_HLG = 18
        /**
         * R7 修复：批量生成失败项的短重试间隔（临时失败，如网络/IO 抖动）。
         * 5 分钟内对同一文件跳过重试，避免每次刷新列表都重复发起远程读取。
         */
        private const val FAILURE_RETRY_SHORT_MS = 5 * 60 * 1000L
        /**
         * R7 修复：批量生成失败项的长重试间隔（确定性失败：<15s 过短、401/403
         * 永久失败、无内嵌封面等）。1 小时后自动恢复重试，不永久阻断。
         */
        private const val FAILURE_RETRY_LONG_MS = 60 * 60 * 1000L
        /**
         * R7 修复：批量生成失败项 → 下次可重试的挂起截止时间（进程内 TTL 内存表）。
         * key 为 "storageId-filePath"，TTL 到期自动移除；清理缓存后手动刷新亦会恢复。
         */
        private val failureRetryAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

        private fun failureKey(storageId: Int, req: RemoteThumbnailRequest): String =
            "$storageId-${req.filePath}"

        /** R7 修复：该请求是否处于失败冷却期（TTL 内跳过重试）。 */
        private fun hasRecentFailure(storageId: Int, req: RemoteThumbnailRequest): Boolean =
            failureRetryAt[failureKey(storageId, req)]?.let { it > System.currentTimeMillis() } ?: false

        /** R7 修复：标记请求失败，ttlMs 内 [hasRecentFailure] 返回 true。 */
        private fun markFailure(storageId: Int, req: RemoteThumbnailRequest, ttlMs: Long) {
            failureRetryAt[failureKey(storageId, req)] = System.currentTimeMillis() + ttlMs
        }
    }
}

/**
 * 根据 [ThumbnailSettings] 的配置计算取帧位置（毫秒）。
 *
 * @param durationMs 视频时长（毫秒）
 * @param positionKey [ThumbnailSettings.framePositionKey]
 * @return 取帧位置（毫秒），始终在 [0, durationMs] 范围内
 */
fun calculateFramePositionMs(
    durationMs: Long,
    positionKey: String,
): Long {
    val frameMs = when (positionKey) {
        "5s" -> 5000L
        "10pct" -> (durationMs * 0.1).toLong()
        "50pct" -> (durationMs * 0.5).toLong()
        else -> 5000L
    }
    return frameMs.coerceIn(0, durationMs)
}

/** 默认取帧位置（第 5 秒）。 */
const val DEFAULT_FRAME_MS = 5000L

/** 默认取帧位置策略 key。 */
const val DEFAULT_POSITION_KEY = "5s"

/** 用于 listFiles .thumb/ 目录的占位 StorageFile。 */
private class ThumbDirFile(override val path: String) : StorageFile {
    override val name = ".thumb"
    override val isDirectory = true
    override val length = 0L
    override val lastModified = 0L
    override val isHidden = false
}

/** 用于 listFiles .cover/ 目录的占位 StorageFile（音频封面服务端缓存）。 */
private class CoverDirFile(override val path: String) : StorageFile {
    override val name = ".cover"
    override val isDirectory = true
    override val length = 0L
    override val lastModified = 0L
    override val isHidden = false
}
