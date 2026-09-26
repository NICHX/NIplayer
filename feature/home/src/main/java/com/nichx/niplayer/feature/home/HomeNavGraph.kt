package com.nichx.niplayer.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nichx.niplayer.feature.home.history.PlayHistoryScreen
import com.nichx.niplayer.feature.home.imageviewer.ImageViewerScreen
import com.nichx.niplayer.feature.home.library.StoragePlusScreen
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessScreen
import com.nichx.niplayer.feature.home.search.SearchScreen
import com.nichx.niplayer.feature.home.settings.AboutScreen
import com.nichx.niplayer.feature.home.settings.BackupScreen
import com.nichx.niplayer.feature.home.settings.CacheManagerScreen
import com.nichx.niplayer.feature.home.settings.EqualizerSettingsScreen
import com.nichx.niplayer.feature.home.settings.ExperimentalScreen
import com.nichx.niplayer.feature.home.settings.IconScreen
import com.nichx.niplayer.feature.home.settings.LanguageScreen
import com.nichx.niplayer.feature.home.settings.MediaLibrarySettingsScreen
import com.nichx.niplayer.feature.home.settings.PlaybackStatsScreen
import com.nichx.niplayer.feature.home.settings.PlayerSettingsScreen
import com.nichx.niplayer.feature.home.settings.ScanManagerScreen
import com.nichx.niplayer.feature.home.settings.ThemeScreen
import com.nichx.niplayer.feature.home.settings.TransferScreen
import com.nichx.niplayer.navigation.Routes

/**
 * :feature:home 导航图的宿主侧状态。
 *
 * 承载需要在**多个路由之间**存活、且不属于某一个页面的状态：
 * - [pendingFileBrowser]：外部页（搜索 / 快速访问）请求「在媒体库 tab 打开文件浏览」的待办，
 *   由 QuickAccessScreen / SearchScreen 写入，回到 Home 根路由后由 HomeScreen 消费并清空。
 *
 * 由宿主经 [rememberHomeNavGraphState] 在 composition 中记住 —— 路由的 `composable {}` 内容
 * 在离开返回栈时会被销毁，状态不能放在那里。
 */
@Stable
class HomeNavGraphState internal constructor() {

    /** 待打开的 (storageId, path)；null 表示无待办。 */
    internal var pendingFileBrowser by mutableStateOf<Pair<Int, String>?>(null)
}

/** 在宿主 composition 中记住 [HomeNavGraphState]，使其跨路由存活。 */
@Composable
fun rememberHomeNavGraphState(): HomeNavGraphState = remember { HomeNavGraphState() }

/**
 * :feature:home 的导航图（**A2 架构修复：feature 聚合层**）。
 *
 * 背景：原先这 20 个路由全部在 `:app` 的 `MainActivity` 里逐个 `composable(...)` 注册，
 * 导致「新增一个设置页」必须同时改动 `:app`（`MainActivity` 直接 import 了 feature 的全部屏幕）。
 * 现在路由由 feature 自己注册，宿主只需调用本函数。
 *
 * 宿主（:app）负责提供的 3 类能力：
 * - [onPlayMedia]：媒体分流 —— 视频走独立 Activity（PlayerActivity），音频走导航内路由。
 *   该决策依赖 :feature:player 的 Activity，故由宿主注入而非本模块自行处理。
 * - [onFileBrowserMultiSelectChanged]：文件浏览多选态上抛，宿主据此隐藏音乐条。
 * - [onApplyEqualizerToPlayer] / [onApplyEqualizerLive]：均衡器设置页应用到播放器的回调
 *   （播放器实例在 :feature:player，本模块不依赖它）。
 *
 * @param navController 宿主导航控制器
 * @param state 跨路由状态（见 [HomeNavGraphState]）
 * @param onFileBrowserMultiSelectChanged 文件浏览多选态变化回调
 * @param onPlayMedia 打开媒体：true 走音频播放页，false 走视频独立 Activity
 * @param onApplyEqualizerToPlayer 均衡器开关切换后应用到播放器（含淡入淡出兜底）
 * @param onApplyEqualizerLive 均衡器参数实时应用到播放器（不淡入淡出）
 */
fun NavGraphBuilder.homeNavGraph(
    navController: NavHostController,
    state: HomeNavGraphState,
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit,
    onPlayMedia: (isAudio: Boolean) -> Unit,
    onApplyEqualizerToPlayer: () -> Unit,
    onApplyEqualizerLive: () -> Unit,
) {
    composable(
        route = Routes.Home.ROOT,
    ) {
        HomeScreen(
            onNavigateToGlobal = { route -> navController.navigate(route) },
            onNavigateToSearch = {
                navController.navigate(Routes.Browse.SEARCH)
            },
            onNavigateToPlayHistory = { filter ->
                navController.navigate(Routes.Browse.playHistoryRoute(filter))
            },
            onNavigateToQuickAccess = {
                navController.navigate(Routes.Browse.QUICK_ACCESS)
            },
            onPlayVideo = onPlayMedia,
            onNavigateToStoragePlus = { type, storageId ->
                val route = if (type != null) {
                    Routes.Storage.storagePlusRoute(type)
                } else {
                    Routes.Storage.storagePlusEditRoute(storageId)
                }
                navController.navigate(route)
            },
            onNavigateToImageViewer = {
                navController.navigate(Routes.ImageViewer.VIEWER)
            },
            onNavigateToDownloadManager = {
                navController.navigate(Routes.Storage.DOWNLOAD_MANAGER)
            },
            pendingFileBrowser = state.pendingFileBrowser,
            onPendingFileBrowserConsumed = { state.pendingFileBrowser = null },
            onFileBrowserMultiSelectChanged = onFileBrowserMultiSelectChanged,
        )
    }
    composable(
        route = Routes.Storage.STORAGE_PLUS_ROUTE,
        arguments = listOf(
            navArgument("type") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("storageId") {
                type = NavType.IntType
                defaultValue = 0
            },
        ),
    ) {
        StoragePlusScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Browse.PLAY_HISTORY_ROUTE,
        arguments = listOf(
            navArgument("filter") {
                type = NavType.IntType
                defaultValue = 0
            },
        ),
    ) { backStackEntry ->
        PlayHistoryScreen(
            initialFilterOrdinal = backStackEntry.arguments?.getInt("filter") ?: 0,
            onNavigateToPlayVideo = onPlayMedia,
        )
    }
    composable(
        route = Routes.Browse.QUICK_ACCESS,
    ) {
        QuickAccessScreen(
            onNavigateToStorageFile = { storageId, path ->
                // 交给 Home 在媒体库 tab 子栈打开文件浏览，返回栈回到快速访问页
                state.pendingFileBrowser = storageId to path
                navController.popBackStack(Routes.Home.ROOT, inclusive = false)
            },
            onNavigateToPlayer = onPlayMedia,
            onNavigateToImageViewer = {
                navController.navigate(Routes.ImageViewer.VIEWER)
            },
        )
    }
    composable(
        route = Routes.Browse.SEARCH,
    ) {
        SearchScreen(
            onBack = navController.navBack(),
            onNavigateToPlayVideo = onPlayMedia,
            onNavigateToStorageFile = { storageId, path ->
                // 交给 Home 在媒体库 tab 子栈打开文件浏览，返回栈回到搜索页
                state.pendingFileBrowser = storageId to path
                navController.popBackStack(Routes.Home.ROOT, inclusive = false)
            },
            onNavigateToImageViewer = {
                navController.navigate(Routes.ImageViewer.VIEWER)
            },
        )
    }
    composable(
        route = Routes.Settings.SWITCH_THEME,
    ) {
        ThemeScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.ICON,
    ) {
        IconScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.LANGUAGE,
    ) {
        LanguageScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.EXPERIMENTAL,
    ) {
        ExperimentalScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.SETTING_PLAYER,
    ) {
        PlayerSettingsScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.MEDIA_LIBRARY,
    ) {
        MediaLibrarySettingsScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.EQUALIZER,
    ) {
        EqualizerSettingsScreen(
            onBack = navController.navBack(),
            onApplyToPlayer = onApplyEqualizerToPlayer,
            onApplyLiveToPlayer = onApplyEqualizerLive,
        )
    }
    composable(
        route = Routes.Settings.PLAYBACK_STATS,
    ) {
        PlaybackStatsScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.BACKUP,
    ) {
        BackupScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.CACHE_MANAGER,
    ) {
        CacheManagerScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.SCAN_MANAGER,
    ) {
        ScanManagerScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Settings.ABOUT,
    ) {
        AboutScreen(onBack = navController.navBack())
    }
    composable(
        route = Routes.Storage.DOWNLOAD_MANAGER,
    ) {
        TransferScreen(
            onBack = navController.navBack(),
            onPlayVideo = onPlayMedia,
            onNavigateToImageViewer = {
                navController.navigate(Routes.ImageViewer.VIEWER)
            },
        )
    }
    composable(
        route = Routes.ImageViewer.VIEWER,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) },
        // 与普通子页一致：显式 pop 退出（淡出，无缩放），避免回退到内置 scaleOut
        popExitTransition = { fadeOut(tween(300)) },
    ) {
        ImageViewerScreen(onBack = navController.navBack())
    }
}

/**
 * 统一的「返回上一页」回调。
 *
 * `popBackStack()` 返回 Boolean（是否真的弹出了），而各 Screen 的 `onBack` 形参类型是 `() -> Unit`，
 * 因此需要一层丢弃返回值的适配。收敛到一处是为了：将来若要在返回路径上统一加行为
 * （埋点、转场控制等），只需改这里。
 */
private fun NavHostController.navBack(): () -> Unit = { popBackStack() }
