package com.nichx.niplayer.player.mpv

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 本地 HTTP 读代理：把 media3 [DataSource]（SMB / WebDAV / FTP / 自定义存储）包装成
 * `http://127.0.0.1:<port>/` 的字节流，供 mpv（libcurl）读取。
 *
 * 背景：mpv 无法识别 app 的 `niplayer-storage://` 自定义协议，也没有 SMB/jcifs 读取器，
 * 因此对非 HTTP 媒体源需要本代理转成 mpv 可读的标准 http 流。
 *
 * 参考 mpvExtended-android 的 [Local HTTP 代理方案](https://github.com/XIONGPEILIN/mpvExtended-android)，
 * 对齐其 SMB 稳定化要点：
 * - **文件大小缓存**：首次 [DataSource.open] 即解析出真实长度并缓存，后续每个 Range 请求
 *   不再重复探测 SMB（避免每次 seek 都重新建连探长）。
 * - **可 seek（206 + Accept-Ranges）**：长度已知时始终回 `Accept-Ranges: bytes` 并按
 *   `bytes=start-end` 回 206，让 mpv 的 demuxer（尤其 MKV/AVI 需索引 seek）能正常工作。
 * - **并发限流**：用 [responseSlots] 信号量限制同一流的并发响应数，避免 mpv 发起并行
 *   Range 请求时一次性打开过量 SMB file handle / 预读线程。
 * - **会话保活**：[keepAlive] 回调周期触发（见 [ensureKeepAlive]），在 mpv 缓冲暂停读取的间隙
 *   维持 SMB 连接不被路由器/NAS 空闲断开，把「缓冲后 seek/切集卡住十几秒」变成即时响应。
 * - **健壮流终止**：有界读取按 [DataSpec] 长度精确定位、异常时保证关闭 DataSource 与连接，
 *   不泄漏 file handle；未知长度降级为 chunked 顺序流（不可 seek 兜底）。
 *
 * 线程模型：accept 循环独立线程，每连接独立处理线程；[stop] 关闭 ServerSocket 使 accept/读退出。
 *
 * @param fileSizeHint 已知文件大小（>0 时直接采用，跳过探测）；否则首次 open 时探测一次。
 * @param keepAlive 可选保活回调，周期调用以维持底层连接（SMB 会话）存活。
 */
@UnstableApi
class StorageProxyServer(
    private val factory: DataSource.Factory,
    private val uri: Uri,
    private val fileSizeHint: Long = C.LENGTH_UNSET.toLong(),
    private val keepAlive: (() -> Unit)? = null,
) {

    private class Client(val socket: Socket, val input: BufferedReader, val output: BufferedOutputStream) {
        fun close() {
            try { input.close() } catch (_: Exception) {}
            try { output.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Client>()
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null

    /** 并发响应信号量（同源并发 Range 限流，防止耗尽 SMB 句柄/线程）。 */
    private val responseSlots = Semaphore(MAX_CONCURRENT_RESPONSES, true)

    /** 已解析的文件长度（字节）；-1 = 未知（长度不可得，降级 chunked）；>=0 = 已知。 */
    @Volatile private var knownSize: Long = NOT_RESOLVED
    private val sizeLock = Any()

    @Volatile private var keepAliveRunning = false
    private var keepAliveThread: Thread? = null
    private val keepAliveLock = Any()

    /** 启动服务，返回基础 URL（形如 `http://127.0.0.1:<port>/`）。 */
    @Synchronized
    fun start(): String {
        if (running.get()) {
            return baseUrl(serverSocket!!.localPort)
        }
        val server = ServerSocket(0)
        serverSocket = server
        running.set(true)
        acceptThread = thread(name = "mpv-proxy-accept", isDaemon = true) {
            while (running.get()) {
                val sock = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                val client = Client(
                    socket = sock,
                    input = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8)),
                    output = BufferedOutputStream(sock.getOutputStream()),
                )
                clients.add(client)
                thread(name = "mpv-proxy-conn", isDaemon = true) {
                    try {
                        handle(client)
                    } catch (_: Throwable) {
                    } finally {
                        clients.remove(client)
                        client.close()
                    }
                }
            }
        }
        return baseUrl(server.localPort)
    }

    private fun baseUrl(port: Int) = "http://127.0.0.1:$port/"

    /**
     * 解析并缓存文件长度。
     *
     * 优先级：外部传入的 [fileSizeHint] → 首次 [DataSource.open] 探测的结果 → 未知。
     * 结果缓存到 [knownSize]，后续 Range 请求不再重复探测 SMB。
     */
    private fun resolveSize(): Long {
        val cached = knownSize
        if (cached != NOT_RESOLVED) return cached
        synchronized(sizeLock) {
            if (knownSize != NOT_RESOLVED) return knownSize
            var size: Long = C.LENGTH_UNSET.toLong()
            if (fileSizeHint > 0) {
                size = fileSizeHint
            } else {
                val probe = try {
                    factory.createDataSource()
                } catch (_: Exception) {
                    null
                }
                if (probe != null) {
                    try {
                        size = probe.open(DataSpec(uri, 0, C.LENGTH_UNSET.toLong(), null))
                    } catch (_: Exception) {
                        size = C.LENGTH_UNSET.toLong()
                    } finally {
                        runCatching { probe.close() }
                    }
                }
            }
            val resolved = size.takeIf { it != C.LENGTH_UNSET.toLong() && it > 0 } ?: -1L
            knownSize = resolved
            return resolved
        }
    }

    /** 在新位置打开一个可读 DataSource，按定长 [DataSpec] 有界读取（对 SMB 可靠）。 */
    private fun openAt(position: Long, length: Long): DataSource? =
        try {
            val specLength = if (length < 0) C.LENGTH_UNSET.toLong() else length
            factory.createDataSource().also { it.open(DataSpec(uri, position, specLength, null)) }
        } catch (_: Exception) {
            null
        }

    private fun handle(client: Client) {
        val requestLine = client.input.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) {
            respond(client.output, 400, "Bad Request")
            return
        }
        val method = parts[0]
        if (method != "GET" && method != "HEAD") {
            respond(client.output, 405, "Method Not Allowed")
            return
        }
        var rangeHeader: String? = null
        while (true) {
            val line = client.input.readLine() ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim().equals("Range", ignoreCase = true)) {
                rangeHeader = line.substring(idx + 1).trim()
            }
        }
        serve(client.output, method, rangeHeader)
    }

    private fun serve(output: OutputStream, method: String, rangeHeader: String?) {
        // 请求处理前先确保保活循环已启动（只对提供了 keepAlive 的源生效）
        ensureKeepAlive()

        // 并发限流：mpv 可能一次性发起多个 Range 请求；SMB 每路 open 都会占 file handle 与预读线程，
        // 超限时回 503 Retry-After 让 libcurl 等待重试，避免句柄/线程耗尽。
        if (!responseSlots.tryAcquire(RESPONSE_SLOT_WAIT_MS, TimeUnit.MILLISECONDS)) {
            writeHead(
                output, 503, "Service Unavailable",
                listOf("Connection: close", "Retry-After: 1"),
            )
            return
        }
        try {
            val totalSize = resolveSize()
            if (totalSize < 0) {
                serveChunked(output, method)
            } else {
                // 长度已知：支持 Range，回 206（可 seek，满足 MKV/AVI 索引需求）
                val (start, endVal) = parseRange(rangeHeader, totalSize)
                val startPos = start ?: 0L
                val endPos = (endVal ?: (totalSize - 1)).coerceAtMost(totalSize - 1)
                val contentLength = endPos - startPos + 1
                val dataSource = openAt(startPos, contentLength) ?: run {
                    writeHead(output, 503, "Service Unavailable", listOf("Connection: close"))
                    return
                }
                try {
                    val partial = start != null
                    val headers = mutableListOf(
                        "Accept-Ranges: bytes",
                        "Content-Type: application/octet-stream",
                        "Content-Length: $contentLength",
                        "Connection: close",
                    )
                    if (partial) {
                        headers.add(0, "Content-Range: bytes $startPos-$endPos/$totalSize")
                    }
                    writeHead(output, if (partial) 206 else 200, if (partial) "Partial Content" else "OK", headers)
                    if (method != "HEAD") {
                        sendBounded(output, dataSource, contentLength)
                    }
                } finally {
                    runCatching { dataSource.close() }
                }
            }
        } finally {
            responseSlots.release()
            if (clients.size <= 0) {
                // 无活跃连接时停止保活，避免空转网络探测
                stopKeepAlive()
            }
        }
    }

    /** 顺序 chunked 读取（未知长度 SMB）：按定长分片 open+read，读到无法推进为止。 */
    private fun serveChunked(output: OutputStream, method: String) {
        writeHead(
            output, 200, "OK",
            listOf(
                "Content-Type: application/octet-stream",
                "Transfer-Encoding: chunked",
                "Connection: close",
            ),
        )
        if (method == "HEAD") return
        var position = 0L
        var segments = 0
        while (segments < MAX_CHUNK_SEGMENTS) {
            val dataSource = openAt(position, -1L) ?: break
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var got = 0L
                while (true) {
                    val read = try { dataSource.read(buffer, 0, buffer.size) } catch (_: Exception) { -1 }
                    if (read <= 0) break
                    output.write("$read\r\n".toByteArray())
                    output.write(buffer, 0, read)
                    output.write("\r\n".toByteArray())
                    output.flush()
                    got += read
                }
                if (got == 0L) break
                position += got
            } finally {
                runCatching { dataSource.close() }
            }
            segments++
        }
        output.write("0\r\n\r\n".toByteArray())
        output.flush()
    }

    /** 从 [dataSource] 发送 [length] 字节，写失败（客户端断开）即中断。 */
    private fun sendBounded(output: OutputStream, dataSource: DataSource, length: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        while (remaining > 0) {
            val read = try { dataSource.read(buffer, 0, minOf(BUFFER_SIZE.toLong(), remaining).toInt()) } catch (_: Exception) { -1 }
            if (read <= 0) break
            try {
                output.write(buffer, 0, read)
            } catch (_: Exception) {
                break // 客户端已断开，停止发送并由上层 finally 关闭 DataSource
            }
            output.flush()
            remaining -= read
        }
        output.flush()
    }

    /** 解析 Range 头。返回 (start, end)；无 Range 时为 (null, null)。 */
    private fun parseRange(header: String?, totalLength: Long): Pair<Long?, Long?> {
        if (header == null) return null to null
        val m = Regex("bytes=(\\d*)-(\\d*)").find(header) ?: return null to null
        val startStr = m.groupValues[1]
        val endStr = m.groupValues[2]
        val start = startStr.toLongOrNull()
        val end = endStr.toLongOrNull()
        if (start == null) {
            // 后缀范围 `bytes=-N`：退回 `bytes=0-`（无总长语义），忽略精确 suffix 的可选优化
            if (end == null) return null to null
            return (totalLength - end).coerceAtLeast(0L) to (totalLength - 1)
        }
        return start to (end?.coerceAtMost(totalLength - 1))
    }

    private fun writeHead(output: OutputStream, code: Int, reason: String, headers: List<String>) {
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        for (h in headers) sb.append(h).append("\r\n")
        sb.append("\r\n")
        output.write(sb.toString().toByteArray())
        output.flush()
    }

    private fun respond(output: OutputStream, code: Int, reason: String) {
        writeHead(output, code, reason, listOf("Connection: close"))
    }

    /** 启动后台保活循环：在连接空闲（mpv 缓冲停止读取）期间周期性触发 [keepAlive]，维持 SMB 会话存活。 */
    private fun ensureKeepAlive() {
        if (keepAlive == null || keepAliveRunning) return
        synchronized(keepAliveLock) {
            if (keepAliveRunning) return
            keepAliveRunning = true
            keepAliveThread = thread(name = "mpv-proxy-keepalive", isDaemon = true) {
                while (running.get() && keepAliveRunning) {
                    try {
                        Thread.sleep(KEEPALIVE_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (!running.get() || !keepAliveRunning) break
                    try {
                        keepAlive()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun stopKeepAlive() {
        if (!keepAliveRunning) return
        synchronized(keepAliveLock) {
            keepAliveRunning = false
            keepAliveThread = null
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        stopKeepAlive()
        try { serverSocket?.close() } catch (_: Exception) {}
        for (c in clients) {
            try { c.socket.close() } catch (_: Exception) {}
        }
        clients.clear()
        serverSocket = null
        acceptThread = null
    }

    companion object {
        private const val NOT_RESOLVED = -2L
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_CHUNK_SEGMENTS = 8192
        /** 同源并发响应上限（mpv Range 请求限流）。 */
        private const val MAX_CONCURRENT_RESPONSES = 4
        /** 响应槽等待时间。 */
        private const val RESPONSE_SLOT_WAIT_MS = 3_000L
        /** 保活探活间隔：低于常见 NAS/路由器 ~25s 的空闲断开窗口。 */
        private const val KEEPALIVE_INTERVAL_MS = 15_000L
    }
}