package com.nichx.niplayer.storage.impl

import android.media.MediaDataSource
import android.util.Log
import org.codelibs.jcifs.smb.SmbRandomAccess
import org.codelibs.jcifs.smb.impl.SmbFile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * SMB 协议的 [MediaDataSource] 实现，用于 [android.media.MediaMetadataRetriever] 生成缩略图。
 *
 * 解决问题：SMB 的 [createPlayUrl][com.nichx.niplayer.storage.impl.SmbStorage.createPlayUrl] 返回 null，
 * MediaMetadataRetriever 无法通过 URL 取帧。本类通过 codelibs/jcifs [SmbFile.openRandomAccess] 获取
 * [SmbRandomAccess] 实例，在 [readAt] 中使用 [SmbRandomAccess.seek] 定位后读取。
 *
 * **读前缓冲 + 并发块读（关键性能优化）**：
 * - 取帧器对远程文件的读取是**大量密集小读**（每次 readAt 都要 seek+read 各发一次 SMB 请求），
 *   在网络往返延迟下被放大 —— 优化前单个 1080p MKV 取帧要 6.5s。此处以 [READ_AHEAD_BYTES] 为单位
 *   一次读入一大块并缓存，落在缓冲内的后续 readAt 直接从内存返回。
 * - 单条 SMB 随机读流吞吐有限（约 1MB/s）。故把每个读前块再拆成 [MAX_LANES] 段，用多条
 *   [SmbRandomAccess] 句柄**并发读**，与播放侧 [SmbParallelInputStream] 同一思路。实测单视频
 *   取帧由 6.5s 降至约 1s。
 *
 * 与 [SmbParallelInputStream] 的区别：
 * - SmbParallelInputStream：顺序预读流，用于播放（media3 DataSource）
 * - SmbMediaDataSource：随机读 + 块缓冲 + 并发块读，用于缩略图生成（MediaMetadataRetriever readAt）
 *
 * @param fileProvider 目标文件的 [SmbFile] 工厂（每条并发通道各自打开一个独立句柄）。
 *   不用 URL 字符串是因为 jcifs 拼 URL 时文件名里的 `?` 会被当作 query 截断（#12）。
 * @param fileSize 文件大小
 */
class SmbMediaDataSource(
    private val fileProvider: () -> SmbFile,
    private val fileSize: Long,
) : MediaDataSource() {

    /** 并发读通道（各自一个独立 [SmbRandomAccess] 句柄）。仅在 [lock] 内访问/填充。 */
    private val lanes = arrayOfNulls<SmbRandomAccess>(MAX_LANES)
    private val lock = Any()

    // ---- 读前缓冲状态（仅在 [lock] 内访问）----
    private var block = ByteArray(0)
    private var blockPos = -1L
    private var blockLen = 0

    /** 在持有 [lock] 的调用线程上取第 [index] 条通道（并发任务不得再调用本方法，避免跨线程等锁）。 */
    private fun ensureLane(index: Int): SmbRandomAccess {
        lanes[index]?.let { return it }
        synchronized(lock) {
            lanes[index]?.let { return it }
            return fileProvider().openRandomAccess("r").also { lanes[index] = it }
        }
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || offset < 0 || size < 0 || offset + size > buffer.size) {
            return -1
        }
        if (size == 0) return 0
        if (fileSize > 0 && position >= fileSize) {
            return -1
        }
        val toRead = if (fileSize > 0) {
            minOf(size.toLong(), fileSize - position).toInt()
        } else {
            size
        }
        if (toRead == 0) return 0

        return synchronized(lock) {
            // 命中缓冲：直接从内存返回，无网络往返
            if (blockPos >= 0 && position >= blockPos && position + toRead <= blockPos + blockLen) {
                val off = (position - blockPos).toInt()
                val n = minOf(toRead, blockLen - off)
                System.arraycopy(block, off, buffer, offset, n)
                return@synchronized n
            }
            // 未命中：以 position 为起点读入一大块
            if (!fillBlock(position, toRead)) return@synchronized -1
            val off = (position - blockPos).toInt()
            val n = minOf(toRead, blockLen - off)
            System.arraycopy(block, off, buffer, offset, n)
            n
        }
    }

    /**
     * 从 [position] 起读入一大块到 [block]：优先多通道并发读，失败重试后回退单句柄顺序读。
     *
     * @return true 表示成功填充缓冲（[blockPos]/[blockLen] 有效）
     */
    private fun fillBlock(position: Long, minSize: Int): Boolean {
        val want = maxOf(minSize, READ_AHEAD_BYTES)
        val available = if (fileSize > 0) {
            minOf(want.toLong(), fileSize - position).toInt()
        } else {
            want
        }
        if (available <= 0) return false
        if (block.size < available) block = ByteArray(available)

        var lastError: Exception? = null
        repeat(MAX_READ_AT_RETRIES + 1) {
            if (Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt()
                return false
            }
            try {
                val read = readBlockParallel(position, available)
                if (read > 0) {
                    blockPos = position
                    blockLen = read
                    return true
                }
            } catch (e: Exception) {
                lastError = e
                closeLanes()
            }
        }
        // 回退：单句柄顺序读（并发通道不可用/异常时兜底）
        try {
            val st = ensureLane(0)
            st.seek(position)
            var done = 0
            while (done < available) {
                val r = st.read(block, done, available - done)
                if (r <= 0) break
                done += r
            }
            if (done > 0) {
                blockPos = position
                blockLen = done
                return true
            }
        } catch (e: Exception) {
            lastError = e
            closeLanes()
        }
        Log.w(TAG, "fillBlock exhausted retries at $position: ${lastError?.message}")
        blockPos = -1
        blockLen = 0
        return false
    }

    /**
     * 把 [len] 字节从 [position] 起按 [MAX_LANES] 段并发读入 [block]，返回实际读到的字节数
     * （遇 EOF 时可能小于 [len]，按通道顺序累计到首个未读满的通道为止）。
     *
     * 注意：所有通道句柄在提交任务前于调用线程（已持 [lock]）上打开，避免并发任务跨线程等锁。
     */
    private fun readBlockParallel(position: Long, len: Int): Int {
        val laneCount = minOf(MAX_LANES, maxOf(1, len / MIN_CHUNK_BYTES))
        if (laneCount <= 1) {
            val st = ensureLane(0)
            st.seek(position)
            var done = 0
            while (done < len) {
                val r = st.read(block, done, len - done)
                if (r <= 0) break
                done += r
            }
            return done
        }

        val chunk = (len + laneCount - 1) / laneCount
        // 在调用线程打开好通道句柄，任务内只做 seek+read
        val handles = Array(laneCount) { ensureLane(it) }
        val futures = ArrayList<Future<Int>>(laneCount)
        for (i in 0 until laneCount) {
            val off = i * chunk
            val chunkSize = minOf(chunk, len - off)
            if (chunkSize <= 0) break
            val handle = handles[i]
            futures += READ_POOL.submit<Int> {
                handle.seek(position + off)
                var done = 0
                while (done < chunkSize) {
                    val r = handle.read(block, off + done, chunkSize - done)
                    if (r <= 0) break
                    done += r
                }
                done
            }
        }

        // 等全部任务结束（无论成败），再决定是否抛出，避免带着未完成的读去关句柄
        var failure: Exception? = null
        val chunkDone = IntArray(futures.size)
        for (i in futures.indices) {
            try {
                chunkDone[i] = futures[i].get()
            } catch (e: Exception) {
                failure = e
            }
        }
        failure?.let { throw it }

        // 按通道顺序累计，遇到首个未读满的通道即认为已到 EOF
        var total = 0
        for (i in chunkDone.indices) {
            total += chunkDone[i]
            val off = i * chunk
            val chunkSize = minOf(chunk, len - off)
            if (chunkDone[i] < chunkSize) break
        }
        return total
    }

    override fun getSize(): Long = fileSize

    override fun close() {
        synchronized(lock) {
            closeLanes()
        }
    }

    private fun closeLanes() {
        for (i in lanes.indices) {
            try {
                lanes[i]?.close()
            } catch (_: Exception) {
            }
            lanes[i] = null
        }
        blockPos = -1
        blockLen = 0
    }

    private companion object {
        const val TAG = "SmbMediaDataSource"
        const val MAX_READ_AT_RETRIES = 2

        /** 读前缓冲块大小（2MB）：把取帧器的大量连续小读合并成少数几次大读。 */
        const val READ_AHEAD_BYTES = 2 * 1024 * 1024

        /** 单块并发读的通道数上限。 */
        const val MAX_LANES = 6

        /** 分段读的最小段大小：块太小则不拆分，避免并发调度开销盖过收益。 */
        const val MIN_CHUNK_BYTES = 128 * 1024

        /**
         * 并发块读共享线程池（守护线程）。全局复用，避免每个 [SmbMediaDataSource] 各建池导致线程爆炸。
         */
        val READ_POOL: ExecutorService = Executors.newFixedThreadPool(MAX_LANES * 2) { runnable ->
            Thread(runnable, "smb-thumb-read").apply { isDaemon = true }
        }
    }
}
