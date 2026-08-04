package com.nichx.niplayer.feature.home.imageviewer

import com.nichx.niplayer.feature.home.R
import android.content.Context
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 图片查看页 ViewModel。
 *
 * 从 [ImageViewerRequestHolder] 消费请求后，重建 Storage 实例并列出同目录下所有图片文件，
 * 供 [ImageViewerScreen] 的 HorizontalPager 横向滑动浏览。
 *
 * 图片加载策略（按 [Storage.createPlayUrl] 返回值分流）：
 * - 非 null URL（Local / DocumentFile / WebDAV）→ [ImageModel.Url]（携带认证头）
 * - null（SMB）→ [Storage.openInputStream] 读取为 [ImageModel.Bytes]
 *
 * SMB 的 ByteArray 通过 [LruCache] 缓存（上限 32MB，按字节大小淘汰），避免反复网络请求。
 *
 * @param holder 跨模块传递的图片查看请求持有者
 * @param storageFactory 存储协议工厂，重建 Storage 实例
 * @param mediaLibraryDao 读取存储源配置
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: ImageViewerRequestHolder,
    private val storageFactory: StorageFactory,
    private val mediaLibraryDao: MediaLibraryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageViewerUiState(isLoading = true))
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    /** 当前 Storage 实例，loadImages 成功后赋值。 */
    private var storage: Storage? = null

    /** SMB 图片的 ByteArray 缓存（按文件路径索引，上限 32MB，按字节大小淘汰）。 */
    private val bytesCache = object : LruCache<String, ByteArray>(32 * 1024 * 1024) { // 32MB
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    init {
        loadImages()
    }

    /** 从 Holder 消费请求，重建 Storage，列出目录图片。 */
    private fun loadImages() {
        val request = holder.consume() ?: run {
            _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.image_viewer_invalid_request)) }
            return
        }

        viewModelScope.launch {
            try {
                val library = withContext(Dispatchers.IO) {
                    mediaLibraryDao.getById(request.storageId)
                }
                if (library == null) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.storage_plus_library_missing)) }
                    return@launch
                }

                val s = withContext(Dispatchers.IO) { storageFactory.create(library) }
                if (s == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = context.getString(R.string.storage_plus_unsupported_type))
                    }
                    return@launch
                }
                storage = s

                // 构造目录 StorageFile
                val dirFile = if (request.directoryPath.isEmpty()) {
                    StorageFactory.ROOT
                } else {
                    object : AbstractStorageFile(
                        path = request.directoryPath,
                        name = request.directoryPath.substringAfterLast('/'),
                        isDirectory = true,
                    ) {}
                }

                val allFiles = withContext(Dispatchers.IO) { s.listFiles(dirFile) }
                val images = allFiles
                    .filter { !it.isDirectory && MediaFileTypes.isImageFile(it.name) }
                    .sortedBy { it.name.lowercase() }

                if (images.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.image_viewer_no_images)) }
                    return@launch
                }

                val initialPosition = images.indexOfFirst { it.path == request.initialFilePath }
                    .coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        images = images,
                        initialPosition = initialPosition,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: context.getString(R.string.image_viewer_load_failed))
                }
            }
        }
    }

    /**
     * 加载单张图片为 [ImageModel]，供 Coil AsyncImage 显示。
     *
     * 先查 [bytesCache]（SMB 的 ByteArray 缓存），命中则直接返回。
     * 未命中时按 [Storage.createPlayUrl] 分流：URL 直接返回；null 则 openInputStream 读取。
     */
    suspend fun loadImage(file: StorageFile): ImageModel? {
        val s = storage ?: return null

        // 先查缓存
        bytesCache.get(file.path)?.let { return ImageModel.Bytes(it) }

        return withContext(Dispatchers.IO) {
            try {
                val playUrl = s.createPlayUrl(file)
                if (playUrl != null) {
                    val headers = if (playUrl.startsWith("http", ignoreCase = true)) {
                        s.getPlayHeaders()
                    } else {
                        emptyMap()
                    }
                    ImageModel.Url(playUrl, headers)
                } else {
                    // SMB：读取为 ByteArray
                    val bytes = s.openInputStream(file).use { it.readBytes() }
                    bytesCache.put(file.path, bytes)
                    ImageModel.Bytes(bytes)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun onCleared() {
        val s = storage ?: return
        // BUG-07 适配：close() 改为 suspend（需获取内部锁），用 runBlocking 在后台线程调用。
        // BUG-T-m2 修复：设置守护线程，避免 SMB close 涉及 share/session/connection 三层
        // close 耗时数秒时 JVM 等待该线程结束导致 app 退出卡死（与 StorageFileViewModel.onCleared 对齐）
        Thread {
            try { kotlinx.coroutines.runBlocking { s.close() } } catch (_: Exception) { }
        }.apply { isDaemon = true }.start()
    }
}

/** 图片加载结果（密封类，供 UI 层按类型构建 Coil model）。 */
sealed class ImageModel {
    /** URL 加载（Local content:// / DocumentFile file:// / WebDAV HTTP）。 */
    data class Url(
        val url: String,
        val headers: Map<String, String>,
    ) : ImageModel()

    /** ByteArray 加载（SMB，无可直接播放的 URL）。 */
    data class Bytes(val bytes: ByteArray) : ImageModel()
}

/** 图片查看页 UI 状态。 */
data class ImageViewerUiState(
    val images: List<StorageFile> = emptyList(),
    val initialPosition: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)
