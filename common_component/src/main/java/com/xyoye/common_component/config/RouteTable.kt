package com.xyoye.common_component.config

/**
 * Created by xyoye on 2020/9/21.
 *
 * 路由表
 */

object RouteTable {

    object Local {
        const val MediaFragment = "/local/media_fragment"
        const val MineFragment = "/local/mine_fragment"
        const val BindExtraSource = "/local/bind_extra_source"
        const val PlayHistory = "/local/play_history"
        const val QuickAccess = "/local/quick_access"
        const val ShooterSubtitle = "/local/shooter_subtitle"
    }

    object Scrape {
        const val ScrapeMedia = "/scrape/media"
        const val ScrapeDetail = "/scrape/detail"
        const val MuluSetting = "/scrape/mulu_setting"
        const val SearchMatch = "/scrape/search_match"
        const val ScrapeMediaLegacy = "/scrape/media_legacy"
    }

    object User {
        const val PersonalFragment = "/user/personal_fragment"
        const val SettingPlayer = "/user/setting_player"
        const val SettingApp = "/user/setting_app"
        const val ThumbnailSetting = "/user/thumbnail_setting"
        const val WebView = "/user/web_view"
        const val ScanManager = "/user/scan_manager"
        const val CacheManager = "/user/cache_manager"
        const val CommonManager = "/user/common_manager"
        const val FileBrowserSetting = "/user/file_browser_setting"
        const val MediaLibrarySetting = "/user/media_library_setting"
        const val MediaLibraryDetail = "/user/media_library_detail"
        const val License = "/user/license"
        const val SwitchTheme = "/user/switch_theme"
        const val BackupManager = "/user/backup_manager"
        const val MusicMetadataApi = "/user/music_metadata_api"
    }

    object Player {
        const val Player = "/player/player_interceptor"
        const val PlayerCenter = "/player/player"
        const val AudioPlayer = "/player/audio_player"
    }

    object Stream {
        const val StorageFile = "/stream/storage_file"
        const val StorageFileProvider = "/stream/storage_file/provider"
        const val StoragePlus = "/stream/storage_plus"
        const val DownloadManager = "/stream/download_manager"
    }

    object ImageViewer {
        const val Viewer = "/image_viewer/viewer"
    }
}