package com.nichx.niplayer.feature.player

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.subtitle.matcher.SubtitleMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 应用内字幕文件选择器的浏览状态。
 *
 * 取代系统 SAF 选择器（[androidx.activity.result.contract.ActivityResultContracts.OpenDocument]）：
 * SAF 只能访问被授权的外部存储文档，**完全看不到 SMB / WebDAV 网络存储**，
 * 而本应用的字幕恰恰大量存在于网络存储中。
 *
 * 两条浏览路径：
 * - **本地**：走 [java.io.File]（应用已申请 MANAGE_EXTERNAL_STORAGE）。
 *   不能用 [Storage]：本地库的 [com.nichx.niplayer.storage.impl.VideoStorage] 只列 Room
 *   `video` 表里的视频，字幕文件不在 MediaStore 索引内，列不出来。
 * - **存储库**：走 [Storage.listFiles]（SMB / WebDAV / SAF 文档树）。
 *
 * 与 [PlayerViewModel] 同样的取舍：**每次列举临时创建并关闭 [Storage]**，不复用长生命周期实例。
 * 代价是 SMB 每次进目录要重连（1–3 s），收益是不会留下未关闭的 session ——
 * 选择器是低频弹层，稳定性优先。
 */
@HiltViewModel
class SubtitlePickerViewModel @Inject constructor(
    private val mediaLibraryDao: MediaLibraryDao,
    private val storageFactory: StorageFactory,
) : ViewModel() {

    /** 根页面上的存储库入口。 */
    data class LibraryItem(val id: Int, val name: String)

    /** 目录项：子目录或字幕文件。 */
    data class Entry(val name: String, val isDirectory: Boolean, val length: Long)

    /** 当前浏览位置。 */
    sealed interface Location {
        /** 本地文件系统。 */
        data object Local : Location

        /** 某个存储库内的相对路径。 */
        data class Remote(val libraryId: Int, val libraryName: String) : Location
    }

    data class UiState(
        val loading: Boolean = false,
        val libraries: List<LibraryItem> = emptyList(),
        /** null 表示停在根页面（本地入口 + 存储库列表）。 */
        val location: Location? = null,
        /** 库内相对路径（[Location.Remote]）/ 绝对路径（[Location.Local]）。 */
        val path: String = "",
        val entries: List<Entry> = emptyList(),
        /** 列举失败提示；非致命，用户仍可返回上级。 */
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 本地浏览起始目录，由播放器按当前视频来源设置。 */
    var localStartPath: String = externalStorageRoot()

    /** 网络存储起始位置（库 ID + 库内目录），由播放器按当前视频来源设置。 */
    private var remoteStartLibraryId: Int? = null
    private var remoteStartPath: String = ""

    /** 播放器是否显式给过本地起始目录（决定打开时是否直接下钻）。 */
    private var hasExplicitLocalStart = false

    /**
     * 浏览代号：目录列举是异步的（SMB 可达数秒），用户快速连点目录时
     * 先发的请求可能后返回。每次新的浏览自增，回调侧比对后再落状态。
     *
     * 自增在主线程、比对在 IO 线程，故标 `@Volatile` 保证可见性。
     */
    @Volatile
    private var browseGeneration = 0

    init {
        viewModelScope.launch {
            mediaLibraryDao.getAll().collect { libraries ->
                val items = libraries
                    .filter { it.mediaType.isBrowsableLibrary() }
                    .map { LibraryItem(it.id, it.displayName) }
                _uiState.update { it.copy(libraries = items) }
            }
        }
    }

    /**
     * 配置弹层打开时的起始位置（由播放器按「当前视频所在目录」传入）。
     *
     * ViewModel 作用域挂在播放页导航条目上、实例跨弹层存活，所以每次打开都要重设；
     * null 表示该类来源无可定位信息。
     */
    fun configureStart(localPath: String?, remote: Pair<Int, String>?) {
        // 每次都要重设（含清空）：否则上一个视频的起始目录会残留到本次（例如从本地视频切到直链视频）
        if (localPath.isNullOrBlank()) {
            hasExplicitLocalStart = false
        } else {
            localStartPath = localPath
            hasExplicitLocalStart = true
        }
        remoteStartLibraryId = remote?.first
        remoteStartPath = remote?.second.orEmpty()
    }

    /**
     * 打开选择器：**直接落在当前视频所在目录**（网络源优先，其次本地）。
     *
     * 字幕通常就在视频旁边或相邻目录，从库根一层层翻过去代价太大。
     * 退路：浏览页首行是「上一级」，一路向上即可回到根页面换存储源。
     * 定位不到时保持原有行为，停在根页面（本地文件入口 + 存储源列表）。
     */
    fun openInitial() {
        val libraryId = remoteStartLibraryId
        if (libraryId != null) {
            val item = _uiState.value.libraries.firstOrNull { it.id == libraryId }
            if (item != null) {
                browseRemote(item, remoteStartPath)
                return
            }
        }
        if (hasExplicitLocalStart) {
            browseLocal(localStartPath)
            return
        }
        backToRoot()
    }

    /** 进入本地文件浏览（从 [localStartPath] 开始）。 */
    fun openLocal() = browseLocal(localStartPath)

    /** 进入某个存储库的根目录。 */
    fun openLibrary(libraryId: Int) {
        val item = _uiState.value.libraries.firstOrNull { it.id == libraryId } ?: return
        browseRemote(item, "")
    }

    /** 下钻到某个目录项。 */
    fun openEntry(entry: Entry) {
        if (!entry.isDirectory) return
        when (val location = _uiState.value.location) {
            null -> Unit
            Location.Local -> browseLocal(File(_uiState.value.path, entry.name).absolutePath)
            is Location.Remote -> {
                val child = joinPath(_uiState.value.path, entry.name)
                browseRemote(
                    LibraryItem(location.libraryId, location.libraryName),
                    child,
                )
            }
        }
    }

    /** 返回上一级；已在根页面时不动作。 */
    fun navigateUp() {
        val state = _uiState.value
        when (val location = state.location) {
            null -> Unit
            Location.Local -> {
                // 以外部存储根为上限，避免一路退到 `/`
                if (state.path == externalStorageRoot()) {
                    backToRoot()
                } else {
                    val parent = File(state.path).parentFile
                    if (parent == null || parent.absolutePath == state.path) backToRoot()
                    else browseLocal(parent.absolutePath)
                }
            }
            is Location.Remote -> {
                if (state.path.isEmpty()) {
                    backToRoot()
                } else {
                    browseRemote(
                        LibraryItem(location.libraryId, location.libraryName),
                        state.path.substringBeforeLast('/', missingDelimiterValue = ""),
                    )
                }
            }
        }
    }

    private fun backToRoot() {
        browseGeneration++
        _uiState.update {
            it.copy(location = null, path = "", entries = emptyList(), loading = false, error = null)
        }
    }

    private fun browseLocal(dirPath: String) {
        val generation = ++browseGeneration
        val target = File(dirPath).takeIf { it.isDirectory }
            ?: File(externalStorageRoot())
        _uiState.update {
            it.copy(
                location = Location.Local,
                path = target.absolutePath,
                loading = true,
                entries = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val entries = try {
                target.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { !it.isHidden }
                    .filter { it.isDirectory || SubtitleMatcher.isSubtitleFile(it.name) }
                    .map { Entry(it.name, it.isDirectory, if (it.isDirectory) 0L else it.length()) }
                    .sortedWith(DIR_FIRST_THEN_NAME)
                    .toList()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (generation != browseGeneration) return@launch
            _uiState.update { it.copy(loading = false, entries = entries) }
        }
    }

    private fun browseRemote(item: LibraryItem, path: String) {
        val generation = ++browseGeneration
        _uiState.update {
            it.copy(
                location = Location.Remote(item.id, item.name),
                path = path,
                loading = true,
                entries = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val (entries, error) = loadRemoteEntries(item.id, path)
            if (generation != browseGeneration) return@launch
            _uiState.update { it.copy(loading = false, entries = entries, error = error) }
        }
    }

    private suspend fun loadRemoteEntries(
        libraryId: Int,
        path: String,
    ): Pair<List<Entry>, String?> {
        val library = mediaLibraryDao.getById(libraryId)
            ?: return emptyList<Entry>() to ERROR_LIBRARY_MISSING
        val storage: Storage = storageFactory.create(library)
            ?: return emptyList<Entry>() to ERROR_NOT_BROWSABLE
        return try {
            val entries = storage.listFiles(storageFileFor(path))
                .asSequence()
                .filter { !it.isHidden && it.name.isNotEmpty() }
                .filter { it.isDirectory || SubtitleMatcher.isSubtitleFile(it.name) }
                .map { Entry(it.name, it.isDirectory, if (it.isDirectory) 0L else it.length) }
                .sortedWith(DIR_FIRST_THEN_NAME)
                .toList()
            entries to null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList<Entry>() to ERROR_LIST_FAILED
        } finally {
            // 同 PlayerViewModel.withStorage：清理必须在 NonCancellable 中执行，且 detekt 只接受
            // 裸 `withContext(NonCancellable) { … }`（外包 try/catch 会被报）。各 Storage 实现的
            // close 都是「尽力关闭、不抛」（AbstractStorage/WebDavStorage 空实现、SmbStorage 逐流
            // 吞异常），故这里无需也无法再吞异常。
            withContext(kotlinx.coroutines.NonCancellable) { storage.close() }
        }
    }

    /** 库内相对路径 → [StorageFile]；空路径用 [StorageFactory.ROOT]。 */
    private fun storageFileFor(path: String): StorageFile =
        if (path.isEmpty()) {
            StorageFactory.ROOT
        } else {
            object : AbstractStorageFile(
                path = path,
                name = path.substringAfterLast('/'),
                isDirectory = true,
            ) {}
        }

    companion object {
        /** 列举失败错误码，UI 层据此映射文案。 */
        const val ERROR_LIBRARY_MISSING = "library_missing"
        const val ERROR_NOT_BROWSABLE = "not_browsable"
        const val ERROR_LIST_FAILED = "list_failed"

        private val DIR_FIRST_THEN_NAME: Comparator<Entry> =
            compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() }

        private fun joinPath(parent: String, name: String): String =
            if (parent.isEmpty()) name else "$parent/$name"

        private fun externalStorageRoot(): String =
            Environment.getExternalStorageDirectory()?.absolutePath ?: "/"

        /** 可经 [Storage.listFiles] 浏览的库类型。 */
        private fun MediaType.isBrowsableLibrary(): Boolean = when (this) {
            MediaType.SMB_SERVER, MediaType.WEBDAV_SERVER, MediaType.EXTERNAL_STORAGE -> true
            // LOCAL_STORAGE 走 java.io.File；QUICK_ACCESS / OTHER_STORAGE 无 Storage 实例
            MediaType.LOCAL_STORAGE, MediaType.QUICK_ACCESS, MediaType.OTHER_STORAGE -> false
        }
    }
}
