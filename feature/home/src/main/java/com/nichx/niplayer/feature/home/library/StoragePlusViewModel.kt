package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.storage.StorageFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 添加 / 编辑存储源 ViewModel。
 *
 * 替代旧仓库 `StoragePlusActivity` + `StoragePlusViewModel` + 5 个 `StorageEditDialog`
 * 的组合。旧仓库按 [MediaType] 弹不同底部对话框，本仓库统一为单一全屏 Compose 表单，
 * 按 [StoragePlusUiState.mediaType] 切换可见字段。
 *
 * 模式：
 * - **新增**：路由 `type` 非空（[MediaType.value]），`storageId=0`，表单按类型填默认值
 * - **编辑**：路由 `type` 为空，`storageId>0`，从 [MediaLibraryDao] 加载已有配置回填
 *
 * 连接测试：构造临时 [MediaLibraryEntity] → [StorageFactory.create] →
 * [com.nichx.niplayer.storage.Storage.testConnection]，结果回写 [StoragePlusUiState.testResult]。
 *
 * 保存：先 [MediaLibraryDao.getByUrl] 去重（同 url + mediaType 且 id 不同视为冲突），
 * 再 [MediaLibraryDao.insert]（REPLACE 策略，编辑时沿用旧 id 覆写）。
 *
 * 已移除 Alist 支持（参见项目 memory）；External 不做连接测试（SAF 权限即访问凭证）。
 *
 * @param savedStateHandle 读取路由参数 `type` 与 `storageId`
 */
@HiltViewModel
class StoragePlusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val mediaLibraryDao: MediaLibraryDao,
    private val quickAccessDao: QuickAccessDao,
    private val playHistoryDao: PlayHistoryDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val storageFactory: StorageFactory,
) : ViewModel() {

    private val routeType: String = savedStateHandle.get<String>("type") ?: ""
    private val storageId: Int = savedStateHandle.get<Int>("storageId") ?: 0

    /** 是否编辑模式（storageId>0），Screen 据此显示删除按钮与标题。 */
    val isEditMode: Boolean get() = storageId > 0

    private val _uiState = MutableStateFlow(StoragePlusUiState())
    val uiState: StateFlow<StoragePlusUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StoragePlusEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StoragePlusEvent> = _events.asSharedFlow()

    init {
        if (storageId > 0) {
            loadExisting()
        } else {
            initNew()
        }
    }

    /** 编辑模式：加载已有存储源回填表单。 */
    private fun loadExisting() {
        viewModelScope.launch {
            val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(storageId) }
            if (library == null) {
                _events.tryEmit(StoragePlusEvent.ShowError(context.getString(R.string.storage_plus_library_missing)))
                _events.tryEmit(StoragePlusEvent.NavigateBack)
                return@launch
            }
            _uiState.value = library.toUiState()
        }
    }

    /** 新增模式：按路由 type 设置 mediaType 与默认端口。 */
    private fun initNew() {
        val type = MediaType.fromValue(routeType)
        _uiState.update {
            it.copy(
                mediaType = type,
                port = defaultPort(type),
                isAnonymous = false,
                webDavStrict = type == MediaType.WEBDAV_SERVER,
                smbV2 = type == MediaType.SMB_SERVER,
            )
        }
    }

    // ---- 字段更新 ----

    fun updateDisplayName(v: String) = _uiState.update { it.copy(displayName = v) }
    fun updateUrl(v: String) = _uiState.update {
        it.copy(url = v.trim())
    }
    fun updateWebDavUseHttps(v: Boolean) = _uiState.update {
        it.copy(webDavUseHttps = v)
    }
    fun updatePort(v: String) = _uiState.update {
        it.copy(port = v.trim().toIntOrNull() ?: 0)
    }
    fun updateAccount(v: String) = _uiState.update { it.copy(account = v) }
    fun updatePassword(v: String) = _uiState.update { it.copy(password = v) }
    fun updateDomain(v: String) = _uiState.update { it.copy(domain = v.trim()) }
    fun updateAnonymous(v: Boolean) = _uiState.update { it.copy(isAnonymous = v) }
    fun updateWebDavStrict(v: Boolean) = _uiState.update { it.copy(webDavStrict = v) }
    fun updateSmbSharePath(v: String) = _uiState.update { it.copy(smbSharePath = v.trim()) }
    fun updateSmbEncryption(v: Boolean) = _uiState.update { it.copy(smbEncryption = v) }
    fun updateExternalUri(v: String) = _uiState.update { it.copy(externalUri = v) }

    /** External 模式下 SAF 选定目录后，回填 displayName 兜底。 */
    fun ensureExternalDisplayName(fallback: String) {
        _uiState.update {
            if (it.displayName.isBlank()) it.copy(displayName = fallback) else it
        }
    }

    // ---- 连接测试 ----

    /** 测试连接。External 无需测试。 */
    fun testConnection() {
        val state = _uiState.value
        val error = validate(state)
        if (error != null) {
            _events.tryEmit(StoragePlusEvent.ShowError(error))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            // BUG-10 修复：testConnection 抛异常时 storage?.close() 不会执行，
            // 导致每次测试失败都泄漏一个 SMBClient + 部分建立的 Connection/Session。
            // 改为 try-finally 确保 storage 在任何路径下都被关闭。
            val library = buildLibrary(state)
            val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
            try {
                val ok = if (storage != null) {
                    withContext(Dispatchers.IO) { storage.testConnection() }
                } else false
                _uiState.update {
                    it.copy(isTesting = false, testResult = ok)
                }
            } catch (e: Exception) {
                Log.e("StoragePlusVM", "testConnection error", e)
                _uiState.update {
                    it.copy(isTesting = false, testResult = false)
                }
                _events.tryEmit(StoragePlusEvent.ShowError(e.message ?: context.getString(R.string.storage_plus_connect_failed)))
            } finally {
                withContext(Dispatchers.IO) { storage?.close() }
            }
        }
    }

    // ---- 保存 ----

    fun save() {
        val state = _uiState.value
        val error = validate(state)
        if (error != null) {
            _events.tryEmit(StoragePlusEvent.ShowError(error))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val library = buildLibrary(state)
                // 去重检查
                val dup = withContext(Dispatchers.IO) {
                    mediaLibraryDao.getByUrl(library.url, library.mediaType)
                }
                if (dup != null && dup.id != library.id) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.tryEmit(StoragePlusEvent.ShowError(context.getString(R.string.storage_plus_duplicate_url)))
                    return@launch
                }
                withContext(Dispatchers.IO) { mediaLibraryDao.insert(library) }
                _events.tryEmit(StoragePlusEvent.Saved)
                _events.tryEmit(StoragePlusEvent.NavigateBack)
            } catch (e: Exception) {
                Log.e("StoragePlusVM", "save error", e)
                _uiState.update { it.copy(isSaving = false) }
                _events.tryEmit(StoragePlusEvent.ShowError(e.message ?: context.getString(R.string.storage_plus_save_failed)))
            }
        }
    }

    // ---- 删除（仅编辑模式，按主键级联） ----

    fun delete() {
        if (storageId <= 0) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 级联删除关联数据
                    quickAccessDao.deleteByLibrary(storageId)
                    playHistoryDao.deleteByStorageId(storageId)
                    downloadTaskDao.deleteByStorageId(storageId)
                    // 最后删除存储源本身
                    mediaLibraryDao.deleteById(storageId)
                }
                _events.tryEmit(StoragePlusEvent.NavigateBack)
            } catch (e: Exception) {
                Log.e("StoragePlusVM", "delete error", e)
                _events.tryEmit(StoragePlusEvent.ShowError(e.message ?: context.getString(R.string.storage_plus_delete_failed)))
            }
        }
    }

    // ---- 校验 ----

    private fun validate(state: StoragePlusUiState): String? {
        return when (state.mediaType) {
            MediaType.WEBDAV_SERVER -> {
                if (state.url.isBlank()) context.getString(R.string.storage_plus_enter_url)
                else if (!state.isAnonymous &&
                    (state.account.isBlank() || state.password.isBlank())
                ) context.getString(R.string.storage_plus_enter_account)
                else null
            }

            MediaType.SMB_SERVER -> {
                if (state.url.isBlank()) context.getString(R.string.storage_plus_enter_ip)
                else if (!state.isAnonymous &&
                    (state.account.isBlank() || state.password.isBlank())
                ) context.getString(R.string.storage_plus_enter_account)
                else null
            }

            MediaType.EXTERNAL_STORAGE -> {
                if (state.externalUri.isBlank()) context.getString(R.string.storage_plus_select_root)
                else null
            }

            else -> context.getString(R.string.storage_plus_unsupported_type)
        }
    }

    // ---- 构造实体 ----

    private fun buildLibrary(state: StoragePlusUiState): MediaLibraryEntity {
        val id = if (storageId > 0) storageId else 0
        // 密码明文存储（v13 起，移除 PasswordVault 加密）
        val plainPassword = if (state.isAnonymous) null else state.password.ifBlank { null }
        return when (state.mediaType) {
            MediaType.WEBDAV_SERVER -> {
                val protocol = if (state.webDavUseHttps) "https://" else "http://"
                val fullUrl = protocol + state.url
                MediaLibraryEntity(
                    id = id,
                    displayName = state.displayName.ifBlank { context.getString(R.string.storage_plus_default_webdav_name) },
                    url = fullUrl,
                    mediaType = MediaType.WEBDAV_SERVER,
                    account = if (state.isAnonymous) null else state.account,
                    password = plainPassword,
                    isAnonymous = state.isAnonymous,
                    describe = fullUrl,
                    webDavStrict = state.webDavStrict,
                )
            }

            MediaType.SMB_SERVER -> {
                val port = if (state.port <= 0) 445 else state.port
                MediaLibraryEntity(
                    id = id,
                    displayName = state.displayName.ifBlank { context.getString(R.string.storage_plus_default_smb_name) },
                    url = state.url,
                    mediaType = MediaType.SMB_SERVER,
                    account = if (state.isAnonymous) null else state.account,
                    password = plainPassword,
                    isAnonymous = state.isAnonymous,
                    // BUG-32：域/工作组，匿名时忽略
                    domain = if (state.isAnonymous) null
                    else state.domain.ifBlank { null },
                    port = port,
                    describe = "smb://${state.url}",
                    smbV2 = state.smbV2,
                    smbSharePath = state.smbSharePath.ifBlank { null },
                    smbEncryption = state.smbEncryption,
                )
            }

            MediaType.EXTERNAL_STORAGE -> {
                MediaLibraryEntity(
                    id = id,
                    displayName = state.displayName.ifBlank { context.getString(R.string.storage_plus_default_external_name) },
                    url = state.externalUri,
                    mediaType = MediaType.EXTERNAL_STORAGE,
                    describe = state.externalUri,
                )
            }

            else -> error("unsupported mediaType: ${state.mediaType}")
        }
    }

    private fun defaultPort(type: MediaType): Int = when (type) {
        MediaType.SMB_SERVER -> 445
        else -> 0
    }

    private fun MediaLibraryEntity.toUiState(): StoragePlusUiState {
        val (strippedUrl, useHttps) = if (mediaType == MediaType.WEBDAV_SERVER) {
            val https = url.startsWith("https://", true)
            val stripped = url.removePrefix("http://").removePrefix("https://")
            stripped to https
        } else {
            url to false
        }
        return StoragePlusUiState(
            displayName = displayName,
            url = strippedUrl,
            mediaType = mediaType,
            account = account.orEmpty(),
            password = password.orEmpty(),
            isAnonymous = isAnonymous,
            port = port,
            webDavStrict = webDavStrict,
            webDavUseHttps = useHttps,
            smbV2 = smbV2,
            smbSharePath = smbSharePath.orEmpty(),
            smbEncryption = smbEncryption,
            domain = domain.orEmpty(),
            externalUri = if (mediaType == MediaType.EXTERNAL_STORAGE) url else "",
        )
    }
}

/** 存储源表单状态，涵盖 WebDAV / SMB / External 全部字段。 */
data class StoragePlusUiState(
    val displayName: String = "",
    val url: String = "",
    val mediaType: MediaType = MediaType.SMB_SERVER,
    val account: String = "",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val port: Int = 0,
    val webDavStrict: Boolean = true,
    val webDavUseHttps: Boolean = false,
    val smbV2: Boolean = true,
    val smbSharePath: String = "",
    val smbEncryption: Boolean = false,
    val domain: String = "",
    val externalUri: String = "",
    val isTesting: Boolean = false,
    val testResult: Boolean? = null,
    val isSaving: Boolean = false,
)

/** 一次性事件。 */
sealed class StoragePlusEvent {
    object Saved : StoragePlusEvent()
    object NavigateBack : StoragePlusEvent()
    data class ShowError(val message: String) : StoragePlusEvent()
}
