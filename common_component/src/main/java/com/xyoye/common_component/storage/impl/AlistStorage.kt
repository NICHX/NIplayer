package com.xyoye.common_component.storage.impl

import android.net.Uri
import com.xyoye.common_component.network.Retrofit
import com.xyoye.common_component.network.repository.AlistRepository
import com.xyoye.common_component.network.repository.ResourceRepository
import com.xyoye.common_component.storage.AbstractStorage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.impl.AlistStorageFile
import com.xyoye.common_component.utils.MediaMetadataExtractor
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.StorageFileInfo
import com.xyoye.data_component.data.alist.AlistFileData
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream

/**
 * Created by xyoye on 2024/1/20.
 */

class AlistStorage(
    library: MediaLibraryEntity
) : AbstractStorage(library) {

    private var token: String = ""

    private val rootUrl by lazy { rootUri.toString() }

    override suspend fun listFiles(file: StorageFile): List<StorageFile> {
        return AlistRepository.openDirectory(rootUrl, token, file.filePath())
            .getOrNull()
            ?.successData
            ?.fileList
            ?.map {
                AlistStorageFile(file.filePath(), it, this)
            } ?: emptyList()
    }

    override suspend fun getRootFile(): StorageFile? {
        val newToken = refreshToken() ?: return null
        this.token = newToken

        val result = AlistRepository.getUserInfo(rootUrl, token)
        if (result.isFailure) {
             ToastCenter.showToast("${result.exceptionOrNull()?.message}")
            return null
        }

        return result.getOrNull()
            ?.successData
            ?.let {
                AlistFileData("/", true)
            }?.let {
                AlistStorageFile("", it, this)
            }
    }

    override suspend fun openFile(file: StorageFile): InputStream? {
        val rawUrl = getStorageFileUrl(file)
            ?: return null

        return try {
            val request = okhttp3.Request.Builder().url(rawUrl).build()
            val response = com.xyoye.common_component.network.Retrofit.downloadClient
                .newCall(request).execute()
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

    override suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile? {
        if (token.isEmpty()) {
            token = refreshToken() ?: return null
        }

        val pathUri = Uri.parse(path)
        val fileName = pathUri.lastPathSegment
        val parentPath = pathUri.path?.removeSuffix("/$fileName") ?: "/"
        return AlistRepository.openFile(rootUrl, token, path)
            .getOrNull()
            ?.successData
            ?.let {
                AlistStorageFile(parentPath, it, this)
            }
    }

    override suspend fun historyFile(history: PlayHistoryEntity): StorageFile? {
        return history.storagePath
            ?.let { pathFile(it, false) }
            ?.also { it.playHistory = history }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? {
        return getStorageFileUrl(file)
    }

    override suspend fun fileInfo(file: StorageFile): StorageFileInfo? {
        if (file !is AlistStorageFile) return null

        val fileData = file.getFile<AlistFileData>()
        val baseInfo = StorageFileInfo(
            name = file.fileName(),
            path = file.storagePath(),
            isDirectory = file.isDirectory(),
            fileSize = fileData?.size ?: 0L,
            lastModified = 0L,
            isVideo = file.isVideoFile(),
            isAudio = file.isAudioFile(),
            isImage = file.isImageFile()
        )

        if (file.isVideoFile() || file.isAudioFile()) {
            val playUrl = getStorageFileUrl(file) ?: return baseInfo
            return try {
                kotlinx.coroutines.withTimeout(5000) {
                    MediaMetadataExtractor.extractFromUrl(playUrl, emptyMap(), baseInfo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                baseInfo
            }
        }
        return baseInfo
    }

    override suspend fun createDirectory(path: String): Boolean {
        if (token.isEmpty()) {
            token = refreshToken() ?: return false
        }
        return try {
            val body = """{"path":"$path"}""".toRequestBody("application/json".toMediaType())
            val result = Retrofit.alistService.createDirectory(rootUrl, token, body)
            if (result.isSuccess) return true
            AlistRepository.openDirectory(rootUrl, token, path).isSuccess
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun saveFile(path: String, data: ByteArray): Boolean {
        if (token.isEmpty()) {
            token = refreshToken() ?: return false
        }
        return withContext(Dispatchers.IO) {
            try {
                val body = data.toRequestBody("application/octet-stream".toMediaType())
                val result = Retrofit.alistService.uploadFile(rootUrl, token, path, "application/octet-stream", body)
                result.isSuccess
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override suspend fun test(): Boolean {
        return refreshToken()?.isNotEmpty() == true
    }

    private suspend fun refreshToken(): String? {
        val username = library.account ?: return null
        val password = library.password ?: return null

        return AlistRepository.login(rootUrl, username, password)
            .getOrNull()
            ?.successData
            ?.token
    }

    private suspend fun getStorageFileUrl(file: StorageFile): String? {
        val rawUrl = file.getFile<AlistFileData>()?.rawUrl
        if (rawUrl?.isNotEmpty() == true) {
            return rawUrl
        }

        return AlistRepository.openFile(rootUrl, token, file.filePath())
            .getOrNull()
            ?.successData
            ?.rawUrl
    }
}