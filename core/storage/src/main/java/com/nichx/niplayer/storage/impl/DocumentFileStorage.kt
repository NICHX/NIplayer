package com.nichx.niplayer.storage.impl

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.storage.AbstractStorage
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import java.io.InputStream

/**
 * [Storage] 的 DocumentFile（SAF）实现，对应 [com.nichx.niplayer.database.enums.MediaType.EXTERNAL_STORAGE]。
 *
 * 替代旧仓库 `common_component/storage/impl/DocumentFileStorage.kt`。
 *
 * 设计要点：
 * - **根 URI**：[library][MediaLibraryEntity.url] 为 SAF 授权的 tree URI
 *   （`content://com.android.externalstorage.documents/tree/...`）
 * - **路径约定**：[StorageFile.path] 为相对根目录的路径，以 `/` 分隔；根目录为空字符串
 * - **createPlayUrl**：返回 `content://` URI，media3 可通过 ContentDataSource 直接播放
 *   （无需 NanoHTTPD 本地代理，旧仓库亦无此代理）
 * - **无认证**：SAF 由系统授权，[MediaLibraryEntity.account] / [MediaLibraryEntity.password] 不使用
 */
class DocumentFileStorage(
    private val context: Context,
    library: MediaLibraryEntity,
) : AbstractStorage(library) {

    private val rootUri: Uri = Uri.parse(library.url)

    private val rootDocument: DocumentFile? by lazy {
        DocumentFile.fromTreeUri(context, rootUri)
    }

    override suspend fun listFiles(directory: com.nichx.niplayer.storage.StorageFile): List<com.nichx.niplayer.storage.StorageFile> {
        val dirDoc = findDocument(directory) ?: return emptyList()
        return dirDoc.listFiles().map { doc ->
            DocumentStorageFile(
                path = buildPath(directory.path, doc.name ?: ""),
                name = doc.name ?: "",
                isDirectory = doc.isDirectory,
                length = doc.length(),
                lastModified = doc.lastModified(),
                uri = doc.uri,
            )
        }
    }

    override suspend fun openInputStream(file: com.nichx.niplayer.storage.StorageFile): InputStream {
        val doc = findDocument(file)
            ?: throw java.io.FileNotFoundException("Document not found: ${file.path}")
        return context.contentResolver.openInputStream(doc.uri)
            ?: throw java.io.IOException("Cannot open input stream for: ${file.path}")
    }

    override suspend fun createPlayUrl(file: com.nichx.niplayer.storage.StorageFile): String? {
        val doc = findDocument(file) ?: return null
        return doc.uri.toString()
    }

    override suspend fun fileExists(path: String): Boolean {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = rootDocument ?: return false
        for (name in parts) {
            current = current.findFile(name) ?: return false
        }
        return true
    }

    override suspend fun deleteFile(file: com.nichx.niplayer.storage.StorageFile): Boolean {
        val doc = findDocument(file) ?: return false
        return doc.delete()
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val root = rootDocument ?: return false
            root.exists() && root.canRead()
        } catch (_: Exception) {
            // SAF 权限过期时 exists()/canRead() 可能抛 SecurityException
            false
        }
    }

    /**
     * 按 [file.path] 从根目录逐级查找 DocumentFile。
     * 根目录（[StorageFactory.ROOT]）直接返回 [rootDocument]。
     */
    private fun findDocument(file: com.nichx.niplayer.storage.StorageFile): DocumentFile? {
        if (file === com.nichx.niplayer.storage.StorageFactory.ROOT ||
            file.path.isEmpty()
        ) {
            return rootDocument
        }
        val parts = file.path.split("/").filter { it.isNotEmpty() }
        var current = rootDocument ?: return null
        for (name in parts) {
            current = current.findFile(name) ?: return null
        }
        return current
    }

    private fun buildPath(parent: String, name: String): String {
        if (parent.isEmpty()) return name
        return if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
    }
}
