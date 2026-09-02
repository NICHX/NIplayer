package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 文件浏览页设置持久化（MMKV + StateFlow）。
 *
 * 配置项：
 * - [sortBy]：排序字段（名称 / 修改时间 / 文件大小 / 类型），默认名称
 * - [sortAscending]：升降序，默认升序
 * - [showOnlyMediaFiles]：仅显示媒体文件（视频/音频/图片）
 * - [showHiddenFiles]：显示隐藏文件（以 . 开头的文件/文件夹）
 * - [mediaFilter]：文件类型过滤（全部/视频/音频/图片），默认全部
 *
 * 目录始终排在文件之前，不受 [sortAscending] 影响。
 *
 * 使用方式：
 * - 写入端：[com.nichx.niplayer.feature.home.library.StorageFileScreen] 排序按钮
 * - 读取端：[com.nichx.niplayer.feature.home.library.StorageFileViewModel] sortFiles()
 */
object FileBrowserSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_SORT_BY = "file_sort_by"
    private const val KEY_SORT_ASCENDING = "file_sort_ascending"
    private const val KEY_SHOW_ONLY_MEDIA = "show_only_media_files"
    private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
    private const val KEY_HIDE_THUMB_FOLDER = "hide_thumb_folder"
    private const val KEY_MEDIA_FILTER = "file_media_filter"
    private const val KEY_VIEW_MODE = "file_browser_view_mode"
    // 旧版布尔视图模式 key（true=网格, false=列表），首次读取新枚举时做一次迁移
    private const val KEY_LEGACY_IS_GRID_VIEW = "file_browser_is_grid_view"

    /** 排序字段枚举。 */
    enum class SortBy(val value: Int) {
        NAME(0),
        MODIFIED(1),
        SIZE(2),
        TYPE(3);

        companion object {
            fun fromValue(v: Int): SortBy = entries.find { it.value == v } ?: NAME
        }
    }

    /** 文件类型过滤枚举。 */
    enum class MediaFilter(val value: Int) {
        ALL(0),
        VIDEO(1),
        AUDIO(2),
        IMAGE(3);

        companion object {
            fun fromValue(v: Int): MediaFilter = entries.find { it.value == v } ?: ALL
        }
    }

    /**
     * 文件浏览视图模式：列表 / 网格 / 画廊。
     *
     * [GALLERY] 用手机相册式密集方形格子展示图片与视频媒体文件，
     * 非媒体文件（音频/文档）隐藏，文件夹保底保留瓦片以便继续导航。
     */
    enum class ViewMode(val value: Int) {
        LIST(0),
        GRID(1),
        GALLERY(2);

        companion object {
            fun fromValue(v: Int): ViewMode = entries.find { it.value == v } ?: LIST
        }
    }

    private val _sortFlow = MutableStateFlow(loadSortConfig())
    /** 排序配置 StateFlow，写入时自动更新。 */
    val sortFlow: StateFlow<SortConfig> = _sortFlow.asStateFlow()

    /** 当前排序字段。 */
    val sortBy: SortBy
        get() = _sortFlow.value.sortBy

    /** 当前是否升序。 */
    val sortAscending: Boolean
        get() = _sortFlow.value.ascending

    /** 仅显示媒体文件。 */
    var showOnlyMediaFiles: Boolean
        get() = mmkv.decodeBool(KEY_SHOW_ONLY_MEDIA, false)
        set(value) {
            mmkv.encode(KEY_SHOW_ONLY_MEDIA, value)
            _sortFlow.value = _sortFlow.value.copy(showOnlyMediaFiles = value)
        }

    /** 是否显示隐藏文件（以 . 开头的文件/文件夹），默认隐藏。 */
    var showHiddenFiles: Boolean
        get() = mmkv.decodeBool(KEY_SHOW_HIDDEN_FILES, false)
        set(value) {
            mmkv.encode(KEY_SHOW_HIDDEN_FILES, value)
            _sortFlow.value = _sortFlow.value.copy(showHiddenFiles = value)
        }

    /** 是否隐藏应用生成的 .thumb 缩略图文件夹，默认隐藏（即便开启了显示隐藏文件也不展示）。 */
    var hideThumbFolder: Boolean
        get() = mmkv.decodeBool(KEY_HIDE_THUMB_FOLDER, true)
        set(value) {
            mmkv.encode(KEY_HIDE_THUMB_FOLDER, value)
            _sortFlow.value = _sortFlow.value.copy(hideThumbFolder = value)
        }

    /** 文件浏览视图模式，默认列表。 */
    var viewMode: ViewMode
        get() {
            // 旧版布尔 key 存在且新枚举 key 未写入时做一次迁移（true=网格, false=列表）
            if (!mmkv.contains(KEY_VIEW_MODE) && mmkv.contains(KEY_LEGACY_IS_GRID_VIEW)) {
                return if (mmkv.decodeBool(KEY_LEGACY_IS_GRID_VIEW, false)) ViewMode.GRID else ViewMode.LIST
            }
            return ViewMode.fromValue(mmkv.decodeInt(KEY_VIEW_MODE, ViewMode.LIST.value))
        }
        set(value) {
            mmkv.remove(KEY_LEGACY_IS_GRID_VIEW)
            mmkv.encode(KEY_VIEW_MODE, value.value)
            _sortFlow.value = _sortFlow.value.copy(viewMode = value)
        }

    /** 设置排序字段，立即持久化并通知 StateFlow。 */
    fun setSortBy(sortBy: SortBy) {
        mmkv.encode(KEY_SORT_BY, sortBy.value)
        _sortFlow.value = _sortFlow.value.copy(sortBy = sortBy)
    }

    /** 设置升降序，立即持久化并通知 StateFlow。 */
    fun setSortAscending(ascending: Boolean) {
        mmkv.encode(KEY_SORT_ASCENDING, ascending)
        _sortFlow.value = _sortFlow.value.copy(ascending = ascending)
    }

    /** 文件类型过滤。 */
    var mediaFilter: MediaFilter
        get() = MediaFilter.fromValue(mmkv.decodeInt(KEY_MEDIA_FILTER, MediaFilter.ALL.value))
        set(value) {
            mmkv.encode(KEY_MEDIA_FILTER, value.value)
            _sortFlow.value = _sortFlow.value.copy(mediaFilter = value)
        }

    private fun loadSortConfig(): SortConfig {
        val sortBy = SortBy.fromValue(mmkv.decodeInt(KEY_SORT_BY, SortBy.NAME.value))
        val ascending = mmkv.decodeBool(KEY_SORT_ASCENDING, true)
        val showOnlyMediaFiles = mmkv.decodeBool(KEY_SHOW_ONLY_MEDIA, false)
        val showHiddenFiles = mmkv.decodeBool(KEY_SHOW_HIDDEN_FILES, false)
        val hideThumbFolder = mmkv.decodeBool(KEY_HIDE_THUMB_FOLDER, true)
        val mediaFilter = MediaFilter.fromValue(mmkv.decodeInt(KEY_MEDIA_FILTER, MediaFilter.ALL.value))
        return SortConfig(sortBy, ascending, showOnlyMediaFiles, showHiddenFiles, hideThumbFolder, mediaFilter, viewMode)
    }
}

/** 排序配置快照。 */
data class SortConfig(
    val sortBy: FileBrowserSettings.SortBy,
    val ascending: Boolean,
    /** 仅显示媒体文件（视频/音频/图片），默认为 false。 */
    val showOnlyMediaFiles: Boolean = false,
    /** 显示隐藏文件（以 . 开头的文件/文件夹），默认为 false。 */
    val showHiddenFiles: Boolean = false,
    /** 隐藏应用生成的 .thumb 缩略图文件夹，默认为 true。 */
    val hideThumbFolder: Boolean = true,
    /** 文件类型过滤，默认为全部。 */
    val mediaFilter: FileBrowserSettings.MediaFilter = FileBrowserSettings.MediaFilter.ALL,
    /** 文件浏览视图模式：列表/网格/画廊，默认列表。 */
    val viewMode: FileBrowserSettings.ViewMode = FileBrowserSettings.ViewMode.LIST,
)
