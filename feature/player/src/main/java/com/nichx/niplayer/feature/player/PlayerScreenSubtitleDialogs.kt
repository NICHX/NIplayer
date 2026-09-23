package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.player.kernel.SubtitleTrackInfo

/** 字幕菜单内部的页面。刻意**不**用嵌套 Dialog：见 [SubtitleManageDialog] 的说明。 */
private enum class SubtitleDialogPage { Menu, Tracks, Delay }

/**
 * 字幕管理（单窗口 + 内部翻页）。
 *
 * 布局：一个 Dialog，三页原地切换 —— 主菜单（5 个功能入口）、轨道列表、延迟调整。
 *
 * 为什么不用嵌套 Dialog（原实现把「轨道」和「延迟」各做成第二个 Dialog 窗口）：
 * 1. 两个窗口的层级取决于**谁后被 show**，父窗口一旦后创建就会把子窗口整个盖住，
 *    子菜单既看不见也点不到（子页面在本文件里靠组合顺序摆放，一旦有人给 `showTrackDialog`
 *    加初值或改成 rememberSaveable，两个窗口就会在同一次组合里创建而踩中这个坑）；
 * 2. 失败提示（OSD）画在播放页主窗口，被 Dialog 窗口挡住 —— 多一层窗口就多一层遮挡。
 *
 * 选中轨道后直接 [onDismiss] 关闭整个菜单：与音频轨道菜单一致，也让随后弹出的成功/失败
 * 提示不再被 Dialog 盖住。
 *
 * @param loadState 外挂字幕任务的进行状态，显示在页面顶部（进行中/失败原因）
 */
@Composable
internal fun SubtitleManageDialog(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    offsetMs: Long,
    sameDirSubtitles: List<String>,
    activeExternalSubtitle: String?,
    loadState: PlayerViewModel.SubtitleLoadState,
    onSelectTrack: (Int) -> Unit,
    onSelectSameDirSubtitle: (String) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAddExternal: () -> Unit,
    onSearch: () -> Unit,
    onOpenStyle: () -> Unit,
    onDismiss: () -> Unit,
) {
    // ViewModel 挂在播放页导航条目上，实例跨弹层存活：每次打开都从主菜单开始
    var page by remember { mutableStateOf(SubtitleDialogPage.Menu) }

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

    // 固定 maxHeight：三页共用同一尺寸，翻页时窗口高度不跳
    PlayerDialog(onDismiss = onDismiss, maxWidth = 360, maxHeight = 460) {
        when (page) {
            SubtitleDialogPage.Tracks -> {
                SubtitlePageTitle(
                    title = stringResource(R.string.player_subtitle_track),
                    onBack = { page = SubtitleDialogPage.Menu },
                )
                PlayerDialogDivider()
                SubtitleStatusRow(loadState)
                SubtitleTrackList(
                    subtitleTracks = subtitleTracks,
                    selectedIndex = selectedIndex,
                    sameDirSubtitles = sameDirSubtitles,
                    activeExternalSubtitle = activeExternalSubtitle,
                    onSelectTrack = { index ->
                        onSelectTrack(index)
                        onDismiss()
                    },
                    onSelectSameDirSubtitle = { name ->
                        onSelectSameDirSubtitle(name)
                        onDismiss()
                    },
                )
            }

            SubtitleDialogPage.Delay -> {
                SubtitlePageTitle(
                    title = stringResource(R.string.player_subtitle_delay),
                    onBack = { page = SubtitleDialogPage.Menu },
                )
                PlayerDialogDivider()
                SubtitleDelayControls(
                    offsetMs = offsetMs,
                    onAdjustOffset = onAdjustOffset,
                    onResetOffset = onResetOffset,
                )
            }

            SubtitleDialogPage.Menu -> {
                Text(
                    text = stringResource(R.string.player_subtitle),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    color = PlayerDialogColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                SubtitleStatusRow(loadState)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    SubtitleMenuItem(
                        icon = Icons.Rounded.Subtitles,
                        label = stringResource(R.string.player_subtitle_track),
                        summary = trackSummary,
                        onClick = { page = SubtitleDialogPage.Tracks },
                    )
                    SubtitleMenuItem(
                        icon = Icons.Rounded.Schedule,
                        label = stringResource(R.string.player_subtitle_delay),
                        summary = delaySummary,
                        onClick = { page = SubtitleDialogPage.Delay },
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
    }
}

/** 子页面标题行：返回箭头 + 标题。 */
@Composable
private fun SubtitlePageTitle(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.player_subtitle_back),
            tint = PlayerDialogColors.textPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(6.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = PlayerDialogColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 字幕任务状态行：进行中显示进度文案，失败显示原因。
 *
 * 这是「点了没反应」的直接解药 —— 载入是异步的（网络存储 1-3 秒），且失败原本只发 OSD，
 * 而 OSD 画在播放页主窗口、被本 Dialog 盖住。
 */
@Composable
private fun SubtitleStatusRow(loadState: PlayerViewModel.SubtitleLoadState) {
    val row = when (loadState) {
        PlayerViewModel.SubtitleLoadState.Idle -> return
        is PlayerViewModel.SubtitleLoadState.Loading -> SubtitleStatus(
            text = loadState.fileName?.let {
                stringResource(R.string.player_subtitle_loading_file, it)
            } ?: stringResource(R.string.player_subtitle_scanning),
            isError = false,
        )
        is PlayerViewModel.SubtitleLoadState.Failed -> SubtitleStatus(
            text = loadState.fileName?.let {
                stringResource(loadState.messageRes, it)
            } ?: stringResource(loadState.messageRes),
            isError = true,
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Text(
            text = row.text,
            color = if (row.isError) MaterialTheme.colorScheme.error
            else PlayerDialogColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

/** 状态行文案与是否错误态。 */
private data class SubtitleStatus(val text: String, val isError: Boolean)

/** 字幕轨道列表（关闭/自动/内嵌轨道/同目录字幕），选中项高亮。 */
@Composable
private fun SubtitleTrackList(
    subtitleTracks: List<SubtitleTrackInfo>,
    selectedIndex: Int,
    sameDirSubtitles: List<String>,
    activeExternalSubtitle: String?,
    onSelectTrack: (Int) -> Unit,
    onSelectSameDirSubtitle: (String) -> Unit,
) {
    val autoSelectedTrack = if (selectedIndex == -1) {
        subtitleTracks.firstOrNull { it.isAutoSelected }
    } else {
        null
    }
    // 外挂字幕生效时，内嵌轨道项不参与高亮（两者互斥：选外挂会关闭内嵌）
    val embeddedSelectionActive = activeExternalSubtitle == null
    val embeddedRows = buildList {
        add(
            EmbeddedTrackRow(
                label = stringResource(R.string.player_subtitle_off),
                index = TRACK_INDEX_OFF,
                description = stringResource(R.string.player_subtitle_none),
            )
        )
        add(
            EmbeddedTrackRow(
                label = stringResource(R.string.player_subtitle_auto),
                index = TRACK_INDEX_AUTO,
                description = autoSelectedTrack?.let {
                    stringResource(R.string.player_subtitle_auto_used, it.label)
                } ?: stringResource(R.string.player_subtitle_auto_by_language),
            )
        )
        subtitleTracks.forEach { track ->
            add(
                EmbeddedTrackRow(
                    label = track.label,
                    index = track.index,
                    description = stringResource(R.string.player_subtitle_embedded),
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        embeddedRows.forEach { row ->
            TrackRow(
                label = row.label,
                description = row.description,
                isSelected = embeddedSelectionActive && row.index == selectedIndex,
                onClick = { onSelectTrack(row.index) },
            )
        }

        // 同目录字幕：选中后由 SubtitleEngine 外挂渲染（本地 / SMB / WebDAV 通用）
        if (sameDirSubtitles.isNotEmpty()) {
            Text(
                text = stringResource(R.string.player_subtitle_same_dir),
                color = PlayerDialogColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
            )
            sameDirSubtitles.forEach { name ->
                TrackRow(
                    label = name,
                    description = null,
                    isSelected = activeExternalSubtitle == name,
                    onClick = { onSelectSameDirSubtitle(name) },
                )
            }
        }
    }
}

/** 内嵌字幕列表项（含两个特殊项「关闭」「自动」）。 */
private data class EmbeddedTrackRow(
    val label: String,
    val index: Int,
    val description: String,
)

/** 轨道列表的一行：选中圆点 + 名称（+ 可选的次级说明）。 */
@Composable
private fun TrackRow(
    label: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    val outlineVariant = PlayerDialogColors.divider
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isSelected) primary else outlineVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isSelected) primary else onSurface,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                // 同目录字幕文件名可能很长：不截断，可横向手动滑动看全名
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
            if (description != null && description.isNotEmpty()) {
                Text(
                    text = description,
                    color = onSurface.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** 字幕延迟调整（-1s…+1s 步进 + 重置）。 */
@Composable
private fun SubtitleDelayControls(
    offsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
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
                        color = if (delta == 0L) onSurface.copy(alpha = 0.5f) else primary,
                    )
                }
            }
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
                // 当前字幕文件名可能很长：限宽 + 可横向滑动，避免把左侧标题挤没
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .widthIn(max = 150.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 4.dp),
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

/** 「关闭」字幕的轨道索引（与 [com.nichx.niplayer.player.kernel.NxPlayer.selectSubtitleTrack] 约定一致）。 */
private const val TRACK_INDEX_OFF = -2

/** 「自动」字幕的轨道索引。 */
private const val TRACK_INDEX_AUTO = -1
