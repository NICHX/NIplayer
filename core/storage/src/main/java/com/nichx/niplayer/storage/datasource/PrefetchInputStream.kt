package com.nichx.niplayer.storage.datasource

import android.util.Log
import java.io.IOException
import java.io.InputStream

/**
 * 异步预读 [InputStream] 包装器。
 *
 * 后台守护线程持续从 [source] 预读数据到内存环形缓冲区，
 * 前台 [read] 从缓冲区读取，不阻塞在网络 IO 上。
 *
 * 用于 SMB 等网络存储的大文件播放：
 * - SMB 单次 READ 往返延迟 10-50ms，顺序读取时吞吐不稳定
 * - 32MB 缓冲区可存储约 3 秒 4K HDR 数据（80Mbps ≈ 10MB/s）
 * - 即使 SMB 偶发延迟，内存缓冲区可平滑波动避免卡顿
 *
 * 线程模型：
 * - 预读线程（daemon）调用 [source.read] 填充环形缓冲区
 * - 调用方线程通过 [read] 从缓冲区读取
 * - [lock] 保护缓冲区状态，[wait]/[notifyAll] 协调生产消费
 *
 * 注意：不支持 [skip]。调用方应在包装 [PrefetchInputStream] 之前
 * 对原始 [source] 执行 skip。
 */
class PrefetchInputStream(
    private val source: InputStream,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) : InputStream() {

    private val buffer = ByteArray(bufferSize)
    private var readPos = 0
    private var writePos = 0
    private var availableBytes = 0
    private var eof = false
    private var error: IOException? = null
    private val lock = Object()

    private val prefetchThread = Thread(this::doPrefetch, "PrefetchInputStream").apply {
        isDaemon = true
        start()
    }

    private fun doPrefetch() {
        val chunk = ByteArray(PREFETCH_CHUNK_SIZE)
        var totalRead = 0L
        var lastLogTime = System.currentTimeMillis()
        var lastLogRead = 0L
        try {
            while (true) {
                synchronized(lock) {
                    // 缓冲区满时等待消费
                    while (availableBytes == bufferSize && !eof) {
                        lock.wait()
                    }
                    if (eof) return
                }

                val readStartMs = System.currentTimeMillis()
                val read = source.read(chunk)
                val readCostMs = System.currentTimeMillis() - readStartMs

                if (read == -1) {
                    synchronized(lock) {
                        eof = true
                        lock.notifyAll()
                    }
                    return
                }
                if (read > 0) {
                    writeToBuffer(chunk, 0, read)
                    totalRead += read

                    // 每秒输出一次预读统计：速度、缓冲区水位、单次读取耗时
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= 1000) {
                        val speedMBps = (totalRead - lastLogRead).toDouble() / (now - lastLogTime) / 1024.0
                        val waterLevel = synchronized(lock) { availableBytes * 100 / bufferSize }
                        Log.d(TAG, "prefetch: +${(totalRead - lastLogRead) / 1024}KB/${speedMBps.format(2)}MB/s water=${waterLevel}% lastRead=${readCostMs}ms total=${totalRead / 1024}KB")
                        lastLogTime = now
                        lastLogRead = totalRead
                    }

                    // 单次读取超过 500ms 说明 SMB 响应慢
                    if (readCostMs > 500) {
                        Log.w(TAG, "prefetch slow read: ${readCostMs}ms for ${read} bytes")
                    }
                }
            }
        } catch (_: InterruptedException) {
            // close() 中断预读线程，正常退出
            Log.d(TAG, "prefetch interrupted (total=${totalRead / 1024}KB)")
        } catch (e: IOException) {
            // source 被并发关闭（media3 seek 会 close 旧 DataSource）会抛 IOException，
            // 设置 error 让消费端感知；media3 会重新 open 新 DataSource
            Log.d(TAG, "prefetch IOException (total=${totalRead / 1024}KB)")
            synchronized(lock) {
                error = e
                lock.notifyAll()
            }
        } catch (e: Exception) {
            // 兜底捕获 smbj NPE 等运行时异常（file 字段并发关闭后变 null）
            Log.w(TAG, "prefetch exception: ${e.javaClass.simpleName}: ${e.message}")
            synchronized(lock) {
                error = if (e is IOException) e else IOException(e)
                lock.notifyAll()
            }
        }
    }

    private fun writeToBuffer(data: ByteArray, offset: Int, length: Int) {
        var remaining = length
        var srcPos = offset
        synchronized(lock) {
            while (remaining > 0) {
                while (availableBytes == bufferSize && !eof && error == null) {
                    lock.wait()
                }
                if (eof || error != null) return

                val space = bufferSize - availableBytes
                val toWrite = minOf(remaining, space)
                val firstWrite = minOf(toWrite, bufferSize - writePos)

                System.arraycopy(data, srcPos, buffer, writePos, firstWrite)
                writePos = (writePos + firstWrite) % bufferSize

                if (toWrite > firstWrite) {
                    val secondWrite = toWrite - firstWrite
                    System.arraycopy(data, srcPos + firstWrite, buffer, writePos, secondWrite)
                    writePos = (writePos + secondWrite) % bufferSize
                }

                availableBytes += toWrite
                srcPos += toWrite
                remaining -= toWrite
                lock.notifyAll()
            }
        }
    }

    override fun read(): Int {
        synchronized(lock) {
            while (availableBytes == 0 && !eof && error == null) {
                try {
                    lock.wait()
                } catch (_: InterruptedException) {
                    // BUG-09 修复：InputStream.read 契约只声明 throws IOException，
                    // InterruptedException 穿透会导致 media3 IO 线程 Crash
                    Thread.currentThread().interrupt()
                    throw IOException("PrefetchInputStream read interrupted")
                }
            }
            error?.let { throw it }
            if (availableBytes == 0 && eof) return -1

            val b = buffer[readPos].toInt() and 0xFF
            readPos = (readPos + 1) % bufferSize
            availableBytes--
            lock.notifyAll()
            return b
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        synchronized(lock) {
            while (availableBytes == 0 && !eof && error == null) {
                try {
                    lock.wait()
                } catch (_: InterruptedException) {
                    // BUG-09 修复：同 read()，捕获 InterruptedException 转 IOException
                    Thread.currentThread().interrupt()
                    throw IOException("PrefetchInputStream read interrupted")
                }
            }
            error?.let { throw it }
            if (availableBytes == 0 && eof) return -1

            val toRead = minOf(len, availableBytes)
            val firstRead = minOf(toRead, bufferSize - readPos)
            System.arraycopy(buffer, readPos, b, off, firstRead)
            readPos = (readPos + firstRead) % bufferSize

            if (toRead > firstRead) {
                val secondRead = toRead - firstRead
                System.arraycopy(buffer, readPos, b, off + firstRead, secondRead)
                readPos = (readPos + secondRead) % bufferSize
            }

            availableBytes -= toRead
            lock.notifyAll()
            return toRead
        }
    }

    override fun available(): Int {
        synchronized(lock) {
            return availableBytes
        }
    }

    @Volatile
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        synchronized(lock) {
            eof = true
            lock.notifyAll()
        }
        // 中断预读线程（避免阻塞在 source.read 等待网络数据）
        prefetchThread.interrupt()
        try {
            source.close()
        } catch (_: IOException) {}
    }

    companion object {
        private const val TAG = "PrefetchInputStream"
        // 32MB 缓冲区：约 3 秒 4K HDR 数据（80Mbps ≈ 10MB/s）
        const val DEFAULT_BUFFER_SIZE = 32 * 1024 * 1024
        // 单次预读块大小：256KB，平衡内存拷贝和预读粒度
        const val PREFETCH_CHUNK_SIZE = 256 * 1024
    }

    private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
}
