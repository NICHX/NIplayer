package com.nichx.niplayer.feature.home.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nichx.niplayer.navigation.Routes

/**
 * 媒体库 tab 的内部子返回栈：
 * 媒体库列表（[Routes.Home.LIBRARY]）→ 文件浏览（[Routes.Stream.STORAGE_FILE_ROUTE]）。
 *
 * 位于 Home 的媒体库 pager 页内；由于 pager 常驻，切到其他 tab 时此子栈随媒体库 tab
 * 完整保留在后台，切回媒体库即恢复文件浏览层级。
 *
 * @param pendingFileBrowser 由搜索/快速访问等外部页发出的"打开文件浏览"待办请求，
 *   在此子栈组合后于媒体库栈内 push 文件浏览。
 */
@Composable
fun LibraryTabNavHost(
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToImageViewer: () -> Unit,
    onNavigateToDownloadManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    // 文件浏览多选态透传到 Home/MainActivity，进入多选时隐藏底栏与音乐条
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit = {},
    pendingFileBrowser: Pair<Int, String>? = null,
    onPendingFileBrowserConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // 外部页请求打开文件浏览：在媒体库子栈 push
    LaunchedEffect(pendingFileBrowser) {
        val pending = pendingFileBrowser ?: return@LaunchedEffect
        navController.navigate(Routes.Stream.storageFileRoute(pending.first, pending.second))
        onPendingFileBrowserConsumed()
    }

    val openFileBrowser: (Int, String) -> Unit = { storageId, path ->
        navController.navigate(Routes.Stream.storageFileRoute(storageId, path))
    }

    NavHost(navController = navController, startDestination = Routes.Home.LIBRARY) {
        composable(Routes.Home.LIBRARY) {
            LibraryScreen(
                onNavigateToStorageFile = openFileBrowser,
                onNavigateToStoragePlus = onNavigateToStoragePlus,
            )
        }
        composable(
            route = Routes.Stream.STORAGE_FILE_ROUTE,
            arguments = listOf(
                navArgument("storageId") {
                    type = NavType.IntType
                },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            FileBrowserScreen(
                storageId = backStackEntry.arguments?.getInt("storageId") ?: 0,
                initialPath = backStackEntry.arguments?.getString("path") ?: "",
                onBack = { navController.popBackStack() },
                onPlayVideo = onPlayVideo,
                onNavigateToImageViewer = onNavigateToImageViewer,
                onNavigateToDownloadManager = onNavigateToDownloadManager,
                onNavigateToSettings = onNavigateToSettings,
                onFileBrowserMultiSelectChanged = onFileBrowserMultiSelectChanged,
            )
        }
    }
}