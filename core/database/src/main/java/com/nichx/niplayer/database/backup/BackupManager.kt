package com.nichx.niplayer.database.backup

import androidx.room.withTransaction
import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.dao.VideoBookmarkDao
import com.nichx.niplayer.database.entity.EncryptedFolderEntity
import com.nichx.niplayer.database.entity.ExtendFolderEntity
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** 备份文件 JSON 根结构。 */
@JsonClass(generateAdapter = true)
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaLibraries: List<MediaLibraryEntity> = emptyList(),
    val quickAccesses: List<QuickAccessEntity> = emptyList(),
    val videoBookmarks: List<VideoBookmarkEntity> = emptyList(),
    val extendFolders: List<ExtendFolderEntity> = emptyList(),
    val encryptedFolders: List<EncryptedFolderEntity> = emptyList(),
)

/** 备份摘要，供 UI 展示。 */
data class BackupSummary(
    val mediaLibraries: Int,
    val quickAccesses: Int,
    val videoBookmarks: Int,
    val extendFolders: Int,
    val encryptedFolders: Int,
)

/** Date <-> Long 时间戳适配器。 */
private object DateAdapter {
    @ToJson
    fun toJson(date: Date): Long = date.time

    @FromJson
    fun fromJson(timestamp: Long): Date = Date(timestamp)
}

@Singleton
class BackupManager @Inject constructor(
    private val db: NiplayerDatabase,
    private val mediaLibraryDao: MediaLibraryDao,
    private val quickAccessDao: QuickAccessDao,
    private val videoBookmarkDao: VideoBookmarkDao,
    private val extendFolderDao: ExtendFolderDao,
    private val encryptedFolderDao: EncryptedFolderDao,
) {
    private val adapter = Moshi.Builder()
        .add(DateAdapter)
        .build()
        .adapter(BackupData::class.java)
        .indent("  ")

    /** 导出用户数据为 JSON 字符串（不含播放历史，播放记录走实时同步）。 */
    suspend fun exportToJson(): String {
        val data = BackupData(
            mediaLibraries = mediaLibraryDao.getAllSuspend(),
            quickAccesses = quickAccessDao.getAll(),
            videoBookmarks = videoBookmarkDao.getAll(),
            extendFolders = extendFolderDao.getAll(),
            encryptedFolders = encryptedFolderDao.getAll(),
        )
        return adapter.toJson(data)
    }

    /**
     * 从 JSON 字符串恢复数据（事务性，失败则回滚）。
     *
     * @param currentLibraries 恢复前数据库中的现有存储源列表。恢复时对备份中
     *   url+account 与之相同的条目，保留现有密码（本地可用凭据），避免备份内的
     *   跨设备密文或旧密码覆盖当前生效的凭据导致连接失效（如 WebDAV 恢复源自身）。
     */
    suspend fun importFromJson(
        json: String,
        currentLibraries: List<MediaLibraryEntity> = emptyList(),
    ): BackupSummary {
        val data = adapter.fromJson(json)
            ?: throw IllegalArgumentException("无效的备份文件")

        if (data.version != BACKUP_VERSION) {
            throw IllegalArgumentException("不支持的备份文件版本: ${data.version}")
        }
        if (data.mediaLibraries.isEmpty() &&
            data.quickAccesses.isEmpty() &&
            data.videoBookmarks.isEmpty() &&
            data.extendFolders.isEmpty() &&
            data.encryptedFolders.isEmpty()
        ) {
            throw IllegalArgumentException("备份文件为空或内容无效")
        }

        // 现有存储源中可用凭据映射：url|account -> 当前密码
        val preservePasswords = currentLibraries
            .filter { it.password != null && it.url.isNotBlank() }
            .associate { credentialKey(it.url, it.account) to it.password!! }

        db.withTransaction {
            // 先清空，再按依赖顺序插入
            mediaLibraryDao.deleteAll()
            quickAccessDao.deleteAll()
            videoBookmarkDao.deleteAll()
            extendFolderDao.deleteAll()
            encryptedFolderDao.deleteAll()

            if (data.mediaLibraries.isNotEmpty()) {
                val libraries = data.mediaLibraries.map { lib ->
                    val currentPwd = preservePasswords[credentialKey(lib.url, lib.account)]
                    if (currentPwd != null && currentPwd != lib.password) {
                        lib.copy(password = currentPwd)
                    } else {
                        lib
                    }
                }
                mediaLibraryDao.insertAll(libraries)
            }
            if (data.quickAccesses.isNotEmpty()) {
                quickAccessDao.insertAll(data.quickAccesses)
            }
            if (data.videoBookmarks.isNotEmpty()) {
                videoBookmarkDao.insertAll(data.videoBookmarks)
            }
            if (data.extendFolders.isNotEmpty()) {
                extendFolderDao.insert(*data.extendFolders.toTypedArray())
            }
            // 加密配置恢复：insertAll 保留实体原始 id，storage_id 关联无需重映射
            if (data.encryptedFolders.isNotEmpty()) {
                data.encryptedFolders.forEach { encryptedFolderDao.insert(it) }
            }
        }

        return BackupSummary(
            mediaLibraries = data.mediaLibraries.size,
            quickAccesses = data.quickAccesses.size,
            videoBookmarks = data.videoBookmarks.size,
            extendFolders = data.extendFolders.size,
            encryptedFolders = data.encryptedFolders.size,
        )
    }

    private fun credentialKey(url: String, account: String?): String = "$url|$account"

    private companion object {
        const val BACKUP_VERSION = 1
    }
}
