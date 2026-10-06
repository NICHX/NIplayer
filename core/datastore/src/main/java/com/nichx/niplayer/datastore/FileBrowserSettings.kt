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
 * - [viewMode]：视图模式（列表/网格/画廊/平铺列表），默认列表
 * - [gridColumns]：网格视图列数，默认自适应（按宽度推导）
 * - [galleryColumns]：画廊视图列数（方形与瀑布流共用），默认自适应（按宽度推导）
 * - [galleryLayout]：画廊视图布局（方形/瀑布流），默认方形
 * - [showFileTypeBadge]：卡片显示文件类型角标（视频/音频/图片），默认关闭
 * - [showFileSizeBadge]：卡片显示文件大小角标，默认关闭
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
    private const val KEY_SHOW_TYPE_BADGE = "file_browser_show_type_badge"
    private const val KEY_SHOW_SIZE_BADGE = "file_browser_show_size_badge"
    private const val KEY_HIDE_THUMB_FOLDER = "hide_thumb_folder"
    private const val KEY_HIDE_NO_MEDIA_FOLDERS = "hide_no_media_folders"
    private const val KEY_MEDIA_FILTER = "file_media_filter"
    private const val KEY_VIEW_MODE = "file_browser_view_mode"
    private const val KEY_GRID_COLUMNS = "file_browser_grid_columns"
    private const val KEY_GALLERY_COLUMNS = "file_browser_gallery_columns"
    private const val KEY_GALLERY_LAYOUT = "file_browser_gallery_layout"
    // 旧版布尔视图模式 key（true=网格, false=列表），首次读取新枚举时做一次迁移
    private const val KEY_LEGACY_IS_GRID_VIEW = "file_browser_is_grid_view"

    /** 网格列数「自适应」哨兵值：列数按可用宽度推导，不做手动覆盖。 */
    const val GRID_COLUMNS_AUTO = 0

    /** 网格列数手动覆盖的全局上限（实际可用上限按窗口宽度类收窄，见 gridColumnRange）。 */
    const val GRID_COLUMNS_MAX = 8

    /** 画廊列数手动覆盖的全局上限（实际可用上限按窗口宽度类收窄，见 galleryColumnRange）。 */
    const val GALLERY_COLUMNS_MAX = 10

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
     * 文件浏览视图模式：列表 / 网格 / 画廊 / 平铺列表。
     *
     * [GALLERY] 用手机相册式密集方形格子展示图片与视频媒体文件，
     * 非媒体文件（音频/文档）隐藏，文件夹保底保留瓦片以便继续导航。
     *
     * [FLAT_LIST]（平铺列表）在同一页面以列表样式展示当前层级所有文件夹，
     * 文件夹可折叠，展开后内联显示其下子项（类似 macOS 访达的列表视图）。
     */
    enum class ViewMode(val value: Int) {
        LIST(0),
        GRID(1),
        GALLERY(2),
        FLAT_LIST(3);

        companion object {
            fun fromValue(v: Int): ViewMode = entries.find { it.value == v } ?: LIST
        }
    }

    /**
     * 画廊视图布局：
     * [SQUARE] 手机相册式统一方形格子；[WATERFALL] 按缩略图/图片原始宽高比错落排布的瀑布流。
     */
    enum class GalleryLayout(val value: Int) {
        SQUARE(0),
        WATERFALL(1);

        companion object {
            fun fromValue(v: Int): GalleryLayout = entries.find { it.value == v } ?: SQUARE
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

    /** 是否在文件卡片上显示文件类型角标（视频/音频/图片），默认关闭。 */
    var showFileTypeBadge: Boolean
        get() = mmkv.decodeBool(KEY_SHOW_TYPE_BADGE, false)
        set(value) {
            mmkv.encode(KEY_SHOW_TYPE_BADGE, value)
            _sortFlow.value = _sortFlow.value.copy(showFileTypeBadge = value)
        }

    /** 是否在文件卡片上显示文件大小角标，默认关闭。 */
    var showFileSizeBadge: Boolean
        get() = mmkv.decodeBool(KEY_SHOW_SIZE_BADGE, false)
        set(value) {
            mmkv.encode(KEY_SHOW_SIZE_BADGE, value)
            _sortFlow.value = _sortFlow.value.copy(showFileSizeBadge = value)
        }

    /** 是否隐藏应用生成的 .thumb 缩略图文件夹，默认隐藏（即便开启了显示隐藏文件也不展示）。 */
    var hideThumbFolder: Boolean
        get() = mmkv.decodeBool(KEY_HIDE_THUMB_FOLDER, true)
        set(value) {
            mmkv.encode(KEY_HIDE_THUMB_FOLDER, value)
            _sortFlow.value = _sortFlow.value.copy(hideThumbFolder = value)
        }

    /**
     * 是否隐藏不含媒体文件的文件夹，默认关闭。
     *
     * 开启后文件夹内（含各级子目录）没有任何视频/音频/图片时该文件夹不展示，
     * 判定结果由文件浏览 ViewModel 异步扫描并按路径缓存。
     */
    var hideNoMediaFolders: Boolean
        get() = mmkv.decodeBool(KEY_HIDE_NO_MEDIA_FOLDERS, false)
        set(value) {
            mmkv.encode(KEY_HIDE_NO_MEDIA_FOLDERS, value)
            _sortFlow.value = _sortFlow.value.copy(hideNoMediaFolders = value)
        }

    /** 文件浏览视图模式，默认列表。 */
    var viewMode: ViewMode
        get() {
            // 旧版布尔 key 存在且新枚举 key 未写入时做一次迁移（true=网格, false=列表）
            if (!mmkv.contains(KEY_VIEW_MODE) && mmkv.contains(KEY_LEGACY_IS_GRID_VIEW)) {
                return if (mmkv.decodeBool(KEY_LEGACY_IS_GRID_VIEW, false)) ViewMode.GRID else ViewMode.LIST
            }
            val mode = ViewMode.fromValue(mmkv.decodeInt(KEY_VIEW_MODE, ViewMode.LIST.value))
            // 平铺列表为实验性功能，未开启时回退到列表模式（持久化值保留，开启后可恢复）
            return if (mode == ViewMode.FLAT_LIST && !ExperimentalSettings.flatListViewEnabled) ViewMode.LIST else mode
        }
        set(value) {
            mmkv.remove(KEY_LEGACY_IS_GRID_VIEW)
            mmkv.encode(KEY_VIEW_MODE, value.value)
            _sortFlow.value = _sortFlow.value.copy(viewMode = value)
        }

    /**
     * 网格视图列数，默认 [GRID_COLUMNS_AUTO]（自适应）。
     *
     * - [GRID_COLUMNS_AUTO]：列数按可用宽度自动推导（维持既有体验，大屏自然显示更多列）。
     * - 数值：用户手动指定的列数，读写时统一收敛到 `0..GRID_COLUMNS_MAX`；
     *   实际展示时再按窗口宽度类收窄上限（手机 4 / 平板 6 / 大屏 8）。
     */
    var gridColumns: Int
        get() = mmkv.decodeInt(KEY_GRID_COLUMNS, GRID_COLUMNS_AUTO).coerceIn(GRID_COLUMNS_AUTO, GRID_COLUMNS_MAX)
        set(value) {
            val clamped = value.coerceIn(GRID_COLUMNS_AUTO, GRID_COLUMNS_MAX)
            mmkv.encode(KEY_GRID_COLUMNS, clamped)
            _sortFlow.value = _sortFlow.value.copy(gridColumns = clamped)
        }

    /**
     * 画廊视图列数，默认 [GRID_COLUMNS_AUTO]（自适应），方形与瀑布流布局共用。
     *
     * - [GRID_COLUMNS_AUTO]：列数按可用宽度自动推导，瀑布流默认即此值（不锁定固定列数）。
     * - 数值：用户手动指定的列数，实际展示时再按窗口宽度类收窄上限（见 galleryColumnRange）。
     */
    var galleryColumns: Int
        get() = mmkv.decodeInt(KEY_GALLERY_COLUMNS, GRID_COLUMNS_AUTO).coerceIn(GRID_COLUMNS_AUTO, GALLERY_COLUMNS_MAX)
        set(value) {
            val clamped = value.coerceIn(GRID_COLUMNS_AUTO, GALLERY_COLUMNS_MAX)
            mmkv.encode(KEY_GALLERY_COLUMNS, clamped)
            _sortFlow.value = _sortFlow.value.copy(galleryColumns = clamped)
        }

    /**
     * 画廊视图布局（方形/瀑布流），默认方形。
     *
     * 瀑布流为实验性功能（[ExperimentalSettings.waterfallGalleryEnabled]），未开启时回退到方形。
     */
    var galleryLayout: GalleryLayout
        get() = resolveGalleryLayout()
        set(value) {
            mmkv.encode(KEY_GALLERY_LAYOUT, value.value)
            _sortFlow.value = _sortFlow.value.copy(galleryLayout = value)
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

    /**
     * 解析画廊布局：瀑布流为实验性功能，未开启时回退到方形（持久化值保留，开启后可恢复）。
     * 与 [viewMode] 对平铺列表的处理保持一致。
     */
    private fun resolveGalleryLayout(): GalleryLayout {
        val layout = GalleryLayout.fromValue(mmkv.decodeInt(KEY_GALLERY_LAYOUT, GalleryLayout.SQUARE.value))
        return if (layout == GalleryLayout.WATERFALL && !ExperimentalSettings.waterfallGalleryEnabled) {
            GalleryLayout.SQUARE
        } else {
            layout
        }
    }

    private fun loadSortConfig(): SortConfig {
        val sortBy = SortBy.fromValue(mmkv.decodeInt(KEY_SORT_BY, SortBy.NAME.value))
        val ascending = mmkv.decodeBool(KEY_SORT_ASCENDING, true)
        val showOnlyMediaFiles = mmkv.decodeBool(KEY_SHOW_ONLY_MEDIA, false)
        val showHiddenFiles = mmkv.decodeBool(KEY_SHOW_HIDDEN_FILES, false)
        val hideThumbFolder = mmkv.decodeBool(KEY_HIDE_THUMB_FOLDER, true)
        val hideNoMediaFolders = mmkv.decodeBool(KEY_HIDE_NO_MEDIA_FOLDERS, false)
        val mediaFilter = MediaFilter.fromValue(mmkv.decodeInt(KEY_MEDIA_FILTER, MediaFilter.ALL.value))
        val gridColumns = mmkv.decodeInt(KEY_GRID_COLUMNS, GRID_COLUMNS_AUTO).coerceIn(GRID_COLUMNS_AUTO, GRID_COLUMNS_MAX)
        val galleryColumns = mmkv.decodeInt(KEY_GALLERY_COLUMNS, GRID_COLUMNS_AUTO).coerceIn(GRID_COLUMNS_AUTO, GALLERY_COLUMNS_MAX)
        val galleryLayout = resolveGalleryLayout()
        val showFileTypeBadge = mmkv.decodeBool(KEY_SHOW_TYPE_BADGE, false)
        val showFileSizeBadge = mmkv.decodeBool(KEY_SHOW_SIZE_BADGE, false)
        return SortConfig(sortBy, ascending, showOnlyMediaFiles, showHiddenFiles, hideThumbFolder, hideNoMediaFolders, mediaFilter, viewMode, gridColumns, showFileTypeBadge, showFileSizeBadge, galleryColumns, galleryLayout)
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
    /** 隐藏不含媒体文件的文件夹（含子目录），默认为 false。 */
    val hideNoMediaFolders: Boolean = false,
    /** 文件类型过滤，默认为全部。 */
    val mediaFilter: FileBrowserSettings.MediaFilter = FileBrowserSettings.MediaFilter.ALL,
    /** 文件浏览视图模式：列表/网格/画廊，默认列表。 */
    val viewMode: FileBrowserSettings.ViewMode = FileBrowserSettings.ViewMode.LIST,
    /** 网格视图列数，默认自适应（[FileBrowserSettings.GRID_COLUMNS_AUTO]）。 */
    val gridColumns: Int = FileBrowserSettings.GRID_COLUMNS_AUTO,
    /** 文件卡片显示文件类型角标，默认为 false。 */
    val showFileTypeBadge: Boolean = false,
    /** 文件卡片显示文件大小角标，默认为 false。 */
    val showFileSizeBadge: Boolean = false,
    /** 画廊视图列数（方形与瀑布流共用），默认自适应（[FileBrowserSettings.GRID_COLUMNS_AUTO]）。 */
    val galleryColumns: Int = FileBrowserSettings.GRID_COLUMNS_AUTO,
    /** 画廊视图布局（方形/瀑布流），默认方形。 */
    val galleryLayout: FileBrowserSettings.GalleryLayout = FileBrowserSettings.GalleryLayout.SQUARE,
)
