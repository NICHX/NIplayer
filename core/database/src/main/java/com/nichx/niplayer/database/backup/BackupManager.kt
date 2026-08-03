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
import com.nichx.niplayer.datastore.AudioSettings
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.LrcApiSettings
import com.nichx.niplayer.datastore.PlayHistorySyncSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.datastore.VideoExtensionSettings
import com.nichx.niplayer.datastore.WebDavSettings
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
    // 在线歌词/音乐 API 配置（LrcApiSettings）
    val lrcApiUrl: String = "",
    val lrcApiAuth: String = "",
    // Assrt 字幕搜索 API token（SubtitleSettings.assrtToken）
    val assrtToken: String = "",
    // 完整应用设置快照（MMKV 持久化的用户偏好）；null 表示旧版备份无此字段，恢复时跳过
    val appSettings: AppSettingsData? = null,
)

/**
 * 应用设置快照（除数据库外的 MMKV 用户偏好，供备份导出/恢复）。
 *
 * 字段可空：null 表示该设置项在备份中缺失（旧版备份），恢复时跳过该项，避免误覆盖。
 */
@JsonClass(generateAdapter = true)
data class AppSettingsData(
    // 主题
    val themeMode: Int? = null,
    val themeScheme: Int? = null,
    // 播放器（lastBrightness / lastSpeedIndex 等运行时状态不备份）
    val playerLongPressSpeed: Float? = null,
    val playerAutoDetectBlackBars: Boolean? = null,
    val playerPitchPreservation: Boolean? = null,
    val playerLongPressTimeoutMs: Int? = null,
    val playerSeekSensitivity: Float? = null,
    val playerDoubleTapStepSeconds: Int? = null,
    // 字幕样式（assrtToken 保持顶层字段，向后兼容）
    val subtitleAutoLoadSameName: Boolean? = null,
    val subtitlePriority: String? = null,
    val subtitleTextSizeFraction: Float? = null,
    val subtitleApplyEmbeddedStyles: Boolean? = null,
    val subtitleFontFamilyKey: String? = null,
    val subtitleFontColor: Int? = null,
    val subtitleOutlineWidth: Float? = null,
    val subtitleOutlineColor: Int? = null,
    val subtitleBottomPaddingDp: Int? = null,
    // 均衡器（band 索引 -> 增益 mB）
    val audioEqualizerEnabled: Boolean? = null,
    val audioEqualizerPresetIndex: Int? = null,
    val audioEqualizerBandLevels: Map<Int, Int>? = null,
    // 缩略图
    val thumbnailGenerate: Boolean? = null,
    val thumbnailGenerateVideo: Boolean? = null,
    val thumbnailGenerateImage: Boolean? = null,
    val thumbnailGenerateAudio: Boolean? = null,
    val thumbnailSaveInSameDir: Boolean? = null,
    val thumbnailFramePositionKey: String? = null,
    val thumbnailCustomPositionSeconds: Int? = null,
    val thumbnailGenerationModeKey: String? = null,
    val thumbnailLibraryModes: Map<Int, String>? = null,
    // 文件浏览
    val fileSortBy: Int? = null,
    val fileSortAscending: Boolean? = null,
    val fileShowOnlyMedia: Boolean? = null,
    val fileShowHiddenFiles: Boolean? = null,
    val fileIsGridView: Boolean? = null,
    // 视频扩展名白名单
    val videoExtensions: String? = null,
    // 下载目录（SAF URI 跨设备可能失效，仍随备份导出便于同设备恢复）
    val downloadDirUri: String? = null,
    val downloadDirName: String? = null,
    // 播放历史云同步（deviceId 不备份：跨设备恢复后重新生成，避免设备冲突）
    val historySyncEnabled: Boolean? = null,
    val historySyncAutoSync: Boolean? = null,
    val historySyncLibraryId: Int? = null,
)

/** 备份摘要，供 UI 展示。 */
data class BackupSummary(
    val mediaLibraries: Int,
    val quickAccesses: Int,
    val videoBookmarks: Int,
    val extendFolders: Int,
    val encryptedFolders: Int,
    val lrcApiConfigured: Boolean = false,
    val assrtConfigured: Boolean = false,
    val appSettingsRestored: Boolean = false,
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
        val libraries = mediaLibraryDao.getAllSuspend()
        val data = BackupData(
            mediaLibraries = libraries,
            quickAccesses = quickAccessDao.getAll(),
            videoBookmarks = videoBookmarkDao.getAll(),
            extendFolders = extendFolderDao.getAll(),
            encryptedFolders = encryptedFolderDao.getAll(),
            // MMKV 设置随备份导出：音乐/LRC API 与 Assrt token
            lrcApiUrl = LrcApiSettings.apiUrl,
            lrcApiAuth = LrcApiSettings.apiAuth,
            assrtToken = SubtitleSettings.assrtToken,
            appSettings = AppSettingsData(
                themeMode = ThemeSettings.themeMode.value,
                themeScheme = ThemeSettings.themeSchemeOrdinal,
                playerLongPressSpeed = PlayerSettings.longPressSpeed,
                playerAutoDetectBlackBars = PlayerSettings.autoDetectBlackBars,
                playerPitchPreservation = PlayerSettings.pitchPreservationEnabled,
                playerLongPressTimeoutMs = PlayerSettings.longPressTimeoutMs,
                playerSeekSensitivity = PlayerSettings.seekSensitivity,
                playerDoubleTapStepSeconds = PlayerSettings.doubleTapStepSeconds,
                subtitleAutoLoadSameName = SubtitleSettings.autoLoadSameNameSubtitle,
                subtitlePriority = SubtitleSettings.subtitlePriority,
                subtitleTextSizeFraction = SubtitleSettings.textSizeFraction,
                subtitleApplyEmbeddedStyles = SubtitleSettings.applyEmbeddedStyles,
                subtitleFontFamilyKey = SubtitleSettings.fontFamilyKey,
                subtitleFontColor = SubtitleSettings.fontColor,
                subtitleOutlineWidth = SubtitleSettings.outlineWidth,
                subtitleOutlineColor = SubtitleSettings.outlineColor,
                subtitleBottomPaddingDp = SubtitleSettings.bottomPaddingDp,
                audioEqualizerEnabled = AudioSettings.equalizerEnabled,
                audioEqualizerPresetIndex = AudioSettings.equalizerPresetIndex,
                audioEqualizerBandLevels = AudioSettings.snapshotBandLevels().ifEmpty { null },
                thumbnailGenerate = ThumbnailSettings.generateThumbnail,
                thumbnailGenerateVideo = ThumbnailSettings.generateForVideo,
                thumbnailGenerateImage = ThumbnailSettings.generateForImage,
                thumbnailGenerateAudio = ThumbnailSettings.generateForAudio,
                thumbnailSaveInSameDir = ThumbnailSettings.saveInSameDir,
                thumbnailFramePositionKey = ThumbnailSettings.framePositionKey,
                thumbnailCustomPositionSeconds = ThumbnailSettings.customPositionSeconds,
                thumbnailGenerationModeKey = ThumbnailSettings.generationMode.key,
                thumbnailLibraryModes = libraries.mapNotNull { lib ->
                    ThumbnailSettings.getLibraryGenerationMode(lib.id)?.let { mode -> lib.id to mode.key }
                }.toMap().ifEmpty { null },
                fileSortBy = FileBrowserSettings.sortBy.value,
                fileSortAscending = FileBrowserSettings.sortAscending,
                fileShowOnlyMedia = FileBrowserSettings.showOnlyMediaFiles,
                fileShowHiddenFiles = FileBrowserSettings.showHiddenFiles,
                fileIsGridView = FileBrowserSettings.isGridView,
                videoExtensions = VideoExtensionSettings.supportText,
                downloadDirUri = DownloadSettings.downloadDirUri.ifBlank { null },
                downloadDirName = DownloadSettings.downloadDirName.ifBlank { null },
                // 播放历史云同步开关随备份迁移；deviceId 不导出（见 AppSettingsData 注释）
                historySyncEnabled = PlayHistorySyncSettings.enabled,
                historySyncAutoSync = PlayHistorySyncSettings.autoSync,
                historySyncLibraryId = WebDavSettings.libraryId.takeIf { it >= 0 },
            ),
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
            data.encryptedFolders.isEmpty() &&
            data.lrcApiUrl.isBlank() &&
            data.lrcApiAuth.isBlank() &&
            data.assrtToken.isBlank() &&
            data.appSettings == null
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

        // MMKV 设置恢复：仅当备份内配置非空时覆盖，避免旧版备份（无这些字段）
        // 恢复时误清空当前设备上已配置的音乐 API 与 Assrt token
        if (data.lrcApiUrl.isNotBlank()) {
            LrcApiSettings.apiUrl = data.lrcApiUrl
        }
        if (data.lrcApiAuth.isNotBlank()) {
            LrcApiSettings.apiAuth = data.lrcApiAuth
        }
        if (data.assrtToken.isNotBlank()) {
            SubtitleSettings.assrtToken = data.assrtToken
        }

        // 应用设置快照恢复：仅恢复备份中非 null 的字段（null = 旧版备份缺失，跳过）
        data.appSettings?.let { s ->
            s.themeMode?.let { ThemeSettings.restoreMode(it) }
            s.themeScheme?.let { ThemeSettings.restoreScheme(it) }
            s.playerLongPressSpeed?.let { PlayerSettings.longPressSpeed = it }
            s.playerAutoDetectBlackBars?.let { PlayerSettings.autoDetectBlackBars = it }
            s.playerPitchPreservation?.let { PlayerSettings.pitchPreservationEnabled = it }
            s.playerLongPressTimeoutMs?.let { PlayerSettings.longPressTimeoutMs = it }
            s.playerSeekSensitivity?.let { PlayerSettings.seekSensitivity = it }
            s.playerDoubleTapStepSeconds?.let { PlayerSettings.doubleTapStepSeconds = it }
            s.subtitleAutoLoadSameName?.let { SubtitleSettings.autoLoadSameNameSubtitle = it }
            s.subtitlePriority?.let { SubtitleSettings.subtitlePriority = it }
            s.subtitleTextSizeFraction?.let { SubtitleSettings.textSizeFraction = it }
            s.subtitleApplyEmbeddedStyles?.let { SubtitleSettings.applyEmbeddedStyles = it }
            s.subtitleFontFamilyKey?.let { SubtitleSettings.fontFamilyKey = it }
            s.subtitleFontColor?.let { SubtitleSettings.fontColor = it }
            s.subtitleOutlineWidth?.let { SubtitleSettings.outlineWidth = it }
            s.subtitleOutlineColor?.let { SubtitleSettings.outlineColor = it }
            s.subtitleBottomPaddingDp?.let { SubtitleSettings.bottomPaddingDp = it }
            // 均衡器：先恢复频段增益（setBandLevel 会置 -1），再恢复预设索引原始值
            s.audioEqualizerBandLevels?.forEach { (band, level) ->
                AudioSettings.setBandLevel(band, level)
            }
            s.audioEqualizerPresetIndex?.let { AudioSettings.equalizerPresetIndex = it }
            s.audioEqualizerEnabled?.let { AudioSettings.equalizerEnabled = it }
            s.thumbnailGenerate?.let { ThumbnailSettings.generateThumbnail = it }
            s.thumbnailGenerateVideo?.let { ThumbnailSettings.generateForVideo = it }
            s.thumbnailGenerateImage?.let { ThumbnailSettings.generateForImage = it }
            s.thumbnailGenerateAudio?.let { ThumbnailSettings.generateForAudio = it }
            s.thumbnailSaveInSameDir?.let { ThumbnailSettings.saveInSameDir = it }
            s.thumbnailFramePositionKey?.let { ThumbnailSettings.framePositionKey = it }
            s.thumbnailCustomPositionSeconds?.let { ThumbnailSettings.customPositionSeconds = it }
            s.thumbnailGenerationModeKey?.let { key ->
                ThumbnailSettings.generationMode = ThumbnailGenerationMode.fromKey(key)
            }
            s.thumbnailLibraryModes?.forEach { (libId, key) ->
                ThumbnailSettings.setLibraryGenerationMode(libId, ThumbnailGenerationMode.fromKey(key))
            }
            s.fileSortBy?.let { FileBrowserSettings.setSortBy(FileBrowserSettings.SortBy.fromValue(it)) }
            s.fileSortAscending?.let { FileBrowserSettings.setSortAscending(it) }
            s.fileShowOnlyMedia?.let { FileBrowserSettings.showOnlyMediaFiles = it }
            s.fileShowHiddenFiles?.let { FileBrowserSettings.showHiddenFiles = it }
            s.fileIsGridView?.let { FileBrowserSettings.isGridView = it }
            s.videoExtensions?.let { VideoExtensionSettings.supportText = it }
            s.downloadDirUri?.let { DownloadSettings.downloadDirUri = it }
            s.downloadDirName?.let { DownloadSettings.downloadDirName = it }
            // 播放历史云同步：恢复开关与所选服务器，并重新生成设备标识
            s.historySyncEnabled?.let { PlayHistorySyncSettings.enabled = it }
            s.historySyncAutoSync?.let { PlayHistorySyncSettings.autoSync = it }
            if (s.historySyncLibraryId != null) {
                WebDavSettings.setLibraryId(s.historySyncLibraryId)
                PlayHistorySyncSettings.resetDeviceId()
            }
        }

        return BackupSummary(
            mediaLibraries = data.mediaLibraries.size,
            quickAccesses = data.quickAccesses.size,
            videoBookmarks = data.videoBookmarks.size,
            extendFolders = data.extendFolders.size,
            encryptedFolders = data.encryptedFolders.size,
            lrcApiConfigured = data.lrcApiUrl.isNotBlank() || data.lrcApiAuth.isNotBlank(),
            assrtConfigured = data.assrtToken.isNotBlank(),
            appSettingsRestored = data.appSettings != null,
        )
    }

    private fun credentialKey(url: String, account: String?): String = "$url|$account"

    private companion object {
        const val BACKUP_VERSION = 1
    }
}
