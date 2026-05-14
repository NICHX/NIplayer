package com.xyoye.common_component.storage.file.helper

import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.impl.SmbStorageFile
import com.xyoye.common_component.storage.impl.SmbStorage
import com.xyoye.common_component.utils.IOUtils
import com.xyoye.common_component.utils.RangeUtils
import com.xyoye.common_component.utils.getFileExtension
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class SmbPlayServer private constructor(port: Int = randomPort()) : NanoHTTPD(port) {

    private val urlFileMap = ConcurrentHashMap<String, Pair<SmbStorage, SmbStorageFile>>()
    private var mInputStream: InputStream? = null

    private val resourceNotFound by lazy {
        resourceNotFoundResponse()
    }
    private val resourceOpenFailed by lazy {
        resourceOpenFailedResponse()
    }

    private object Holder {
        val instance = SmbPlayServer()
    }

    companion object {

        private fun randomPort() = Random.nextInt(20000, 30000)

        @JvmStatic
        fun getInstance() = Holder.instance
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

        IOUtils.closeIO(mInputStream)

        val inputStream = getInputStream(storage, storageFile)
            ?: return resourceOpenFailed
        mInputStream = inputStream

        val rangeText = session.headers["range"]
        val rangeArray = rangeText?.run {
            RangeUtils.getRange(this, storageFile.fileLength())
        }

        val contentType = resolveContentType(storageFile.filePath())

        return if (rangeArray != null && rangeArray[2] != 0L) {
            getPartialResponse(inputStream, rangeArray, storageFile.fileLength(), contentType)
        } else {
            getOKResponse(inputStream, contentType)
        }
    }

    private fun getInputStream(storage: Storage, file: StorageFile) = runBlocking {
        storage.openFile(file)
    }

    private fun getPartialResponse(
        inputStream: InputStream,
        rangeArray: Array<Long>,
        sourceLength: Long,
        contentType: String
    ): Response {
        val rangeLength = rangeArray[1] - rangeArray[0] + 1

        try {
            var remaining = rangeArray[0]
            while (remaining > 0) {
                val skipped = inputStream.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            contentType,
            inputStream,
            rangeLength
        )
        val contentRange = "bytes ${rangeArray[0]}-${rangeArray[1]}/$sourceLength"
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Range", contentRange)
        response.addHeader("Content-Length", rangeLength.toString())
        return response
    }

    private fun getOKResponse(inputStream: InputStream, contentType: String): Response {
        return newChunkedResponse(
            Response.Status.OK,
            contentType,
            inputStream
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
            } catch (e: java.io.IOException) {
                lastError = e
                stop()
                val newPort = Random.nextInt(20000, 30000)
                updatePort(newPort)
            }
        }
        lastError?.printStackTrace()
        return false
    }

    fun generatePlayUrl(
        storage: SmbStorage,
        storageFile: SmbStorageFile
    ): String {
        val urlPath = "/" + storageFile.uniqueKey()
        urlFileMap[urlPath] = storage to storageFile
        return "http://127.0.0.1:$listeningPort$urlPath"
    }

    fun releaseStorage(storage: SmbStorage) {
        val storageId = storage.library.id
        urlFileMap.entries.removeAll { it.value.first.library.id == storageId }
    }

    fun release() {
        IOUtils.closeIO(mInputStream)
        mInputStream = null
        urlFileMap.clear()
        this@SmbPlayServer.stop()
    }
}
