package com.nichx.niplayer.navigation

import android.net.Uri

/**
 * 全局路由常量。
 *
 * 替代旧仓库 common_component/config/RouteTable.kt 的 ARouter 路由表，
 * 适配 androidx.navigation.compose 的字符串路由模型。
 *
 * 命名空间沿用旧仓库 5 组划分（Local / User / Player / Stream / ImageViewer），
 * 但路径采用扁平字符串（不含旧仓库的 `/local/` 前缀斜杠），便于与
 * NavHostBuilder.composable(route = Routes.X) 配合使用。
 *
 * 阶段2 仅定义常量骨架，对应 Composable 实现将在阶段5 UI 重做时填充。
 */
object Routes {

    /**
     * 主页路由。
     *
     * v2 重构后三 Tab 划分（消除旧仓库 MediaFragment/MineFragment 展示同一 MediaLibraryEntity
     * 的冗余设计）：
     * - [HOME] 首页：信息聚合（最近播放 + 快速访问），不直接管理存储源
     * - [LIBRARY] 媒体库：存储源管理（Local/SMB/WebDAV 列表，点击进入文件浏览）
     * - [SETTINGS] 设置：设置中心
     *
     * [ROOT] 是全局导航图的主页入口路由，:app 通过 NiNavHost builder 注入
     * composable(Routes.Home.ROOT) { HomeScreen() }。
     */
    object Home {
        const val ROOT = "home"
        const val HOME = "home/home"
        const val LIBRARY = "home/library"
        const val SETTINGS = "home/settings"
    }

    /** 旧 RouteTable.Local namespace。 */
    object Local {
        const val QUICK_ACCESS = "local/quick_access"
        const val PLAY_HISTORY = "local/play_history"
        const val SEARCH = "local/search"
    }

    /** 旧 RouteTable.User namespace。 */
    object User {
        const val SETTING_PLAYER = "user/setting_player"
        const val LRCAPI = "user/lrc_api"
        const val SCAN_MANAGER = "user/scan_manager"
        const val CACHE_MANAGER = "user/cache_manager"
        const val SWITCH_THEME = "user/switch_theme"
        const val LANGUAGE = "user/language"
        const val ABOUT = "user/about"
        const val EQUALIZER = "user/equalizer"
        const val PLAYBACK_STATS = "user/playback_stats"
        const val BACKUP = "user/backup"
    }

    /**
     * 旧 RouteTable.Player namespace。
     *
     * - [GUARD]：播放路由守卫，替代旧仓库 PlayerInterceptorActivity（透明网关 Activity）。
     *   PlayerGuardViewModel peek PlaybackRequestHolder 按 isAudio 分流到 [PLAYER] / [AUDIO_PLAYER]。
     * - [PLAYER]：视频播放页（PlayerScreen，SurfaceView 渲染）
     * - [AUDIO_PLAYER]：音频播放页（AudioPlayerScreen，无 SurfaceView）
     */
    object Player {
        const val GUARD = "player/guard"
        const val PLAYER = "player/player"
        const val AUDIO_PLAYER = "player/audio_player"
    }

    /** 旧 RouteTable.Stream namespace。 */
    object Stream {
        const val STORAGE_FILE = "stream/storage_file"

        /** 带参路由模板，注册到 NavHost：`stream/storage_file/{storageId}?path={path}`。 */
        const val STORAGE_FILE_ROUTE = "$STORAGE_FILE/{storageId}?path={path}"

        /** 构造导航用的完整路由字符串：`stream/storage_file/3` 或 `stream/storage_file/3?path=%2FMovies`。 */
        fun storageFileRoute(storageId: Int, path: String = ""): String {
            val base = "$STORAGE_FILE/$storageId"
            return if (path.isNotEmpty()) "$base?path=${Uri.encode(path)}" else base
        }

        const val STORAGE_PLUS = "stream/storage_plus"

        /**
         * 带参路由模板，注册到 NavHost：
         * `stream/storage_plus?type={type}&storageId={storageId}`。
         *
         * - 新增模式：`type` 为 [MediaType.value]（如 `"smb_server"`），`storageId=0`
         * - 编辑模式：`type` 为空字符串，`storageId` 为已存在存储源 id
         */
        const val STORAGE_PLUS_ROUTE =
            "$STORAGE_PLUS?type={type}&storageId={storageId}"

        /**
         * 构造新增存储源导航路由。
         *
         * @param type [MediaType.value]，决定表单类型
         */
        fun storagePlusRoute(type: String): String = "$STORAGE_PLUS?type=$type&storageId=0"

        /**
         * 构造编辑存储源导航路由。
         *
         * @param storageId 已存在存储源 id，ViewModel 据此加载表单
         */
        fun storagePlusEditRoute(storageId: Int): String =
            "$STORAGE_PLUS?type=&storageId=$storageId"

        const val DOWNLOAD_MANAGER = "stream/download_manager"
    }

    /** 旧 RouteTable.ImageViewer namespace。 */
    object ImageViewer {
        const val VIEWER = "image_viewer/viewer"
    }

    /** 播放列表系统（扩展功能方案二）。 */
    object Playlist {
        const val LIST = "playlist/list"

        const val DETAIL = "playlist/detail"

        /** 带参路由模板，注册到 NavHost：`playlist/detail/{playlistId}`。 */
        const val DETAIL_ROUTE = "$DETAIL/{playlistId}"

        /** 构造歌单详情导航路由：`playlist/detail/3`。 */
        fun detailRoute(playlistId: Int): String = "$DETAIL/$playlistId"
    }
}
