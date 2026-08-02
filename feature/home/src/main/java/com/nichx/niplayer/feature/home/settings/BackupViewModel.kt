package com.nichx.niplayer.feature.home.settings

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.backup.BackupManager
import com.nichx.niplayer.database.backup.BackupSummary
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.impl.WebDavHttpException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class ExportSuccess(val message: String) : BackupUiState
    data class ImportSuccess(val summary: BackupSummary) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** 已添加的 WebDAV 存储源，供备份上传选择。 */
    private val _webDavLibraries = MutableStateFlow<List<MediaLibraryEntity>>(emptyList())
    val webDavLibraries: StateFlow<List<MediaLibraryEntity>> = _webDavLibraries.asStateFlow()

    /** 所选 WebDAV 服务器 NIplayer_backup 目录下的备份文件，供恢复选择。 */
    private val _webDavBackupFiles = MutableStateFlow<List<StorageFile>>(emptyList())
    val webDavBackupFiles: StateFlow<List<StorageFile>> = _webDavBackupFiles.asStateFlow()

    init {
        viewModelScope.launch {
            _webDavLibraries.value = mediaLibraryDao.getByMediaTypeSuspend(MediaType.WEBDAV_SERVER)
        }
    }

    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working
            try {
                val json = backupManager.exportToJson()
                val output = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("无法打开目标文件")
                output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                _state.value = BackupUiState.ExportSuccess("备份成功")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("备份失败: ${e.toUserMessage()}")
            }
        }
    }

    /** 将备份文件上传到指定的已添加 WebDAV 服务器（NIplayer_backup 目录）。 */
    fun exportToWebDav(libraryId: Int) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working
            try {
                val library = _webDavLibraries.value.firstOrNull { it.id == libraryId }
                    ?: throw IllegalStateException("未找到所选 WebDAV 服务器")
                val json = backupManager.exportToJson()
                withContext(Dispatchers.IO) {
                    val storage = storageFactory.create(library)
                        ?: throw IllegalStateException("无法连接 WebDAV 服务器")
                    // 上传前先验证连接与认证，401/403 等 HTTP 错误直接透传友好提示
                    try {
                        storage.testConnection()
                    } catch (e: WebDavHttpException) {
                        throw IllegalStateException(e.friendlyMessage)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw IllegalStateException("无法连接服务器: ${e.message ?: "网络错误"}")
                    }
                    if (!storage.createDirectory(WEBDAV_BACKUP_DIR)) {
                        throw IllegalStateException("无法创建备份目录 $WEBDAV_BACKUP_DIR")
                    }
                    val ok = storage.saveFile(
                        "$WEBDAV_BACKUP_DIR/${defaultFileName()}",
                        json.toByteArray(Charsets.UTF_8),
                    )
                    if (!ok) throw IllegalStateException("上传失败，请检查服务器配置")
                }
                _state.value = BackupUiState.ExportSuccess("已备份到 ${library.displayName}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WebDAV 备份失败", e)
                _state.value = BackupUiState.Error("WebDAV 备份失败: ${e.toUserMessage()}")
            }
        }
    }

    /** 列出所选 WebDAV 服务器 NIplayer_backup 目录下的备份文件。 */
    fun loadWebDavBackupFiles(libraryId: Int) {
        viewModelScope.launch {
            _webDavBackupFiles.value = emptyList()
            try {
                val library = _webDavLibraries.value.firstOrNull { it.id == libraryId }
                    ?: return@launch
                val files = withContext(Dispatchers.IO) {
                    val storage = storageFactory.create(library) ?: return@withContext emptyList()
                    val dir = object : AbstractStorageFile(WEBDAV_BACKUP_DIR, WEBDAV_BACKUP_DIR, true) {}
                    storage.listFiles(dir)
                        .filter { !it.isDirectory && it.name.endsWith(".json") }
                        .sortedByDescending { it.lastModified }
                }
                _webDavBackupFiles.value = files
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 目录不存在或无权限等：保持空列表，UI 提示无备份文件
            }
        }
    }

    /** 从所选 WebDAV 服务器下载备份文件并恢复。 */
    fun restoreFromWebDav(libraryId: Int, fileName: String) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working
            try {
                val library = _webDavLibraries.value.firstOrNull { it.id == libraryId }
                    ?: throw IllegalStateException("未找到所选 WebDAV 服务器")
                val json = withContext(Dispatchers.IO) {
                    val storage = storageFactory.create(library)
                        ?: throw IllegalStateException("无法连接 WebDAV 服务器")
                    try {
                        storage.testConnection()
                    } catch (e: WebDavHttpException) {
                        throw IllegalStateException(e.friendlyMessage)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw IllegalStateException("无法连接服务器: ${e.message ?: "网络错误"}")
                    }
                    val file = object : AbstractStorageFile(
                        path = "$WEBDAV_BACKUP_DIR/$fileName",
                        name = fileName,
                        isDirectory = false,
                    ) {}
                    storage.openInputStream(file).use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }
                // 恢复前快照现有存储源，恢复时保留本地可用凭据（避免覆盖 WebDAV 恢复源自身等）
                val currentLibraries = mediaLibraryDao.getAllSuspend()
                val summary = backupManager.importFromJson(json, currentLibraries)
                _state.value = BackupUiState.ImportSuccess(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WebDAV 恢复失败", e)
                _state.value = BackupUiState.Error("WebDAV 恢复失败: ${e.toUserMessage()}")
            }
        }
    }

    fun import(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working
            try {
                val json = resolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("无法读取文件")

                // 恢复前快照现有存储源，恢复时保留本地可用凭据
                val currentLibraries = mediaLibraryDao.getAllSuspend()
                val summary = backupManager.importFromJson(json, currentLibraries)
                _state.value = BackupUiState.ImportSuccess(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BackupUiState.Error("恢复失败: ${e.toUserMessage()}")
            }
        }
    }

    fun resetState() {
        _state.value = BackupUiState.Idle
    }

    companion object {
        private const val TAG = "BackupViewModel"
        private const val WEBDAV_BACKUP_DIR = "NIplayer_backup"

        fun defaultFileName(): String {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            return "niplayer_backup_$ts.json"
        }
    }
}

/** 将异常转为面向用户的中文提示，避免显示 "null" 或空字符串。 */
private fun Throwable.toUserMessage(): String = when (this) {
    is WebDavHttpException -> friendlyMessage
    is IllegalStateException -> message ?: "操作失败"
    else -> message ?: toString()
}
