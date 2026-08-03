package com.nichx.niplayer.player.kernel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放列表项。持有重建播放源所需的最小可序列化信息。
 *
 * [PlayerViewModel] 播放下一首时，通过 [libraryId] 从 [com.nichx.niplayer.database.dao
 * .MediaLibraryDao] 查询 [com.nichx.niplayer.database.entity.MediaLibraryEntity]，
 * 再用 [com.nichx.niplayer.storage.StorageFactory] 重建 Storage，
 * 最后用 [com.nichx.niplayer.feature.home.MediaSourceBuilder.createVirtualFile] +
 * [com.nichx.niplayer.feature.home.MediaSourceBuilder.buildMediaSource] 构造播放源。
 *
 * @param libraryId 存储源 ID
 * @param filePath 文件在存储源内的路径（Storage.path）
 * @param fileName 文件名（用于标题显示）
 * @param mediaTypeValue 存储源类型 value（如 "smb_server"），用于构造 HistoryDescriptor
 * @param fileSize 文件大小（字节），BUG-26 修复：供 createVirtualFile 传入 StorageFile.length，
 *   使 StorageDataSource.bytesRemaining 返回真实值，media3 可立即显示进度条与总时长。
 *   0 表示未知（向后兼容），media3 会从 Extractor 解析 duration。
 */
data class PlaylistItem(
    val libraryId: Int,
    val filePath: String,
    val fileName: String,
    val mediaTypeValue: String,
    val fileSize: Long = 0L,
)

/**
 * 跨模块传递播放列表的 @Singleton 持有者。
 *
 * 生命周期：由 Hilt 管理，应用级单例。生产者（StorageFileViewModel）[set] 后导航到
 * 播放页，消费者（PlayerViewModel）[consume] 后立即清空，避免跨会话残留。
 *
 * 与 [PlaybackRequestHolder] 配合：[PlaybackRequest] 携带首个播放项的完整播放源，
 * [PlaylistHolder] 携带同目录其余视频列表供 next/prev 连播。
 *
 * BUG-H2 修复：PlayStarter 在后台异步构造同目录列表（SMB/WebDAV listFiles 耗时
 * 1-3 秒），可能晚于播放页 [consume]。故新增 [playlistFlow]，set 时同步 emit，
 * PlayerViewModel 订阅后可接收延迟到达的列表；[consume] 与 [clear] 均会清空该流，
 * 保证同步路径（文件浏览页）不会重复消费。
 */
@Singleton
class PlaylistHolder @Inject constructor() {

    @Volatile
    private var items: List<PlaylistItem>? = null

    @Volatile
    private var startIndex: Int = 0

    /**
     * 播放列表变更流：set 时更新为 (列表, 起始索引)，clear/consume 时置 null。
     * PlayerViewModel 在 init 末尾订阅，接收 PlayStarter 异步构造的延迟列表。
     */
    private val _playlistFlow = MutableStateFlow<Pair<List<PlaylistItem>, Int>?>(null)
    val playlistFlow: StateFlow<Pair<List<PlaylistItem>, Int>?> = _playlistFlow.asStateFlow()

    /** 生产者调用：缓存播放列表与起始索引，并通知 [playlistFlow] 订阅者。 */
    fun set(items: List<PlaylistItem>, startIndex: Int) {
        this.items = items
        this.startIndex = startIndex
        _playlistFlow.value = items to startIndex
    }

    /** 消费者调用：取出并清空。播放页 init 时调用一次。 */
    fun consume(): Pair<List<PlaylistItem>, Int>? {
        val current = items
        items = null
        _playlistFlow.value = null
        return current?.let { it to startIndex }
    }

    /** 订阅者应用列表后调用：清除残留（含流），避免跨会话混入。 */
    fun clear() {
        items = null
        _playlistFlow.value = null
    }
}
