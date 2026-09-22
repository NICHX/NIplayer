package com.nichx.niplayer.feature.home.history

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiSectionHeader
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.sync.SyncUiState
import java.text.SimpleDateFormat
import java.util.Date

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
    var errorContent: String? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayHistoryEvent.Toast -> messageController.post(NiMessage.info(event.message))
                is PlayHistoryEvent.NavigateToPlayer -> onNavigateToPlayVideo(event.isAudio)
                is PlayHistoryEvent.ShowError -> messageController.post(NiMessage.error(event.message))
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
