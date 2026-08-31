package com.nichx.niplayer.feature.home.history

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nichx.niplayer.designsystem.components.NiGlassOverlay
import com.nichx.niplayer.designsystem.components.NiGlassOverlayKind
import com.nichx.niplayer.designsystem.components.NiGlassOverlayRequest
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import com.nichx.niplayer.designsystem.components.niGlassPanelSurfaceColor
import androidx.compose.animation.core.LinearEasing
import coil3.compose.AsyncImage
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.SyncConflictEntity
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiSectionHeader
import com.nichx.niplayer.designsystem.components.NiSkeletonBox
import com.nichx.niplayer.designsystem.components.NiSkeletonLine
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiThumbCard
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.sync.SyncUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter(@StringRes val labelRes: Int) {
        ALL(R.string.history_filter_all),
        VIDEO(R.string.history_filter_video),
        AUDIO(R.string.history_filter_audio),
    }

@Composable
fun PlayHistoryScreen(
    onNavigateToPlayVideo: (Boolean) -> Unit = {},
    initialFilterOrdinal: Int = 0,
    viewModel: PlayHistoryViewModel = hiltViewModel(),
) {
    var activeFilter by remember {
        mutableStateOf(HistoryFilter.entries.getOrElse(initialFilterOrdinal) { HistoryFilter.ALL })
    }
    var selectedItem by remember { mutableStateOf<PlayHistoryEntity?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    // 分组方式：false=按日期（默认），true=按文件夹聚合
    var groupByFolder by remember { mutableStateOf(false) }
    // 文件夹分组的展开状态（key 为存储源+目录，data class 的 equals/hashCode 稳定）
    val expandedFolders = remember { mutableStateMapOf<FolderKey, Boolean>() }
    val allHistory by viewModel.histories.collectAsStateWithLifecycle()
    val videoHistories by viewModel.videoHistories.collectAsStateWithLifecycle()
    val audioHistories by viewModel.audioHistories.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    val thumbnailUrls by viewModel.thumbnailUrls.collectAsStateWithLifecycle()

    val displayHistory = when (activeFilter) {
        HistoryFilter.ALL -> allHistory
        HistoryFilter.VIDEO -> videoHistories
        HistoryFilter.AUDIO -> audioHistories
    }

    val grouped = displayHistory.groupBy {
        SimpleDateFormat("yyyy-MM-dd", LocalConfiguration.current.locales[0]).format(Date(it.playTime.time))
    }

    val rootLabel = stringResource(R.string.play_history_root_folder)
    // 按文件夹聚合（storageId + 存储内父目录为分组键），组内按播放时间倒序，组间按最新播放时间倒序。
    val folderGroups = remember(groupByFolder, displayHistory, rootLabel) {
        if (!groupByFolder) emptyList()
        else displayHistory.groupBy { h ->
            FolderKey(h.storageId, folderDirOf(h.storagePath))
        }.map { (key, items) ->
            HistoryFolderGroup(
                key = key,
                displayName = key.folder.ifEmpty { rootLabel }.substringAfterLast('/'),
                items = items.sortedByDescending { it.playTime.time },
            )
        }.sortedByDescending { group -> group.items.maxOf { it.playTime.time } }
    }

    val hasHistory = allHistory.isNotEmpty()
    val messageController = LocalAppMessageController.current
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val syncConfig by viewModel.syncConfig.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    var showConflicts by remember { mutableStateOf(false) }
    var errorContent: String? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayHistoryEvent.Toast -> messageController.post(NiMessage.info(event.message))
                is PlayHistoryEvent.NavigateToPlayer -> onNavigateToPlayVideo(event.isAudio)
                is PlayHistoryEvent.ShowError -> messageController.post(NiMessage.error(event.message))
                else -> {}
            }
        }
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.play_history_title),
                actions = {
                    if (syncConfig.enabled) {
                        val isSyncing = syncState is SyncUiState.Syncing
                        val done = syncState as? SyncUiState.Done
                        val isError = done != null && !done.success
                        // 三态：就绪/成功=cloud_done、失败=cloud_off、同步中=旋转
                        val state = when {
                            isSyncing -> SyncUiState.Syncing
                            isError -> SyncUiState.Done(false, done?.message.orEmpty())
                            else -> SyncUiState.Idle
                        }
                        SyncIndicator(
                            state = state,
                            onSyncClick = { viewModel.syncNow() },
                            onErrorClick = { errorContent = done?.message },
                            successContentDescription = stringResource(R.string.play_history_sync_success),
                            idleContentDescription = stringResource(R.string.play_history_cloud_sync),
                        )
                    }
                    if (hasHistory) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.play_history_clear_history),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        // 满铺全屏：内容延伸到顶栏背后，滚动内容被顶栏真实渐进模糊（液态玻璃）。
        // 顶栏高度仅由滚动列表顶部 inset 让位，避免首项顶到状态栏（参照 HomeTabScreen）。
        val topInset = padding.calculateTopPadding()
        val bottomInset = padding.calculateBottomPadding()
        if (!dataReady) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = topInset, bottom = bottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                userScrollEnabled = false,
            ) {
                items(List(6) { it }) {
                    HistoryItemSkeleton()
                }
            }
            return@NiScaffold
        }
        if (displayHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset, bottom = bottomInset),
                contentAlignment = Alignment.Center,
            ) {
                NiEmptyState(
                    icon = Icons.Filled.History,
                    text = if (allHistory.isEmpty()) stringResource(R.string.play_history_empty_title)
                    else stringResource(R.string.play_history_no_match),
                    hint = if (allHistory.isEmpty()) stringResource(R.string.play_history_empty_hint)
                    else stringResource(R.string.play_history_no_match_hint),
                )
            }
            return@NiScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = topInset, bottom = bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (conflicts.isNotEmpty()) {
                item(key = "conflicts_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .clickable { showConflicts = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.play_history_conflicts_banner, conflicts.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (hasHistory) {
                item(key = "filter_chips") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HistoryFilter.entries.forEach { filter ->
                                val count = when (filter) {
                                    HistoryFilter.ALL -> allHistory.size
                                    HistoryFilter.VIDEO -> videoHistories.size
                                    HistoryFilter.AUDIO -> audioHistories.size
                                }
                                FilterChip(
                                    selected = activeFilter == filter,
                                    onClick = { activeFilter = filter },
                                    label = { Text(stringResource(filter.labelRes) + " ($count)") },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.history_group_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HistoryGroupToggle(
                                selectedFolderMode = groupByFolder,
                                onSelectDate = { groupByFolder = false },
                                onSelectFolder = { groupByFolder = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (groupByFolder) {
                folderGroups.forEach { group ->
                    val expanded = expandedFolders[group.key] ?: false
                    item(key = "folder_header_${group.key.stableKey}") {
                        FolderGroupHeader(
                            name = group.displayName,
                            count = group.items.size,
                            thumbPath = thumbnailUrls[group.items.first().url],
                            expanded = expanded,
                            onClick = { expandedFolders[group.key] = !expanded },
                        )
                    }
                    if (expanded) {
                        items(
                            items = group.items,
                            key = { it.id },
                        ) { item ->
                            HistoryItem(
                                item = item,
                                thumbPath = thumbnailUrls[item.url],
                                onClick = { viewModel.resumePlay(item) },
                                onLongClick = { selectedItem = item },
                            )
                        }
                    }
                }
            } else {
                grouped.forEach { (dateKey, items) ->
                    item(key = "header_$dateKey") {
                        NiSectionHeader(
                            title = formatDateGroup(dateKey, context),
                            count = items.size,
                            onClick = null,
                        )
                    }
                    items(
                        items = items,
                        key = { it.id },
                    ) { item ->
                        HistoryItem(
                            item = item,
                            thumbPath = thumbnailUrls[item.url],
                            onClick = { viewModel.resumePlay(item) },
                            onLongClick = { selectedItem = item },
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        val sheetId = "play_history_item_sheet_${item.id}"
        DisposableEffect(item.id) {
            onDispose { NiGlassOverlay.dismiss(sheetId) }
        }
        LaunchedEffect(sheetId, item.id) {
            NiGlassOverlay.show(
                NiGlassOverlayRequest(
                    id = sheetId,
                    kind = NiGlassOverlayKind.BottomSheet,
                    title = item.videoName,
                    onDismiss = { selectedItem = null },
                ) {
                    Column(modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedItem = null
                                    viewModel.resumePlay(item)
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.play_history_resume),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedItem = null
                                    viewModel.deleteHistory(item.id)
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.play_history_delete_record),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        }
    }

    if (showConflicts) {
        val sheetId = "play_history_conflicts"
        val conflictsTitle = stringResource(R.string.play_history_conflicts_title)
        DisposableEffect(Unit) {
            onDispose { NiGlassOverlay.dismiss(sheetId) }
        }
        LaunchedEffect(sheetId) {
            NiGlassOverlay.show(
                NiGlassOverlayRequest(
                    id = sheetId,
                    kind = NiGlassOverlayKind.BottomSheet,
                    title = conflictsTitle,
                    onDismiss = { showConflicts = false },
                ) {
                    Column(modifier = Modifier.padding(bottom = 32.dp)) {
                        Text(
                            text = stringResource(R.string.play_history_conflicts_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        conflicts.forEachIndexed { index, conflict ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                            }
                            ConflictItem(
                                conflict = conflict,
                                onKeepLocal = {
                                    viewModel.resolveConflictKeepLocal(conflict)
                                    if (conflicts.size == 1) showConflicts = false
                                },
                                onKeepRemote = {
                                    viewModel.resolveConflictKeepRemote(conflict)
                                    if (conflicts.size == 1) showConflicts = false
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    if (showDeleteAllDialog) {
        NiConfirmDialog(
            title = stringResource(R.string.play_history_clear_all),
            text = stringResource(R.string.play_history_clear_all_confirm),
            onConfirm = {
                viewModel.clearAll()
                showDeleteAllDialog = false
            },
            onDismiss = { showDeleteAllDialog = false },
        )
    }

    errorContent?.let { message ->
        val errorId = "play_history_sync_error"
        val errorTitle = stringResource(R.string.play_history_sync_failed)
        val errorDetail = message.ifBlank { stringResource(R.string.play_history_sync_error_detail) }
        DisposableEffect(errorId) {
            onDispose { NiGlassOverlay.dismiss(errorId) }
        }
        LaunchedEffect(errorId) {
            NiGlassOverlay.show(
                NiGlassOverlayRequest(
                    id = errorId,
                    kind = NiGlassOverlayKind.Dialog,
                    title = errorTitle,
                    onDismiss = { errorContent = null },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            text = errorDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { errorContent = null }) {
                                Text(stringResource(R.string.close))
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                errorContent = null
                                viewModel.syncNow()
                            }) {
                                Text(
                                    text = stringResource(R.string.retry),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun HistoryItem(
    item: PlayHistoryEntity,
    thumbPath: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val progress = if (item.videoDuration > 0)
        item.videoPosition.toFloat() / item.videoDuration.toFloat() else 0f
    val isAudio = MediaFileTypes.isAudioFile(item.videoName)

    NiThumbCard(
        title = item.videoName,
        durationText = "",
        thumbnailModel = thumbPath,
        progressFraction = progress,
        contentScale = if (isAudio) ContentScale.Fit else ContentScale.Crop,
        onClick = onClick,
        onLongClick = onLongClick,
        horizontal = true,
        subtitleText = formatPlayTime(item.playTime),
        mediaLabel = mediaTypeLabel(item.mediaType),
        squareCover = isAudio,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FolderGroupHeader(
    name: String,
    count: Int,
    thumbPath: String?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(niGlassPanelSurfaceColor())
            .border(NiGlassHairWidth, niGlassBorderColor(), RoundedCornerShape(14.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbPath != null) {
                AsyncImage(
                    model = thumbPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = if (expanded) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** 分组方式切换：液玻璃胶囊分段控件（按日期 / 按文件夹）。 */
@Composable
private fun HistoryGroupToggle(
    selectedFolderMode: Boolean,
    onSelectDate: () -> Unit,
    onSelectFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(niGlassPanelSurfaceColor())
            .border(NiGlassHairWidth, niGlassBorderColor(), RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        HistoryGroupToggleOption(
            text = stringResource(R.string.history_group_by_date),
            selected = !selectedFolderMode,
            onClick = onSelectDate,
        )
        HistoryGroupToggleOption(
            text = stringResource(R.string.history_group_by_folder),
            selected = selectedFolderMode,
            onClick = onSelectFolder,
        )
    }
}

@Composable
private fun RowScope.HistoryGroupToggleOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            )
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.LOCAL_STORAGE -> stringResource(R.string.storage_type_local)
    MediaType.EXTERNAL_STORAGE -> stringResource(R.string.storage_type_device)
    MediaType.SMB_SERVER -> "SMB"
    MediaType.WEBDAV_SERVER -> "WebDAV"
    MediaType.QUICK_ACCESS -> stringResource(R.string.storage_type_quick)
    else -> stringResource(R.string.storage_type_other)
}

private fun formatPlayTime(date: Date): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(date)
}

private fun formatDateGroup(dateKey: String, context: Context): String {
    // dateKey is yyyy-MM-dd
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
        Date(System.currentTimeMillis() - 86400000)
    )
    return when (dateKey) {
        today -> context.getString(R.string.play_history_today)
        yesterday -> context.getString(R.string.play_history_yesterday)
        else -> dateKey
    }
}

@Composable
private fun HistoryItemSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NiSkeletonBox(width = 64.dp, height = 56.dp, shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            NiSkeletonLine(widthFraction = 0.8f)
            Spacer(Modifier.height(6.dp))
            NiSkeletonLine(widthFraction = 0.5f)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SyncIndicator(
    state: SyncUiState,
    onSyncClick: () -> Unit,
    onErrorClick: () -> Unit,
    successContentDescription: String,
    idleContentDescription: String,
) {
    val isSyncing = state is SyncUiState.Syncing
    val isError = state is SyncUiState.Done && !state.success
    val isSuccess = !isSyncing && !isError
    // 同步中：同步图标绕中心持续旋转，隐喻"进行中"
    val rotation = rememberInfiniteTransition(label = "sync_rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sync_rotate",
    )
    // 状态配色：就绪/成功=绿色、失败=error、同步中=primary
    val stateColor = when {
        isSuccess -> Color(0xFF4CAF50)
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    // 图标：就绪/成功=cloud_done、失败=cloud_off、同步中=sync（旋转）
    val icon = when {
        isError -> Icons.Outlined.CloudOff
        isSyncing -> Icons.Outlined.Sync
        else -> Icons.Outlined.CloudDone
    }
    // 同步中不可点击；就绪可点击重新同步；失败可点击弹错误窗
    val onClick = when {
        isError -> onErrorClick
        else -> onSyncClick
    }
    IconButton(onClick = onClick, enabled = !isSyncing) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(stateColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isError) idleContentDescription else successContentDescription,
                tint = stateColor,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = if (isSyncing) rotation.value else 0f
                    },
            )
        }
    }
}

@Composable
private fun ConflictItem(
    conflict: SyncConflictEntity,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            text = conflict.videoName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.play_history_local),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.play_history_progress, formatPositionMs(conflict.localVideoPosition), formatPositionMs(conflict.localVideoDuration)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.play_history_updated_at, formatPlayTime(Date(conflict.localPlayTime))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.play_history_remote),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.play_history_progress, formatPositionMs(conflict.remoteVideoPosition), formatPositionMs(conflict.remoteVideoDuration)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.play_history_updated_at, formatPlayTime(Date(conflict.remoteUpdatedAt))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onKeepLocal) { Text(stringResource(R.string.play_history_keep_local)) }
            TextButton(onClick = onKeepRemote) { Text(stringResource(R.string.play_history_keep_remote)) }
        }
    }
}

private fun formatPositionMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** 文件夹分组键：存储源 + 存储内父目录。 */
private data class FolderKey(val storageId: Int?, val folder: String) {
    val stableKey: String get() = "${storageId ?: "local"}_$folder"
}

/** 按文件夹聚合的播放历史组。 */
private data class HistoryFolderGroup(
    val key: FolderKey,
    val displayName: String,
    val items: List<PlayHistoryEntity>,
)

/** 从存储内相对路径提取父目录；无目录（文件在存储根下）返回空串。 */
private fun folderDirOf(path: String?): String {
    if (path.isNullOrEmpty()) return ""
    val idx = path.lastIndexOf('/')
    return if (idx <= 0) "" else path.substring(0, idx)
}
