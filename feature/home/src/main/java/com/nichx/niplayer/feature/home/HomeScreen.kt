package com.nichx.niplayer.feature.home

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.LocalHazeState
import com.nichx.niplayer.designsystem.components.niHazeSource
import com.nichx.niplayer.designsystem.components.rememberNiHazeState
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.feature.home.home.HomeTabScreen
import com.nichx.niplayer.feature.home.library.FileBrowserScreen
import com.nichx.niplayer.feature.home.library.LibraryScreen
import com.nichx.niplayer.feature.home.settings.SettingsScreen
import com.nichx.niplayer.navigation.Routes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

private enum class TabKey { HOME, LIBRARY, SETTINGS }

/**
 * 首页宿主：pager 切 tab + 文件浏览内联状态切换（无嵌套 NavHost）。
 *
 * 修复说明：旧实现把文件浏览放进媒体库页的嵌套 NavHost（LibraryTabNavHost），
 * 从顶层页返回后该页渲染损坏导致空白；v2.2.0 无此嵌套 NavHost 故无此问题。
 * 此处恢复为无嵌套 NavHost：文件浏览是否打开由 [fbStorageId] 状态决定，
 * 内联渲染在媒体库 pager 页内（处于 haze 玻璃内容容器中，玻璃效果正常）。
 * pager beyondViewport 常驻页面，切走再切回不重建 FileBrowserScreen，目录层级得以保留。
 */
@Composable
fun HomeScreen(
    onNavigateToGlobal: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToPlayHistory: (Int) -> Unit = {},
    onNavigateToQuickAccess: () -> Unit = {},
    onPlayVideo: (Boolean) -> Unit = {},
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit = { _, _ -> },
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToDownloadManager: () -> Unit = {},
    // 外部页（搜索/快速访问）请求打开文件浏览：切到媒体库 tab 并打开
    pendingFileBrowser: Pair<Int, String>? = null,
    onPendingFileBrowserConsumed: () -> Unit = {},
    // 文件浏览多选态（已结合"当前在文件浏览"）回传 MainActivity：供其隐藏音乐条
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit = {},
) {
    var currentTab by rememberSaveable { mutableStateOf(TabKey.HOME) }
    val tabKeys = arrayOf(TabKey.HOME, TabKey.LIBRARY, TabKey.SETTINGS)
    val coroutineScope = rememberCoroutineScope()

    // 返回防误触：首页根路由且无更上层(文件浏览等已用各自 BackHandler 优先拦截)，
    // 第一次返回仅弹 snackbar 提示，2s 内第二次按才真正退出。退出属于非破坏性操作，
    // 用轻量的"再按一次"替代确认弹窗，符合 Material 返回导航规范。
    val messageController = LocalAppMessageController.current
    val context = LocalContext.current
    val doubleBackExitHint = stringResource(R.string.home_double_back_exit)
    var lastBackPressedAt by remember { mutableStateOf(0L) }
    BackHandler {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackPressedAt <= (2.5f * 1000).toLong()) {
            (context as? Activity)?.finishAndRemoveTask()
        } else {
            lastBackPressedAt = now
            messageController.postInfo(doubleBackExitHint)
        }
    }

    // 三个 Tab 用原生 HorizontalPager 承载：切走常驻保留后台状态（含文件浏览目录层级）
    val pagerState: PagerState = rememberPagerState(
        initialPage = tabKeys.indexOf(currentTab).coerceAtLeast(0),
        pageCount = { tabKeys.size },
    )
    // 记录切 tab 前的上一页（供 Crossfade 淡出）
    var previousPage by remember { mutableIntStateOf(pagerState.currentPage) }

    // 文件浏览打开状态：内联在媒体库页展示（无嵌套 NavHost）
    var fbStorageId by rememberSaveable { mutableIntStateOf(0) }
    var fbPath by rememberSaveable { mutableStateOf("") }

    // 打开文件浏览：记录要浏览的存储源，并切到媒体库 tab
    val openFileBrowser: (Int, String) -> Unit = { storageId, path ->
        fbStorageId = storageId
        fbPath = path
        if (pagerState.currentPage != TabKey.LIBRARY.ordinal) {
            coroutineScope.launch { pagerState.scrollToPage(TabKey.LIBRARY.ordinal) }
        }
    }

    // 关闭文件浏览：回到媒体库 tab 的存储源列表
    val closeFileBrowser: () -> Unit = {
        fbStorageId = 0
        fbPath = ""
    }

    // 文件浏览多选态：进入多选时隐藏共享底栏，并仅在"当前在文件浏览"时上抛给 MainActivity
    var fileBrowserMultiSelect by remember { mutableStateOf(false) }
    val inFileBrowser = fbStorageId > 0
    val inFileBrowserMultiSelect = fileBrowserMultiSelect && inFileBrowser
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

    // 共享底栏就地切换 tab（落地到 pager 对应页，瞬时切换无动画）
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
                fbStorageId = fbStorageId,
                fbPath = fbPath,
                onCloseFileBrowser = closeFileBrowser,
                onOpenFileBrowser = openFileBrowser,
                onNavigateToGlobal = onNavigateToGlobal,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToPlayHistory = onNavigateToPlayHistory,
                onNavigateToQuickAccess = onNavigateToQuickAccess,
                onPlayVideo = onPlayVideo,
                onNavigateToStoragePlus = onNavigateToStoragePlus,
                onNavigateToImageViewer = onNavigateToImageViewer,
                onNavigateToDownloadManager = onNavigateToDownloadManager,
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
 * Tab 内容块：各页面切换动画 + 媒体库页内联文件浏览。
 */
@Composable
private fun HomeTabContent(
    pagerState: PagerState,
    previousPage: Int,
    fbStorageId: Int,
    fbPath: String,
    onCloseFileBrowser: () -> Unit,
    onOpenFileBrowser: (Int, String) -> Unit,
    onNavigateToGlobal: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: (Int) -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onPlayVideo: (Boolean) -> Unit,
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit,
    onNavigateToImageViewer: () -> Unit,
    onNavigateToDownloadManager: () -> Unit,
    onFileBrowserMultiSelectChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 原生 HorizontalPager：beyondViewportPageCount 让三页常驻，切走保留文件浏览状态
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
                    if (fbStorageId > 0) {
                        FileBrowserScreen(
                            storageId = fbStorageId,
                            initialPath = fbPath,
                            onBack = onCloseFileBrowser,
                            onPlayVideo = onPlayVideo,
                            onNavigateToImageViewer = onNavigateToImageViewer,
                            onNavigateToDownloadManager = onNavigateToDownloadManager,
                            onFileBrowserMultiSelectChanged = onFileBrowserMultiSelectChanged,
                        )
                    } else {
                        LibraryScreen(
                            onNavigateToStorageFile = onOpenFileBrowser,
                            onNavigateToStoragePlus = onNavigateToStoragePlus,
                        )
                    }
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
 * 切 tab 轻量交叉淡入淡出：仅 alpha 淡入淡出，无 scale 无位移。
 * 内容已由 scrollToPage 瞬时就位，通过合成级 alpha（graphicsLayer）实现旧页淡出+新页淡入。
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