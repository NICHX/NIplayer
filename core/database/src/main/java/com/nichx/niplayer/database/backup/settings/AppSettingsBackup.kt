package com.nichx.niplayer.database.backup.settings

import com.nichx.niplayer.database.backup.BackupItem
import com.nichx.niplayer.database.backup.RestoreMode
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
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject

/**
 * 应用设置备份项（v2 格式）。
 *
 * 统一处理所有 MMKV 用户偏好设置的备份/恢复，替代旧版 [com.nichx.niplayer.database.backup.BackupManager]
 * 中 `AppSettingsData` 的导出/恢复逻辑。
 *
 * v2 变更：原顶层字段 `lrcApiUrl` / `lrcApiAuth` / `assrtToken` 并入 appSettings，
 * 不再作为 BackupData 顶层字段。v1 → v2 兼容由 BackupManager 处理。
 *
 * 序列化采用 Moshi 原生 [JsonAdapter.toJsonValue] / [JsonAdapter.fromJsonValue] 模式
 * （基于 [Any] 松散对象），不依赖 JsonElement DOM（Moshi 1.15.2 不提供该 API）。
 *
 * 恢复语义：MMKV 设置均为"有就覆盖，没有就保留"，[RestoreMode] 对本项无影响。
 */
class AppSettingsBackup @Inject constructor() : BackupItem {

    override val key: String = "appSettings"

    private val adapter by lazy {
        Moshi.Builder().build().adapter(AppSettingsData::class.java)
    }

    override suspend fun snapshot(): Any? {
        val data = AppSettingsData(
            // 主题
            themeMode = ThemeSettings.themeMode.value,
            themeScheme = ThemeSettings.themeSchemeOrdinal,
            // 播放器（lastBrightness / lastSpeedIndex 等运行时状态不备份）
            playerLongPressSpeed = PlayerSettings.longPressSpeed,
            playerAutoDetectBlackBars = PlayerSettings.autoDetectBlackBars,
            playerPitchPreservation = PlayerSettings.pitchPreservationEnabled,
            playerLongPressTimeoutMs = PlayerSettings.longPressTimeoutMs,
            playerSeekSensitivity = PlayerSettings.seekSensitivity,
            playerDoubleTapStepSeconds = PlayerSettings.doubleTapStepSeconds,
            // 字幕
            subtitleAutoLoadSameName = SubtitleSettings.autoLoadSameNameSubtitle,
            subtitlePriority = SubtitleSettings.subtitlePriority,
            subtitleTextSizeFraction = SubtitleSettings.textSizeFraction,
            subtitleApplyEmbeddedStyles = SubtitleSettings.applyEmbeddedStyles,
            subtitleFontFamilyKey = SubtitleSettings.fontFamilyKey,
            subtitleFontColor = SubtitleSettings.fontColor,
            subtitleOutlineWidth = SubtitleSettings.outlineWidth,
            subtitleOutlineColor = SubtitleSettings.outlineColor,
            subtitleBottomPaddingDp = SubtitleSettings.bottomPaddingDp,
            // 均衡器（band 索引 -> 增益 mB）
            audioEqualizerEnabled = AudioSettings.equalizerEnabled,
            audioEqualizerPresetIndex = AudioSettings.equalizerPresetIndex,
            audioEqualizerBandLevels = AudioSettings.snapshotBandLevels().ifEmpty { null },
            // 缩略图
            thumbnailGenerate = ThumbnailSettings.generateThumbnail,
            thumbnailGenerateVideo = ThumbnailSettings.generateForVideo,
            thumbnailGenerateImage = ThumbnailSettings.generateForImage,
            thumbnailGenerateAudio = ThumbnailSettings.generateForAudio,
            thumbnailSaveInSameDir = ThumbnailSettings.saveInSameDir,
            thumbnailFramePositionKey = ThumbnailSettings.framePositionKey,
            thumbnailCustomPositionSeconds = ThumbnailSettings.customPositionSeconds,
            thumbnailGenerationModeKey = ThumbnailSettings.generationMode.key,
            thumbnailLibraryModes = ThumbnailSettings.snapshotAllLibraryModes().ifEmpty { null },
            // 文件浏览
            fileSortBy = FileBrowserSettings.sortBy.value,
            fileSortAscending = FileBrowserSettings.sortAscending,
            fileShowOnlyMedia = FileBrowserSettings.showOnlyMediaFiles,
            fileShowHiddenFiles = FileBrowserSettings.showHiddenFiles,
            fileIsGridView = FileBrowserSettings.isGridView,
            // 视频扩展名白名单
            videoExtensions = VideoExtensionSettings.supportText,
            // 下载目录（SAF URI 跨设备可能失效，仍随备份导出便于同设备恢复）
            downloadDirUri = DownloadSettings.downloadDirUri.ifBlank { null },
            downloadDirName = DownloadSettings.downloadDirName.ifBlank { null },
            // 播放历史云同步（deviceId 不备份：跨设备恢复后重新生成，避免设备冲突）
            historySyncEnabled = PlayHistorySyncSettings.enabled,
            historySyncAutoSync = PlayHistorySyncSettings.autoSync,
            historySyncLibraryId = WebDavSettings.libraryId.takeIf { it >= 0 },
            // 在线歌词/音乐 API 与 Assrt 字幕 token（v2 并入 appSettings，不再顶层字段）
            lrcApiUrl = LrcApiSettings.apiUrl.ifBlank { null },
            lrcApiAuth = LrcApiSettings.apiAuth.ifBlank { null },
            assrtToken = SubtitleSettings.assrtToken.ifBlank { null },
        )
        return adapter.toJsonValue(data)
    }

    override suspend fun restore(data: Any?, mode: RestoreMode) {
        // mode 对 MMKV 设置无影响：设置都是"有就覆盖，没有就保留"
        if (data == null) return
        val s: AppSettingsData = adapter.fromJsonValue(data) ?: return

        // 主题
        s.themeMode?.let { ThemeSettings.restoreMode(it) }
        s.themeScheme?.let { ThemeSettings.restoreScheme(it) }
        // 播放器
        s.playerLongPressSpeed?.let { PlayerSettings.longPressSpeed = it }
        s.playerAutoDetectBlackBars?.let { PlayerSettings.autoDetectBlackBars = it }
        s.playerPitchPreservation?.let { PlayerSettings.pitchPreservationEnabled = it }
        s.playerLongPressTimeoutMs?.let { PlayerSettings.longPressTimeoutMs = it }
        s.playerSeekSensitivity?.let { PlayerSettings.seekSensitivity = it }
        s.playerDoubleTapStepSeconds?.let { PlayerSettings.doubleTapStepSeconds = it }
        // 字幕
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
        // 缩略图
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
        // 文件浏览
        s.fileSortBy?.let { FileBrowserSettings.setSortBy(FileBrowserSettings.SortBy.fromValue(it)) }
        s.fileSortAscending?.let { FileBrowserSettings.setSortAscending(it) }
        s.fileShowOnlyMedia?.let { FileBrowserSettings.showOnlyMediaFiles = it }
        s.fileShowHiddenFiles?.let { FileBrowserSettings.showHiddenFiles = it }
        s.fileIsGridView?.let { FileBrowserSettings.isGridView = it }
        // 视频扩展名
        s.videoExtensions?.let { VideoExtensionSettings.supportText = it }
        // 下载目录
        s.downloadDirUri?.let { DownloadSettings.downloadDirUri = it }
        s.downloadDirName?.let { DownloadSettings.downloadDirName = it }
        // 播放历史云同步：恢复开关与所选服务器，并重新生成设备标识
        s.historySyncEnabled?.let { PlayHistorySyncSettings.enabled = it }
        s.historySyncAutoSync?.let { PlayHistorySyncSettings.autoSync = it }
        if (s.historySyncLibraryId != null) {
            WebDavSettings.setLibraryId(s.historySyncLibraryId)
            PlayHistorySyncSettings.resetDeviceId()
        }
        // 在线歌词/音乐 API 与 Assrt 字幕 token（仅非空时覆盖）
        s.lrcApiUrl?.let { if (it.isNotBlank()) LrcApiSettings.apiUrl = it }
        s.lrcApiAuth?.let { if (it.isNotBlank()) LrcApiSettings.apiAuth = it }
        s.assrtToken?.let { if (it.isNotBlank()) SubtitleSettings.assrtToken = it }
    }

    override fun describe(data: Any?): String? {
        if (data == null) return null
        return "应用设置: 已恢复"
    }
}

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
    // 字幕
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
    // 在线歌词/音乐 API 配置（LrcApiSettings）— v2 从 BackupData 顶层并入
    val lrcApiUrl: String? = null,
    val lrcApiAuth: String? = null,
    // Assrt 字幕搜索 API token（SubtitleSettings.assrtToken）— v2 从 BackupData 顶层并入
    val assrtToken: String? = null,
)
