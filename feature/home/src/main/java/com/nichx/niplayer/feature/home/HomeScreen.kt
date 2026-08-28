package com.nichx.niplayer.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nichx.niplayer.designsystem.components.LocalHazeState
import com.nichx.niplayer.designsystem.components.niHazeSource
import com.nichx.niplayer.designsystem.components.rememberNiHazeState
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.feature.home.home.HomeTabScreen
import com.nichx.niplayer.feature.home.library.LibraryTabNavHost
import com.nichx.niplayer.feature.home.settings.SettingsScreen
import com.nichx.niplayer.navigation.Routes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch

private enum class TabKey { HOME, LIBRARY, SETTINGS }

@Composable
fun HomeScreen(
    onNavigateToGlobal: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToPlayHistory: (Int) -> Unit = {},
    onNavigateToQuickAccess: () -> Unit = {},
    onPlayVideo: () -> Unit = {},
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit = { _, _ -> },
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToDownloadManager: () -> Unit = {},
    // 外部页（搜索/快速访问）请求打开文件浏览：切到媒体库 tab 并在此子栈打开
    pendingFileBrowser: Pair<Int, String>? = null,
    onPendingFileBrowserConsumed: () -> Unit = {},
    // 文件浏览多选态（已结合"当前在媒体库 tab"）回传 MainActivity：供其隐藏音乐条
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(TabKey.HOME) }
    val tabKeys = arrayOf(TabKey.HOME, TabKey.LIBRARY, TabKey.SETTINGS)
    val coroutineScope = rememberCoroutineScope()

    // 三个 Tab 用原生 HorizontalPager 承载：页面自带 swipe 切换，切走常驻保留后台状态
    val pagerState: PagerState = rememberPagerState(
        initialPage = tabKeys.indexOf(currentTab).coerceAtLeast(0),
        pageCount = { tabKeys.size },
    )

    // 底部 tab 切换动画：降低刚度以放慢切换，让过渡更有存在感
    // （冷启动玻璃 shader 编译完成后不至于显得切换过快）
    val tabSwitchSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    // 媒体库 tab 的内部子返回栈（列表 → 文件浏览），随媒体库 pager 页常驻
    val libraryNavController: NavHostController = rememberNavController()

    // 打开文件浏览：在媒体库子栈 push，并切到媒体库 tab（已在媒体库则无副作用）
    val openFileBrowser: (Int, String) -> Unit = { storageId, path ->
        libraryNavController.navigate(Routes.Stream.storageFileRoute(storageId, path))
        coroutineScope.launch { pagerState.animateScrollToPage(TabKey.LIBRARY.ordinal, animationSpec = tabSwitchSpec) }
    }

    // 文件浏览页跳设置：切到设置 tab（媒体库子栈留在后台，切回恢复文件浏览）
    val goToSettingsTab: () -> Unit = {
        coroutineScope.launch { pagerState.animateScrollToPage(TabKey.SETTINGS.ordinal, animationSpec = tabSwitchSpec) }
    }

    // 文件浏览多选态：进入多选时隐藏共享底栏，并仅在"当前在媒体库 tab"时上抛给 MainActivity 隐藏音乐条
    var fileBrowserMultiSelect by remember { mutableStateOf(false) }
    val inFileBrowserMultiSelect = fileBrowserMultiSelect && pagerState.currentPage == TabKey.LIBRARY.ordinal
    LaunchedEffect(inFileBrowserMultiSelect) {
        onFileBrowserMultiSelectChanged(inFileBrowserMultiSelect)
    }

    // 外部页请求打开文件浏览：消费后落地到媒体库 tab 的文件浏览
    LaunchedEffect(pendingFileBrowser) {
        val pending = pendingFileBrowser ?: return@LaunchedEffect
        openFileBrowser(pending.first, pending.second)
        onPendingFileBrowserConsumed()
    }

    // pager -> currentTab：页面滑动后同步选中态（供底栏高亮使用）
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

    // 共享底栏在同一宿主内就地切换 tab（落地到 pager 对应页）
    val onTabSelected: (Int) -> Unit = { index ->
        if (index in tabKeys.indices && index != pagerState.currentPage) {
            coroutineScope.launch { pagerState.animateScrollToPage(index, animationSpec = tabSwitchSpec) }
        }
    }

    // 创建共享 Haze 状态：内容层（niHazeSource）与浮层共享，实现真实背景模糊
    val hazeState = rememberNiHazeState()
    // 玻璃底栏背景画布：先铺一层 surface 作为统一底色，再捕获页面内容（drawContent）供模糊
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
                onOpenFileBrowser = openFileBrowser,
                libraryNavController = libraryNavController,
                onNavigateToGlobal = onNavigateToGlobal,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToPlayHistory = onNavigateToPlayHistory,
                onNavigateToQuickAccess = onNavigateToQuickAccess,
                onPlayVideo = onPlayVideo,
                onNavigateToStoragePlus = onNavigateToStoragePlus,
                onNavigateToImageViewer = onNavigateToImageViewer,
                onNavigateToDownloadManager = onNavigateToDownloadManager,
                onNavigateToSettingsTab = goToSettingsTab,
                pendingFileBrowser = pendingFileBrowser,
                onPendingFileBrowserConsumed = onPendingFileBrowserConsumed,
                onFileBrowserMultiSelectChanged = { fileBrowserMultiSelect = it },
                modifier = Modifier
                    .niHazeSource(hazeState)
                    .layerBackdrop(floatingBarBackdrop),
            )

            // 共享玻璃底栏：文件浏览多选态下隐藏（操作栏贴底，避免堆叠）
            if (!inFileBrowserMultiSelect) {
                HomeBottomNavBar(
                    selectedIndex = { pagerState.targetPage },
                    onSelect = onTabSelected,
                    backdrop = floatingBarBackdrop,
                    maxWidth = bottomBarMaxWidth,
                    bottomInset = 8.dp + bottomNavInset,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Tab 内容块：各页面切换动画。
 * 文件浏览收敛为媒体库 tab 的内部子返回栈（见 [LibraryTabNavHost]）。
 */
@Composable
private fun HomeTabContent(
    pagerState: PagerState,
    onOpenFileBrowser: (Int, String) -> Unit,
    libraryNavController: NavHostController,
    onNavigateToGlobal: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: (Int) -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit,
    onNavigateToImageViewer: () -> Unit,
    onNavigateToDownloadManager: () -> Unit,
    onNavigateToSettingsTab: () -> Unit,
    pendingFileBrowser: Pair<Int, String>?,
    onPendingFileBrowserConsumed: () -> Unit,
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 原生 HorizontalPager：Tab 页自带 swipe 切换，beyondViewportPageCount 让三页常驻
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
                        onNavigateToStorageFile = onOpenFileBrowser,
                        onPlayVideo = onPlayVideo,
                        onNavigateToSettings = onNavigateToSettingsTab,
                    )
                }
                1 -> Box(modifier = Modifier.fillMaxSize()) {
                    LibraryTabNavHost(
                        navController = libraryNavController,
                        onNavigateToStoragePlus = onNavigateToStoragePlus,
                        onPlayVideo = onPlayVideo,
                        onNavigateToImageViewer = onNavigateToImageViewer,
                        onNavigateToDownloadManager = onNavigateToDownloadManager,
                        onNavigateToSettings = onNavigateToSettingsTab,
                        pendingFileBrowser = pendingFileBrowser,
                        onPendingFileBrowserConsumed = onPendingFileBrowserConsumed,
                        onFileBrowserMultiSelectChanged = onFileBrowserMultiSelectChanged,
                    )
                }
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onNavigateToGlobal = onNavigateToGlobal,
                    )
                }
            }
        }
    }
}