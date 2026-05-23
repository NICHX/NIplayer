package com.xyoye.common_component.storage.impl

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.network.config.HeaderKey
import com.xyoye.common_component.network.helper.UnsafeOkHttpClient
import com.xyoye.common_component.storage.AbstractStorage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.impl.WebDavStorageFile
import com.xyoye.common_component.utils.AudioMetadata
import com.xyoye.common_component.utils.AudioMetadataCache
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.StorageFileInfo
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.sardine.DavResource
import com.xyoye.sardine.impl.OkHttpSardine
import com.xyoye.sardine.util.SardineConfig
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Date

/**
 * Created by xyoye on 2022/12/29
 */

class WebDavStorage(
    library: MediaLibraryEntity
) : AbstractStorage(library) {

    private val sardine = OkHttpSardine(UnsafeOkHttpClient.client)

    init {
        SardineConfig.isXmlStrictMode = this.library.webDavStrict
        getAccountInfo()?.let {
            sardine.setCredentials(it.first, it.second)
        }
    }

    override suspend fun getRootFile(): StorageFile {
        val rootPath = Uri.parse(library.url).path ?: "/"
        return pathFile(rootPath, true)
    }

    override suspend fun openFile(file: StorageFile): InputStream? {
        return try {
            val requestBuilder = okhttp3.Request.Builder().url(file.fileUrl())
            getNetworkHeaders()?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val response = com.xyoye.common_component.network.Retrofit.downloadClient
                .newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun listFiles(file: StorageFile): List<StorageFile> {
        return try {
            sardine.list(file.fileUrl())
                .filter { isChildFile(file.fileUrl(), it.href) }
                .map { WebDavStorageFile(it, this) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile {
        val hrefUrl = resolvePath(path).toString()
        val contentLength = if (!isDirectory) {
            try {
                val resources = sardine.list(hrefUrl, 0)
                resources.firstOrNull()?.contentLength ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else 0L
        val davResource = CustomDavResource(hrefUrl, isDirectory, contentLength)
        return WebDavStorageFile(davResource, this)
    }

    override suspend fun fileExists(path: String): Boolean {
        val targetUrl = rootUri.buildUpon().path(path).toString()
        val headers = getNetworkHeaders()
        return try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .head()
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            response.isSuccessful.also { response.close() }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun createDirectory(path: String): Boolean {
        val targetUrl = rootUri.buildUpon().path(path).toString()
        val headers = getNetworkHeaders()
        return try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .method("MKCOL", null)
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            response.isSuccessful.also { response.close() }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun historyFile(history: PlayHistoryEntity): StorageFile? {
        val storagePath = history.storagePath ?: return null
        return pathFile(storagePath, false).also {
            it.playHistory = history
        }
    }

    override suspend fun cacheLrc(audioFile: StorageFile): String? {
        if (audioFile.isDirectory() || !audioFile.isAudioFile()) return null
        val audioFileName = audioFile.fileName() ?: return null
        val audioNameWithoutExt = audioFileName.substringBeforeLast(".", "")
        if (audioNameWithoutExt.isEmpty()) return null

        val lrcFile = directoryFiles.firstOrNull { file ->
            file.isFile() && file.fileName()?.let { name ->
                name.substringBeforeLast(".", "").equals(audioNameWithoutExt, ignoreCase = true) &&
                name.substringAfterLast(".", "").equals("lrc", ignoreCase = true)
            } == true
        } ?: return null

        return try {
            val inputStream = openFile(lrcFile) ?: return null
            val content = inputStream.reader().readText()
            val lrcDir = File(BaseApplication.getAppContext().cacheDir, "lrc_cache")
            lrcDir.mkdirs()
            val localFile = File(lrcDir, "${audioFile.uniqueKey()}.lrc")
            localFile.writeText(content)
            localFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun createPlayUrl(file: StorageFile): String {
        return file.fileUrl()
    }

    override suspend fun saveFile(path: String, data: ByteArray): Boolean {
        val targetUrl = rootUri.buildUpon().path(path).toString()
        val headers = getNetworkHeaders()
        return try {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .put(data.toRequestBody("image/jpeg".toMediaType()))
                .header("Overwrite", "T")
            headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            response.isSuccessful.also {
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ToastCenter.showError("保存文件失败: ${e.message}")
            false
        }
    }

    override suspend fun fileInfo(file: StorageFile): StorageFileInfo? {
        if (file !is WebDavStorageFile) return null
        return try {
            val resources = sardine.list(file.fileUrl(), 0)
            val resource = resources.firstOrNull() ?: return null
            val isDir = resource.isDirectory

            val baseInfo = StorageFileInfo(
                name = file.fileName(),
                path = file.storagePath(),
                isDirectory = isDir,
                fileSize = resource.contentLength,
                lastModified = resource.modified.time,
                childCount = if (isDir) sardine.list(file.fileUrl()).size - 1 else 0,
                isVideo = file.isVideoFile(),
                isAudio = file.isAudioFile(),
                isImage = file.isImageFile()
            )

            if (file.isAudioFile()) {
                extractAudioMetadataAndCache(file, baseInfo)
            } else {
                baseInfo
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ToastCenter.showError("获取文件信息失败: ${e.message}")
            null
        }
    }

    private suspend fun extractAudioMetadataAndCache(file: WebDavStorageFile, base: StorageFileInfo): StorageFileInfo {
        val uniqueKey = file.uniqueKey()
        val existingMetadata = AudioMetadataCache.get(uniqueKey)
        if (existingMetadata != null && existingMetadata.title.isNotEmpty()) {
            return base.copy(durationMs = existingMetadata.duration)
        }

        val headers = getNetworkHeaders()
        val requestBuilder = Request.Builder().url(file.fileUrl())
        headers?.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        return try {
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return base
            }

            val body = response.body ?: run {
                response.close()
                return base
            }

            val retriever = MediaMetadataRetriever()
            try {
                val inputStream = body.byteStream()
                val bytes = inputStream.readBytes()
                retriever.setDataSource(object : MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        val srcOffset = position.toInt()
                        if (srcOffset >= bytes.size) {
                            return -1
                        }
                        val count = minOf(size, bytes.size - srcOffset)
                        System.arraycopy(bytes, srcOffset, buffer, offset, count)
                        return count
                    }

                    override fun getSize(): Long = bytes.size.toLong()

                    override fun close() {}
                })

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L

                val resolvedTitle = title?.takeIf { it.isNotEmpty() }
                    ?: file.fileName()?.substringBeforeLast(".")?.takeIf { it.isNotEmpty() }
                    ?: ""
                val resolvedArtist = artist?.takeIf { it.isNotEmpty() }
                    ?: albumArtist?.takeIf { it.isNotEmpty() }
                    ?: ""

                val metadata = AudioMetadata(
                    artist = resolvedArtist,
                    title = resolvedTitle,
                    duration = duration,
                    coverPath = null
                )
                AudioMetadataCache.put(uniqueKey, metadata)

                base.copy(durationMs = duration)
            } finally {
                retriever.release()
                response.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            base
        }
    }

    override suspend fun delete(file: StorageFile): Boolean {
        if (file !is WebDavStorageFile) return false
        return try {
            sardine.delete(file.fileUrl())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            ToastCenter.showError("删除失败: ${e.message}")
            false
        }
    }

    override fun getNetworkHeaders(): Map<String, String>? {
        val accountInfo = getAccountInfo()
            ?: return null
        val credential = Credentials.basic(accountInfo.first, accountInfo.second)
        return mapOf(Pair(HeaderKey.AUTHORIZATION, credential))
    }

    override suspend fun test(): Boolean {
        return try {
            sardine.list(getRootFile().fileUrl())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            ToastCenter.showError("连接失败: ${e.message}")
            false
        }
    }

    private fun getAccountInfo(): Pair<String, String>? {
        if (library.account.isNullOrEmpty()) {
            return null
        }
        return Pair(library.account ?: "", library.password ?: "")
    }

    private fun isChildFile(parent: String, child: URI): Boolean {
        try {
            val parentPath = URI(parent).path
            val childPath = child.path
            return parentPath != childPath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private class CustomDavResource(href: String, isDirectory: Boolean = true, contentLength: Long = 0) : DavResource(
        href,
        Date(),
        Date(),
        if (isDirectory) "httpd/unix-directory" else "application/octet-stream",
        contentLength,
        "",
        "",
        emptyList(),
        "",
        emptyList(),
        emptyMap()
    )
}