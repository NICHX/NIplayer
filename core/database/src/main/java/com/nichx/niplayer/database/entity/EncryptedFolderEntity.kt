package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * 加密文件夹表（文件夹访问加密功能）。
 *
 * 记录存储源（SMB/WebDAV）下被密码保护的文件夹。进入该文件夹（及所有子目录）前
 * 必须通过密码或生物识别验证，且其中文件的播放不写入播放历史。
 *
 * 关联策略：按 [storageId] 关联 media_library（自增 id）。存储源被删除时加密配置随
 * 级联清理；重新添加存储源必须重新输入存储源凭据（SMB/WebDAV），等同一次重新认证，
 * 因此删除重建不会构成密码绕过（本地/SAF 不支持加密，无此顾虑）。
 *
 * 凭据存储：
 * - [passwordHash] / [passwordSalt] / [iterations]：PBKDF2WithHmacSHA256 哈希，
 *   不可逆，仅用于密码验证（不提供找回）
 * - [biometricSecret]：保留列（v10 迁移遗留），已不再写入，仅供数据库结构兼容
 */
@Entity(
    tableName = "encrypted_folder",
    indices = [Index(value = ["storage_id", "folder_path"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class EncryptedFolderEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "storage_id")
    val storageId: Int,

    @ColumnInfo(name = "folder_path")
    var folderPath: String,

    @ColumnInfo(name = "password_hash")
    var passwordHash: String,

    @ColumnInfo(name = "password_salt")
    var passwordSalt: String,

    @ColumnInfo(name = "iterations")
    var iterations: Int = 120000,

    /** 保留列（v10 迁移遗留），已不再写入。 */
    @ColumnInfo(name = "biometric_secret")
    val biometricSecret: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis(),
)
