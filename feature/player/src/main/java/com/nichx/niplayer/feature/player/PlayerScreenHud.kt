package com.nichx.niplayer.feature.player

import android.content.res.Configuration
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.PlaylistItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow


internal val AbLoopColorA = Color(0xFFFFAB40)
internal val AbLoopColorB = Color(0xFFFF5252)

@Composable
internal fun PlayerProgressBar(
    positionFraction: Float,
    bufferedFraction: Float,
    durationMs: Long,
    abLoopA: Long?,
    abLoopB: Long?,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onDragFractionChange: (Float?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val trackHeight = 4.dp
    val thumbRadius = 8.dp
    val isDragging = remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val primary = MaterialTheme.colorScheme.primary
    val primaryDark = MaterialTheme.colorScheme.primary

    val displayFraction = if (isDragging.value) dragFraction else positionFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight + thumbRadius * 2)
            .clip(RoundedCornerShape(trackHeight / 2))
            // 无障碍（UX-3，2026-09-22）：本进度条是自绘 Canvas + pointerInput，
            // 原先没有任何语义信息 —— TalkBack 读不出「当前进度 / 总时长」，
            // 也无法用无障碍手势调节（视障用户完全无法 seek）。
            // 补三件事：进度范围、可读的当前值文本、可设置进度的动作。
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = positionFraction.coerceIn(0f, 1f),
                    range = 0f..1f,
                )
                if (durationMs > 0) {
                    stateDescription = "${formatDuration((positionFraction * durationMs).toLong())} / " +
                        formatDuration(durationMs)
                }
                setProgress { target ->
                    onSeek(target.coerceIn(0f, 1f))
                    onSeekFinished()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging.value = true
                    dragFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                    onDragFractionChange(dragFraction)
                    onSeek(dragFraction)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            isDragging.value = false
                            onDragFractionChange(null)
                            onSeekFinished()
                            break
                        }
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDragFractionChange(dragFraction)
                        onSeek(dragFraction)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .height(trackHeight),
        ) {
            val w = size.width
            val h = size.height
            val cornerRadius = h / 2

            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            )

            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset.Zero,
                    size = Size(w * bufferedFraction.coerceIn(0f, 1f), h),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }

            val display = displayFraction.coerceIn(0f, 1f)
            if (display > 0f) {
                val gradient = Brush.horizontalGradient(
                    colors = listOf(primaryDark, primary),
                    startX = 0f, endX = w * display,
                )
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset.Zero,
                    size = Size(w * display, h),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
            }

            val a = abLoopA
            val b = abLoopB
            if (a != null && b != null && b > a && durationMs > 0) {
                val aX = (a.toFloat() / durationMs).coerceIn(0f, 1f) * w
                val bX = (b.toFloat() / durationMs).coerceIn(0f, 1f) * w
                drawRoundRect(
                    color = AbLoopColorA.copy(alpha = 0.4f),
                    topLeft = Offset(aX, 0f),
                    size = Size((bX - aX).coerceAtLeast(2f), h),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                )
                drawCircle(
                    color = AbLoopColorA,
                    radius = 4.dp.toPx(),
                    center = Offset(aX, h / 2f),
                )
                drawCircle(
                    color = AbLoopColorB,
                    radius = 4.dp.toPx(),
                    center = Offset(bX, h / 2f),
                )
            }


            val thumbX = w * display
            drawCircle(
                color = Color.White,
                radius = thumbRadius.toPx() - 1.dp.toPx(),
                center = Offset(thumbX, h / 2f),
            )
            drawCircle(
                color = primary,
                radius = thumbRadius.toPx() - 2.dp.toPx(),
                center = Offset(thumbX, h / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = (thumbRadius - 3.dp).toPx(),
                center = Offset(thumbX, h / 2f),
            )
        }
    }
}

@Composable
internal fun LockedOverlay(onToggleLock: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.player_unlock),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** 单个 HUD 按钮的配置描述。 */
internal data class HudButtonConfig(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color = Color.White,
    val iconSize: Dp = 24.dp,
    val order: Int = 0,
    val side: HudButtonSide = HudButtonSide.LEFT,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Unit)? = null,
)

internal enum class HudButtonSide { LEFT, RIGHT }

/** 垂直排列的 HUD 按钮列（左列/右列），由配置列表驱动渲染。
 *
 * 高度自适应用于防止「把全部按钮放到一侧」时溢出 / 错位：
 * - 竖屏 ([portrait] = true)：按钮列在画面中下部区域垂直居中，区域下方预留底栏高度；
 * - 横屏 ([portrait] = false)：整屏垂直居中；
 * - 当某侧按钮太多而放不下时，先收缩按钮间距，再按单侧数量上限截断
 *   （横屏 3 个 / 竖屏 4 个；竖屏可用高度不足时还会进一步收缩），保证任何排布都不超出屏幕。
 */
@Composable
internal fun HudButtonColumn(
    configs: List<HudButtonConfig>,
    side: HudButtonSide,
    modifier: Modifier = Modifier,
    portrait: Boolean = false,
) {
    val sideConfigs = configs.filter { it.side == side }.sortedBy { it.order }
    val density = LocalDensity.current
    // 列占满整屏（fillMaxSize），用 side 把按钮固定在左/右边缘并垂直居中，
    // 保证约束高度可测（用于自适应间距/单边数量限制）。
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullH = with(density) { constraints.maxHeight.toDp() }
        // 底部为进度条与控制区，需预留空间避免按钮被遮挡/顶出屏幕
        val bottomReserve = if (portrait) 176.dp else 24.dp
        // 竖屏中部区域底部起点（按钮集中在中下部分，避开顶部挖孔与画面中心）
        val bandTop = if (portrait) fullH * 0.40f else 0.dp
        val avail = (fullH - bottomReserve - bandTop).coerceAtLeast(0.dp)

        val btn = 48.dp
        val minGap = 8.dp
        val idealGap = 12.dp
        val n = sideConfigs.size
        // 每侧数量硬上限：横屏 3 个、竖屏 4 个（仍会按可用高度进一步收缩以免溢出）
        val maxPerSide = if (portrait) 4 else 3
        // 间距自适应：优先 12dp，拥挤则收缩，下限 8dp
        val gap = when {
            n <= 1 -> 0.dp
            else -> maxOf(minGap, minOf((avail - btn * n) / (n - 1), idealGap))
        }
        // 单边数量限制：横屏 3 / 竖屏 4；仍放不下时再收缩到可容纳数
        val canFitAll = n <= 1 || (n <= maxPerSide && btn * n + minGap * (n - 1) <= avail)
        val shown = if (canFitAll) sideConfigs
        else sideConfigs.take(
            minOf(maxPerSide, ((avail + minGap).value / (btn + minGap).value).toInt().coerceAtLeast(1)),
        )

        val colHeight = btn * shown.size + gap * (shown.size - 1)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = bandTop, bottom = bottomReserve)
                .padding(horizontal = if (side == HudButtonSide.LEFT) 16.dp else 16.dp),
            contentAlignment = if (side == HudButtonSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .height(colHeight),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                shown.forEachIndexed { _, cfg ->
                    // 禁用的按钮：颜色弱化 + 忽略点击/长按
                    val effTint = if (cfg.enabled) cfg.tint else cfg.tint.copy(alpha = 0.35f)
                    // 禁用态（enabled=false）时用空操作：按钮可见但不响应点击
                    val noop: () -> Unit = {}
                    val effClick: () -> Unit = if (cfg.enabled) cfg.onClick else noop
                    if (cfg.onLongClick != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .playerHudGlass()
                                .combinedClickable(
                                    onClick = effClick,
                                    onLongClick = if (cfg.enabled) cfg.onLongClick else null,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = cfg.icon,
                                contentDescription = cfg.contentDescription,
                                tint = effTint,
                                modifier = Modifier.size(cfg.iconSize),
                            )
                        }
                    } else {
                        PlayerHudButton(onClick = effClick) {
                            Icon(
                                imageVector = cfg.icon,
                                contentDescription = cfg.contentDescription,
                                tint = effTint,
                                modifier = Modifier.size(cfg.iconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 控件 HUD 按钮材质 =====
// 按用户要求回退按钮材质（仅保留弹窗材质修改）：中部侧边按钮恢复为原始生硬的半透明黑色
// 圆底（alpha=0.35），不使用玻璃描边，保持与改动前一致的视觉。
internal val HudButtonBg = Color.Black.copy(alpha = 0.35f)

/** 统一 HUD 圆形按钮外框：圆角裁剪 + 半透明黑色圆底。 */
internal fun Modifier.playerHudGlass(): Modifier = this
    .clip(CircleShape)
    .background(HudButtonBg)

/**
 * 统一 HUD 圆形按钮（单次点击）。用于旋转/去黑边/截图等功能按钮。
 * 图标颜色、尺寸、内容由调用方通过 [content] 提供，保证所有 HUD 按钮视觉一致。
 */
@Composable
internal fun PlayerHudButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .playerHudGlass()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerControllerLayer(
    title: String,
    state: PlaybackState,
    // P0-1 结构改造（2026-09-22）：下面三个是每 500ms 变化的播放进度 / 缓冲 / 网速。
    // 原先以 Long 直接传入，本层每次重组都会收到新值 —— 即便其余参数全等也永远无法跳过，
    // 于是播放中整层（全部 HUD 按钮、菜单、OSD）每秒被重组 2 次。
    // 改为传 StateFlow，由内部叶子组件自行 collect，把高频重组收敛到那几个组件。
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    bufferedMsFlow: StateFlow<Long>,
    networkSpeedFlow: StateFlow<Long>,
    speedIndex: Int,
    abLoopA: Long?,
    abLoopB: Long?,
    speedLabel: String,
    scaleIndex: Int,
    playlistInfo: String,
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    sleepTimerText: String,
    previousMusicVolume: Int,
    onPreviousMusicVolumeChange: (Int) -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onToggleMoreMenu: () -> Unit,
    onToggleAudioTrackMenu: () -> Unit,
    onCycleScale: () -> Unit,
    onAddSubtitle: () -> Unit,
    onSearchSubtitle: () -> Unit,
    onToggleLock: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit,
    onMediaInfo: () -> Unit,
    onLongPressSpeed: () -> Unit,
    onShowAbLoopDialog: () -> Unit,
    onQuickToggleAbLoop: () -> Unit,
    blackBarCropActive: Boolean = false,
    onToggleBlackBarCrop: () -> Unit = {},
    onPlayAtIndex: (Int) -> Unit,
    onTogglePlaylistDialog: () -> Unit,
    pipEnabled: Boolean = false,
    onPictureInPicture: () -> Unit = {},
    onDownload: () -> Unit = {},
    /** 本地文件（已下载/缓存直链）来源时为 false，隐藏下载按钮。 */
    showDownload: Boolean = true,
    /** 已按用户自定义好的 HUD 按钮配置（含所在侧与序），用于渲染左右列。 */
    hudButtons: List<HudButtonConfig> = emptyList(),
    /** 双指缩放是否处于放大态：为 true 时在进度条正上方显示「还原」按钮。 */
    zoomActive: Boolean = false,
    onResetZoom: () -> Unit = {},
    /** 底部控制栏要渲染的功能按钮 id（由「控制栏自定义」决定，已按 order 排序）。 */
    bottomEntryIds: List<String> = emptyList(),
    /**
     * 任意功能 id → HUD 按钮配置。让底栏能够渲染 HUD 类功能（旋转/截图/画中画…），
     * 与 HUD 列/「更多」菜单互为通用，从而实现「除播放控制外任意功能可放任意位置」。
     */
    ctrlButton: @Composable (String) -> HudButtonConfig? = { null },
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    var clockText by remember { mutableStateOf(formatClock()) }
    var batteryLevel by remember { mutableIntStateOf(getBatteryLevel(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatClock()
            batteryLevel = getBatteryLevel(context)
            delay(30_000)
        }
    }

    Box(Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.0f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 用稳定的挖孔安全区替代 statusBarsPadding：进入播放页时系统栏是带动画收起的，
                // statusBars 的 inset 会逐帧变化，导致顶部控件先被顶到靠下位置、再升回最顶部；
                // displayCutout 是硬件挖孔 inset，系统栏隐藏时保持不变，控件从首帧就停在最终位置
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            // A-B 循环指示（竖屏空间紧张时隐藏）
            if (abLoopA != null && abLoopB != null && abLoopB > abLoopA && !isPortrait) {
                Icon(
                    imageVector = AbLoopIcon,
                    contentDescription = null,
                    tint = Color(0xFFFFAB40),
                    modifier = Modifier.size(20.dp),
                )
            }

            // P0-1 结构改造：网速每 500ms 变化，订阅收敛到本组件内部，
            // 避免把整个 PlayerControllerLayer 拖成每 500ms 重组一次。
            NetworkSpeedLabel(
                networkSpeedFlow = networkSpeedFlow,
                visible = !isPortrait,
            )

            Box(
                modifier = Modifier.width(48.dp).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = clockText,
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }

            if (batteryLevel >= 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryFull,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }

            if (sleepTimerText.isNotEmpty()) {
                Text(
                    text = sleepTimerText,
                    color = Color(0xFFFFAB40),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            IconButton(onClick = onToggleMoreMenu, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.player_more),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 中部 HUD 侧边按钮（拱形左右列）：由配置驱动的按钮自由分布在左右列，
        // 每列自适应高度 + 单边数量限制，即使把全部按钮放到一侧也不会溢出 / 错位。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter),
        ) {
            HudButtonColumn(
                configs = hudButtons,
                side = HudButtonSide.LEFT,
                modifier = Modifier.align(Alignment.CenterStart),
                portrait = isPortrait,
            )
            HudButtonColumn(
                configs = hudButtons,
                side = HudButtonSide.RIGHT,
                modifier = Modifier.align(Alignment.CenterEnd),
                portrait = isPortrait,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 底部挖孔 inset 是硬件稳定值，系统栏隐藏时不变化，避免进入时被导航栏顶起再下移的跳变
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            // 双指缩放还原：放大态下在进度条正上方居中显示（随控件一起显隐）
            if (zoomActive) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onResetZoom() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.player_zoom_reset),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // P0-1 结构改造：进度条与时间文本是播放器里唯一必须每 500ms 刷新的部分。
            // 订阅收敛到 PlayerProgressInline 内部，使本层其余内容（HUD 按钮、菜单、OSD）
            // 不再因进度变化而重组。时间放在进度条两侧以降低底栏高度。
            PlayerProgressInline(
                positionMsFlow = positionMsFlow,
                bufferedMsFlow = bufferedMsFlow,
                durationMs = durationMs,
                abLoopA = abLoopA,
                abLoopB = abLoopB,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
            )

            Spacer(Modifier.height(6.dp))

            if (isPortrait) {
                // 竖屏：功能按钮行（≤7 个均匀分布，超出可横向滑动）+ 核心播放控制行
                val fnCount = bottomEntryIds.count { it != "bar_playback" }
                val fnScrollable = fnCount > 7
                BottomFunctionButtons(
                    ids = bottomEntryIds,
                    ctrlButton = ctrlButton,
                    speedIndex = speedIndex,
                    speedLabel = speedLabel,
                    scaleIndex = scaleIndex,
                    showDownload = showDownload,
                    playlist = playlist,
                    audioManager = audioManager,
                    previousMusicVolume = previousMusicVolume,
                    onPreviousMusicVolumeChange = onPreviousMusicVolumeChange,
                    onToggleSpeedMenu = onToggleSpeedMenu,
                    onCycleScale = onCycleScale,
                    onDownload = onDownload,
                    onToggleAudioTrackMenu = onToggleAudioTrackMenu,
                    onAddSubtitle = onAddSubtitle,
                    onTogglePlaylistDialog = onTogglePlaylistDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fnScrollable) {
                                Modifier.horizontalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 8.dp),
                    arrangement = if (fnScrollable) Arrangement.Start else Arrangement.SpaceEvenly,
                )

                Spacer(Modifier.height(6.dp))

                // 第二行：核心播放控制（默认居中；「播放控制」位于最左时整行靠左）
                PlaybackControlsRow(
                    state = state,
                    hasEpisodes = playlist.size > 1,
                    onSkipPrevious = onSkipPrevious,
                    onRewind = onRewind,
                    onTogglePlayPause = onTogglePlayPause,
                    onForward = onForward,
                    onSkipNext = onSkipNext,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    arrangement = if (bottomEntryIds.firstOrNull() == "bar_playback") {
                        Arrangement.Start
                    } else {
                        Arrangement.Center
                    },
                )
            } else {
                // 横屏：与编辑器一致——「播放控制」居中时左侧按钮左对齐、右侧按钮右对齐；
                // 「播放控制」最左时功能按钮右对齐
                val playIdx = bottomEntryIds.indexOf("bar_playback")
                val before = if (playIdx > 0) bottomEntryIds.subList(0, playIdx) else emptyList()
                val after =
                    if (playIdx >= 0) bottomEntryIds.subList(playIdx + 1, bottomEntryIds.size) else emptyList()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playIdx == 0) {
                        // 最左：播放控制贴左，功能按钮右对齐
                        PlaybackControlsRow(
                            state = state,
                            hasEpisodes = playlist.size > 1,
                            onSkipPrevious = onSkipPrevious,
                            onRewind = onRewind,
                            onTogglePlayPause = onTogglePlayPause,
                            onForward = onForward,
                            onSkipNext = onSkipNext,
                        )
                        Spacer(Modifier.weight(1f))
                        if (after.isNotEmpty()) {
                            BottomFunctionButtons(
                                ids = after,
                                ctrlButton = ctrlButton,
                                speedIndex = speedIndex,
                                speedLabel = speedLabel,
                                scaleIndex = scaleIndex,
                                showDownload = showDownload,
                                playlist = playlist,
                                audioManager = audioManager,
                                previousMusicVolume = previousMusicVolume,
                                onPreviousMusicVolumeChange = onPreviousMusicVolumeChange,
                                onToggleSpeedMenu = onToggleSpeedMenu,
                                onCycleScale = onCycleScale,
                                onDownload = onDownload,
                                onToggleAudioTrackMenu = onToggleAudioTrackMenu,
                                onAddSubtitle = onAddSubtitle,
                                onTogglePlaylistDialog = onTogglePlaylistDialog,
                            )
                        }
                    } else {
                        // 居中：左区左对齐 · 播放控制居中 · 右区右对齐
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (before.isNotEmpty()) {
                                BottomFunctionButtons(
                                    ids = before,
                                    ctrlButton = ctrlButton,
                                    speedIndex = speedIndex,
                                    speedLabel = speedLabel,
                                    scaleIndex = scaleIndex,
                                    showDownload = showDownload,
                                    playlist = playlist,
                                    audioManager = audioManager,
                                    previousMusicVolume = previousMusicVolume,
                                    onPreviousMusicVolumeChange = onPreviousMusicVolumeChange,
                                    onToggleSpeedMenu = onToggleSpeedMenu,
                                    onCycleScale = onCycleScale,
                                    onDownload = onDownload,
                                    onToggleAudioTrackMenu = onToggleAudioTrackMenu,
                                    onAddSubtitle = onAddSubtitle,
                                    onTogglePlaylistDialog = onTogglePlaylistDialog,
                                )
                            }
                        }
                        PlaybackControlsRow(
                            state = state,
                            hasEpisodes = playlist.size > 1,
                            onSkipPrevious = onSkipPrevious,
                            onRewind = onRewind,
                            onTogglePlayPause = onTogglePlayPause,
                            onForward = onForward,
                            onSkipNext = onSkipNext,
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            if (after.isNotEmpty()) {
                                BottomFunctionButtons(
                                    ids = after,
                                    ctrlButton = ctrlButton,
                                    speedIndex = speedIndex,
                                    speedLabel = speedLabel,
                                    scaleIndex = scaleIndex,
                                    showDownload = showDownload,
                                    playlist = playlist,
                                    audioManager = audioManager,
                                    previousMusicVolume = previousMusicVolume,
                                    onPreviousMusicVolumeChange = onPreviousMusicVolumeChange,
                                    onToggleSpeedMenu = onToggleSpeedMenu,
                                    onCycleScale = onCycleScale,
                                    onDownload = onDownload,
                                    onToggleAudioTrackMenu = onToggleAudioTrackMenu,
                                    onAddSubtitle = onAddSubtitle,
                                    onTogglePlaylistDialog = onTogglePlaylistDialog,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 网速标签（P0-1 结构改造，2026-09-22）。
 *
 * 单独抽出，把 [networkSpeedFlow] 的每 500ms 订阅收敛在本组件作用域内。
 * 若直接在 [PlayerControllerLayer] 主体读取，整层（HUD 按钮、菜单、OSD）都会被拖成
 * 每秒重组 2 次。本地文件源下速度恒为 0，本组件不渲染任何内容。
 */
@Composable
private fun NetworkSpeedLabel(
    networkSpeedFlow: StateFlow<Long>,
    visible: Boolean,
) {
    val networkSpeed by networkSpeedFlow.collectAsStateWithLifecycle()
    if (networkSpeed > 0L && visible) {
        Text(
            text = formatNetworkSpeed(networkSpeed),
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** 视频播放位置的上报周期（ms）：进度展示按等长线性补间抹平台阶。 */
private const val VideoPositionTickMs = 500

/**
 * 单手布局用的进度行：时间显示在进度条两侧（当前时间在左、剩余时间在右），
 * 省去独立的时间行，从而降低底部控制栏高度。
 */
@Composable
private fun PlayerProgressInline(
    positionMsFlow: StateFlow<Long>,
    bufferedMsFlow: StateFlow<Long>,
    durationMs: Long,
    abLoopA: Long?,
    abLoopB: Long?,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val bufferedMs by bufferedMsFlow.collectAsStateWithLifecycle()
    // 拖动进度条时记录预览位置（fraction），两侧时间跟随显示目标时间
    var dragFractionPreview by remember { mutableStateOf<Float?>(null) }
    // 位置每 ~500ms 才更新一次，直接绑 UI 会一格格跳。用**等长线性补间**抹平台阶
    // （只在叶子层做，不牵动整页重组）；开启"减少动态效果"时补间时长归零即瞬跳。
    val reducedMotion = LocalNiReduceMotion.current
    val smoothPositionMs by animateFloatAsState(
        targetValue = positionMs.toFloat(),
        animationSpec = tween(
            if (reducedMotion) 0 else VideoPositionTickMs,
            easing = LinearEasing,
        ),
        label = "smoothPositionMs",
    )
    val displayPositionMs = dragFractionPreview?.let { (it * durationMs).toLong() }
        ?: smoothPositionMs.toLong()
    val previewPos = displayPositionMs
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration(previewPos),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        PlayerProgressBar(
            positionFraction = if (durationMs > 0) displayPositionMs.toFloat() / durationMs else 0f,
            bufferedFraction = if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f,
            durationMs = durationMs,
            abLoopA = abLoopA,
            abLoopB = abLoopB,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            onDragFractionChange = { dragFractionPreview = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = "-${formatDuration((durationMs - previewPos).coerceAtLeast(0L))}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * 核心播放控制行（上一集 / 快退 / 播放暂停 / 快进 / 下一集），竖屏与横屏共用。
 *
 * [arrangement] 决定整行在可用宽度内的对齐方式：竖屏「居中」预设用 Center、
 * 「最左」预设用 Start；横屏由外层三段式布局决定位置。
 */
@Composable
private fun PlaybackControlsRow(
    state: PlaybackState,
    hasEpisodes: Boolean,
    onSkipPrevious: () -> Unit,
    onRewind: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.Center,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasEpisodes) {
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.player_episode_previous),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Replay10,
                contentDescription = stringResource(R.string.player_rewind_10s),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Icon(
                imageVector = if (state is PlaybackState.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state is PlaybackState.Playing) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Forward10,
                contentDescription = stringResource(R.string.player_forward_10s),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        if (hasEpisodes) {
            IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.player_episode_next),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 底部控制栏的功能按钮组（受「控制栏自定义」驱动）。
 *
 * [ids] 为要渲染的功能 id（已按 order 排序）：默认含 倍速 / 画面比例 / 音量 / 下载 /
 * 音轨 / 字幕 / 选集。用户可在设置里隐藏、排序，或把某功能移到 HUD 左/右列或「更多」菜单
 * （移出后不再出现在底栏）。
 */
@Composable
private fun BottomFunctionButtons(
    ids: List<String>,
    ctrlButton: @Composable (String) -> HudButtonConfig?,
    speedIndex: Int,
    speedLabel: String,
    scaleIndex: Int,
    showDownload: Boolean,
    playlist: List<PlaylistItem>,
    audioManager: AudioManager?,
    previousMusicVolume: Int,
    onPreviousMusicVolumeChange: (Int) -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onCycleScale: () -> Unit,
    onDownload: () -> Unit,
    onToggleAudioTrackMenu: () -> Unit,
    onAddSubtitle: () -> Unit,
    onTogglePlaylistDialog: () -> Unit,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.Start,
) {
    val primary = MaterialTheme.colorScheme.primary
    var muted by remember {
        mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
    }
    LaunchedEffect(previousMusicVolume) {
        muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }
    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ids.forEach { id ->
            // 「播放控制」整组单独渲染，不在这里出按钮
            if (id == "bar_playback") return@forEach
            when (id) {
                "bar_speed" -> TextButton(onClick = onToggleSpeedMenu, modifier = Modifier.height(44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = stringResource(R.string.player_speed_icon),
                            tint = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = speedLabel,
                            color = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = if (speedIndex != 1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                "bar_scale" -> IconButton(onClick = onCycleScale, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.AspectRatio,
                        contentDescription = stringResource(R.string.player_scale_icon),
                        tint = if (scaleIndex != 0) primary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                "bar_volume" -> IconButton(
                    onClick = { muted = toggleVolumeButton(audioManager, onPreviousMusicVolumeChange) },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                        tint = if (muted) primary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                "bar_download" -> if (showDownload) {
                    IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.player_download_icon),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                "bar_audio" -> IconButton(onClick = onToggleAudioTrackMenu, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = stringResource(R.string.player_audio_track),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                "bar_subtitle" -> IconButton(onClick = onAddSubtitle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = stringResource(R.string.player_subtitle),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                "bar_playlist" -> if (playlist.isNotEmpty()) {
                    IconButton(onClick = onTogglePlaylistDialog, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ViewList,
                            contentDescription = stringResource(R.string.player_episode_list_icon),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                // 其余任意功能（HUD 类：旋转 / 截图 / 画中画 / 后台播放 / 锁屏 / AB 循环…）
                // 统一按 HUD 按钮配置渲染，实现「除播放控制外任意功能可放任意位置」
                else -> ctrlButton(id)?.let { cfg ->
                    IconButton(
                        onClick = { if (cfg.enabled) cfg.onClick() },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = cfg.icon,
                            contentDescription = cfg.contentDescription,
                            tint = cfg.tint.copy(alpha = if (cfg.enabled) 0.85f else 0.35f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
