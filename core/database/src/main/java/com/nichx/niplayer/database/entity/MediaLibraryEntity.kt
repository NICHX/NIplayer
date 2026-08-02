package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nichx.niplayer.database.enums.MediaType
import com.squareup.moshi.JsonClass

/**
 * 媒体库表，记录用户配置的存储源（本地/WebDAV/SMB 等）。
 * 迁移自旧仓库 media_library 表（v1→v17 历次 Migration 累积出的当前 schema）。
 * 本仓库移除 FTP 支持，相关字段（ftp_mode / ftp_address / ftp_encoding）已删除，
 * schema 变更通过 fallbackToDestructiveMigration 兜底（参见项目 memory）。
 */
@Entity(
    tableName = "media_library",
    indices = [Index(value = ["url", "media_type"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class MediaLibraryEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "display_name")
    var displayName: String,

    @ColumnInfo(name = "url")
    var url: String,

    @ColumnInfo(name = "media_type")
    var mediaType: MediaType,

    @ColumnInfo(name = "account")
    var account: String? = null,

    @ColumnInfo(name = "password")
    var password: String? = null,

    /**
     * SMB 域/工作组（如 "WORKGROUP" / "CORP"）。
     *
     * BUG-32 修复：原实现 AuthenticationContext 的 domain 参数恒为 null，
     * Active Directory 域环境用户无法认证（域控要求 `domain\username` 格式）。
     * 新增此字段供 [com.nichx.niplayer.storage.impl.SmbStorage] 传入 AuthenticationContext。
     * 仅 SMB 类型使用，其他类型忽略。
     */
    @ColumnInfo(name = "domain")
    var domain: String? = null,

    @ColumnInfo(name = "is_anonymous")
    var isAnonymous: Boolean = false,

    @ColumnInfo(name = "port")
    var port: Int = 0,

    @ColumnInfo(name = "describe")
    var describe: String? = null,

    @ColumnInfo(name = "smb_v2")
    var smbV2: Boolean = true,

    @ColumnInfo(name = "smb_share_path")
    var smbSharePath: String? = null,

    /**
     * SMB 加密传输开关（仅 smbV2=true 时有效）。
     *
     * 开启时同时启用 SMB3 加密（withEncryptData）和签名（withSigningEnabled），
     * 关闭时仅使用 SMB2/3 明文传输，千兆内网吞吐可从 ~10MB/s 提升至 80-110 MB/s。
     * 默认关闭以提供最佳性能，内网环境安全风险低。
     */
    @ColumnInfo(name = "smb_encryption")
    var smbEncryption: Boolean = false,

    @ColumnInfo(name = "remote_secret")
    var remoteSecret: String? = null,

    @ColumnInfo(name = "web_dav_strict")
    var webDavStrict: Boolean = true,

    @ColumnInfo(name = "screencast_address")
    var screencastAddress: String = "",

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis(),
)
