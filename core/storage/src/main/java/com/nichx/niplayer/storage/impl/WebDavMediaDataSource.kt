package com.nichx.niplayer.storage.impl

import android.media.MediaDataSource
import android.util.Log
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * WebDAV 随机读取数据源（带并行预读缓存）。
 *
 * BUG-13 修复：原 [close] 为空实现，依赖 OkHttpClient 连接池超时回收（默认 5 分钟），
 * 批量生成缩略图时会积累大量空闲连接。改为持有当前正在执行的 [Call] 引用，
 * [close] 时主动 [Call.cancel] 中断正在进行的 HTTP 请求，立即释放连接。
 *
 * BUG-T3 修复：添加 [closed] 标志位，[close] 后 [readAt] 立即返回 -1，
 * 防止 MediaMetadataRetriever 在 close 后再次调用 readAt 发起新的 HTTP 请求。
 * 原实现 close 仅取消当前 call，但 readAt 可能在 close 后被再次调用，
 * 导致已"关闭"的 DataSource 仍发起新请求，连接泄漏。
 *
 * W-M3+W-M4 修复：实现 1MB 预读 buffer，减少 HTTP 请求次数。
 * 原实现每次 readAt 都发一个独立 HTTP 请求，MediaMetadataRetriever 取一帧往往需要
 * 5-20 次 readAt（容器解析+帧定位），远程 WebDAV 单帧生成可能 5-15 秒。
 * 现引入 1MB 预读 buffer：readAt 时若 position 在 buffer 范围内直接从内存复制，
 * 否则发一次较大 Range 请求填充 buffer，后续 readAt 顺序读直接命中 buffer。
 * 顺序读场景下 HTTP 请求次数降低 ~95%，seek 场景下 buffer 自动失效重填。
 *
 * P1-2 增强：并行预读缓存。
 * 当同步 fetch 填充 buffer 后，后台线程并行发起多个 HTTP Range 请求，预取
 * buffer 之后的数据块存入 [prefetchCache]。readAt 时先查 buffer，再查 prefetchCache，
 * 最后 fallback 到同步 fetch。大文件 seek 后的连续读取能命中 prefetch cache，
 * 减少同步 HTTP 请求等待。
 */
class WebDavMediaDataSource(
    private val client: OkHttpClient,
    private val url: HttpUrl,
    private val credentials: String?,
    private val fileSize: Long,
) : MediaDataSource() {

    /** 当前正在执行的同步 HTTP 调用，[close] 时取消。 */
    private val currentCall = AtomicReference<Call?>(null)

    /** close 标志位，[close] 后所有 [readAt] 立即返回 -1（BUG-T3 修复）。 */
    @Volatile
    private var closed = false

    /**
     * W-M9 修复：最后一次 HTTP 错误码（0 表示无错误）。
     *
     * 当 readAt 遇到 401/403/5xx 时记录，供上层 [com.nichx.niplayer.thumbnail.ThumbnailManager]
     * 在 MediaMetadataRetriever 失败后检查，区分"凭证错误"（不应重试）与"视频解析失败"（可重试）。
     *
     * P1-2 注意：后台并行预读的 HTTP 错误**不会**设置此字段，仅同步 fetch 路径设置。
     */
    @Volatile
    var lastHttpErrorCode: Int = 0
        private set

    /**
     * W-M3+W-M4 修复：预读 buffer，减少 HTTP 请求次数。
     *
     * - [bufferBytes]：1MB 字节数组，存放从 [bufferStart] 开始的文件数据
     * - [bufferStart]：buffer 对应的文件起始位置，-1 表示 buffer 无效（初始化或 seek 后）
     * - [bufferValid]：buffer 中有效数据量（字节）
     *
     * readAt 时先检查 position 是否在 [bufferStart, bufferStart+bufferValid) 范围内，
     * 命中则直接从 buffer 复制，避免发 HTTP 请求；未命中则发一次较大 Range 请求填充 buffer。
     * 顺序读场景下（MediaMetadataRetriever 解析容器）命中率接近 100%。
     */
    private val bufferBytes = ByteArray(BUFFER_SIZE)
    @Volatile
    private var bufferStart: Long = -1L
    @Volatile
    private var bufferValid: Int = 0

    /**
     * P1-2：并行预读缓存。
     *
     * 后台线程在同步 fetch 完成后，预取后续数据块存入此缓存。
     * key = data block 的起始文件偏移，value = 该 block 的字节数组。
     * 使用 [ConcurrentSkipListMap] 保证线程安全 + 排序。
     */
    private val prefetchCache = ConcurrentSkipListMap<Long, ByteArray>()

    /**
     * 后台预读线程池。
     *
     * 线程数 = [PREFETCH_CONCURRENCY]，daemon 线程，close 时 shutdownNow。
     */
    private val prefetchService: ExecutorService = Executors.newFixedThreadPool(PREFETCH_CONCURRENCY) { r ->
        Thread(r, "wd-prefetch").apply { isDaemon = true }
    }

    /**
     * 后台预读世代计数器（M-01 修复）。
     *
     * 每次启动新一批预读或发生 seek 时 [incrementAndGet]，
     * 正在运行的后台预读任务在入口处比对世代号，不一致则放弃执行。
     * 避免了原 [AtomicBoolean] cancel flag 在 set(true) 后立即 set(false)
     * 导致旧批次任务看不到取消信号的问题。
     */
    private val prefetchGeneration = AtomicLong(0)

    /**
     * 当前预读基址。
     *
     * 后台预读从此位置开始取后续 N 个 chunk。同步 fetch 完成后更新此值。
     */
    @Volatile
    private var prefetchBase: Long = -1L

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        // BUG-T3 修复：close 后不再发起新请求
        if (closed) return -1
        if (position < 0 || offset < 0 || size < 0 || offset + size > buffer.size) return -1
        if (fileSize > 0 && position >= fileSize) return -1
        val toRead = if (fileSize > 0) {
            minOf(size.toLong(), fileSize - position).toInt()
        } else {
            size
        }
        if (toRead == 0) return 0

        // ---- 1. 检查主 buffer 命中 ----
        val bufEnd = if (bufferStart >= 0) bufferStart + bufferValid else -1L
        if (bufferStart >= 0 && position >= bufferStart && position < bufEnd) {
            val offsetInBuffer = (position - bufferStart).toInt()
            val available = bufferValid - offsetInBuffer
            val copySize = minOf(toRead, available)
            System.arraycopy(bufferBytes, offsetInBuffer, buffer, offset, copySize)
            return copySize
        }

        // ---- 2. 检查 prefetch cache 命中 ----
        val cacheChunkStart = alignChunk(position)
        val cached = prefetchCache[cacheChunkStart]
        if (cached != null) {
            val offsetInChunk = (position - cacheChunkStart).toInt()
            if (offsetInChunk < cached.size) {
                val copySize = minOf(toRead, cached.size - offsetInChunk)
                System.arraycopy(cached, offsetInChunk, buffer, offset, copySize)
                return copySize
            }
        }

        // ---- 3. cache 全 miss：同步 fetch ----
        // 如果 seek 到远距离位置，取消正在进行的预读
        if (prefetchBase >= 0 && kotlin.math.abs(position - prefetchBase) > PREFETCH_CHUNK_SIZE) {
            prefetchGeneration.incrementAndGet()
            prefetchCache.clear()
        }

        val fetchSize = if (fileSize > 0) {
            minOf(BUFFER_SIZE.toLong(), fileSize - position).toInt()
        } else {
            BUFFER_SIZE
        }

        val rangeHeader = if (fileSize > 0) {
            val endPos = position + fetchSize - 1
            "bytes=$position-$endPos"
        } else {
            "bytes=$position-"
        }
        val request = Request.Builder()
            .url(url)
            .header("Range", rangeHeader)
            .apply { credentials?.let { header("Authorization", it) } }
            .build()

        val call = client.newCall(request)
        currentCall.set(call)
        try {
            val response = call.execute()
            when (response.code) {
                206 -> { }
                200 -> {
                    if (position != 0L) {
                        response.close()
                        Log.w(TAG, "readAt: server ignored Range, got 200 at position=$position")
                        return -1
                    }
                }
                else -> {
                    lastHttpErrorCode = response.code
                    Log.w(TAG, "readAt failed: HTTP ${response.code} at position=$position")
                    response.close()
                    return -1
                }
            }
            val body = response.body ?: run {
                response.close()
                return -1
            }
            return body.byteStream().use { stream ->
                var totalRead = 0
                val readBuffer = ByteArray(READ_CHUNK_SIZE)
                while (totalRead < fetchSize) {
                    if (closed) break
                    val n = stream.read(readBuffer, 0, minOf(READ_CHUNK_SIZE, fetchSize - totalRead))
                    if (n < 0) break
                    System.arraycopy(readBuffer, 0, bufferBytes, totalRead, n)
                    totalRead += n
                }
                if (totalRead == 0) {
                    return@use -1
                }
                bufferStart = position
                bufferValid = totalRead

                val copySize = minOf(toRead, totalRead)
                System.arraycopy(bufferBytes, 0, buffer, offset, copySize)

                // ---- 同步 fetch 完成后，启动后台并行预读 ----
                if (!closed && fileSize > 0 && totalRead >= fetchSize) {
                    launchPrefetch(position + fetchSize)
                }

                copySize
            }
        } catch (e: IOException) {
            Log.w(TAG, "readAt IO error at position=$position: ${e.message}")
            return -1
        } finally {
            currentCall.compareAndSet(call, null)
        }
    }

    /**
     * 启动后台并行预读。
     *
     * 从 [basePos] 开始，向 [prefetchService] 提交 [PREFETCH_CONCURRENCY] 个预读任务，
     * 每个任务读取 [PREFETCH_CHUNK_SIZE] 字节的数据块存入 [prefetchCache]。
     *
     * 线程安全：prefetchCache 是 ConcurrentSkipListMap + prefetchCancelFlag 控制取消。
     */
    private fun launchPrefetch(basePos: Long) {
        val gen = prefetchGeneration.incrementAndGet()
        prefetchBase = basePos

        for (i in 0 until PREFETCH_CONCURRENCY) {
            val chunkStart = basePos + i * PREFETCH_CHUNK_SIZE.toLong()
            if (chunkStart >= fileSize) break
            prefetchService.submit { doPrefetchChunk(chunkStart, gen) }
        }
    }

    /**
     * 单个后台预读任务：发送 HTTP Range 请求读取 [chunkStart] 开始的 [PREFETCH_CHUNK_SIZE] 字节，
     * 成功后将数据存入 [prefetchCache]，同时按 [MAX_CACHE_ENTRIES] 淘汰过时条目。
     *
     * 注意：此方法运行在后台线程（prefetchService），不阻塞 readAt 的调用方。
     * 预读失败不会设置 [lastHttpErrorCode]——只有同步 fetch 路径的 HTTP 错误才视为"最后错误"。
     */
    private fun doPrefetchChunk(chunkStart: Long, generation: Long) {
        if (closed || prefetchGeneration.get() != generation) return

        val chunkEnd = minOf(chunkStart + PREFETCH_CHUNK_SIZE - 1, fileSize - 1)
        val rangeHeader = "bytes=$chunkStart-$chunkEnd"
        val request = Request.Builder()
            .url(url)
            .header("Range", rangeHeader)
            .apply { credentials?.let { header("Authorization", it) } }
            .build()

        try {
            val response = client.newCall(request).execute()
            when (response.code) {
                206 -> {
                    val body = response.body
                    if (body != null) {
                        val data = body.bytes()
                        if (data.isNotEmpty() && !closed && prefetchGeneration.get() == generation) {
                            trimCache()
                            prefetchCache[chunkStart] = data
                        }
                    }
                    response.close()
                }
                200 -> {
                    if (chunkStart == 0L) {
                        val body = response.body
                        if (body != null) {
                            // 只取 chunk 范围内的数据
                            val fullData = body.bytes()
                            if (fullData.isNotEmpty() && !closed && prefetchGeneration.get() == generation) {
                                val data = fullData.copyOfRange(0, minOf(fullData.size, PREFETCH_CHUNK_SIZE))
                                trimCache()
                                prefetchCache[chunkStart] = data
                            }
                        }
                    }
                    response.close()
                }
                else -> {
                    response.close()
                    Log.w(TAG, "Prefetch HTTP ${response.code} at $chunkStart")
                }
            }
        } catch (e: IOException) {
            if (!closed) {
                Log.w(TAG, "Prefetch failed at $chunkStart: ${e.message}")
            }
        }
    }

    /**
     * 淘汰 prefetchCache 中的过时条目。
     *
     * 当缓存条目数达到 [MAX_CACHE_ENTRIES] 时，删除比 [prefetchBase] 小的所有条目
     * （已过时的数据）。如果条目数仍超限，从最小 key 开始删除直到降至上限。
     */
    private fun trimCache() {
        if (prefetchCache.size < MAX_CACHE_ENTRIES) return
        if (prefetchBase >= 0) {
            prefetchCache.headMap(prefetchBase).clear()
        }
        while (prefetchCache.size > MAX_CACHE_ENTRIES) {
            prefetchCache.pollFirstEntry() ?: break
        }
    }

    /**
     * 将 [position] 对齐到 [PREFETCH_CHUNK_SIZE] 的整数倍。
     */
    private fun alignChunk(position: Long): Long =
        position / PREFETCH_CHUNK_SIZE * PREFETCH_CHUNK_SIZE

    override fun getSize(): Long = fileSize

    override fun close() {
        if (closed) return
        closed = true
        prefetchGeneration.incrementAndGet()
        prefetchService.shutdownNow()
        currentCall.getAndSet(null)?.cancel()
        bufferStart = -1L
        bufferValid = 0
        prefetchCache.clear()
    }

    companion object {
        private const val TAG = "WebDavMediaDataSource"
        /** W-M3+W-M4 修复：预读 buffer 大小，1MB 平衡内存与命中率。 */
        private const val BUFFER_SIZE = 1024 * 1024
        /** 从响应流读取时的单次 chunk 大小，64KB。 */
        private const val READ_CHUNK_SIZE = 64 * 1024
        /** P1-2 增强：后台预读单次请求大小，512KB。 */
        private const val PREFETCH_CHUNK_SIZE = 512 * 1024
        /** P1-2 增强：后台预读并发线程数。 */
        private const val PREFETCH_CONCURRENCY = 3
        /** P1-2 增强：prefetchCache 最大条目数（6 个 × 512KB = 3MB）。 */
        private const val MAX_CACHE_ENTRIES = 6
    }
}
