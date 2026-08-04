package com.nichx.niplayer.feature.home.settings

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.backup.BackupManager
import com.nichx.niplayer.database.backup.BackupSummary
import com.nichx.niplayer.database.backup.RestoreMode
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.PlayHistorySyncConfig
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.datastore.WebDavSettings
import com.nichx.niplayer.sync.PlayHistorySyncManager
import com.nichx.niplayer.sync.SyncUiState
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
    private val syncManager: PlayHistorySyncManager,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** 已添加的 WebDAV 存储源，供备份上传选择。 */
    private val _webDavLibraries = MutableStateFlow<List<MediaLibraryEntity>>(emptyList())
    val webDavLibraries: StateFlow<List<MediaLibraryEntity>> = _webDavLibraries.asStateFlow()

    /** 所选 WebDAV 服务器 id（共享配置 [WebDavSettings]，备份与云同步共用）。 */
    private val _selectedWebDavId = MutableStateFlow(WebDavSettings.libraryId)
    val selectedWebDavId: StateFlow<Int> = _selectedWebDavId.asStateFlow()

    /** 所选 WebDAV 服务器 NIplayer_backup 目录下的备份文件，供恢复选择。 */
    private val _webDavBackupFiles = MutableStateFlow<List<StorageFile>>(emptyList())
    val webDavBackupFiles: StateFlow<List<StorageFile>> = _webDavBackupFiles.asStateFlow()

    /** 备份文件列表加载中（慢网速/大目录时提示）。 */
    private val _webDavBackupLoading = MutableStateFlow(false)
    val webDavBackupLoading: StateFlow<Boolean> = _webDavBackupLoading.asStateFlow()

    /** 加载备份文件列表失败的错误提示（卡片内展示，null = 无错误）。 */
    private val _webDavBackupError = MutableStateFlow<String?>(null)
    val webDavBackupError: StateFlow<String?> = _webDavBackupError.asStateFlow()

    /** 播放历史云同步状态（设置页卡片与历史页 TopBar 共用）。 */
    val syncState: StateFlow<SyncUiState> = syncManager.state

    /** 播放历史云同步配置（开关 / 自动同步 / 上次结果）。 */
    val historySyncConfig: StateFlow<PlayHistorySyncConfig> = PlayHistorySyncSettings.flow

    init {
        viewModelScope.launch {
            _webDavLibraries.value = mediaLibraryDao.getByMediaTypeSuspend(MediaType.WEBDAV_SERVER)
            // 共享配置优先；配置缺失或已失效时默认选第一个并回写
            val libs = _webDavLibraries.value
            if (libs.isNotEmpty()) {
                val saved = WebDavSettings.libraryId
                if (saved < 0 || libs.none { it.id == saved }) {
                    selectWebDavServer(libs.first().id)
                } else {
                    _selectedWebDavId.value = saved
                }
            }
        }
    }

    /** 选择 WebDAV 服务器：写入共享配置，并自动启用播放历史云同步。 */
    fun selectWebDavServer(libraryId: Int) {
        WebDavSettings.setLibraryId(libraryId)
        _selectedWebDavId.value = libraryId
        PlayHistorySyncSettings.enabled = true
    }

    /** 播放历史云同步总开关。 */
    fun setHistorySyncEnabled(enabled: Boolean) {
        PlayHistorySyncSettings.enabled = enabled
    }

    /** 自动同步开关（应用启动 / 播放器退出后触发）。 */
    fun setAutoSync(enabled: Boolean) {
        PlayHistorySyncSettings.autoSync = enabled
    }

    /** 立即执行一次播放历史云同步。 */
    fun syncNow() {
        viewModelScope.launch {
            syncManager.sync()
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
                    // 清理当前设备的旧备份，最多保留 3 份
                    pruneDeviceBackups(storage, MAX_BACKUPS_PER_DEVICE)
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
            _webDavBackupLoading.value = true
            _webDavBackupError.value = null
            _webDavBackupFiles.value = emptyList()
            try {
                val library = _webDavLibraries.value.firstOrNull { it.id == libraryId }
                    ?: return@launch
                val files = withContext(Dispatchers.IO) {
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
                    val dir = object : AbstractStorageFile(WEBDAV_BACKUP_DIR, WEBDAV_BACKUP_DIR, true) {}
                    try {
                        storage.listFiles(dir)
                            .filter { !it.isDirectory && it.name.endsWith(".json") }
                            .sortedByDescending { it.lastModified }
                    } catch (e: WebDavHttpException) {
                        // 404 = 备份目录尚不存在：不是错误，视为无备份文件
                        if (e.code == 404) {
                            emptyList()
                        } else {
                            throw IllegalStateException(e.friendlyMessage)
                        }
                    }
                }
                _webDavBackupFiles.value = files
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "加载备份文件列表失败", e)
                _webDavBackupError.value = "加载失败: ${e.toUserMessage()}"
            } finally {
                _webDavBackupLoading.value = false
            }
        }
    }

    /** 从所选 WebDAV 服务器下载备份文件并恢复（默认 MERGE 模式，保留本机独有数据）。 */
    fun restoreFromWebDav(libraryId: Int, fileName: String, mode: RestoreMode = RestoreMode.MERGE) {
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
                val summary = backupManager.importFromJson(json, mode)
                _state.value = BackupUiState.ImportSuccess(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WebDAV 恢复失败", e)
                _state.value = BackupUiState.Error("WebDAV 恢复失败: ${e.toUserMessage()}")
            }
        }
    }

    fun import(resolver: ContentResolver, uri: Uri, mode: RestoreMode = RestoreMode.MERGE) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working
            try {
                val json = resolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("无法读取文件")
                val summary = backupManager.importFromJson(json, mode)
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

    /**
     * 清理当前设备在 WebDAV 备份目录中的旧备份，保留最新的 [keep] 份。
     *
     * 按文件名前缀 `niplayer_backup_{deviceTag}_` 筛选当前设备的备份，
     * 按修改时间倒序排序，删除超出的部分。清理失败不影响备份结果（仅记录日志）。
     */
    private suspend fun pruneDeviceBackups(storage: com.nichx.niplayer.storage.Storage, keep: Int) {
        try {
            val prefix = "niplayer_backup_${deviceTag()}_"
            val dir = object : AbstractStorageFile(WEBDAV_BACKUP_DIR, WEBDAV_BACKUP_DIR, true) {}
            val files = storage.listFiles(dir)
                .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".json") }
                .sortedByDescending { it.lastModified }
            if (files.size <= keep) return
            files.drop(keep).forEach { stale ->
                try {
                    storage.deleteFile(stale)
                } catch (e: Exception) {
                    Log.w(TAG, "删除旧备份 ${stale.name} 失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理旧备份失败（不影响本次备份）: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BackupViewModel"
        private const val WEBDAV_BACKUP_DIR = "NIplayer_backup"
        private const val MAX_BACKUPS_PER_DEVICE = 3

        /**
         * 备份文件名：包含设备短标识，便于多设备场景区分备份来源。
         * 格式: niplayer_backup_{deviceId前4位}_{yyyyMMdd_HHmmss}.json
         */
        fun defaultFileName(): String {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            return "niplayer_backup_${deviceTag()}_$ts.json"
        }

        /** 当前设备短标识（deviceId 前 4 位），用于备份文件名与旧备份清理筛选。 */
        fun deviceTag(): String = PlayHistorySyncSettings.deviceId
            .takeIf { it.isNotBlank() }
            ?.take(4)
            ?: "local"
    }
}

/** 将异常转为面向用户的中文提示，避免显示 "null" 或空字符串。 */
private fun Throwable.toUserMessage(): String = when (this) {
    is WebDavHttpException -> friendlyMessage
    is IllegalStateException -> message ?: "操作失败"
    else -> message ?: toString()
}
