package com.nichx.niplayer.storage

/**
 * 存储文件抽象。
 *
 * 统一描述 LocalStorage / WebDavStorage / SmbStorage 下的文件/目录。
 * 不同协议的具体实现见 [AbstractStorageFile] 子类。
 */
interface StorageFile {

    /** 相对于存储库根目录的路径，以 `/` 分隔。根目录为空字符串。 */
    val path: String

    /** 显示名称（不含路径）。 */
    val name: String

    /** 是否为目录。 */
    val isDirectory: Boolean

    /** 文件字节数；目录或未知返回 0。 */
    val length: Long

    /** 最后修改时间戳（ms），未知返回 0。 */
    val lastModified: Long

    /**
     * 服务端 ETag（文件内容强校验指纹），仅 WebDAV 提供；未知或不可用返回 null。
     * 用于增量同步等场景在 mtime（秒级粒度）之外做精确变更判定。
     */
    val etag: String?

    /**
     * 是否为隐藏文件/目录。
     *
     * - SMB：由 [com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_HIDDEN] 决定
     * - WebDAV：由 `ishidden` 属性决定
     * - 本地文件：名称以 `.` 开头
     */
    val isHidden: Boolean
}
