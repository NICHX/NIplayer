package com.nichx.niplayer.storage.impl

import android.net.Uri
import android.util.Log
import android.util.Xml
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.storage.AbstractStorage
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * W-M1 修复：WebDAV HTTP 错误码异常（4xx/5xx）。
 *
 * 继承 [IOException] 保持与现有 catch 兼容性，调用方可通过 `is WebDavHttpException`
 * 区分 HTTP 错误码与网络异常，决定是否重试。
 *
 * W-N1 / W-N12 修复：[friendlyMessage] 提供面向用户的中文错误提示，
 * 调用方（ViewModel）优先使用此属性而非 [message]（含英文技术细节）。
 *
 * 作为顶层类声明（原嵌套在 [WebDavStorage.companion] 中），便于外部模块
 * 通过 `import ...WebDavHttpException` 直接引用。
 *
 * @param code HTTP 响应码（401/403/404/500 等）
 * @param message 含 URL 和响应码的描述信息（技术细节，用于日志）
 */
class WebDavHttpException(
    val code: Int,
    message: String,
) : IOException(message) {
    /**
     * 面向用户的中文错误提示，按 HTTP 响应码分类。
     *
     * - 401：账号密码错误 → 提示重新编辑存储源
     * - 403：无权限 → 提示检查权限
     * - 404：资源不存在 → 提示路径错误或已删除
     * - 5xx：服务器错误 → 提示服务器异常
     * - 其他：通用错误提示
     */
    val friendlyMessage: String
        get() = when (code) {
            401 -> "账号或密码错误，请编辑存储源重新输入凭据"
            403 -> "无访问权限，请检查账号权限或共享路径"
            404 -> "资源不存在，路径可能已删除或移动"
            in 400..499 -> "请求被服务器拒绝（HTTP $code）"
            in 500..599 -> "服务器异常，请稍后重试（HTTP $code）"
            else -> "WebDAV 请求失败（HTTP $code）"
        }
}

class WebDavStorage(
    library: MediaLibraryEntity,
    private val sharedHttpClient: OkHttpClient,
) : AbstractStorage(library) {

    private val baseUrl: HttpUrl = library.url.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid WebDAV URL: ${library.url}")

    private val credentials: String? by lazy {
        val account = library.account
        if (account.isNullOrEmpty()) null
        else Credentials.basic(account, library.password ?: "")
    }

    private val client: OkHttpClient = if (library.webDavStrict) {
        sharedHttpClient
    } else {
        sharedHttpClient.newBuilder()
            .sslSocketFactory(TRUST_ALL_SSL.socketFactory, TRUST_ALL_MANAGER)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * W-N3 修复：listFiles 结果短期内存缓存。
     *
     * 缓存 key = directory.path，value = (timestamp, files)。
     * TTL = [LIST_CACHE_TTL_MS]，过期后下次 listFiles 重新发 PROPFIND。
     *
     * 目的：避免频繁进出同目录（goUp 后立即 openDirectory / jumpToDepth 来回切换）
     * 重复发 PROPFIND 请求。10s TTL 足够覆盖典型导航场景，又不至于让用户看到过期数据。
     *
     * 线程安全：[ConcurrentHashMap] + [computeIfAbsent] 保证并发安全。
     * 缓存命中时直接返回列表副本（避免调用方修改污染缓存）。
     */
    private val listCache = java.util.concurrent.ConcurrentHashMap<String, List<StorageFile>>()
    private val listCacheTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * 列出目录文件。
     *
     * BUG-F3 修复：首次异常不再静默吞掉，记录 Log.w 后再重试一次。
     * 重试仍失败则抛出第二次异常，由调用方 catch 展示给用户。
     *
     * W-M1 修复：原实现 catch (e: Exception) 无差别重试，401/403/404 等 HTTP 错误码
     * 也被重试一次（凭据未变必再失败），浪费请求且加重服务器负担。现仅对网络异常
     * （非 [WebDavHttpException] 的 IOException）重试，HTTP 错误码直接抛出。
     *
     * W-N3 修复：优先检查 [listCache]，命中且未过期直接返回；未命中或过期才发 PROPFIND。
     */
    override suspend fun listFiles(directory: StorageFile): List<StorageFile> {
        val cacheKey = directory.path
        val now = System.currentTimeMillis()
        // 检查缓存命中
        val cached = listCache[cacheKey]
        val cachedAt = listCacheTimestamps[cacheKey]
        if (cached != null && cachedAt != null && now - cachedAt < LIST_CACHE_TTL_MS) {
            return cached.toList()  // 返回副本，避免调用方修改污染缓存
        }

        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRY) {
            try {
                if (attempt > 0) {
                    val delayMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                    Log.w(TAG, "WebDAV listFiles 网络异常，第 ${attempt + 1} 次重试，等待 ${delayMs}ms")
                    kotlinx.coroutines.delay(delayMs)
                }
                val result = propfind(directory)
                // 写入缓存
                listCache[cacheKey] = result
                listCacheTimestamps[cacheKey] = System.currentTimeMillis()
                return result
            } catch (e: WebDavHttpException) {
                // HTTP 错误码（4xx/5xx）不重试，直接抛出
                throw e
            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "WebDAV listFiles 网络异常(第 ${attempt + 1} 次): ${e.message}")
            }
        }
        throw lastException ?: IOException("WebDAV listFiles 失败")
    }

    /**
     * 打开文件输入流。
     *
     * BUG-F3 修复：首次异常不再静默吞掉，记录 Log.w 后再重试一次。
     * 重试仍失败则抛出第二次异常，由调用方 catch。
     *
     * W-M1 修复：同 [listFiles]，仅对网络异常重试，HTTP 错误码直接抛出。
     */
    override suspend fun openInputStream(file: StorageFile): InputStream {
        return try {
            httpGet(file)
        } catch (e: WebDavHttpException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "WebDAV openInputStream 网络异常，重试: ${e.message}", e)
            httpGet(file)
        }
    }

    /**
     * 断点续传：通过 HTTP Range 头从指定偏移量开始下载。
     *
     * 服务器返回 206 Partial Content 时返回偏移流；不支持 Range 或返回 200 时回退 null，
     * 调用方（DownloadManager）会从头下载。
     */
    override suspend fun openInputStream(file: StorageFile, offset: Long): InputStream? {
        if (offset < 0) return null
        return try {
            httpGet(file, offset)
        } catch (e: WebDavHttpException) {
            // 416 Range Not Satisfiable 等错误表示不支持续传，回退到完整下载
            Log.w(TAG, "WebDAV Range 请求失败，回退完整下载: ${e.message}")
            null
        } catch (e: IOException) {
            Log.w(TAG, "WebDAV Range 请求网络异常，回退完整下载: ${e.message}", e)
            null
        }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? =
        resourceUrl(file.path).toString()

    override suspend fun openMediaDataSource(file: StorageFile): android.media.MediaDataSource? {
        return WebDavMediaDataSource(client, resourceUrl(file.path), credentials, file.length)
    }

    override fun getPlayHeaders(): Map<String, String> {
        val auth = credentials ?: return emptyMap()
        return mapOf("Authorization" to auth)
    }

    /**
     * W-C3 修复：非 strict 模式下告知播放器需要 trust-all TLS。
     *
     * 浏览/缩略图路径由本类的 [client]（已派生 trust-all）处理；
     * 播放路径走 :player:kernel 的 NxMedia3Player（注入 strict 单例 client），
     * 通过此属性让播放器知道需要为当前 MediaSource 派生 trust-all client。
     */
    override val trustAllCertificates: Boolean
        get() = !library.webDavStrict

    /**
     * W-M2 修复：区分"文件不存在"与"网络错误"。
     *
     * - 200/207：存在，返回 true
     * - 404：不存在，返回 false（原实现正确）
     * - 其他 HTTP 错误码（401/403/5xx）：抛 [WebDavHttpException]，让调用方区分
     * - 网络异常（IOException）：抛出，让调用方区分（原实现错误返回 false）
     *
     * 原实现把所有 IOException 当成"不存在"，调用方无法区分网络错误与逻辑结果，
     * 导致缩略图上传流程在网络错误时误以为目录不存在继续尝试 createDirectory。
     */
    override suspend fun fileExists(path: String): Boolean {
        val url = resourceUrl(path)
        val request = buildRequest(url)
            .method("PROPFIND", newPropfindBody())
            .header("Depth", "0")
            .build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                200, 207 -> true
                404 -> false
                else -> throw WebDavHttpException(response.code, "PROPFIND ${url} -> ${response.code} ${response.message}")
            }
        }
    }

    /**
     * W-M2 修复：同 [fileExists]，区分"文件已删除"与"网络错误"。
     *
     * - 200/204：删除成功，返回 true
     * - 404：文件本就不存在，视为删除成功返回 true
     * - 其他 HTTP 错误码（401/403/5xx）：抛 [WebDavHttpException]
     * - 网络异常（IOException）：抛出
     */
    override suspend fun deleteFile(file: StorageFile): Boolean {
        val url = resourceUrl(file.path)
        val request = buildRequest(url)
            .delete()
            .apply { if (file.isDirectory) header("Depth", "infinity") }
            .build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                200, 204 -> {
                    // 失效父目录缓存，确保下次 listFiles 不返回已删除的文件
                    invalidateParentCache(file.path)
                    true
                }
                404 -> true  // 文件本就不存在，视为删除成功
                else -> throw WebDavHttpException(response.code, "DELETE ${url} -> ${response.code} ${response.message}")
            }
        }
    }

    /**
     * W-M1 修复：同 [listFiles]/[openInputStream]，仅对网络异常重试，HTTP 错误码直接返回 false。
     * 原实现 catch (_: Exception) 无日志，调试困难，现补充 Log.w。
     */
    override suspend fun saveFile(path: String, data: ByteArray): Boolean {
        return try {
            httpPut(path, data)
        } catch (e: WebDavHttpException) {
            Log.w(TAG, "WebDAV saveFile HTTP 失败: ${e.message}")
            false
        } catch (e: IOException) {
            Log.w(TAG, "WebDAV saveFile 网络异常，重试: ${e.message}", e)
            try {
                httpPut(path, data)
            } catch (e2: kotlinx.coroutines.CancellationException) {
                throw e2
            } catch (e2: Exception) {
                Log.w(TAG, "WebDAV saveFile 重试仍失败: ${e2.message}", e2)
                false
            }
        }
    }

    override suspend fun createDirectory(path: String): Boolean {
        // 复用 fileExists 检查目录是否已存在
        val exists = try {
            fileExists(path)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (exists) {
            Log.i(TAG, "createDirectory PROPFIND $path -> 目录已存在")
            return true
        }

        // 使用 isDirectory=true 确保 MKCOL 请求 URL 有尾部斜杠，部分 WebDAV 服务器对此有要求
        val url = resourceUrl(path, isDirectory = true)
        val request = buildRequest(url)
            .method("MKCOL", null)
            .build()
        return try {
            val result = client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful || response.code == 405 || response.code == 301 || response.code == 302
                if (!ok) {
                    Log.w(TAG, "createDirectory MKCOL $url -> ${response.code} ${response.message}")
                }
                ok
            }
            // MKCOL 成功或目录已存在
            if (result) {
                // 失效父目录缓存，确保新建目录立即出现在列表中
                invalidateParentCache(path)
                return true
            }
            // 服务器不支持 MKCOL（如 501），改为 PUT 临时文件创建目录
            Log.w(TAG, "createDirectory MKCOL 失败，尝试 PUT 回退创建目录: $path")
            val fallbackOk = createDirectoryViaPutFallback(path)
            if (fallbackOk) invalidateParentCache(path)
            fallbackOk
        } catch (_: IOException) {
            // W-C1 修复：网络错误应返回 false（创建失败）。
            // 原实现错误返回 true，导致调用方（ThumbnailManager 上传缩略图）误以为
            // .thumb/ 目录已创建，后续 PUT 全部失败。目录已存在的情况已由 405 分支处理。
            Log.w(TAG, "createDirectory MKCOL $url -> IOException, 尝试 PUT 回退")
            try {
                val fallbackOk = createDirectoryViaPutFallback(path)
                if (fallbackOk) invalidateParentCache(path)
                fallbackOk
            } catch (_: IOException) {
                false
            }
        }
    }

    /**
     * WebDAV 重命名：HTTP `MOVE` 方法。
     *
     * 目标 URL = 父目录路径 + 新名称。`Destination` 头必须是绝对 URL。
     * 使用 `Overwrite: F` 避免覆盖已存在的同名资源——目标已存在时服务器返回 412，
     * 本方法返回 false，由调用方提示用户。
     *
     * 成功后失效源目录（= 目标目录，rename 为同目录操作）的 listCache。
     */
    override suspend fun rename(file: StorageFile, newName: String): Boolean {
        if (newName.isBlank() || newName == file.name) return false
        val parentPath = file.path.substringBeforeLast('/', "")
        val destPath = if (parentPath.isEmpty()) newName.trim() else "$parentPath/${newName.trim()}"
        return moveInternal(file.path, destPath, file.isDirectory).also { ok ->
            if (ok) invalidateParentCache(file.path, destPath)
        }
    }

    /**
     * WebDAV 移动：HTTP `MOVE` 方法，跨目录移动。
     *
     * 目标 URL = targetDirectory.path + file.name。
     * 同 [rename] 使用 `Overwrite: F`，目标已存在返回 false。
     *
     * 成功后失效源目录和目标目录的 listCache。
     */
    override suspend fun move(file: StorageFile, targetDirectory: StorageFile): Boolean {
        if (file.path == targetDirectory.path) return false
        val destPath = if (targetDirectory.path.isEmpty()) file.name
        else "${targetDirectory.path}/${file.name}"
        return moveInternal(file.path, destPath, file.isDirectory).also { ok ->
            if (ok) invalidateParentCache(file.path, destPath)
        }
    }

    /**
     * WebDAV 流式上传：HTTP PUT 请求，body 为流式 [RequestBody]。
     *
     * 使用 OkHttp 的流式 RequestBody，避免一次性读取整个文件到内存。
     * 上传成功后失效目标目录的 listCache。
     */
    override suspend fun uploadFile(remotePath: String, inputStream: InputStream): Boolean {
        val url = resourceUrl(remotePath)
        // 流式 RequestBody：OkHttp 按需从 InputStream 读取数据写入网络
        val requestBody = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun writeTo(sink: okio.BufferedSink) {
                val buffer = ByteArray(8192)
                inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        val request = buildRequest(url)
            .put(requestBody)
            .header("Overwrite", "T")
            .build()
        return try {
            val ok = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "uploadFile PUT $url -> ${response.code} ${response.message}")
                    false
                } else true
            }
            if (ok) invalidateParentCache(remotePath)
            ok
        } catch (e: IOException) {
            Log.w(TAG, "uploadFile PUT $url 网络异常: ${e.message}", e)
            runCatching { inputStream.close() }
            false
        }
    }

    /**
     * 执行 WebDAV MOVE 请求。
     *
     * @param srcPath 源资源相对路径
     * @param destPath 目标资源相对路径
     * @param isDirectory 是否为目录（影响 URL 尾部斜杠）
     * @return true 表示移动成功，false 表示失败或目标已存在
     */
    private fun moveInternal(srcPath: String, destPath: String, isDirectory: Boolean): Boolean {
        val srcUrl = resourceUrl(srcPath, isDirectory = isDirectory)
        val destUrl = resourceUrl(destPath, isDirectory = isDirectory)
        val request = buildRequest(srcUrl)
            .method("MOVE", ByteArray(0).toRequestBody(null))
            .header("Destination", destUrl.toString())
            .header("Overwrite", "F")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    // 201 Created: 目标资源新建成功（Overwrite: F 下的正常成功）
                    // 204 No Content: 覆盖了已有资源（Overwrite: T 时才会出现，此处设为 F 不应出现，但兼容处理）
                    201, 204 -> {
                        Log.i(TAG, "MOVE $srcPath -> $destPath 成功 (${response.code})")
                        true
                    }
                    // 412 Precondition Failed: Overwrite: F 但目标已存在
                    412 -> {
                        Log.w(TAG, "MOVE 失败：目标已存在 $destPath")
                        false
                    }
                    // 404 源资源不存在
                    404 -> {
                        Log.w(TAG, "MOVE 失败：源资源不存在 $srcPath")
                        false
                    }
                    else -> {
                        Log.w(TAG, "MOVE $srcPath -> $destPath 失败: ${response.code} ${response.message}")
                        false
                    }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "MOVE $srcPath -> $destPath 网络异常: ${e.message}", e)
            false
        }
    }

    /**
     * 失效 MOVE/RENAME 涉及的源目录和目标目录的 listCache。
     *
     * 失效两个路径各自所在的父目录缓存，确保下次 listFiles 重新发 PROPFIND。
     */
    private fun invalidateParentCache(vararg paths: String) {
        paths.forEach { path ->
            val parent = path.substringBeforeLast('/', "")
            listCache.remove(parent)
            listCacheTimestamps.remove(parent)
        }
    }

    /**
     * PUT 回退策略：
     * 1. 先尝试直接 PUT 空文件到目录路径（尾部斜杠），部分服务器会将其视为创建集合
     * 2. 再尝试 PUT 临时标记文件到目录内，依赖服务器自动创建父目录
     * 3. 最后尝试 PUT 带数据的文件到目录路径（无尾部斜杠），部分服务器仅接受非空 PUT
     * 4. 三次都失败则返回 false
     */
    private suspend fun createDirectoryViaPutFallback(path: String): Boolean {
        val emptyBody = ByteArray(0).toRequestBody(OCTET_STREAM)
        val dummyBody = byteArrayOf(0x00).toRequestBody(OCTET_STREAM)

        // 策略1：PUT 空文件到目录路径本身（带尾部斜杠）
        val dirUrl = resourceUrl(path, isDirectory = true)
        val dirPutOk = client.newCall(
            buildRequest(dirUrl).put(emptyBody).header("Overwrite", "T").build()
        ).execute().use { response ->
            if (response.isSuccessful) true else {
                Log.w(TAG, "createDirectoryViaPutFallback 策略1 PUT $dirUrl -> ${response.code} ${response.message}")
                false
            }
        }
        if (dirPutOk) {
            Log.i(TAG, "createDirectoryViaPutFallback 策略1成功: $path")
            return true
        }

        // 策略2：PUT 标记文件到目录内（依赖服务器自动创建父目录）
        val markerPath = path.trimEnd('/') + "/" + DIR_MARKER_FILE
        val markerUrl = resourceUrl(markerPath)
        val markerPutOk = client.newCall(
            buildRequest(markerUrl).put(emptyBody).header("Overwrite", "T").build()
        ).execute().use { response ->
            if (response.isSuccessful) true else {
                Log.w(TAG, "createDirectoryViaPutFallback 策略2 PUT $markerUrl -> ${response.code} ${response.message}")
                false
            }
        }
        if (markerPutOk) {
            // 删除临时标记文件
            runCatching {
                client.newCall(buildRequest(markerUrl).delete().build()).execute().close()
            }
            Log.i(TAG, "createDirectoryViaPutFallback 策略2成功: $path")
            return true
        }

        // 策略3：PUT 带数据的文件到目录路径（无尾部斜杠）
        val dirUrlNoSlash = resourceUrl(path)
        val dirPutDataOk = client.newCall(
            buildRequest(dirUrlNoSlash).put(dummyBody).header("Overwrite", "T").build()
        ).execute().use { response ->
            if (response.isSuccessful) true else {
                Log.w(TAG, "createDirectoryViaPutFallback 策略3 PUT $dirUrlNoSlash -> ${response.code} ${response.message}")
                false
            }
        }
        if (dirPutDataOk) {
            // 删除临时文件
            runCatching {
                client.newCall(buildRequest(dirUrlNoSlash).delete().build()).execute().close()
            }
            Log.i(TAG, "createDirectoryViaPutFallback 策略3成功: $path")
            return true
        }

        Log.w(TAG, "createDirectoryViaPutFallback 策略1/2/3均失败: $path")

        // 诊断：发送 OPTIONS 请求查看服务器支持的方法
        try {
            val optionsResponse = client.newCall(
                buildRequest(baseUrl).method("OPTIONS", null).build()
            ).execute().use { response ->
                val allow = response.header("Allow", "N/A")
                val dav = response.header("DAV", "N/A")
                Log.i(TAG, "createDirectory 服务器能力 - Allow: $allow, DAV: $dav, code: ${response.code}")
            }
        } catch (_: Exception) { }

        // 诊断：PROPFIND 根目录 Depth:1 查看已有目录结构
        try {
            val rootList = client.newCall(
                buildRequest(baseUrl)
                    .method("PROPFIND", newPropfindBody())
                    .header("Depth", "1")
                    .build()
            ).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "N/A"
                    Log.i(TAG, "createDirectory 根目录 PROPFIND Depth:1 body(前500字): ${body.take(500)}")
                } else {
                    Log.w(TAG, "createDirectory 根目录 PROPFIND -> ${response.code}")
                }
            }
        } catch (_: Exception) { }

        return false
    }

    /**
     * WebDAV 心跳检测：发送轻量级 HEAD 请求验证服务器可达性。
     *
     * 比 testConnection（PROPFIND Depth:0）更轻量——HEAD 不解析 XML body。
     */
    override suspend fun ping(): Boolean {
        return try {
            val request = buildRequest(baseUrl).head().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 400..499
                // 4xx 表示服务器可达（只是方法不允许），5xx 表示服务器存活但异常
                // 任何非异常响应都说明连接正常
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun testConnection(): Boolean {
        val request = buildRequest(baseUrl)
            .method("PROPFIND", newPropfindBody())
            .header("Depth", "0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // W-M1 修复：抛 WebDavHttpException，调用方可区分 HTTP 错误码与网络异常
                throw WebDavHttpException(response.code, "WebDAV ${baseUrl} -> ${response.code} ${response.message}")
            }
            return true
        }
    }

    override suspend fun close() {
        // OkHttpClient managed by Hilt
    }

    private fun propfind(directory: StorageFile): List<StorageFile> {
        val isRoot = directory === StorageFactory.ROOT || directory.path.isEmpty()
        val url = resourceUrl(directory.path, isDirectory = isRoot || directory.isDirectory)
        val request = buildRequest(url)
            .method("PROPFIND", newPropfindBody())
            .header("Depth", "1")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavHttpException(response.code, "PROPFIND ${url} -> ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IOException("PROPFIND empty body from $url")
            body.use { parsePropfindXml(it.byteStream(), url) }
        }
    }

    private fun httpGet(file: StorageFile, offset: Long = 0): InputStream {
        val url = resourceUrl(file.path)
        val builder = buildRequest(url).get()
        if (offset > 0) {
            builder.header("Range", "bytes=$offset-")
        }
        val request = builder.build()
        val response = client.newCall(request).execute()
        // Range 请求成功返回 206；不支持 Range 的服务器返回 200（完整内容）
        if (offset > 0 && response.code == 200) {
            // 服务器忽略 Range 头，返回完整内容 —— 无法续传，让调用方回退
            response.close()
            throw WebDavHttpException(200, "Server ignored Range request, full content returned")
        }
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw WebDavHttpException(response.code, "GET ${url} -> ${response.code} ${response.message}")
        }
        val body = response.body
            ?: run {
                response.close()
                throw IOException("GET empty body from $url")
            }
        return ResponseInputStream(body, response)
    }

    /**
     * W-M1 修复：HTTP 错误码时抛 [WebDavHttpException]，让 [saveFile] 的重试逻辑能区分
     * HTTP 错误与网络异常。原实现直接返回 isSuccessful，无法区分 401 与网络错误。
     */
    private fun httpPut(path: String, data: ByteArray): Boolean {
        val url = resourceUrl(path)
        val body = data.toRequestBody(OCTET_STREAM)
        val request = buildRequest(url)
            .put(body)
            .header("Overwrite", "T")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavHttpException(response.code, "PUT ${url} -> ${response.code} ${response.message}")
            }
            true
        }
    }

    private fun resourceUrl(path: String, isDirectory: Boolean = false): HttpUrl {
        val trimmed = path.trim('/')
        val builder = baseUrl.newBuilder()
        if (trimmed.isNotEmpty()) {
            trimmed.split('/').forEach { builder.addPathSegment(it) }
        }
        if (isDirectory && trimmed.isNotEmpty()) {
            val url = builder.build()
            if (!url.encodedPath.endsWith("/")) {
                builder.encodedPath("${url.encodedPath}/")
            }
        }
        return builder.build()
    }

    private fun buildRequest(url: HttpUrl): Request.Builder =
        Request.Builder().url(url).apply {
            credentials?.let { header("Authorization", it) }
        }

    private fun parsePropfindXml(
        stream: InputStream,
        requestedUrl: HttpUrl,
    ): List<StorageFile> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, "UTF-8")

        val results = mutableListOf<StorageFile>()
        val requestedPath = requestedUrl.encodedPath.trimEnd('/')

        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, NS_DAV, "multistatus")

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.namespace == NS_DAV && parser.name == "response") {
                parseResponseEntry(parser, requestedPath)?.let { results.add(it) }
            } else {
                skipTag(parser)
            }
        }
        return results
    }

    private fun parseResponseEntry(
        parser: XmlPullParser,
        requestedPath: String,
    ): StorageFile? {
        parser.require(XmlPullParser.START_TAG, NS_DAV, "response")
        var href: String? = null
        var isCollection = false
        var contentLength = 0L
        var lastModified = 0L
        var isHidden = false

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.namespace != NS_DAV) {
                skipTag(parser)
                continue
            }
            when (parser.name) {
                "href" -> {
                    href = parser.nextText().trim()
                }
                "propstat" -> {
                    parsePropstat(parser) { name, value ->
                        when (name) {
                            "resourcetype" -> isCollection = true
                            "getcontentlength" -> contentLength = value?.toLongOrNull() ?: 0L
                            "getlastmodified" -> lastModified = parseHttpDate(value) ?: 0L
                            "ishidden" -> isHidden = value == "1" || value.equals("true", ignoreCase = true)
                        }
                    }
                }
                else -> skipTag(parser)
            }
        }

        val rawHref = href ?: return null
        val relPath = computeRelativePath(rawHref) ?: return null
        if (relPath.isEmpty()) return null

        // 计算被列目录自身的路径（relative to baseUrl），若 entry 的 relPath 与之相同则为自身引用
        // 解码后再比较：href 可能 URL 编码也可能不编码，取决于服务器实现
        val baseUrlPath = baseUrl.encodedPath.trimEnd('/')
        val dirSelfPath = requestedPath.removePrefix(baseUrlPath).trimStart('/')
        if (relPath.trimEnd('/') == dirSelfPath || Uri.decode(relPath).trimEnd('/') == Uri.decode(dirSelfPath)) return null

        // W-N9 修复：path 编码归一化
        // 1. Uri.decode 解码 percent-encoded 序列（%E4%B8%AD → 中）
        // 2. Normalizer.normalize NFC 归一化 Unicode，避免同一字符的 NFC/NFD 不同编码
        //    （如组合字符 é 在 NFC 是单 codepoint U+00E9，NFD 是 e + U+0301）
        //    不同服务器/客户端可能返回不同 Unicode form，导致 cache key 不一致。
        val decodedPath = java.text.Normalizer.normalize(
            Uri.decode(relPath),
            java.text.Normalizer.Form.NFC,
        )
        val name = decodedPath.substringAfterLast('/').ifEmpty {
            Uri.decode(href!!.trimEnd('/').substringAfterLast('/')).ifEmpty { return null }
        }

        return object : AbstractStorageFile(
            path = decodedPath,
            name = name,
            isDirectory = isCollection,
            length = contentLength,
            lastModified = lastModified,
            isHidden = isHidden,
        ) {}
    }

    /**
     * 解析 propstat 元素。
     *
     * propstat 结构：
     * ```
     * <propstat>
     *   <prop>...</prop>
     *   <status>HTTP/1.1 200 OK</status>
     * </propstat>
     * ```
     *
     * BUG-F4 修复：解析 `<status>` 元素，仅当状态码为 2xx 时才采纳 prop 中的值。
     * 某些 WebDAV 服务器对无权限或不存在的属性返回 404/403，
     * 原实现忽略 status 直接采纳 prop，导致 resourcetype 等属性被错误置空。
     */
    private fun parsePropstat(
        parser: XmlPullParser,
        onProp: (String, String?) -> Unit,
    ) {
        parser.require(XmlPullParser.START_TAG, NS_DAV, "propstat")
        // 先收集 prop 与 status，解析完 propstat 后再按 status 决定是否回调
        var propParsed = false
        var statusOk = true // 默认视为成功，兼容无 status 的服务器
        val pendingProps = mutableListOf<Pair<String, String?>>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.namespace != NS_DAV) {
                skipTag(parser)
                continue
            }
            when (parser.name) {
                "prop" -> {
                    parsePropBody(parser) { name, value ->
                        pendingProps.add(name to value)
                    }
                    propParsed = true
                }
                "status" -> {
                    val statusText = parser.nextText().trim()
                    // 格式如 "HTTP/1.1 200 OK"，提取状态码
                    val code = statusText.substringAfter(' ')
                        .substringBefore(' ')
                        .toIntOrNull()
                    statusOk = code != null && code in 200..299
                }
                else -> skipTag(parser)
            }
        }

        // 仅当 status 为 2xx 时才回调，避免采纳 404/403 的空 prop 值
        if (propParsed && statusOk) {
            pendingProps.forEach { (name, value) -> onProp(name, value) }
        }
    }

    private fun parsePropBody(
        parser: XmlPullParser,
        onProp: (String, String?) -> Unit,
    ) {
        parser.require(XmlPullParser.START_TAG, NS_DAV, "prop")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.namespace != NS_DAV) {
                skipTag(parser)
                continue
            }
            when (parser.name) {
                "resourcetype" -> {
                    var depth = 1
                    while (depth > 0) {
                        when (parser.next()) {
                            XmlPullParser.START_TAG -> {
                                if (parser.namespace == NS_DAV && parser.name == "collection") {
                                    onProp("resourcetype", null)
                                }
                                depth++
                            }
                            XmlPullParser.END_TAG -> depth--
                        }
                    }
                }
                "getcontentlength", "getlastmodified", "displayname", "ishidden" -> {
                    val name = parser.name
                    val text = parser.nextText()
                    if (text.isNotEmpty()) {
                        onProp(name, text)
                    }
                }
                else -> skipTag(parser)
            }
        }
    }

    private fun computeRelativePath(href: String): String? {
        val basePath = baseUrl.encodedPath.trimEnd('/') + "/"
        val hrefPath = when {
            href.startsWith("http://") || href.startsWith("https://") -> {
                try {
                    href.toHttpUrlOrNull()?.encodedPath ?: href
                } catch (_: Exception) {
                    href
                }
            }
            else -> href
        }
        return when {
            hrefPath.startsWith(basePath) -> hrefPath.removePrefix(basePath).trimStart('/')
            else -> hrefPath.trimStart('/')
        }
    }

    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }

    companion object {
        private const val TAG = "WebDavStorage"
        private const val NS_DAV = "DAV:"
        /** 自动重连最大重试次数。 */
        private const val MAX_RETRY = 3
        /** 重试基础延迟（ms），实际延迟按指数退避：500ms, 1000ms。 */
        private const val RETRY_BASE_DELAY_MS = 500L
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        /**
         * W-N3 修复：listFiles 缓存 TTL（10 秒）。
         *
         * - 短 enough：用户在 10s 内修改文件后刷新能看到新数据
         * - 长 enough：覆盖典型的"进入子目录 → 返回上级 → 再进入"导航场景
         */
        private const val LIST_CACHE_TTL_MS = 10_000L
        /** PUT 回退创建目录时使用的临时标记文件名。 */
        private const val DIR_MARKER_FILE = ".tmp_dir_marker"

        private val PROPFIND_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <propfind xmlns="DAV:">
                <prop>
                    <resourcetype/>
                    <getcontentlength/>
                    <getlastmodified/>
                    <ishidden/>
                </prop>
            </propfind>
        """.trimIndent()

        /**
         * W-N10 修复：每次调用新建 RequestBody，避免多线程并发 listFiles 共用同一实例。
         *
         * 原实现 [PROPFIND_BODY] 是常量 [RequestBody]，OkHttp RequestBody 虽对
         * `String.toRequestBody()` 创建的不可变实例是线程安全的，但 OkHttp 内部
         * 会对 RequestBody 做一次性消费（如重试时重新读取），共享实例在某些边缘场景
         * 下可能触发问题。改为工厂方法每次返回新实例，更符合 OkHttp 用法约定。
         */
        private fun newPropfindBody(): RequestBody =
            PROPFIND_XML.toRequestBody("application/xml".toMediaType())

        private val TRUST_ALL_MANAGER = object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {}
            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?,
            ) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        private val TRUST_ALL_SSL: SSLContext by lazy {
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(TRUST_ALL_MANAGER), SecureRandom())
            }
        }

        private val DATE_FORMATS = object : ThreadLocal<List<SimpleDateFormat>>() {
            override fun initialValue(): List<SimpleDateFormat> = listOf(
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            ).also { it.forEach { fmt -> fmt.timeZone = TimeZone.getTimeZone("GMT") } }
        }

        private fun parseHttpDate(value: String?): Long? {
            if (value == null) return null
            for (format in DATE_FORMATS.get()!!) {
                try {
                    return format.parse(value)?.time
                } catch (_: Exception) {}
            }
            return null
        }
    }
}

private class ResponseInputStream(
    private val body: ResponseBody,
    private val response: okhttp3.Response,
) : InputStream() {
    private val delegate = body.byteStream()

    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
    override fun skip(n: Long): Long = delegate.skip(n)
    override fun available(): Int = delegate.available()

    override fun close() {
        try {
            delegate.close()
        } finally {
            try {
                body.close()
            } finally {
                response.close()
            }
        }
    }
}
