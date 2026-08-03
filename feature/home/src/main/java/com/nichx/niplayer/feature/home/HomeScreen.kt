package com.nichx.niplayer.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiBottomBar
import com.nichx.niplayer.designsystem.components.NiBottomBarTab
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.feature.home.home.HomeTabScreen
import com.nichx.niplayer.feature.home.library.FileBrowserOverlay
import com.nichx.niplayer.feature.home.library.LibraryScreen
import com.nichx.niplayer.feature.home.settings.SettingsScreen
import com.nichx.niplayer.navigation.Routes

private enum class TabKey { HOME, LIBRARY, SETTINGS }

@Composable
fun HomeScreen(
    onNavigateToGlobal: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToPlayHistory: () -> Unit = {},
    onNavigateToQuickAccess: () -> Unit = {},
    onPlayVideo: () -> Unit = {},
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit = { _, _ -> },
    pendingFileBrowser: Pair<Int, String>? = null,
    onPendingFileBrowserConsumed: () -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(TabKey.HOME) }
    var fbStorageId by rememberSaveable { mutableIntStateOf(0) }
    var fbPath by rememberSaveable { mutableStateOf("") }

    // 底部导航栏最大宽度限制，与首页正文最大宽度保持对齐
    val bottomBarMaxWidth = when (LocalNiWindowSizeClass.current.width) {
        NiWindowWidthSizeClass.Compact -> Dp.Unspecified
        NiWindowWidthSizeClass.Medium -> 720.dp
        NiWindowWidthSizeClass.Expanded -> 960.dp
    }

    val tabs = HomeTab.entries.map { tab ->
        NiBottomBarTab(
            route = tab.route,
            label = tab.label,
            selectedIcon = tab.selectedIcon,
            unselectedIcon = tab.unselectedIcon,
        )
    }

    val currentRoute = when (currentTab) {
        TabKey.HOME -> HomeTab.HOME.route
        TabKey.LIBRARY -> HomeTab.LIBRARY.route
        TabKey.SETTINGS -> HomeTab.SETTINGS.route
    }

    val onTabSelected: (NiBottomBarTab) -> Unit = { tab ->
        currentTab = when (tab.route) {
            HomeTab.HOME.route -> TabKey.HOME
            HomeTab.LIBRARY.route -> TabKey.LIBRARY
            else -> TabKey.SETTINGS
        }
    }

    val openFileBrowser: (Int, String) -> Unit = { storageId, path ->
        fbStorageId = storageId
        fbPath = path
        if (currentTab != TabKey.LIBRARY) {
            currentTab = TabKey.LIBRARY
        }
    }

    val closeFileBrowser: () -> Unit = {
        fbStorageId = 0
        fbPath = ""
    }

    // 外部页面（快速访问/搜索）请求打开文件浏览器：
    // 回到根路由后由这里消费，复用 overlay 文件浏览器并保留底部导航栏
    LaunchedEffect(pendingFileBrowser) {
        val pending = pendingFileBrowser ?: return@LaunchedEffect
        openFileBrowser(pending.first, pending.second)
        onPendingFileBrowserConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 内容层 + 底部导航栏（大屏下限制最大宽度并与正文对齐，避免拉伸过宽）
        HomeTabContent(
            currentTab = currentTab,
            fbStorageId = fbStorageId,
            fbPath = fbPath,
            onCloseFileBrowser = closeFileBrowser,
            onOpenFileBrowser = openFileBrowser,
            onNavigateToGlobal = onNavigateToGlobal,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToPlayHistory = onNavigateToPlayHistory,
            onNavigateToQuickAccess = onNavigateToQuickAccess,
            onPlayVideo = onPlayVideo,
            onNavigateToImageViewer = onNavigateToImageViewer,
            onNavigateToStoragePlus = onNavigateToStoragePlus,
        )

        NiBottomBar(
            tabs = tabs,
            currentRoute = currentRoute,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = bottomBarMaxWidth),
        )
    }
}

/**
 * Tab 内容块：各页面切换动画 + 文件浏览器 overlay。
 * 大屏布局（侧边导航）与紧凑布局（底部导航）共用，避免内容层重复。
 */
@Composable
private fun HomeTabContent(
    currentTab: TabKey,
    fbStorageId: Int,
    fbPath: String,
    onCloseFileBrowser: () -> Unit,
    onOpenFileBrowser: (Int, String) -> Unit,
    onNavigateToGlobal: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: () -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToImageViewer: () -> Unit,
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit,
) {
    Crossfade(
        targetState = currentTab,
        animationSpec = tween(220),
        label = "tabCrossfade",
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars),
    ) { tab ->
        when (tab) {
            TabKey.HOME -> HomeTabScreen(
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToPlayHistory = onNavigateToPlayHistory,
                onNavigateToQuickAccess = onNavigateToQuickAccess,
                onNavigateToStorageFile = onOpenFileBrowser,
                onPlayVideo = onPlayVideo,
                onNavigateToSettings = { onNavigateToGlobal(Routes.User.SWITCH_THEME) },
            )
            TabKey.LIBRARY -> {
                if (fbStorageId <= 0) {
                    LibraryScreen(
                        onNavigateToStorageFile = onOpenFileBrowser,
                        onNavigateToStoragePlus = onNavigateToStoragePlus,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
            TabKey.SETTINGS -> SettingsScreen(
                onNavigateToGlobal = onNavigateToGlobal,
            )
        }
    }

    AnimatedVisibility(
        visible = currentTab == TabKey.LIBRARY && fbStorageId > 0,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
    ) {
        if (fbStorageId > 0) {
            FileBrowserOverlay(
                storageId = fbStorageId,
                initialPath = fbPath,
                onBack = onCloseFileBrowser,
                onPlayVideo = onPlayVideo,
                onNavigateToImageViewer = onNavigateToImageViewer,
                onNavigateToDownloadManager = { onNavigateToGlobal(Routes.Stream.DOWNLOAD_MANAGER) },
            )
        }
    }
}

private data class FileBrowserState(
    val storageId: Int,
    val initialPath: String = "",
)
