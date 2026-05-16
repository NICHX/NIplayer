package com.xyoye.common_component.config

/**
 * Created by xyoye on 2020/9/21.
 *
 * 路由表
 */

object RouteTable {

    object Anime {
        const val Search = "/anime/search"
        const val HomeFragment = "/anime/home_fragment"
        const val AnimeDetail = "/anime/anime_detail"
        const val AnimeSeason = "/anime/anime_season"
        const val AnimeFollow = "/anime/follow"
        const val AnimeTag = "/anime/tag"
        const val AnimeHistory = "/anime/history"
    }

    object Local {
        const val MediaFragment = "/local/media_fragment"
        const val MineFragment = "/local/mine_fragment"
        const val BindExtraSource = "/local/bind_extra_source"
        const val PlayHistory = "/local/play_history"
        const val ShooterSubtitle = "/local/shooter_subtitle"
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
        const val License = "/user/license"
        const val SwitchTheme = "/user/switch_theme"
    }

    object Player {
        const val Player = "/player/player_interceptor"
        const val PlayerCenter = "/player/player"
    }

    object Stream {
        const val StorageFile = "/stream/storage_file"
        const val StorageFileProvider = "/stream/storage_file/provider"
        const val StoragePlus = "/stream/storage_plus"
    }

    object ImageViewer {
        const val Viewer = "/image_viewer/viewer"
    }
}