package com.nichx.niplayer.storage

import android.media.MediaDataSource
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 存储协议抽象。
 *
 * 统一 Local / WebDAV / SMB 三套协议的访问接口。
 * 实现类通过 [StorageFactory] 按 [MediaLibraryEntity.mediaType] 分发创建。
 *
 * 设计要点：
 * - **不依赖 media3**：保持 :core:storage 纯净，播放器适配由 :player:kernel 承担
 * - **[createPlayUrl] 返回可空**：Local/WebDAV 返回可直接播放的 URL；
 *   SMB 返回 null（需通过 RandomAccessSource 注入 media3，Phase 4 后续实现）
 * - **Coroutines suspend**：全面替代旧仓库的同步阻塞调用
 * - **无 LiveData**：状态由调用方（ViewModel）通过 Flow 暴露
 *
 * 旧仓库 AbstractStorage 承担的额外职责（字幕缓存 / 缩略图 / 搜索）已下沉到
 * utils 或 Phase 5 UI 层，本接口不包含。
 */
interface Storage {

    /** 关联的媒体库配置。 */
    val library: MediaLibraryEntity

    /**
     * 列出目录下的文件/子目录。
     *
     * @param directory 目录，传根目录用 [StorageFactory.ROOT]。
     */
    suspend fun listFiles(directory: StorageFile): List<StorageFile>

    /**
     * 打开文件输入流，用于下载或读取。
     *
     * 注意：SMB 流不支持 random access，seek 需重新打开流。
     */
    suspend fun openInputStream(file: StorageFile): InputStream

    /**
     * 打开文件输入流（从指定偏移量开始），用于断点续传下载。
     *
     * 默认返回 null 表示不支持断点续传，调用方应回退到 [openInputStream] 从头下载。
     * WebDAV 通过 HTTP Range 头实现；其他协议可按需覆盖。
     *
     * @param file 目标文件
     * @param offset 字节偏移量，从该位置开始读取
     * @return 偏移输入流，或 null 表示不支持断点续传
     */
    suspend fun openInputStream(file: StorageFile, offset: Long): InputStream? = null

    /**
     * 读取文件头部最多 [maxBytes] 字节。
     *
     * 默认实现使用 [openInputStream] 读取。
     * SMB 等协议可覆盖此方法以提供更高效的实现（避免创建多线程流）。
     */
    suspend fun readFileBytes(file: StorageFile, maxBytes: Int): ByteArray? {
        return openInputStream(file)?.use { input ->
            val baos = ByteArrayOutputStream(maxBytes)
            val buf = ByteArray(8192)
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (read == -1) break
                baos.write(buf, 0, read)
                total += read
            }
            baos.toByteArray()
        }
    }

    /**
     * 创建可直接播放的 URL。
     *
     * - Local / DocumentFile：返回 `file://` 或 `content://` URI
     * - WebDAV：返回 HTTP(S) URL（media3 OkHttpDataSource 可直接播放）
     * - SMB：返回 null（需通过 RandomAccessSource 注入 media3 DataSource）
     *
     * @return 可直接播放的 URL，或 null 表示需要 DataSource 注入
     */
    suspend fun createPlayUrl(file: StorageFile): String?

    /**
     * 打开支持随机读取的 [MediaDataSource]，用于 [android.media.MediaMetadataRetriever] 生成缩略图。
     *
     * 仅 SMB 覆盖此方法（[createPlayUrl] 返回 null 时，ThumbnailManager 回退到此方法）。
     * Local / WebDAV 不需要：URL 已可直接交给 MediaMetadataRetriever。
     *
     * 调用方负责 [MediaDataSource.close] 释放资源。
     *
     * @return 支持随机读的 MediaDataSource，或 null 表示不支持
     */
    suspend fun openMediaDataSource(file: StorageFile): MediaDataSource? = null

    /**
     * 打开专用于播放的 [InputStream]。
     *
     * 与 [openInputStream] 的区别：播放流应当使用独立的底层连接，
     * 避免文件浏览/缩略图生成等其他操作出错时影响播放流。
     *
     * - SMB：创建独立的 DiskShare 连接，与浏览/缩略图操作完全隔离
     * - 其他（Local / WebDAV）：返回 null，由调用方回退到 [openInputStream]
     *
     * @return 播放流，或 null 表示不支持独立播放连接
     */
    suspend fun openPlayStream(file: StorageFile): InputStream? = null

    /**
     * 播放时附加的 HTTP 请求头（如 WebDAV `Authorization`）。默认空。
     *
     * 仅 [com.nichx.niplayer.player.kernel.NxMediaSource.Http] 使用：
     * [createPlayUrl] 返回 HTTP(S) URL 时，调用方（:feature:home 文件浏览页）
     * 将本方法返回的 headers 注入 NxMediaSource.Http，使 media3 OkHttpDataSource
     * 在拉取 WebDAV 资源时携带认证信息。
     *
     * [NxMediaSource.Local] / [NxMediaSource.DataSource] 不需要 headers。
     */
    fun getPlayHeaders(): Map<String, String> = emptyMap()

    /**
     * 是否需要信任所有 TLS 证书（含自签证书）。
     *
     * W-C3 修复：WebDAV 在非 strict 模式下（`webDavStrict=false`）需要绕过 TLS 校验，
     * 浏览/缩略图路径由 [WebDavStorage] 内部派生 trust-all OkHttpClient 实现，
     * 但播放路径走 :player:kernel 的 NxMedia3Player（注入 strict 单例 client），
     * 此标志让播放器知道需要为当前 MediaSource 派生 trust-all client。
     *
     * 默认 false（Local/SMB 不需要）。WebDavStorage 覆盖为 `!library.webDavStrict`。
     */
    val trustAllCertificates: Boolean get() = false

    /** 文件/目录是否存在。 */
    suspend fun fileExists(path: String): Boolean

    /** 删除文件。目录需为空。返回是否成功。 */
    suspend fun deleteFile(file: StorageFile): Boolean

    /**
     * 保存文件到指定路径（覆盖写入）。
     *
     * 用于缩略图上传到服务端 `.thumb/` 目录：ThumbnailManager 生成缩略图后调用此方法，
     * 下次浏览同一目录时可直接下载已有缩略图，避免重复生成。
     *
     * LocalStorage / VideoStorage 不需要上传（已有本地缓存），默认返回 false。
     *
     * @param path 文件路径（相对存储库根）
     * @param data 文件内容
     * @return 是否保存成功
     */
    suspend fun saveFile(path: String, data: ByteArray): Boolean = false

    /**
     * 创建目录（含父目录）。
     *
     * 用于创建 `.thumb/` 缩略图目录。已存在时返回 true。
     *
     * @param path 目录路径（相对存储库根）
     * @return 是否创建成功（或已存在）
     */
    suspend fun createDirectory(path: String): Boolean = false

    /**
     * 重命名文件或目录（同目录内改名）。
     *
     * 用于文件浏览页"重命名"功能。默认返回 false 表示不支持。
     * SMB 通过 [org.codelibs.jcifs.smb.SmbFile.renameTo] 实现；
     * 其他协议可按需覆盖。
     *
     * @param file 目标文件/目录
     * @param newName 新名称（不含路径，仅文件名）
     * @return 是否重命名成功
     */
    suspend fun rename(file: StorageFile, newName: String): Boolean = false

    /**
     * 移动文件或目录到另一目录。
     *
     * 用于文件浏览页"移动到"功能。默认返回 false 表示不支持。
     * SMB 通过 [org.codelibs.jcifs.smb.SmbFile.renameTo] 实现跨目录移动
     *（SMB renameTo 支持跨目录，等同于 MOVE）。
     *
     * @param file 待移动的文件/目录
     * @param targetDirectory 目标目录（必须已存在）
     * @return 是否移动成功
     */
    suspend fun move(file: StorageFile, targetDirectory: StorageFile): Boolean = false

    /**
     * 流式上传文件到远程存储。
     *
     * 用于文件浏览页"上传文件"功能。读取 [inputStream] 的全部数据写入远程路径。
     * 默认返回 false 表示不支持。
     *
     * SMB 通过 [org.codelibs.jcifs.smb.SmbFile.getOutputStream] 流式写入；
     * WebDAV 通过 HTTP PUT 请求流式 body。
     *
     * @param remotePath 远程目标路径（相对存储库根，含文件名）
     * @param inputStream 本地文件输入流
     * @return 是否上传成功
     */
    suspend fun uploadFile(remotePath: String, inputStream: InputStream): Boolean = false

    /**
     * 带进度回调的上传。
     *
     * 基于 [uploadFile] 扩展：底层读取 [inputStream] 时，[onProgress] 上报**累计已写字节数**。
     * [totalBytes] 用于计算百分比；为负或 0 时表示未知总长，仅按字节累计（无百分比）。
     * [onProgress] 会在非挂起回调（可能 IO/网络线程），需线程安全且低开销。
     * 默认返回 false 表示不支持（Local / FTP 走此回退）。
     *
     * 实现必须**协作式响应协程取消**（在写入循环中检查 [kotlinx.coroutines.ensureActive]），
     * 以便上传任务可被暂停/取消：取消时抛 [kotlinx.coroutines.CancellationException]，
     * 由调用方（[com.nichx.niplayer.storage.download.UploadManager]）落库为 PAUSED/CANCELLED。
     *
     * @param remotePath 远程目标路径（相对存储库根，含文件名）
     * @param inputStream 本地文件输入流
     * @param totalBytes 文件总字节数（未知时传 -1）
     * @param offset 已上传字节数（断点续传起点）。> 0 时底层应跳过本地前 offset 字节、
     *   在远程对应位置继续写入；协议不支持续传时可忽略该值并从头重传。
     *   [onProgress] 上报的字节数为**绝对位置**（含 offset）。
     * @param onProgress 累计已写字节回调（绝对位置）
     */
    suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        totalBytes: Long,
        offset: Long = 0,
        onProgress: (Long) -> Unit,
    ): Boolean = uploadFile(remotePath, inputStream)

    /**
     * 缩略图生成的建议并发数。
     *
     * - SMB / WebDAV：底层线程安全（smbj DiskShare / OkHttpClient），返回 6
     * - Local / VideoStorage：文件 IO 无并发限制，返回 6
     */
    val thumbnailConcurrency: Int get() = 6

    /** 测试连接（登录/认证），返回是否可访问。 */
    suspend fun testConnection(): Boolean

    /**
     * 轻量级连接心跳检测，用于定期验证远程存储是否仍然可达。
     *
     * 与 [testConnection] 的区别：
     * - [testConnection] 用于首次建立连接时验证完整凭据/路径
     * - [ping] 用于已建立连接后快速检测存活，应尽量轻量
     *
     * 默认实现委托 [testConnection]，子类可覆盖为更轻量的实现。
     */
    suspend fun ping(): Boolean = testConnection()

    /**
     * 释放底层资源（连接、session 等）。
     *
     * BUG-07 修复：改为 suspend，允许子类在 close 时获取内部锁（如 SmbStorage 的
     * connectMutex），避免与 ensureSession/ensureShare 竞态导致 NPE 或 Session closed。
     */
    suspend fun close()
}
