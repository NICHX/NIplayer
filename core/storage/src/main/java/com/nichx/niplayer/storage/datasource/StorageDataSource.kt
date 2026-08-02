package com.nichx.niplayer.storage.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

/**
 * 基于 [Storage] 的 media3 [DataSource] 实现。
 *
 * 替代旧仓库 `SmbPlayServer` 的 NanoHTTPD 本地代理方案：
 * 旧仓库启动随机端口 NanoHTTPD（30001-40000 + 反射改端口 hack），
 * media3 通过 `http://127.0.0.1:port/...` 拉取，代理服务器内部从 SMB 转发。
 *
 * 新方案直接将 [Storage.openInputStream] 包装为 [DataSource]，由 media3 在 IO 线程调用，
 * 无本地代理服务器、无端口反射 hack、无中间字节缓冲。
 *
 * 适用范围：
 * - SMB（[com.nichx.niplayer.storage.impl.SmbStorage]）
 * - DocumentFile（[com.nichx.niplayer.storage.impl.DocumentFileStorage]）
 *
 * 不适用于 WebDAV（直接用 HTTP URL + OkHttpDataSource 更高效）。
 *
 * 限制：
 * - **不支持随机位置 seek**：media3 seek 时会 close → 重新 open，[Storage.openInputStream]
 *   返回的新 InputStream 从头开始，DataSpec.position 通过 [InputStream.skip] 跳过
 * - **传输协议层特性**：SMB seek 需重新建立连接，性能弱于 HTTP Range
 *   （旧仓库 SmbPlayServer 同样有此限制，本方案不增不减）
 *
 * 线程模型与 BUG-P3 修复：
 * - media3 的 [DataSource.open] 是**同步接口**，无法改为 suspend
 * - [runBlocking] 是连接同步接口与 suspend [Storage.openInputStream] 的唯一方式
 * - BUG-P3 修复：为 [runBlocking] 添加 [OPEN_TIMEOUT_MS] 超时保护，
 *   避免 SMB 60s 超时期间 media3 IO 线程被无限阻塞
 * - BUG-F2 修复后，[SmbStorage.ensureShare] 的 double-check 路径在连接已建立时
 *   不获取 Mutex 直接返回，[runBlocking] 实际阻塞时间通常 <100ms
 */
class StorageDataSource private constructor(
    private val storage: Storage,
    private val file: StorageFile,
) : BaseDataSource(true) {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        // BUG-06 修复：media3 在某些 extractor 边缘场景下未调用 close 再次调用 open，
        // 直接覆盖旧 inputStream 引用会导致旧 SmbParallelInputStream（4 个 file handle
        // + 4 个预读线程）泄漏。先关闭旧流再创建新流。
        inputStream?.let { old ->
            try { old.close() } catch (_: java.io.IOException) {}
            inputStream = null
        }

        // BUG-P3 修复：添加超时保护，避免 SMB 60s 超时期间 media3 IO 线程被无限阻塞。
        // BUG-05 修复（间接）：SmbParallelInputStream 改为懒启动后，构造函数不再做 IO，
        // withTimeoutOrNull 超时不会泄漏 file handle / 预读线程。SmbStorage.openInputStream
        // 内部 ensureShare() 是 suspend，超时可正常取消并释放。
        // BUG-17 修复：将 openInputStream 抛出的非 IOException 异常包装为 IOException，
        // 满足 media3 DataSource.open 契约，避免 RuntimeException 穿透导致播放器 Crash。
        //
        // 旧版优化：优先使用 openPlayStream（SMB 独立连接），回退到 openInputStream。
        // openPlayStream 使用独立的 SMB DiskShare，与文件浏览/缩略图操作的连接完全隔离，
        // 避免浏览操作出错重置连接时影响正在播放的视频流。
        val stream = try {
            runBlocking {
                withTimeoutOrNull(OPEN_TIMEOUT_MS) {
                    storage.openPlayStream(file) ?: storage.openInputStream(file)
                }
            }
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Throwable) {
            throw java.io.IOException("Storage openInputStream failed for ${file.path}", e)
        } ?: run {
            throw java.io.IOException(
                "Storage openInputStream timed out after ${OPEN_TIMEOUT_MS}ms for ${file.path}"
            )
        }

        // 跳过到起始位置
        // SmbParallelInputStream.skip 是 O(1)（只更新消费位置和预读起点）
        val position = dataSpec.position
        if (position > 0) {
            var skipped = 0L
            while (skipped < position) {
                val n = stream.skip(position - skipped)
                if (n <= 0) break
                skipped += n
            }
            if (skipped < position) {
                stream.close()
                throw java.io.IOException(
                    "Cannot skip to position $position (skipped $skipped) for ${file.path}"
                )
            }
        }

        // SMB 流已内置多线程并行预读（SmbParallelInputStream），无需再包装
        inputStream = stream
        uri = dataSpec.uri
        opened = true

        // 计算剩余可读字节
        val specLength = dataSpec.length
        bytesRemaining = if (specLength == C.LENGTH_UNSET.toLong()) {
            // 未指定长度：用文件大小估算（file.length 已知则用之，否则返回 LENGTH_UNSET）
            val fileSize = file.length
            if (fileSize > 0) fileSize - position else C.LENGTH_UNSET.toLong()
        } else {
            specLength
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        if (toRead == 0) return C.RESULT_END_OF_INPUT

        val read = stream.read(buffer, offset, toRead)
        if (read < 0) return C.RESULT_END_OF_INPUT

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read.toLong()
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        opened = false
        try {
            inputStream?.close()
        } catch (_: java.io.IOException) {
            // 忽略 close 异常
        }
        inputStream = null
    }

    /**
     * [DataSource.Factory] 实现：每次 [createDataSource] 返回绑定 [storage] + [file] 的新实例。
     *
     * 调用方（:feature:player 的 PlayerViewModel）在播放前构造：
     * ```kotlin
     * val storage = storageFactory.create(library) ?: return
     * val file = object : StorageFile { ... } // 路径信息
     * val dataSource = NxMediaSource.DataSource(
     *     factory = StorageDataSource.Factory(storage, file),
     *     uri = Uri.parse(file.path),
     * )
     * nxPlayer.setSource(dataSource)
     * ```
     */
    class Factory(
        private val storage: Storage,
        private val file: StorageFile,
    ) : DataSource.Factory {
        override fun createDataSource(): StorageDataSource =
            StorageDataSource(storage, file)
    }

    private companion object {
        /**
         * openInputStream 超时保护（BUG-P3 修复）。
         *
         * SMB SoTimeout=60s，设 30s 超时让 media3 在 SMB 超时前先抛出明确错误，
         * 避免用户长时间看着加载转圈不知发生了什么。
         * 连接已建立时通常 <100ms 返回，30s 足够覆盖首次建连 + 弱网场景。
         */
        private const val OPEN_TIMEOUT_MS = 30_000L
    }
}
