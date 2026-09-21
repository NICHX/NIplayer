package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.nichx.niplayer.designsystem.components.NiGlassCircleIcon
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowHeightSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: (Int) -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onNavigateToStorageFile: (Int, String) -> Unit,
    onPlayVideo: (Boolean) -> Unit,
    onNavigateToImageViewer: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    viewModel: HomeTabViewModel = hiltViewModel(),
) {
    val recentPlays by viewModel.recentPlays.collectAsStateWithLifecycle()
    val recentVideoPlays by viewModel.recentVideoPlays.collectAsStateWithLifecycle()
    val recentAudioPlays by viewModel.recentAudioPlays.collectAsStateWithLifecycle()
    val quickAccessItems by viewModel.quickAccessItems.collectAsStateWithLifecycle()
    val videoHistoryCount by viewModel.videoHistoryCount.collectAsStateWithLifecycle()
    val audioHistoryCount by viewModel.audioHistoryCount.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    val thumbnailUrls by viewModel.thumbnailUrls.collectAsStateWithLifecycle()
    val qaThumbnailUrls by viewModel.qaThumbnailUrls.collectAsStateWithLifecycle()
    val storageReachability by viewModel.storageReachability.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val messageController = LocalAppMessageController.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeTabEvent.NavigateToPlayer -> onPlayVideo(event.isAudio)
                is HomeTabEvent.NavigateToStorageFile -> onNavigateToStorageFile(event.libraryId, event.relativePath)
                is HomeTabEvent.NavigateToImageViewer -> onNavigateToImageViewer()
                is HomeTabEvent.ShowError -> messageController.post(NiMessage.error(event.message))
            }
        }
    }

    // ===== 响应式布局参数 =====
    // 按窗口宽度分级适配：紧凑(手机竖屏)用单列 + 底部导航；中大屏(平板/横屏/桌面)
    // 用侧边导航 + 杂志式单列滚动（影院横幅 + 各分区横向行）。
    val windowSizeClass = LocalNiWindowSizeClass.current
    val useMagazine = windowSizeClass.width != NiWindowWidthSizeClass.Compact
    val qaColumns = when (windowSizeClass.width) {
        NiWindowWidthSizeClass.Compact -> 2
        NiWindowWidthSizeClass.Medium -> 3
        NiWindowWidthSizeClass.Expanded -> 4
    }
    // 最近播放网格列数：紧凑宽度用 1 列(横向滚动)，中大屏用多列网格
    val recentColumns = when (windowSizeClass.width) {
        NiWindowWidthSizeClass.Compact -> 1
        NiWindowWidthSizeClass.Medium -> 2
        NiWindowWidthSizeClass.Expanded -> 3
    }
    // 内容最大宽度：大屏避免内容拉伸过宽，提升可读性
    val contentMaxWidth = when (windowSizeClass.width) {
        NiWindowWidthSizeClass.Compact -> Dp.Unspecified
        NiWindowWidthSizeClass.Medium -> 720.dp
        NiWindowWidthSizeClass.Expanded -> 960.dp
    }
    // 英雄卡最大宽度：横屏(高度紧凑)下大幅收窄避免 16:9 撑满全屏；中大屏也收窄
    val heroMaxWidth = when {
        windowSizeClass.height == NiWindowHeightSizeClass.Compact -> 380.dp
        windowSizeClass.width == NiWindowWidthSizeClass.Medium -> 560.dp
        windowSizeClass.width == NiWindowWidthSizeClass.Expanded -> 560.dp
        else -> Dp.Unspecified
    }
    // 影院横幅最大高度：横屏(高度紧凑)下压低，避免占满整屏高度
    val bannerMaxHeight = if (windowSizeClass.height == NiWindowHeightSizeClass.Compact) 220.dp else 280.dp

    NiScaffold(
        topBar = {
            // 大屏下顶部栏也限制最大宽度并居中，与正文对齐
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                NiTopBar(
                    title = "NIplayer",
                    modifier = Modifier.widthIn(max = contentMaxWidth),
                    actions = {
                        NiGlassCircleIcon(
                            icon = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search),
                            onClick = onNavigateToSearch,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        NiGlassCircleIcon(
                            icon = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.play_history_title),
                            onClick = { onNavigateToPlayHistory(0) },
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        NiGlassCircleIcon(
                            icon = Icons.Rounded.Palette,
                            contentDescription = stringResource(R.string.theme_title),
                            onClick = onNavigateToTheme,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    },
                )
            }
        },
    ) { padding ->
        // 内容满铺全屏并延伸到顶栏背后，滚动内容可被顶栏真实模糊；
        // 顶栏高度由列表顶部 inset 让位，避免首项顶到状态栏
        val homeTopInset = padding.calculateTopPadding()
        // 下拉刷新指示器避开顶栏：整页内容满铺全屏滚到玻璃顶栏之下，默认指示器定位在全屏顶部
        // 会被透明顶栏盖住；用自定义 indicator 下移 homeTopInset，显现在顶栏之下。
        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = homeTopInset),
                )
            },
        ) {
            if (!dataReady) {
                HomeSkeletonLayout(
                    contentMaxWidth = contentMaxWidth,
                    topInset = homeTopInset,
                    recentColumns = recentColumns,
                    qaColumns = qaColumns,
                )
            } else if (useMagazine) {
                HomeMagazineLayout(
                    recentPlays = recentPlays,
                    recentVideoPlays = recentVideoPlays,
                    recentAudioPlays = recentAudioPlays,
                    quickAccessItems = quickAccessItems,
                    thumbnailUrls = thumbnailUrls,
                    qaThumbnailUrls = qaThumbnailUrls,
                    storageReachability = storageReachability,
                    contentMaxWidth = contentMaxWidth,
                    bannerMaxHeight = bannerMaxHeight,
                    topInset = homeTopInset,
                    videoHistoryCount = videoHistoryCount,
                    audioHistoryCount = audioHistoryCount,
                    onNavigateToPlayHistory = onNavigateToPlayHistory,
                    onNavigateToQuickAccess = onNavigateToQuickAccess,
                    onResumePlay = { viewModel.resumePlay(it) },
                    onOpenQuickAccess = { viewModel.openQuickAccessItem(it) },
                )
            } else {
                HomeSingleColumnLayout(
                    recentPlays = recentPlays,
                    recentVideoPlays = recentVideoPlays,
                    recentAudioPlays = recentAudioPlays,
                    quickAccessItems = quickAccessItems,
                    thumbnailUrls = thumbnailUrls,
                    qaThumbnailUrls = qaThumbnailUrls,
                    storageReachability = storageReachability,
                    recentColumns = recentColumns,
                    qaColumns = qaColumns,
                    contentMaxWidth = contentMaxWidth,
                    heroMaxWidth = heroMaxWidth,
                    topInset = homeTopInset,
                    videoHistoryCount = videoHistoryCount,
                    audioHistoryCount = audioHistoryCount,
                    onNavigateToPlayHistory = onNavigateToPlayHistory,
                    onNavigateToQuickAccess = onNavigateToQuickAccess,
                    onResumePlay = { viewModel.resumePlay(it) },
                    onOpenQuickAccess = { viewModel.openQuickAccessItem(it) },
                )
            }
        }
    }
}
