package com.nichx.niplayer.feature.home.home

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.feature.home.MediaFileTypes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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

@Composable
fun HomeTabScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayHistory: () -> Unit,
    onNavigateToQuickAccess: () -> Unit,
    onNavigateToStorageFile: (Int, String) -> Unit,
    onPlayVideo: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeTabViewModel = hiltViewModel(),
) {
    val recentPlays by viewModel.recentPlays.collectAsStateWithLifecycle()
    val recentVideoPlays by viewModel.recentVideoPlays.collectAsStateWithLifecycle()
    val recentAudioPlays by viewModel.recentAudioPlays.collectAsStateWithLifecycle()
    val quickAccessItems by viewModel.quickAccessItems.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    val thumbnailUrls by viewModel.thumbnailUrls.collectAsStateWithLifecycle()
    val qaThumbnailUrls by viewModel.qaThumbnailUrls.collectAsStateWithLifecycle()
    val storageReachability by viewModel.storageReachability.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeTabEvent.NavigateToPlayer -> onPlayVideo()
                is HomeTabEvent.NavigateToStorageFile -> onNavigateToStorageFile(event.libraryId, event.relativePath)
                is HomeTabEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "NIplayer",
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
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState, bottomPadding = 80.dp) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!dataReady) {
                NiHomeLoadingState()
            } else {
                val qaColumns = when (LocalNiWindowSizeClass.current.width) {
                    NiWindowWidthSizeClass.Compact -> 2
                    NiWindowWidthSizeClass.Medium -> 3
                    NiWindowWidthSizeClass.Expanded -> 4
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (recentPlays.isEmpty() && quickAccessItems.isEmpty()) {
                        item(key = "empty_all") {
                            NiEmptyState(
                                icon = Icons.Rounded.Star,
                                text = "暂无内容",
                                hint = "从媒体库添加存储源开始使用",
                            )
                        }
                    } else {
                        if (recentPlays.isNotEmpty()) {
                            val hero = recentPlays.first()
                            val heroProgress = if (hero.videoDuration > 0)
                                hero.videoPosition.toFloat() / hero.videoDuration.toFloat() else 0f
                            val heroReachable = isHistoryReachable(hero, storageReachability)
                            item(key = "hero") {
                                Box {
                                    NiHeroResumeCard(
                                        title = hero.videoName,
                                        durationText = formatTime(hero.videoDuration),
                                        positionText = formatTime(hero.videoPosition),
                                        thumbnailModel = buildHeroThumbnailModel(
                                            hero.url, hero.mediaType, hero.videoName, thumbnailUrls,
                                        ),
                                        progressFraction = heroProgress,
                                        contentScale = ContentScale.Crop,
                                        onClick = { viewModel.resumePlay(hero) },
                                        modifier = if (!heroReachable) Modifier.graphicsLayer { alpha = 0.5f } else Modifier,
                                    )
                                    if (!heroReachable) {
                                        UnreachableBadge(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                        )
                                    }
                                }
                            }

                            if (recentVideoPlays.isNotEmpty()) {
                                item(key = "recent_video_header") {
                                    NiSectionHeader(
                                        title = "最近播放视频",
                                        count = recentVideoPlays.size,
                                        onClick = onNavigateToPlayHistory,
                                    )
                                }

                                item(key = "recent_video_row") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(recentVideoPlays, key = { it.id }) { history ->
                                            val progress = if (history.videoDuration > 0)
                                                history.videoPosition.toFloat() / history.videoDuration.toFloat() else 0f
                                            val reachable = isHistoryReachable(history, storageReachability)
                                            Box(modifier = Modifier.graphicsLayer { if (!reachable) alpha = 0.5f }) {
                                                NiThumbCard(
                                                    title = history.videoName,
                                                    durationText = formatTime(history.videoDuration),
                                                    thumbnailModel = buildThumbnailModel(history.url, history.mediaType, thumbnailUrls),
                                                    progressFraction = progress,
                                                    mediaLabel = mediaTypeLabel(history.mediaType),
                                                    contentScale = ContentScale.Crop,
                                                    onClick = { viewModel.resumePlay(history) },
                                                )
                                            }
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
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(recentAudioPlays, key = { it.id }) { history ->
                                            val progress = if (history.videoDuration > 0)
                                                history.videoPosition.toFloat() / history.videoDuration.toFloat() else 0f
                                            val reachable = isHistoryReachable(history, storageReachability)
                                            Box(modifier = Modifier.graphicsLayer { if (!reachable) alpha = 0.5f }) {
                                                NiThumbCard(
                                                    title = history.videoName,
                                                    durationText = formatTime(history.videoDuration),
                                                    thumbnailModel = buildThumbnailModel(history.url, history.mediaType, thumbnailUrls),
                                                    progressFraction = progress,
                                                    mediaLabel = mediaTypeLabel(history.mediaType),
                                                    contentScale = ContentScale.Fit,
                                                    onClick = { viewModel.resumePlay(history) },
                                                    squareCover = true,
                                                )
                                            }
                                        }
                                    }
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    row.forEach { qaItem ->
                                        val effectiveValid = qaItem.libraryValid &&
                                            storageReachability[qaItem.entity.libraryId] != false
                                        HomeQuickAccessGridItem(
                                            item = qaItem,
                                            thumbnailUrl = qaThumbnailUrls[qaItem.entity.storagePath],
                                            isValid = effectiveValid,
                                            onClick = { viewModel.openQuickAccessItem(qaItem) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (row.size < qaColumns) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
