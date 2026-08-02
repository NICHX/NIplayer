package com.nichx.niplayer.player.kernel

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次播放请求的载体。
 *
 * 由文件浏览页（:feature:home 的 StorageFileViewModel）在用户点击视频文件时构造，
 * 经 [PlaybackRequestHolder] 传递给播放页（:feature:player 的 PlayerViewModel）消费。
 *
 * 持有已构造完成的 [NxMediaSource]——对于 SMB 协议，[NxMediaSource.DataSource]
 * 含不可序列化的 [androidx.media3.datasource.DataSource.Factory]（[com.nichx.niplayer
 * .storage.datasource.StorageDataSource.Factory]），无法通过 Compose Navigation
 * 路由参数传递，故采用持有者模式。
 *
 * 替代旧仓库 `VideoSourceManager` 全局单例，改进点：
 * - Hilt @Singleton 管理生命周期，类型安全
 * - [PlaybackRequestHolder.consume] 取出后立即清空，避免跨会话残留
 * - [isAudio] 由生产者按文件扩展名预判，PlayerGuard 据此分流到视频/音频播放页
 *
 * @param source 已就绪的播放源（Http / Local / DataSource）
 * @param title 标题（文件名），用于播放页顶栏与 MediaSession 元数据
 * @param startPositionMs 续播起始位置（ms），0 表示从头播放；由调用方从 PlayHistory 查询后填充
 * @param history 历史记录描述符；非空时 PlayerViewModel 会写入/更新 play_history 表，
 *   为 null 表示本次播放不记录历史（如预览）
 * @param isAudio 是否为音频文件（按文件扩展名预判）。true 时 PlayerGuard 路由到
 *   AudioPlayerScreen（无 SurfaceView），false 路由到 PlayerScreen（视频）
 */
data class PlaybackRequest(
    val source: NxMediaSource,
    val title: String,
    val startPositionMs: Long = 0L,
    val history: HistoryDescriptor? = null,
    val isAudio: Boolean = false,
)

/**
 * 判断文件名/路径是否为音频文件（按扩展名）。
 *
 * BUG-1 修复：原 private AUDIO_EXTENSIONS 与 :feature:home 的 MediaFileTypes 不一致
 * （缺 `amr`，且曾误含 `m4s`）。改为委托到 [com.nichx.niplayer.player.kernel
 * .MediaFileTypes.isAudioFile]，扩展名表统一管理。
 *
 * PlayerGuard 据此结果决定路由到 [com.nichx.niplayer.feature.player.PlayerScreen]
 *（视频）或 [com.nichx.niplayer.feature.player.AudioPlayerScreen]（音频）。
 */
fun isAudioFile(name: String): Boolean = MediaFileTypes.isAudioFile(name)

/**
 * 播放历史记录描述符。
 *
 * 携带写回 `play_history` 表所需的全部元数据。放在 :player:kernel 而非 :core:database，
 * 是为了让 [PlaybackRequest] 能在 :player:kernel 中引用它而不引入对 :core:database 的
 * 编译依赖——[mediaTypeValue] 使用 [com.nichx.niplayer.database.enums.MediaType.value]
 * 字符串（如 `"smb_server"`）而非枚举序数，避免枚举顺序变更导致历史数据错乱。
 *
 * PlayerViewModel 消费时通过 [com.nichx.niplayer.database.enums.MediaType.fromValue]
 * 还原为 [com.nichx.niplayer.database.enums.MediaType] 枚举。
 *
 * @param uniqueKey 去重键（unique_key 列），同 key + storageId 视为同一播放项
 * @param url 原始 URL（HTTP URL / 本地 file:// 或 content:// / 存储库内路径）
 * @param mediaTypeValue [com.nichx.niplayer.database.enums.MediaType.value] 字符串
 * @param storageId 关联存储库 ID（可空，本地/直链无存储库）
 * @param storagePath 存储库内相对路径（可空，仅 SMB/WebDAV/External 有值）
 * @param httpHeader HTTP 请求头 JSON（可空，WebDAV 认证等）
 */
data class HistoryDescriptor(
    val uniqueKey: String,
    val url: String,
    val mediaTypeValue: String,
    val storageId: Int? = null,
    val storagePath: String? = null,
    val httpHeader: String? = null,
    val fileSize: Long = 0L,
)

/**
 * 跨模块传递 [PlaybackRequest] 的 @Singleton 持有者。
 *
 * 生命周期：由 Hilt 管理，应用级单例。生产者（文件浏览页）[set] 后立即导航到播放页，
 * 消费者（PlayerViewModel）[consume] 后立即清空内部引用，避免泄漏。
 *
 * 线程安全：[consume] / [peek] 在主线程调用（ViewModel init / Composable），
 * [set] 在主线程调用（点击回调），[Volatile] 保证可见性。
 */
@Singleton
class PlaybackRequestHolder @Inject constructor() {

    @Volatile
    private var request: PlaybackRequest? = null

    /** 生产者调用：缓存播放请求，随后导航到播放页。 */
    fun set(request: PlaybackRequest) {
        this.request = request
    }

    /** 消费者调用：取出并清空。播放页 init 时调用一次。 */
    fun consume(): PlaybackRequest? {
        val current = request
        request = null
        return current
    }

    /** 仅查看不清空（用于判断是否有待播放请求）。 */
    fun peek(): PlaybackRequest? = request
}
