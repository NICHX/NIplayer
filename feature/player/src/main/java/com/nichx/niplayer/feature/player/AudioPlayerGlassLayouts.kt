package com.nichx.niplayer.feature.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nichx.niplayer.designsystem.components.LocalNiGlassEnabled
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlinx.coroutines.delay

/**
 * 液态玻璃 / 模糊封面风格的音频播放器布局。
 *
 * 与既有黑胶布局（[PortraitLayout] / [LandscapeLayout]）并存，通过
 * [com.nichx.niplayer.datastore.PlayerSettings.audioPlayerStyle] 选择：
 * - 背景：封面高斯模糊铺底 + 主题色 scrim（浅色模式减弱，保留背景色彩供玻璃折射）。
 * - 玻璃质感只属于**按钮**：所有圆形控件（返回/外观/更多/播放控件）统一走
 *   [GlassCircleButton]（Kyant backdrop 的 vibrancy + blur + lens 真折射），
 *   不再给控制区套整张玻璃卡片。
 * - 封面卡外框同样是 backdrop 玻璃，与按钮同一配方。
 * - 复用既有 [ProgressSection] / [LyricsView] / [ThinProgressBar]，播放逻辑完全一致。
 */

/** 封面卡玻璃外框圆角。 */
private val GlassCoverCorner = 24.dp

/** backdrop 玻璃的模糊/折射参数（与 NiGlassBarDefaults 一致，全局质感统一）。 */
private object GlassCardDefaults {
    const val BlurRadius = 8f
    const val LensRadius = 6f
}

/**
 * 全屏玻璃背景：主题底色 + 封面高斯模糊铺底 + 主题色 scrim。
 *
 * 模糊半径取 32dp：保留少量可辨识的明暗结构，供按钮 lens 折射呈现内容；
 * scrim 在浅色模式下减弱（洗白会让玻璃透不出背景色彩，失去玻璃感）。
 */
@Composable
internal fun GlassBackground(coverData: Any?) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    // 浅色模式 scrim 减半：保住背景色彩饱和度，让玻璃按钮有"色"可折射
    val scrimStrength = if (NiExtraColors.current.isDark) 1f else 0.55f
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(background))
        if (coverData != null) {
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
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // 放大避免模糊边缘露出底色，再高斯模糊形成环境光
                    .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
                    .blur(32.dp)
                    .alpha(0.95f),
            )
        }
        // 主题色 scrim：顶部轻、底部重，压住模糊纹理保证前景文字对比度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            background.copy(alpha = 0.34f * scrimStrength),
                            background.copy(alpha = 0.20f * scrimStrength),
                            background.copy(alpha = 0.60f * scrimStrength),
                            background.copy(alpha = 0.82f * scrimStrength),
                        ),
                    ),
                ),
        )
    }
}

/**
 * 专辑封面卡：backdrop 玻璃外框 + 内嵌封面（无封面时回退音符占位）。
 * 与按钮同一套 vibrancy + blur + lens 配方；不可用时回退半透明色块 + 高光描边。
 */
@Composable
private fun GlassCoverCard(
    coverData: Any?,
    backdrop: com.kyant.backdrop.Backdrop?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glassActive = isPlayerGlassActive(backdrop)
    val isInLightTheme = !NiExtraColors.current.isDark
    val shape = RoundedCornerShape(GlassCoverCorner)
    val innerCorner = GlassCoverCorner - 6.dp
    val surfaceTint = if (isInLightTheme) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .then(
                if (glassActive) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop!!,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(GlassCardDefaults.BlurRadius.dp.toPx())
                            lens(
                                GlassCardDefaults.LensRadius.dp.toPx(),
                                GlassCardDefaults.LensRadius.dp.toPx(),
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f),
                            )
                        },
                        onDrawSurface = { drawRect(surfaceTint) },
                    )
                } else {
                    Modifier
                        .shadow(18.dp, shape)
                        .background(surfaceTint, shape)
                        .border(1.dp, glassEdgeBrush(), shape)
                },
            )
            .padding(8.dp),
    ) {
        if (coverData != null) {
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
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(innerCorner)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(innerCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

/**
 * 玻璃样式控制区：进度条 + 一行式玻璃播放控件，**无卡片容器**——
 * 控件直接排在页面上，玻璃质感只属于按钮本身。
 */
@Composable
private fun GlassControlsSection(
    backdrop: com.kyant.backdrop.Backdrop?,
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
    modeIcon: ImageVector,
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

        GlassPlaybackControls(
            backdrop = backdrop,
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
            compact = compact,
        )
    }
}

/**
 * 竖屏玻璃布局：玻璃顶栏（返回 / 歌名 / 外观 / 更多）+ 居中封面卡（点击切歌词）+ 玻璃控件区。
 * 参数与 [PortraitLayout] 一致，额外接收「外观切换」入口。
 */
@Composable
internal fun GlassPortraitLayout(
    glassBackdrop: com.kyant.backdrop.Backdrop?,
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
    modeIcon: ImageVector,
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
    sleepTimerText: String = "",
    onSleepTimer: () -> Unit = {},
    showExternalActions: Boolean = false,
    onOpenWith: () -> Unit = {},
    onShare: () -> Unit = {},
    appearanceIcon: ImageVector,
    appearanceLabel: String,
    onCycleAppearance: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassCircleButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.player_back),
                backdrop = glassBackdrop,
            )
            Text(
                text = if (title.isNotEmpty()) title else stringResource(R.string.player_unknown_song),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface.copy(alpha = 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GlassCircleButton(
                onClick = onCycleAppearance,
                icon = appearanceIcon,
                contentDescription = appearanceLabel,
                size = 36.dp,
                iconSize = 18.dp,
                backdrop = glassBackdrop,
            )
            TopBarActions(
                onDownload = onDownload,
                onEqualizer = onEqualizer,
                speedOptions = speedOptions,
                currentSpeedIndex = currentSpeedIndex,
                onSpeedSelect = onSpeedSelect,
                showDownload = showDownload,
                sleepTimerText = sleepTimerText,
                onSleepTimer = onSleepTimer,
                showExternalActions = showExternalActions,
                onOpenWith = onOpenWith,
                onShare = onShare,
                glassButtons = true,
                backdrop = glassBackdrop,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                showLyrics -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
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
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCoverCard(
                        coverData = coverPath,
                        backdrop = glassBackdrop,
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f),
                    )
                }
            }
        }

        if (hasActiveContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (playlist.isNotEmpty() && currentIndex >= 0) {
                    Text(
                        text = "${currentIndex + 1} / ${playlist.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GlassControlsSection(
                    backdrop = glassBackdrop,
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
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * 横屏玻璃布局：左侧封面卡，右侧顶栏 + 歌词 + 玻璃控件区。
 * 复用既有横屏的沉浸式自动隐藏（3s 无操作收起顶栏与底部控件，仅留歌词）。
 */
@Composable
internal fun GlassLandscapeLayout(
    glassBackdrop: com.kyant.backdrop.Backdrop?,
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
    modeIcon: ImageVector,
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
    sleepTimerText: String = "",
    onSleepTimer: () -> Unit = {},
    showExternalActions: Boolean = false,
    onOpenWith: () -> Unit = {},
    onShare: () -> Unit = {},
    appearanceIcon: ImageVector,
    appearanceLabel: String,
    onCycleAppearance: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 800

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val hasLyrics = lrcLines.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }

    fun onBackgroundTap() {
        controlsVisible = !controlsVisible
        if (controlsVisible) interactionTick++
    }

    LaunchedEffect(interactionTick, hasLyrics, menuOpen) {
        if (!hasLyrics || menuOpen) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(AUTO_HIDE_DELAY_MS)
        controlsVisible = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onBackgroundTap() } },
    ) {
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
                        text = stringResource(R.string.player_no_source),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurface.copy(alpha = 0.6f),
                    )
                    playbackError != null -> PlaybackErrorState(
                        errorMessage = playbackError,
                        onRetry = onRetry,
                    )
                    else -> GlassCoverCard(
                        coverData = coverPath,
                        backdrop = glassBackdrop,
                        modifier = Modifier
                            .fillMaxHeight(0.86f)
                            .aspectRatio(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            ) {
                AnimatedVisibility(visible = controlsVisible) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GlassCircleButton(
                                onClick = onBack,
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.player_back),
                                backdrop = glassBackdrop,
                            )
                            Text(
                                text = if (title.isNotEmpty()) title else stringResource(R.string.player_unknown_song),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface.copy(alpha = 0.94f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            GlassCircleButton(
                                onClick = onCycleAppearance,
                                icon = appearanceIcon,
                                contentDescription = appearanceLabel,
                                size = 36.dp,
                                iconSize = 18.dp,
                                backdrop = glassBackdrop,
                            )
                            TopBarActions(
                                onDownload = onDownload,
                                onEqualizer = onEqualizer,
                                speedOptions = speedOptions,
                                currentSpeedIndex = currentSpeedIndex,
                                onSpeedSelect = onSpeedSelect,
                                onMenuOpenChange = { menuOpen = it },
                                showDownload = showDownload,
                                sleepTimerText = sleepTimerText,
                                onSleepTimer = onSleepTimer,
                                showExternalActions = showExternalActions,
                                onOpenWith = onOpenWith,
                                onShare = onShare,
                                glassButtons = true,
                                backdrop = glassBackdrop,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (hasLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 132.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsView(
                            lrcLines = lrcLines,
                            currentPositionMs = positionMs,
                            onSeek = onSeek,
                            maxVisibleLines = if (controlsVisible) {
                                if (isLargeScreen) 5 else 3
                            } else {
                                if (isLargeScreen) 8 else 5
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    AnimatedVisibility(visible = controlsVisible) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            GlassControlsSection(
                                backdrop = glassBackdrop,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassControlsSection(
                            backdrop = glassBackdrop,
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

        AnimatedVisibility(visible = !controlsVisible) {
            ThinProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
