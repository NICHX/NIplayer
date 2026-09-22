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
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.player.kernel.PlaylistItem
import kotlinx.coroutines.delay


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
    bookmarkPositions: List<Long> = emptyList(),
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

            // F-19：书签标记（绿色小圆点）
            if (bookmarkPositions.isNotEmpty() && durationMs > 0) {
                bookmarkPositions.forEach { pos ->
                    val x = (pos.toFloat() / durationMs).coerceIn(0f, 1f) * w
                    drawCircle(
                        color = Color(0xFF66BB6A),
                        radius = 3.dp.toPx(),
                        center = Offset(x, h / 2f),
                    )
                }
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
 * - 当某侧按钮太多而放不下时，先收缩按钮间距，仍放不下则限制该侧数量（截断到可容纳数），
 *   保证任何排布都不超出屏幕。
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
        // 横屏每侧最多 3 个（竖屏由可用高度自适应决定数量）
        val maxPerSide = if (portrait) Int.MAX_VALUE else 3
        // 间距自适应：优先 12dp，拥挤则收缩，下限 8dp
        val gap = when {
            n <= 1 -> 0.dp
            else -> maxOf(minGap, minOf((avail - btn * n) / (n - 1), idealGap))
        }
        // 单边数量限制：横屏固定上限 3；竖屏仍放不下时收缩到可容纳数
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
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    networkSpeed: Long,
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
    bookmarkPositions: List<Long> = emptyList(),
    pipEnabled: Boolean = false,
    onPictureInPicture: () -> Unit = {},
    onShowBookmarks: () -> Unit = {},
    onDownload: () -> Unit = {},
    /** 本地文件（已下载/缓存直链）来源时为 false，隐藏下载按钮。 */
    showDownload: Boolean = true,
    /** 已按用户自定义好的 HUD 按钮配置（含所在侧与序），用于渲染左右列。 */
    hudButtons: List<HudButtonConfig> = emptyList(),
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

            if (networkSpeed > 0L && !isPortrait) {
                Text(
                    text = formatNetworkSpeed(networkSpeed),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }

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
            // 拖动进度条时记录预览位置（fraction），时间文本跟随显示目标时间
            var dragFractionPreview by remember { mutableStateOf<Float?>(null) }
            PlayerProgressBar(
                positionFraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                bufferedFraction = if (durationMs > 0) bufferedMs.toFloat() / durationMs else 0f,
                durationMs = durationMs,
                abLoopA = abLoopA,
                abLoopB = abLoopB,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
                onDragFractionChange = { dragFractionPreview = it },
                bookmarkPositions = bookmarkPositions,
            )

            Spacer(Modifier.height(2.dp))

            val previewPos = dragFractionPreview?.let { (it * durationMs).toLong() } ?: positionMs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(previewPos),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "-${formatDuration((durationMs - previewPos).coerceAtLeast(0L))}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(6.dp))

            if (isPortrait) {
                // 竖屏：底部控制拆为两行布局，确保上/下一集与选集按钮可用
                // 第一行：功能按钮（倍速/音量 | 选集/音轨/字幕）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        val primary = MaterialTheme.colorScheme.primary
                        IconButton(onClick = onToggleSpeedMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = stringResource(R.string.player_speed_icon),
                                tint = if (speedIndex != 1) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        var muted by remember {
                            mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                        }
                        LaunchedEffect(previousMusicVolume) {
                            muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                        }
                        IconButton(
                            onClick = {
                                muted = toggleVolumeButton(audioManager, onPreviousMusicVolumeChange)
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff
                                    else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                                tint = if (muted) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (playlist.isNotEmpty()) {
                            IconButton(onClick = onTogglePlaylistDialog, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                    contentDescription = stringResource(R.string.player_episode_list_icon),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleAudioTrackMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = stringResource(R.string.player_audio_track),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = onAddSubtitle, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = stringResource(R.string.player_subtitle),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 第二行：核心播放控制（上一集/快退/播放/快进/下一集）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playlist.size > 1) {
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
                    if (playlist.size > 1) {
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
            } else {
                // 横屏：单行三层布局（功能 | 播放控制 | 功能）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        val primary = MaterialTheme.colorScheme.primary
                        TextButton(
                            onClick = onToggleSpeedMenu,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
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
                        IconButton(onClick = onCycleScale, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.AspectRatio,
                                contentDescription = stringResource(R.string.player_scale_icon),
                                tint = if (scaleIndex != 0) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        var muted by remember {
                            mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                        }
                        LaunchedEffect(previousMusicVolume) {
                            muted = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
                        }
                        IconButton(
                            onClick = {
                                muted = toggleVolumeButton(audioManager, onPreviousMusicVolumeChange)
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff
                                    else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (muted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                                tint = if (muted) primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (playlist.size > 1) {
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
                        if (playlist.size > 1) {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (showDownload) {
                            IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = stringResource(R.string.player_download_icon),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        IconButton(onClick = onToggleAudioTrackMenu, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = stringResource(R.string.player_audio_track),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = onAddSubtitle, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = stringResource(R.string.player_subtitle),
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (playlist.isNotEmpty()) {
                            IconButton(onClick = onTogglePlaylistDialog, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                    contentDescription = stringResource(R.string.player_episode_list_icon),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

