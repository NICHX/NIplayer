package com.nichx.niplayer.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Size
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.ThumbnailFramePosition
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.SmbMediaDataSource
import com.nichx.niplayer.storage.impl.WebDavMediaDataSource
import kotlinx.coroutines.CancellationException
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.math.exp
import kotlin.math.pow

/**
 * 视频取帧结果。
 *
 * 比 [ThumbnailResult] 更细：区分「已解码的帧」与「失败原因」，把「落盘」留给调用方，
 * 避免取帧与缓存写盘耦合在一个巨大方法里。
 */
internal sealed interface FrameExtraction {
    /** 解码成功（已按 [ThumbnailManager.MAX_WIDTH] 缩放）。 */
    data class Ok(val bitmap: Bitmap) : FrameExtraction

    /** 临时失败（IO/解码错误），可重试。 */
    data object Failed : FrameExtraction

    /** 永久失败（WebDAV 401/403 等凭证错误），不建议重试。 */
    data object PermanentFailure : FrameExtraction
}

/**
 * 视频缩略图取帧：负责「选数据源 → 选帧位置 → 解码 → HDR 补偿 → 缩放」。
 *
 * 把旧 [ThumbnailManager] 中 5 个 `generateFromUrl/FromDataSource/...At` + 两个
 * `extractAndSaveFrame*` 收敛为「一个数据源策略 + 一次取帧」，不再与落盘纠缠。
 */
internal class VideoFrameExtractor(private val context: Context) {

    /**
     * ffmpeg 取帧失败（解不出任何帧）的「存储源 + 文件路径」集合（LRU，容量有限）。
     *
     * 拖动预览会高频请求取帧；对某些超高码率 / 超长时间的文件，ffmpeg 往往要**读满一次网络读取预算**
     * 才能判定失败（实测 8K、近 30GB 的 MP4 耗时约 4s、读取 65MB）。记住这类文件后，后续预览直接
     * 走 retriever 兜底，避免每次拖动都重复这趟重读取。
     *
     * key 含 storageId：同一路径可能存在于多个存储源，只按 path 记会互相污染
     * （把 A 源的失败结论套到 B 源同一路径的文件上）。
     */
    private val ffmpegUnsupported: MutableSet<String> = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Boolean>?,
                ): Boolean = size > FFMPEG_UNSUPPORTED_CACHE_MAX
            },
        ),
    )

    /** 预览取帧会话：复用数据源 + ffmpeg 解封装上下文（详见 [extractPreview]）。 */
    private class PreviewSession(
        val key: String,
        val source: MediaDataSource,
        val ffmpeg: FfmpegFrameDecoder.Session,
    )

    /** 当前预览会话；[Volatile] 因为取消/关闭可能在其它线程发起。 */
    @Volatile
    private var previewSession: PreviewSession? = null

    private fun unsupportedKey(storageId: Int, path: String): String = "$storageId|$path"

    /** 按用户配置的取帧位置策略 [position] 取帧。 */
    suspend fun extract(
        storage: Storage,
        file: StorageFile,
        position: ThumbnailFramePosition,
        skipDurationCheck: Boolean,
    ): FrameExtraction {
        val startedAt = SystemClock.elapsedRealtime()
        val isRemote = storage.library.mediaType != MediaType.LOCAL_STORAGE

        // 本地 MediaStore 视频优先用「系统缩略图」（MediaProvider 生成/缓存）：
        // 它不占用本进程的 MediaMetadataRetriever 配额。该配额在 MIUI 等机型上是**全局**的
        // （系统日志 `MAX_METADATA_RESOURCE:4`），常被系统/其他应用占满，导致本进程
        // `getFrameAtTime` 直接超时返回 null —— 表现为本地媒体库所有视频都没有缩略图。
        // 系统缩略图不可得（fileId=0 的扩展目录文件、API<29、无缩略图）时再回退 retriever。
        if (storage.library.mediaType == MediaType.LOCAL_STORAGE) {
            loadSystemThumbnail(storage, file)?.let { bitmap ->
                val scaled = scaleToMaxWidth(bitmap, ThumbnailManager.MAX_WIDTH)
                if (scaled !== bitmap) bitmap.recycle()
                Log.d(
                    TAG,
                    "extract ok: ${file.name} src=system/${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                return FrameExtraction.Ok(scaled)
            }
        }

        // 远程视频走 ffmpeg 本地软解（不占用系统取帧资源池），避免多路并发时撞上
        // 「Acquire metadata retriever resource timeout!!」（实测 MP4 曾因此单集耗 24s 且失败）；
        // 实测覆盖 mp4/mkv/ts/rmvb 等常见容器，失败才回退 MediaMetadataRetriever。
        if (isRemote && unsupportedKey(storage.library.id, file.path) !in ffmpegUnsupported) {
            extractRemoteViaFfmpeg(storage, file, position)?.let { bitmap ->
                val scaled = scaleToMaxWidth(bitmap, ThumbnailManager.MAX_WIDTH)
                if (scaled !== bitmap) bitmap.recycle()
                Log.d(TAG, "extract ok: ${file.name} ${SystemClock.elapsedRealtime() - startedAt}ms")
                return FrameExtraction.Ok(scaled)
            }
        }

        val (extraction, dataSource) = withRetriever(storage, file) { retriever, _ ->
            val positions = framePositionsFor(retriever, position, skipDurationCheck)
            readFrame(retriever, positions)
        }

        // 读取预算耗尽：说明这个容器没有取帧器可用的索引，按时间点取帧会退化成全文件扫描，
        // 直接判永久失败（上层记「不重试」）。
        if ((dataSource as? SmbMediaDataSource)?.budgetExceeded == true) {
            Log.w(TAG, "read budget exceeded, skip without retry: ${file.name}")
            val result = FrameExtraction.PermanentFailure
            logExtraction(file, result, dataSource, startedAt)
            return result
        }

        val result = resolvePermanentFailure(extraction, dataSource)
        logExtraction(file, result, dataSource, startedAt)
        return result
    }

    /**
     * 读取系统（MediaProvider）为本地 MediaStore 视频生成的缩略图。
     *
     * 走 [android.content.ContentResolver.loadThumbnail]（API 29+），由系统媒体库进程负责生成/缓存，
     * **不占用本进程的 [MediaMetadataRetriever] 配额**，因此在 MIUI 等把该配额全局化、易被他方占满的
     * 机型上，是本地视频缩略图最可靠的取图方式。
     *
     * 仅对 `content://` 播放地址生效；扩展目录（fileId=0，`file://`）或系统取图失败时返回 null，
     * 由调用方回退到 [MediaMetadataRetriever]。
     */
    private suspend fun loadSystemThumbnail(storage: Storage, file: StorageFile): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val url = try {
            storage.createPlayUrl(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "loadSystemThumbnail createPlayUrl failed: ${e.message}")
            null
        } ?: return null
        if (!url.startsWith("content", ignoreCase = true)) return null
        return try {
            context.contentResolver.loadThumbnail(
                Uri.parse(url),
                Size(ThumbnailManager.MAX_WIDTH, ThumbnailManager.MAX_WIDTH),
                null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "loadSystemThumbnail failed: ${file.name} ${e.message}")
            null
        }
    }

    private suspend fun openMediaDataSource(storage: Storage, file: StorageFile): MediaDataSource? = try {
        storage.openMediaDataSource(file)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "openMediaDataSource failed: ${e.message}")
        null
    }

    /**
     * 远程视频取帧：统一走 **ffmpeg 本地软解**（不占用系统取帧资源池）。
     *
     * libavformat 走索引 seek + 软解少量帧，读取量小（实测 4K MKV/MP4 约 6~8MB），远小于
     * MediaExtractor 在同一文件上的 46~65MB；覆盖 matroska、mov(mp4)、mpegts、avi、flv、asf、rmvb 等。
     * 不可用（`.so` 未打包）或解码失败时返回 null，由调用方回退 [MediaMetadataRetriever]。
     */
    private suspend fun extractRemoteViaFfmpeg(
        storage: Storage,
        file: StorageFile,
        position: ThumbnailFramePosition,
    ): Bitmap? {
        if (!FfmpegFrameDecoder.isAvailable) return null
        val source = openMediaDataSource(storage, file) ?: return null
        val (startMs, fraction) = decodePosition(position)
        return try {
            decodeWithFfmpeg(source, startMs, fraction).frame?.bitmap
        } finally {
            closeQuietly(source)
        }
    }

    /** 取帧位置 → ffmpeg 入参（startMs 或 fraction，二选一）。 */
    private fun decodePosition(position: ThumbnailFramePosition): Pair<Long, Double> = when (position) {
        ThumbnailFramePosition.POS_10_PCT -> -1L to 0.1
        ThumbnailFramePosition.POS_50_PCT -> -1L to 0.5
        ThumbnailFramePosition.POS_5S -> DEFAULT_FRAME_MS to -1.0
    }

    /**
     * ffmpeg 软解一帧（一次性：内部再 open/probe 一次）。
     *
     * 需要连续多次取帧（拖动预览）时改用 [extractPreview] —— 那里的常驻会话可复用解封装上下文。
     *
     * @return 解出的帧（已做 HDR 色调映射）与失败原因；[FfmpegDecodeResult.isUnsupported]
     *   为真才代表「这个文件 ffmpeg 确实吃不下」，可记入不支持名单。
     */
    private fun decodeWithFfmpeg(
        source: MediaDataSource,
        startMs: Long,
        fraction: Double,
        pickNearest: Boolean = false,
    ): FfmpegDecodeResult {
        val result = FfmpegFrameDecoder.decodeFirstFrame(
            source,
            ThumbnailManager.MAX_WIDTH,
            startMs,
            fraction,
            pickNearest,
        )
        val frame = result.frame ?: return result
        // ffmpeg 直解出的是原始码值、**没有系统 tone map**，HDR 帧必须自行色调映射，
        // 否则 HDR 片源的预览/缩略图会发暗或过曝（retriever 路径同样做了补偿）。
        if (frame.hdrKind == HDR_KIND_NONE) return result
        val mapped = applyHdrToneMapCompensation(frame.bitmap, frame.hdrKind)
        if (mapped !== frame.bitmap) frame.bitmap.recycle()
        return result.copy(frame = frame.copy(bitmap = mapped))
    }

    private fun closeQuietly(source: MediaDataSource) {
        try {
            source.close()
        } catch (_: Exception) {
        }
    }

    /** 永久失败识别：仅 WebDAV MediaDataSource 能给出 HTTP 错误码（401/403 不应重试）。 */
    private fun resolvePermanentFailure(
        extraction: FrameExtraction,
        dataSource: MediaDataSource?,
    ): FrameExtraction = if (extraction is FrameExtraction.Failed &&
        dataSource is WebDavMediaDataSource &&
        dataSource.lastHttpErrorCode in setOf(401, 403)
    ) {
        Log.w(TAG, "WebDAV permanent failure (HTTP ${dataSource.lastHttpErrorCode})")
        FrameExtraction.PermanentFailure
    } else {
        extraction
    }

    /**
     * 在精确位置 [positionMs] 取帧（退出播放覆盖缩略图；拖动预览用 [extractPreview]）。
     *
     * [exactFrame] = true：要"精确到帧"——
     * - 远程源走 ffmpeg 的 nearest 模式（取第一个不早于目标时刻的帧）；
     * - 本地源用 `OPTION_CLOSEST` 就近解码，且不掺 10%/50% 兜底位置。
     * 若走缩略图那套（ffmpeg 取窗口内最亮帧 / retriever `CLOSEST_SYNC` 只到关键帧），
     * 画面会与目标时间对不上（长 GOP 甚至差数秒、场景切换处会跳到下一场景）。
     *
     * [exactFrame] = false（库缩略图）：保持"跳黑帧/关键帧"取向，画面稳定好看即可。
     */
    suspend fun extractAt(
        storage: Storage,
        file: StorageFile,
        positionMs: Long,
        skipDurationCheck: Boolean,
        exactFrame: Boolean = false,
    ): FrameExtraction {
        val startedAt = SystemClock.elapsedRealtime()
        val isRemote = storage.library.mediaType != MediaType.LOCAL_STORAGE
        val key = unsupportedKey(storage.library.id, file.path)
        if (isRemote && FfmpegFrameDecoder.isAvailable && key !in ffmpegUnsupported) {
            val source = openMediaDataSource(storage, file)
            if (source != null) {
                val decode = try {
                    decodeWithFfmpeg(source, startMs = positionMs, fraction = -1.0, pickNearest = exactFrame)
                } finally {
                    closeQuietly(source)
                }
                val frame = decode.frame
                if (frame != null) {
                    Log.d(
                        TAG,
                        "extractAt ok(ffmpeg${if (exactFrame) ",exact" else ""}): " +
                            "${file.name} ${SystemClock.elapsedRealtime() - startedAt}ms",
                    )
                    return FrameExtraction.Ok(frame.bitmap)
                }
                // 只有「确实吃不下这个流」才记入名单：取消/网络抖动不在此列，
                // 否则一次抖动就会让该文件在本次进程内永久退化成关键帧兜底。
                if (decode.isUnsupported) {
                    ffmpegUnsupported.add(key)
                    Log.w(TAG, "ffmpeg unsupported, fall back to retriever: ${file.name}")
                }
            }
        }
        return extractViaRetriever(storage, file, positionMs, exactFrame, skipDurationCheck, startedAt)
    }

    /**
     * 拖动进度条 / 横滑快进的实时预览取帧。
     *
     * 与 [extractAt] 的关键差异：远程源使用**常驻取帧会话**（[previewSession]）——
     * 整个拖动过程只 open/probe 一次，之后每帧只做 seek + decode。一次性取帧每帧都要重新
     * `avformat_open_input` + `find_stream_info`（远程还要重建连接、重新读索引），
     * 单帧成本远高于 seek 本身，拖动时预览会明显跟不上手指。
     *
     * 会话按「存储源 + 路径」复用；切换文件或调用 [closePreviewSession] 时释放。
     */
    suspend fun extractPreview(storage: Storage, file: StorageFile, positionMs: Long): FrameExtraction {
        val startedAt = SystemClock.elapsedRealtime()
        val isRemote = storage.library.mediaType != MediaType.LOCAL_STORAGE
        val key = unsupportedKey(storage.library.id, file.path)
        if (isRemote && FfmpegFrameDecoder.isAvailable && key !in ffmpegUnsupported) {
            val session = ensurePreviewSession(storage, file, key)
            if (session != null) {
                // 会话跨帧复用同一数据源：读取预算按「单帧」语义重置，否则长时间拖动
                // 会在累计读到上限后中途失效（预览突然消失）
                (session.source as? SmbMediaDataSource)?.resetReadBudget()
                val decode = session.ffmpeg.decode(positionMs, pickNearest = true)
                val frame = decode.frame
                if (frame != null) {
                    // 会话解出的帧同样要过 HDR 补偿
                    val mapped = if (frame.hdrKind == HDR_KIND_NONE) {
                        frame.bitmap
                    } else {
                        applyHdrToneMapCompensation(frame.bitmap, frame.hdrKind)
                            .also { if (it !== frame.bitmap) frame.bitmap.recycle() }
                    }
                    Log.d(TAG, "extractPreview ok(session): ${file.name} ${SystemClock.elapsedRealtime() - startedAt}ms")
                    return FrameExtraction.Ok(mapped)
                }
                // 用户已松手：本次结果作废，保留会话（下次拖动接着用）
                if (decode.isCancelled) return FrameExtraction.Failed
                // 其它失败：关掉会话（下次重新 open 一次），本次退回 retriever
                closePreviewSessionInternal()
                if (decode.isUnsupported) {
                    ffmpegUnsupported.add(key)
                    Log.w(TAG, "preview session unsupported, fall back to retriever: ${file.name}")
                }
            }
        }
        return extractViaRetriever(storage, file, positionMs, exactFrame = true, skipDurationCheck = false, startedAt = startedAt)
    }

    /**
     * 打断正在进行的预览取帧（非阻塞，可从主线程调用）。
     *
     * 正在解的那一帧会尽快收手，把解码资源让给真实播放；不影响会话本身。
     */
    fun cancelPreviewFrame() {
        previewSession?.ffmpeg?.cancel()
    }

    /** 关闭预览会话并释放其持有的数据源与解码器（幂等）。 */
    fun closePreviewSession() {
        closePreviewSessionInternal()
    }

    private fun closePreviewSessionInternal() {
        val session = previewSession ?: return
        previewSession = null
        try {
            // close 会等 native 侧解码锁；先 cancel 保证它尽快返回
            session.ffmpeg.cancel()
            session.ffmpeg.close()
        } catch (_: Exception) {
        }
        closeQuietly(session.source)
    }

    /**
     * 取得（必要时建立）预览会话。
     *
     * 建立失败时返回 null：容器/编码器不识别的情况记入不支持名单，后续直接走 retriever。
     */
    private suspend fun ensurePreviewSession(
        storage: Storage,
        file: StorageFile,
        key: String,
    ): PreviewSession? {
        previewSession?.let { if (it.key == key) return it }
        closePreviewSessionInternal()
        val source = openMediaDataSource(storage, file) ?: return null
        val opened = FfmpegFrameDecoder.openSession(source, ThumbnailManager.MAX_WIDTH)
        val session = opened.session
        if (session == null) {
            closeQuietly(source)
            if (opened.error == FfmpegFrameDecoder.ERR_UNSUPPORTED) {
                ffmpegUnsupported.add(key)
                Log.w(TAG, "ffmpeg session unsupported, fall back to retriever: ${file.name}")
            }
            return null
        }
        Log.d(TAG, "preview session opened: ${file.name}")
        return PreviewSession(key, source, session).also { previewSession = it }
    }

    /**
     * retriever 兜底取帧：只有本地才用「就近解码」（精确、且本地读取便宜）；
     * 远程兜底一律只用关键帧 —— 就近解码要自关键帧向前解码到目标帧，对高码率远程文件会
     * 读穿单次读取预算（实测 8K 远程 MP4 直接取不到帧），用关键帧才能稳定出图。
     */
    private suspend fun extractViaRetriever(
        storage: Storage,
        file: StorageFile,
        positionMs: Long,
        exactFrame: Boolean,
        skipDurationCheck: Boolean,
        startedAt: Long,
    ): FrameExtraction {
        val isRemote = storage.library.mediaType != MediaType.LOCAL_STORAGE
        val localExact = exactFrame && !isRemote
        val option = if (localExact) {
            MediaMetadataRetriever.OPTION_CLOSEST
        } else {
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        }
        val (extraction, dataSource) = withRetriever(storage, file) { retriever, _ ->
            val positions = if (exactFrame) {
                // 预览只认目标时间点本身：不掺缩略图用的 10%/50% 兜底位置
                listOf(positionMs)
            } else {
                framePositionsAt(retriever, positionMs, skipDurationCheck)
            }
            readFrame(retriever, positions, option)
        }
        logExtraction(file, extraction, dataSource, startedAt)
        return extraction
    }

    /**
     * 记录一次取帧的耗时、数据源类型与结果。
     *
     * 失败此前完全无日志，无法区分「取帧失败」与「远程读取太慢」；此处补齐，
     * 成功用 [Log.d]、失败用 [Log.w]，便于用 `adb logcat -s VideoFrameExtractor` 定位。
     */
    private fun logExtraction(
        file: StorageFile,
        result: FrameExtraction,
        dataSource: MediaDataSource?,
        startedAt: Long,
    ) {
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        val source = dataSource?.javaClass?.simpleName ?: "url"
        when (result) {
            is FrameExtraction.Ok -> Log.d(TAG, "extract ok: ${file.name} src=$source ${elapsedMs}ms")
            FrameExtraction.Failed -> Log.w(TAG, "extract failed: ${file.name} src=$source ${elapsedMs}ms")
            FrameExtraction.PermanentFailure -> Log.w(TAG, "extract permanent-failure: ${file.name} src=$source ${elapsedMs}ms")
        }
    }

    /**
     * 建立合适的数据源并执行 [block]，返回 (取帧结果, 实际使用的 MediaDataSource 或 null)。
     *
     * 数据源选择（与旧实现一致）：
     * - file/content URL：`setDataSource(context, uri)`
     * - http URL：非自签证书时优先 URL+Headers（系统 HTTP 栈更稳），失败回退 MediaDataSource；
     *   自签证书（`trustAllCertificates`）直接走 MediaDataSource（URL+Headers 必失败）
     * - 其它（SMB 等）：MediaDataSource
     */
    private suspend fun withRetriever(
        storage: Storage,
        file: StorageFile,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> = RetrieverGate.withPermit {
        val url = storage.createPlayUrl(file)
        when {
            url != null && (url.startsWith("file") || url.startsWith("content")) ->
                useUrlUri(url, block)

            url != null && url.startsWith("http", ignoreCase = true) ->
                useHttp(storage, file, url, block)

            else ->
                useDataSource(storage, file, block)
        }
    }

    private fun useUrlUri(
        url: String,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(url))
            block(retriever, null) to null
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource(uri) failed: ${e.message}")
            FrameExtraction.Failed to null
        } finally {
            releaseQuietly(retriever)
        }
    }

    private suspend fun useHttp(
        storage: Storage,
        file: StorageFile,
        url: String,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        // W-M6：自签 HTTPS 下 URL+Headers 走系统栈必失败，跳过直接走 MediaDataSource
        if (!storage.trustAllCertificates) {
            val headers = storage.getPlayHeaders()
            if (headers.isNotEmpty()) {
                val viaHeaders = useUrlHeaders(url, headers, block)
                if (viaHeaders.first is FrameExtraction.Ok) return viaHeaders
            }
        }
        val dataSource = storage.openMediaDataSource(file)
        return if (dataSource != null) {
            useDataSourceInstance(dataSource, block)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(url))
                block(retriever, null) to null
            } catch (e: Exception) {
                Log.w(TAG, "fallback setDataSource(uri) failed: ${e.message}")
                FrameExtraction.Failed to null
            } finally {
                releaseQuietly(retriever)
            }
        }
    }

    private fun useUrlHeaders(
        url: String,
        headers: Map<String, String>,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers)
            block(retriever, null) to null
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource(url, headers) failed: ${e.message}")
            FrameExtraction.Failed to null
        } finally {
            releaseQuietly(retriever)
        }
    }

    private suspend fun useDataSource(
        storage: Storage,
        file: StorageFile,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val dataSource = storage.openMediaDataSource(file)
        if (dataSource == null) {
            Log.w(TAG, "openMediaDataSource returned null for ${file.path}")
            return FrameExtraction.Failed to null
        }
        return useDataSourceInstance(dataSource, block)
    }

    private fun useDataSourceInstance(
        dataSource: MediaDataSource,
        block: (MediaMetadataRetriever, MediaDataSource?) -> FrameExtraction,
    ): Pair<FrameExtraction, MediaDataSource?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            block(retriever, dataSource) to dataSource
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource(MediaDataSource) failed: ${e.message}")
            FrameExtraction.Failed to dataSource
        } finally {
            releaseQuietly(retriever)
            try {
                dataSource.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseQuietly(retriever: MediaMetadataRetriever) {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "VideoFrameExtractor"

        /** `ffmpeg 不支持` 的文件路径缓存容量（LRU）。 */
        const val FFMPEG_UNSUPPORTED_CACHE_MAX = 64
    }
}

// ---------- 位置选择与解码（顶层纯函数，便于复用与测试） ----------

/**
 * 计算按 [position] 的候选取帧位置（毫秒）。
 *
 * - 短视频（durationMs < [ThumbnailManager.MIN_DURATION_MS]）且未跳过检查 → 取第一个关键帧
 * - 否则用户配置位置优先，再回退 10% / 50%
 * - duration 不可用 → 退化为默认位置
 */
internal fun framePositionsFor(
    retriever: MediaMetadataRetriever,
    position: ThumbnailFramePosition,
    skipDurationCheck: Boolean,
): List<Long> {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    val isShort = !skipDurationCheck && durationMs != null && durationMs < ThumbnailManager.MIN_DURATION_MS
    val positions = mutableListOf<Long>()
    if (isShort) {
        positions.add(0L)
        return positions
    }
    positions.add(
        if (durationMs != null) calculateFramePositionMs(durationMs, position) else DEFAULT_FRAME_MS
    )
    if (durationMs != null) {
        val tenPct = (durationMs * 0.1).toLong()
        val fiftyPct = (durationMs * 0.5).toLong()
        if (tenPct !in positions) positions.add(tenPct)
        if (fiftyPct !in positions) positions.add(fiftyPct)
    }
    return positions
}

/** 计算显式 [positionMs] 的候选取帧位置（毫秒），短视频改为第一个关键帧。 */
internal fun framePositionsAt(
    retriever: MediaMetadataRetriever,
    positionMs: Long,
    skipDurationCheck: Boolean,
): List<Long> {
    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    val isShort = !skipDurationCheck && durationMs != null && durationMs < ThumbnailManager.MIN_DURATION_MS
    if (isShort) return listOf(0L)
    return mutableListOf(positionMs).also {
        if (durationMs != null) {
            val tenPct = (durationMs * 0.1).toLong()
            val fiftyPct = (durationMs * 0.5).toLong()
            if (tenPct !in it) it.add(tenPct)
            if (fiftyPct !in it) it.add(fiftyPct)
        }
    }
}

/** 依次尝试 [positions]，返回第一个成功解码并完成 HDR 补偿、缩放的帧；全部失败返回 [FrameExtraction.Failed]。 */
internal fun readFrame(
    retriever: MediaMetadataRetriever,
    positions: List<Long>,
    option: Int = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
): FrameExtraction {
    val isHdr = isHdrVideo(retriever)
    var frame: Bitmap? = null
    for (posMs in positions) {
        frame = retriever.frameAt(posMs * 1000L, ThumbnailManager.MAX_WIDTH, option)
        if (frame != null) break
    }
    if (frame == null) return FrameExtraction.Failed
    val compensated = applyHdrCompensationIfNeeded(frame, isHdr)
    val scaled = scaleToMaxWidth(compensated, ThumbnailManager.MAX_WIDTH)
    // 缩放到新实例后回收补偿/原始帧，避免内存累积
    if (scaled !== compensated) compensated.recycle()
    return FrameExtraction.Ok(scaled)
}

/** 等比缩放到 [maxWidth]，宽度不超时不复制、直接返回 [src]（不回收 [src]，中间产物由调用方回收）。 */
internal fun scaleToMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
    if (src.width <= maxWidth) return src
    val ratio = maxWidth.toFloat() / src.width
    val newHeight = (src.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, maxWidth, newHeight, true)
}

/** 计算 [inSampleSize]：2 的幂倍，使原图至少一边不超 [maxDimension]。 */
internal fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
    var sampleSize = 1
    while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * 按接近 [maxWidth] 的目标尺寸解码指定时间点的帧。
 *
 * API 27+ 且视频无旋转（0/180）时优先 [MediaMetadataRetriever.getScaledFrameAtTime] 直接降采样解码，
 * 避免先解出全分辨率帧再缩小；其余情况回退 [MediaMetadataRetriever.getFrameAtTime]。
 *
 * @param option 取帧策略：缩略图用 `OPTION_CLOSEST_SYNC`（只到关键帧，快）；拖动预览用
 * `OPTION_CLOSEST`（就近解码，准）。
 */
internal fun MediaMetadataRetriever.frameAt(
    positionUs: Long,
    maxWidth: Int,
    option: Int = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 0 || rotation == 180) {
            val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0) {
                val ratio = if (width > maxWidth) maxWidth.toFloat() / width else 1f
                val dstWidth = (width * ratio).toInt().coerceAtLeast(1)
                val dstHeight = (height * ratio).toInt().coerceAtLeast(1)
                try {
                    return getScaledFrameAtTime(
                        positionUs,
                        option,
                        dstWidth,
                        dstHeight,
                    )
                } catch (_: Exception) {
                    // 回退到 getFrameAtTime
                }
            }
        }
    }
    return try {
        getFrameAtTime(positionUs, option)
    } catch (_: Exception) {
        null
    }
}

// ---------- HDR 色调映射 ----------

/** 检测视频是否为 HDR 编码（PQ/HDR10/Dolby Vision 或 HLG）。 */
internal fun isHdrVideo(retriever: MediaMetadataRetriever): Boolean {
    val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
        ?: return false
    return transfer == ThumbnailManager.COLOR_TRANSFER_SMPTE_ST_2084 || transfer == ThumbnailManager.COLOR_TRANSFER_HLG
}

/**
 * HDR → SDR 色调映射（软件，逐像素）。
 *
 * 旧实现是「在 PQ **编码值**上乘 3.0 再截断」—— 编码值 0.333（约 33nits）以上全部爆到 255，
 * 所以 HDR 画面严重过曝。现按物理链路做：
 *
 *   1. transfer 反变换（PQ 的 EOTF / HLG 的 inverse OETF）→ 线性光，归一化到 1.0 = 峰值亮度；
 *   2. 换算成「相对 SDR 漫反射白（100nits）」的倍数；
 *   3. 扩展 Reinhard 压缩：`L·(1 + L/W²)/(1 + L)`，把白点 [HDR_WHITE_POINT]（≈800nits）映射到 1.0，
 *      以下不截断、以上缓慢逼近 1 —— 因此不会再出现大面积过曝；
 *   4. gamma 编码回 8bit。
 *
 * 逐通道处理（会轻微降低高光饱和度），对预览/缩略图足够；不做 BT.2020→BT.709 色域映射。
 *
 * @param hdrKind 见 [HDR_KIND_PQ] / [HDR_KIND_HLG]
 */
internal fun applyHdrToneMapCompensation(src: Bitmap, hdrKind: Int = HDR_KIND_PQ): Bitmap {
    val width = src.width
    val height = src.height
    if (width <= 0 || height <= 0) return src
    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)
    val peakNits = if (hdrKind == HDR_KIND_HLG) HLG_PEAK_NITS else PQ_PEAK_NITS
    for (i in pixels.indices) {
        val p = pixels[i]
        val a = p ushr 24 and 0xFF
        if (a == 0) continue
        val r = toneMapChannel((p ushr 16 and 0xFF) / 255f, hdrKind, peakNits)
        val g = toneMapChannel((p ushr 8 and 0xFF) / 255f, hdrKind, peakNits)
        val b = toneMapChannel((p and 0xFF) / 255f, hdrKind, peakNits)
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    val result = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

/** 单个通道：HDR 编码值 → 色调映射后的 8bit。 */
private fun toneMapChannel(code: Float, hdrKind: Int, peakNits: Float): Int {
    var linear = if (hdrKind == HDR_KIND_HLG) hlgInverseOetf(code) else pqEotf(code)
    if (hdrKind == HDR_KIND_HLG) {
        // HLG 的 OOTF：场景线性 → 显示线性（系统 gamma ≈1.2）
        linear = linear.pow(HLG_SYSTEM_GAMMA)
    }
    val relative = linear * peakNits / SDR_WHITE_NITS
    val mapped = relative * (1f + relative / (HDR_WHITE_POINT * HDR_WHITE_POINT)) / (1f + relative)
    val value = mapped.coerceIn(0f, 1f).pow(1f / DISPLAY_GAMMA)
    return (value * 255f + 0.5f).toInt().coerceIn(0, 255)
}

/** SMPTE ST 2084 (PQ) EOTF：编码值 → 线性光（0..1 对应 0..[PQ_PEAK_NITS]）。 */
private fun pqEotf(code: Float): Float {
    val v = code.coerceIn(0f, 1f).pow(1f / PQ_M2)
    val numerator = (v - PQ_C1).coerceAtLeast(0f)
    val denominator = PQ_C2 - PQ_C3 * v
    if (denominator <= 0f) return 0f
    return (numerator / denominator).pow(1f / PQ_M1)
}

/** ARIB STD-B67 (HLG) inverse OETF：编码值 → 场景线性。 */
private fun hlgInverseOetf(code: Float): Float {
    val v = code.coerceIn(0f, 1f)
    return if (v <= 0.5f) {
        v * v / 3f
    } else {
        (exp(((v - HLG_C) / HLG_A).toDouble()).toFloat() + HLG_B) / 12f
    }
}

private const val PQ_M1 = 2610f / 16384f
private const val PQ_M2 = 2523f / 4096f * 128f
private const val PQ_C1 = 3424f / 4096f
private const val PQ_C2 = 2413f / 4096f * 32f
private const val PQ_C3 = 2392f / 4096f * 32f
private const val HLG_A = 0.17883277f
private const val HLG_B = 0.28466892f
private const val HLG_C = 0.55991073f
private const val HLG_SYSTEM_GAMMA = 1.2f
private const val PQ_PEAK_NITS = 10000f
private const val HLG_PEAK_NITS = 1000f
private const val SDR_WHITE_NITS = 100f

/** 色调映射白点（相对 SDR 白）：≈800nits 映射到显示白，以下不截断。 */
private const val HDR_WHITE_POINT = 8f

/** 显示 gamma（BT.1886 近似）。 */
private const val DISPLAY_GAMMA = 2.2f

/** 按需做 HDR 补偿：API 34+ 系统已 tone map，直接返回；否则对 HDR 帧补偿。 */
internal fun applyHdrCompensationIfNeeded(src: Bitmap, isHdr: Boolean): Bitmap {
    if (!isHdr) return src
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return src
    val mapped = applyHdrToneMapCompensation(src)
    if (mapped !== src) src.recycle()
    return mapped
}
