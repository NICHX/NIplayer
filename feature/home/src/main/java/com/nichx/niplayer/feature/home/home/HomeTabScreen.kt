package com.nichx.niplayer.feature.home.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.designsystem.theme.NiSpacings
import com.nichx.niplayer.designsystem.theme.NiWindowHeightSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.feature.home.MediaFileTypes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiSnackbarDefaults
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.showNiMessage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiHeroResumeCard
import com.nichx.niplayer.designsystem.components.NiHomeLoadingState
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessUiItem
import com.nichx.niplayer.designsystem.components.NiSectionHeader
import com.nichx.niplayer.designsystem.components.NiThumbCard
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.PlaceholderText
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: () -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onNavigateToStorageFile: (Int, String) -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeTabViewModel = hiltViewModel(),
) {
    val recentPlays by viewModel.recentPlays.collectAsStateWithLifecycle()
    val recentVideoPlays by viewModel.recentVideoPlays.collectAsStateWithLifecycle()
    val recentAudioPlays by viewModel.recentAudioPlays.collectAsStateWithLifecycle()
    val quickAccessItems by viewModel.quickAccessItems.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistCoverUrls by viewModel.playlistCoverUrls.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    val thumbnailUrls by viewModel.thumbnailUrls.collectAsStateWithLifecycle()
    val qaThumbnailUrls by viewModel.qaThumbnailUrls.collectAsStateWithLifecycle()
    val storageReachability by viewModel.storageReachability.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeTabEvent.NavigateToPlayer -> onPlayVideo()
                is HomeTabEvent.NavigateToStorageFile -> onNavigateToStorageFile(event.libraryId, event.relativePath)
                is HomeTabEvent.ShowError -> snackbarHostState.showNiMessage(NiMessage.error(event.message))
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

    Scaffold(
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
                        IconButton(onClick = onNavigateToSearch) {
                            NiStyleIcon(
                                icon = Icons.Rounded.Search,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = "搜索",
                            )
                        }
                        IconButton(onClick = onNavigateToPlayHistory) {
                            NiStyleIcon(
                                icon = Icons.Rounded.History,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = "播放历史",
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            NiStyleIcon(
                                icon = Icons.Rounded.Settings,
                                style = NiAppIconStyle,
                                containerSize = 40.dp,
                                iconSize = 22.dp,
                                contentDescription = "设置",
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = {
                NiSnackbarHost(
                    hostState = snackbarHostState,
                    bottomObstruction = NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION,
                )
            },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!dataReady) {
                NiHomeLoadingState()
            } else if (useMagazine) {
                HomeMagazineLayout(
                    recentPlays = recentPlays,
                    recentVideoPlays = recentVideoPlays,
                    recentAudioPlays = recentAudioPlays,
                    quickAccessItems = quickAccessItems,
                    playlists = playlists,
                    playlistCoverUrls = playlistCoverUrls,
                    thumbnailUrls = thumbnailUrls,
                    qaThumbnailUrls = qaThumbnailUrls,
                    storageReachability = storageReachability,
                    contentMaxWidth = contentMaxWidth,
                    bannerMaxHeight = bannerMaxHeight,
                    onNavigateToPlayHistory = onNavigateToPlayHistory,
                    onNavigateToQuickAccess = onNavigateToQuickAccess,
                    onOpenPlaylists = onOpenPlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                    onResumePlay = { viewModel.resumePlay(it) },
                    onOpenQuickAccess = { viewModel.openQuickAccessItem(it) },
                )
            } else {
                HomeSingleColumnLayout(
                    recentPlays = recentPlays,
                    recentVideoPlays = recentVideoPlays,
                    recentAudioPlays = recentAudioPlays,
                    quickAccessItems = quickAccessItems,
                    playlists = playlists,
                    playlistCoverUrls = playlistCoverUrls,
                    thumbnailUrls = thumbnailUrls,
                    qaThumbnailUrls = qaThumbnailUrls,
                    storageReachability = storageReachability,
                    recentColumns = recentColumns,
                    qaColumns = qaColumns,
                    contentMaxWidth = contentMaxWidth,
                    heroMaxWidth = heroMaxWidth,
                    onNavigateToPlayHistory = onNavigateToPlayHistory,
                    onNavigateToQuickAccess = onNavigateToQuickAccess,
                    onOpenPlaylists = onOpenPlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                    onResumePlay = { viewModel.resumePlay(it) },
                    onOpenQuickAccess = { viewModel.openQuickAccessItem(it) },
                )
            }
        }
    }
}

/**
 * 杂志式单列布局（中大屏/横屏）：
 * 全宽影院横幅 + 各分区横向滚动行（最近播放视频 / 最近播放音乐 / 快速访问 / 歌单）。
 * 模块顺序与竖屏单列布局保持一致。单轴滚动、卡片尺寸恒定，
 * 避免双栏布局的右栏挤压与视线跳跃。
 */
@Composable
private fun HomeMagazineLayout(
    recentPlays: List<PlayHistoryEntity>,
    recentVideoPlays: List<PlayHistoryEntity>,
    recentAudioPlays: List<PlayHistoryEntity>,
    quickAccessItems: List<QuickAccessUiItem>,
    playlists: List<PlaylistWithCount>,
    playlistCoverUrls: Map<Int, String>,
    thumbnailUrls: Map<String, String>,
    qaThumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    contentMaxWidth: Dp,
    bannerMaxHeight: Dp,
    onNavigateToPlayHistory: () -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onResumePlay: (PlayHistoryEntity) -> Unit,
    onOpenQuickAccess: (QuickAccessUiItem) -> Unit,
) {
    val screenOuter = NiSpacings.responsiveScreenOuter
    val listGap = NiSpacings.responsiveListGap

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(listGap),
        ) {
            if (recentPlays.isEmpty() && quickAccessItems.isEmpty() && playlists.isEmpty()) {
                item(key = "empty_all") {
                    NiEmptyState(
                        icon = Icons.Rounded.Star,
                        text = "暂无内容",
                        hint = "从媒体库添加存储源开始使用",
                        modifier = Modifier.padding(horizontal = screenOuter),
                    )
                }
            } else {
                if (recentPlays.isNotEmpty()) {
                    item(key = "hero_banner") {
                        if (recentVideoPlays.isNotEmpty()) {
                            HeroCarousel(
                                mediaItems = recentVideoPlays,
                                pageContent = { video ->
                                    CinematicHeroBanner(
                                        title = video.videoName,
                                        durationText = formatTime(video.videoDuration),
                                        positionText = formatTime(video.videoPosition),
                                        thumbnailModel = buildHeroThumbnailModel(
                                            video.url, video.mediaType, video.videoName, thumbnailUrls,
                                        ),
                                        progressFraction = if (video.videoDuration > 0)
                                            video.videoPosition.toFloat() / video.videoDuration.toFloat() else 0f,
                                        maxHeight = bannerMaxHeight,
                                        onClick = { onResumePlay(video) },
                                    )
                                },
                            )
                        } else {
                            val hero = recentPlays.first()
                            CinematicHeroBanner(
                                title = hero.videoName,
                                durationText = formatTime(hero.videoDuration),
                                positionText = formatTime(hero.videoPosition),
                                thumbnailModel = buildHeroThumbnailModel(
                                    hero.url, hero.mediaType, hero.videoName, thumbnailUrls,
                                ),
                                progressFraction = if (hero.videoDuration > 0)
                                    hero.videoPosition.toFloat() / hero.videoDuration.toFloat() else 0f,
                                maxHeight = bannerMaxHeight,
                                onClick = { onResumePlay(hero) },
                            )
                        }
                    }
                }

                if (recentAudioPlays.isNotEmpty()) {
                    item(key = "recent_audio_header") {
                        NiSectionHeader(
                            title = "最近播放音乐",
                            count = recentAudioPlays.size,
                            onClick = onNavigateToPlayHistory,
                            modifier = Modifier.padding(horizontal = screenOuter),
                        )
                    }
                    item(key = "recent_audio_row") {
                        RecentMediaGrid(
                            mediaItems = recentAudioPlays,
                            columns = 1,
                            thumbnailUrls = thumbnailUrls,
                            storageReachability = storageReachability,
                            contentScale = ContentScale.Fit,
                            squareCover = true,
                            edgePadding = screenOuter,
                            cardWidth = 140.dp,
                            onItemClick = onResumePlay,
                        )
                    }
                }

                if (quickAccessItems.isNotEmpty()) {
                    item(key = "qa_header") {
                        NiSectionHeader(
                            title = "快速访问",
                            count = quickAccessItems.size,
                            onClick = onNavigateToQuickAccess,
                            modifier = Modifier.padding(horizontal = screenOuter),
                        )
                    }
                    item(key = "qa_row") {
                        HomeQuickAccessLazyRow(
                            items = quickAccessItems,
                            thumbnailUrls = qaThumbnailUrls,
                            storageReachability = storageReachability,
                            onItemClick = onOpenQuickAccess,
                            edgePadding = screenOuter,
                            cardWidth = 200.dp,
                        )
                    }
                }

                if (playlists.isNotEmpty()) {
                    item(key = "playlists_header") {
                        NiSectionHeader(
                            title = "歌单",
                            count = playlists.size,
                            onClick = onOpenPlaylists,
                            modifier = Modifier.padding(horizontal = screenOuter),
                        )
                    }
                    item(key = "playlists_row") {
                        HomePlaylistLazyRow(
                            playlists = playlists,
                            coverUrls = playlistCoverUrls,
                            onItemClick = onOpenPlaylist,
                            edgePadding = screenOuter,
                            cardWidth = 200.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 影院横幅（杂志式首页首屏）：
 * 缩略图模糊铺满 + 加深遮罩保证可读性；前景为「继续播放」引导、标题、
 * 进度信息与播放按钮，整卡可点击续播。无缩略图时降级为主题渐变背景。
 */
@Composable
private fun CinematicHeroBanner(
    title: String,
    durationText: String,
    positionText: String,
    thumbnailModel: Any?,
    progressFraction: Float,
    maxHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val isDark = NiExtraColors.current.isDark
    val fallbackBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            if (isDark) Color(0xFF0F0F17) else Color(0xFFEDEAE2),
        ),
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "bannerScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = maxHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(NiExtraColors.current.surfaceLevel2)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // 背景层：缩略图放大模糊铺满；无缩略图时用主题渐变
        if (thumbnailModel != null) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
                    .blur(18.dp)
                    .alpha(0.95f),
            )
        } else {
            Box(Modifier.fillMaxSize().background(fallbackBrush))
        }
        // 遮罩：仅左侧轻微加深托住前景文字，保留缩略图可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.12f),
                        ),
                    ),
                ),
        )
        // 前景内容：左对齐，限制文本宽度避免过宽
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "继续播放",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$positionText / $durationText",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.28f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color.White),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "继续播放",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单列布局（紧凑宽度/手机竖屏）：
 * 英雄卡 + 最近播放视频 + 最近播放音乐 + 快速访问，纵向单列滚动。
 */
@Composable
private fun HomeSingleColumnLayout(
    recentPlays: List<PlayHistoryEntity>,
    recentVideoPlays: List<PlayHistoryEntity>,
    recentAudioPlays: List<PlayHistoryEntity>,
    quickAccessItems: List<QuickAccessUiItem>,
    playlists: List<PlaylistWithCount>,
    playlistCoverUrls: Map<Int, String>,
    thumbnailUrls: Map<String, String>,
    qaThumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    recentColumns: Int,
    qaColumns: Int,
    contentMaxWidth: Dp,
    heroMaxWidth: Dp,
    onNavigateToPlayHistory: () -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    onResumePlay: (PlayHistoryEntity) -> Unit,
    onOpenQuickAccess: (QuickAccessUiItem) -> Unit,
) {
    val screenOuter = NiSpacings.responsiveScreenOuter

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = screenOuter,
                end = screenOuter,
                top = 8.dp,
                bottom = 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(NiSpacings.responsiveListGap),
        ) {
            if (recentPlays.isEmpty() && quickAccessItems.isEmpty() && playlists.isEmpty()) {
                item(key = "empty_all") {
                    NiEmptyState(
                        icon = Icons.Rounded.Star,
                        text = "暂无内容",
                        hint = "从媒体库添加存储源开始使用",
                    )
                }
            } else {
                if (recentPlays.isNotEmpty()) {
                    item(key = "hero") {
                        if (recentVideoPlays.isNotEmpty()) {
                            // 最近视频轮播：每页沿用英雄卡样式，大屏/横屏下限制最大宽度并居中
                            HeroCarousel(
                                mediaItems = recentVideoPlays,
                                pageContent = { video ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        Box(modifier = Modifier.widthIn(max = heroMaxWidth)) {
                                            HomeHeroItem(
                                                hero = video,
                                                thumbnailUrls = thumbnailUrls,
                                                storageReachability = storageReachability,
                                                onClick = { onResumePlay(video) },
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            val hero = recentPlays.first()
                            // 无视频记录时降级为单张英雄卡（可能是音乐续播），样式不变
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(modifier = Modifier.widthIn(max = heroMaxWidth)) {
                                    HomeHeroItem(
                                        hero = hero,
                                        thumbnailUrls = thumbnailUrls,
                                        storageReachability = storageReachability,
                                        onClick = { onResumePlay(hero) },
                                    )
                                }
                            }
                        }
                    }

                    if (recentAudioPlays.isNotEmpty()) {
                        item(key = "recent_audio_header") {
                            NiSectionHeader(
                                title = "最近播放音乐",
                                count = recentAudioPlays.size,
                                onClick = onNavigateToPlayHistory,
                            )
                        }

                        item(key = "recent_audio_row") {
                            RecentMediaGrid(
                                mediaItems = recentAudioPlays,
                                columns = recentColumns,
                                thumbnailUrls = thumbnailUrls,
                                storageReachability = storageReachability,
                                contentScale = ContentScale.Fit,
                                squareCover = true,
                                onItemClick = onResumePlay,
                            )
                        }
                    }
                } else {
                    item(key = "history_empty") {
                        NiEmptyState(
                            icon = Icons.Rounded.History,
                            text = "暂无播放记录",
                            hint = "从媒体库选择视频或音乐开始播放",
                        )
                    }
                }
            }

            if (quickAccessItems.isNotEmpty()) {
                item(key = "qa_header") {
                    NiSectionHeader(
                        title = "快速访问",
                        count = quickAccessItems.size,
                        onClick = onNavigateToQuickAccess,
                    )
                }

                quickAccessItems.chunked(qaColumns).forEachIndexed { chunkIdx, row ->
                    item(key = "qa_row_$chunkIdx") {
                        HomeQuickAccessRow(
                            row = row,
                            columns = qaColumns,
                            thumbnailUrls = qaThumbnailUrls,
                            storageReachability = storageReachability,
                            onItemClick = onOpenQuickAccess,
                        )
                    }
                }
            }

            if (playlists.isNotEmpty()) {
                item(key = "playlists_header") {
                    NiSectionHeader(
                        title = "歌单",
                        count = playlists.size,
                        onClick = onOpenPlaylists,
                    )
                }

                // 与快速访问同样式：按 qaColumns 均分宽度网格，卡片尺寸一致
                playlists.chunked(qaColumns).forEachIndexed { chunkIdx, row ->
                    item(key = "playlists_row_$chunkIdx") {
                        HomePlaylistRow(
                            row = row,
                            columns = qaColumns,
                            coverUrls = playlistCoverUrls,
                            onItemClick = onOpenPlaylist,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 英雄续播项：横版/竖版英雄卡 + 不可达半透明 + 右上角「离线」角标。
 */
@Composable
private fun HomeHeroItem(
    hero: PlayHistoryEntity,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    onClick: () -> Unit,
) {
    val heroProgress = if (hero.videoDuration > 0)
        hero.videoPosition.toFloat() / hero.videoDuration.toFloat() else 0f
    val heroReachable = isHistoryReachable(hero, storageReachability)
    Box(modifier = Modifier.fillMaxWidth()) {
        NiHeroResumeCard(
            title = hero.videoName,
            durationText = formatTime(hero.videoDuration),
            positionText = formatTime(hero.videoPosition),
            thumbnailModel = buildHeroThumbnailModel(
                hero.url, hero.mediaType, hero.videoName, thumbnailUrls,
            ),
            progressFraction = heroProgress,
            contentScale = ContentScale.Crop,
            onClick = onClick,
            modifier = if (!heroReachable) Modifier.graphicsLayer { alpha = 0.5f } else Modifier,
        )
        if (!heroReachable) {
            UnreachableBadge(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
    }
}

/**
 * 英雄卡轮播（替代原「最近播放视频」横向行）：
 *
 * 复用调用方传入的 [pageContent]（原英雄卡样式，竖屏 [HomeHeroItem] / 横屏 [CinematicHeroBanner]），
 * 通过 [HorizontalPager] 横向滑动切换；多页时自动轮播（用户按住 / 拖动时暂停），
 * 底部渲染分页指示点。单条时退化为静态卡，与原有布局视觉一致。
 */
@Composable
private fun HeroCarousel(
    mediaItems: List<PlayHistoryEntity>,
    pageContent: @Composable (PlayHistoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    autoScrollIntervalMs: Long = AUTO_SCROLL_INTERVAL_MS,
) {
    if (mediaItems.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val isPressed by pagerState.interactionSource.collectIsPressedAsState()

    LaunchedEffect(isDragged, isPressed, mediaItems.size) {
        if (mediaItems.size <= 1 || isDragged || isPressed) return@LaunchedEffect
        while (true) {
            delay(autoScrollIntervalMs)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % mediaItems.size)
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            pageContent(mediaItems[page])
        }
        if (mediaItems.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(mediaItems.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 快速访问单行：按 [columns] 均分宽度，末行不足时用等宽占位填齐避免拉伸。
 */
@Composable
private fun HomeQuickAccessRow(
    row: List<QuickAccessUiItem>,
    columns: Int,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    onItemClick: (QuickAccessUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        row.forEach { qaItem ->
            val effectiveValid = qaItem.libraryValid &&
                storageReachability[qaItem.entity.libraryId] != false
            HomeQuickAccessGridItem(
                item = qaItem,
                thumbnailUrl = thumbnailUrls[qaItem.entity.storagePath],
                isValid = effectiveValid,
                onClick = { onItemClick(qaItem) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size < columns) {
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 歌单单行（竖屏）：按 [columns] 均分宽度，末行不足时用等宽占位填齐，
 * 与快速访问网格卡片尺寸保持一致。
 */
@Composable
private fun HomePlaylistRow(
    row: List<PlaylistWithCount>,
    columns: Int,
    coverUrls: Map<Int, String>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        row.forEach { playlist ->
            HomePlaylistItem(
                playlist = playlist,
                coverUrl = coverUrls[playlist.playlist.id],
                onClick = { onItemClick(playlist.playlist.id) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size < columns) {
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.LOCAL_STORAGE -> "本地"
    MediaType.EXTERNAL_STORAGE -> "设备"
    MediaType.SMB_SERVER -> "SMB"
    MediaType.WEBDAV_SERVER -> "WebDAV"
    MediaType.QUICK_ACCESS -> "快捷"
    else -> "其他"
}

private fun buildThumbnailModel(
    url: String,
    mediaType: MediaType,
    thumbnailUrls: Map<String, String> = emptyMap(),
): Any? {
    val cachedThumb = thumbnailUrls[url]
    if (cachedThumb != null) return cachedThumb

    val isLocal = mediaType == MediaType.LOCAL_STORAGE || mediaType == MediaType.EXTERNAL_STORAGE
    if (isLocal && url.isNotEmpty()) {
        val fileName = url.substringAfterLast('/')
        if (MediaFileTypes.isAudioFile(fileName)) return null
        return if (url.startsWith("/")) "file://$url" else url
    }
    return null
}

private fun buildHeroThumbnailModel(
    url: String,
    mediaType: MediaType,
    fileName: String,
    thumbnailUrls: Map<String, String> = emptyMap(),
): Any {
    buildThumbnailModel(url, mediaType, thumbnailUrls)?.let { return it }
    // 英雄卡来自播放历史（已播放），此时仍无缩略图说明生成失败/太短，
    // 标签标为"无缩略图"；未播放过的普通条目不传 label
    val firstChar = fileName.firstOrNull { !it.isWhitespace() }?.toString() ?: "▶"
    return PlaceholderText(firstChar, label = "无缩略图")
}

/** 英雄卡轮播自动切换间隔（ms）。 */
private const val AUTO_SCROLL_INTERVAL_MS = 3000L

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** 不可达角标：右上角显示"离线"标识。 */
@Composable
private fun UnreachableBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "离线",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 判断播放历史条目是否可达（库存在 + 远程连接正常）。 */
private fun isHistoryReachable(
    history: PlayHistoryEntity,
    storageReachability: Map<Int, Boolean>,
): Boolean {
    val sid = history.storageId ?: return true // 本地播放，无 storageId，视为可达
    return storageReachability[sid] != false // 未验证（null）视为可达，明确 false 才不可达
}

/**
 * 最近播放媒体展示容器：
 * - [columns] <= 1（紧凑宽度）：横向滚动 [LazyRow]，固定宽度卡片。
 * - [columns] > 1（中大屏）：多列网格，卡片填满列宽，末行用等宽占位填齐避免拉伸。
 *
 * 不可达条目统一叠加半透明效果。
 */
@Composable
private fun RecentMediaGrid(
    mediaItems: List<PlayHistoryEntity>,
    columns: Int,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    contentScale: ContentScale,
    squareCover: Boolean,
    onItemClick: (PlayHistoryEntity) -> Unit,
    edgePadding: Dp = 0.dp,
    cardWidth: Dp = Dp.Unspecified,
) {
    val gap = NiSpacings.responsiveCardGroupGap
    if (columns <= 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(mediaItems, key = { it.id }) { history ->
                RecentThumbItem(
                    history = history,
                    thumbnailUrls = thumbnailUrls,
                    storageReachability = storageReachability,
                    contentScale = contentScale,
                    squareCover = squareCover,
                    fillWidth = false,
                    cardWidth = cardWidth,
                    onClick = { onItemClick(history) },
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            mediaItems.chunked(columns).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { history ->
                        RecentThumbItem(
                            history = history,
                            thumbnailUrls = thumbnailUrls,
                            storageReachability = storageReachability,
                            contentScale = contentScale,
                            squareCover = squareCover,
                            fillWidth = true,
                            onClick = { onItemClick(history) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 用与满行等数量的占位填齐末行，避免末行卡片被拉伸变宽
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 最近播放单条卡片：包裹 [NiThumbCard] 并叠加不可达半透明效果。 */
@Composable
private fun RecentThumbItem(
    history: PlayHistoryEntity,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    contentScale: ContentScale,
    squareCover: Boolean,
    fillWidth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = Dp.Unspecified,
) {
    val progress = if (history.videoDuration > 0)
        history.videoPosition.toFloat() / history.videoDuration.toFloat() else 0f
    val reachable = isHistoryReachable(history, storageReachability)
    Box(modifier = modifier.graphicsLayer { if (!reachable) alpha = 0.5f }) {
        NiThumbCard(
            title = history.videoName,
            durationText = formatTime(history.videoDuration),
            thumbnailModel = buildThumbnailModel(history.url, history.mediaType, thumbnailUrls),
            progressFraction = progress,
            mediaLabel = mediaTypeLabel(history.mediaType),
            contentScale = contentScale,
            onClick = onClick,
            squareCover = squareCover,
            fillWidth = fillWidth,
            cardWidth = cardWidth,
        )
    }
}

/**
 * 快速访问横向滚动行（杂志式布局用）：
 * 固定宽度 16:9 磁贴横向滚动，与最近播放行的卡片尺寸协调。
 */
@Composable
private fun HomeQuickAccessLazyRow(
    items: List<QuickAccessUiItem>,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    onItemClick: (QuickAccessUiItem) -> Unit,
    edgePadding: Dp = 0.dp,
    cardWidth: Dp = 200.dp,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = edgePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.entity.storagePath }) { qaItem ->
            val effectiveValid = qaItem.libraryValid &&
                storageReachability[qaItem.entity.libraryId] != false
            HomeQuickAccessGridItem(
                item = qaItem,
                thumbnailUrl = thumbnailUrls[qaItem.entity.storagePath],
                isValid = effectiveValid,
                onClick = { onItemClick(qaItem) },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

@Composable
private fun HomeQuickAccessGridItem(
    item: QuickAccessUiItem,
    thumbnailUrl: String?,
    isValid: Boolean = item.libraryValid,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "homeQaScale",
    )

    val cardShape = RoundedCornerShape(16.dp)
    val name = item.entity.name
    val isVideo = !item.entity.isDirectory && MediaFileTypes.isVideoFile(name)
    val isAudio = !item.entity.isDirectory && MediaFileTypes.isAudioFile(name)
    val isImage = !item.entity.isDirectory && MediaFileTypes.isImageFile(name)
    val hasThumbnail = thumbnailUrl != null && (isVideo || isAudio || isImage)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation = 1.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .background(NiExtraColors.current.surfaceLevel3)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            if (item.entity.isDirectory) {
                val pc = MaterialTheme.colorScheme.primaryContainer
                val gradientColors = remember(name) { listOf(pc, pc.copy(alpha = 0.7f)) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradientColors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                val thumbBg = if (isAudio)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else NiExtraColors.current.surfaceLevel3
                Box(
                    modifier = Modifier.fillMaxSize().background(thumbBg),
                ) {
                    if (hasThumbnail) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isVideo || isAudio) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when {
                                    isVideo -> Icons.Rounded.Movie
                                    isAudio -> Icons.Rounded.MusicNote
                                    isImage -> Icons.Rounded.Image
                                    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = when {
                                    isAudio -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    isVideo || isImage -> Color.White.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NiAutoSizeText(
            text = item.entity.name,
            maxLines = 2,
            minFontSize = 11.sp,
            maxFontSize = 13.sp,
            color = if (isValid) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

/**
 * 歌单横向滚动行（单列与杂志式布局共用）：
 * 固定宽度 16:9 封面磁贴，无封面时降级为主题色 + 歌单图标。
 */
@Composable
private fun HomePlaylistLazyRow(
    playlists: List<PlaylistWithCount>,
    coverUrls: Map<Int, String>,
    onItemClick: (Int) -> Unit,
    edgePadding: Dp = 0.dp,
    cardWidth: Dp = 150.dp,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = edgePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(playlists, key = { it.playlist.id }) { playlist ->
            HomePlaylistItem(
                playlist = playlist,
                coverUrl = coverUrls[playlist.playlist.id],
                onClick = { onItemClick(playlist.playlist.id) },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

/** 歌单单条卡片：16:9 封面 + 条目数角标 + 名称。 */
@Composable
private fun HomePlaylistItem(
    playlist: PlaylistWithCount,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "homePlaylistScale",
    )

    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation = 1.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = "${playlist.itemCount} 个条目",
                style = MaterialTheme.typography.labelSmall,
                color = if (coverUrl != null) Color.White
                else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (coverUrl != null) Color.Black.copy(alpha = 0.35f)
                        else Color.Transparent,
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        NiAutoSizeText(
            text = playlist.playlist.name,
            maxLines = 2,
            minFontSize = 11.sp,
            maxFontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}
