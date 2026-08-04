package com.nichx.niplayer.feature.home.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.PlaylistDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.entity.PlaylistEntity
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.database.security.EncryptedFolderManager
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.LrcApiSettings
import com.nichx.niplayer.datastore.SortConfig
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.imageviewer.ImageViewerRequest
import com.nichx.niplayer.feature.home.imageviewer.ImageViewerRequestHolder
import com.nichx.niplayer.player.kernel.HistoryDescriptor
import com.nichx.niplayer.player.kernel.MediaSourceBuilder
import com.nichx.niplayer.player.kernel.PlaybackRequest
import com.nichx.niplayer.player.kernel.PlaybackRequestHolder
import com.nichx.niplayer.player.kernel.PlaylistHolder
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.isAudioFile
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.storage.download.DownloadManager
import com.nichx.niplayer.storage.impl.WebDavHttpException
import com.nichx.niplayer.thumbnail.ThumbnailManager
import com.nichx.niplayer.thumbnail.ThumbnailResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * 文件浏览页 ViewModel。
 *
 * 从 [LibraryScreen] 点击存储源进入，列出根目录文件，支持逐级进入子目录、返回上级、
 * 点击视频文件构造播放源并导航到 [com.nichx.niplayer.feature.player.PlayerScreen]。
 *
 * 替代旧仓库 `StorageFileActivity` + `StorageFileViewModel` + `StorageFileFragment` 的
 * Fragment 堆栈式目录管理，改用 ViewModel 内 [directoryStack] 维护目录层级。
 *
 * 播放源构造委托 [MediaSourceBuilder.buildMediaSource]（按 [Storage.createPlayUrl] 返回值
 * 分流 Http / Local / DataSource），构造好的 [NxMediaSource] 经 [PlaybackRequestHolder]
 * 传递给 :feature:player。
 *
 * 播放历史（P1）：
 * - [playFile] 时构造 [HistoryDescriptor]（uniqueKey = `"${library.id}:${file.path}"`）
 *   填充到 [PlaybackRequest.history]，PlayerViewModel 据此写回 play_history 表
 * - [playFile] 时查询 [PlayHistoryDao.getPlayHistory] 获取续播位置，设置到
 *   [PlaybackRequest.startPositionMs]，实现"接着上次看"
 *
 * @param savedStateHandle 由 Navigation Compose hiltViewModel 自动注入，读取路由参数 `storageId`
 */
@HiltViewModel
class StorageFileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val storageFactory: StorageFactory,
    private val mediaLibraryDao: MediaLibraryDao,
    private val playHistoryDao: PlayHistoryDao,
    private val quickAccessDao: QuickAccessDao,
    private val playbackRequestHolder: PlaybackRequestHolder,
    private val imageViewerRequestHolder: ImageViewerRequestHolder,
    private val playlistHolder: PlaylistHolder,
    private val thumbnailManager: ThumbnailManager,
    private val downloadManager: DownloadManager,
    private val encryptedFolderManager: EncryptedFolderManager,
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
) : ViewModel() {

    private var storageId: Int = savedStateHandle.get<Int>("storageId") ?: 0
    private var initialized = false

    /**
     * Initialize the ViewModel with explicit parameters (used by file browser overlay).
     * When called from a navigation route, SavedStateHandle already provides storageId.
     */
    fun initialize(storageId: Int, initialPath: String = "") {
        if (initialized) return
        initialized = true
        this.storageId = storageId
        _initialPath = initialPath
        loadRoot()
        // 收集本存储源的加密文件夹配置（锁定角标）
        viewModelScope.launch {
            encryptedFolderManager.getEncryptedFlow(storageId).collect { list ->
                _encryptedPaths.value = list.map { it.folderPath.trimEnd('/') }.toSet()
            }
        }
    }

    /**
     * W-N1 / W-N12 修复：将异常转换为面向用户的中文错误提示。
     *
     * - [WebDavHttpException]：使用 [WebDavHttpException.friendlyMessage]
     *   按 HTTP 响应码分类（401 账号密码错误 / 403 无权限 / 404 不存在 / 5xx 服务器异常）
     * - [java.net.UnknownHostException] / [java.net.SocketTimeoutException]：网络异常提示
     * - 其他：回退到 e.message 或通用错误
     */
    private fun Throwable.toFriendlyMessage(): String = when (this) {
        is WebDavHttpException -> friendlyMessage
        is java.net.UnknownHostException -> "无法连接到服务器，请检查网络或地址"
        is java.net.SocketTimeoutException -> "连接超时，请检查网络或服务器响应"
        is java.net.ConnectException -> "连接被拒绝，请检查服务器是否运行"
        else -> message ?: "未知错误"
    }

    /** 当前 Storage 实例，loadRoot 成功后赋值。 */
    private var storage: Storage? = null

    /** 当前存储源实体，playFile 时用于构造 HistoryDescriptor。 */
    private var currentLibrary: MediaLibraryEntity? = null

    /** 初始路径（overlay 模式从 composable 传入），loadRoot 完成后自动跳转。 */
    private var _initialPath: String = ""

    /** 目录栈：记录已进入的目录，栈底为根目录。用于 [goUp] 返回上级。 */
    private val directoryStack = ArrayDeque<StorageFile>()

    /**
     * 目录入口路径栈：与 [directoryStack] 同步（少 root），记录进入每一级子目录时
     * 点击的文件夹路径。返回上级目录时据此定位 scroll 目标位置。
     * - entryPathStack[i] 对应从 directoryStack[i] 进入 directoryStack[i+1] 的点击项路径
     */
    private val entryPathStack = ArrayDeque<String>()

    private val _uiState = MutableStateFlow(StorageFileUiState(isLoading = true))
    val uiState: StateFlow<StorageFileUiState> = _uiState.asStateFlow()

    /** 视频文件路径 → 可播放 URI（用于 Coil 加载缩略图）。目录切换时清空。 */
    private val _thumbnailUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val thumbnailUrls: StateFlow<Map<String, String>> = _thumbnailUrls.asStateFlow()

    /** 视频时长过短（< 15s）的文件路径集合，UI 显示 "<15s" 标识。目录切换时清空。 */
    private val _tooShortPaths = MutableStateFlow<Set<String>>(emptySet())
    val tooShortPaths: StateFlow<Set<String>> = _tooShortPaths.asStateFlow()

    /** 缩略图生成进度（0-100），-1 表示未在生成。 */
    private val _thumbnailProgress = MutableStateFlow(-1)
    val thumbnailProgress: StateFlow<Int> = _thumbnailProgress.asStateFlow()

    /** 活跃下载任务数（WAITING + DOWNLOADING），> 0 时顶栏显示下载按钮角标。 */
    val activeDownloadCount: StateFlow<Int> = downloadManager.activeDownloadCount

    /** 排序配置，从 [FileBrowserSettings] 持久化读取，UI 可 collect 展示当前排序态。 */
    val sortConfig: StateFlow<SortConfig> = FileBrowserSettings.sortFlow

    /**
     * 连接健康状态：远程存储的心跳检测结果。
     *
     * 仅远程存储（SMB/WebDAV）启用心跳。心跳每 [HEARTBEAT_INTERVAL_MS] 执行一次，
     * 通过 [Storage.ping] 验证连接是否仍然可达。
     * UI 层据此在顶栏显示连接状态指示器。
     */
    private val _connectionHealthy = MutableStateFlow<Boolean?>(null)
    val connectionHealthy: StateFlow<Boolean?> = _connectionHealthy.asStateFlow()

    /** 下拉刷新状态：true 时 UI 显示刷新指示器。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 心跳检测 Job，存储源切换或 ViewModel 销毁时取消。 */
    private var heartbeatJob: Job? = null

    /** 本存储源已加密的文件夹路径集合（锁定角标用），响应式：加密配置变更后自动更新。 */
    private val _encryptedPaths = MutableStateFlow<Set<String>>(emptySet())
    val encryptedPaths: StateFlow<Set<String>> = _encryptedPaths.asStateFlow()

    /** 待解锁的加密文件夹（进入目录被拦截时设置，密码对话框提交后消费）。 */
    private val _pendingUnlockFolder = MutableStateFlow<StorageFile?>(null)
    val pendingUnlockFolder: StateFlow<StorageFile?> = _pendingUnlockFolder.asStateFlow()

    /** 解锁密码错误提示：非空时解锁对话框内联显示错误。 */
    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    /** 清除解锁密码错误（对话框输入变更时调用）。 */
    fun clearUnlockError() {
        _unlockError.value = null
    }

    // ---- 多选模式（长按进入，供批量添加到歌单 / 批量删除）----

    /** 是否处于多选模式。 */
    private val _isMultiSelect = MutableStateFlow(false)
    val isMultiSelect: StateFlow<Boolean> = _isMultiSelect.asStateFlow()

    /** 已选中的文件路径集合。 */
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    /** 全量歌单及条目数（选歌单弹窗用）。 */
    val playlists: StateFlow<List<PlaylistWithCount>> = playlistDao.getAllWithCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 长按文件/目录进入多选模式并选中该项。 */
    fun enterMultiSelect(file: StorageFile) {
        _selectedPaths.value = setOf(file.path)
        _isMultiSelect.value = true
    }

    /** 多选模式下点击切换选中状态。 */
    fun toggleSelection(file: StorageFile) {
        val current = _selectedPaths.value
        _selectedPaths.value = if (file.path in current) current - file.path else current + file.path
    }

    /** 退出多选模式并清空选择。 */
    fun exitMultiSelect() {
        _selectedPaths.value = emptySet()
        _isMultiSelect.value = false
    }

    /** 全选当前目录中的文件（不含子目录，子目录不可入歌单）。 */
    fun selectAllFiles() {
        _selectedPaths.value = _uiState.value.files
            .filter { !it.isDirectory }
            .map { it.path }
            .toSet()
    }

    /**
     * 将选中文件批量加入歌单（入口②）。
     *
     * 歌单仅支持音频：非音频文件自动跳过；已存在（playlist_id, file_path）的自动去重。
     */
    fun addSelectedToPlaylist(playlistId: Int) {
        val library = currentLibrary ?: return
        val selected = _uiState.value.files.filter {
            it.path in _selectedPaths.value && !it.isDirectory && MediaFileTypes.isAudioFile(it.name)
        }
        if (selected.isEmpty()) {
            _events.tryEmit(StorageFileEvent.ShowToast("仅支持添加音频文件"))
            return
        }
        val entities = selected.map {
            PlaylistItemEntity(
                playlistId = playlistId,
                libraryId = library.id,
                filePath = it.path,
                fileName = it.name,
                mediaTypeValue = library.mediaType.value,
                fileSize = it.length,
            )
        }
        viewModelScope.launch {
            val inserted = withContext(Dispatchers.IO) {
                runCatching {
                    val count = playlistItemDao.addItems(playlistId, entities)
                    playlistDao.touch(playlistId, System.currentTimeMillis())
                    count
                }.getOrDefault(0)
            }
            exitMultiSelect()
            val skipped = selected.size - inserted
            val message = when {
                inserted > 0 && skipped > 0 ->
                    "已添加 $inserted 个条目到歌单，跳过 $skipped 个重复"
                inserted > 0 ->
                    "已添加 $inserted 个条目到歌单"
                else ->
                    "所选音频已全部在歌单中"
            }
            _events.tryEmit(StorageFileEvent.ShowToast(message))
        }
    }

    /**
     * 新建歌单并把选中音频文件加入其中（选歌单弹窗「新建歌单」路径）。
     */
    fun createPlaylistAndAdd(name: String) {
        val library = currentLibrary ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val selected = _uiState.value.files.filter {
            it.path in _selectedPaths.value && !it.isDirectory && MediaFileTypes.isAudioFile(it.name)
        }
        if (selected.isEmpty()) {
            _events.tryEmit(StorageFileEvent.ShowToast("仅支持添加音频文件"))
            return
        }
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                runCatching {
                    val playlistId = playlistDao.insert(PlaylistEntity(name = trimmed)).toInt()
                    val entities = selected.map {
                        PlaylistItemEntity(
                            playlistId = playlistId,
                            libraryId = library.id,
                            filePath = it.path,
                            fileName = it.name,
                            mediaTypeValue = library.mediaType.value,
                            fileSize = it.length,
                        )
                    }
                    playlistItemDao.addItems(playlistId, entities)
                }.getOrDefault(0)
            }
            exitMultiSelect()
            _events.tryEmit(StorageFileEvent.ShowToast(if (added > 0) "已创建歌单「$trimmed」并添加 $added 个条目" else "已创建歌单「$trimmed」"))
        }
    }

    /** 批量删除选中文件/目录。 */
    fun deleteSelected() {
        val s = storage ?: return
        val selected = _uiState.value.files.filter { it.path in _selectedPaths.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            var okCount = 0
            withContext(Dispatchers.IO) {
                selected.forEach { file ->
                    if (runCatching { s.deleteFile(file) }.getOrDefault(false)) {
                        okCount++
                        if (file.isDirectory) {
                            encryptedFolderManager.deleteFolderPrefix(storageId, file.path)
                        }
                    }
                }
            }
            exitMultiSelect()
            if (okCount == selected.size) {
                _events.tryEmit(StorageFileEvent.ShowToast("已删除 $okCount 项"))
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("部分删除失败（${okCount}/${selected.size}）"))
            }
            refreshCurrentDirectory()
        }
    }

    /** 取消解锁（对话框取消按钮）：清除待解锁文件夹与错误提示。 */
    fun cancelUnlock() {
        _pendingUnlockFolder.value = null
        _unlockError.value = null
    }

    private val _events = MutableSharedFlow<StorageFileEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StorageFileEvent> = _events.asSharedFlow()

    init {
        if (storageId > 0) initialize(storageId)
    }

    /** 加载存储源并列出根目录。 */
    private fun loadRoot() {
        viewModelScope.launch {
            val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(storageId) }
            if (library == null) {
                _uiState.update { it.copy(isLoading = false, error = "存储源不存在") }
                return@launch
            }
            try {
                val s = storageFactory.create(library)
                if (s == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "不支持的存储类型：${library.mediaType.storageName}")
                    }
                    return@launch
                }
                storage = s
                currentLibrary = library
                _uiState.update {
                    it.copy(
                        storageName = library.displayName,
                        isRemoteStorage = library.mediaType != MediaType.LOCAL_STORAGE
                                && library.mediaType != MediaType.EXTERNAL_STORAGE,
                    )
                }
                listDirectory(StorageFactory.ROOT) {
                    if (directoryStack.isEmpty()) directoryStack.addLast(StorageFactory.ROOT)
                }
                if (_initialPath.isNotEmpty()) {
                    navigateToPathSegments(_initialPath)
                }
                // 远程存储启用心跳检测
                startHeartbeat()
            } catch (e: Exception) {
                // W-C2 修复：原 catch 仅捕获 UnsupportedOperationException，
                // 但 WebDavStorage 构造时对非法 URL 抛 IllegalArgumentException，
                // SmbStorage 构造时也可能抛其他 RuntimeException，均会漏捕导致 UI 永久 loading。
                // 改为 catch (e: Exception) 兜底，确保任何初始化异常都能反馈给用户。
                // W-N1/W-N12 修复：用 toFriendlyMessage 中文化错误提示
                _uiState.update { it.copy(isLoading = false, error = e.toFriendlyMessage()) }
            }
        }
    }

    /**
     * 进入子目录。保存入口路径用于返回时定位滚动位置。
     *
     * 文件夹访问加密门禁：目录为加密根目录且未解锁时，不进入，
     * 改为设置 [pendingUnlockFolder]，UI 观察该 state 自动弹出解锁对话框。
     */
    fun openDirectory(file: StorageFile) {
        val sid = storageId
        viewModelScope.launch {
            if (encryptedFolderManager.isEncrypted(sid, file.path) &&
                !encryptedFolderManager.isUnlocked(sid, file.path)
            ) {
                _pendingUnlockFolder.value = file
                return@launch
            }
            entryPathStack.addLast(file.path)
            listDirectory(file) { directoryStack.addLast(file) }
        }
    }

    /**
     * 密码对话框提交：验证密码，成功则消费 [pendingUnlockFolder] 并进入目录，
     * 失败则设置 [unlockError] 供对话框内联提示（不关闭对话框）。
     *
     * @param password 用户输入的密码
     */
    fun submitFolderPassword(password: String) {
        val folder = _pendingUnlockFolder.value ?: return
        val sid = storageId
        viewModelScope.launch {
            if (encryptedFolderManager.unlockWithPassword(sid, folder.path, password)) {
                _pendingUnlockFolder.value = null
                _unlockError.value = null
                _events.tryEmit(StorageFileEvent.ShowToast("已解锁 ${folder.name}"))
                entryPathStack.addLast(folder.path)
                listDirectory(folder) { directoryStack.addLast(folder) }
            } else {
                _unlockError.value = "密码错误，请重试"
            }
        }
    }

    /** 为文件夹设置密码（加密）。 */
    fun encryptFolder(folder: StorageFile, password: String) {
        val sid = storageId
        viewModelScope.launch {
            encryptedFolderManager.setPassword(sid, folder.path, password)
            _events.tryEmit(StorageFileEvent.ShowToast("已加密 ${folder.name}，其中文件不再计入播放历史"))
        }
    }

    /** 取消加密（需验证当前密码）。 */
    fun decryptFolder(folder: StorageFile, password: String) {
        val sid = storageId
        viewModelScope.launch {
            if (encryptedFolderManager.removePassword(sid, folder.path, password)) {
                _events.tryEmit(StorageFileEvent.ShowToast("已取消加密 ${folder.name}"))
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("密码错误，请重试"))
            }
        }
    }

    /** 修改访问密码（需验证当前密码）。 */
    fun resetFolderPassword(folder: StorageFile, oldPassword: String, newPassword: String) {
        val sid = storageId
        viewModelScope.launch {
            if (encryptedFolderManager.changePassword(sid, folder.path, oldPassword, newPassword)) {
                _events.tryEmit(StorageFileEvent.ShowToast("已修改 ${folder.name} 的访问密码"))
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("当前密码错误，请重试"))
            }
        }
    }

    /**
     * 导航到指定初始路径（由快速访问书签等深层入口传入）。
     * 在 [loadRoot] 完成后依次进入路径的每一级子目录。
     * 路径为空时不执行任何操作（保持根目录）。
     * [loadRoot] 失败（storage 最终为 null）时静默跳过。
     */
    fun navigateToPath(path: String) {
        if (path.isEmpty()) return
        viewModelScope.launch {
            // 等待存储初始化完成：storage 就绪且目录加载结束。
            // 原实现仅等待 storage 非空，根目录列表未加载完就按空列表下钻，
            // 第一层目录匹配不到直接 break，导致快速访问深层跳转偶发失败。
            while ((storage == null || _uiState.value.isLoading) && _uiState.value.error == null) {
                delay(50)
            }
            if (storage != null) navigateToPathSegments(path)
        }
    }

    /**
     * 跳转到指定路径的每一级子目录。
     *
     * 修复：文件浏览已进入深层目录后，快速访问再跳转同一存储源的其他目录时，
     * 原实现在"当前目录的文件列表"里查找路径第一段，必然找不到直接 break，
     * 表现为点击快速访问无反应。现在：
     * - 目标与当前目录同分支（相等或当前目录是目标的祖先）→ 只下钻剩余层级
     * - 目标与当前目录不同分支 → 先清栈回到根目录，再逐级进入
     */
    private suspend fun navigateToPathSegments(path: String) {
        val segments = path.split("/").filter { it.isNotEmpty() }
        val currentPath = _uiState.value.currentPath.trimEnd('/')
        val targetPath = path.trimEnd('/')
        if (targetPath == currentPath) return

        val remainingSegments = if (currentPath.isEmpty() || targetPath.startsWith("$currentPath/")) {
            val currentDepth = if (currentPath.isEmpty()) 0
            else currentPath.split("/").count { it.isNotEmpty() }
            segments.drop(currentDepth)
        } else {
            resetToRoot()
            segments
        }
        for (segment in remainingSegments) {
            val dir = _uiState.value.rawFiles.firstOrNull { it.name == segment && it.isDirectory }
            if (dir == null) break
            // 文件夹访问加密门禁：深层跳转遇到未解锁的加密目录时同样拦截
            if (encryptedFolderManager.isEncrypted(storageId, dir.path) &&
                !encryptedFolderManager.isUnlocked(storageId, dir.path)
            ) {
                _pendingUnlockFolder.value = dir
                break
            }
            listDirectory(dir) { directoryStack.addLast(dir) }
        }
    }

    /** 回到根目录：清空目录栈并重新列出根目录。 */
    private suspend fun resetToRoot() {
        directoryStack.clear()
        entryPathStack.clear()
        listDirectory(StorageFactory.ROOT) { directoryStack.addLast(StorageFactory.ROOT) }
    }

    /**
     * 返回上级目录；已在根目录时不操作（由 UI 调用 onBack 退出页面）。
     *
     * 注意：不在此处修改目录栈，而是在 [listDirectory] 成功后才 removeLast，
     * 避免列目录失败时栈状态被破坏导致用户无法回到正确目录。
     */
    fun goUp() {
        if (directoryStack.size <= 1) return
        val parent = directoryStack[directoryStack.size - 2]
        val targetPath = if (entryPathStack.isNotEmpty()) entryPathStack.removeLast() else null
        viewModelScope.launch {
            listDirectory(parent) { if (directoryStack.size > 1) directoryStack.removeLast() }
            if (targetPath != null) {
                val targetIndex = _uiState.value.files.indexOfFirst { it.path == targetPath }
                if (targetIndex >= 0) {
                    _uiState.update { it.copy(scrollTargetIndex = targetIndex) }
                }
            }
        }
    }

    /**
     * 目录加载互斥锁。
     *
     * BUG-F6 修复：原 [listDirectory] 无同步，用户快速点击进入 A → B 时两个协程并发执行，
     * stackOp 对 [directoryStack] 的 addLast/removeLast 交错执行导致栈错乱
     * （如栈变为 [root, B, A] 而非 [root, B]）。
     *
     * 用 [Mutex] 串行化 listDirectory，确保同一时刻只有一个目录加载在执行，
     * stackOp 也串行执行，栈状态始终正确。
     */
    private val dirMutex = Mutex()

    /**
     * 缩略图生成世代计数器。
     *
     * BUG-50 修复：切目录时生成新世代，旧世代 cancelled 后 finally 块中的 flushBatch
     * 通过比对世代自动丢弃，避免旧目录的缩略图重新写入已清空的 _thumbnailUrls，
     * 造成新目录页面显示旧目录的缩略图（显示错乱）。
     */
    private val thumbnailGeneration = AtomicLong(0)

    /**
     * 当前目录缩略图生成任务。切目录前 cancel，避免旧目录生成占用资源 + 新目录叠加导致卡顿。
     *
     * 性能修复：原实现 [generateThumbnailUrls] 在 [dirMutex.withLock] 内部调用，
     * 必须等当前目录所有缩略图生成完才能释放 dirMutex，用户点子目录时被阻塞 → 界面卡死。
     * 现拆出独立 Job，listDirectory 只负责加载文件列表，立即释放 dirMutex。
     */
    private var thumbnailJob: Job? = null

    /**
     * 列出指定目录文件。
     *
     * 栈变更通过 [stackOp] lambda 表达，在列目录成功后执行，失败时保持原栈不变。
     *
     * BUG-F6 修复：用 [dirMutex] 串行化，避免并发加载导致目录栈竞态。
     *
     * @param stackOp 栈操作回调，在列目录成功后调用。常见模式：
     *  - 进入子目录：`{ directoryStack.addLast(directory) }`
     *  - 返回上级：`{ if (directoryStack.size > 1) directoryStack.removeLast() }`
     *  - 重试当前目录：`{ }`（不动栈）
     *  - 跳到指定深度：`{ while (directoryStack.size > targetSize) directoryStack.removeLast() }`
     */
    private suspend fun listDirectory(directory: StorageFile, stackOp: () -> Unit) {
        val s = storage ?: return
        // 性能修复：generateThumbnailUrls 移出 dirMutex.withLock，避免缩略图生成期间
        // dirMutex 被占用导致用户切目录请求被阻塞（界面卡死的根因）。
        // listDirectory 只负责加载文件列表，立即释放 dirMutex；缩略图生成用独立
        // thumbnailJob 后台异步进行，切目录前 cancel 旧 Job。
        val files = dirMutex.withLock {
            _uiState.update { it.copy(isLoading = true, error = null) }
            _thumbnailUrls.value = emptyMap()
            _tooShortPaths.value = emptySet()
            try {
                val fs = withContext(Dispatchers.IO) { s.listFiles(directory) }
                // 栈变更在列目录成功后执行，失败时保持原栈不变
                stackOp()
                _uiState.update {
                    it.copy(
                        rawFiles = fs,
                        files = applyFilterAndSort(fs),
                        currentPath = directory.path,
                        isLoading = false,
                        canGoUp = directoryStack.size > 1,
                    )
                }
                // 退出加密文件夹自动重新上锁：当前目录不再覆盖的已解锁加密根目录立即锁定
                encryptedFolderManager.reLockUncovered(storageId, directory.path)
                fs
            } catch (e: Exception) {
                // W-N1/W-N12 修复：用 toFriendlyMessage 中文化错误提示
                _uiState.update {
                    it.copy(isLoading = false, error = e.toFriendlyMessage())
                }
                null
            }
        } ?: return

        // dirMutex 已释放，启动缩略图生成（独立 Job，不阻塞 listDirectory 调用方）
        // 先 cancel 旧目录的生成任务，避免叠加导致 CPU/IO 抢占
        thumbnailJob?.cancel()
        val gen = thumbnailGeneration.incrementAndGet()
        thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
            generateThumbnailUrls(s, files, gen)
        }
    }

    /**
     * 重试加载当前目录（错误状态下点「重试」调用）。
     *
     * 若 [storage] 为 null（[loadRoot] 失败），重新执行初始化；
     * 否则重新列当前栈顶目录。
     */
    fun retryLoadCurrent() {
        if (storage == null || directoryStack.isEmpty()) {
            // storage 未初始化或目录栈为空（loadRoot 部分失败），重新执行初始化
            initialized = false
            initialized = true
            _uiState.update { it.copy(isLoading = true, error = null) }
            loadRoot()
            return
        }
        val current = directoryStack.lastOrNull() ?: return
        viewModelScope.launch { listDirectory(current) { } }
    }

    /**
     * 下拉刷新当前目录：重新加载栈顶目录的文件列表。
     *
     * 解决 SAF/网络存储外部更新后列表不刷新的问题（原实现需退出重新进入）。
     * 存储未初始化时回退到 [retryLoadCurrent] 重新初始化；
     * 刷新期间通过 [isRefreshing] 驱动 UI 显示下拉刷新指示器。
     */
    fun refresh() {
        if (storage == null || directoryStack.isEmpty()) {
            retryLoadCurrent()
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                listDirectory(directoryStack.last()) { }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 启动远程存储心跳检测。
     *
     * 每 [HEARTBEAT_INTERVAL_MS] 通过 [Storage.ping] 检测连接是否仍然可达。
     * ping 失败时更新 [_connectionHealthy] 为 false，UI 显示断开指示。
     * ping 成功时恢复为 true。
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        val s = storage ?: return
        val isRemote = currentLibrary?.mediaType?.let {
            it != MediaType.LOCAL_STORAGE && it != MediaType.EXTERNAL_STORAGE
        } ?: false
        if (!isRemote) {
            _connectionHealthy.value = null // 本地存储不需要心跳
            return
        }
        _connectionHealthy.value = true // 初始假设健康
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val healthy = withContext(Dispatchers.IO) { s.ping() }
                    _connectionHealthy.value = healthy
                } catch (_: Exception) {
                    _connectionHealthy.value = false
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _connectionHealthy.value = null
    }

    /**
     * 刷新当前目录的缩略图：清除本地缓存 → 清空状态 → 重新生成。
     *
     * 适用于用户手动点「刷新缩略图」按钮：
     * - 清空当前目录相关缩略图本地缓存（BUG-T-M4 修复：仅清当前目录，不清全应用）
     * - 重置 _thumbnailUrls / _tooShortPaths 状态
     * - 重新触发 [generateThumbnailUrls] 并发生成
     *
     * BUG-T-M4 修复：原实现调用 `thumbnailManager.clearCache()`（无参）会清空
     * `video_cover` / `audio_cover` / `image_thumb` / `seek_preview` 全部四个缓存目录，
     * 导致用户在某个 SMB 目录点"刷新"会清空其他存储源、其他目录、播放历史页已缓存的
     * 缩略图，全应用缩略图被强制重新生成。现改用 `clearCache(storageId, files)`
     * 细粒度清理，仅删除当前目录文件对应的本地缓存。
     */
    fun refreshThumbnails() {
        val s = storage ?: return
        val current = directoryStack.lastOrNull() ?: return
        val libId = currentLibrary?.id ?: return
        // 当前目录的文件列表快照（在协程外捕获，避免 listDirectory 重新加载后丢失）
        val filesToClear = _uiState.value.rawFiles
        viewModelScope.launch {
            // BUG-T-M4 修复：仅清当前目录相关缓存，不影响其他目录/存储源/播放历史
            withContext(Dispatchers.IO) { thumbnailManager.clearCache(libId, filesToClear) }
            // 清空状态
            _thumbnailUrls.value = emptyMap()
            _tooShortPaths.value = emptySet()
            // 重新列当前目录（触发 generateThumbnailUrls）
            listDirectory(current) { }
        }
    }

    /**
     * 跳到面包屑指定深度的目录（单次协程，避免多次 goUp 产生竞态）。
     *
     * @param targetDepth 目标在目录栈中的索引（0 = 根目录）。若等于当前栈顶索引则不操作。
     */
    fun jumpToDepth(targetDepth: Int) {
        if (targetDepth < 0 || targetDepth >= directoryStack.size) return
        if (targetDepth == directoryStack.size - 1) return
        val target = directoryStack[targetDepth]
        val exitedDirPath = directoryStack[targetDepth + 1].path
        viewModelScope.launch {
            listDirectory(target) {
                while (directoryStack.size > targetDepth + 1) directoryStack.removeLast()
            }
            // 同步 entryPathStack：移除目标层级之后的入口记录
            while (entryPathStack.size > targetDepth) entryPathStack.removeLast()
            val targetIndex = _uiState.value.files.indexOfFirst { it.path == exitedDirPath }
            if (targetIndex >= 0) {
                _uiState.update { it.copy(scrollTargetIndex = targetIndex) }
            }
        }
    }

    /** UI 消费完滚动目标后调用，重置 [StorageFileUiState.scrollTargetIndex]。 */
    fun clearScrollTarget() {
        _uiState.update { it.copy(scrollTargetIndex = -1) }
    }

    /**
     * 异步加载视频缩略图：先预加载服务端已有缓存，再并发生成缺失的缩略图。
     *
     * 两阶段策略：
     * 1. **预加载**：检查服务端 `.thumb/` 目录，并发下载已生成的缩略图到本地缓存
     *    （第二次浏览同一目录时，几十 ms/张 vs 生成 1-3s/张，数量级提升）
     * 2. **并发生成**：对预加载未命中的视频，用 [Storage.thumbnailConcurrency] 控制并发数
     *    生成新缩略图（SMB/WebDAV 6 并发）
     *
     * 生成完成后，异步上传到服务端 `.thumb/` 目录（跳过 LocalStorage），
     * 使其他设备访问同一存储时可直接下载，无需重新生成。
     *
     * 性能修复（卡顿根治）：
     * - **批量合并 + 节流**：原实现每生成一个就 `_thumbnailUrls.update`，100 个文件
     *   = 100 次 StateFlow emit + 100 次 Compose 重组 + 100 次 Map 全量拷贝。
     *   现用 batchAccumulator 累积结果，flusher 协程每 250ms 批量提交一次，
     *   100 个文件降至 4-5 次 emit。
     * - **进度按 5% 步进**：避免每个文件完成都触发进度 StateFlow emit。
     * - **上传并行化 + fire-and-forget**：原实现串行 uploadThumbnail 阻塞生成协程，
     *   现用 async + Semaphore 并发，且不等待上传完成即返回（上传失败不影响 UI）。
     */
    private suspend fun generateThumbnailUrls(s: Storage, files: List<StorageFile>, generation: Long = -1) {
        val videoFiles = files.filter { !it.isDirectory && MediaFileTypes.isVideoFile(it.name) }
        val audioFiles = files.filter { !it.isDirectory && MediaFileTypes.isAudioFile(it.name) }
        val imageFiles = files.filter { !it.isDirectory && MediaFileTypes.isImageFile(it.name) }
        if (videoFiles.isEmpty() && audioFiles.isEmpty() && imageFiles.isEmpty()) return
        val libId = currentLibrary?.id ?: return
        // 存储源生效策略检查：关闭模式跳过全部（含缓存命中与 preload）；
        // "仅播放后生成"模式保留缓存命中与服务端 preload，但跳过浏览时批量生成
        val mode = ThumbnailSettings.effectiveMode(libId)
        val browseGenerationAllowed = mode == ThumbnailGenerationMode.ALL
        if (mode == ThumbnailGenerationMode.OFF) return
        val isLocal = s.library.mediaType == MediaType.LOCAL_STORAGE

        withContext(Dispatchers.IO) {
            // ---- 批量合并 + 节流机制 ----
            // 累积缩略图结果，由 flusher 协程定期批量提交到 _thumbnailUrls
            val batchAccumulator = mutableMapOf<String, String>()
            // 用 synchronized 而非 Mutex：onLoaded 回调是非 suspend lambda，不能调 withLock；
            // 且持有时间极短（仅 map put/get/clear），synchronized 在 IO 线程上无影响。
            val batchLock = Any()
            // flusher 协程：每 250ms 把累积结果批量提交，大幅减少 StateFlow emit 次数
            val flusher = launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flushBatch(batchAccumulator, batchLock)
                }
            }

            var completed = 0
            var totalCount = 0
            // 进度按 5% 步进，避免每个文件完成都触发 emit
            var lastProgressStep = -1
            fun reportProgress() {
                if (totalCount <= 0) return
                val current = (completed * 100) / totalCount
                val stepped = (current / PROGRESS_STEP) * PROGRESS_STEP
                // 步进变化或完成时才 emit
                if (stepped != lastProgressStep || current >= 100) {
                    lastProgressStep = stepped
                    _thumbnailProgress.value = if (current >= 100) 100 else stepped
                }
            }

            try {
                // ---- 图片缩略图 ----
                if (imageFiles.isNotEmpty() &&
                    browseGenerationAllowed &&
                    ThumbnailSettings.generateThumbnail && ThumbnailSettings.generateForImage
                ) {
                    // BUG-T-m4 修复：先扫描本地缓存，已命中的立即可用（与视频组/音频组对齐）
                    // 原实现直接 launch 协程调用 generateImageThumbnail，每个文件都要协程调度 +
                    // 获取 mutex + 检查缓存，100 张图片 = 100 次协程 launch 仅为了命中已存在的缓存
                    val cachedImages = imageFiles.mapNotNull { file ->
                        val path = thumbnailManager.getCachedImageThumbnailPath(libId, file.path)
                        if (path != null) file.path to path else null
                    }.toMap()
                    if (cachedImages.isNotEmpty()) {
                        synchronized(batchLock) { batchAccumulator.putAll(cachedImages) }
                        flushBatch(batchAccumulator, batchLock)
                    }

                    // 仅对未命中缓存的图片启动生成
                    val toGenerateImages = imageFiles.filter { file ->
                        _thumbnailUrls.value[file.path] == null &&
                            batchAccumulator[file.path] == null
                    }
                    totalCount += toGenerateImages.size
                    if (completed == 0 && totalCount > 0) {
                        _thumbnailProgress.value = 0
                        lastProgressStep = 0
                    }
                    if (toGenerateImages.isNotEmpty()) {
                        val imageConcurrency = minOf(s.thumbnailConcurrency, toGenerateImages.size)
                        val imageSemaphore = Semaphore(imageConcurrency)
                        coroutineScope {
                            for (file in toGenerateImages) {
                                launch {
                                    imageSemaphore.withPermit {
                                        try {
                                            val path = thumbnailManager.generateImageThumbnail(s, libId, file)
                                            if (path != null) {
                                                synchronized(batchLock) {
                                                    batchAccumulator[file.path] = path
                                                }
                                            }
                                        } catch (_: Exception) {
                                        }
                                        completed++
                                        reportProgress()
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- 音频封面 ----
                if (audioFiles.isNotEmpty()) {
                    // 扫描本地缓存（不受开关影响）
                    val cached = audioFiles.mapNotNull { file ->
                        val path = thumbnailManager.getCachedAudioCoverPath(libId, file.path)
                        if (path != null) file.path to path else null
                    }.toMap()
                    if (cached.isNotEmpty()) {
                        synchronized(batchLock) { batchAccumulator.putAll(cached) }
                        flushBatch(batchAccumulator, batchLock)
                    }

                    // BUG-T-M1 修复：第一步 - 预加载服务端 .cover/ 已生成的封面
                    // 与视频组 preloadThumbnails 对称，跨设备复用服务端缓存
                    val remainingFromCache = audioFiles.filter { file ->
                        _thumbnailUrls.value[file.path] == null &&
                            batchAccumulator[file.path] == null
                    }
                    if (remainingFromCache.isNotEmpty() && !isLocal) {
                        try {
                            thumbnailManager.preloadAudioCovers(
                                s, libId, remainingFromCache,
                                onLoaded = { audioPath, coverPath ->
                                    synchronized(batchLock) { batchAccumulator[audioPath] = coverPath }
                                },
                            )
                        } catch (_: Exception) {
                        }
                    }

                    // 第二步：本地提取内嵌封面（受生成模式与 generateForAudio 开关控制）
                    val toGenerate = if (browseGenerationAllowed &&
                        ThumbnailSettings.generateThumbnail && ThumbnailSettings.generateForAudio
                    ) {
                        audioFiles.filter { file ->
                            _thumbnailUrls.value[file.path] == null &&
                                batchAccumulator[file.path] == null &&
                                (if (LrcApiSettings.isConfigured) {
                                    // API 已配置：放行从未尝试过 API 的文件（即使有 no_cover）
                                    !thumbnailManager.hasNoCover(libId, file.path) ||
                                        !thumbnailManager.hasApiNoCover(libId, file.path)
                                } else {
                                    !thumbnailManager.hasNoCover(libId, file.path)
                                })
                        }
                    } else {
                        emptyList()
                    }
                    totalCount += toGenerate.size
                    if (toGenerate.isNotEmpty()) {
                        if (completed == 0 && totalCount > 0) {
                            _thumbnailProgress.value = 0
                            lastProgressStep = 0
                        }
                        val audioConcurrency = minOf(s.thumbnailConcurrency, toGenerate.size)
                        val audioSemaphore = Semaphore(audioConcurrency)
                        val successFiles = Collections.synchronizedList(mutableListOf<StorageFile>())
                        coroutineScope {
                            for (file in toGenerate) {
                                launch {
                                    audioSemaphore.withPermit {
                                        try {
                                            val path = thumbnailManager.generateAudioCover(s, libId, file)
                                            if (path != null) {
                                                synchronized(batchLock) {
                                                    batchAccumulator[file.path] = path
                                                }
                                                successFiles.add(file)
                                            }
                                        } catch (_: Exception) {
                                        }
                                        completed++
                                        reportProgress()
                                    }
                                }
                            }
                        }

                        // BUG-T-M1 修复：第三步 - 上传新生成的封面到服务端 .cover/
                        // 与视频组 uploadThumbnail 对称，跨设备复用
                        // uploadAudioCover 内部已应用 BUG-T-C1 fileExists 检查，不覆盖服务端已有文件
                        if (!isLocal && successFiles.isNotEmpty() && ThumbnailSettings.saveInSameDir) {
                            val uploadConcurrency = minOf(s.thumbnailConcurrency, successFiles.size)
                            val uploadSemaphore = Semaphore(uploadConcurrency)
                            launch {
                                coroutineScope {
                                    for (file in successFiles) {
                                        launch {
                                            uploadSemaphore.withPermit {
                                                try {
                                                    thumbnailManager.uploadAudioCover(s, file)
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- 视频缩略图 ----
                if (videoFiles.isNotEmpty()) {
                    // 第一步：扫描本地缓存，已存在的缩略图立即可用（不受开关影响）
                    val cached = videoFiles.mapNotNull { file ->
                        val path = thumbnailManager.getCachedThumbnailPath(libId, file.path)
                        if (path != null) file.path to path else null
                    }.toMap()
                    if (cached.isNotEmpty()) {
                        synchronized(batchLock) { batchAccumulator.putAll(cached) }
                        flushBatch(batchAccumulator, batchLock)
                    }

                    val remainingFromCache = videoFiles.filter { file ->
                        _thumbnailUrls.value[file.path] == null &&
                            batchAccumulator[file.path] == null &&
                            _tooShortPaths.value.contains(file.path).not()
                    }
                    if (remainingFromCache.isNotEmpty() && !isLocal) {
                        try {
                            // BUG-T-M6 修复：传入同目录全部文件（files 已是 listDirectory 返回值，
                            // 含 {name}-thumb.jpg 刮削缩略图），避免 preloadThumbnails 重复 listFiles
                            thumbnailManager.preloadThumbnails(
                                s, libId, remainingFromCache,
                                onLoaded = { videoPath, thumbPath ->
                                    synchronized(batchLock) { batchAccumulator[videoPath] = thumbPath }
                                },
                                sameDirFiles = files,
                            )
                        } catch (_: Exception) {
                        }
                    }

                    // 第二步：本地生成新缩略图（受生成模式与 generateForVideo 开关控制）
                    val toGenerate = if (browseGenerationAllowed &&
                        ThumbnailSettings.generateThumbnail && ThumbnailSettings.generateForVideo
                    ) {
                        videoFiles.filter { file ->
                            _thumbnailUrls.value[file.path] == null &&
                                batchAccumulator[file.path] == null &&
                                _tooShortPaths.value.contains(file.path).not()
                        }
                    } else {
                        emptyList()
                    }
                    totalCount += toGenerate.size
                    if (toGenerate.isNotEmpty()) {
                        _thumbnailProgress.value = 0
                        lastProgressStep = 0
                        val concurrency = minOf(s.thumbnailConcurrency, toGenerate.size)
                        val semaphore = Semaphore(concurrency)
                        val successFiles = Collections.synchronizedList(mutableListOf<StorageFile>())
                        coroutineScope {
                            for (file in toGenerate) {
                                launch {
                                    semaphore.withPermit {
                                        try {
                                            when (val result = thumbnailManager.generateThumbnail(
                                            s, libId, file,
                                            positionKey = ThumbnailSettings.framePositionKey,
                                            customSeconds = ThumbnailSettings.customPositionSeconds,
                                        )) {
                                                is ThumbnailResult.Success -> {
                                                    synchronized(batchLock) {
                                                        batchAccumulator[file.path] = result.path
                                                    }
                                                    successFiles.add(file)
                                                }
                                                is ThumbnailResult.TooShort -> {
                                                    _tooShortPaths.update { it + file.path }
                                                }
                                                is ThumbnailResult.Failed -> {
                                                }
                                                // W-M9 修复：401/403 凭证错误等永久失败，加入 _tooShortPaths
                                                // 复用"不重试"集合语义，避免每次刷新都无谓重试（凭据未变必再失败）
                                                is ThumbnailResult.PermanentFailure -> {
                                                    _tooShortPaths.update { it + file.path }
                                                }
                                            }
                                        } catch (_: Exception) {
                                        }
                                        completed++
                                        reportProgress()
                                    }
                                }
                            }
                        }

                        // 上传并行化 + fire-and-forget：不阻塞 generateThumbnailUrls 返回
                        // 原实现串行 uploadThumbnail，10 个文件 × 几秒 = 几十秒阻塞
                        if (!isLocal && successFiles.isNotEmpty() && ThumbnailSettings.saveInSameDir) {
                            val uploadConcurrency = minOf(s.thumbnailConcurrency, successFiles.size)
                            val uploadSemaphore = Semaphore(uploadConcurrency)
                            // launch 独立协程，不 await，生成协程立即返回
                            launch {
                                coroutineScope {
                                    for (file in successFiles) {
                                        launch {
                                            uploadSemaphore.withPermit {
                                                try {
                                                    thumbnailManager.uploadThumbnail(s, file)
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                // 停止 flusher，强制提交剩余累积结果
                flusher.cancel()
                // BUG-50 修复：仅当仍是当前世代时才提交，避免 cancelled 后旧目录的
                // 缩略图数据写入已清空的 _thumbnailUrls，造成页面显示错乱
                if (generation <= 0 || generation == thumbnailGeneration.get()) {
                    flushBatch(batchAccumulator, batchLock)
                }
                _thumbnailProgress.value = -1
            }
        }
    }

    /**
     * 把 [accumulator] 中累积的缩略图结果批量提交到 [_thumbnailUrls]。
     * 提交后清空 [accumulator]，等待下一批累积。
     */
    private fun flushBatch(
        accumulator: MutableMap<String, String>,
        lock: Any,
    ) {
        val batch = synchronized(lock) {
            if (accumulator.isEmpty()) return@synchronized null
            val snapshot = accumulator.toMap()
            accumulator.clear()
            snapshot
        } ?: return
        if (batch.isNotEmpty()) {
            _thumbnailUrls.update { it + batch }
        }
    }

    /**
     * 点击视频文件：构造播放源 → 查询续播位置 → 写入 [PlaybackRequestHolder] → 通知 UI 导航。
     *
     * 同时构造同目录视频文件列表写入 [PlaylistHolder]，供 PlayerViewModel 实现连播。
     */
    fun playFile(file: StorageFile) {
        val s = storage ?: return
        val library = currentLibrary ?: return
        viewModelScope.launch {
            try {
                val uniqueKey = "${library.id}:${file.path}"
                // W-N7 修复：传入 uniqueKey 作为 mediaId，与播放历史 uniqueKey 一致。
                val source = MediaSourceBuilder.buildMediaSource(s, file, mediaId = uniqueKey)

                // 文件夹访问加密双保险：加密目录内的文件不写播放历史（history = null 走现有"不记历史"机制）
                val withinEncrypted = encryptedFolderManager.isWithinEncrypted(library.id, file.path)

                // 查询续播位置
                val startPositionMs = withContext(Dispatchers.IO) {
                    playHistoryDao.getPlayHistory(uniqueKey, library.id)?.videoPosition ?: 0L
                }

                // 构造同目录播放列表（仅视频文件，按当前排序顺序）
                val playlist = buildPlaylist(file)
                val startIndex = playlist.indexOfFirst { it.filePath == file.path }
                if (startIndex >= 0) {
                    playlistHolder.set(playlist, startIndex)
                }

                playbackRequestHolder.set(
                    PlaybackRequest(
                        source = source,
                        title = file.name,
                        startPositionMs = startPositionMs,
                        history = if (withinEncrypted) null else HistoryDescriptor(
                            uniqueKey = uniqueKey,
                            url = file.path,
                            mediaTypeValue = library.mediaType.value,
                            storageId = library.id,
                            storagePath = file.path,
                            fileSize = file.length,
                        ),
                        isAudio = isAudioFile(file.name),
                    )
                )
                _events.tryEmit(StorageFileEvent.NavigateToPlayer)
            } catch (e: Exception) {
                _events.tryEmit(StorageFileEvent.ShowError(e.message ?: "无法打开播放源"))
            }
        }
    }

    /**
     * 构造同目录视频文件播放列表。
     *
     * 从当前 [StorageFileUiState.rawFiles] 筛选视频文件（按扩展名），转换为
     * [PlaylistItem] 列表。若当前目录无其他视频，返回空列表。
     */
    private fun buildPlaylist(currentFile: StorageFile): List<PlaylistItem> {
        val library = currentLibrary ?: return emptyList()
        // BUG-9 修复：原实现只过滤视频文件，导致音频播放列表始终为空、上下首按钮永远禁用。
        // 改为按"当前点击文件类型"过滤——点击音频则构建音频播放列表，点击视频则构建视频列表，
        // 避免音视频混播（用户在音频页点击下一首切到视频文件会导致 UI 错乱）
        val isAudio = MediaFileTypes.isAudioFile(currentFile.name)
        return _uiState.value.rawFiles
            .filter { sf ->
                !sf.isDirectory && (
                    (isAudio && MediaFileTypes.isAudioFile(sf.name)) ||
                        (!isAudio && MediaFileTypes.isVideoFile(sf.name))
                )
            }
            .map {
                PlaylistItem(
                    libraryId = library.id,
                    filePath = it.path,
                    fileName = it.name,
                    mediaTypeValue = library.mediaType.value,
                    // BUG-26：携带文件大小，切集时 createVirtualFile 传入真实 size
                    fileSize = it.length,
                )
            }
    }

    /**
     * 点击图片文件：写入 [ImageViewerRequestHolder] → 通知 UI 导航到图片查看页。
     *
     * 携带 storageId + 当前目录路径 + 点击的文件路径，[com.nichx.niplayer.feature.home
     * .imageviewer.ImageViewerViewModel] 据此重建 Storage 并列出同目录所有图片。
     */
    fun openImageFile(file: StorageFile) {
        val library = currentLibrary ?: return
        imageViewerRequestHolder.set(
            ImageViewerRequest(
                storageId = library.id,
                directoryPath = _uiState.value.currentPath,
                initialFilePath = file.path,
            )
        )
        _events.tryEmit(StorageFileEvent.NavigateToImageViewer)
    }

    // ---- 下载 ----

    /**
     * 下载文件到指定目标目录。
     *
     * - [targetStorageUrl] 为 SAF tree URI（`content://...`）时下载到用户选定目录；
     *   为 null 时下载到应用缓存目录（`<cache>/download/`）。
     * - uniqueKey 与播放历史保持一致（`"${library.id}:${file.path}"`），便于去重与关联。
     * - 任务去重由 [DownloadManager.addTask] 处理：已存在活跃任务时忽略，已结束任务重新插入。
     *
     * @param file 待下载文件（必须为非目录文件）
     * @param targetStorageUrl 目标存储 tree URI，null 表示下载到缓存
     * @param targetStorageName 目标存储显示名（用于下载管理页展示），null 时显示"缓存"
     */
    fun downloadFile(file: StorageFile, targetStorageUrl: String?, targetStorageName: String?) {
        val library = currentLibrary ?: return
        val uniqueKey = "${library.id}:${file.path}"
        downloadManager.addTask(
            storageId = library.id,
            filePath = file.path,
            fileName = file.name,
            uniqueKey = uniqueKey,
            totalBytes = file.length,
            targetStorageUrl = targetStorageUrl,
            targetStorageName = targetStorageName,
        )
        _events.tryEmit(StorageFileEvent.ShowToast("已添加到下载队列"))
    }

    /**
     * 设置下载目录并下载文件。
     * 用于用户首次下载时选择目录后，自动保存为下载目录并添加到存储源。
     */
    fun setDownloadDirAndDownload(file: StorageFile, treeUri: String, dirName: String) {
        setDownloadDir(treeUri, dirName)
        downloadFile(file, treeUri, dirName)
    }

    /**
     * 批量下载选中文件（多选模式）。
     *
     * 与 [downloadFile] 相同语义：仅非目录文件逐个加入下载队列，
     * uniqueKey 与播放历史保持一致，任务去重由 [DownloadManager.addTask] 处理。
     * 完成后退出多选模式。
     *
     * @param files 待下载文件列表（自动过滤目录）
     * @param targetStorageUrl 目标存储 tree URI，null 表示下载到缓存
     * @param targetStorageName 目标存储显示名，null 时显示"缓存"
     */
    fun downloadFiles(files: List<StorageFile>, targetStorageUrl: String?, targetStorageName: String?) {
        val library = currentLibrary ?: return
        val filesToDownload = files.filter { !it.isDirectory }
        if (filesToDownload.isEmpty()) return
        filesToDownload.forEach { file ->
            downloadManager.addTask(
                storageId = library.id,
                filePath = file.path,
                fileName = file.name,
                uniqueKey = "${library.id}:${file.path}",
                totalBytes = file.length,
                targetStorageUrl = targetStorageUrl,
                targetStorageName = targetStorageName,
            )
        }
        exitMultiSelect()
        _events.tryEmit(StorageFileEvent.ShowToast("已将 ${filesToDownload.size} 个文件添加到下载队列"))
    }

    /**
     * 设置下载目录并批量下载文件（多选模式）。
     * 用于用户首次批量下载时选择目录后，自动保存为下载目录并添加到存储源。
     */
    fun setDownloadDirAndDownloadFiles(files: List<StorageFile>, treeUri: String, dirName: String) {
        setDownloadDir(treeUri, dirName)
        downloadFiles(files, treeUri, dirName)
    }

    /** 保存下载目录并注册为外部存储源（首次下载目录选择时调用）。 */
    private fun setDownloadDir(treeUri: String, dirName: String) {
        DownloadSettings.setDownloadDir(treeUri, dirName)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = mediaLibraryDao.getByUrl(treeUri, MediaType.EXTERNAL_STORAGE)
                if (existing == null) {
                    mediaLibraryDao.insert(
                        MediaLibraryEntity(
                            displayName = dirName.ifBlank { "下载目录" },
                            url = treeUri,
                            mediaType = MediaType.EXTERNAL_STORAGE,
                            describe = treeUri,
                        )
                    )
                }
            }
        }
    }

    // ---- 快速访问（长按文件） ----

    /** 长按文件：查询是否已收藏，emit 菜单事件供 UI 弹出添加/移除选项。 */
    fun checkQuickAccess(file: StorageFile) {
        val library = currentLibrary ?: return
        viewModelScope.launch {
            val favorited = withContext(Dispatchers.IO) {
                quickAccessDao.get(library.id, file.path) != null
            }
            _events.tryEmit(StorageFileEvent.ShowQuickAccessMenu(file, favorited))
        }
    }

    /** 添加到快速访问。新增项排在列表末尾（sortIndex 取当前最大值 +1）。 */
    fun addQuickAccess(file: StorageFile) {
        val library = currentLibrary ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val nextIndex = (quickAccessDao.getMaxSortIndex() ?: -1) + 1
                quickAccessDao.insert(
                    QuickAccessEntity(
                        name = file.name,
                        storagePath = file.path,
                        isDirectory = file.isDirectory,
                        libraryId = library.id,
                        sortIndex = nextIndex,
                    )
                )
            }
            _events.tryEmit(StorageFileEvent.ShowToast("已添加到快速访问"))
        }
    }

    /** 从快速访问移除。 */
    fun removeQuickAccess(file: StorageFile) {
        val library = currentLibrary ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                quickAccessDao.delete(library.id, file.path)
            }
            _events.tryEmit(StorageFileEvent.ShowToast("已从快速访问移除"))
        }
    }

    // ---- 文件管理（重命名 / 移动 / 新建文件夹 / 删除） ----

    /**
     * 是否支持文件管理操作（重命名/移动/新建文件夹/删除）。
     *
     * 仅远程存储（SMB/WebDAV）支持；本地存储通过系统文件管理器操作。
     */
    val supportsFileManagement: Boolean
        get() = currentLibrary?.mediaType == MediaType.SMB_SERVER ||
            currentLibrary?.mediaType == MediaType.WEBDAV_SERVER

    /**
     * 重命名当前目录下的文件/目录。
     *
     * @param file 待重命名的文件（必须在当前目录内）
     * @param newName 新名称（不含路径）
     */
    fun renameFile(file: StorageFile, newName: String) {
        val s = storage ?: return
        if (newName.isBlank() || newName == file.name) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { s.rename(file, newName.trim()) }.getOrDefault(false)
            }
            if (ok) {
                _events.tryEmit(StorageFileEvent.ShowToast("已重命名为 ${newName.trim()}"))
                // 文件夹访问加密联动：目录重命名时同步更新加密配置前缀
                if (file.isDirectory) {
                    val oldPath = file.path.trimEnd('/')
                    val newPath = (file.path.substringBeforeLast('/', "").trimEnd('/') + "/" + newName.trim()).trimStart('/')
                    if (oldPath != newPath) {
                        encryptedFolderManager.renameFolderPrefix(storageId, oldPath, newPath)
                    }
                }
                refreshCurrentDirectory()
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("重命名失败，请检查名称是否合法或已存在"))
            }
        }
    }

    /**
     * 移动文件/目录到指定目标目录。
     *
     * @param file 待移动的文件
     * @param targetDirectory 目标目录（必须已存在）
     */
    fun moveFile(file: StorageFile, targetDirectory: StorageFile) {
        val s = storage ?: return
        if (file.path == targetDirectory.path) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { s.move(file, targetDirectory) }.getOrDefault(false)
            }
            if (ok) {
                _events.tryEmit(StorageFileEvent.ShowToast("已移动到 ${targetDirectory.name}"))
                refreshCurrentDirectory()
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("移动失败，目标可能已存在同名文件"))
            }
        }
    }

    /**
     * 在当前目录下新建文件夹。
     *
     * @param name 新文件夹名称
     */
    fun createFolder(name: String) {
        val s = storage ?: return
        if (name.isBlank()) return
        val currentDir = directoryStack.lastOrNull() ?: return
        val newPath = if (currentDir.path.isEmpty()) name.trim()
        else "${currentDir.path}/${name.trim()}"
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { s.createDirectory(newPath) }.getOrDefault(false)
            }
            if (ok) {
                _events.tryEmit(StorageFileEvent.ShowToast("已创建文件夹 ${name.trim()}"))
                refreshCurrentDirectory()
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("创建文件夹失败，可能已存在同名项"))
            }
        }
    }

    /**
     * 上传本地文件到当前目录。
     *
     * 通过 [ContentResolver] 打开本地 Uri 的输入流，调用 [Storage.uploadFile] 流式上传。
     * 上传完成后自动刷新当前目录。
     *
     * @param uri 本地文件 Uri（来自 SAF OpenDocument）
     * @param fileName 原始文件名（用于远程目标路径）
     */
    fun uploadFile(uri: android.net.Uri, fileName: String) {
        val s = storage ?: return
        val currentDir = directoryStack.lastOrNull() ?: return
        val remotePath = if (currentDir.path.isEmpty()) fileName
        else "${currentDir.path}/$fileName"
        viewModelScope.launch {
            _events.tryEmit(StorageFileEvent.ShowToast("正在上传 $fileName ..."))
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        s.uploadFile(remotePath, input)
                    } ?: false
                }.getOrDefault(false)
            }
            if (ok) {
                _events.tryEmit(StorageFileEvent.ShowToast("已上传 $fileName"))
                refreshCurrentDirectory()
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("上传失败，请检查网络或权限"))
            }
        }
    }

    /**
     * 删除文件或目录（目录需为空或可递归删除）。
     *
     * @param file 待删除的文件/目录
     */
    fun deleteFile(file: StorageFile) {
        val s = storage ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { s.deleteFile(file) }.getOrDefault(false)
            }
            if (ok) {
                _events.tryEmit(StorageFileEvent.ShowToast("已删除 ${file.name}"))
                // 文件夹访问加密联动：目录删除时清理其前缀下的加密配置
                if (file.isDirectory) {
                    encryptedFolderManager.deleteFolderPrefix(storageId, file.path)
                }
                refreshCurrentDirectory()
            } else {
                _events.tryEmit(StorageFileEvent.ShowError("删除失败，目录可能非空或无权限"))
            }
        }
    }

    /**
     * 列出可作为移动目标的候选目录（当前存储源下的子目录，排除待移动文件自身及其子目录）。
     *
     * 用于"移动到"选择器。为避免复杂递归，仅列出当前目录的子目录 + 返回上级目录。
     *
     * @param file 待移动的文件（用于排除自身）
     */
    suspend fun listMoveTargets(file: StorageFile): List<StorageFile> {
        val s = storage ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val targets = mutableListOf<StorageFile>()
            // 当前目录的子目录
            val current = directoryStack.lastOrNull() ?: return@withContext emptyList()
            runCatching {
                s.listFiles(current).filter { it.isDirectory && it.name != file.name }
            }.getOrDefault(emptyList()).let { targets.addAll(it) }
            // 上级目录（若非根）
            if (directoryStack.size > 1) {
                val parent = directoryStack[directoryStack.size - 2]
                targets.add(0, object : AbstractStorageFile(
                    path = parent.path,
                    name = "..",
                    isDirectory = true,
                    length = 0L,
                    lastModified = 0L,
                    isHidden = false,
                ) {})
            }
            targets
        }
    }

    /** 刷新当前目录文件列表（重命名/移动/删除/新建后调用）。 */
    private fun refreshCurrentDirectory() {
        val current = directoryStack.lastOrNull() ?: return
        viewModelScope.launch { listDirectory(current) { } }
    }

    // ---- 排序 ----

    /** 切换排序字段，持久化并立即重排当前列表。 */
    fun setSortBy(sortBy: FileBrowserSettings.SortBy) {
        FileBrowserSettings.setSortBy(sortBy)
        _uiState.update {
            it.copy(files = applyFilterAndSort(it.rawFiles))
        }
    }

    /** 切换升降序，持久化并立即重排当前列表。 */
    fun setSortAscending(ascending: Boolean) {
        FileBrowserSettings.setSortAscending(ascending)
        _uiState.update {
            it.copy(files = applyFilterAndSort(it.rawFiles))
        }
    }

    /** 切换"仅显示媒体文件"开关，持久化并立即刷新当前目录列表。 */
    fun toggleShowOnlyMediaFiles() {
        val newValue = !FileBrowserSettings.showOnlyMediaFiles
        FileBrowserSettings.showOnlyMediaFiles = newValue
        _uiState.update {
            it.copy(files = applyFilterAndSort(it.rawFiles))
        }
    }

    /** 切换"显示隐藏文件"开关，持久化并立即刷新当前目录列表。 */
    fun toggleShowHiddenFiles() {
        val newValue = !FileBrowserSettings.showHiddenFiles
        FileBrowserSettings.showHiddenFiles = newValue
        _uiState.update {
            it.copy(files = applyFilterAndSort(it.rawFiles))
        }
    }

    /**
     * 过滤 + 排序：先按 [FileBrowserSettings.showHiddenFiles] 过滤隐藏文件，
     * 再按 [FileBrowserSettings.showOnlyMediaFiles] 过滤非媒体文件，最后按 [FileBrowserSettings] 排序。
     *
     * 排序规则：目录始终在前；同类型内按 [SortConfig.sortBy] 排序，
     * [SortConfig.ascending] 控制升降序。名称排序不区分大小写。
     */
    private fun applyFilterAndSort(files: List<StorageFile>): List<StorageFile> {
        val config = FileBrowserSettings.sortFlow.value
        val hiddenFiltered = if (config.showHiddenFiles) {
            files
        } else {
            files.filter { !it.name.startsWith('.') && !it.isHidden }
        }
        val mediaFiltered = if (config.showOnlyMediaFiles) {
            hiddenFiltered.filter { it.isDirectory || isMediaFile(it) }
        } else {
            hiddenFiltered
        }

        val comparator = when (config.sortBy) {
            FileBrowserSettings.SortBy.NAME -> compareBy<StorageFile> { it.name.lowercase() }
            FileBrowserSettings.SortBy.MODIFIED -> compareBy<StorageFile> { it.lastModified }
            FileBrowserSettings.SortBy.SIZE -> compareBy<StorageFile> { it.length }
            FileBrowserSettings.SortBy.TYPE -> compareBy<StorageFile> {
                val dot = it.name.lastIndexOf('.')
                if (dot < 0 || dot == it.name.length - 1) "" else it.name.substring(dot + 1).lowercase()
            }
        }
        // 目录始终在前（不受升降序影响），同类型内按 comparator 排序
        val dirFirst = Comparator<StorageFile> { a, b ->
            val aDir = if (a.isDirectory) 0 else 1
            val bDir = if (b.isDirectory) 0 else 1
            aDir.compareTo(bDir)
        }
        val effective = if (config.ascending) comparator else comparator.reversed()
        return mediaFiltered.sortedWith(dirFirst.then(effective))
    }

    /**
     * 判断文件是否为媒体文件（视频/音频/图片），排除 sidecar 缩略图文件。
     *
     * BUG-T-m9 修复：当"仅显示媒体文件"开启时，侧车缩略图文件（如 `{name}-thumb.jpg`、
     * `{name}-cover.jpg`）仅扩展名是图片但实际是缩略图缓存，不应显示在文件列表中。
     */
    private fun isMediaFile(file: StorageFile): Boolean =
        !isSidecarThumbnailFile(file.name) && (
            MediaFileTypes.isVideoFile(file.name) ||
                MediaFileTypes.isAudioFile(file.name) ||
                MediaFileTypes.isImageFile(file.name)
            )

    /**
     * 判断文件名是否为 sidecar 缩略图/封面文件。
     *
     * 匹配 [ThumbnailManager.uploadThumbnail] / [ThumbnailManager.uploadAudioCover]
     * 生成的服务端缓存文件名模式：
     * - 视频缩略图：`{视频去扩展名}-thumb.jpg` / `-thumb.jpeg`
     * - 音频封面：`{完整文件名}-cover.jpg` / `-cover.jpeg`
     */
    private fun isSidecarThumbnailFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith("-thumb.jpg") || lower.endsWith("-thumb.jpeg") ||
            lower.endsWith("-cover.jpg") || lower.endsWith("-cover.jpeg")
    }

    override fun onCleared() {
        stopHeartbeat()
        val s = storage ?: return
        // BUG-X2 修复：close() 涉及网络 IO（SMB logout+disconnect），不能在主线程执行。
        // 原实现用裸 Thread 非守护，app 退出时 JVM 会等待该线程结束，
        // SMB close 涉及 share/session/connection 三层 close 可能耗时数秒，导致 app 卡死。
        // 改为守护线程（isDaemon=true），JVM 退出时不再等待，立即终止。
        // BUG-07 适配：close() 改为 suspend（需获取 connectMutex），用 runBlocking 在后台线程调用。
        Thread {
            try { kotlinx.coroutines.runBlocking { s.close() } } catch (_: Exception) { }
        }.apply { isDaemon = true }.start()
    }

    private companion object {
        /** 批量提交缩略图结果的间隔（ms）。降低 StateFlow emit 次数，减少 Compose 重组。 */
        const val FLUSH_INTERVAL_MS = 250L
        /** 进度按 5% 步进 emit，避免每个文件完成都触发进度 StateFlow 更新。 */
        const val PROGRESS_STEP = 5
        /** 远程存储心跳检测间隔（ms）。30 秒检测一次，平衡实时性与网络开销。 */
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}

/** 文件浏览页 UI 状态。 */
data class StorageFileUiState(
    val storageName: String = "",
    /** 原始文件列表（未过滤未排序），用于排序/过滤变更时重新计算 [files]。 */
    val rawFiles: List<StorageFile> = emptyList(),
    /** 当前展示的文件列表（已过滤 + 已排序）。 */
    val files: List<StorageFile> = emptyList(),
    val currentPath: String = "",
    val isLoading: Boolean = false,
    val canGoUp: Boolean = false,
    /** 列目录时的持续错误（加载失败显示）。播放错误走 [StorageFileEvent.ShowError]。 */
    val error: String? = null,
    /** 返回上级目录后需要滚动到的目标索引（-1 表示不滚动）。UI 消费后调用 [clearScrollTarget]。 */
    val scrollTargetIndex: Int = -1,
    /** 当前存储源是否为远程（SMB/WebDAV），远程文件可下载，本地文件不需要下载。 */
    val isRemoteStorage: Boolean = false,
)

/** 一次性事件（导航、Toast），由 [StorageFileScreen] collect。 */
sealed class StorageFileEvent {
    /** 播放源已就绪，导航到 [com.nichx.niplayer.navigation.Routes.Player.PLAYER]。 */
    object NavigateToPlayer : StorageFileEvent()

    /** 图片请求已就绪，导航到 [com.nichx.niplayer.navigation.Routes.ImageViewer.VIEWER]。 */
    object NavigateToImageViewer : StorageFileEvent()

    /** 播放源构造失败，显示错误提示。 */
    data class ShowError(val message: String) : StorageFileEvent()

    /** 长按文件后，UI 弹出快速访问菜单（添加/移除）。 */
    data class ShowQuickAccessMenu(
        val file: StorageFile,
        val isFavorited: Boolean,
    ) : StorageFileEvent()

    /** 简短提示（添加/移除成功）。 */
    data class ShowToast(val message: String) : StorageFileEvent()
}
