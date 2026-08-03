package com.nichx.niplayer.feature.player

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.util.Locale
import kotlinx.coroutines.launch

private enum class PlayMode(val label: String) {
    Loop("顺序播放"),
    Shuffle("随机播放"),
    Single("单曲循环"),
}

@Composable
fun AudioPlayerScreen(
    onBack: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    audioPlaybackManager: AudioPlaybackManager? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.downloadEvent.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // 所有播放状态直接从 AudioPlaybackManager 读取
    val isPlaying by audioPlaybackManager?.isPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val title by audioPlaybackManager?.currentTitle?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") }
    val coverPath by audioPlaybackManager?.audioCoverPath?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val positionMs by audioPlaybackManager?.positionMs?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0L) }
    val durationMs by audioPlaybackManager?.durationMs?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0L) }
    val playlist by audioPlaybackManager?.playlist?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(emptyList()) }
    val currentIndex by audioPlaybackManager?.currentIndex?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(-1) }
    val lrcText by audioPlaybackManager?.lrcText?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val playbackError by audioPlaybackManager?.playbackError?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }

    val hasActiveContent = title.isNotEmpty()

    var playMode by remember { mutableIntStateOf(0) }
    val mode = PlayMode.entries[playMode]
    val modeIcon = when (mode) {
        PlayMode.Loop -> Icons.Rounded.Repeat
        PlayMode.Shuffle -> Icons.Rounded.Shuffle
        PlayMode.Single -> Icons.Rounded.RepeatOne
    }

    val lrcLines = remember(lrcText) {
        if (lrcText != null) LrcParser.parse(lrcText!!) else emptyList()
    }

    var showLyrics by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }

    val hasNext = currentIndex in 0 until playlist.lastIndex
    val hasPrev = currentIndex > 0

    // 横屏 / 竖屏自适应：横屏用左右分栏布局（黑胶 + 控制区），竖屏用原单列布局
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundLayer(coverData = coverPath)

        if (isLandscape) {
            LandscapeLayout(
                hasActiveContent = hasActiveContent,
                playbackError = playbackError,
                onRetry = { audioPlaybackManager?.retry() },
                lrcLines = lrcLines,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
                title = title,
                isPlaying = isPlaying,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                coverPath = coverPath,
                playMode = playMode,
                modeIcon = modeIcon,
                modeLabel = mode.label,
                onCyclePlayMode = { playMode = (playMode + 1) % PlayMode.entries.size },
                onShowPlaylist = { showPlaylist = true },
                onBack = onBack,
                onDownload = { viewModel.downloadCurrentFile() },
                onEqualizer = onEqualizer,
            )
        } else {
            PortraitLayout(
                hasActiveContent = hasActiveContent,
                playbackError = playbackError,
                onRetry = { audioPlaybackManager?.retry() },
                showLyrics = showLyrics,
                lrcLines = lrcLines,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { pos -> audioPlaybackManager?.seekTo(pos) },
                title = title,
                isPlaying = isPlaying,
                hasPrev = hasPrev,
                hasNext = hasNext,
                onTogglePlay = { audioPlaybackManager?.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                coverPath = coverPath,
                playlist = playlist,
                currentIndex = currentIndex,
                playMode = playMode,
                modeIcon = modeIcon,
                modeLabel = mode.label,
                onToggleLyrics = { showLyrics = !showLyrics },
                onCyclePlayMode = { playMode = (playMode + 1) % PlayMode.entries.size },
                onShowPlaylist = { showPlaylist = true },
                onBack = onBack,
                onDownload = { viewModel.downloadCurrentFile() },
                onEqualizer = onEqualizer,
            )
        }

        if (showPlaylist && playlist.isNotEmpty()) {
            PlaylistSheet(
                playlist = playlist,
                currentIndex = currentIndex,
                playMode = playMode,
                onDismiss = { showPlaylist = false },
                onPlayAtIndex = { index -> viewModel.playAtIndex(index) },
                onSwitchPlayMode = {
                    playMode = (playMode + 1) % PlayMode.entries.size
                },
                onSaveToPlaylist = {
                    showPlaylist = false
                    showSavePlaylistDialog = true
                },
            )
        }

        if (showSavePlaylistDialog) {
            SaveToPlaylistDialog(
                playlist = playlist,
                onDismiss = { showSavePlaylistDialog = false },
                onSaved = { message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                },
            )
        }

        NiSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BackgroundLayer(coverData: Any?) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(background))
        if (coverData != null) {
            val context = LocalContext.current
            val request = remember(coverData) {
                when (coverData) {
                    is String -> ImageRequest.Builder(context)
                        .data(coverData)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    is ImageRequest -> coverData.newBuilder()
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    else -> coverData
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.20f,
            )
        }
        // Bottom gradient overlay for depth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
        ) {
            Spacer(modifier = Modifier.weight(0.55f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                background.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    hasActiveContent: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    showLyrics: Boolean,
    lrcLines: List<LrcLine>,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    title: String,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    coverPath: Any?,
    playlist: List<*>,
    currentIndex: Int,
    playMode: Int,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeLabel: String,
    onToggleLyrics: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onEqualizer: () -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        TopBar(
            title = title,
            onBack = onBack,
            onDownload = onDownload,
            onEqualizer = onEqualizer,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasActiveContent) {
                Text(
                    text = "无播放源",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface.copy(alpha = 0.6f),
                )
            } else if (playbackError != null) {
                PlaybackErrorState(
                    errorMessage = playbackError,
                    onRetry = onRetry,
                )
            } else if (showLyrics) {
                // 歌词视图：点击空白处（非歌词行）切换回唱片
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
                    // 歌词区域只占中间 75% 高度并居中，上下各留约 12.5% 空白，
                    // 便于点击空白处切换回唱片；行数限制为 5 行，避免显示过多。
                    Box(
                        modifier = Modifier.fillMaxHeight(0.75f),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsView(
                            lrcLines = lrcLines,
                            currentPositionMs = positionMs,
                            onSeek = onSeek,
                            maxVisibleLines = 7,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                // 唱片视图：点击唱片切换到歌词
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
                    VinylRecordPlayer(
                        coverData = coverPath,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f),
                    )
                }
            }
        }

        if (hasActiveContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (playlist.isNotEmpty() && currentIndex >= 0) {
                    val subtitleText = "${currentIndex + 1} / ${playlist.size}"
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ProgressSection(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )

                Spacer(modifier = Modifier.height(8.dp))

                PlaybackControls(
                    isPlaying = isPlaying,
                    buffering = false,
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onTogglePlay = onTogglePlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    playMode = playMode,
                    modeIcon = modeIcon,
                    modeLabel = modeLabel,
                    onCyclePlayMode = onCyclePlayMode,
                    onShowPlaylist = onShowPlaylist,
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * 横屏布局：左右分栏。
 * 左侧展示黑胶 / 播放错误 / 无播放源（保持不变）；
 * 右侧同时显示歌词（上方，行数自适应、最少 3 行）与一行式播放控件（底部）；
 * 无歌词时右侧仅显示控件并居中。横屏高度紧凑，使用紧凑模式控件。
 */
@Composable
private fun LandscapeLayout(
    hasActiveContent: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    lrcLines: List<LrcLine>,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    title: String,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    coverPath: Any?,
    playMode: Int,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onEqualizer: () -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        TopBar(
            title = title,
            onBack = onBack,
            onDownload = onDownload,
            onEqualizer = onEqualizer,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !hasActiveContent -> Text(
                        text = "无播放源",
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurface.copy(alpha = 0.6f),
                    )
                    playbackError != null -> PlaybackErrorState(
                        errorMessage = playbackError,
                        onRetry = onRetry,
                    )
                    else -> VinylRecordPlayer(
                        coverData = coverPath,
                        isPlaying = isPlaying,
                        // 高度 60% 保证唱针尖端不超出边界（唱针高于碟面约 29% 直径）
                        modifier = Modifier
                            .fillMaxHeight(0.6f)
                            .aspectRatio(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (lrcLines.isNotEmpty()) {
                    // 歌词：上方歌词（行数自适应，最少 3 行）+ 底部一行控件，同时显示
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = 132.dp), // 44dp × 3 行下限
                            contentAlignment = Alignment.Center,
                        ) {
                            LyricsView(
                                lrcLines = lrcLines,
                                currentPositionMs = positionMs,
                                onSeek = onSeek,
                                maxVisibleLines = 8,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        ControlColumn(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeek = onSeek,
                            isPlaying = isPlaying,
                            hasPrev = hasPrev,
                            hasNext = hasNext,
                            onTogglePlay = onTogglePlay,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            playMode = playMode,
                            modeIcon = modeIcon,
                            modeLabel = modeLabel,
                            onCyclePlayMode = onCyclePlayMode,
                            onShowPlaylist = onShowPlaylist,
                            compact = true,
                        )
                    }
                } else if (hasActiveContent) {
                    ControlColumn(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = onSeek,
                        isPlaying = isPlaying,
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        onTogglePlay = onTogglePlay,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        playMode = playMode,
                        modeIcon = modeIcon,
                        modeLabel = modeLabel,
                        onCyclePlayMode = onCyclePlayMode,
                        onShowPlaylist = onShowPlaylist,
                        compact = true,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * 播放控件列：进度条 + 一行式播放控制。
 *
 * 竖屏（compact=false）沿用原尺寸与间距；横屏（compact=true）收紧
 * 按钮尺寸与间距，为上方歌词区域腾出空间。
 */
@Composable
private fun ControlColumn(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playMode: Int,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressSection(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

        PlaybackControls(
            isPlaying = isPlaying,
            buffering = false,
            hasPrev = hasPrev,
            hasNext = hasNext,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            playMode = playMode,
            modeIcon = modeIcon,
            modeLabel = modeLabel,
            onCyclePlayMode = onCyclePlayMode,
            onShowPlaylist = onShowPlaylist,
            compact = compact,
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onDownload: () -> Unit = {},
    onEqualizer: () -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = onSurface.copy(alpha = 0.8f),
                )
            }
        }

        Text(
            text = if (title.isNotEmpty()) title else "音频播放",
            style = MaterialTheme.typography.titleMedium,
            color = onSurface.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onEqualizer) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = "均衡器",
                    tint = onSurface.copy(alpha = 0.8f),
                )
            }
        }

        IconButton(onClick = onDownload) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "下载",
                    tint = onSurface.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val duration = durationMs.coerceAtLeast(1L)
    val sliderPos by animateFloatAsState(
        targetValue = positionMs.toFloat().coerceIn(0f, duration.toFloat()),
        animationSpec = tween(durationMillis = 200),
        label = "sliderPosition",
    )

    Slider(
        value = sliderPos,
        onValueChange = { onSeek(it.toLong()) },
        valueRange = 0f..duration.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = primary,
            activeTrackColor = primary.copy(alpha = 0.8f),
            inactiveTrackColor = onSurface.copy(alpha = 0.2f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTime(positionMs),
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    buffering: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playMode: Int,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    compact: Boolean = false,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val sideSize = if (compact) 38.dp else 42.dp
    val sideIconSize = if (compact) 24.dp else 28.dp
    val mainSize = if (compact) 56.dp else 64.dp
    val mainIconSize = if (compact) 32.dp else 36.dp
    val gap = if (compact) 12.dp else 16.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左端：播放模式
        Box(modifier = Modifier.weight(1f)) {
            IconButton(onClick = onCyclePlayMode) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = modeLabel,
                        tint = onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }
        }

        // 中间：上一首 / 播放暂停 / 下一首（严格居中）
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = hasPrev,
            ) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        tint = if (hasPrev) onSurface.copy(alpha = 0.8f)
                        else onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }

            Spacer(modifier = Modifier.width(gap))

            Box(
                modifier = Modifier
                    .size(mainSize)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.2f))
                    .clickable(enabled = !buffering) { onTogglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = primary,
                    modifier = Modifier.size(mainIconSize),
                )
            }

            Spacer(modifier = Modifier.width(gap))

            IconButton(
                onClick = onNext,
                enabled = hasNext,
            ) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = if (hasNext) onSurface.copy(alpha = 0.8f)
                        else onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }
        }

        // 右端：播放列表
        Box(modifier = Modifier.weight(1f)) {
            IconButton(
                onClick = onShowPlaylist,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "播放列表",
                        tint = onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = "播放错误",
            tint = errorColor,
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "播放失败",
            style = MaterialTheme.typography.headlineSmall,
            color = onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { onRetry() }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
