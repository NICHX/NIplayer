package com.xyoye.common_component.storage.impl

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.storage.AbstractStorage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.impl.DocumentStorageFile
import com.xyoye.common_component.utils.MediaMetadataExtractor
import com.xyoye.data_component.bean.StorageFileInfo
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import java.io.InputStream

/**
 * Created by xyoye on 2022/12/29
 */

class DocumentFileStorage(
    library: MediaLibraryEntity
) : AbstractStorage(library) {

    private val context = BaseApplication.getAppContext()

    override suspend fun getRootFile(): StorageFile? {
        val documentFile = DocumentFile.fromTreeUri(context, rootUri)
            ?: return null
        return DocumentStorageFile(documentFile, this)
    }

    override suspend fun openFile(file: StorageFile): InputStream? {
        if (file.isDirectory() || file.canRead().not()) {
            return null
        }
        val documentFile = file.getFile<DocumentFile>()
            ?: return null
        return context.contentResolver.openInputStream(documentFile.uri)
    }

    override suspend fun listFiles(file: StorageFile): List<StorageFile> {
        val documentFile = file.getFile<DocumentFile>()
            ?: return emptyList()

        return documentFile.listFiles()
            //类型封装，耗时操作，因为需要通过contentResolve查询信息
            .map { DocumentStorageFile(it, this) }
            //过滤文件名为空的文件
            .filter { it.fileName().isNotEmpty() }
    }

    override suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile? {
        val documentFile = if (path.startsWith("content://")) {
            DocumentFile.fromSingleUri(context, Uri.parse(path))
        } else {
            DocumentFile.fromTreeUri(context, resolvePath(path))
        } ?: return null
        return DocumentStorageFile(documentFile, this)
    }

    override suspend fun historyFile(history: PlayHistoryEntity): StorageFile? {
        val storagePath = history.storagePath ?: return null
        val documentFile = DocumentFile.fromSingleUri(
            context,
            Uri.parse(storagePath)
        ) ?: return null

        return DocumentStorageFile(documentFile, this).also {
            it.playHistory = history
        }
    }

    override suspend fun createPlayUrl(file: StorageFile): String {
        return file.fileUrl()
    }

    override suspend fun fileInfo(file: StorageFile): StorageFileInfo? {
        val documentFile = file.getFile<DocumentFile>() ?: return null

        val baseInfo = StorageFileInfo(
            name = file.fileName(),
            path = file.storagePath(),
            isDirectory = file.isDirectory(),
            fileSize = documentFile.length(),
            lastModified = documentFile.lastModified(),
            isVideo = file.isVideoFile(),
            isAudio = file.isAudioFile(),
            isImage = file.isImageFile()
        )

        if (file.isVideoFile() || file.isAudioFile()) {
            val fileUri = documentFile.uri
            return try {
                kotlinx.coroutines.withTimeout(5000) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, fileUri)
                    val result = MediaMetadataExtractor.extractFromRetriever(retriever, baseInfo)
                    retriever.release()
                    result
                }
            } catch (e: Exception) {
                e.printStackTrace()
                baseInfo
            }
        }
        return baseInfo
    }
}