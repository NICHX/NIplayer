package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nichx.niplayer.datastore.PlayerControlEntry
import com.nichx.niplayer.datastore.PlayerControlLayout
import com.nichx.niplayer.datastore.PlayerControlOrientation
import com.nichx.niplayer.datastore.PlayerControlSurface
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.feature.home.R

/**
 * 控制栏自定义子页面：横屏 / 竖屏两个 Tab 各自独立布局；Tab 内用长按拖放调整。
 *
 * 布局持久化到 [PlayerControlLayout]（按屏幕方向分桶），播放器按当前方向读取，实时同步。
 */
@Composable
fun PlayerControlCustomizeScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val orientation =
        if (tab == 0) PlayerControlOrientation.PORTRAIT else PlayerControlOrientation.LANDSCAPE
    var resetTick by remember { mutableIntStateOf(0) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.player_ctrl_customize),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.player_ctrl_customize_instruction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(10.dp))
            OrientationTabBar(
                selected = tab,
                options = listOf(
                    stringResource(R.string.player_orientation_portrait),
                    stringResource(R.string.player_orientation_landscape),
                ),
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.player_ctrl_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    PlayerControlLayout.reset(orientation)
                    resetTick++
                }) {
                    Text(stringResource(R.string.player_ctrl_reset))
                }
            }
            Spacer(Modifier.height(4.dp))
            ControlDragEditor(
                orientation = orientation,
                resetTick = resetTick,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/** 胶囊式分段 Tab 栏（替代 Material 默认下划线样式，更贴合应用的圆形控件风格）。 */
@Composable
private fun OrientationTabBar(
    selected: Int,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 单个功能拖拽块的高度（含间距），用于计算插入位。 */
private val ChipStep = 48.dp

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

/** 控制功能 id → 与播放器一致的图标。 */
private fun ctrlIcon(id: String): ImageVector = when (id) {
    "rotate" -> Icons.Rounded.ScreenRotation
    "ab_loop" -> AbLoopIcon
    "black_bar_crop" -> Icons.Rounded.Crop
    "lock" -> Icons.Rounded.LockOpen
    "screenshot" -> Icons.Rounded.PhotoCamera
    "long_press_speed" -> Icons.Rounded.Speed
    "pip" -> Icons.Rounded.PictureInPictureAlt
    "sleep_timer" -> Icons.Rounded.Bedtime
    "media_info" -> Icons.Rounded.Info
    else -> Icons.Rounded.Bookmark // bookmarks
}

/** 拖放式布局编辑器：左列 / 右列 / 更多 三栏，长按某功能拖动到新位置（可跨栏 / 栏内排序）。 */
@Composable
private fun ControlDragEditor(
    orientation: PlayerControlOrientation,
    resetTick: Int,
    modifier: Modifier = Modifier,
) {
    var entries by remember(orientation, resetTick) {
        // 移除显隐开关后，布局不再暴露可见性；把历史遗留的隐藏项一并置为可见，保证不会永久隐藏。
        PlayerControlLayout.ALL_IDS.forEachIndexed { i, id ->
            val e = PlayerControlLayout.loadEntry(id, i, orientation)
            if (!e.visible) {
                PlayerControlLayout.saveEntry(id, e.surface, true, e.order, orientation)
            }
        }
        mutableStateOf(
            PlayerControlLayout.ALL_IDS.mapIndexed { i, id ->
                PlayerControlLayout.loadEntry(id, i, orientation).copy(visible = true)
            },
        )
    }
    fun persist(e: PlayerControlEntry) =
        PlayerControlLayout.saveEntry(e.id, e.surface, e.visible, e.order, orientation)

    val density = LocalDensity.current
    val stepPx = with(density) { ChipStep.toPx() }
    val ghostWidthPx = with(density) { 110.dp.toPx() }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var hoverSurface by remember { mutableStateOf<PlayerControlSurface?>(null) }
    var hoverIndex by remember { mutableIntStateOf(0) }
    var editorTopLeft by remember { mutableStateOf(Offset.Zero) }
    val columnBounds = remember { mutableStateMapOf<PlayerControlSurface, Rect>() }
    val columnHeaderH = remember { mutableStateMapOf<PlayerControlSurface, Float>() }

    fun colCount(surface: PlayerControlSurface): Int =
        entries.count { it.surface == surface && it.id != draggingId }

    fun computeHover(p: Offset) {
        var surf: PlayerControlSurface? = null
        var idx = 0
        PlayerControlLayout.ALL_SURFACES.forEach { s ->
            val b = columnBounds[s] ?: return@forEach
            if (b.contains(p)) {
                val contentTop = b.top + (columnHeaderH[s] ?: 0f)
                val count = colCount(s)
                val raw = ((p.y - contentTop) / stepPx)
                idx = if (raw.isNaN() || raw < 0) 0 else raw.toInt().coerceIn(0, count)
                surf = s
            }
        }
        hoverSurface = surf
        hoverIndex = idx
    }

    fun cancelDrag() {
        draggingId = null
        hoverSurface = null
        hoverIndex = 0
    }

    fun commitDrop(id: String) {
        val target = hoverSurface
        val moving = entries.firstOrNull { it.id == id }
        if (moving == null || target == null) {
            cancelDrag()
            return
        }
        val rest = entries.filter { it.id != id }
        val bySurf = rest.groupBy { it.surface }
        val result = mutableListOf<PlayerControlEntry>()
        var order = 0
        PlayerControlLayout.ALL_SURFACES.forEach { s ->
            val list = bySurf[s]?.sortedBy { it.order }?.toMutableList() ?: mutableListOf()
            if (s == target) {
                val idx = hoverIndex.coerceIn(0, list.size)
                list.add(idx, moving.copy(surface = s))
            }
            list.forEach { result.add(it.copy(order = order++)) }
        }
        result.forEach(::persist)
        entries = result
        draggingId = null
        hoverSurface = null
        hoverIndex = 0
    }

    Box(modifier = modifier.onGloballyPositioned { editorTopLeft = it.positionInRoot() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerControlLayout.ALL_SURFACES.forEach { surface ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { columnBounds[surface] = it.boundsInRoot() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ColumnHeader(
                        surface = surface,
                        count = entries.count { it.surface == surface },
                        onHeight = { columnHeaderH[surface] = it },
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        var shown = 0
                        entries.filter { it.surface == surface }.sortedBy { it.order }
                            .forEach { entry ->
                                val collapsed = entry.id == draggingId
                                // 被拖拽项渲染为高度 0，保留其 pointerInput（否则拖动会被中断），由浮层展示影像
                                if (!collapsed && surface == hoverSurface && hoverIndex == shown) InsertionGap()
                                if (!collapsed) shown++
                                DragChip(
                                    entry = entry,
                                    collapsed = collapsed,
                                    onDragStart = { start ->
                                        draggingId = entry.id
                                        dragPos = start
                                        computeHover(start)
                                    },
                                    onDragMove = { p ->
                                        dragPos = p
                                        computeHover(p)
                                    },
                                    onDragEnd = { id -> commitDrop(id) },
                                    onDragCancel = ::cancelDrag,
                                )
                            }
                        if (surface == hoverSurface && hoverIndex == shown) InsertionGap()
                    }
                }
            }
        }

        // 拖拽中的浮动影像
        draggingId?.let { id ->
            val e = entries.firstOrNull { it.id == id } ?: return@let
            val x = dragPos.x - editorTopLeft.x - ghostWidthPx / 2f
            val y = dragPos.y - editorTopLeft.y
            Box(
                Modifier
                    .offset { IntOffset(x.toInt(), y.toInt()) }
                    .width(110.dp)
                    .zIndex(10f),
            ) {
                ChipCard(entry = e, ghost = true)
            }
        }
    }
}

/** 单个可拖拽的功能块：实际控件图标 + 名称。 */
@Composable
private fun DragChip(
    entry: PlayerControlEntry,
    collapsed: Boolean = false,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (String) -> Unit,
    onDragCancel: () -> Unit,
) {
    var chipRoot by remember(entry.id) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (collapsed) 0.dp else 48.dp)
            .onGloballyPositioned { chipRoot = it.positionInRoot() }
            .pointerInput(entry.id) {
                var total = Offset.Zero
                var start = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        total = Offset.Zero
                        start = chipRoot + local
                        onDragStart(start)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                        onDragMove(start + total)
                    },
                    onDragEnd = { onDragEnd(entry.id) },
                    onDragCancel = { onDragCancel() },
                )
            },
    ) {
        if (!collapsed) ChipCard(entry = entry, ghost = false)
    }
}

/** 功能块视觉（普通 / 拖拽浮动影像通用）：控件图标 + 名称。 */
@Composable
private fun ChipCard(
    entry: PlayerControlEntry,
    ghost: Boolean,
) {
    val tint = if (ghost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ctrlIcon(entry.id),
            contentDescription = ctrlName(entry.id),
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = ctrlName(entry.id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 栏标题：位置名 + 数量。 */
@Composable
private fun ColumnScope.ColumnHeader(
    surface: PlayerControlSurface,
    count: Int,
    onHeight: (Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeight(it.size.height.toFloat()) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = ctrlSurfaceLabel(surface),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.player_ctrl_group_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 插入位置指示线。 */
@Composable
private fun InsertionGap() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/** 功能 id → 本地化名称。 */
@Composable
internal fun ctrlName(id: String): String = stringResource(
    when (id) {
        "rotate" -> R.string.player_ctrl_name_rotate
        "ab_loop" -> R.string.player_ctrl_name_ab_loop
        "black_bar_crop" -> R.string.player_ctrl_name_black_bar_crop
        "lock" -> R.string.player_ctrl_name_lock
        "screenshot" -> R.string.player_ctrl_name_screenshot
        "long_press_speed" -> R.string.player_ctrl_name_long_press_speed
        "pip" -> R.string.player_ctrl_name_pip
        "sleep_timer" -> R.string.player_ctrl_name_sleep_timer
        "media_info" -> R.string.player_ctrl_name_media_info
        "bookmarks" -> R.string.player_ctrl_name_bookmarks
        else -> R.string.player_ctrl_name_bookmarks
    },
)

/** 控制面 → 本地化标签。 */
@Composable
internal fun ctrlSurfaceLabel(surface: PlayerControlSurface): String = when (surface) {
    PlayerControlSurface.LEFT -> stringResource(R.string.player_ctrl_side_left)
    PlayerControlSurface.RIGHT -> stringResource(R.string.player_ctrl_side_right)
    PlayerControlSurface.MORE -> stringResource(R.string.player_ctrl_side_more)
}