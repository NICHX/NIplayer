package com.nichx.niplayer.storage.impl

import android.util.Log
import org.codelibs.jcifs.smb.CIFSContext
import org.codelibs.jcifs.smb.SmbRandomAccess
import org.codelibs.jcifs.smb.impl.SmbFile
import java.io.InputStream
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * SMB 多线程并行预读 [InputStream]。
 *
 * 替代 smbj [com.hierynomus.smbj.share.FileInputStream] 的单线程同步读取。
 *
 * 为什么需要并行预读：
 * - smbj FileInputStream 严格同步：发 SMB2 READ → 等响应 → 再发下一个
 * - 千兆网单请求同步吞吐约 7-8 MB/s（60 Mbps），无法满足高码率视频（60+ Mbps）
 * - 多线程并行发 N 个 READ 请求（SMB2 Multi-Credit），吞吐可提升 N 倍
 *
 * 架构：
 * - N 个预读线程，通过 [SmbRandomAccess] + [SmbRandomAccess.seek] 高效定位到目标 offset
 * - 每个线程独立持有自己的 [SmbRandomAccess] 实例，[seek] 是 SMB 协议级操作，
 *   不产生数据下载（区别于 [InputStream.skip] 的读取-丢弃行为）
 * - [nextReadChunk] 原子分配 chunk 序号，避免重复读取
 * - [chunks] 按 seq 排序存储，消费线程按顺序读取
 * - [skip] 是 O(1)（只更新消费位置和预读起点，清空缓冲）
 *
 * 与 v1（smbj）版本的差异：
 * - codelibs/jcifs 的 SmbFileInputStream 不支持服务端 offset 读取
 * - 改用 SmbFile.openRandomAccess("r") + seek(offset) 实现高效随机定位
 * - 每个预读通道在读取前通过 seek() 定位到目标 offset（不传输数据）
 *
 * @param context 已认证的 CIFSContext
 * @param url 文件的 smb:// 完整 URL
 * @param fileSize 文件大小（<=0 时按无限流处理，读到 EOF 为止）
 * @param parallelism 并行预读线程数
 * @param chunkSize 单次读取块大小
 * @param maxBufferedChunks 最大缓冲 chunk 数（总缓冲 = chunkSize × maxBufferedChunks）
 */
class SmbParallelInputStream(
    private val context: CIFSContext,
    private val url: String,
    private val fileSize: Long,
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    private val maxBufferedChunks: Int = DEFAULT_MAX_BUFFERED_CHUNKS,
) : InputStream() {

    companion object {
        private const val TAG = "SmbParallelInputStream"

        const val DEFAULT_PARALLELISM = 4
        const val DEFAULT_CHUNK_SIZE = 1024 * 1024
        const val DEFAULT_MAX_BUFFERED_CHUNKS = 16
    }

    private class PrefetchChannel(val raf: SmbRandomAccess)

    private val lock = ReentrantLock()
    private val bufferNotEmpty: Condition = lock.newCondition()
    private val bufferNotFull: Condition = lock.newCondition()

    private val nextReadChunk = AtomicLong(0)
    private val chunks = ConcurrentSkipListMap<Long, ByteArray>()
    private var consumeChunkSeq = 0L
    private var consumeOffsetInChunk = 0

    @Volatile
    private var closed = false

    @Volatile
    private var readError: Throwable? = null

    private val filesLock = Object()
    private var channels: MutableList<PrefetchChannel>? = null
    private var threads: MutableList<Thread>? = null

    private val runningPrefetchCount = AtomicInteger(0)

    private fun ensureStarted() {
        synchronized(filesLock) {
            if (channels != null) return
            if (closed) return
            val opened = ArrayList<PrefetchChannel>(parallelism)
            try {
                repeat(parallelism) {
                    val sf = SmbFile(url, context)
                    val raf = sf.openRandomAccess("r")
                    opened.add(PrefetchChannel(raf = raf))
                }
                channels = opened
                runningPrefetchCount.set(parallelism)
                val started = ArrayList<Thread>(parallelism)
                for (i in 0 until parallelism) {
                    val t = Thread({ doPrefetch(opened[i]) }, "SmbPrefetch-$i").apply {
                        isDaemon = true
                        start()
                    }
                    started.add(t)
                }
                threads = started
            } catch (e: Throwable) {
                opened.forEach { runCatching { it.raf.close() } }
                throw e
            }
        }
    }

    private fun doPrefetch(channel: PrefetchChannel) {
        try {
            while (!closed) {
                val chunkSeq = nextReadChunk.getAndIncrement()

                if (fileSize > 0) {
                    val offset = chunkSeq * chunkSize.toLong()
                    if (offset >= fileSize) return
                }

                lock.lock()
                try {
                    while (chunks.size >= maxBufferedChunks && !closed) {
                        bufferNotFull.await()
                    }
                    if (closed) return
                } finally {
                    lock.unlock()
                }

                val offset = chunkSeq * chunkSize.toLong()
                val length = if (fileSize > 0) {
                    val remaining = fileSize - offset
                    when {
                        remaining <= 0 -> return
                        remaining >= chunkSize.toLong() -> chunkSize
                        else -> remaining.toInt()
                    }
                } else {
                    chunkSize
                }
                val buffer = ByteArray(length)

                val readStartMs = System.currentTimeMillis()

                var totalRead = 0

                // 通过 seek() 定位到目标 offset，seek 是 SMB 协议级操作，不传输数据
                synchronized(channel) {
                    channel.raf.seek(offset)
                    while (totalRead < length) {
                        val n = channel.raf.read(buffer, totalRead, length - totalRead)
                        if (n <= 0) break
                        totalRead += n
                    }
                }

                val readCostMs = System.currentTimeMillis() - readStartMs

                if (totalRead <= 0) {
                    lock.lock()
                    try {
                        bufferNotEmpty.signalAll()
                    } finally {
                        lock.unlock()
                    }
                    return
                }

                val data = if (totalRead == length) buffer else buffer.copyOf(totalRead)
                lock.lock()
                try {
                    chunks[chunkSeq] = data
                    bufferNotEmpty.signal()
                } finally {
                    lock.unlock()
                }

                if (readCostMs > 2000) {
                    Log.w(TAG, "slow read: ${readCostMs}ms for ${length} bytes at offset $offset")
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            val msg = e.message
            when {
                msg != null && (msg.contains("has been closed") ||
                    msg.contains("STATUS_FILE_CLOSED") ||
                    msg.contains("STATUS_CONNECTION_DISCONNECTED")) ->
                    Log.w(TAG, "prefetch stopped (share closed): $msg")
                msg != null && (msg.contains("InterruptedException") || msg.contains("interrupted")) ->
                    Log.d(TAG, "prefetch interrupted (stream closed): $msg")
                else ->
                    Log.w(TAG, "prefetch error: $msg")
            }
            readError = e
            lock.lock()
            try {
                bufferNotEmpty.signalAll()
            } finally {
                lock.unlock()
            }
        } finally {
            lock.lock()
            try {
                runningPrefetchCount.decrementAndGet()
                bufferNotEmpty.signalAll()
            } finally {
                lock.unlock()
            }
        }
    }

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) == -1) -1 else b[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        ensureStarted()
        lock.lock()
        try {
            while (chunks[consumeChunkSeq] == null && readError == null && !closed) {
                if (fileSize > 0) {
                    val consumed = consumeChunkSeq * chunkSize.toLong() + consumeOffsetInChunk
                    if (consumed >= fileSize) return -1
                }
                if (runningPrefetchCount.get() <= 0) return -1
                try {
                    bufferNotEmpty.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.IOException("SMB read interrupted")
                }
            }

            readError?.let { throw java.io.IOException("SMB parallel read failed", it) }

            val chunk = chunks[consumeChunkSeq] ?: return -1
            val available = chunk.size - consumeOffsetInChunk
            val toRead = minOf(len, available)
            System.arraycopy(chunk, consumeOffsetInChunk, b, off, toRead)

            consumeOffsetInChunk += toRead
            if (consumeOffsetInChunk >= chunk.size) {
                chunks.remove(consumeChunkSeq)
                consumeChunkSeq++
                consumeOffsetInChunk = 0
                bufferNotFull.signal()
            }
            return toRead
        } finally {
            lock.unlock()
        }
    }

    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        ensureStarted()
        lock.lock()
        try {
            val currentOffset = consumeChunkSeq * chunkSize.toLong() + consumeOffsetInChunk
            val actualN = if (fileSize > 0) {
                val remaining = fileSize - currentOffset
                if (remaining <= 0) return 0L
                minOf(n, remaining)
            } else {
                n
            }
            val targetOffset = currentOffset + actualN

            consumeChunkSeq = targetOffset / chunkSize
            consumeOffsetInChunk = (targetOffset % chunkSize).toInt()

            chunks.clear()
            nextReadChunk.set(consumeChunkSeq)
            bufferNotFull.signalAll()
            return actualN
        } finally {
            lock.unlock()
        }
    }

    override fun available(): Int {
        ensureStarted()
        lock.lock()
        try {
            var total = 0
            var seq = consumeChunkSeq
            while (chunks[seq] != null) {
                val chunk = chunks[seq]!!
                total += if (seq == consumeChunkSeq) {
                    chunk.size - consumeOffsetInChunk
                } else {
                    chunk.size
                }
                seq++
            }
            return total
        } finally {
            lock.unlock()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        lock.lock()
        try {
            bufferNotEmpty.signalAll()
            bufferNotFull.signalAll()
        } finally {
            lock.unlock()
        }
        channels?.forEach { try { it.raf.close() } catch (_: Exception) {} }
        threads?.forEach { it.interrupt() }
    }
}
