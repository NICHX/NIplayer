package com.nichx.niplayer.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.nichx.niplayer.player.kernel.PlaylistItem
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo


// NI_ACCENT / NI_ACCENT_DARK 已移除，统一使用 MaterialTheme.colorScheme.primary

@Composable
internal fun MoreMenuDialog(
    onDismiss: () -> Unit,
    /** 更多菜单中的动作列表（已按用户自定义过滤 + 排序，且与 HUD 配置同步）。 */
    actions: List<MoreAction>,
) {
    val onSurface = PlayerDialogColors.textPrimary
    // scrollable = true：把所有功能放进「更多」时项很多，容器在限高内滚动，避免内容超出窗口被裁切。
    PlayerDialog(onDismiss = onDismiss, maxWidth = 340, scrollable = true) {
        Text(
            text = stringResource(R.string.player_more),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()
        Spacer(Modifier.height(4.dp))
        actions.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 3.dp),
            ) {
                // 固定 3 等份槽位，保证各行的图标/项目落在同一列，末行项数不足也不错位
                repeat(3) { i ->
                    val action = row.getOrNull(i)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (action != null) {
                            MoreMenuItem(
                                icon = action.icon,
                                label = action.label,
                                enabled = action.enabled,
                                onClick = action.onClick,
                                isActive = action.isActive,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class MoreAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isActive: Boolean = false,
)

@Composable
internal fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = PlayerDialogColors.textSecondary
    val tint = when {
        isActive -> primary
        enabled -> primary
        else -> onSurfaceVariant.copy(alpha = 0.38f)
    }
    val bg = when {
        isActive -> primary.copy(alpha = 0.28f)
        enabled -> primary.copy(alpha = 0.10f)
        else -> PlayerDialogColors.divider
    }
    val textColor = when {
        isActive -> primary
        enabled -> PlayerDialogColors.textPrimary
        else -> onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isActive) Modifier.border(
                    1.5.dp,
                    primary.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp),
                ) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AbLoopDialog(
    abLoopA: Long?,
    abLoopB: Long?,
    durationMs: Long,
    positionMs: Long,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isActive = abLoopA != null && abLoopB != null && abLoopB > abLoopA
    val aSet = abLoopA != null
    val posFormatted = formatDuration(positionMs)
    val aFormatted = abLoopA?.let { formatDuration(it) } ?: stringResource(R.string.player_ab_loop_not_set)
    val bFormatted = abLoopB?.let { formatDuration(it) } ?: stringResource(R.string.player_ab_loop_not_set)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val outlineVariant = PlayerDialogColors.divider
        val onSurfaceVariant = PlayerDialogColors.textSecondary
        val surfaceVariant = PlayerDialogColors.background
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 280.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {

                // 标题行：图标 + 标题 + 当前播放时间
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AbLoopIcon,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFFFFAB40) else onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_ab_loop_title),
                        color = PlayerDialogColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = posFormatted,
                        color = PlayerDialogColors.textPrimary.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(16.dp))

                // A/B 时间显示：两张等宽玻璃卡片
                if (durationMs > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AbLoopPointCard(
                            label = stringResource(R.string.player_ab_loop_point_a),
                            time = if (aSet) aFormatted else stringResource(R.string.player_ab_loop_not_set),
                            accent = Color(0xFFFFAB40),
                            set = aSet,
                            modifier = Modifier.weight(1f),
                        )
                        AbLoopPointCard(
                            label = stringResource(R.string.player_ab_loop_point_b),
                            time = if (abLoopB != null) bFormatted else stringResource(R.string.player_ab_loop_not_set),
                            accent = Color(0xFFFF5252),
                            set = abLoopB != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                    Spacer(Modifier.height(12.dp))

                    // 进度条
                    val aFrac = (abLoopA?.toFloat()?.div(durationMs) ?: 0f).coerceIn(0f, 1f)
                    val bFrac = (abLoopB?.toFloat()?.div(durationMs) ?: 0f).coerceIn(0f, 1f)
                    val posFrac = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val r = CornerRadius(h / 2, h / 2)

                            drawRoundRect(
                                color = surfaceVariant,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                cornerRadius = r,
                            )

                            if (isActive) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFFFFAB40), Color(0xFFFFAB40), Color(0xFFFF5252), Color(0xFFFF5252)),
                                    ),
                                    topLeft = Offset(aFrac * w, 0f),
                                    size = Size((bFrac - aFrac).coerceAtLeast(2f) * w, h),
                                    cornerRadius = r,
                                )
                            } else if (aSet) {
                                drawRoundRect(
                                    color = Color(0xFFFFAB40).copy(alpha = 0.5f),
                                    topLeft = Offset(aFrac * w, 0f),
                                    size = Size(w * (1f - aFrac), h),
                                    cornerRadius = r,
                                )
                            }

                            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(posFrac * w, h / 2f))
                            drawCircle(color = Color(0xFF2095F4), radius = 2.dp.toPx(), center = Offset(posFrac * w, h / 2f))
                        }
                    }

                Spacer(Modifier.height(16.dp))

                // 操作按钮：统一药丸玻璃样式
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AbLoopActionPill(
                        label = if (aSet) stringResource(R.string.player_ab_loop_a_value, aFormatted) else stringResource(R.string.player_ab_loop_set_a, posFormatted),
                        accent = Color(0xFFFFAB40),
                        enabled = !aSet,
                        modifier = Modifier.weight(1f),
                        onClick = onSetPointA,
                    )
                    AbLoopActionPill(
                        label = if (abLoopB != null) stringResource(R.string.player_ab_loop_b_value, bFormatted) else stringResource(R.string.player_ab_loop_set_b, posFormatted),
                        accent = Color(0xFFFF5252),
                        enabled = aSet && abLoopB == null,
                        modifier = Modifier.weight(1f),
                        onClick = onSetPointB,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 状态提示 + 清除按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isActive -> Color(0xFFFFAB40).copy(alpha = 0.08f)
                                    aSet -> Color(0xFFFFAB40).copy(alpha = 0.05f)
                                    else -> surfaceVariant
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = when {
                                isActive -> stringResource(R.string.player_ab_loop_active, aFormatted, bFormatted)
                                aSet -> stringResource(R.string.player_ab_loop_a_set_prompt)
                                else -> stringResource(R.string.player_ab_loop_b_set_prompt)
                            },
                            fontSize = 12.sp,
                            color = when {
                                isActive -> Color(0xFFFFAB40)
                                aSet -> Color(0xFFFFAB40)
                                else -> onSurfaceVariant
                            },
                        )
                    }

                    if (aSet) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onClearAbLoop,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.player_ab_loop_clear), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // 快速操作提示
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.player_ab_loop_quick_hint),
                        color = onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

/** A-B 循环弹窗的端点卡片：A/B 起止时间的高亮玻璃卡片。 */
@Composable
internal fun AbLoopPointCard(
    label: String,
    time: String,
    accent: Color,
    set: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (set) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f))
            .border(
                0.5.dp,
                if (set) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (set) accent else Color.White.copy(alpha = 0.25f)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = PlayerDialogColors.textSecondary,
                fontSize = 11.sp,
            )
            Text(
                text = time,
                color = if (set) accent else PlayerDialogColors.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** A-B 循环弹窗的操作按钮：统一药丸玻璃样式，未启用时置灰。 */
@Composable
internal fun AbLoopActionPill(
    label: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = if (enabled) accent else PlayerDialogColors.textSecondary.copy(alpha = 0.6f)
    val bg = if (enabled) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
    val borderColor = if (enabled) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(bg)
            .border(0.5.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PlaylistDialog(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    onPlayAtIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.player_episode_list, currentIndex + 1, playlist.size),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((playlist.size.coerceAtMost(8) * 52).dp),
                ) {
                    itemsIndexed(playlist) { index, item ->
                        val isCurrent = index == currentIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable { onPlayAtIndex(index) }
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) primary
                                else onSurface.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(28.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.fileName,
                                color = if (isCurrent) primary else onSurface,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookmarkListDialog(
    bookmarks: List<VideoBookmarkEntity>,
    onSeek: (Long) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.player_bookmark_list, bookmarks.size),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                if (bookmarks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_bookmark_empty),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        color = onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((bookmarks.size.coerceAtMost(8) * 52).dp),
                    ) {
                        itemsIndexed(bookmarks) { _, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSeek(item.positionMs) }
                                    .padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.label?.takeIf { it.isNotBlank() } ?: formatDuration(item.positionMs),
                                    color = onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.label != null) {
                                    Text(
                                        text = formatDuration(item.positionMs),
                                        color = onSurface.copy(alpha = 0.4f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(item.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.player_delete_bookmark),
                                        tint = onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
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

/**
 * 字幕管理（主菜单）。
 *
 * 布局优化：主菜单保持紧凑（仅 5 个功能入口行），把占面积最大的「轨道列表」和
 * 「延迟调整」收进二级 Dialog（点对应行弹层），「外挂/搜索/样式」继续走原有回调跳转。
 * 当前选项在行右侧以摘要回显，点击即进入对应二级弹层。
 */
@Composable
internal fun SubtitleManageDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    offsetMs: Long,
    onSelectTrack: (Int) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAddExternal: () -> Unit,
    onSearch: () -> Unit,
    onOpenStyle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    // 二级弹层开关
    var showTrackDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }

    // 当前字幕摘要（「轨道」行右侧回显）
    val autoSelectedTrack = if (selectedIndex == -1) {
        subtitleTracks.firstOrNull { it.isAutoSelected }
    } else {
        null
    }
    val trackSummary = when {
        selectedIndex == -2 -> stringResource(R.string.player_subtitle_none)
        selectedIndex == -1 -> autoSelectedTrack?.let {
            stringResource(R.string.player_subtitle_auto_used, it.label)
        } ?: stringResource(R.string.player_subtitle_auto_by_language)
        else -> subtitleTracks.firstOrNull { it.index == selectedIndex }?.label
            ?: stringResource(R.string.player_subtitle_none)
    }
    // 当前延迟摘要：正 → "+Xms"；负 → "-Xms"；0 → "0ms"
    val delaySummary = when {
        offsetMs < 0 -> "${offsetMs}ms"
        offsetMs > 0 -> "+${offsetMs}ms"
        else -> "0ms"
    }

    if (showTrackDialog) {
        SubtitleTrackDialog(
            subtitleTracks = subtitleTracks,
            selectedIndex = selectedIndex,
            onSelectTrack = onSelectTrack,
            onDismiss = { showTrackDialog = false },
        )
    }
    if (showDelayDialog) {
        SubtitleDelayDialog(
            offsetMs = offsetMs,
            onAdjustOffset = onAdjustOffset,
            onResetOffset = onResetOffset,
            onDismiss = { showDelayDialog = false },
        )
    }

    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 560) {
        Text(
            text = stringResource(R.string.player_subtitle),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        PlayerDialogDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            SubtitleMenuItem(
                icon = Icons.Rounded.Subtitles,
                label = stringResource(R.string.player_subtitle_track),
                summary = trackSummary,
                onClick = { showTrackDialog = true },
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.Schedule,
                label = stringResource(R.string.player_subtitle_delay),
                summary = delaySummary,
                onClick = { showDelayDialog = true },
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.FolderOpen,
                label = stringResource(R.string.player_subtitle_external),
                onClick = onAddExternal,
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.player_subtitle_search),
                onClick = onSearch,
            )
            SubtitleMenuItem(
                icon = Icons.Rounded.Palette,
                label = stringResource(R.string.player_subtitle_style),
                onClick = onOpenStyle,
            )
        }
    }
}

/** 字幕主菜单的紧凑功能行：图标 + 标题 + 可选摘要 + 右侧箭头。 */
@Composable
internal fun SubtitleMenuItem(
    icon: ImageVector,
    label: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (summary != null) {
            Text(
                text = summary,
                color = onSurface.copy(alpha = 0.4f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 字幕轨道选择二级 Dialog（关闭/自动/内嵌轨道列表，选中项高亮）。 */
@Composable
internal fun SubtitleTrackDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    val outlineVariant = PlayerDialogColors.divider
    val autoSelectedTrack = if (selectedIndex == -1) {
        subtitleTracks.firstOrNull { it.isAutoSelected }
    } else {
        null
    }
    val trackItems = buildList<TrackOption> {
        add(TrackOption(stringResource(R.string.player_subtitle_off), -2, stringResource(R.string.player_subtitle_none)))
        add(
            TrackOption(
                stringResource(R.string.player_subtitle_auto),
                -1,
                autoSelectedTrack?.let { stringResource(R.string.player_subtitle_auto_used, it.label) }
                    ?: stringResource(R.string.player_subtitle_auto_by_language),
            )
        )
        subtitleTracks.forEach { track ->
            add(TrackOption(track.label, track.index, stringResource(R.string.player_subtitle_embedded)))
        }
    }

    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 460) {
        Text(
            text = stringResource(R.string.player_subtitle_track),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            trackItems.forEach { option: TrackOption ->
                val isSelected = option.index == selectedIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) primary.copy(alpha = 0.08f)
                            else Color.Transparent
                        )
                        .clickable { onSelectTrack(option.index) }
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) primary
                                else outlineVariant
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = option.label,
                            color = if (isSelected) primary else onSurface,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = option.description,
                            color = onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 字幕延迟调整二级 Dialog（-1s…+1s 步进 + 重置）。 */
@Composable
internal fun SubtitleDelayDialog(
    offsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 260) {
        Text(
            text = stringResource(R.string.player_subtitle_delay),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()

        Column(modifier = Modifier.fillMaxWidth()) {
            // 当前偏移值 + 内嵌字幕 STUB 提示（仅外挂字幕生效）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_subtitle_external_only),
                    color = onSurface.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (offsetMs > 0) "+${offsetMs}ms" else "${offsetMs}ms",
                        color = primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val delayActions = listOf(
                    -1000L to "-1s",
                    -500L to "-0.5s",
                    -100L to "-0.1s",
                    0L to stringResource(R.string.player_subtitle_reset),
                    100L to "+0.1s",
                    500L to "+0.5s",
                    1000L to "+1s",
                )
                delayActions.forEach { (delta, label) ->
                    TextButton(
                        onClick = {
                            if (delta == 0L) onResetOffset()
                            else onAdjustOffset(delta)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = label,
                            fontSize = if (delta == 0L) 12.sp else 11.sp,
                            fontWeight = if (delta == 0L) FontWeight.Bold else FontWeight.Medium,
                            color = if (delta == 0L) onSurface.copy(alpha = 0.5f)
                                else primary,
                        )
                    }
                }
            }
        }
    }
}

internal data class TrackOption(
    val label: String,
    val index: Int,
    val description: String,
)

@Composable
internal fun MediaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = PlayerDialogColors.textSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = PlayerDialogColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SpeedMenuDialog(
    speedIndex: Int,
    onSelectPreset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, scrollable = false) {
        Text(
            text = stringResource(R.string.player_speed_menu_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()
        Spacer(Modifier.height(8.dp))
        SPEED_LABELS.chunked(4).forEachIndexed { rowIdx, rowItems ->
            val baseIndex = rowIdx * 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEachIndexed { colIdx, label ->
                    val index = baseIndex + colIdx
                    val selected = index == speedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .clickable { onSelectPreset(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) primary else onSurface,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
