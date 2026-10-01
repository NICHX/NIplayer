package com.nichx.niplayer.feature.home.settings

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nichx.niplayer.datastore.PlayerControlEntry
import com.nichx.niplayer.datastore.PlayerControlLayout
import com.nichx.niplayer.datastore.PlayerControlOrientation
import com.nichx.niplayer.datastore.PlayerControlSurface
import com.nichx.niplayer.feature.home.R

/**
 * 全屏播放器编辑页：整页就是该方向的**模拟真实播放器**（页面锁定到对应方向）。
 *
 * - 左上角悬浮返回、右上角悬浮「恢复默认」；
 * - 底部为「更多」悬浮框：集中当前不在播放器界面上的按钮，长按可拖入播放器中的空位，
 *   播放器内的按钮也可拖回该悬浮框取消放置。
 *
 * 布局持久化到 [PlayerControlLayout]（按屏幕方向分桶），播放器按当前方向读取，实时同步。
 */
@Composable
internal fun PlayerControlEditorScreen(
    orientation: PlayerControlOrientation,
    onBack: () -> Unit,
) {
    var resetTick by remember { mutableIntStateOf(0) }
    val activity = LocalActivity.current

    // 编辑横屏时整页横过来；离开时还原进入前的方向
    DisposableEffect(orientation) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = if (orientation == PlayerControlOrientation.PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val immersive = orientation == PlayerControlOrientation.LANDSCAPE

    // 横屏：真实全屏（隐藏系统状态栏 / 导航栏），离开时恢复（与播放器一致）。
    // 竖屏：**不隐藏系统栏** —— 竖屏编辑并不会用到状态栏那块区域，若隐藏它，退出恢复时
    //       系统栏重绘会让整页出现一次明显的下移过程，故竖屏保持系统栏原样。
    DisposableEffect(immersive) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (immersive) {
            val previousBehavior = controller?.systemBarsBehavior
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                previousBehavior?.let { controller.systemBarsBehavior = it }
            }
        } else {
            // 竖屏兜底：确保系统栏处于显示态（避免被别处隐藏后沿用）
            controller?.show(WindowInsetsCompat.Type.systemBars())
            onDispose { }
        }
    }

    // 横屏系统栏已隐藏，整屏铺满（播放器自身延伸到挖孔之下）；
    // 竖屏保留系统栏，内容整体避让系统栏区域。
    val contentInsetModifier = if (immersive) {
        Modifier
    } else {
        Modifier.windowInsetsPadding(WindowInsets.systemBars)
    }
    val topInset = if (immersive) {
        WindowInsets.displayCutout.only(WindowInsetsSides.Top)
    } else {
        WindowInsets.systemBars.only(WindowInsetsSides.Top)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 重置时整体重建编辑器状态，避免残留拖拽态
        key(orientation, resetTick) {
            ControlCustomizeEditor(
                orientation = orientation,
                onBack = onBack,
                modifier = Modifier.fillMaxSize().then(contentInsetModifier),
            )
        }

        // 顶部居中：纯文字「恢复默认」（避让状态栏 / 挖孔）
        Text(
            text = stringResource(R.string.player_ctrl_reset),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(topInset)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    PlayerControlLayout.reset(orientation)
                    resetTick++
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 编辑器中的一个可放置位置：所属面 + 面内序号。 */
private data class SlotKey(val surface: PlayerControlSurface, val index: Int)

/** 底栏整块的哨兵槽：落在底栏空白处时，吸附到最近的底栏槽位。 */
private const val BOTTOM_BAR_BG = Int.MIN_VALUE

/** 底栏「播放控制」预设位哨兵：拖到它上面即强制切到「最左」。 */
private const val BOTTOM_PRESET_LEFT = Int.MIN_VALUE + 1

/** 底栏「播放控制」预设位哨兵：拖到它上面即强制切到「居中」。 */
private const val BOTTOM_PRESET_CENTER = Int.MIN_VALUE + 2

/** 底栏功能位/拖动占位的近似尺寸（比真实播放器略大，便于手指拖动）。 */
private val FunctionSlotWidth = 44.dp
private val FunctionSlotHeight = 38.dp

/** HUD 预设位尺寸（同样略放大，便于拖动）。 */
private val HudSlotSize = 38.dp

/** 「更多」悬浮框内单个条目的尺寸。 */
private val MoreChipWidth = 66.dp
private val MoreChipHeight = 82.dp

/**
 * 拖动落点的「虚拟占位按钮」：尺寸从 0 平滑展开（带动其它按钮让位），
 * 内容为虚线轮廓 + 被拖按钮的图标（替代原先「高亮目标按钮」的做法）。
 *
 * @param horizontal true 时沿水平方向展开（底栏/更多面板），false 时沿垂直方向展开（HUD 列）
 */
@Composable
private fun DropPlaceholder(
    visible: Boolean,
    mainSize: Dp,
    crossSize: Dp,
    horizontal: Boolean,
    circular: Boolean,
    icon: ImageVector?,
    register: (Rect) -> Unit = {},
) {
    val animated by animateDpAsState(
        targetValue = if (visible) mainSize else 0.dp,
        label = "dropPlaceholder",
    )
    val width = if (horizontal) animated else crossSize
    val height = if (horizontal) crossSize else animated
    val shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
    Box(
        Modifier
            .width(width)
            .height(height)
            .onGloballyPositioned { register(it.boundsInRoot()) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .dashedOutline(
                    color = Color.White.copy(alpha = 0.6f),
                    cornerRadius = if (circular) null else 10.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(crossSize * 0.5f),
                )
            }
        }
    }
}

/** 拖拽回调集合（在各可拖拽卡片间共享）。 */
private class DragCallbacks(
    val onStart: (String, Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: (String) -> Unit,
    val onCancel: () -> Unit,
)

/** 长按拖拽手势：把卡片根位置 + 位移换算成根坐标系里的指针位置，回调给编辑器做命中判定。 */
private fun Modifier.controlDrag(
    id: String,
    rootPosition: () -> Offset,
    drag: DragCallbacks,
): Modifier = pointerInput(id) {
    var total = Offset.Zero
    var start = Offset.Zero
    detectDragGesturesAfterLongPress(
        onDragStart = { local ->
            total = Offset.Zero
            start = rootPosition() + local
            drag.onStart(id, start)
        },
        onDrag = { change, amount ->
            change.consume()
            total += amount
            drag.onMove(start + total)
        },
        onDragEnd = { drag.onEnd(id) },
        onDragCancel = { drag.onCancel() },
    )
}

/** 虚线描边（圆角矩形，cornerRadius 取 minDimension/2 即为圆）。 */
private fun Modifier.dashedOutline(
    color: Color,
    strokeWidth: Dp = 1.4.dp,
    cornerRadius: Dp? = null,
): Modifier = drawBehind {
    val sw = strokeWidth.toPx()
    val radius = cornerRadius?.toPx() ?: (size.minDimension / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(sw / 2f, sw / 2f),
        size = Size((size.width - sw).coerceAtLeast(0f), (size.height - sw).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
    )
}

// ===== 「更多」悬浮框材质：复刻播放器内弹窗的暗色液态玻璃调色板 =====
private val MoreGlassTop = Color(0xE82C2C30)
private val MoreGlassBottom = Color(0xE8141416)
private val MorePanelBorder = Color.White.copy(alpha = 0.06f)
private val MoreTextPrimary = Color(0xFFE8E8EA)
private val MoreTextSecondary = Color(0xFF9E9EA2)
private val MoreDivider = Color.White.copy(alpha = 0.05f)
private val MoreSelectedBg = Color.White.copy(alpha = 0.10f)

/**
 * 核心编辑器：整屏模拟真实播放器 + 屏幕居中的「更多」悬浮框。
 *
 * 单份状态（[entries]）驱动整棵 UI；拖拽时用根坐标命中各位置的边界矩形，
 * 落点即为要插入的 (面, 序号)。
 */
@Composable
private fun ControlCustomizeEditor(
    orientation: PlayerControlOrientation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entries by remember {
        // 布局不再暴露可见性；把历史遗留的隐藏项一并置为可见，保证不会永久隐藏。
        PlayerControlLayout.ALL_IDS.forEachIndexed { i, id ->
            val e = PlayerControlLayout.loadEntry(id, i, orientation)
            if (!e.visible) PlayerControlLayout.saveEntry(id, e.surface, true, e.order, orientation)
        }
        mutableStateOf(
            PlayerControlLayout.ALL_IDS.mapIndexed { i, id ->
                PlayerControlLayout.loadEntry(id, i, orientation).copy(visible = true)
            },
        )
    }
    fun persist(e: PlayerControlEntry) =
        PlayerControlLayout.saveEntry(e.id, e.surface, e.visible, e.order, orientation)

    // HUD 左/右列的单侧数量上限（与播放器一致）：横屏 3、竖屏 4
    val hudLimit = if (orientation == PlayerControlOrientation.PORTRAIT) 4 else 3

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<SlotKey?>(null) }
    var editorTopLeft by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { HashMap<SlotKey, Rect>() }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // 浮动影像相对指针的偏移，避免被手指遮住
    val ghostOffsetX = with(density) { 52.dp.toPx() }
    val ghostOffsetY = with(density) { 40.dp.toPx() }

    fun surfaceEntries(surface: PlayerControlSurface) =
        entries.filter { it.surface == surface }.sortedBy { it.order }

    fun computeHover(p: Offset) {
        var best: SlotKey? = null
        var bestArea = Float.MAX_VALUE
        var inBottomBar = false
        slotBounds.forEach { (k, r) ->
            if (r.contains(p)) {
                if (k.index == BOTTOM_BAR_BG) {
                    inBottomBar = true
                } else {
                    val a = r.width * r.height
                    if (a <= bestArea) {
                        bestArea = a
                        best = k
                    }
                }
            }
        }
        // 落在底栏区域内 → 一律改用最近的底栏槽位：
        // 既能吸收底栏空白处，也能避免被其它面残留的过期矩形抢走命中
        if (inBottomBar && best?.surface != PlayerControlSurface.BOTTOM) {
            val nearest = slotBounds.entries
                .filter {
                    // 仅取真实槽位（哨兵 index < 0 不参与「就近吸附」）
                    it.key.surface == PlayerControlSurface.BOTTOM && it.key.index >= 0
                }
                .minByOrNull { (_, r) ->
                    val dx = r.center.x - p.x
                    val dy = r.center.y - p.y
                    dx * dx + dy * dy
                }
                ?.key
            if (nearest != null) best = nearest
        }
        hover = best
    }

    fun cancelDrag() {
        draggingId = null
        hover = null
    }

    fun commitDrop(id: String) {
        val target = hover
        val moving = entries.firstOrNull { it.id == id }
        if (moving == null || target == null) {
            cancelDrag()
            return
        }
        val dest = target.surface
        // 「播放控制」整组只能留在底栏
        if (id == "bar_playback" && dest != PlayerControlSurface.BOTTOM) {
            cancelDrag()
            return
        }
        // 左/右列有数量上限（横屏 3 / 竖屏 4）：已满且来自别处时拒绝
        if ((dest == PlayerControlSurface.LEFT || dest == PlayerControlSurface.RIGHT) &&
            moving.surface != dest &&
            entries.count { it.surface == dest } >= hudLimit
        ) {
            cancelDrag()
            return
        }

        val oldOrdered = entries.filter { it.surface == dest }.sortedBy { it.order }
        val oldIdx = oldOrdered.indexOfFirst { it.id == id }
        val insert = if (dest == PlayerControlSurface.BOTTOM && id == "bar_playback") {
            // 播放控制整组仅可置于「最左」或「居中」：落到首格 / 最左预设取 0，其余统一吸附到中间
            val others = entries.count { it.surface == PlayerControlSurface.BOTTOM && it.id != id }
            if (target.index == 0 || target.index == BOTTOM_PRESET_LEFT) 0 else others / 2
        } else if (dest == PlayerControlSurface.BOTTOM && target.index < 0) {
            // 功能位落到「播放控制预设位」上：按该预设位对应的位置插入（左预设=最前、居中预设=中间）
            if (target.index == BOTTOM_PRESET_LEFT) {
                0
            } else {
                entries.count { it.surface == PlayerControlSurface.BOTTOM } / 2
            }
        } else {
            var raw = target.index
            if (oldIdx >= 0 && oldIdx < raw) raw -= 1
            raw
        }

        val rest = entries.filter { it.id != id }
        val result = mutableListOf<PlayerControlEntry>()
        var order = 0
        PlayerControlLayout.ALL_SURFACES.forEach { s ->
            val list = rest.filter { it.surface == s }.sortedBy { it.order }.toMutableList()
            if (s == dest) list.add(insert.coerceIn(0, list.size), moving.copy(surface = s))
            list.forEach { result.add(it.copy(order = order++)) }
        }
        result.forEach { persist(it) }
        entries = result
        cancelDrag()
    }

    val drag = DragCallbacks(
        onStart = { id, p ->
            draggingId = id
            dragPos = p
            computeHover(p)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onMove = { p ->
            dragPos = p
            computeHover(p)
        },
        onEnd = { id ->
            commitDrop(id)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        onCancel = {
            cancelDrag()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
    )

    val registerSlot: (SlotKey, Rect) -> Unit = { key, rect -> slotBounds[key] = rect }

    Box(modifier = modifier.onGloballyPositioned { editorTopLeft = it.positionInRoot() }) {
        // 整屏模拟播放器（底部预留出「更多」悬浮框的高度，避免底栏被遮挡）
        SimulatedPlayer(
            orientation = orientation,
            left = surfaceEntries(PlayerControlSurface.LEFT),
            right = surfaceEntries(PlayerControlSurface.RIGHT),
            bottom = surfaceEntries(PlayerControlSurface.BOTTOM),
            hudLimit = hudLimit,
            hover = hover,
            draggingId = draggingId,
            registerSlot = registerSlot,
            drag = drag,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )

        // 屏幕居中「更多」悬浮框
        FloatingMorePanel(
            items = surfaceEntries(PlayerControlSurface.MORE),
            hover = hover,
            draggingId = draggingId,
            registerSlot = registerSlot,
            drag = drag,
            modifier = Modifier.align(Alignment.Center),
            landscape = orientation == PlayerControlOrientation.LANDSCAPE,
        )

        // 拖拽中的浮动影像
        draggingId?.let { id ->
            val e = entries.firstOrNull { it.id == id } ?: return@let
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (dragPos.x - editorTopLeft.x - ghostOffsetX).toInt(),
                            (dragPos.y - editorTopLeft.y - ghostOffsetY).toInt(),
                        )
                    }
                    .zIndex(10f),
            ) {
                DragGhost(entry = e)
            }
        }
    }
}

/**
 * 屏幕居中的「更多」悬浮框：与播放器内「更多」弹窗同款（液态玻璃 + 标题 + 分隔线 + 三列网格项）。
 * 内容超出时在限高内滚动；整个面板同时是「拖回更多」的落点。
 */
@Composable
private fun FloatingMorePanel(
    items: List<PlayerControlEntry>,
    hover: SlotKey?,
    draggingId: String?,
    registerSlot: (SlotKey, Rect) -> Unit,
    drag: DragCallbacks,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    // 限高：屏幕居中时也不能压到顶栏/底栏（横向 0.58 屏高留出上下余量）
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    // 横屏空间充裕：弹窗放宽并排 4 列；竖屏保持较窄，给左右 HUD 列留出拖放空间
    val columns = if (landscape) 4 else 3
    Column(
        modifier
            .widthIn(
                min = if (landscape) 300.dp else 224.dp,
                max = if (landscape) 384.dp else 248.dp,
            )
            .heightIn(max = maxPanelHeight)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(MoreGlassTop, MoreGlassBottom)))
            .border(0.5.dp, MorePanelBorder, shape)
            // 整个悬浮框作为「移回更多」的落点（追加到末尾）；各条目区域更小，命中时优先条目
            .onGloballyPositioned {
                registerSlot(SlotKey(PlayerControlSurface.MORE, items.size), it.boundsInRoot())
            }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.player_ctrl_pool_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = MoreTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = MoreDivider, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.player_ctrl_pool_empty),
                color = MoreTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.chunked(columns).forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp)) {
                        repeat(columns) { colIdx ->
                            val index = rowIdx * columns + colIdx
                            val e = row.getOrNull(colIdx)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (e != null) {
                                    val isDragged = e.id == draggingId
                                    val hoveredHere =
                                        hover == SlotKey(PlayerControlSurface.MORE, index)
                                    if (hoveredHere && !isDragged) {
                                        // 落点：显示「虚拟占位按钮」（被拖按钮的图标）
                                        DropPlaceholder(
                                            visible = true,
                                            mainSize = MoreChipWidth,
                                            crossSize = MoreChipHeight,
                                            horizontal = true,
                                            circular = false,
                                            icon = draggingId?.let { ctrlIcon(it) },
                                            register = {
                                                registerSlot(
                                                    SlotKey(PlayerControlSurface.MORE, index),
                                                    it,
                                                )
                                            },
                                        )
                                    } else {
                                        MoreChip(
                                            entry = e,
                                            hovered = false,
                                            dragging = isDragged,
                                            register = {
                                                registerSlot(
                                                    SlotKey(PlayerControlSurface.MORE, index),
                                                    it,
                                                )
                                            },
                                            drag = drag,
                                        )
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

/** 「更多」悬浮框中的单个功能项（圆底图标 + 名称，复刻播放器「更多」弹窗样式）。 */
@Composable
private fun MoreChip(
    entry: PlayerControlEntry,
    hovered: Boolean,
    dragging: Boolean,
    register: (Rect) -> Unit,
    drag: DragCallbacks,
) {
    var root by remember(entry.id) { mutableStateOf(Offset.Zero) }
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onGloballyPositioned {
                root = it.positionInRoot()
                register(it.boundsInRoot())
            }
            .controlDrag(entry.id, { root }, drag)
            .clip(shape)
            .background(if (hovered) MoreSelectedBg else Color.Transparent)
            .then(
                if (hovered) {
                    Modifier.border(1.5.dp, primary.copy(alpha = 0.5f), shape)
                } else {
                    Modifier
                },
            )
            .alpha(if (dragging) 0f else 1f)
            .padding(8.dp)
            .fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ctrlIcon(entry.id),
                contentDescription = ctrlName(entry.id),
                tint = primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = ctrlName(entry.id),
            color = MoreTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 模拟真实播放器界面（横屏 / 竖屏）。
 *
 * 直接铺满编辑器可用区域：页面本身已锁定到该方向，所以这一块就相当于真实的全屏播放器。
 * 内部为两段式结构 —— 「主体区（顶栏 + 左右 HUD 列）」+「底栏」，
 * HUD 只在主体区内垂直居中，因此不会与底栏重叠。
 */
@Composable
private fun SimulatedPlayer(
    orientation: PlayerControlOrientation,
    left: List<PlayerControlEntry>,
    right: List<PlayerControlEntry>,
    bottom: List<PlayerControlEntry>,
    hudLimit: Int,
    hover: SlotKey?,
    draggingId: String?,
    registerSlot: (SlotKey, Rect) -> Unit,
    drag: DragCallbacks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPortrait = orientation == PlayerControlOrientation.PORTRAIT
    // 左列 / 右列预设位（至少 hudLimit 个；历史遗留的超限项也照常显示，避免被隐藏后无法操作）
    val leftSlots = maxOf(hudLimit, left.size)
    val rightSlots = maxOf(hudLimit, right.size)

    Box(
        modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xFF242833), Color(0xFF0B0C10))),
            ),
    ) {
        // 上下压暗遮罩，贴近播放器视觉
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.30f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize()) {
            // 顶栏（其返回箭头即真正的返回按钮）
            PreviewTopBar(
                title = stringResource(R.string.player_ctrl_preview_title),
                onBack = onBack,
            )

            // 主体区：左右 HUD 列（在顶栏与底栏之间垂直居中）
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // 横屏刘海在侧边时，HUD 列一并避让
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    ),
            ) {
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(leftSlots) { i ->
                        val e = left.getOrNull(i)
                        val hoveredHere = hover == SlotKey(PlayerControlSurface.LEFT, i)
                        DropPlaceholder(
                            visible = hoveredHere && e != null,
                            mainSize = HudSlotSize,
                            crossSize = HudSlotSize,
                            horizontal = false,
                            circular = true,
                            icon = draggingId?.let { ctrlIcon(it) },
                        )
                        SlotButton(
                            entry = e,
                            // 有按钮时用「虚拟占位」提示落点，空位才高亮
                            hovered = hoveredHere && e == null,
                            dragging = e != null && e.id == draggingId,
                            collapsed = e != null && e.id == draggingId,
                            register = { registerSlot(SlotKey(PlayerControlSurface.LEFT, i), it) },
                            drag = drag,
                        )
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(rightSlots) { i ->
                        val e = right.getOrNull(i)
                        val hoveredHere = hover == SlotKey(PlayerControlSurface.RIGHT, i)
                        DropPlaceholder(
                            visible = hoveredHere && e != null,
                            mainSize = HudSlotSize,
                            crossSize = HudSlotSize,
                            horizontal = false,
                            circular = true,
                            icon = draggingId?.let { ctrlIcon(it) },
                        )
                        SlotButton(
                            entry = e,
                            hovered = hoveredHere && e == null,
                            dragging = e != null && e.id == draggingId,
                            collapsed = e != null && e.id == draggingId,
                            register = { registerSlot(SlotKey(PlayerControlSurface.RIGHT, i), it) },
                            drag = drag,
                        )
                    }
                }
            }

            // 底栏：主体区之下，固定高度
            if (isPortrait) {
                PortraitBottomBar(bottom, hover, draggingId, registerSlot, drag)
            } else {
                LandscapeBottomBar(bottom, hover, draggingId, registerSlot, drag)
            }
        }
    }
}

/** 模拟播放器顶栏：返回（即退出编辑）/ 标题 / 更多。 */
@Composable
private fun PreviewTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // 挖孔/刘海避让（系统栏已隐藏，仅硬件挖孔 inset 恒定）
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.BatteryFull,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.player_ctrl_preview_more),
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** HUD 侧边预设位：有按钮显示其图标，空位显示虚线圆 + 加号。 */
@Composable
private fun SlotButton(
    entry: PlayerControlEntry?,
    hovered: Boolean,
    dragging: Boolean,
    collapsed: Boolean = false,
    register: (Rect) -> Unit,
    drag: DragCallbacks,
) {
    var root by remember(entry?.id) { mutableStateOf(Offset.Zero) }
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .then(if (collapsed) Modifier.size(0.dp) else Modifier.size(38.dp))
            .onGloballyPositioned {
                root = it.positionInRoot()
                register(it.boundsInRoot())
            }
            .then(if (entry != null) Modifier.controlDrag(entry.id, { root }, drag) else Modifier)
            .clip(CircleShape)
            .then(if (entry == null) Modifier.dashedOutline(Color.White.copy(alpha = 0.35f)) else Modifier)
            .background(if (entry != null) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.05f))
            .then(if (hovered) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
            .alpha(if (dragging) 0.35f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        if (entry != null) {
            Icon(
                imageVector = ctrlIcon(entry.id),
                contentDescription = ctrlName(entry.id),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.player_ctrl_slot_empty),
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 竖屏底栏：功能行（均匀铺满）+ 播放控制组独占一行（可「居中」或「靠左」）。 */
@Composable
private fun PortraitBottomBar(
    bottom: List<PlayerControlEntry>,
    hover: SlotKey?,
    draggingId: String?,
    registerSlot: (SlotKey, Rect) -> Unit,
    drag: DragCallbacks,
) {
    val playIdx = bottom.indexOfFirst { it.id == "bar_playback" }
    val playback = bottom.getOrNull(playIdx)
    val leftAligned = playIdx == 0
    // 功能位槽位索引 == 其在底栏列表中的真实序号（不能再偏移，否则会与末尾追加位撞号）
    val functions = bottom.withIndex()
        .filter { it.value.id != "bar_playback" }
        .map { (slotIdx, e) -> slotIdx to e }
    val hoverIndex = hover?.takeIf { it.surface == PlayerControlSurface.BOTTOM }?.index

    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                registerSlot(SlotKey(PlayerControlSurface.BOTTOM, BOTTOM_BAR_BG), it.boundsInRoot())
            }
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
            // 四周留出余量：既让落点远离屏幕边缘（避免与系统手势区冲突），也便于瞄准
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
    ) {
        MockProgressBar()
        Spacer(Modifier.height(6.dp))
        // 功能行：≤7 个时均匀分布；超出则横向滑动
        val scrollable = functions.size > 7
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (scrollable) {
                Arrangement.spacedBy(2.dp)
            } else {
                Arrangement.SpaceEvenly
            },
        ) {
            functions.forEach { (slotIdx, e) ->
                DropPlaceholder(
                    visible = hoverIndex == slotIdx,
                    mainSize = FunctionSlotWidth,
                    crossSize = FunctionSlotHeight,
                    horizontal = true,
                    circular = false,
                    icon = draggingId?.let { ctrlIcon(it) },
                )
                BottomFunctionSlot(
                    entry = e,
                    hovered = false,
                    dragging = e.id == draggingId,
                    collapsed = e.id == draggingId,
                    register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, slotIdx), it) },
                    drag = drag,
                )
            }
            EmptyBottomSlot(
                hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, bottom.size),
                register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, bottom.size), it) },
            )
        }
        Spacer(Modifier.height(6.dp))
        // 播放控制组独占一行；另一个预设位以虚线占位提示，拖过去即可切换
        val alternate = if (leftAligned) BOTTOM_PRESET_CENTER else BOTTOM_PRESET_LEFT
        Box(Modifier.fillMaxWidth()) {
            playback?.let { pb ->
                PlaybackGroupSlot(
                    entry = pb,
                    hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, playIdx),
                    dragging = pb.id == draggingId,
                    collapsed = pb.id == draggingId,
                    register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, playIdx), it) },
                    modifier = Modifier.align(
                        if (leftAligned) Alignment.CenterStart else Alignment.Center,
                    ),
                    drag = drag,
                )
            }
            PlaybackPresetPlaceholder(
                hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, alternate),
                // 仅在拖动「播放控制组」时作为落点；否则让位给「就近吸附」，避免抢走功能位的落点
                register = { rect ->
                    if (draggingId == "bar_playback") {
                        registerSlot(SlotKey(PlayerControlSurface.BOTTOM, alternate), rect)
                    }
                },
                modifier = Modifier.align(
                    if (leftAligned) Alignment.Center else Alignment.CenterStart,
                ),
            )
        }
    }
}

/**
 * 横屏底栏（与真实播放器一致）：
 * - 居中：播放控制居中，其**左侧**功能按钮左对齐、**右侧**功能按钮右对齐；
 * - 最左：播放控制贴左，功能按钮**右对齐**；中间留出「居中」预设位虚线。
 */
@Composable
private fun LandscapeBottomBar(
    bottom: List<PlayerControlEntry>,
    hover: SlotKey?,
    draggingId: String?,
    registerSlot: (SlotKey, Rect) -> Unit,
    drag: DragCallbacks,
) {
    val playIdx = bottom.indexOfFirst { it.id == "bar_playback" }
    val playback = bottom.getOrNull(playIdx)
    val leftAligned = playIdx == 0
    val before = if (playIdx > 0) (0 until playIdx).map { it to bottom[it] } else emptyList()
    val after =
        if (playIdx >= 0) ((playIdx + 1) until bottom.size).map { it to bottom[it] } else emptyList()
    val appendIndex = bottom.size
    val hoverIndex = hover?.takeIf { it.surface == PlayerControlSurface.BOTTOM }?.index

    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                registerSlot(SlotKey(PlayerControlSurface.BOTTOM, BOTTOM_BAR_BG), it.boundsInRoot())
            }
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Bottom))
            // 四周留出余量：既让落点远离屏幕边缘（避免与系统手势区冲突），也便于瞄准
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
    ) {
        MockProgressBar()
        Spacer(Modifier.height(6.dp))
        if (playback != null && leftAligned) {
            // 最左预设：播放控制贴左；功能按钮右对齐；中间为「居中」预设位虚线
            Box(Modifier.fillMaxWidth()) {
                PlaybackGroupSlot(
                    entry = playback,
                    hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, playIdx),
                    dragging = playback.id == draggingId,
                    collapsed = playback.id == draggingId,
                    register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, playIdx), it) },
                    modifier = Modifier.align(Alignment.CenterStart),
                    drag = drag,
                )
                PlaybackPresetPlaceholder(
                    hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, BOTTOM_PRESET_CENTER),
                    // 仅在拖动「播放控制组」时作为落点
                    register = { rect ->
                        if (draggingId == "bar_playback") {
                            registerSlot(
                                SlotKey(PlayerControlSurface.BOTTOM, BOTTOM_PRESET_CENTER),
                                rect,
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
                FunctionSlots(
                    entries = after,
                    hover = hover,
                    draggingId = draggingId,
                    registerSlot = registerSlot,
                    drag = drag,
                    alignEnd = true,
                    appendIndex = appendIndex,
                    hoverIndex = hoverIndex,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        } else {
            // 居中预设：左区左对齐、右区右对齐，播放控制居中
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FunctionSlots(
                    entries = before,
                    hover = hover,
                    draggingId = draggingId,
                    registerSlot = registerSlot,
                    drag = drag,
                    alignEnd = false,
                    appendIndex = null,
                    hoverIndex = hoverIndex,
                    modifier = Modifier.weight(1f),
                )
                playback?.let { pb ->
                    PlaybackGroupSlot(
                        entry = pb,
                        hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, playIdx),
                        dragging = pb.id == draggingId,
                        collapsed = pb.id == draggingId,
                        register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, playIdx), it) },
                        drag = drag,
                    )
                }
                FunctionSlots(
                    entries = after,
                    hover = hover,
                    draggingId = draggingId,
                    registerSlot = registerSlot,
                    drag = drag,
                    alignEnd = true,
                    appendIndex = appendIndex,
                    hoverIndex = hoverIndex,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 底栏的一段功能区：按 alignEnd 决定左对齐或右对齐；末尾可选「追加」空位；拖动时让出动画空隙。 */
@Composable
private fun FunctionSlots(
    entries: List<Pair<Int, PlayerControlEntry>>,
    hover: SlotKey?,
    draggingId: String?,
    registerSlot: (SlotKey, Rect) -> Unit,
    drag: DragCallbacks,
    alignEnd: Boolean,
    appendIndex: Int?,
    hoverIndex: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        entries.forEach { (idx, e) ->
            DropPlaceholder(
                visible = hoverIndex == idx,
                mainSize = FunctionSlotWidth,
                crossSize = FunctionSlotHeight,
                horizontal = true,
                circular = false,
                icon = draggingId?.let { ctrlIcon(it) },
            )
            BottomFunctionSlot(
                entry = e,
                hovered = false,
                dragging = e.id == draggingId,
                collapsed = e.id == draggingId,
                register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, idx), it) },
                drag = drag,
            )
        }
        appendIndex?.let { idx ->
            EmptyBottomSlot(
                hovered = hover == SlotKey(PlayerControlSurface.BOTTOM, idx),
                register = { registerSlot(SlotKey(PlayerControlSurface.BOTTOM, idx), it) },
            )
        }
    }
}

/** 底栏功能位（图标；倍速额外显示文字，贴近真实底栏）。 */
@Composable
private fun BottomFunctionSlot(
    entry: PlayerControlEntry,
    hovered: Boolean,
    dragging: Boolean,
    collapsed: Boolean = false,
    register: (Rect) -> Unit,
    drag: DragCallbacks,
) {
    var root by remember(entry.id) { mutableStateOf(Offset.Zero) }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .then(if (collapsed) Modifier.size(0.dp) else Modifier.height(38.dp))
            .onGloballyPositioned {
                root = it.positionInRoot()
                register(it.boundsInRoot())
            }
            .controlDrag(entry.id, { root }, drag)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) scheme.primary.copy(alpha = 0.3f) else Color.Transparent)
            .alpha(if (dragging) 0.35f else 1f)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ctrlIcon(entry.id),
            contentDescription = ctrlName(entry.id),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp),
        )
        if (entry.id == "bar_speed") {
            Spacer(Modifier.width(2.dp))
            Text(text = "1.0x", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
        }
    }
}

/** 播放控制整组（上一集 / 快退 / 播放 / 快进 / 下一集），作为一个整体可拖动。 */
@Composable
private fun PlaybackGroupSlot(
    entry: PlayerControlEntry,
    hovered: Boolean,
    dragging: Boolean,
    collapsed: Boolean = false,
    register: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    drag: DragCallbacks,
) {
    var root by remember(entry.id) { mutableStateOf(Offset.Zero) }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .then(if (collapsed) Modifier.size(0.dp) else Modifier)
            .onGloballyPositioned {
                root = it.positionInRoot()
                register(it.boundsInRoot())
            }
            .controlDrag(entry.id, { root }, drag)
            .clip(RoundedCornerShape(18.dp))
            .background(if (hovered) scheme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.25f))
            .alpha(if (dragging) 0.35f else 1f)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Icon(Icons.Rounded.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** 底栏末尾的「追加」空位：把按钮放到功能行最后。 */
@Composable
private fun EmptyBottomSlot(
    hovered: Boolean,
    register: (Rect) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(38.dp)
            .onGloballyPositioned { register(it.boundsInRoot()) }
            .clip(RoundedCornerShape(8.dp))
            .dashedOutline(
                color = if (hovered) scheme.primary else Color.White.copy(alpha = 0.35f),
                cornerRadius = 8.dp,
            )
            .alpha(if (hovered) 1f else 0.9f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.player_ctrl_slot_empty),
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 「播放控制」另一个预设位的虚线占位：把播放控制组拖到这里即可切换「最左 / 居中」。 */
@Composable
private fun PlaybackPresetPlaceholder(
    hovered: Boolean,
    register: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .width(96.dp)
            .height(34.dp)
            .onGloballyPositioned { register(it.boundsInRoot()) }
            .clip(RoundedCornerShape(17.dp))
            .dashedOutline(
                color = if (hovered) scheme.primary else Color.White.copy(alpha = 0.35f),
                cornerRadius = 17.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayCircleOutline,
            contentDescription = stringResource(R.string.player_ctrl_name_bar_playback),
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 模拟进度条（装饰）：当前时间 + 轨道 + 剩余时间。 */
@Composable
private fun MockProgressBar() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "00:42",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height(10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.36f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.36f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White))
            }
        }
        Text(
            text = "-02:18",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** 拖拽浮动影像：跟随指针的图标 + 名称。 */
@Composable
private fun DragGhost(entry: PlayerControlEntry) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(scheme.primary)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ctrlIcon(entry.id),
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = ctrlName(entry.id), style = MaterialTheme.typography.bodySmall, color = scheme.onPrimary)
    }
}

/**
 * A-B 循环专用图标（与播放器内一致：圆形环 + A/B 字形，全描边）。
 * home 模块不依赖 feature:player，故此处复制绘制，保证符号相同。
 */
private val AbLoopIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    val ringPath = PathParser().parsePathString(
        "M12 5 A7 7 0 1 1 12 19 A7 7 0 1 1 12 5 Z"
    ).toNodes()
    val letterA = PathParser().parsePathString(
        "M10 9.5 L8.4 14.5 M10 9.5 L11.6 14.5 M9 11.9 L11 11.9"
    ).toNodes()
    val letterB = PathParser().parsePathString(
        "M13.6 9.5 V14.5 " +
            "M13.6 11.9 C15.2 11.9 15.6 11.2 15.6 10.6 C15.6 10.0 15.2 9.5 13.6 9.6 " +
            "M13.6 11.9 C15.2 11.9 15.6 12.6 15.6 13.2 C15.6 13.6 15.2 14.5 13.6 14.4"
    ).toNodes()
    ImageVector.Builder(
        name = "AbLoop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = ringPath, stroke = SolidColor(Color.White), strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = letterA, stroke = SolidColor(Color.White), strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = letterB, stroke = SolidColor(Color.White), strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

/**
 * VR 头戴显示器图标（与播放器内一致：凸起头带 + 两侧镜片，全描边）。
 * home 模块不依赖 feature:player，故此处复制绘制，保证符号相同。
 */
private val VrHeadsetIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    val band = PathParser().parsePathString(
        "M5 9 C5 4.5 19 4.5 19 9 " +
            "M5 9 Q5 13 6.5 14 L6.5 9 Z " +
            "M19 9 Q19 13 17.5 14 L17.5 9 Z " +
            "M6.5 14 Q5.5 15 5 14 L5 9 Q5 13 6.5 14 Z " +
            "M17.5 14 Q18.5 15 19 14 L19 9 Q19 13 17.5 14 Z"
    ).toNodes()
    val frame = PathParser().parsePathString(
        "M7.5 9.5 A1.5 1.5 0 1 1 7.49 9.5 " +
            "M16.5 9.5 A1.5 1.5 0 1 1 16.49 9.5"
    ).toNodes()
    ImageVector.Builder(
        name = "VrHeadset",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = band, stroke = SolidColor(Color.White), strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = frame, stroke = SolidColor(Color.White), strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

/** 控制功能 id → 与播放器一致的图标。 */
private fun ctrlIcon(id: String): ImageVector = when (id) {
    "rotate" -> Icons.Rounded.ScreenRotation
    "ab_loop" -> AbLoopIcon
    "black_bar_crop" -> Icons.Rounded.Crop
    "lock" -> Icons.Rounded.LockOpen
    "screenshot" -> Icons.Rounded.PhotoCamera
    "long_press_speed" -> Icons.Rounded.Speed
    "pip" -> Icons.Rounded.PictureInPictureAlt
    "background_play" -> Icons.Rounded.HeadsetMic
    "sleep_timer" -> Icons.Rounded.Bedtime
    "media_info" -> Icons.Rounded.Info
    "vr" -> VrHeadsetIcon
    "bar_playback" -> Icons.Rounded.PlayCircleOutline
    "bar_speed" -> Icons.Rounded.Speed
    "bar_scale" -> Icons.Rounded.AspectRatio
    "bar_volume" -> Icons.AutoMirrored.Rounded.VolumeUp
    "bar_download" -> Icons.Rounded.ArrowDownward
    "bar_audio" -> Icons.Rounded.MusicNote
    "bar_subtitle" -> Icons.Rounded.Subtitles
    "bar_playlist" -> Icons.AutoMirrored.Rounded.ViewList
    else -> Icons.Rounded.Extension
}

/** 功能 id → 本地化名称。 */
@Composable
private fun ctrlName(id: String): String = stringResource(
    when (id) {
        "rotate" -> R.string.player_ctrl_name_rotate
        "ab_loop" -> R.string.player_ctrl_name_ab_loop
        "black_bar_crop" -> R.string.player_ctrl_name_black_bar_crop
        "lock" -> R.string.player_ctrl_name_lock
        "screenshot" -> R.string.player_ctrl_name_screenshot
        "long_press_speed" -> R.string.player_ctrl_name_long_press_speed
        "pip" -> R.string.player_ctrl_name_pip
        "background_play" -> R.string.player_ctrl_name_background_play
        "sleep_timer" -> R.string.player_ctrl_name_sleep_timer
        "media_info" -> R.string.player_ctrl_name_media_info
        "vr" -> R.string.player_ctrl_name_vr
        "bar_playback" -> R.string.player_ctrl_name_bar_playback
        "bar_speed" -> R.string.player_ctrl_name_bar_speed
        "bar_scale" -> R.string.player_ctrl_name_bar_scale
        "bar_volume" -> R.string.player_ctrl_name_bar_volume
        "bar_download" -> R.string.player_ctrl_name_bar_download
        "bar_audio" -> R.string.player_ctrl_name_bar_audio
        "bar_subtitle" -> R.string.player_ctrl_name_bar_subtitle
        "bar_playlist" -> R.string.player_ctrl_name_bar_playlist
        else -> R.string.player_ctrl_name_unknown
    },
)
