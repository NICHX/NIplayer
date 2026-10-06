package com.nichx.niplayer.feature.home.imageviewer

import com.nichx.niplayer.feature.home.R
import android.content.Context
import android.util.LruCache
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.datastore.ExperimentalSettings
import com.nichx.niplayer.player.kernel.MediaSourceBuilder
import com.nichx.niplayer.player.kernel.NxPlayer
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.VideoSize
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFactory
import com.nichx.niplayer.storage.StorageFile
import com.nichx.niplayer.thumbnail.ThumbnailManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
 * 从 [ImageViewerRequestHolder] 读取请求（不消费，可安全重建），重建 Storage 实例并列出
 * 同目录下的媒体文件，供 [ImageViewerScreen] 的 HorizontalPager 横向滑动浏览。
 *
 * **图片+视频混合浏览**（[ExperimentalSettings.viewerVideoEnabled] 开启时）：
 * 同目录的视频会和图片按名称一起纳入列表；滑到视频页时内嵌 [NxPlayer] 自动静音播放，
 * 由浮层控件（播放/暂停、进度、静音）驱动。关闭开关则退回纯图片浏览。
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
 * @param player 内嵌视频播放内核（未加 @Singleton，本 ViewModel 独占一个实例）
 * @param thumbnailManager 查询视频缩略图缓存，用于非当前页的视频占位图
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: ImageViewerRequestHolder,
    private val storageFactory: StorageFactory,
    private val mediaLibraryDao: MediaLibraryDao,
    private val player: NxPlayer,
    private val thumbnailManager: ThumbnailManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageViewerUiState(isLoading = true))
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    /** 当前 Storage 实例，loadImages 成功后赋值。 */
    private var storage: Storage? = null

    /** 当前存储源 ID，用于查询缩略图缓存。 */
    private var libraryId: Int = -1

    /** SMB 图片的 ByteArray 缓存（按文件路径索引，上限 32MB，按字节大小淘汰）。 */
    private val bytesCache = object : LruCache<String, ByteArray>(32 * 1024 * 1024) { // 32MB
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    // region 视频播放

    /** 当前已装载的视频文件路径；null 表示未装载任何视频。 */
    private var currentVideoPath: String? = null

    /** 最近一次请求过的渲染表面（路径 + Surface），用于处理表面先于装载就绪的竞态。 */
    private var pendingSurface: Pair<String, Surface>? = null

    /** 是否静音（进入视频页默认静音自动播放）。 */
    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** 内嵌播放器状态。 */
    val playbackState: StateFlow<PlaybackState> get() = player.state

    /** 内嵌播放器当前播放位置（ms）。 */
    val positionMs: StateFlow<Long> get() = player.positionMs

    /** 内嵌播放器总时长（ms）。 */
    val durationMs: StateFlow<Long> get() = player.durationMs

    /** 内嵌播放器视频尺寸，用于按比例摆放渲染表面。 */
    val videoSize: StateFlow<VideoSize> get() = player.videoSize

    // endregion

    init {
        loadImages()
    }

    /** 从 Holder 读取请求（不消费，ViewModel 可安全重建），重建 Storage，列出目录媒体。 */
    private fun loadImages() {
        val request = holder.peek() ?: run {
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
                libraryId = library.id

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

                val includeVideo = ExperimentalSettings.viewerVideoEnabled
                val allFiles = withContext(Dispatchers.IO) { s.listFiles(dirFile) }
                val media = allFiles
                    .filter {
                        !it.isDirectory && (
                            MediaFileTypes.isImageFile(it.name) ||
                                (includeVideo && MediaFileTypes.isVideoFile(it.name))
                            )
                    }
                    .sortedBy { it.name.lowercase() }

                if (media.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.image_viewer_no_images)) }
                    return@launch
                }

                val initialPosition = media.indexOfFirst { it.path == request.initialFilePath }
                    .coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        media = media,
                        initialPosition = initialPosition,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 查询视频的本地缓存缩略图路径，供非当前页的视频占位图使用；无缓存返回 null。
     */
    suspend fun loadVideoThumbnail(file: StorageFile): String? = withContext(Dispatchers.IO) {
        try {
            thumbnailManager.getCachedThumbnailPath(libraryId, file.path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 进入视频页：构建播放源并按静音自动播放。
     *
     * 每次进入都从头开始（简易浏览场景不做续播），并把静音状态复位为静音。
     */
    fun onEnterVideo(file: StorageFile) {
        viewModelScope.launch {
            val s = storage ?: return@launch
            try {
                val source = MediaSourceBuilder.buildMediaSource(s, file, ownsStorage = false)
                currentVideoPath = file.path
                _muted.value = true
                player.setVolume(0f)
                player.setSource(source, 0L)
                player.prepare()
                player.play()
                // 表面可能先于本次装载就绪，补挂载
                pendingSurface?.let { (path, surface) ->
                    if (path == currentVideoPath) player.attachSurface(surface)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 失败时状态由内核 Error 暴露，UI 显示失败提示
            }
        }
    }

    /** 离开视频页：暂停播放（表面由 UI 销毁时解绑）。 */
    fun onLeaveVideo() {
        currentVideoPath = null
        player.pause()
    }

    /** 挂载渲染表面（仅在仍是当前视频时挂载，避免相邻页表面互相顶替）。 */
    fun onSurfaceAvailable(path: String, surface: Surface) {
        pendingSurface = path to surface
        if (path == currentVideoPath) player.attachSurface(surface)
    }

    /** 解绑渲染表面。 */
    fun onSurfaceDestroyed(path: String) {
        if (pendingSurface?.first == path) pendingSurface = null
        if (path == currentVideoPath) player.attachSurface(null)
    }

    /** 切换播放/暂停。 */
    fun togglePlayPause() {
        when (player.state.value) {
            is PlaybackState.Playing -> player.pause()
            is PlaybackState.Paused,
            is PlaybackState.Ready,
            is PlaybackState.Ended -> player.play()
            // 枚举穷尽化：Idle/Buffering 起播无意义（未装载或仍在缓冲），Error 需用户重试
            is PlaybackState.Idle,
            is PlaybackState.Buffering,
            is PlaybackState.Error -> Unit
        }
    }

    /** 跳转到指定位置（ms）。 */
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    /** 切换静音。 */
    fun toggleMute() {
        val next = !_muted.value
        _muted.value = next
        player.setVolume(if (next) 0f else 1f)
    }

    override fun onCleared() {
        // 释放内嵌播放器（ExoPlayer 实例）
        try {
            player.release()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

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
    val media: List<StorageFile> = emptyList(),
    val initialPosition: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)
