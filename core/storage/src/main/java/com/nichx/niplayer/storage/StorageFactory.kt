package com.nichx.niplayer.storage

import android.content.Context
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.storage.impl.DocumentFileStorage
import com.nichx.niplayer.storage.impl.SmbStorage
import com.nichx.niplayer.storage.impl.VideoStorage
import com.nichx.niplayer.storage.impl.WebDavStorage
import com.nichx.niplayer.storage.scanner.VideoScanner
import com.nichx.niplayer.storage.security.PasswordVault
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储协议工厂。
 *
 * 按 [MediaLibraryEntity.mediaType] 分发到具体 [Storage] 实现。
 * 替代旧仓库 `common_component/storage/StorageFactory.kt` 的 object 单例，
 * 改为 Hilt @Inject，便于注入 [Context] / [OkHttpClient] / [VideoDao] 等依赖。
 *
 * 当前已实现：
 * - [MediaType.LOCAL_STORAGE] → [VideoStorage]（MediaStore 扫描 + Room 缓存）
 * - [MediaType.EXTERNAL_STORAGE] → [DocumentFileStorage]（SAF/DocumentFile）
 * - [MediaType.SMB_SERVER] → [SmbStorage]（smbj 0.14.0）
 * - [MediaType.WEBDAV_SERVER] → [WebDavStorage]（OkHttp WebDAV 原生实现）
 *
 * 旧仓库的 ALIST_STORAGE 与 FTP_SERVER 已在新仓库移除（参见项目 memory）。
 */
@Singleton
class StorageFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val videoDao: VideoDao,
    private val videoScanner: VideoScanner,
    private val passwordVault: PasswordVault,
) {

    /**
     * 按 [library.mediaType] 创建 [Storage] 实例。
     *
     * BUG-33 修复：创建 Storage 前用 [PasswordVault] 解密 password 字段，
     * 解密后的明文密码仅存在于 Storage 实例内存中，DB 始终存储密文。
     * 解密失败（密钥丢失）返回 null，Storage 认证失败，用户需重新输入密码。
     *
     * @return 对应的 Storage，或 null 表示该 mediaType 不需要 Storage（如 QUICK_ACCESS）
     */
    fun create(library: MediaLibraryEntity): Storage? {
        // BUG-33：解密密码，解密后的副本传入 Storage，原 library 不变
        val decryptedPassword = passwordVault.decrypt(library.password)
        val decryptedLibrary = if (decryptedPassword != library.password) {
            library.copy(password = decryptedPassword)
        } else {
            library
        }
        return when (library.mediaType) {
            MediaType.LOCAL_STORAGE -> VideoStorage(context, decryptedLibrary, videoDao, videoScanner)
            MediaType.EXTERNAL_STORAGE -> DocumentFileStorage(context, decryptedLibrary)
            MediaType.SMB_SERVER -> SmbStorage(decryptedLibrary)
            MediaType.WEBDAV_SERVER -> WebDavStorage(decryptedLibrary, httpClient)
            else -> null
        }
    }

    companion object {
        /** 根目录占位，用于 [Storage.listFiles] 起始点。 */
        val ROOT: StorageFile = RootStorageFile
    }
}

private object RootStorageFile : StorageFile {
    override val path: String = ""
    override val name: String = ""
    override val isDirectory: Boolean = true
    override val length: Long = 0L
    override val lastModified: Long = 0L
    override val isHidden: Boolean = false
}
