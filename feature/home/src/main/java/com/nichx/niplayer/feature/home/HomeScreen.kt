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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
    // 记录切 tab 前的上一页（供 Crossfade 淡出），对齐 unraid_assistant 的轻量交叉淡入淡出
    var previousPage by remember { mutableIntStateOf(pagerState.currentPage) }

    // 媒体库 tab 的内部子返回栈（列表 → 文件浏览），随媒体库 pager 页常驻
    val libraryNavController: NavHostController = rememberNavController()

    // 打开文件浏览：在媒体库子栈 push，并切到媒体库 tab（已在媒体库则无副作用）。
    // 用 scrollToPage 瞬时切换，避免 pager 平移动画期间每帧重绘（中间页 + 底栏 backdrop）导致的掉帧
    val openFileBrowser: (Int, String) -> Unit = { storageId, path ->
        libraryNavController.navigate(Routes.Stream.storageFileRoute(storageId, path))
        coroutineScope.launch { pagerState.scrollToPage(TabKey.LIBRARY.ordinal) }
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

    // pager -> currentTab：页面滑动后同步选中态（供底栏高亮使用），并记录上一页供淡出
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val newIdx = page.coerceIn(0, tabKeys.size - 1)
            // currentTab 尚未更新，取 old ordinal 作为上一页；若页面确实切换则记录，供新页淡入时旧页淡出
            if (page != currentTab.ordinal) previousPage = currentTab.ordinal
            currentTab = tabKeys[newIdx]
        }
    }

    // 底部导航栏最大宽度限制，与首页正文最大宽度保持对齐
    val bottomBarMaxWidth = when (LocalNiWindowSizeClass.current.width) {
        NiWindowWidthSizeClass.Compact -> Dp.Unspecified
        NiWindowWidthSizeClass.Medium -> 720.dp
        NiWindowWidthSizeClass.Expanded -> 960.dp
    }

    // 共享底栏在同一宿主内就地切换 tab（落地到 pager 对应页，瞬时切换无动画）
    val onTabSelected: (Int) -> Unit = { index ->
        if (index in tabKeys.indices && index != pagerState.currentPage) {
            coroutineScope.launch { pagerState.scrollToPage(index) }
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
                previousPage = previousPage,
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
    previousPage: Int,
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
                0 -> CrossfadePage(
                    current = pagerState.currentPage,
                    previous = previousPage,
                    page = 0,
                ) {
                    HomeTabScreen(
                        onNavigateToSearch = onNavigateToSearch,
                        onNavigateToPlayHistory = onNavigateToPlayHistory,
                        onNavigateToQuickAccess = onNavigateToQuickAccess,
                        onNavigateToStorageFile = onOpenFileBrowser,
                        onPlayVideo = onPlayVideo,
                        onNavigateToTheme = { onNavigateToGlobal(Routes.User.SWITCH_THEME) },
                    )
                }
                1 -> CrossfadePage(
                    current = pagerState.currentPage,
                    previous = previousPage,
                    page = 1,
                ) {
                    LibraryTabNavHost(
                        navController = libraryNavController,
                        onNavigateToStoragePlus = onNavigateToStoragePlus,
                        onPlayVideo = onPlayVideo,
                        onNavigateToImageViewer = onNavigateToImageViewer,
                        onNavigateToDownloadManager = onNavigateToDownloadManager,
                        pendingFileBrowser = pendingFileBrowser,
                        onPendingFileBrowserConsumed = onPendingFileBrowserConsumed,
                        onFileBrowserMultiSelectChanged = onFileBrowserMultiSelectChanged,
                    )
                }
                else -> CrossfadePage(
                    current = pagerState.currentPage,
                    previous = previousPage,
                    page = 2,
                ) {
                    SettingsScreen(
                        onNavigateToGlobal = onNavigateToGlobal,
                    )
                }
            }
        }
    }
}

/**
 * 切 tab 轻量交叉淡入淡出（对齐 unraid_assistant）：仅 alpha 淡入淡出，无 scale 无位移。
 * 内容已由 scrollToPage 瞬时就位，通过合成级 alpha（graphicsLayer，不触发 measure/layout）
 * 实现旧页淡出（1→0）+ 新页淡入（0→1）叠加；时长 120ms，避免切换拖沓感。
 * 冷启动时当前页走"previous==自己"分支直接全显，不反复淡入。
 */
private const val TabCrossfadeDurationMs = 120

@Composable
private fun CrossfadePage(
    current: Int,
    previous: Int,
    page: Int,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(current, previous, page) {
        if (current == page) {
            // 冷启动/驻留当前页：若由其它页切来则先置透明再淡入
            if (previous != page) alpha.snapTo(0f)
            alpha.animateTo(1f, tween(durationMillis = TabCrossfadeDurationMs))
        } else if (previous == page) {
            // 刚离开的页面淡出
            alpha.animateTo(0f, tween(durationMillis = TabCrossfadeDurationMs))
        } else {
            // 其它页保持透明
            alpha.snapTo(0f)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        content()
    }
}