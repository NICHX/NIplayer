package com.nichx.niplayer.feature.home.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiSectionHeader
import com.nichx.niplayer.designsystem.components.NiSkeletonBox
import com.nichx.niplayer.designsystem.components.NiSkeletonLine
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiThumbCard
import com.nichx.niplayer.designsystem.components.NiTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("全部"),
    VIDEO("视频"),
    AUDIO("音频"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayHistoryScreen(
    onNavigateToPlayVideo: () -> Unit = {},
    viewModel: PlayHistoryViewModel = hiltViewModel(),
) {
    var activeFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedItem by remember { mutableStateOf<PlayHistoryEntity?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
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
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.playTime.time))
    }

    val hasHistory = allHistory.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayHistoryEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is PlayHistoryEvent.NavigateToPlayer -> onNavigateToPlayVideo()
                is PlayHistoryEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "播放历史",
                actions = {
                    if (hasHistory) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "清空历史",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!dataReady) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    userScrollEnabled = false,
                ) {
                    items(List(6) { it }) {
                        HistoryItemSkeleton()
                    }
                }
                return@Column
            }
            if (hasHistory) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        val count = when (filter) {
                            HistoryFilter.ALL -> allHistory.size
                            HistoryFilter.VIDEO -> videoHistories.size
                            HistoryFilter.AUDIO -> audioHistories.size
                        }
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text("${filter.label} ($count)") },
                        )
                    }
                }
            }

            if (displayHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NiEmptyState(
                        icon = Icons.Filled.History,
                        text = if (allHistory.isEmpty()) "暂无播放记录" else "没有匹配的记录",
                        hint = if (allHistory.isEmpty()) "播放视频或音乐后将自动记录" else "尝试切换筛选条件",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    grouped.forEach { (dateKey, items) ->
                        item(key = "header_$dateKey") {
                            NiSectionHeader(
                                title = formatDateGroup(dateKey),
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
                                onClick = { selectedItem = item },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedItem = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = item.videoName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
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
                        text = "继续播放",
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
                        text = "删除记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        NiConfirmDialog(
            title = "清空历史",
            text = "确定删除所有播放记录？此操作不可撤销。",
            onConfirm = {
                viewModel.clearAll()
                showDeleteAllDialog = false
            },
            onDismiss = { showDeleteAllDialog = false },
        )
    }
}

@Composable
private fun HistoryItem(
    item: PlayHistoryEntity,
    thumbPath: String?,
    onClick: () -> Unit,
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
        horizontal = true,
        subtitleText = formatPlayTime(item.playTime),
        mediaLabel = mediaTypeLabel(item.mediaType),
        squareCover = isAudio,
    )
    Spacer(Modifier.height(8.dp))
}

private fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.LOCAL_STORAGE -> "本地"
    MediaType.EXTERNAL_STORAGE -> "设备"
    MediaType.SMB_SERVER -> "SMB"
    MediaType.WEBDAV_SERVER -> "WebDAV"
    MediaType.QUICK_ACCESS -> "快捷"
    else -> "其他"
}

private fun formatPlayTime(date: Date): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(date)
}

private fun formatDateGroup(dateKey: String): String {
    // dateKey is yyyy-MM-dd
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
        Date(System.currentTimeMillis() - 86400000)
    )
    return when (dateKey) {
        today -> "今天"
        yesterday -> "昨天"
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
