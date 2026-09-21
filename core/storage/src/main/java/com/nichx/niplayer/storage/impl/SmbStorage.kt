package com.nichx.niplayer.storage.impl

import android.net.Uri
import android.util.Log
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.storage.AbstractStorage
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    private val rawUrl: String = library.url
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

    /** 寻址（#12）：负责「共享根 + 相对名 → SmbFile」的解析，避免自己拼 URL 时 `?` 被截断。 */
    private val address = SmbAddressing(host, port, shareName)

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
     * 主线/播放线各自的共享内子目录前缀（如 `smbSharePath = "share/films"` → `"films/"`）。
     */
    private fun rootPrefix(isPlay: Boolean): String = if (isPlay) playRootPrefix else shareRootPrefix

    /**
     * 把共享内相对路径解析为 [SmbFile]。
     *
     * codelibs/jcifs 的 SmbFile 使用 `smb://host:port/share/path` 格式，
     * 认证信息通过 context.withCredentials() 传递，不嵌入 URL。
     *
     * 寻址细节（含 `?` / `:` 的边界处理）见 [SmbAddressing]：原实现此处自己拼接
     * `smb://host:port/{share}/{path}` 字符串，文件名含 `?` 时会被 jcifs 当作 query 截断（#12）。
     *
     * - 配置了共享路径：`smb://host:port/{share}/{prefix}{path}`，path 是共享内的相对路径；
     * - 未配置共享路径（可选，[MediaLibraryEntity.smbSharePath] 为 null）：path 首段即共享名
     *   （由服务器根目录的共享枚举产生），直接 `smb://host:port/{path}`。
     */
    private fun resolveSmbFile(
        path: String,
        ctx: CIFSContext = smbContext,
        isPlay: Boolean = false,
    ): SmbFile = address.resolve(ctx, rootPrefix(isPlay), path, isDirectory = false)

    /**
     * 解析**待枚举的目录**。
     *
     * 与 [resolveSmbFile] 分开是为了让「必须补尾随 `/`」这件事在调用点可见、无法漏传：
     * 目录的 SmbFile 若不以 `/` 结尾，jcifs 枚举出的子项会同时丢失父段（URL 错）并把
     * 父名粘进子名（`getName()` 返回 `filmsmovie.mkv`）—— 详见 [SmbAddressing] 类注释。
     */
    private fun resolveSmbDirectory(path: String): SmbFile =
        address.resolve(smbContext, shareRootPrefix, path, isDirectory = true)

    /**
     * 确保主线共享已就绪（实质为设置 [shareRootPrefix]）。
     *
     * codelibs/jcifs 不要求显式 connectShare，SmbFile 的 URL 包含了完整路径；
     * 但为了兼容旧版行为（测试连接时验证 share 可达性），保留此方法。
     */
    private suspend fun ensureShare() {
        connectMutex.withLock {
            if (shareRootPrefix.isNotEmpty()) return@withLock
            val name = shareName
            // 未配置共享路径：以服务器根为浏览起点，前缀留空，由 SmbAddressing 处理
            // （根目录 listFiles 会枚举服务器上可用的共享）
            if (name.isNullOrBlank()) {
                shareRootPrefix = ""
                return@withLock
            }
            val shareOnly = name.trim('/').split("/").first().trim()
            if (shareOnly.isBlank()) {
                throw IllegalArgumentException("SMB 共享名不能为空")
            }
            val sub = name.trim('/').split("/").drop(1).joinToString("/")
            shareRootPrefix = if (sub.isEmpty()) "" else "$sub/"
        }
    }

    private fun ensurePlayShare() {
        val name = shareName
        // 未配置共享路径的同主线处理：直接由 SmbAddressing 处理（命令首段即共享名）
        if (name.isNullOrBlank()) {
            playRootPrefix = ""
            return
        }
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
        val dirFile = resolveSmbDirectory(directory.path)

        // 未配置共享路径时，服务器根目录（path 为空）的 exists() 因无共享会失败，
        // 因此跳过 exists 检查，直接枚举服务器上可用的共享列表。
        val children: List<SmbFile> = if (shareName.isNullOrBlank() && directory.path.isBlank()) {
            dirFile.listFiles().toList()
        } else {
            if (!dirFile.exists()) return emptyList()
            dirFile.listFiles().toList()
        }

        return children
            .filter { file ->
                val n = address.nameOf(file)
                n != "." && n != ".." && !isSystemFile(file)
            }
            .map { file ->
                val name = address.nameOf(file)
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
            // 校验**实际**跳过量：[SmbParallelInputStream.skip] 会按 fileSize 夹紧并返回真实跳过的字节数，
            // 因此 offset 超过远程文件长度时返回的值小于 offset。原实现忽略返回值，会从错误位置开始读，
            // 而调用方（DownloadManager）是按 offset 记录进度的 —— 写出的文件从错位点起全是垃圾数据。
            // 此处返回 null，让调用方回退到 offset = 0 完整重下。
            //
            // 注意：fileSize <= 0（长度未知）时 skip 不做夹紧，该场景无法校验。
            val skipped = stream.skip(offset)
            if (skipped < offset) {
                Log.w(TAG, "SMB 续传偏移 ${offset} 超出文件长度（实际仅跳过 ${skipped}），放弃续传")
                runCatching { stream.close() }
                return null
            }
            stream
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun openInputStreamInternal(file: StorageFile): InputStream {
        ensureShare()
        val fileSize = file.length
        // 下载使用 SmbParallelInputStream 多线程并行预读（与播放流一致），
        // 提升千兆网吞吐 3-4 倍。fileSize<=0 时按无限流处理。
        // 预读通道各自需要独立的 SmbFile 句柄，故传入工厂而非单个实例（#12：寻址交给 SmbAddressing）。
        val stream = SmbParallelInputStream({ resolveSmbFile(file.path) }, fileSize)
        activeStreams.add(stream)
        return SmbParallelInputStreamWrapper(stream, activeStreams)
    }

    override suspend fun readFileBytes(file: StorageFile, maxBytes: Int): ByteArray? {
        return try {
            ensureShare()
            val actualSize = if (file.length > 0) minOf(maxBytes.toLong(), file.length).toInt() else maxBytes
            if (actualSize <= 0) return null
            val sf = resolveSmbFile(file.path)
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

    private fun openPlayStreamInternal(file: StorageFile): InputStream {
        ensurePlayShare()
        val fileSize = file.length
        val stream = SmbParallelInputStream(
            { resolveSmbFile(file.path, playContext, isPlay = true) },
            fileSize,
        )
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun openMediaDataSourceInternal(file: StorageFile): SmbMediaDataSource? {
        ensureShare()
        // file.length 可能为 0（播放退出时用 createVirtualFile 构造的虚拟文件未传 size），
        // 此时用 SmbFile 查询真实文件大小，避免 MediaDataSource 无法打开导致缩略图生成失败。
        val size = if (file.length > 0) file.length
        else runCatching { resolveSmbFile(file.path).length() }.getOrDefault(-1L)
        if (size <= 0) return null
        return SmbMediaDataSource({ resolveSmbFile(file.path) }, size)
    }

    override suspend fun fileExists(path: String): Boolean {
        ensureShare()
        return resolveSmbFile(path).exists()
    }

    override suspend fun deleteFile(file: StorageFile): Boolean {
        return try {
            ensureShare()
            deleteRecursively(file)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "SMB deleteFile failed: ${e.message}")
            false
        }
    }

    /**
     * 递归删除文件/目录。
     *
     * SMB 的 `SmbFile.delete()` 仅能删除空目录，非空目录删除会抛异常。
     * 目录需先删除全部子项（含嵌套子目录）再删自身，才能删除非空文件夹。
     */
    private suspend fun deleteRecursively(file: StorageFile) {
        if (file.isDirectory) {
            listFilesInternal(file).forEach { child ->
                deleteRecursively(child)
            }
        }
        // 长目录删除过程中支持协程取消
        currentCoroutineContext().ensureActive()
        resolveSmbFile(file.path).delete()
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun saveFileInternal(path: String, data: ByteArray): Boolean {
        ensureShare()
        resolveSmbFile(path).getOutputStream().use { os ->
            os.write(data)
        }
        return true
    }

    override suspend fun createDirectory(path: String): Boolean {
        return try {
            ensureShare()
            resolveSmbFile(path).mkdirs()
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 目录已存在或创建失败，通过 exists() 判断
            try { resolveSmbFile(path).exists() } catch (_: Exception) { false }
        }
    }

    /**
     * SMB 重命名：[SmbFile.renameTo] 在同共享内支持跨目录移动（等同 MOVE）。
     *
     * jcifs 的 renameTo 要求目标与源在同一个 server:port:share 上，本实现始终满足此约束
     *（所有路径都由 [SmbAddressing] 解析在同一共享内）。
     */
    override suspend fun rename(file: StorageFile, newName: String): Boolean {
        return try {
            ensureShare()
            // 构建目标 path：父目录 + 新名称
            val parentPath = file.path.substringBeforeLast('/', "")
            val destPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            val src = resolveSmbFile(file.path)
            val dest = resolveSmbFile(destPath)
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
     * 目标路径为 `targetDirectory.path + "/" + file.name`。
     */
    override suspend fun move(file: StorageFile, targetDirectory: StorageFile): Boolean {
        return try {
            ensureShare()
            val destPath = if (targetDirectory.path.isEmpty()) file.name
            else "${targetDirectory.path}/${file.name}"
            val src = resolveSmbFile(file.path)
            val dest = resolveSmbFile(destPath)
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
     * 使用 1MB 缓冲合并写入，避免一次性加载到内存；通过同步写循环累计已写字节并经 [onProgress] 上报。
     */
    override suspend fun uploadFile(remotePath: String, inputStream: InputStream): Boolean =
        uploadFileInternal(remotePath, inputStream, totalBytes = -1L, offset = 0L, onProgress = {})

    override suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        totalBytes: Long,
        offset: Long,
        onProgress: (Long) -> Unit,
    ): Boolean = uploadFileInternal(remotePath, inputStream, totalBytes, offset, onProgress)

    /**
     * SMB 上传核心实现。
     *
     * 性能关键点：codelibs/jcifs 的 [SmbFileOutputStream] **没有内部缓冲**，每次 write()
     * 就是一次同步 SMB2 WRITE 请求（等服务器 ACK 后才返回）。若用小缓冲区（如 8KB）循环写，
     * 每 8KB 一次 RTT，千兆局域网吞吐只有 1-2MB/s。因此这里在本地用 1MB 缓冲累积读取，
     * **攒满一个缓冲块才发起一次同步写**（单块 1MB 与配置的 snd_buf_size 匹配），吞吐可接近线速。
     *
     * 断点续传：`offset > 0` 时用 [SmbRandomAccessFile] seek 到已上传位置继续写，
     * 跳过本地前 offset 字节；`offset == 0` 时用 [SmbFile.getOutputStream]（O_TRUNC 截断重写）。
     *
     * 取消/暂停：写入循环内调用 [kotlinx.coroutines.ensureActive]，任务协程被取消时抛
     * [kotlinx.coroutines.CancellationException]，由 UploadManager 落库为 PAUSED/CANCELLED。
     */
    private suspend fun uploadFileInternal(
        remotePath: String,
        inputStream: InputStream,
        totalBytes: Long,
        offset: Long,
        onProgress: (Long) -> Unit,
    ): Boolean {
        return try {
            ensureShare()
            val smbFile = resolveSmbFile(remotePath)
            if (offset > 0) {
                uploadResume(smbFile, inputStream, offset, onProgress)
            } else {
                uploadFresh(smbFile, inputStream, onProgress)
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 失败原因原先被完全吞掉（只返回 false），续传校验失败等场景无从排查
            Log.w(TAG, "SMB 上传失败 remotePath=$remotePath: ${e.message}")
            false
        } finally {
            runCatching { inputStream.close() }
        }
    }

    /** 全新上传：O_TRUNC 截断目标文件后从头写入。 */
    private suspend fun uploadFresh(
        smbFile: SmbFile,
        inputStream: InputStream,
        onProgress: (Long) -> Unit,
    ) {
        smbFile.getOutputStream().use { rawOut ->
            uploadWriteLoop(
                inputStream = inputStream,
                offset = 0L,
                onProgress = onProgress,
            ) { b, o, l -> rawOut.write(b, o, l) }
        }
    }

    /** 断点续传：seek 到 offset，跳过本地已上传字节后继续写入。 */
    private suspend fun uploadResume(
        smbFile: SmbFile,
        inputStream: InputStream,
        offset: Long,
        onProgress: (Long) -> Unit,
    ) {
        val raf = smbFile.openRandomAccess("rw")
        try {
            raf.seek(offset)
            // 跳过本地前 offset 字节（这部分已上传到远程，无需重复读取）
            var remaining = offset
            val skipBuf = ByteArray(SMB_UPLOAD_BUFFER)
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val n = inputStream.read(skipBuf, 0, minOf(skipBuf.size.toLong(), remaining).toInt())
                if (n < 0) break
                remaining -= n
            }
            // 校验确实跳满了 offset：本地文件短于记录的已上传字节数时（典型场景：暂停期间用户
            // 替换或截断了源文件），remaining > 0 而流已到 EOF。原实现直接 break 后继续写入，
            // 会把本地 offset 之后的内容写到远程的 offset 位置 —— 远程文件从该点起全部错位。
            // 此处显式失败（→ uploadFileInternal 返回 false → UploadManager 落 FAILED），
            // 而不是产出一个内容错位的远程文件；用户重试时进度会重置为 0 走全量上传。
            if (remaining > 0) {
                throw IOException(
                    "本地文件短于已上传进度（尚缺 $remaining 字节），无法续传"
                )
            }
            uploadWriteLoop(
                inputStream = inputStream,
                offset = offset,
                onProgress = onProgress,
            ) { b, o, l -> raf.write(b, o, l) }
        } finally {
            runCatching { raf.close() }
        }
    }

    /**
     * 合并写循环：本地缓冲攒满 [SMB_UPLOAD_BUFFER] 才执行一次同步远程写，
     * 并在**同步写返回后**才累计进度（保证 [onProgress] 与远程实际落盘字节一致，避免断点续传空洞）。
     */
    private suspend fun uploadWriteLoop(
        inputStream: InputStream,
        offset: Long,
        onProgress: (Long) -> Unit,
        write: (ByteArray, Int, Int) -> Unit,
    ) {
        val buffer = ByteArray(SMB_UPLOAD_BUFFER)
        var buffered = 0
        var total = offset
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = inputStream.read(buffer, buffered, buffer.size - buffered)
            if (read < 0) {
                if (buffered > 0) {
                    write(buffer, 0, buffered)
                    total += buffered
                    onProgress(total)
                }
                break
            }
            buffered += read
            if (buffered == buffer.size) {
                write(buffer, 0, buffered)
                total += buffered
                onProgress(total)
                buffered = 0
            }
        }
    }

    /**
     * SMB 心跳检测：验证共享目录可达性。
     *
     * 比 testConnection 更轻量——仅检查 share 是否可达，不设置 shareRootPrefix。
     */
    override suspend fun ping(): Boolean {
        return try {
            if (!shareName.isNullOrBlank()) {
                SmbFile(address.shareBaseUrl(), smbContext).exists()
            } else {
                SmbFile(address.shareBaseUrl(), smbContext)
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
            val smbFile = SmbFile(address.shareBaseUrl(), smbContext)
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
            // 无 share 配置时通过枚举服务器共享验证主机可达与凭据有效
            //（根目录 listFiles 触发 IPC$ 树连接与共享枚举，成功即登录通过）
            SmbFile(address.shareBaseUrl(), smbContext).listFiles()
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

        /**
         * 上传合并写缓冲：与 snd_buf_size 匹配（1MB）。
         * SmbFileOutputStream 无内部缓冲，攒满一块才发起一次同步 SMB2 WRITE，
         * 避免小缓冲（8KB）造成每块一次 RTT 导致千兆网只有 1-2MB/s。
         */
        const val SMB_UPLOAD_BUFFER = 1_048_576

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
