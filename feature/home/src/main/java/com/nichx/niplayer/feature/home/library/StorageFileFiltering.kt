package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.SortConfig
import com.nichx.niplayer.storage.StorageFile

/**
 * 过滤 + 排序：先按 [FileBrowserSettings.showHiddenFiles] 过滤隐藏文件，
 * 再按 [FileBrowserSettings.showOnlyMediaFiles] 过滤非媒体文件，
 * 再按 [FileBrowserSettings.mediaFilter] 过滤媒体类型，
 * 再按 [FileBrowserSettings.hideNoMediaFolders] 过滤无媒体文件夹，
 * 最后按 [FileBrowserSettings] 排序。
 *
 * 无媒体文件夹过滤依赖 ViewModel 异步扫描的判定缓存：已知"无媒体"的文件夹移除，
 * 未判定的保留（fail-open），扫描完成后由 [startFolderVerdictScan] 渐进式移除。
 *
 * 排序规则：目录始终在前；同类型内按 [SortConfig.sortBy] 排序，
 * [SortConfig.ascending] 控制升降序。名称排序用自然排序（不区分大小写，
 * 连续数字按数值比较，如 "2" < "10"）。
 */
internal fun filterAndSortStorageFiles(
    files: List<StorageFile>,
    config: SortConfig,
    folderMediaVerdicts: Map<String, Boolean>,
): List<StorageFile> {
    // 始终过滤应用生成的 .thumb 缩略图文件夹（即便开启显示隐藏文件也不展示，可通过开关放行）
    val thumbFiltered = if (config.hideThumbFolder) {
        files.filter { it.name != ".thumb" }
    } else {
        files
    }
    val hiddenFiltered = if (config.showHiddenFiles) {
        thumbFiltered
    } else {
        thumbFiltered.filter { !it.name.startsWith('.') && !it.isHidden }
    }
    val mediaFiltered = if (config.showOnlyMediaFiles) {
        hiddenFiltered.filter { it.isDirectory || isMediaFile(it) }
    } else {
        hiddenFiltered
    }
    val typeFiltered = when (config.mediaFilter) {
        FileBrowserSettings.MediaFilter.ALL -> mediaFiltered
        FileBrowserSettings.MediaFilter.VIDEO ->
            mediaFiltered.filter { it.isDirectory || MediaFileTypes.isVideoFile(it.name) }
        FileBrowserSettings.MediaFilter.AUDIO ->
            mediaFiltered.filter { it.isDirectory || MediaFileTypes.isAudioFile(it.name) }
        FileBrowserSettings.MediaFilter.IMAGE ->
            mediaFiltered.filter { it.isDirectory || MediaFileTypes.isImageFile(it.name) }
    }
    val verdictFiltered = if (config.hideNoMediaFolders) {
        typeFiltered.filter { !it.isDirectory || folderMediaVerdicts[it.path.trimEnd('/')] != false }
    } else {
        typeFiltered
    }

    return verdictFiltered.sortedWith(storageFileComparator(config))
}

/**
 * 判断文件是否为媒体文件（视频/音频/图片），排除 sidecar 缩略图文件。
 *
 * BUG-T-m9 修复：当"仅显示媒体文件"开启时，侧车缩略图文件（如 `{name}-thumb.jpg`、
 * `{name}-cover.jpg`）仅扩展名是图片但实际是缩略图缓存，不应显示在文件列表中。
 */
internal fun isMediaFile(file: StorageFile): Boolean =
    !isSidecarThumbnailFile(file.name) && (
        MediaFileTypes.isVideoFile(file.name) ||
            MediaFileTypes.isAudioFile(file.name) ||
            MediaFileTypes.isImageFile(file.name)
        )

/**
 * 判断文件名是否为 sidecar 缩略图/封面文件。
 *
 * 匹配 [ThumbnailManager.uploadThumbnail] / [ThumbnailManager.uploadAudioCover]
 * 生成的服务端缓存文件名模式：
 * - 视频缩略图：`{视频去扩展名}-thumb.jpg` / `-thumb.jpeg`
 * - 音频封面：`{完整文件名}-cover.jpg` / `-cover.jpeg`
 */
internal fun isSidecarThumbnailFile(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith("-thumb.jpg") || lower.endsWith("-thumb.jpeg") ||
        lower.endsWith("-cover.jpg") || lower.endsWith("-cover.jpeg")
}
