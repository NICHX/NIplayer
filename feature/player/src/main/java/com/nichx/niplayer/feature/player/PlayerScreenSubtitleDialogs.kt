package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo


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
    sameDirSubtitles: List<String>,
    activeExternalSubtitle: String?,
    onSelectTrack: (Int) -> Unit,
    onSelectSameDirSubtitle: (String) -> Unit,
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

    // 当前字幕摘要（「轨道」行右侧回显）。外挂字幕生效时优先回显其文件名
    val autoSelectedTrack = if (selectedIndex == -1) {
        subtitleTracks.firstOrNull { it.isAutoSelected }
    } else {
        null
    }
    val trackSummary = when {
        activeExternalSubtitle != null -> activeExternalSubtitle
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
            sameDirSubtitles = sameDirSubtitles,
            activeExternalSubtitle = activeExternalSubtitle,
            onSelectTrack = onSelectTrack,
            onSelectSameDirSubtitle = onSelectSameDirSubtitle,
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

/** 字幕轨道选择二级 Dialog（关闭/自动/内嵌轨道/同目录字幕，选中项高亮）。 */
@Composable
internal fun SubtitleTrackDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    sameDirSubtitles: List<String>,
    activeExternalSubtitle: String?,
    onSelectTrack: (Int) -> Unit,
    onSelectSameDirSubtitle: (String) -> Unit,
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
    // 外挂字幕生效时，内嵌轨道项不参与高亮（两者互斥：选外挂会关闭内嵌）
    val embeddedSelectionActive = activeExternalSubtitle == null
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
        // 同目录字幕文件：选中后由 SubtitleEngine 外挂渲染（本地 / SMB / WebDAV 通用）
        sameDirSubtitles.forEach { name ->
            add(
                TrackOption(
                    label = name,
                    index = TRACK_INDEX_EXTERNAL,
                    description = stringResource(R.string.player_subtitle_same_dir),
                    externalFileName = name,
                )
            )
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
                val isSelected = if (option.externalFileName != null) {
                    activeExternalSubtitle == option.externalFileName
                } else {
                    embeddedSelectionActive && option.index == selectedIndex
                }
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
                        .clickable {
                            val external = option.externalFileName
                            if (external != null) onSelectSameDirSubtitle(external)
                            else onSelectTrack(option.index)
                        }
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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

/**
 * 字幕轨道列表项。
 *
 * [externalFileName] 非空表示这是同目录外挂字幕文件（走 SubtitleEngine 渲染），
 * 此时 [index] 无意义，取 [TRACK_INDEX_EXTERNAL] 占位。
 */
internal data class TrackOption(
    val label: String,
    val index: Int,
    val description: String,
    val externalFileName: String? = null,
)

/** 同目录外挂字幕项的占位索引（不参与内嵌轨道索引比较）。 */
private const val TRACK_INDEX_EXTERNAL = Int.MIN_VALUE
