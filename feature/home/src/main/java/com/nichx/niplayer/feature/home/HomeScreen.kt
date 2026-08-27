package com.nichx.niplayer.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nichx.niplayer.designsystem.components.LocalHazeState
import com.nichx.niplayer.designsystem.components.NiBottomBarTab
import com.nichx.niplayer.designsystem.components.NiGlassBarItem
import com.nichx.niplayer.designsystem.components.NiGlassBottomBar
import com.nichx.niplayer.designsystem.components.niHazeSource
import com.nichx.niplayer.designsystem.components.rememberNiHazeState
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.feature.home.home.HomeTabScreen
import com.nichx.niplayer.feature.home.library.FileBrowserOverlay
import com.nichx.niplayer.feature.home.library.LibraryScreen
import com.nichx.niplayer.feature.home.settings.SettingsScreen
import com.nichx.niplayer.navigation.Routes
import kotlinx.coroutines.launch

private enum class TabKey { HOME, LIBRARY, SETTINGS }

@Composable
fun HomeScreen(
    onNavigateToGlobal: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToPlayHistory: () -> Unit = {},
    onNavigateToQuickAccess: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onOpenPlaylist: (Int) -> Unit = {},
    onPlayVideo: () -> Unit = {},
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit = { _, _ -> },
    pendingFileBrowser: Pair<Int, String>? = null,
    onPendingFileBrowserConsumed: () -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(TabKey.HOME) }
    var fbStorageId by rememberSaveable { mutableIntStateOf(0) }
    var fbPath by rememberSaveable { mutableStateOf("") }
    val tabKeys = arrayOf(TabKey.HOME, TabKey.LIBRARY, TabKey.SETTINGS)
    val coroutineScope = rememberCoroutineScope()

    // 三个 Tab 用原生 HorizontalPager 承载（参考 legado-with-MD3）：
    // - 页面自带 swipe 切换，系统保证可靠，不再依赖自研拖拽；
    // - beyondViewportPageCount 让三个页常驻，切换不重建（避免首点卡顿）。
    val pagerState: PagerState = rememberPagerState(
        initialPage = tabKeys.indexOf(currentTab).coerceAtLeast(0),
        pageCount = { tabKeys.size },
    )

    // pager -> currentTab：页面滑动后同步选中态（供底栏高亮/文件浏览浮层使用）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentTab = tabKeys[page.coerceIn(0, tabKeys.size - 1)]
        }
    }

    // 底部导航栏最大宽度限制，与首页正文最大宽度保持对齐
    val bottomBarMaxWidth = when (LocalNiWindowSizeClass.current.width) {
        NiWindowWidthSizeClass.Compact -> Dp.Unspecified
        NiWindowWidthSizeClass.Medium -> 720.dp
        NiWindowWidthSizeClass.Expanded -> 960.dp
    }

    val tabs = HomeTab.entries.map { tab ->
        NiBottomBarTab(
            route = tab.route,
            label = stringResource(tab.labelRes),
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
        val index = tabs.indexOf(tab)
        if (index >= 0 && index != pagerState.currentPage) {
            coroutineScope.launch { pagerState.animateScrollToPage(index) }
        }
    }

    val openFileBrowser: (Int, String) -> Unit = { storageId, path ->
        fbStorageId = storageId
        fbPath = path
        coroutineScope.launch { pagerState.animateScrollToPage(TabKey.LIBRARY.ordinal) }
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

    // 创建共享 Haze 状态：内容层（niHazeSource）与浮层共享，实现真实背景模糊
    val hazeState = rememberNiHazeState()
    // 玻璃底栏背景画布：先铺一层 surface 作为统一底色（保证所有页面底栏观感一致，
    // 避免空白页面只透出底层导致透明度偏高），再捕获页面内容（drawContent）供模糊。
    val floatingBarSurface = MaterialTheme.colorScheme.surface
    val floatingBarBackdrop = rememberLayerBackdrop {
        drawRect(floatingBarSurface)
        drawContent()
    }
    // 底部系统导航栏高度：玻璃底栏悬浮在其上方，需叠加 inset 做到真正的"贴底"
    val bottomNavInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 内容层：标记为 haze 模糊源 + 玻璃底栏的 backdrop 背景源
            HomeTabContent(
            pagerState = pagerState,
            currentTab = currentTab,
            fbStorageId = fbStorageId,
            fbPath = fbPath,
            onCloseFileBrowser = closeFileBrowser,
            onOpenFileBrowser = openFileBrowser,
            onNavigateToGlobal = onNavigateToGlobal,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToPlayHistory = onNavigateToPlayHistory,
            onNavigateToQuickAccess = onNavigateToQuickAccess,
            onOpenPlaylists = onOpenPlaylists,
            onOpenPlaylist = onOpenPlaylist,
            onPlayVideo = onPlayVideo,
            onNavigateToImageViewer = onNavigateToImageViewer,
            onNavigateToStoragePlus = onNavigateToStoragePlus,
            modifier = Modifier
                .niHazeSource(hazeState)
                .layerBackdrop(floatingBarBackdrop),
        )

        // 悬浮液态玻璃底栏（完全复刻 legado-with-MD3 FloatingBottomBar）
        NiGlassBottomBar(
            selectedIndex = { pagerState.targetPage },
            onSelected = { index ->
                tabs.getOrNull(index)?.let(onTabSelected)
            },
            onReselected = { index ->
                // 再次点按媒体库按钮：关闭文件浏览页，回到存储源列表
                if (tabKeys[index.coerceIn(0, 2)] == TabKey.LIBRARY && fbStorageId > 0) {
                    closeFileBrowser()
                }
            },
            backdrop = floatingBarBackdrop,
            tabsCount = tabs.size,
            isBlurEnabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = bottomBarMaxWidth)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp + bottomNavInset),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = currentRoute == tab.route
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
                NiGlassBarItem(
                    onClick = { onTabSelected(tab) },
                ) {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        }
    }
}

/**
 * Tab 内容块：各页面切换动画 + 文件浏览器 overlay。
 * 大屏布局（侧边导航）与紧凑布局（底部导航）共用，避免内容层重复。
 */
@Composable
private fun HomeTabContent(
    pagerState: PagerState,
    currentTab: TabKey,
    fbStorageId: Int,
    fbPath: String,
    onCloseFileBrowser: () -> Unit,
    onOpenFileBrowser: (Int, String) -> Unit,
    onNavigateToGlobal: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: () -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToImageViewer: () -> Unit,
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 用同一个 Box 承载 hazeSource + layerBackdrop，让 Tab 内容与文件浏览 overlay
    // 都成为玻璃底栏的模糊来源。若 overlay(AnimatedVisibility) 不在 backdrop 捕获范围内，
    // 底栏在文件浏览页会“无内容可糊”→ 表现为完全没有模糊效果。
    Box(modifier = modifier.fillMaxSize()) {
        // 原生 HorizontalPager（参考 legado-with-MD3）：Tab 页自带 swipe 切换，
        // beyondViewportPageCount 让三个页常驻、切换只平移不重建。
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) { page ->
            when (page) {
                0 -> Box(modifier = Modifier.fillMaxSize()) {
                    HomeTabScreen(
                        onNavigateToSearch = onNavigateToSearch,
                        onNavigateToPlayHistory = onNavigateToPlayHistory,
                        onNavigateToQuickAccess = onNavigateToQuickAccess,
                        onOpenPlaylists = onOpenPlaylists,
                        onOpenPlaylist = onOpenPlaylist,
                        onNavigateToStorageFile = onOpenFileBrowser,
                        onPlayVideo = onPlayVideo,
                        onNavigateToSettings = { onNavigateToGlobal(Routes.User.SWITCH_THEME) },
                    )
                }
                1 -> if (fbStorageId <= 0) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LibraryScreen(
                            onNavigateToStorageFile = onOpenFileBrowser,
                            onNavigateToStoragePlus = onNavigateToStoragePlus,
                        )
                    }
                } else {
                    // 文件浏览浮层（FileBrowserOverlay）覆盖其上，此处保持透明
                    Box(modifier = Modifier.fillMaxSize())
                }
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onNavigateToGlobal = onNavigateToGlobal,
                    )
                }
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
}

private data class FileBrowserState(
    val storageId: Int,
    val initialPath: String = "",
)
