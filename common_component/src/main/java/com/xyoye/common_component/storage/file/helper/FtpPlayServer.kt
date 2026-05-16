package com.xyoye.common_component.storage.file.helper

import com.xyoye.common_component.storage.file.impl.FtpStorageFile
import com.xyoye.common_component.storage.impl.FtpStorage
import com.xyoye.common_component.utils.RangeUtils
import com.xyoye.common_component.utils.getFileExtension
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class FtpPlayServer private constructor(port: Int = randomPort()) : NanoHTTPD(port) {

    private val urlFileMap = ConcurrentHashMap<String, Pair<FtpStorage, FtpStorageFile>>()

    private val resourceNotFound by lazy {
        resourceNotFoundResponse()
    }
    private val resourceOpenFailed by lazy {
        resourceOpenFailedResponse()
    }

    private object Holder {
        val instance = FtpPlayServer()
    }

    companion object {
        private const val RETRY_DELAY_MS = 200L
        private const val STREAM_BUFFER_SIZE = 256 * 1024

        private fun randomPort() = Random.nextInt(25001, 30000)

        @JvmStatic
        fun getInstance() = Holder.instance
    }

    init {
    }

    private fun updatePort(newPort: Int) {
        try {
            val portField = NanoHTTPD::class.java.getDeclaredField("myPort")
            portField.isAccessible = true
            portField.setInt(this, newPort)
        } catch (ignored: Exception) {
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val entry = urlFileMap[uri]
        if (entry == null) {
            return resourceNotFound
        }
        val (storage, storageFile) = entry

        storage.completePending()

        val rangeText = session.headers["range"]
        val rangeArray = rangeText?.run {
            RangeUtils.getRange(this, storageFile.fileLength())
        }

        val contentType = resolveContentType(storageFile.filePath())

        return if (rangeArray != null && rangeArray[2] != 0L) {
            getPartialResponse(storage, storageFile, rangeArray, storageFile.fileLength(), contentType)
        } else {
            getOKResponse(storage, storageFile, contentType)
        }
    }

    private fun getInputStream(
        storage: FtpStorage,
        file: FtpStorageFile,
        offset: Long = -1
    ) = runBlocking {
        storage.openFile(file, offset)
    }

    private fun getInputStreamWithRetry(
        storage: FtpStorage,
        file: FtpStorageFile,
        offset: Long = -1
    ): InputStream? {
        val inputStream = getInputStream(storage, file, offset)
        if (inputStream != null) return inputStream
        try {
            Thread.sleep(RETRY_DELAY_MS)
        } catch (_: InterruptedException) {
        }
        return getInputStream(storage, file, offset)
    }

    private fun getPartialResponse(
        storage: FtpStorage,
        storageFile: FtpStorageFile,
        rangeArray: Array<Long>,
        sourceLength: Long,
        contentType: String
    ): Response {
        val inputStream = getInputStreamWithRetry(storage, storageFile, rangeArray[0])
            ?: return resourceOpenFailed
        val rangeLength = rangeArray[1] - rangeArray[0] + 1
        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            contentType,
            BufferedInputStream(inputStream, STREAM_BUFFER_SIZE),
            rangeLength
        )
        val contentRange = "bytes ${rangeArray[0]}-${rangeArray[1]}/$sourceLength"
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Range", contentRange)
        response.addHeader("Content-Length", rangeLength.toString())
        return response
    }

    private fun getOKResponse(
        storage: FtpStorage,
        storageFile: FtpStorageFile,
        contentType: String
    ): Response {
        val inputStream = getInputStreamWithRetry(storage, storageFile)
            ?: return resourceOpenFailed
        return newFixedLengthResponse(
            Response.Status.OK,
            contentType,
            BufferedInputStream(inputStream, STREAM_BUFFER_SIZE),
            storageFile.fileLength()
        )
    }

    private fun resourceNotFoundResponse(): Response {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            "*/*",
            "resource not found"
        )
    }

    private fun resourceOpenFailedResponse(): Response {
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            "*/*",
            "open resource failed"
        )
    }

    private fun resolveContentType(filePath: String): String {
        if (filePath.isEmpty()) {
            return "video/*"
        }
        val extension = getFileExtension(filePath)
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "heif", "heic" -> "image/heic"
            else -> "video/$extension"
        }
    }

    suspend fun startSync(timeoutMs: Long = 5000): Boolean {
        if (wasStarted()) {
            return true
        }
        var lastError: Exception? = null
        for (attempt in 0..5) {
            try {
                return withTimeout(timeoutMs) {
                    start()
                    while (isActive) {
                        if (wasStarted()) {
                            return@withTimeout true
                        }
                    }
                    stop()
                    return@withTimeout false
                }
            } catch (e: Exception) {
                lastError = e
                stop()
                val newPort = Random.nextInt(25001, 30000)
                updatePort(newPort)
            }
        }
        lastError?.printStackTrace()
        return false
    }

    fun generatePlayUrl(
        storage: FtpStorage,
        storageFile: FtpStorageFile
    ): String {
        val urlPath = "/" + storageFile.uniqueKey()
        urlFileMap[urlPath] = storage to storageFile
        return "http://127.0.0.1:$listeningPort$urlPath"
    }

    fun releaseStorage(storage: FtpStorage) {
        val storageId = storage.library.id
        urlFileMap.entries.removeAll { it.value.first.library.id == storageId }
    }

    fun removePlayUrl(urlPath: String) {
        urlFileMap.remove(urlPath)
    }

    fun release() {
        urlFileMap.clear()
        this@FtpPlayServer.stop()
    }
}
