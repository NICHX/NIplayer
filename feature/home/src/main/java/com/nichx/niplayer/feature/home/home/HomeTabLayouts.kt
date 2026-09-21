package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.feature.home.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.designsystem.theme.NiSpacings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiHeroResumeCard
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessUiItem
import com.nichx.niplayer.designsystem.components.NiSectionHeader


/**
 * 杂志式单列布局（中大屏/横屏）：
 * 全宽影院横幅 + 各分区横向滚动行（最近播放视频 / 最近播放音乐 / 快速访问 / 歌单）。
 * 模块顺序与竖屏单列布局保持一致。单轴滚动、卡片尺寸恒定，
 * 避免双栏布局的右栏挤压与视线跳跃。
 */
@Composable
internal fun HomeMagazineLayout(
    recentPlays: List<PlayHistoryEntity>,
    recentVideoPlays: List<PlayHistoryEntity>,
    recentAudioPlays: List<PlayHistoryEntity>,
    quickAccessItems: List<QuickAccessUiItem>,
    thumbnailUrls: Map<String, String>,
    qaThumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    contentMaxWidth: Dp,
    bannerMaxHeight: Dp,
    topInset: Dp,
    videoHistoryCount: Int,
    audioHistoryCount: Int,
    onNavigateToPlayHistory: (Int) -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onResumePlay: (PlayHistoryEntity) -> Unit,
    onOpenQuickAccess: (QuickAccessUiItem) -> Unit,
) {
    val screenOuter = NiSpacings.responsiveScreenOuter
    val listGap = NiSpacings.responsiveListGap
    // 底部导航栏避让：玻璃底栏悬浮在 8dp+系统导航栏高度上方（高度 64dp），预留清除空间
    val bottomBarClearance = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    } + 88.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomBarClearance),
            verticalArrangement = Arrangement.spacedBy(listGap),
        ) {
            if (recentPlays.isEmpty() && quickAccessItems.isEmpty()) {
                item(key = "empty_all") {
                    NiEmptyState(
                        icon = Icons.Rounded.Star,
                        text = stringResource(R.string.home_empty_title),
                        hint = stringResource(R.string.home_empty_hint),
                        modifier = Modifier.padding(horizontal = screenOuter),
                    )
                }
            } else {
                // 英雄卡仅展示最近一个视频（无轮播），视频历史其余项单独成行
                val featuredVideo = recentVideoPlays.firstOrNull()
                val restVideo = featuredVideo?.let { recentVideoPlays.drop(1) } ?: emptyList()

                if (featuredVideo != null) {
                    item(key = "hero") {
                        CinematicHeroBanner(
                            title = featuredVideo.videoName,
                            durationText = formatTime(featuredVideo.videoDuration),
                            positionText = formatTime(featuredVideo.videoPosition),
                            thumbnailModel = buildHeroThumbnailModel(
                                featuredVideo.url, featuredVideo.mediaType, featuredVideo.videoName, thumbnailUrls,
                            ),
                            progressFraction = if (featuredVideo.videoDuration > 0)
                                featuredVideo.videoPosition.toFloat() / featuredVideo.videoDuration.toFloat() else 0f,
                            maxHeight = bannerMaxHeight,
                            onClick = { onResumePlay(featuredVideo) },
                        )
                    }
                }

                if (restVideo.isNotEmpty()) {
                    item(key = "video_header") {
                        NiSectionHeader(
                            title = stringResource(R.string.home_recent_video),
                            count = videoHistoryCount,
                            onClick = { onNavigateToPlayHistory(1) },
                            modifier = Modifier.padding(horizontal = screenOuter),
                        )
                    }
                    item(key = "video_row") {
                        RecentMediaGrid(
                            mediaItems = restVideo,
                            columns = 1,
                            thumbnailUrls = thumbnailUrls,
                            storageReachability = storageReachability,
                            contentScale = ContentScale.Crop,
                            squareCover = false,
                            edgePadding = screenOuter,
                            cardWidth = 160.dp,
                            onItemClick = onResumePlay,
                        )
                    }
                }

                if (recentAudioPlays.isNotEmpty()) {
                    item(key = "audio_header") {
                        NiSectionHeader(
                            title = stringResource(R.string.home_recent_audio),
                            count = audioHistoryCount,
                            onClick = { onNavigateToPlayHistory(2) },
                            modifier = Modifier.padding(horizontal = screenOuter),
                        )
                    }
                    item(key = "audio_row") {
                        RecentMediaGrid(
                            mediaItems = recentAudioPlays,
                            columns = 1,
                            thumbnailUrls = thumbnailUrls,
                            storageReachability = storageReachability,
                            contentScale = ContentScale.Fit,
                            squareCover = true,
                            edgePadding = screenOuter,
                            cardWidth = 120.dp,
                            onItemClick = onResumePlay,
                        )
                    }
                }

                if (quickAccessItems.isNotEmpty()) {
                    item(key = "qa_header") {
                        NiSectionHeader(
                            title = stringResource(R.string.quick_access_title),
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
internal fun CinematicHeroBanner(
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
    // 组合色：无缩略图时用 主色→三级色 渐变更能体现整屏氛围
    val fallbackBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
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
            // 语义合并：背景/标题/进度合并为单一节点，降低语义树节点数
            .semantics(mergeDescendants = true) {}
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
                            Color.Black.copy(alpha = 0.32f),
                            Color.Black.copy(alpha = 0.06f),
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
                    text = stringResource(R.string.home_continue_play),
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
                            contentDescription = stringResource(R.string.play),
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
                            text = stringResource(R.string.home_continue_play),
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
internal fun HomeSingleColumnLayout(
    recentPlays: List<PlayHistoryEntity>,
    recentVideoPlays: List<PlayHistoryEntity>,
    recentAudioPlays: List<PlayHistoryEntity>,
    quickAccessItems: List<QuickAccessUiItem>,
    thumbnailUrls: Map<String, String>,
    qaThumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    recentColumns: Int,
    qaColumns: Int,
    contentMaxWidth: Dp,
    heroMaxWidth: Dp,
    topInset: Dp,
    videoHistoryCount: Int,
    audioHistoryCount: Int,
    onNavigateToPlayHistory: (Int) -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onResumePlay: (PlayHistoryEntity) -> Unit,
    onOpenQuickAccess: (QuickAccessUiItem) -> Unit,
) {
    val screenOuter = NiSpacings.responsiveScreenOuter
    // 底部导航栏避让：与 HomeMagazineLayout 保持一致，清除玻璃底栏
    val bottomBarClearance = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    } + 88.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = screenOuter,
                end = screenOuter,
                top = topInset + 8.dp,
                bottom = bottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(NiSpacings.responsiveListGap),
        ) {
            if (recentPlays.isEmpty() && quickAccessItems.isEmpty()) {
                item(key = "empty_all") {
                    NiEmptyState(
                        icon = Icons.Rounded.Star,
                        text = stringResource(R.string.home_empty_title),
                        hint = stringResource(R.string.home_empty_hint),
                    )
                }
            } else {
                if (recentPlays.isEmpty()) {
                    item(key = "history_empty") {
                        NiEmptyState(
                            icon = Icons.Rounded.History,
                            text = stringResource(R.string.home_no_history),
                            hint = stringResource(R.string.home_no_history_hint),
                        )
                    }
                } else {
                    // 英雄卡仅展示最近一个视频（无轮播），视频历史其余项单独成行
                    val featuredVideo = recentVideoPlays.firstOrNull()
                    val restVideo = featuredVideo?.let { recentVideoPlays.drop(1) } ?: emptyList()

                    if (featuredVideo != null) {
                        item(key = "hero") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(modifier = Modifier.widthIn(max = heroMaxWidth)) {
                                    HomeHeroItem(
                                        hero = featuredVideo,
                                        thumbnailUrls = thumbnailUrls,
                                        storageReachability = storageReachability,
                                        onClick = { onResumePlay(featuredVideo) },
                                    )
                                }
                            }
                        }
                    }

                    if (restVideo.isNotEmpty()) {
                        item(key = "video_header") {
                            NiSectionHeader(
                                title = stringResource(R.string.home_recent_video),
                                count = videoHistoryCount,
                                onClick = { onNavigateToPlayHistory(1) },
                            )
                        }
                        item(key = "video_row") {
                            RecentMediaGrid(
                                mediaItems = restVideo,
                                columns = recentColumns,
                                thumbnailUrls = thumbnailUrls,
                                storageReachability = storageReachability,
                                contentScale = ContentScale.Crop,
                                squareCover = false,
                                onItemClick = onResumePlay,
                            )
                        }
                    }

                    if (recentAudioPlays.isNotEmpty()) {
                        item(key = "audio_header") {
                            NiSectionHeader(
                                title = stringResource(R.string.home_recent_audio),
                                count = audioHistoryCount,
                                onClick = { onNavigateToPlayHistory(2) },
                            )
                        }
                        item(key = "audio_row") {
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
                }
            }

            if (quickAccessItems.isNotEmpty()) {
                item(key = "qa_header") {
                    NiSectionHeader(
                        title = stringResource(R.string.quick_access_title),
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
        }
    }
}

/**
 * 英雄续播项：横版/竖版英雄卡 + 不可达半透明 + 右上角「离线」角标。
 */
@Composable
internal fun HomeHeroItem(
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
 * 快速访问单行：按 [columns] 均分宽度，末行不足时用等宽占位填齐避免拉伸。
 */
@Composable
internal fun HomeQuickAccessRow(
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
                thumbnailUrl = thumbnailUrls[qaItem.qaThumbKey],
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
