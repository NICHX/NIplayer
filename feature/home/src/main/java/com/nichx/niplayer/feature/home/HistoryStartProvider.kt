package com.nichx.niplayer.feature.home

import com.nichx.niplayer.feature.home.R
import android.content.Context
import com.nichx.niplayer.common.coroutine.AppCoroutineScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.resumeStartPositionMs
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.nichx.niplayer.player.kernel.HistoryDescriptor
import com.nichx.niplayer.player.kernel.MediaSourceBuilder
import com.nichx.niplayer.player.kernel.PlaybackRequest
import com.nichx.niplayer.player.kernel.PlaybackRequestHolder
import com.nichx.niplayer.player.kernel.PlaylistHolder
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.isAudioFile
import com.nichx.niplayer.storage.AbstractStorageFile
import com.nichx.niplayer.storage.StorageFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 历史 / 快速访问启动链 Provider。
 *
 * 承载从 [PlayHistoryEntity] 恢复播放与从 [QuickAccessEntity] 快捷启动两条路径，
 * 复用「library → Storage → NxMediaSource → PlaybackRequest」链路。同目录自动连播列表
 * 在后台异步构造（[buildAndSetPlaylist]）。
 *
 * 与歌单启动链解耦，[PlayStarter] 按来源场景分发到本 Provider。
 */
@Singleton
class HistoryStartProvider @Inject constructor(
    private val storageFactory: StorageFactory,
    private val mediaLibraryDao: MediaLibraryDao,
    private val playbackRequestHolder: PlaybackRequestHolder,
    private val playlistHolder: PlaylistHolder,
    private val appScope: AppCoroutineScope,
    @ApplicationContext private val context: Context,
) {

    /**
     * 从播放历史恢复播放。
     *
     * @return [PlayStartResult.Success] 表示 PlaybackRequest 已写入 Holder，ViewModel 应 emit 导航事件；
     *   [PlayStartResult.Error] 表示恢复失败，ViewModel 应 emit 错误提示
     */
    suspend fun startFromHistory(history: PlayHistoryEntity): PlayStartResult {
        val storageId = history.storageId
            ?: return PlayStartResult.Error(context.getString(R.string.play_error_history_no_storage))

        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(storageId) }
            ?: return PlayStartResult.Error(context.getString(R.string.play_error_library_deleted))

        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
            ?: return PlayStartResult.Error(
                context.getString(
                    R.string.play_error_unsupported_storage,
                    context.getString(library.mediaType.storageNameRes),
                )
            )

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
                    startPositionMs = history.resumeStartPositionMs(),
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

            PlayStartResult.Success
        } catch (e: Exception) {
            PlayStartResult.Error(e.message ?: context.getString(R.string.play_error_restore_failed))
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
     * 从快速访问书签播放文件（非文件夹）。
     *
     * 与 [startFromHistory] 共用「library → Storage → NxMediaSource → PlaybackRequest」链路，
     * 但数据源为 [QuickAccessEntity]（无续播位置，startPositionMs=0）。[HistoryDescriptor]
     * 的 uniqueKey 采用 `"${libraryId}:${storagePath}"`，与 [StorageFileViewModel.playFile]
     * 一致，使快速访问播放写入同一历史项，后续可在最近播放中续播。
     *
     * 文件夹书签不应调用本方法（由 [QuickAccessViewModel] 直接 emit 导航到文件浏览页）。
     */
    suspend fun startFromQuickAccess(item: QuickAccessEntity): PlayStartResult {
        if (item.isDirectory) return PlayStartResult.Error(context.getString(R.string.play_error_folder_bookmark))

        val library = withContext(Dispatchers.IO) { mediaLibraryDao.getById(item.libraryId) }
            ?: return PlayStartResult.Error(context.getString(R.string.play_error_library_deleted))

        val storage = withContext(Dispatchers.IO) { storageFactory.create(library) }
            ?: return PlayStartResult.Error(
                context.getString(
                    R.string.play_error_unsupported_storage,
                    context.getString(library.mediaType.storageNameRes),
                )
            )

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
            PlayStartResult.Success
        } catch (e: Exception) {
            PlayStartResult.Error(e.message ?: context.getString(R.string.play_error_open_failed))
        }
    }
}