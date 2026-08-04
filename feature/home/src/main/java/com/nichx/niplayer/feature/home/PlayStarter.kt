package com.nichx.niplayer.feature.home

import com.nichx.niplayer.common.coroutine.AppCoroutineScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.nichx.niplayer.feature.home.playlist.toPlaylistItem
import com.nichx.niplayer.player.kernel.HistoryDescriptor
import com.nichx.niplayer.player.kernel.MediaSourceBuilder
import com.nichx.niplayer.player.kernel.PlaybackRequest
import com.nichx.niplayer.player.kernel.PlaybackRequestHolder
import com.nichx.niplayer.player.kernel.PlaylistHolder
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.isAudioFile
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 [PlayHistoryEntity] 恢复播放的封装。
 *
 * 供首页最近播放卡片（[HomeTabViewModel]）与播放历史列表（PlayHistoryViewModel）复用，
 * 避免在两处重复实现"历史记录 → Storage → NxMediaSource → PlaybackRequest"链路。
 *
 * 流程：
 * 1. 从 [PlayHistoryEntity.storageId] 查 [MediaLibraryEntity]
 * 2. [StorageFactory.create] 创建 Storage 实例
 * 3. [MediaSourceBuilder.createVirtualFile] 用 storagePath 构造虚拟 StorageFile
 * 4. [MediaSourceBuilder.buildMediaSource] 构造 NxMediaSource（按协议分流）
 * 5. 列出父目录中视频文件，构造 [PlaylistItem] 列表写入 [PlaylistHolder]（支持连播）
 * 6. 组装 [PlaybackRequest]（含 [HistoryDescriptor] 供 PlayerViewModel 更新历史）写入
 *    [PlaybackRequestHolder]，返回 [StartResult.Success] 供 ViewModel emit 导航事件
 */
@Singleton
class PlayStarter @Inject constructor(
    private val storageFactory: StorageFactory,
    private val mediaLibraryDao: MediaLibraryDao,
    private val playlistItemDao: PlaylistItemDao,
    private val playbackRequestHolder: PlaybackRequestHolder,
    private val playlistHolder: PlaylistHolder,
    private val appScope: AppCoroutineScope,
) {

    /**
     * 从播放历史恢复播放。
     *
     * @return [StartResult.Success] 表示 PlaybackRequest 已写入 Holder，ViewModel 应 emit 导航事件；
     *   [StartResult.Error] 表示恢复失败，ViewModel 应 emit 错误提示
     */
    suspend fun startFromHistory(history: PlayHistoryEntity): StartResult {
        val storageId = history.storageId
            ?: return StartResult.Error("不支持的历史记录类型（无存储源）")

        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(storageId) }
            ?: return StartResult.Error("存储源已删除")

        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
            ?: return StartResult.Error("不支持的存储类型：${library.mediaType.storageName}")

        return try {
            val file = MediaSourceBuilder.createVirtualFile(
                path = history.storagePath ?: "",
                name = history.videoName,
            )
            // W-N7 修复：传入 uniqueKey 作为 mediaId
            val source = MediaSourceBuilder.buildMediaSource(storage, file, mediaId = history.uniqueKey)

            // BUG-22 修复：先 set Holder 再 build playlist。
            // 原实现先 buildAndSetPlaylist（可能耗时 1-3 秒列 SMB 大目录）再 set Holder，
            // 期间 UI 不导航到播放页，用户看到长时间转圈。改为先 set Holder 让 UI 立即导航，
            // playlist 在后台异步构造（失败不影响播放，仅丢失连播能力）。
            playbackRequestHolder.set(
                PlaybackRequest(
                    source = source,
                    title = history.videoName,
                    startPositionMs = history.videoPosition,
                    history = HistoryDescriptor(
                        uniqueKey = history.uniqueKey,
                        url = history.url,
                        mediaTypeValue = history.mediaType.value,
                        storageId = storageId,
                        storagePath = history.storagePath,
                        httpHeader = history.httpHeader,
                        playlistId = history.playlistId,
                    ),
                    isAudio = isAudioFile(history.videoName),
                )
            )

            // 播放列表恢复：来源歌单时从歌单恢复（本地 DB 查询，快），否则后台异步列目录
            val playlistId = history.playlistId
            if (playlistId != null) {
                restorePlaylistFromDb(playlistId, history.storagePath)
            } else {
                // BUG-21+22：后台构造同目录播放列表，不阻塞返回。
                // BUG-21 修复：按 history.videoName 扩展名判断音/视频类型，相应过滤。
                // 原实现固定 isVideoFile 过滤，从历史恢复音频时 playlist 为空，连播按钮禁用。
                val isAudio = isAudioFile(history.videoName)
                // playlist 在后台异步构造（不阻塞返回），大目录 SMB/WebDAV listFiles 可能耗时 1-3 秒。
                // buildAndSetPlaylist 内部 set playlistHolder，player 开始播放时不依赖 playlist 立即可用
                // （失败不影响播放，仅丢失连播能力）。
                // O-13：使用注入的 AppCoroutineScope 替代游离 CoroutineScope(Dispatchers.IO)
                appScope.launch {
                    buildAndSetPlaylist(storage, library, file, isAudio)
                }
            }

            StartResult.Success
        } catch (e: Exception) {
            StartResult.Error(e.message ?: "无法恢复播放")
        }
    }

    /**
     * 构造同目录播放列表，支持连播。
     *
     * BUG-21 修复：按 [isAudio] 参数过滤音频或视频文件，与 [StorageFileViewModel.buildPlaylist]
     * 逻辑一致。原实现固定 isVideoFile 过滤，音频历史恢复后 playlist 为空。
     *
     * BUG-22 修复：本方法在 [playbackRequestHolder.set] 之后调用，失败不影响播放启动。
     *
     * @param isAudio true 过滤音频文件，false 过滤视频文件
     */
    private suspend fun buildAndSetPlaylist(
        storage: com.nichx.niplayer.storage.Storage,
        library: com.nichx.niplayer.database.entity.MediaLibraryEntity,
        currentFile: com.nichx.niplayer.storage.StorageFile,
        isAudio: Boolean,
    ) {
        try {
            val parentPath = currentFile.path.substringBeforeLast('/', missingDelimiterValue = "")
            val parentName = if (parentPath.isEmpty()) "" else parentPath.substringAfterLast('/')
            val parentDir = object : AbstractStorageFile(
                path = parentPath,
                name = parentName,
                isDirectory = true,
                length = 0L,
                lastModified = 0L,
            ) {}
            val files = withContext(Dispatchers.IO) { storage.listFiles(parentDir) }
            // BUG-21：按 isAudio 过滤对应类型文件
            val filter: (String) -> Boolean = if (isAudio) {
                { name -> MediaFileTypes.isAudioFile(name) }
            } else {
                { name -> MediaFileTypes.isVideoFile(name) }
            }
            val items = files
                .filter { !it.isDirectory && filter(it.name) }
                .map {
                    PlaylistItem(
                        libraryId = library.id,
                        filePath = it.path,
                        fileName = it.name,
                        mediaTypeValue = library.mediaType.value,
                        // BUG-26：携带文件大小
                        fileSize = it.length,
                    )
                }
            val startIndex = items.indexOfFirst { it.filePath == currentFile.path }
            if (startIndex >= 0) {
                playlistHolder.set(items, startIndex)
            }
        } catch (_: Exception) {
            // 播放列表构造失败不中断播放流程
        }
    }

    /**
     * 从歌单恢复播放列表（本地 DB 查询）。
     *
     * [startFromHistory] 检测到 [PlayHistoryEntity.playlistId] 非空时调用本方法替代
     * [buildAndSetPlaylist]（后台列目录）。优势：本地 SQLite 查询快，且歌单顺序
     * 由用户排序决定，恢复后与原播放会话一致。
     *
     * 找不到匹配当前曲目的条目时静默失败（不影响播放，仅丢失连播）。
     *
     * @param playlistId 来源歌单 ID
     * @param currentFilePath 当前曲目在存储源内的路径（用于定位 startIndex）
     */
    private suspend fun restorePlaylistFromDb(playlistId: Int, currentFilePath: String?) {
        try {
            val entities = withContext(Dispatchers.IO) { playlistItemDao.getByPlaylist(playlistId) }
            val audioItems = entities.filter { MediaFileTypes.isAudioFile(it.fileName) }
            if (audioItems.isEmpty()) return
            val items = audioItems.map { it.toPlaylistItem() }
            val startIndex = currentFilePath?.let { path ->
                items.indexOfFirst { it.filePath == path }
            } ?: -1
            if (startIndex >= 0) {
                playlistHolder.set(items, startIndex)
            }
        } catch (_: Exception) {
            // 歌单恢复失败不中断播放流程
        }
    }

    sealed class StartResult {
        /** PlaybackRequest 已写入 Holder，ViewModel 应 emit 导航到播放页事件。 */
        object Success : StartResult()

        /** 恢复失败，ViewModel 应 emit 错误提示。 */
        data class Error(val message: String) : StartResult()
    }

    /**
     * 从快速访问书签播放文件（非文件夹）。
     *
     * 与 [startFromHistory] 共用「library → Storage → NxMediaSource → PlaybackRequest」链路，
     * 但数据源为 [QuickAccessEntity]（无续播位置，startPositionMs=0）。[HistoryDescriptor]
     * 的 uniqueKey 采用 `"${libraryId}:${storagePath}"`，与 [StorageFileViewModel.playFile]
     * 一致，使快速访问播放写入同一历史项，后续可在最近播放中续播。
     *
     * 文件夹书签不应调用本方法（由 [QuickAccessViewModel] 直接 emit 导航到文件浏览页）。
     */
    suspend fun startFromQuickAccess(item: QuickAccessEntity): StartResult {
        if (item.isDirectory) return StartResult.Error("不支持打开文件夹书签")

        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(item.libraryId) }
            ?: return StartResult.Error("存储源已删除")

        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
            ?: return StartResult.Error("不支持的存储类型：${library.mediaType.storageName}")

        return try {
            val file = MediaSourceBuilder.createVirtualFile(
                path = item.storagePath,
                name = item.name,
            )
            val uniqueKey = "${item.libraryId}:${item.storagePath}"
            // W-N7 修复：传入 uniqueKey 作为 mediaId
            val source = MediaSourceBuilder.buildMediaSource(storage, file, mediaId = uniqueKey)
            playbackRequestHolder.set(
                PlaybackRequest(
                    source = source,
                    title = item.name,
                    startPositionMs = 0L,
                    history = HistoryDescriptor(
                        uniqueKey = uniqueKey,
                        url = item.storagePath,
                        mediaTypeValue = library.mediaType.value,
                        storageId = item.libraryId,
                        storagePath = item.storagePath,
                    ),
                    isAudio = isAudioFile(item.name),
                )
            )
            StartResult.Success
        } catch (e: Exception) {
            StartResult.Error(e.message ?: "无法打开播放源")
        }
    }

    /**
     * 从歌单条目开始播放（歌单详情页「播放全部」）。
     *
     * 与 [startFromHistory] 共用「library → Storage → NxMediaSource → PlaybackRequest」链路，
     * 但播放列表已由歌单持久化提供（[PlaylistItem] 列表），直接同步写入 [PlaylistHolder]，
     * 无需后台异步列目录。切歌（上一集/下一集）由 PlayerViewModel.playAtIndex 按
     * item.libraryId 重建存储源完成，覆盖同目录自动连播。
     *
     * @param playlistId 歌单 ID，写入 [HistoryDescriptor.playlistId] 供历史记录来源
     * @param items 歌单条目（已按用户排序）
     * @param startIndex 起始播放下标，默认从头
     */
    suspend fun startFromPlaylist(
        playlistId: Int,
        items: List<PlaylistItem>,
        startIndex: Int = 0,
    ): StartResult {
        if (items.isEmpty()) return StartResult.Error("歌单为空")
        val target = items.getOrNull(startIndex)
            ?: return StartResult.Error("播放位置无效")
        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(target.libraryId) }
            ?: return StartResult.Error("存储源已删除")
        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
            ?: return StartResult.Error("不支持的存储类型：${library.mediaType.storageName}")

        return try {
            val file = MediaSourceBuilder.createVirtualFile(
                path = target.filePath,
                name = target.fileName,
            )
            val source = MediaSourceBuilder.buildMediaSource(
                storage,
                file,
                mediaId = "${target.libraryId}:${target.filePath}",
            )
            // 直接写入完整歌单列表，切歌沿用同一列表（覆盖同目录自动连播）
            playlistHolder.set(items, startIndex)
            playbackRequestHolder.set(
                PlaybackRequest(
                    source = source,
                    title = target.fileName,
                    startPositionMs = 0L,
                    history = HistoryDescriptor(
                        uniqueKey = "${target.libraryId}:${target.filePath}",
                        url = target.filePath,
                        mediaTypeValue = target.mediaTypeValue,
                        storageId = target.libraryId,
                        storagePath = target.filePath,
                        fileSize = target.fileSize,
                        playlistId = playlistId,
                    ),
                    isAudio = isAudioFile(target.fileName),
                )
            )
            StartResult.Success
        } catch (e: Exception) {
            StartResult.Error(e.message ?: "无法打开播放源")
        }
    }
}
