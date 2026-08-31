package com.nichx.niplayer.feature.player

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow

import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.datastore.DownloadSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.designsystem.components.DownloadTargetChooserDialog
import com.nichx.niplayer.designsystem.components.NiGlassDropdownMenu
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import java.util.Locale
import kotlinx.coroutines.delay

/** 横屏沉浸模式：无操作自动隐藏控件的延时（ms）。 */
private const val AUTO_HIDE_DELAY_MS = 3000L

@Composable
fun AudioPlayerScreen(
    onBack: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    audioPlaybackManager: AudioPlaybackManager? = null,
) {
    val messageController = LocalAppMessageController.current

    LaunchedEffect(Unit) {
        viewModel.downloadEvent.collect { msg ->
            messageController.post(NiMessage.info(msg))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            messageController.post(NiMessage.info(msg))
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
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()
    // 本地文件（已下载/缓存直链）来源时隐藏下载按钮
    val isLocalSource by audioPlaybackManager?.isLocalSource?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }

    val hasActiveContent = title.isNotEmpty()

    // 播放模式由 AudioPlaybackManager 统一管理（含持久化），UI 只读订阅
    val playMode by audioPlaybackManager?.playModeIndex?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(0) }
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

    // 倍速：音频独立 4 档（0.5/1/1.5/2），偏好走 PlayerSettings.audioSpeedIndex，
    // 与视频 8 档互不影响
    val speedValues = AudioPlaybackManager.AudioPlaybackSpeedValues
    var speedIndex by rememberSaveable {
        mutableIntStateOf(PlayerSettings.audioSpeedIndex.coerceIn(0, speedValues.lastIndex))
    }
    LaunchedEffect(speedIndex) {
        audioPlaybackManager?.setPlaybackSpeed(speedValues[speedIndex])
        PlayerSettings.audioSpeedIndex = speedIndex
    }

    val hasNext = currentIndex in 0 until playlist.lastIndex
    val hasPrev = currentIndex > 0

    // 横屏 / 竖屏自适应：横屏用左右分栏布局（黑胶 + 控制区），竖屏用原单列布局
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 横屏沉浸全屏：隐藏系统状态栏与手势条，让唱片在无系统栏干扰下真正居中；
    // 轻扫屏幕边缘可临时唤出系统栏。退出横屏（回竖屏/离开页面）时恢复系统栏。
    val activity = LocalActivity.current
    DisposableEffect(isLandscape) {
        if (isLandscape) {
            val window = activity?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val originalBehavior = controller?.systemBarsBehavior
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                activity?.window?.let { w ->
                    val c = WindowCompat.getInsetsController(w, w.decorView)
                    c.show(WindowInsetsCompat.Type.systemBars())
                    originalBehavior?.let { c.systemBarsBehavior = it }
                }
            }
        } else {
            onDispose { }
        }
    }

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
                modeLabel = stringResource(mode.labelRes),
                onCyclePlayMode = { audioPlaybackManager?.cyclePlayMode() },
                onShowPlaylist = { showPlaylist = true },
                onBack = onBack,
                onDownload = { viewModel.requestDownload() },
                onEqualizer = onEqualizer,
                speedOptions = speedValues,
                currentSpeedIndex = speedIndex,
                onSpeedSelect = { speedIndex = it },
                showDownload = !isLocalSource,
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
                modeLabel = stringResource(mode.labelRes),
                onToggleLyrics = { showLyrics = !showLyrics },
                onCyclePlayMode = { audioPlaybackManager?.cyclePlayMode() },
                onShowPlaylist = { showPlaylist = true },
                onBack = onBack,
                onDownload = { viewModel.requestDownload() },
                onEqualizer = onEqualizer,
                speedOptions = speedValues,
                currentSpeedIndex = speedIndex,
                onSpeedSelect = { speedIndex = it },
                showDownload = !isLocalSource,
            )
        }

        PlaylistSheet(
            show = showPlaylist && playlist.isNotEmpty(),
            playlist = playlist,
            currentIndex = currentIndex,
            playMode = playMode,
            onDismiss = { showPlaylist = false },
            onPlayAtIndex = { index -> viewModel.playAtIndex(index) },
        )

        if (showDownloadDialog) {
            DownloadTargetChooserDialog(
                presetPath = DownloadSettings.downloadDirPath,
                onDismiss = { viewModel.closeDownloadDialog() },
                onDownloadToPreset = { viewModel.downloadToPreset() },
                onDownloadToPath = { path, dirName, setAsPreset ->
                    viewModel.downloadToPath(path, dirName, setAsPreset)
                },
            )
        }

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
    speedOptions: List<Float> = listOf(1f),
    currentSpeedIndex: Int = 0,
    onSpeedSelect: (Int) -> Unit = {},
    showDownload: Boolean = true,
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
            speedOptions = speedOptions,
            currentSpeedIndex = currentSpeedIndex,
            onSpeedSelect = onSpeedSelect,
            showDownload = showDownload,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasActiveContent) {
                Text(
                    text = stringResource(R.string.player_no_source),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(2f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

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
    speedOptions: List<Float> = listOf(1f),
    currentSpeedIndex: Int = 0,
    onSpeedSelect: (Int) -> Unit = {},
    showDownload: Boolean = true,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    // 大屏（平板/大屏手机横屏）下歌词行数更多，配合 LyricsView 内部字号/行高自适应放大
    val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 800

    // 沉浸模式：横屏 3s 无操作自动隐藏顶部行与底部控件，仅留歌词（5 行）；
    // 点击空白处切换控件显隐（显示时歌词 3 行，隐藏时 5 行）。
    // 无歌词时不启用自动隐藏。
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val hasLyrics = lrcLines.isNotEmpty()
    // 更多/倍速下拉菜单展开中：暂停自动隐藏计时，避免菜单还开着控件就收起
    var menuOpen by remember { mutableStateOf(false) }

    fun onBackgroundTap() {
        // 点击空白处切换控件显隐；恢复显示时重启 3s 自动隐藏计时
        controlsVisible = !controlsVisible
        if (controlsVisible) interactionTick++
    }

    // 每次交互递增 interactionTick 重启 3s 计时；无歌词时强制显示控件；
    // 下拉菜单展开时不执行自动隐藏
    LaunchedEffect(interactionTick, hasLyrics, menuOpen) {
        if (!hasLyrics || menuOpen) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(AUTO_HIDE_DELAY_MS)
        controlsVisible = false
    }

    // 横屏不使用独立顶栏：整屏为左右分栏 Row。
    // 左侧唱片占满全部高度；右侧顶部一行 = 返回 + 歌名 + 操作按钮（更多），
    // 下方为歌词（占满剩余空间）与一行式播放控件。最底部常驻细进度条。
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 点击任意空白处切换控件显隐
            .pointerInput(Unit) { detectTapGestures { onBackgroundTap() } },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // 横屏已沉浸全屏（隐藏系统栏），无需 statusBarsPadding，保证唱片居中
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // 左侧：唱片占满全部高度
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasActiveContent -> Text(
                    text = stringResource(R.string.player_no_source),
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
                    // 横屏移除唱针；高度留白较多，让出空间给歌词区
                    modifier = Modifier
                        .fillMaxHeight(0.78f)
                        .aspectRatio(1f),
                    showNeedle = false,
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // 右侧列：顶部行（返回 + 歌名 + 操作按钮）+ 歌词 + 控件
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
        ) {
            // 顶部行：返回 | 歌名 | 更多（沉浸模式下自动隐藏）
            AnimatedVisibility(visible = controlsVisible) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    contentDescription = stringResource(R.string.player_back),
                                    tint = onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                        Text(
                            text = if (title.isNotEmpty()) title else stringResource(R.string.player_unknown_song),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TopBarActions(
                            onDownload = onDownload,
                            onEqualizer = onEqualizer,
                            speedOptions = speedOptions,
                            currentSpeedIndex = currentSpeedIndex,
                            onSpeedSelect = onSpeedSelect,
                            onMenuOpenChange = { menuOpen = it },
                            showDownload = showDownload,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (hasLyrics) {
                // 歌词：控件可见时 3 行（为顶栏/控件让位），隐藏时 5 行（沉浸展示）
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
                        // 大屏行数更多：控件可见 3/5 行，沉浸 5/8 行
                        maxVisibleLines = if (controlsVisible) {
                            if (isLargeScreen) 5 else 3
                        } else {
                            if (isLargeScreen) 8 else 5
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 底部一行式控件（沉浸模式下自动隐藏）
                AnimatedVisibility(visible = controlsVisible) {
                    Column {
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
                }
            } else if (hasActiveContent) {
                // 无歌词：控件在剩余空间垂直居中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
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
        }

        // 最底部细进度条：仅在沉浸模式（控件隐藏）下显示，作为进度的唯一指示；
        // 控件可见时底部已有完整进度条，避免重复
        AnimatedVisibility(visible = !controlsVisible) {
            ThinProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
/** 底部细进度条：3dp 高，按播放进度填充主色，不参与交互，用于沉浸模式下指示进度。 */
@Composable
private fun ThinProgressBar(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(3.dp)
            .background(onSurface.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(primary),
        )
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

/**
 * 顶栏操作按钮组：更多（内含倍速二级菜单 / 均衡器 / 下载）。
 * 竖屏 TopBar 与横屏顶部行共用，保证按钮与菜单样式一致。
 */
@Composable
private fun TopBarActions(
    onDownload: () -> Unit,
    onEqualizer: () -> Unit,
    speedOptions: List<Float>,
    currentSpeedIndex: Int,
    onSpeedSelect: (Int) -> Unit,
    onMenuOpenChange: (Boolean) -> Unit = {},
    showDownload: Boolean = true,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // 更多/倍速下拉菜单锚点（More 按钮屏幕坐标，供玻璃菜单定位）
    var moreMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    val safeSpeedIndex = currentSpeedIndex.coerceIn(0, speedOptions.lastIndex)

    // 任意下拉菜单展开/收起时通知外层（横屏用于暂停自动隐藏计时）
    LaunchedEffect(showSpeedMenu, showMoreMenu) {
        onMenuOpenChange(showSpeedMenu || showMoreMenu)
    }

    // 菜单卡片样式：与 NiPopupMenu 统一的磨砂风格（20dp 大圆角 + 不透明磨砂底色 + 细边框 + 阴影）
    // 菜单位于独立 Popup 窗口，底色必须不透明，否则会把窗口垫层透出来形成多余浅色矩形
    val menuShape = RoundedCornerShape(20.dp)
    val menuBorderColor = niGlassBorderColor()
    val menuItemPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 更多：倍速（二级菜单）/ 均衡器 / 下载 收进溢出菜单，保持顶栏简洁
        Box(
            modifier = Modifier.onGloballyPositioned { coords ->
                // 锚点取按钮左下角，菜单从按钮正下方展开（不遮挡按钮）
                val topLeft = coords.localToRoot(Offset.Zero)
                moreMenuAnchor = topLeft + Offset(0f, coords.size.height.toFloat())
            },
        ) {
            IconButton(onClick = { showMoreMenu = true }) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.player_more),
                        tint = onSurface.copy(alpha = 0.8f),
                    )
                }
            }
            NiGlassDropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                anchor = IntOffset(moreMenuAnchor.x.toInt(), moreMenuAnchor.y.toInt()),
            ) {
                // 倍速：子菜单入口，尾部显示当前档位 + 展开箭头
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.player_speed_icon),
                            fontSize = 14.sp,
                            color = onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = formatSpeedLabel(speedOptions[safeSpeedIndex]),
                                color = onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    contentPadding = menuItemPadding,
                    onClick = {
                        showMoreMenu = false
                        showSpeedMenu = true
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = onSurface.copy(alpha = 0.08f),
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.player_equalizer),
                            fontSize = 14.sp,
                            color = onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    contentPadding = menuItemPadding,
                    onClick = {
                        showMoreMenu = false
                        onEqualizer()
                    },
                )
                if (showDownload) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.player_download_icon),
                                fontSize = 14.sp,
                                color = onSurface,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        contentPadding = menuItemPadding,
                        onClick = {
                            showMoreMenu = false
                            onDownload()
                        },
                    )
                }
            }
            // 倍速二级菜单：磨砂卡片 + 标题头，选择后自动关闭
            NiGlassDropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { showSpeedMenu = false },
                anchor = IntOffset(moreMenuAnchor.x.toInt(), moreMenuAnchor.y.toInt()),
            ) {
                // 菜单标题（本版本无 DropdownMenuHeader，用普通文本行代替）
                Text(
                    text = stringResource(R.string.player_speed_menu_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    thickness = 0.5.dp,
                    color = onSurface.copy(alpha = 0.08f),
                )
                speedOptions.forEachIndexed { idx, speed ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = formatSpeedLabel(speed),
                                fontSize = 14.sp,
                                color = if (idx == safeSpeedIndex) primary else onSurface,
                                fontWeight = if (idx == safeSpeedIndex) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        contentPadding = menuItemPadding,
                        onClick = {
                            showSpeedMenu = false
                            onSpeedSelect(idx)
                        },
                        trailingIcon = if (idx == safeSpeedIndex) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.player_current_speed),
                                    tint = primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onDownload: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    speedOptions: List<Float> = listOf(1f),
    currentSpeedIndex: Int = 0,
    onSpeedSelect: (Int) -> Unit = {},
    showDownload: Boolean = true,
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
                    contentDescription = stringResource(R.string.player_back),
                    tint = onSurface.copy(alpha = 0.8f),
                )
            }
        }

        // 标题：空标题时（无此场景）不显示占位文本，用 Spacer 维持两端按钮间距
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = onSurface.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        TopBarActions(
            onDownload = onDownload,
            onEqualizer = onEqualizer,
            speedOptions = speedOptions,
            currentSpeedIndex = currentSpeedIndex,
            onSpeedSelect = onSpeedSelect,
            showDownload = showDownload,
        )
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

    // 拖动进度条时以本地值驱动滑块（跟手），松手才提交 seek
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val sliderPos = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, duration.toFloat())
    // 拖动中左侧时间显示目标位置（预览），松手后回到实际播放位置
    val displayMs = if (dragging) dragValue.toLong() else positionMs

    Slider(
        value = sliderPos,
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            if (dragging) {
                onSeek(dragValue.toLong())
                dragging = false
            }
        },
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
            text = formatTime(displayMs),
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
                        contentDescription = stringResource(R.string.player_previous),
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
                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
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
                        contentDescription = stringResource(R.string.player_next),
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
                        contentDescription = stringResource(R.string.player_playlist),
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
            contentDescription = stringResource(R.string.player_playback_error),
            tint = errorColor,
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.player_error_title),
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
                    text = stringResource(R.string.player_retry),
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

/** 倍速文字标签：整数档省略小数（1.0x、1.25x）。 */
private fun formatSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}
