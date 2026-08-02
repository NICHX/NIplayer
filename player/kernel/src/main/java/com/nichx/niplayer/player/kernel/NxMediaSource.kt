package com.nichx.niplayer.player.kernel

import android.net.Uri
import androidx.media3.datasource.DataSource

/**
 * 播放器数据源抽象。
 *
 * 替代旧仓库 BaseVideoSource 体系。新仓库 media3 单一内核，
 * 内核通过 DataSpec / OkHttpDataSource 携带 headers，无需 VlcProxyServer 本地代理。
 *
 * SMB 等非 HTTP 协议通过 [DataSource] 类型注入自定义 [DataSource.Factory]，
 * 替代旧仓库 SmbPlayServer / FtpPlayServer 的 NanoHTTPD 本地代理方案。
 */
sealed class NxMediaSource {

    abstract val uri: Uri

    /**
     * W-N7 修复：业务侧 mediaId，用于 media3 [androidx.media3.common.MediaItem.mediaId]。
     *
     * 调用方（PlayerViewModel / StorageFileViewModel）应传入与应用层 uniqueKey 一致
     * 的值（如 `"${library.id}:${file.path}"`），让 media3 的 mediaId 与播放历史、
     * MediaSession 元数据保持一致。
     *
     * 默认空字符串，由 [com.nichx.niplayer.player.kernel.media3.NxMedia3Player.setSource]
     * 回退到 [uri] 字符串，保持向后兼容。
     */
    abstract val mediaId: String

    /**
     * HTTP(S) 数据源。
     *
     * @param headers 自定义请求头（Referer / User-Agent / Cookie 等），
     *                由 :player:kernel 内部通过 media3 OkHttpDataSource 注入。
     * @param trustAllCertificates 是否信任所有 TLS 证书（含自签）。
     *                W-C3 修复：WebDAV 非 strict 模式下，浏览/缩略图路径由 WebDavStorage
     *                内部派生 trust-all OkHttpClient，但播放路径走 NxMedia3Player 注入的
     *                strict 单例 client。此标志让播放器为当前 MediaSource 派生 trust-all client。
     */
    data class Http(
        override val uri: Uri,
        override val mediaId: String = "",
        val headers: Map<String, String> = emptyMap(),
        val trustAllCertificates: Boolean = false,
    ) : NxMediaSource()

    /**
     * 本地数据源：file / content / android.resource。
     *
     * 不携带 headers，直接由 media3 DefaultDataSource 处理。
     */
    data class Local(
        override val uri: Uri,
        override val mediaId: String = "",
    ) : NxMediaSource()

    /**
     * 自定义 [DataSource] 数据源。
     *
     * 用于 SMB 等非 HTTP 协议：调用方（如 :feature:player 的 PlayerViewModel）
     * 通过 [StorageFactory] 创建 [com.nichx.niplayer.storage.Storage] 实例后，
     * 用其构造 [factory] 注入播放器。
     *
     * 替代旧仓库 SmbPlayServer / FtpPlayServer 的 NanoHTTPD 本地代理 + 反射改端口 hack。
     *
     * BUG-19+23 修复：新增 [storage] 字段，让调用方创建的 Storage 实例随 NxMediaSource
     * 一并传递给 PlayerViewModel。PlayerViewModel 在切换源或 onCleared 时统一关闭，
     * 避免 playAtIndex / PlayStarter 创建的 Storage 永不关闭（连接泄漏）。
     *
     * 注意：[StorageDataSource.close] 不关闭 storage，因为 media3 在 seek 时会反复
     * close/open DataSource，过早关闭 storage 会破坏后续 open。storage 的真正释放
     * 由 PlayerViewModel 在播放器 release 时调用。
     *
     * @param factory media3 DataSource.Factory，每次 [NxPlayer.setSource] 时由内核
     *                包装进 DefaultMediaSourceFactory
     * @param uri 用于 media3 MediaItem.uri（影响 MediaSession 元数据展示）
     * @param storage 调用方创建的 Storage 实例（可为 null，HTTP/Local 类型不携带），
     *               由 PlayerViewModel 在切换或退出时关闭
     */
    data class DataSource(
        val factory: DataSource.Factory,
        override val uri: Uri,
        override val mediaId: String = "",
        val storage: com.nichx.niplayer.storage.Storage? = null,
    ) : NxMediaSource()
}
