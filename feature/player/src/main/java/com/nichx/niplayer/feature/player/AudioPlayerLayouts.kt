package com.nichx.niplayer.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import com.nichx.niplayer.datastore.AudioPlayerStyle
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlinx.coroutines.delay


/** 横屏沉浸模式：无操作自动隐藏控件的延时（ms）。 */
internal const val AUTO_HIDE_DELAY_MS = 3000L

/** Apple 歌词页：无操作自动收起底部控件的延时（ms）。 */
private const val APPLE_LYRICS_CONTROLS_IDLE_MS = 4000L

/** Apple 播放器内容的左右边距（参照 BitChord 的 PLAYER_GUTTER = 30dp）。 */
private val APPLE_PLAYER_GUTTER = 30.dp

/** Apple 封面页：歌名行与封面底部之间的间距。 */
private val APPLE_CREDITS_TOP_GAP = 16.dp

/** Apple 封面页：封面在「屏宽」之外再多占的高度（状态栏 + 上下留白）。 */
private val APPLE_COVER_EXTRA = 48.dp

/** Apple 封面页：封面之下必须留给歌名行、进度与传输键的高度。 */
private val APPLE_COVER_RESERVED = 300.dp

/**
 * Apple 封面页：封面高度占屏高的上限。
 *
 * 封面主体是屏宽见方的全宽贴顶图，短屏上再高就会把下面的控件挤没。
 */
private const val APPLE_COVER_HEIGHT_FRACTION = 0.60f

/**
 * Apple 封面页：封面块与底部控件块之间，留给空白的权重。
 *
 * 参考里下方的控件块很高（歌词条 + 进度 + 传输 + 音量 + 操作行），多余空间被
 * 它自己吃掉；我们只有进度和传输键，把空余全丢给封面与控件之间就会留下一个大洞。
 * 所以按比例分给「中间」和「底部」两段，两边都留一点呼吸，谁也不像被掏空。
 */
private const val APPLE_TOP_SLACK_WEIGHT = 0.45f
private const val APPLE_BOTTOM_SLACK_WEIGHT = 0.55f

/** 封面 → 歌词：封面整组上移的距离（dp），与歌词一起构成上下错位的推入感。 */
private val APPLE_ARTWORK_EXIT_DISTANCE = 300.dp

/** 封面 → 歌词：歌词自下方进入的起始距离（dp）。 */
private val APPLE_LYRICS_ENTER_DISTANCE = 400.dp

/**
 * 有封面时，动态流光背景作为**叠加层**的不透明度。
 *
 * 无封面时流光整屏兜底（1f）；有封面时压在封面色彩网格之上、封面横幅之下，
 * 取一个偏高的值让「流动」看得见，又不至于把封面自身的底色冲淡。
 */
private const val APPLE_FLOW_OVERLAY_ALPHA = 0.85f

/** 歌词层在「切出歌词页」后保留的时长（ms）：覆盖退场动画后再卸载，避免退场途中断帧。 */
private const val APPLE_LYRICS_LAYER_EXIT_MS = 420L

@Composable
internal fun BackgroundLayer(coverData: Any?) {
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

/** 简约封面主题：封面卡圆角。 */
private val CoverCorner = 20.dp

/**
 * 背景光采样边长（px）：以极小分辨率采样封面，再拉伸铺满，天然形成柔和背景光。
 *
 * 这样**不需要** `Modifier.blur`（整屏 RenderEffect）：整屏逐帧重建离屏层是硬约束禁止项。
 */
private const val CoverBackdropSampleSize = 64

/**
 * 简约封面主题的全屏背景：主题底色 + 封面高斯模糊铺底 + 主题色 scrim。
 *
 * 与黑胶主题 [BackgroundLayer]（弱化封面 + 底部渐变）不同，这里把封面放大高斯模糊，
 * 形成以封面主色为基调的环境光背景。
 */
@Composable
internal fun CoverBlurBackground(coverData: Any?) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    // 浅色模式 scrim 减半：保住背景色彩饱和度
    val scrimStrength = if (NiExtraColors.current.isDark) 1f else 0.55f
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(background))
        if (coverData != null) {
            val request = remember(coverData) {
                when (coverData) {
                    is String -> ImageRequest.Builder(context)
                        .data(coverData)
                        .size(CoverBackdropSampleSize, CoverBackdropSampleSize)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    is ImageRequest -> coverData.newBuilder()
                        .size(CoverBackdropSampleSize, CoverBackdropSampleSize)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    else -> coverData
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // 低分辨率图被拉伸铺满即得背景光：不再挂整屏 blur / 缩放 / alpha 图层
                modifier = Modifier.fillMaxSize(),
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
 * 简约封面主题的封面卡：方形圆角封面 + 轻投影，**不含任何玻璃质感**。
 * 无封面时回退为音符占位。
 */
@Composable
internal fun CoverCard(
    coverData: Any?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(CoverCorner)
    Box(
        modifier = modifier
            .shadow(18.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
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

@Composable
internal fun PortraitLayout(
    hasActiveContent: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    showLyrics: Boolean,
    lrcLines: List<LrcLine>,
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    title: String,
    artist: String,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    coverPath: Any?,
    style: AudioPlayerStyle = AudioPlayerStyle.VINYL,
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
    sleepTimerText: String = "",
    onSleepTimer: () -> Unit = {},
    showExternalActions: Boolean = false,
    onOpenWith: () -> Unit = {},
    onShare: () -> Unit = {},
    onStyleSelect: (AudioPlayerStyle) -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isAppleStyle = style == AudioPlayerStyle.APPLE_MUSIC
    val titleColor = if (isAppleStyle) Color.White else onSurface
    val secondaryColor =
        if (isAppleStyle) Color.White.copy(alpha = 0.6f) else onSurface.copy(alpha = 0.6f)

    // Apple 歌词页：底部控件（进度 + 传输键）一段时间无操作后自动收起，整页交给歌词。
    // controlsActivityGeneration 让「滚动等交互」也能把计时器重新计时（否则控件已可见时不会再续期）。
    var lyricsControlsVisible by remember(showLyrics) { mutableStateOf(true) }
    var controlsActivityGeneration by remember(showLyrics) { mutableIntStateOf(0) }
    LaunchedEffect(isAppleStyle, showLyrics, lyricsControlsVisible, controlsActivityGeneration) {
        if (isAppleStyle && showLyrics && lyricsControlsVisible) {
            delay(APPLE_LYRICS_CONTROLS_IDLE_MS)
            lyricsControlsVisible = false
        }
    }

    // 歌词层较重（整条 LyricsView + 逐行模糊图层 + 文本测量）：
    // 封面页（例如「最近播放」直接进播放器）**不为一个不可见的歌词层付出组合/layout 代价**，
    // 只在歌词页、以及刚切出歌词页的退场动画期间保留。
    var keepLyricsLayer by remember { mutableStateOf(showLyrics) }
    LaunchedEffect(showLyrics) {
        if (showLyrics) {
            keepLyricsLayer = true
        } else {
            delay(APPLE_LYRICS_LAYER_EXIT_MS)
            keepLyricsLayer = false
        }
    }

    // Apple：封面文件路径（Apple 风格的封面是本地抽帧文件，形如 String 路径）
    val coverFile = coverPath as? String
    // Apple 封面页：封面自身的颜色网格 + 全宽贴顶的封面（参照 BitChord）。
    // 网格由封面从小图解码后算一次并缓存，不涉及整屏 RenderEffect。
    val appleMesh = if (isAppleStyle) rememberAppleArtworkMesh(coverFile) else null
    // 动态流光背景用的 9 色取色（无封面时是兜底配色）
    val applePalette = if (isAppleStyle) {
        rememberAppleArtworkPalette(coverFile)
    } else {
        AppleArtworkPalette.Fallback
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // 封面从屏幕 y=0 起画（含状态栏背后）。参考里的封面块是「方形封面 + 状态栏 +
    // 上下留白」，所以这里也取屏宽再加回状态栏与留白，屏高不够时再收敛。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val coverHeight = minOf(
        maxWidth + statusBarTop + APPLE_COVER_EXTRA,
        maxHeight * APPLE_COVER_HEIGHT_FRACTION,
        // 给歌名行和底部控件留出底线，短屏上封面不能把下面的内容顶出去
        (maxHeight - APPLE_COVER_RESERVED).coerceAtLeast(0.dp),
    )
    // Apple：有可显示的封面/歌词内容（无内容或播放失败时另走提示分支）
    val appleActive = isAppleStyle && hasActiveContent && playbackError == null

    // 封面页 ↔ 歌词页 切换动效优化：
    // - 封面整组上移 −300dp 并淡出；
    // - 歌词自下方 +400dp 上移并淡入；
    // - 同一时间线，走 bounce≈0.3（ζ=0.7）的弹性，切换带一点回弹。
    val pageTransition = updateTransition(targetState = showLyrics, label = "applePage")
    val artworkAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleArtworkAlpha",
    ) { if (it) 0f else 1f }
    val artworkOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleArtworkOffset",
    ) { if (it) -APPLE_ARTWORK_EXIT_DISTANCE else 0.dp }
    val lyricsAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleLyricsAlpha",
    ) { if (it) 1f else 0f }
    val lyricsOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleLyricsOffset",
    ) { if (it) 0.dp else APPLE_LYRICS_ENTER_DISTANCE }

    // 封面组是否还「可交互」：用 derivedStateOf 只在跨过阈值时变一次（避免逐帧重组整个布局）。
    // 封面淡出后其按钮必须立刻失效——否则那层透明按钮会盖在歌词上、把一大片点击吃掉。
    val artworkInteractive by remember {
        derivedStateOf { artworkAlpha > 0.01f }
    }

    if (isAppleStyle) {
        if (coverFile != null) {
            AppleArtworkMeshBackdrop(
                mesh = appleMesh,
                // 网格接缝随封面页让位：进歌词页时收回到 0，整屏变成连续渐变
                seam = if (showLyrics) 0.dp else coverHeight,
            )
        }
        // 动态流光背景：无封面时作为整屏兜底；有封面时半透明叠加在色彩网格之上
        // （封面横幅随后盖在其上，所以封面本身仍保持清晰）。
        AppleFlowingLightBackdrop(
            palette = applePalette,
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (coverFile == null) 1f else APPLE_FLOW_OVERLAY_ALPHA },
        )
        if (appleActive) {
            AppleMusicCoverBanner(
                coverData = coverPath,
                height = coverHeight,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        alpha = artworkAlpha
                        translationY = artworkOffset.toPx()
                    },
            )
        }
    }

    // 歌词页：铺满整屏的独立层（自下方上滑 + 淡入）。
    // 放在 Column **之前**，这样 Column 里的底部控件叠在它之上、可正常点击；
    // 未进入歌词页时它已被推到屏外且 alpha=0，不会拦截触摸。
    if (appleActive && keepLyricsLayer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = lyricsAlpha
                    translationY = lyricsOffset.toPx()
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { lyricsControlsVisible = !lyricsControlsVisible },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部信息条：缩略图 + 歌名/艺术家 + 「···」
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppleMusicThumbnail(
                            coverData = coverPath,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = onToggleLyrics),
                        )
                        AppleTrackTitle(
                            title = title,
                            artist = artist,
                            modifier = Modifier.weight(1f),
                            compact = true,
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
                            appleStyle = true,
                            audioStyle = style,
                            onStyleSelect = onStyleSelect,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsView(
                            lrcLines = lrcLines,
                            currentPositionMs = positionMs,
                            onSeek = onSeek,
                            maxVisibleLines = Int.MAX_VALUE,
                            modifier = Modifier.fillMaxSize(),
                            appleStyle = true,
                            // 任何滚动交互都把控件呼出并把自动收起计时器重新计时
                            onUserScroll = {
                                lyricsControlsVisible = true
                                controlsActivityGeneration += 1
                            },
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (!isAppleStyle) {
        TopBar(
            // Apple Music 风格顶栏不重复显示歌名（歌名在封面下方单独展示），
            // 「更多」也移到歌名右侧的圆形按钮上
            title = if (isAppleStyle) "" else title,
            onBack = onBack,
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
            appleStyle = isAppleStyle,
            showMore = !isAppleStyle,
            audioStyle = style,
            onStyleSelect = onStyleSelect,
        )
        }

        if (isAppleStyle && appleActive) {
            // ---- Apple 封面页：封面点击区 + 歌名行（整组随切换上移 + 淡出）----
            // 封面本身画在背景层上（全宽贴顶），这里只放一个与封面等高的透明点击区，
            // 点它进歌词页；歌名行紧贴在封面下方。
            //
            // 顺序很重要：这一块是**固定高度**、不带权重，权重只给下面两段 Spacer，
            // Column 会先把固定高度的孩子量完再分剩下的——否则底部的进度与传输键
            // 会被挤成 0 高度（就是「控件没了」）。
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = artworkAlpha
                    translationY = artworkOffset.toPx()
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((coverHeight - statusBarTop).coerceAtLeast(0.dp))
                        // 封面淡出后撤掉点击：避免这层透明区域挡住歌词交互
                        .then(
                            if (artworkInteractive) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onToggleLyrics() }
                            } else {
                                Modifier
                            },
                        ),
                )
                Spacer(Modifier.height(APPLE_CREDITS_TOP_GAP))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = APPLE_PLAYER_GUTTER),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppleTrackTitle(
                        title = title,
                        artist = artist,
                        modifier = Modifier.weight(1f),
                    )
                    // 同理：淡出后不再渲染动作按钮（它们是不可见的点击目标，会吃掉歌词页的点击）
                    if (artworkInteractive) {
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
                            appleStyle = true,
                            audioStyle = style,
                            onStyleSelect = onStyleSelect,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(APPLE_TOP_SLACK_WEIGHT))
        } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = if (isAppleStyle) Alignment.TopCenter else Alignment.Center,
        ) {
            if (!hasActiveContent) {
                Text(
                    text = stringResource(R.string.player_no_source),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                )
            } else if (playbackError != null) {
                PlaybackErrorState(
                    errorMessage = playbackError,
                    onRetry = onRetry,
                )
            } else if (showLyrics) {
                // 歌词视图：点击**空白处**切换底部控件显隐，点击歌词文本才是跳转进度。
                //
                // 这一层只负责空白处，所以用普通（冒泡阶段）的 clickable 就够了——歌词
                // 行的点击区域收在文字上，文字把事件消费掉，空白处自然落回这里。
                // 其它风格保持原样：点歌词区域回到封面。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (isAppleStyle) {
                                lyricsControlsVisible = !lyricsControlsVisible
                            } else {
                                onToggleLyrics()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Apple Music：歌词页顶部一条信息条（缩略图 + 歌名/艺术家 + 「···」）
                        if (isAppleStyle) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppleMusicThumbnail(
                                    coverData = coverPath,
                                    modifier = Modifier
                                        .size(48.dp)
                                        // 点击小封面回到封面页
                                        .clickable(onClick = onToggleLyrics),
                                )
                                AppleTrackTitle(
                                    title = title,
                                    artist = artist,
                                    modifier = Modifier.weight(1f),
                                    compact = true,
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
                                    appleStyle = true,
                                    audioStyle = style,
                                    onStyleSelect = onStyleSelect,
                                )
                            }
                        }
                        // 歌词区域只占中间 75% 高度并居中，上下各留约 12.5% 空白，
                        // 便于点击空白处切换回唱片；行数限制为 7 行，避免显示过多。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxHeight(if (isAppleStyle) 1f else 0.75f),
                                contentAlignment = Alignment.Center,
                            ) {
                                LyricsView(
                                    lrcLines = lrcLines,
                                    currentPositionMs = positionMs,
                                    onSeek = onSeek,
                                    // Apple：歌词铺满整页；其它风格限制 7 行
                                    maxVisibleLines = if (isAppleStyle) Int.MAX_VALUE else 7,
                                    modifier = Modifier.fillMaxSize(),
                                    appleStyle = isAppleStyle,
                                )
                            }
                        }
                    }
                }
            } else {
                // 封面/唱片视图（Apple 的封面页不走这里，见上面的 Apple 分支）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
                    when (style) {
                        AudioPlayerStyle.GLASS -> CoverCard(
                            coverData = coverPath,
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .aspectRatio(1f),
                        )
                        else -> VinylRecordPlayer(
                            coverData = coverPath,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
        }

        if (hasActiveContent) {
            // Apple 歌词页：底部控件自动收起，整页交给歌词
            AnimatedVisibility(visible = !(isAppleStyle && showLyrics) || lyricsControlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isAppleStyle) APPLE_PLAYER_GUTTER else 24.dp),
                horizontalAlignment =
                if (isAppleStyle) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                when {
                    // Apple：歌名行在封面下方（封面页）或顶部信息条里（歌词页），
                    // 底部这一块都不再重复，直接留白给进度与传输键。
                    isAppleStyle -> Unit
                    else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(2f),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (!isAppleStyle && playlist.isNotEmpty() && currentIndex >= 0) {
                    val subtitleText = "${currentIndex + 1} / ${playlist.size}"
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isAppleStyle) {
                    AppleProgressSection(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = onSeek,
                    )
                } else {
                    ProgressSection(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = onSeek,
                    )
                }

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
                    appleStyle = isAppleStyle,
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
            }
        }

        // Apple：底部再留一段空白，与控件上方那段一起把空余分掉
        // （见 APPLE_BOTTOM_SLACK_WEIGHT）。条件只取决于「有无可播放内容」，与是否歌词页无关，
        // 这样封面/歌词切换时控件位置稳定、不会跳。
        if (isAppleStyle && appleActive) {
            Spacer(Modifier.weight(APPLE_BOTTOM_SLACK_WEIGHT))
        }
    }
    // Apple 封面页：悬浮返回；歌词页不显示返回按钮（点小封面回到封面页 / 系统返回手势）
    if (isAppleStyle && !showLyrics) {
        AppleMusicBackButton(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )
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
internal fun LandscapeLayout(
    hasActiveContent: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    lrcLines: List<LrcLine>,
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    title: String,
    artist: String,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    coverPath: Any?,
    style: AudioPlayerStyle = AudioPlayerStyle.VINYL,
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
    sleepTimerText: String = "",
    onSleepTimer: () -> Unit = {},
    showExternalActions: Boolean = false,
    onOpenWith: () -> Unit = {},
    onShare: () -> Unit = {},
    onStyleSelect: (AudioPlayerStyle) -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isAppleStyle = style == AudioPlayerStyle.APPLE_MUSIC
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
    Box(modifier = Modifier.fillMaxSize()) {
    if (isAppleStyle) {
        // 横屏没有贴顶封面，整屏都是封面自身的颜色网格
        AppleArtworkMeshBackdrop(
            mesh = rememberAppleArtworkMesh(coverPath as? String),
            seam = 0.dp,
        )
    }
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
                    color = if (isAppleStyle) Color.White.copy(alpha = 0.6f)
                    else onSurface.copy(alpha = 0.6f),
                )
                playbackError != null -> PlaybackErrorState(
                    errorMessage = playbackError,
                    onRetry = onRetry,
                )
                style == AudioPlayerStyle.APPLE_MUSIC -> AppleMusicArtwork(
                    coverData = coverPath,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxHeight(0.86f)
                        .aspectRatio(1f),
                )
                style == AudioPlayerStyle.GLASS -> CoverCard(
                    coverData = coverPath,
                    modifier = Modifier
                        .fillMaxHeight(0.86f)
                        .aspectRatio(1f),
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
                                    .background(
                                        if (isAppleStyle) Color.Transparent
                                        else onSurface.copy(alpha = 0.08f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.player_back),
                                    tint = if (isAppleStyle) Color.White.copy(alpha = 0.9f)
                                    else onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                        if (isAppleStyle) {
                            AppleTrackTitle(
                                title = title.ifEmpty { stringResource(R.string.player_unknown_song) },
                                artist = artist,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        } else {
                            Text(
                                text = if (title.isNotEmpty()) title else stringResource(R.string.player_unknown_song),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
                            appleStyle = isAppleStyle,
                            audioStyle = style,
                            onStyleSelect = onStyleSelect,
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
                        appleStyle = isAppleStyle,
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
                            appleStyle = isAppleStyle,
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
                        appleStyle = isAppleStyle,
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
                accentColor = if (isAppleStyle) Color.White else MaterialTheme.colorScheme.primary,
                trackColor = if (isAppleStyle) Color.White.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
    }
}
