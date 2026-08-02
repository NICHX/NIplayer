package com.nichx.niplayer.database.enums

/**
 * 媒体库类型枚举。
 *
 * 迁移自旧仓库 data_component 的 MediaType 枚举。本仓库移除 Alist 与 FTP 支持
 * （参见 project memory：不迁移 AlistStorage / FtpStorage），故不含 ALIST_STORAGE / FTP_SERVER。
 *
 * 已移除的属性：
 * - `cover: Int`（旧仓库引用 R.drawable.ic_*）：UI 层资源，不属 :core:database
 * - `toAction()`：返回 SheetActionBean，UI 层职责
 *
 * [sortOrder] 用于媒体库页面按类型分组排序的优先级。
 */
enum class MediaType(
    val value: String,
    val storageName: String,
    val sortOrder: Int,
) {
    LOCAL_STORAGE("local_storage", "本地视频库", 0),
    EXTERNAL_STORAGE("external_storage", "设备存储库", 1),
    SMB_SERVER("smb_server", "SMB服务器", 2),
    WEBDAV_SERVER("webdav_server", "WebDav服务器", 3),
    OTHER_STORAGE("other_storage", "外部服务器", 4),
    QUICK_ACCESS("quick_access", "快速访问", 5);

    companion object {
        fun fromValue(value: String): MediaType {
            return when (value) {
                "local_storage" -> LOCAL_STORAGE
                "webdav_server" -> WEBDAV_SERVER
                "smb_server" -> SMB_SERVER
                "external_storage" -> EXTERNAL_STORAGE
                "quick_access" -> QUICK_ACCESS
                else -> OTHER_STORAGE
            }
        }
    }
}
