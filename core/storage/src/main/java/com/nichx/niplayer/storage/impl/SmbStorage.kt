package com.nichx.niplayer.storage.impl

import android.net.Uri
import android.util.Log
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.storage.AbstractStorage
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.codelibs.jcifs.smb.CIFSContext
import org.codelibs.jcifs.smb.config.PropertyConfiguration
import org.codelibs.jcifs.smb.context.BaseContext
import org.codelibs.jcifs.smb.impl.NtlmPasswordAuthenticator
import org.codelibs.jcifs.smb.impl.SmbFile
import java.io.IOException
import java.io.InputStream
import java.util.Properties

/**
 * [Storage] 的 SMB 实现，对应 [com.nichx.niplayer.database.enums.MediaType.SMB_SERVER]。
 *
 * 使用 codelibs/jcifs 替代 smbj。
 *
 * 设计要点：
 * - **协议库**：codelibs/jcifs 3.0.0（fork from jcifs-ng，完整 SMB2/3 支持）
 * - **URL 解析**：[library][MediaLibraryEntity.url] 支持两种格式：
 *   - `smb://host[:port]`（标准）
 *   - `host[:port]`（用户直接填 IP）
 * - **认证**：[library][MediaLibraryEntity.isAnonymous] 为 true 时匿名登录，
 *   否则用 account/password
 * - **共享路径可选**：[library][MediaLibraryEntity.smbSharePath] 为 null 时
 *   仍可测试连接（只建认证），但 listFiles/openInputStream 会抛异常
 * - **createPlayUrl 返回 null**：SMB 需要通过 DataSource 注入 media3
 */
class SmbStorage(
    library: MediaLibraryEntity,
) : AbstractStorage(library) {

    private val rawUrl: String = library.url.orEmpty()
    private val parsedUri: Uri = if (rawUrl.contains("://")) {
        Uri.parse(rawUrl)
    } else {
        Uri.parse("smb://$rawUrl")
    }

    private val host: String = parsedUri.host
        ?: throw IllegalArgumentException("无效的 SMB 地址: ${library.url}")
    private val port: Int = parsedUri.port.takeIf { it > 0 }
        ?: library.port.takeIf { it > 0 }
        ?: DEFAULT_SMB_PORT

    private val shareName: String? = library.smbSharePath

    /**
     * 构建 [CIFSContext]（替代 smbj 的 SMBClient + SmbConfig）。
     *
     * 根据 [MediaLibraryEntity.smbV2] 和 [MediaLibraryEntity.smbEncryption] 配置协议版本与加密。
     * codelibs/jcifs 对 SMB3 加密提供完整支持（AES-128-CCM/GCM），非实验性。
     */
    private val smbContext: CIFSContext by lazy {
        val props = Properties().apply {
            // 连接超时 10s：服务器不可达时快速失败，避免长时间无响应
            setProperty("jcifs.smb.client.connTimeout", CONN_TIMEOUT_MS.toString())
            // 响应超时 60s：宽松响应窗口，容忍大文件播放/网络波动的读取延迟
            setProperty("jcifs.smb.client.responseTimeout", RESPONSE_TIMEOUT_MS.toString())

            // 缓冲调优：匹配千兆网络吞吐
            setProperty("jcifs.smb.client.rcv_buf_size", RCV_BUF_SIZE.toString())
            setProperty("jcifs.smb.client.snd_buf_size", SND_BUF_SIZE.toString())
            setProperty("jcifs.smb.client.bufferSize", BUFFER_SIZE.toString())

            // 并行请求调优：SMB2 多路复用数
            setProperty("jcifs.smb.client.maxMpxCount", MAX_MPX_COUNT.toString())

            // TCP 低延迟
            setProperty("jcifs.smb.client.tcpNoDelay", "true")

            if (library.smbV2) {
                setProperty("jcifs.smb.client.minVersion", "SMB210")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                if (library.smbEncryption) {
                    setProperty("jcifs.smb.client.encryptData", "true")
                    setProperty("jcifs.smb.client.signingEnabled", "true")
                }
            } else {
                setProperty("jcifs.smb.client.minVersion", "SMB1")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
            }
        }
        val config = PropertyConfiguration(props)
        val auth = if (library.isAnonymous) {
            NtlmPasswordAuthenticator(NtlmPasswordAuthenticator.AuthenticationType.NULL)
        } else {
            NtlmPasswordAuthenticator(
                library.domain?.takeIf { it.isNotBlank() },
                library.account,
                library.password ?: "",
            )
        }
        BaseContext(config).withCredentials(auth)
    }

    /** 播放专用的独立 CIFSContext（共享配置但使用独立连接池）。 */
    private val playContext: CIFSContext by lazy {
        BaseContext(smbContext.config).withCredentials(smbContext.credentials)
    }

    /**
     * 当前活跃的 [InputStream] 列表。
     * 已从 smbj 的 resetSessionLocked 简化，jcifs 连接池由库内部管理。
     */
    private val activeStreams = java.util.Collections.synchronizedList(mutableListOf<InputStream>())
    private val playActiveStreams = java.util.Collections.synchronizedList(mutableListOf<InputStream>())

    /**
     * 并发连接互斥锁。
     * codelibs/jcifs 的 CIFSContext 内部管理连接池，但 ensureShare 仍需保护
     * [shareRootPrefix] 等状态的并发写入。
     */
    private val connectMutex = Mutex()

    /** [ensureShare] 后设置的共享内子路径前缀（如 name="/media/films" → "films/"）。 */
    @Volatile private var shareRootPrefix: String = ""
    @Volatile private var playRootPrefix: String = ""

    /**
     * 构建 smb:// 完整 URL。
     *
     * codelibs/jcifs 的 SmbFile 使用 `smb://host:port/share/path` 格式，
     * 认证信息通过 context.withCredentials() 传递，不嵌入 URL。
     */
    private fun buildSmbUrl(path: String, isPlay: Boolean = false): String {
        val prefix = if (isPlay) playRootPrefix else shareRootPrefix
        val normalizedPath = prefix + path
        val share = shareName?.trim('/')?.split("/")?.first()?.trim()
            ?: throw IllegalStateException("未配置 SMB 共享路径")
        return "smb://$host:$port/$share/$normalizedPath"
    }

    /**
     * 确保主线共享已就绪（实质为设置 [shareRootPrefix]）。
     *
     * codelibs/jcifs 不要求显式 connectShare，SmbFile 的 URL 包含了完整路径；
     * 但为了兼容旧版行为（测试连接时验证 share 可达性），保留此方法。
     */
    private suspend fun ensureShare() {
        connectMutex.withLock {
            if (!shareRootPrefix.isEmpty()) return@withLock
            val name = shareName
                ?: throw IllegalStateException("未配置 SMB 共享路径，无法浏览文件")
            val shareOnly = name.trim('/').split("/").first().trim()
            if (shareOnly.isBlank()) {
                throw IllegalArgumentException("SMB 共享名不能为空")
            }
            val sub = name.trim('/').split("/").drop(1).joinToString("/")
            shareRootPrefix = if (sub.isEmpty()) "" else "$sub/"
        }
    }

    private suspend fun ensurePlayShare() {
        val name = shareName
            ?: throw IllegalStateException("未配置 SMB 共享路径")
        val shareOnly = name.trim('/').split("/").first().trim()
        if (shareOnly.isBlank()) {
            throw IllegalArgumentException("SMB 共享名不能为空")
        }
        val sub = name.trim('/').split("/").drop(1).joinToString("/")
        playRootPrefix = if (sub.isEmpty()) "" else "$sub/"
    }

    /** SMB 缩略图生成建议并发数。 */
    override val thumbnailConcurrency: Int get() = 2

    override suspend fun listFiles(directory: StorageFile): List<StorageFile> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRY) {
            try {
                if (attempt > 0) {
                    // 重置状态，让 ensureShare() 重新计算 shareRootPrefix
                    shareRootPrefix = ""
                    val delayMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                    Log.w(TAG, "SMB listFiles 第 ${attempt + 1} 次重试，等待 ${delayMs}ms")
                    kotlinx.coroutines.delay(delayMs)
                }
                return listFilesInternal(directory)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "SMB listFiles 失败(第 ${attempt + 1} 次): ${e.message}")
            }
        }
        throw lastException ?: IOException("SMB listFiles 失败")
    }

    private suspend fun listFilesInternal(directory: StorageFile): List<StorageFile> {
        ensureShare()
        val url = buildSmbUrl(directory.path)
        val dirFile = SmbFile(url, smbContext)

        if (!dirFile.exists()) return emptyList()

        return dirFile.listFiles().toList()
            .filter { file ->
                val n = extractName(file)
                n != "." && n != ".." && !isSystemFile(file)
            }
            .map { file ->
                val name = extractName(file)
                val hidden = file.isHidden()
                object : AbstractStorageFile(
                    path = buildPath(directory.path, name),
                    name = name,
                    isDirectory = file.isDirectory(),
                    length = file.length(),
                    lastModified = file.lastModified().coerceAtLeast(0L),
                    isHidden = hidden,
                ) {}
            }
    }

    /** codelibs/jcifs SmbFile.getName() 返回从 share 根开始的完整路径，需提取最后一级。 */
    private fun extractName(file: SmbFile): String {
        return file.path.trimEnd('/').substringAfterLast('/')
    }

    private fun isSystemFile(file: SmbFile): Boolean {
        val attrs = file.getAttributes()
        return (attrs and 0x04) != 0
    }

    override suspend fun openInputStream(file: StorageFile): InputStream {
        try {
            return openInputStreamInternal(file)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            shareRootPrefix = ""
            try {
                return openInputStreamInternal(file)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (ce: Exception) {
                throw IOException(
                    "打开 SMB 文件失败：${file.name}（${ce.message ?: ce::class.simpleName}）",
                    ce,
                )
            }
        }
    }

    /**
     * 断点续传：通过 [SmbParallelInputStream] 的 [skip] 跳过已下载字节。
     *
     * SMB 协议不支持 HTTP Range 那样的原生 offset 读取，但 [SmbParallelInputStream.skip]
     * 是 O(1) 操作（仅更新消费位置和预读起点，清空缓冲），seek 到目标 offset 后并行预读，
     * 不会产生读取-丢弃开销。
     */
    override suspend fun openInputStream(file: StorageFile, offset: Long): InputStream? {
        if (offset <= 0) return null
        return try {
            val stream = openInputStreamInternal(file) as SmbParallelInputStreamWrapper
            stream.skip(offset)
            stream
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun openInputStreamInternal(file: StorageFile): InputStream {
        ensureShare()
        val url = buildSmbUrl(file.path)
        val fileSize = file.length
        // 下载使用 SmbParallelInputStream 多线程并行预读（与播放流一致），
        // 提升千兆网吞吐 3-4 倍。fileSize<=0 时按无限流处理。
        val stream = SmbParallelInputStream(smbContext, url, fileSize)
        activeStreams.add(stream)
        return SmbParallelInputStreamWrapper(stream, activeStreams)
    }

    override suspend fun readFileBytes(file: StorageFile, maxBytes: Int): ByteArray? {
        return try {
            ensureShare()
            val url = buildSmbUrl(file.path)
            val actualSize = if (file.length > 0) minOf(maxBytes.toLong(), file.length).toInt() else maxBytes
            if (actualSize <= 0) return null
            val sf = SmbFile(url, smbContext)
            val raf = sf.openRandomAccess("r")
            try {
                val buffer = ByteArray(actualSize)
                var totalRead = 0
                raf.seek(0)
                while (totalRead < actualSize) {
                    val n = raf.read(buffer, totalRead, actualSize - totalRead)
                    if (n <= 0) break
                    totalRead += n
                }
                if (totalRead <= 0) null else buffer.copyOf(totalRead)
            } finally {
                try { raf.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "readFileBytes failed: ${e.message}")
            null
        }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? = null

    override suspend fun openPlayStream(file: StorageFile): InputStream? {
        return try {
            openPlayStreamInternal(file)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                openPlayStreamInternal(file)
            } catch (e: Exception) {
                throw IOException(
                    "打开 SMB 播放流失败：${file.name}（${e.message ?: e::class.simpleName}）",
                    e,
                )
            }
        }
    }

    private suspend fun openPlayStreamInternal(file: StorageFile): InputStream {
        ensurePlayShare()
        val url = buildSmbUrl(file.path, isPlay = true)
        val fileSize = file.length
        val stream = SmbParallelInputStream(playContext, url, fileSize)
        playActiveStreams.add(stream)
        return object : InputStream() {
            override fun read() = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int) = stream.read(b, off, len)
            override fun skip(n: Long) = stream.skip(n)
            override fun available() = stream.available()
            override fun close() {
                playActiveStreams.remove(stream)
                stream.close()
            }
        }
    }

    override suspend fun openMediaDataSource(file: StorageFile): android.media.MediaDataSource? {
        return try {
            openMediaDataSourceInternal(file)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            shareRootPrefix = ""
            try {
                openMediaDataSourceInternal(file)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun openMediaDataSourceInternal(file: StorageFile): SmbMediaDataSource? {
        ensureShare()
        val url = buildSmbUrl(file.path)
        if (file.length <= 0) return null
        return SmbMediaDataSource(smbContext, url, file.length)
    }

    override suspend fun fileExists(path: String): Boolean {
        ensureShare()
        val url = buildSmbUrl(path)
        return SmbFile(url, smbContext).exists()
    }

    override suspend fun deleteFile(file: StorageFile): Boolean {
        return try {
            ensureShare()
            val url = buildSmbUrl(file.path)
            SmbFile(url, smbContext).delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun saveFile(path: String, data: ByteArray): Boolean {
        return try {
            saveFileInternal(path, data)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            shareRootPrefix = ""
            try {
                saveFileInternal(path, data)
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun saveFileInternal(path: String, data: ByteArray): Boolean {
        ensureShare()
        val url = buildSmbUrl(path)
        SmbFile(url, smbContext).getOutputStream().use { os ->
            os.write(data)
        }
        return true
    }

    override suspend fun createDirectory(path: String): Boolean {
        var url: String? = null
        return try {
            ensureShare()
            url = buildSmbUrl(path)
            SmbFile(url, smbContext).mkdirs()
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 目录已存在或创建失败，通过 exists() 判断
            val resolvedUrl = url ?: return false
            try { SmbFile(resolvedUrl, smbContext).exists() } catch (_: Exception) { false }
        }
    }

    /**
     * SMB 重命名：[SmbFile.renameTo] 在同共享内支持跨目录移动（等同 MOVE）。
     *
     * jcifs 的 renameTo 要求目标与源在同一个 server:port:share 上，本实现始终满足此约束
     *（所有 URL 由 [buildSmbUrl] 构建在同一共享内）。
     */
    override suspend fun rename(file: StorageFile, newName: String): Boolean {
        return try {
            ensureShare()
            val srcUrl = buildSmbUrl(file.path)
            // 构建目标 path：父目录 + 新名称
            val parentPath = file.path.substringBeforeLast('/', "")
            val destPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            val destUrl = buildSmbUrl(destPath)
            val src = SmbFile(srcUrl, smbContext)
            val dest = SmbFile(destUrl, smbContext)
            src.renameTo(dest)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * SMB 移动：跨目录等同于 rename（SMB renameTo 支持跨目录）。
     *
     * 目标 URL 为 `targetDirectory.path + "/" + file.name`。
     */
    override suspend fun move(file: StorageFile, targetDirectory: StorageFile): Boolean {
        return try {
            ensureShare()
            val srcUrl = buildSmbUrl(file.path)
            val destPath = if (targetDirectory.path.isEmpty()) file.name
            else "${targetDirectory.path}/${file.name}"
            val destUrl = buildSmbUrl(destPath)
            val src = SmbFile(srcUrl, smbContext)
            val dest = SmbFile(destUrl, smbContext)
            src.renameTo(dest)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * SMB 流式上传：[SmbFile.getOutputStream] 返回的输出流流式写入。
     *
     * 使用 8KB 缓冲区循环读取 [inputStream] 并写入远程文件，避免一次性加载到内存。
     */
    override suspend fun uploadFile(remotePath: String, inputStream: InputStream): Boolean {
        return try {
            ensureShare()
            val url = buildSmbUrl(remotePath)
            val smbFile = SmbFile(url, smbContext)
            smbFile.getOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            runCatching { inputStream.close() }
        }
    }

    /**
     * SMB 心跳检测：验证共享目录可达性。
     *
     * 比 testConnection 更轻量——仅检查 share 是否可达，不设置 shareRootPrefix。
     */
    override suspend fun ping(): Boolean {
        return try {
            val name = shareName
            if (!name.isNullOrBlank()) {
                val shareOnly = name.trim('/').split("/").first().trim()
                val testUrl = "smb://$host:$port/$shareOnly/"
                SmbFile(testUrl, smbContext).exists()
            } else {
                val testUrl = "smb://$host:$port/"
                SmbFile(testUrl, smbContext)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun testConnection(): Boolean {
        val name = shareName
        if (!name.isNullOrBlank()) {
            val shareOnly = name.trim('/').split("/").first().trim()
            if (shareOnly.isBlank()) {
                throw IOException("SMB 共享名不能为空")
            }
            // 通过创建 SmbFile 并验证 exists 来测试 share 可达性
            val testUrl = "smb://$host:$port/$shareOnly/"
            val smbFile = SmbFile(testUrl, smbContext)
            if (!smbFile.exists()) {
                throw IOException("无法访问共享「$shareOnly」")
            }
            connectMutex.withLock {
                if (shareRootPrefix.isEmpty()) {
                    val sub = name.trim('/').split("/").drop(1).joinToString("/")
                    shareRootPrefix = if (sub.isEmpty()) "" else "$sub/"
                }
            }
        } else {
            // 无 share 配置时至少验证服务器可达（通过 SmbFile 构造不抛异常）
            val testUrl = "smb://$host:$port/"
            SmbFile(testUrl, smbContext)
        }
        return true
    }

    override suspend fun close() {
        synchronized(activeStreams) {
            val iter = activeStreams.iterator()
            while (iter.hasNext()) {
                try { iter.next().close() } catch (_: Exception) {}
                iter.remove()
            }
        }
        synchronized(playActiveStreams) {
            val iter = playActiveStreams.iterator()
            while (iter.hasNext()) {
                try { iter.next().close() } catch (_: Exception) {}
                iter.remove()
            }
        }
    }

    private fun buildPath(parent: String, name: String): String {
        if (parent.isEmpty()) return name
        return if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
    }

    private companion object {
        const val TAG = "SmbStorage"
        const val DEFAULT_SMB_PORT = 445
        /** 连接建立超时（ms）：服务器不可达时快速失败。 */
        const val CONN_TIMEOUT_MS = 10_000L
        /** 请求响应超时（ms）：宽松响应窗口，容忍大文件播放/网络波动。 */
        const val RESPONSE_TIMEOUT_MS = 60_000L
        /** 自动重连最大重试次数。 */
        const val MAX_RETRY = 3
        /** 重试基础延迟（ms），实际延迟按指数退避：500ms, 1000ms。 */
        const val RETRY_BASE_DELAY_MS = 500L

        /** SMB 配置缓冲大小。 */
        const val RCV_BUF_SIZE = 1_048_576
        const val SND_BUF_SIZE = 1_048_576
        const val BUFFER_SIZE = 2_097_152

        /** SMB2 最大并发未完成请求数（多路复用）。 */
        const val MAX_MPX_COUNT = 128
    }
}

/**
 * [SmbParallelInputStream] 的委托包装器，负责从 [SmbStorage.activeStreams] 移除自身。
 *
 * 下载路径使用（[SmbStorage.openInputStream]），与播放路径的匿名 InputStream 包装一致：
 * close 时从 activeStreams 移除并关闭底层流。
 */
private class SmbParallelInputStreamWrapper(
    private val delegate: SmbParallelInputStream,
    private val activeStreams: MutableList<InputStream>,
) : InputStream() {
    override fun read() = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int) = delegate.read(b, off, len)
    override fun skip(n: Long) = delegate.skip(n)
    override fun available() = delegate.available()
    override fun close() {
        activeStreams.remove(delegate)
        delegate.close()
    }
}
