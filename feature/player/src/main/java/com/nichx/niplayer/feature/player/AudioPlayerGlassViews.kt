package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.datastore.AudioPlayerStyle

/**
 * 「简约封面」主题（[AudioPlayerStyle.GLASS]）的竖屏视觉件。
 *
 * 与另外两套主题的关系：黑胶与 Apple Music 各有自己的封面/背景语言，这里只做「一张大封面
 * + 三行信息栈 + 一条细进度 + 一排传输键」的极简版式，颜色全部来自当前封面
 * （见 [rememberCoverAccent]），所以同一套版式在每首歌下都是不同的配色。
 */

/** 封面与顶栏之间的留白：整组贴顶排布，余量全部留给歌词行与进度条之间。 */
internal val GlassCoverTopGap = 6.dp

/** 封面与信息栈之间的留白。 */
internal val GlassCreditsGap = 38.dp

/** 信息栈里「歌名 + 艺术家」两行的高度。 */
private val GlassCreditsHeadHeight = 30.dp + 6.dp + 19.dp

/** 信息栈里「当前歌词」一行占的高度（含它上方的留白）。与 [GlassLyricFontSize] 的行高同步。 */
private val GlassCreditsLyricHeight = 47.dp + 27.dp

/** 封面页当前歌词的字号 / 行高。 */
private val GlassLyricFontSize = 19.sp
private val GlassLyricLineHeight = 27.sp

/**
 * 信息栈占用的高度，供封面算边长用 —— 封面必须先知道下面要留多少，否则短屏上会把歌词挤出屏幕。
 *
 * **恒按「有歌词」预留**：歌词是异步到的（要先把 .lrc 取回来），按「当前有没有歌词」算，
 * 会让封面在歌词到达的那一帧突然缩小一档。典型屏幕（可用高 ≈ 610dp）上这个预留根本
 * 用不满，恒预留不花任何代价；只在很短的屏上才生效，而那正是需要保守估计的地方。
 */
internal val glassCreditsHeight: Dp = GlassCreditsHeadHeight + GlassCreditsLyricHeight

/**
 * 简约封面的顶栏：返回 · 居中「正在播放 + 当前歌名」两行 · 菜单。
 *
 * 与黑胶主题 [TopBar] 的区别只在标题区：歌名从左侧搬到中间，并补一行固定的
 * 「正在播放」；更多菜单沿用同一套 [TopBarActions]（只是图标换成三横线）。
 */
@Composable
internal fun GlassTopBar(
    title: String,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onEqualizer: () -> Unit,
    speedOptions: List<Float>,
    currentSpeedIndex: Int,
    onSpeedSelect: (Int) -> Unit,
    showDownload: Boolean,
    sleepTimerText: String,
    onSleepTimer: () -> Unit,
    showExternalActions: Boolean,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    audioStyle: AudioPlayerStyle,
    onStyleSelect: (AudioPlayerStyle) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.player_back),
                tint = onSurface.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.player_now_playing),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = onSurface.copy(alpha = 0.92f),
                maxLines = 1,
            )
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

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
            audioStyle = audioStyle,
            onStyleSelect = onStyleSelect,
            menuIcon = true,
        )
    }
}

/**
 * 封面下方的信息栈：歌名 / 艺术家 / 当前歌词。
 *
 * 三行都居中，且**当前歌词行是「已唱部分强调色、未唱部分正文色」的分色文本** ——
 * 这与参考图一致：一眼能看出唱到哪了，而不需要进歌词页。
 *
 * 歌词行单独拆成 [GlassCurrentLyricLine]：只有它读 [positionMs]，于是每秒的重组
 * 只落在这一行上，不会把整页（含封面）带着重组。
 */
@Composable
internal fun GlassTrackCredits(
    title: String,
    artist: String,
    lrcLines: List<LrcLine>,
    positionMs: State<Long>,
    isPlaying: Boolean,
    perChar: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title.ifEmpty { stringResource(R.string.player_unknown_song) },
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
            color = onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        // 艺术家为空、或与歌名完全相同（无元数据的文件会把文件名同时当作两者）时不留空行 ——
        // 上下两行一模一样会读成「界面坏了」。
        if (artist.isNotEmpty() && artist != title) {
            Text(
                text = artist,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (lrcLines.isNotEmpty()) {
            GlassCurrentLyricLine(
                lrcLines = lrcLines,
                positionMs = positionMs,
                isPlaying = isPlaying,
                perChar = perChar,
                accent = accent,
                modifier = Modifier.padding(top = 47.dp),
            )
        }
    }
}

/**
 * 当前歌词行：已唱的字用强调色、未唱的字用正文色。
 *
 * 两层同款文本叠在一起、上层按进度裁切并**逐字抬升**（见 [perCharSung]），落点由
 * [rememberLiveSungFraction] 给出 —— 播放位置 1Hz 上报的台阶被帧时钟抹平，
 * 逐字歌词才不会落后一整秒。
 */
@Composable
private fun GlassCurrentLyricLine(
    lrcLines: List<LrcLine>,
    positionMs: State<Long>,
    isPlaying: Boolean,
    perChar: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // 实时播放位置：换行与逐字都按它算 —— 直接用 1Hz 的上报值会让「换行」最多晚一整秒
    val livePosition = rememberLivePosition(positionMs = positionMs, isPlaying = isPlaying)
    val lineIndex by remember(lrcLines, livePosition) {
        derivedStateOf {
            lrcLines.indexOfLast { it.timeMs <= livePosition.value }.coerceAtLeast(0)
        }
    }
    val line = lrcLines.getOrNull(lineIndex) ?: return
    val fraction = rememberSungFraction(
        text = line.text,
        wordTimes = line.wordTimes,
        lineStartMs = line.timeMs,
        nextLineStartMs = lrcLines.getOrNull(lineIndex + 1)?.timeMs,
        livePosition = livePosition,
        enabled = perChar,
    )
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 光带按「行高的 Dp」算尺寸：行高是 sp，需要按当前字体缩放换算，否则大字号下光带会偏小
    val density = LocalDensity.current
    val lineHeightDp = with(density) { GlassLyricLineHeight.toDp() }
    val reduceMotion = LocalNiReduceMotion.current
    // 三段几何（已唱完 / 正在唱 / 未唱）只算一次，两层共用
    // 抬升是 Apple Music 主题的效果，这里（简约封面）只做逐字铺色
    val sungSplit = remember(layoutResult, fraction) {
        derivedStateOf { layoutResult?.let { buildSungSplit(it, fraction.value, lift = false) } }
    }
    // 逐字抬升量：字号的 10%，夹在 1.5~6dp；「减少动态效果」时不做
    val lyricRisePx = if (reduceMotion) {
        0f
    } else {
        with(density) {
            (GlassLyricFontSize.value * LYRIC_LIFT_FONT_RATIO)
                .coerceIn(LYRIC_LIFT_MIN_DP, LYRIC_LIFT_MAX_DP)
                .dp
                .toPx()
        }
    }

    val style = MaterialTheme.typography.titleLarge.copy(
        fontSize = GlassLyricFontSize,
        lineHeight = GlassLyricLineHeight,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )
    val unsungColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    // 两层都必须 wrapContent（不能 fillMaxWidth）：裁切起点是「文字左边缘」，
    // 文本框一旦被撑满整宽，居中的歌词会从屏幕左边开始擦、先亮一大片空白。
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // 与歌词页同一套「流动光带」：封面页的这一行也是「当前句」，不该只在歌词页有光
        GlassLyricLightBand(
            accent = accent,
            isPlaying = isPlaying,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = lineHeightDp * (1f - GLASS_BAND_HEIGHT_RATIO) / 2f)
                .fillMaxWidth()
                .height(lineHeightDp * GLASS_BAND_HEIGHT_RATIO),
        )
        Text(
            text = line.text,
            style = style,
            color = unsungColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            onTextLayout = { layoutResult = it },
            // 底层只画未唱区：已唱的字被抬起后原位会空出来，照常画整句会留下影子
            modifier = if (perChar) {
                Modifier.perCharUnsung { sungSplit.value }
            } else {
                Modifier
            },
        )
        if (perChar) {
            Text(
                text = line.text,
                style = style,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.perCharSung(
                    splitProvider = { sungSplit.value },
                    risePx = lyricRisePx,
                ),
            )
        }
    }
}

/**
 * 简约封面的进度区：发丝胶囊进度条（无滑块拇指，按住变粗）+ 下方左右时间。
 *
 * Material 的 Slider 固定带滑块与更高的轨道，画不出参考图这种「一条线」的形状，
 * 故与 Apple 主题一样直接绘制；触控区比可见条高得多，点按与拖动共用一个手势循环。
 */
@Composable
internal fun GlassProgressSection(
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    val currentPositionMs = positionMs.value
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) {
        dragValue
    } else {
        (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    }
    val displayMs = if (dragging) (dragValue * duration).toLong() else currentPositionMs
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val timeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Column(modifier = modifier.fillMaxWidth()) {
        GlassThinSlider(
            value = fraction,
            accent = accent,
            trackColor = trackColor,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                if (dragging) {
                    onSeek((dragValue * duration).toLong())
                    dragging = false
                }
            },
        )
        // 进度条的触控区比可见条高得多，时间行上提贴回条下
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationShort(displayMs),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = timeColor,
            )
            Text(
                text = formatDurationShort(durationMs),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = timeColor,
            )
        }
    }
}

/** 发丝胶囊进度条：3dp 静置、按住变粗到 5dp。 */
@Composable
private fun GlassThinSlider(
    value: Float,
    accent: Color,
    trackColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idleHeight = 3.dp
    val activeHeight = 5.dp
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassSliderHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 可见条只有 3dp，触控区放大到条高 + 24dp
            .height(activeHeight + 24.dp)
            // 点按与拖动共用一个手势循环：拆成两个检测器时，拖动检测器会先吃掉指针，
            // 而点按没有拖动量可报告，于是点按永远不生效。
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }
                    dragging = false
                    onValueChangeFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = trackColor, cornerRadius = radius)
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(
                    color = accent,
                    size = Size(
                        width = filled.coerceAtLeast(size.height).coerceAtMost(size.width),
                        height = size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** 简约封面的传输键尺寸：五个按钮居中成组（与参考图一致，不是两端贴边）。 */
private val GlassSideButton = 44.dp
private val GlassSideIcon = 26.dp
private val GlassMainButton = 74.dp
private val GlassMainIcon = 30.dp
private val GlassControlGap = 17.dp

/** 紧凑档（横屏）：高度吃紧，按钮与间距各收一档，与黑胶/Apple 的 compact 口径一致。 */
private val GlassSideButtonCompact = 38.dp
private val GlassSideIconCompact = 22.dp
private val GlassMainButtonCompact = 56.dp
private val GlassMainIconCompact = 24.dp
private val GlassControlGapCompact = 12.dp

/**
 * 简约封面的控件列：细进度条 + 传输键行。竖屏与横屏共用，只是 [compact] 不同档。
 *
 * 横屏原来直接复用黑胶的 [ControlColumn]（Material3 Slider 带圆点拇指 + 两端贴边的五键），
 * 于是同一个主题在横屏下又变回黑胶的样子 —— 这里换成同一套玻璃控件。
 */
@Composable
internal fun GlassControlColumn(
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modeIcon: ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    accent: Color,
    onAccent: Color,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassProgressSection(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
            accent = accent,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
        GlassPlaybackControls(
            isPlaying = isPlaying,
            hasPrev = hasPrev,
            hasNext = hasNext,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            modeIcon = modeIcon,
            modeLabel = modeLabel,
            onCyclePlayMode = onCyclePlayMode,
            onShowPlaylist = onShowPlaylist,
            accent = accent,
            onAccent = onAccent,
            compact = compact,
        )
    }
}

/**
 * 简约封面的播放控件行：播放模式 · 上一首 · 播放/暂停 · 下一首 · 播放列表。
 *
 * 五个按钮**居中成组**：外侧两个不贴边，整组宽 44×4 + 74 + 17×4 = 318dp，在 393dp 宽的
 * 屏上左右各留 37.5dp —— 这正是参考图的排布。播放键用封面强调色实心圆 + 投影，
 * 建立明确的主操作层次。
 */
@Composable
internal fun GlassPlaybackControls(
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modeIcon: ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    accent: Color,
    onAccent: Color,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val sideButton = if (compact) GlassSideButtonCompact else GlassSideButton
    val sideIcon = if (compact) GlassSideIconCompact else GlassSideIcon
    val mainButton = if (compact) GlassMainButtonCompact else GlassMainButton
    val mainIcon = if (compact) GlassMainIconCompact else GlassMainIcon
    val gap = if (compact) GlassControlGapCompact else GlassControlGap
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            onClick = onCyclePlayMode,
            size = sideButton,
            iconSize = sideIcon,
            icon = modeIcon,
            contentDescription = modeLabel,
            tint = onSurface.copy(alpha = 0.75f),
        )

        Spacer(Modifier.size(gap))

        GlassIconButton(
            onClick = onPrevious,
            enabled = hasPrev,
            size = sideButton,
            iconSize = sideIcon,
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.player_previous),
            tint = onSurface.copy(alpha = if (hasPrev) 0.85f else 0.2f),
        )

        Spacer(Modifier.size(gap))

        Box(
            modifier = Modifier
                .size(mainButton)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onTogglePlay,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.player_pause)
                } else {
                    stringResource(R.string.player_play)
                },
                tint = onAccent,
                modifier = Modifier.size(mainIcon),
            )
        }

        Spacer(Modifier.size(gap))

        GlassIconButton(
            onClick = onNext,
            enabled = hasNext,
            size = sideButton,
            iconSize = sideIcon,
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.player_next),
            tint = onSurface.copy(alpha = if (hasNext) 0.85f else 0.2f),
        )

        Spacer(Modifier.size(gap))

        GlassIconButton(
            onClick = onShowPlaylist,
            size = sideButton,
            iconSize = sideIcon,
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = stringResource(R.string.player_playlist),
            tint = onSurface.copy(alpha = 0.65f),
        )
    }
}

/**
 * 圆底图标按钮。
 *
 * 不用 `IconButton`：它会把点击目标撑到 48dp 下限，五个按钮就不再是参考图那种紧凑居中组
 * （整组会宽 60dp 多，外侧两个被推到贴边）。
 */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
