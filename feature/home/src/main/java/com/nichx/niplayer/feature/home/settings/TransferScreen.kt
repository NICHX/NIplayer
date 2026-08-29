package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/** 传输管理中心的双 tab。 */
enum class TransferTab { DOWNLOAD, UPLOAD }

/**
 * 统一「传输管理」中心页。
 *
 * 以双 tab（下载 / 上传）聚合 [DownloadManager] 与 [UploadManager] 两个并行引擎，
 * 替代原先单独的下载管理页入口。上传任务由 [UploadManager] 在 App 级作用域后台调度，
 * 切出本页或返回存储浏览页后仍继续执行。
 */
@Composable
fun TransferScreen(
    onBack: () -> Unit = {},
    onPlayVideo: (Boolean) -> Unit = {},
    onNavigateToImageViewer: () -> Unit = {},
    downloadViewModel: DownloadManagerViewModel = hiltViewModel(),
    uploadViewModel: UploadManagerViewModel = hiltViewModel(),
) {
    var tab by remember { mutableStateOf(TransferTab.DOWNLOAD) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.transfer_manager_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.download_manager_more),
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.transfer_clear_completed)) },
                                onClick = {
                                    showOverflowMenu = false
                                    when (tab) {
                                        TransferTab.DOWNLOAD -> downloadViewModel.removeCompleted()
                                        TransferTab.UPLOAD -> uploadViewModel.clearCompleted()
                                    }
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.transfer_clear_failed)) },
                                onClick = {
                                    showOverflowMenu = false
                                    when (tab) {
                                        TransferTab.DOWNLOAD -> downloadViewModel.clearFailed()
                                        TransferTab.UPLOAD -> uploadViewModel.clearFailed()
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            TransferTabSwitcher(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = padding.calculateTopPadding() + 8.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    TransferTab.DOWNLOAD -> DownloadManagerTab(
                        viewModel = downloadViewModel,
                        onPlayVideo = onPlayVideo,
                        onNavigateToImageViewer = onNavigateToImageViewer,
                        // 顶栏 inset 已由上方 Column 顶部偏移承担，列表从 tab 下方开始滚动
                        topPadding = 0.dp,
                    )
                    TransferTab.UPLOAD -> UploadManagerTab(viewModel = uploadViewModel)
                }
            }
        }
    }
}

@Composable
private fun TransferTabSwitcher(
    selected: TransferTab,
    onSelect: (TransferTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransferTabPill(
            label = stringResource(R.string.transfer_tab_download),
            selected = selected == TransferTab.DOWNLOAD,
            onClick = { onSelect(TransferTab.DOWNLOAD) },
        )
        TransferTabPill(
            label = stringResource(R.string.transfer_tab_upload),
            selected = selected == TransferTab.UPLOAD,
            onClick = { onSelect(TransferTab.UPLOAD) },
        )
    }
}

@Composable
private fun TransferTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun UploadManagerTab(viewModel: UploadManagerViewModel) {
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()

    if (uploads.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NiEmptyState(
                icon = Icons.Filled.ArrowUpward,
                text = stringResource(R.string.transfer_upload_empty),
                hint = stringResource(R.string.transfer_upload_empty_hint),
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(uploads, key = { it.task.id }) { item ->
                UploadTaskCard(
                    item = item,
                    onPause = { viewModel.pause(item.task.id) },
                    onResume = { viewModel.resume(item.task.id) },
                    onCancel = { viewModel.cancel(item.task.id) },
                    onDelete = { viewModel.delete(item.task.id) },
                )
            }
        }
    }
}

@Composable
private fun UploadTaskCard(
    item: UploadItemUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val task = item.task
    val state = task.state

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            UploadStateBadge(state = state)
        }

        Text(
            text = stringResource(R.string.transfer_upload_to, task.storageName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (state == DownloadState.DOWNLOADING ||
            state == DownloadState.WAITING ||
            state == DownloadState.PAUSED
        ) {
            Spacer(Modifier.height(8.dp))
            val hasKnownSize = task.totalBytes > 0
            NiProgressTrack(fraction = if (hasKnownSize) item.progress else 0f)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (hasKnownSize) {
                        "${formatBytes(item.uploadedBytes)} / ${formatBytes(task.totalBytes)}"
                    } else {
                        stringResource(R.string.transfer_upload_waiting_unknown)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (state == DownloadState.DOWNLOADING && item.speedBytesPerSec > 0) {
                    Text(
                        text = buildString {
                            append(formatSpeed(item.speedBytesPerSec))
                            val eta = formatEta(item)
                            if (eta.isNotEmpty()) append(" · ").append(eta)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (state == DownloadState.FAILED && !task.errorMessage.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = task.errorMessage!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = NiExtraColors.current.surfaceLevel3)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                DownloadState.WAITING, DownloadState.DOWNLOADING -> {
                    UploadActionIconButton(
                        icon = Icons.Filled.Pause,
                        label = stringResource(R.string.download_manager_pause),
                        onClick = onPause,
                    )
                    UploadActionIconButton(
                        icon = Icons.Filled.Clear,
                        label = stringResource(R.string.transfer_upload_cancel),
                        onClick = onCancel,
                    )
                }
                DownloadState.PAUSED -> {
                    UploadActionIconButton(
                        icon = Icons.Filled.PlayArrow,
                        label = stringResource(R.string.download_manager_resume),
                        onClick = onResume,
                    )
                    UploadActionIconButton(
                        icon = Icons.Filled.Clear,
                        label = stringResource(R.string.transfer_upload_cancel),
                        onClick = onCancel,
                    )
                }
                else -> UploadActionIconButton(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.transfer_upload_delete),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun UploadStateBadge(state: Int) {
    val (text, color) = when (state) {
        DownloadState.WAITING -> stringResource(R.string.transfer_upload_state_waiting) to MaterialTheme.colorScheme.outline
        DownloadState.DOWNLOADING -> stringResource(R.string.transfer_upload_state_uploading) to MaterialTheme.colorScheme.primary
        DownloadState.PAUSED -> stringResource(R.string.download_state_paused) to MaterialTheme.colorScheme.outline
        DownloadState.COMPLETED -> stringResource(R.string.transfer_upload_state_completed) to MaterialTheme.colorScheme.tertiary
        DownloadState.FAILED -> stringResource(R.string.transfer_upload_state_failed) to MaterialTheme.colorScheme.error
        DownloadState.CANCELLED -> stringResource(R.string.transfer_upload_state_cancelled) to MaterialTheme.colorScheme.outline
        else -> stringResource(R.string.transfer_upload_state_unknown) to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun UploadActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else String.format("%.1f %s", size, units[unitIndex])
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1000 * 1000 -> String.format("%.1f MB/s", bytesPerSec / (1000.0 * 1000.0))
    bytesPerSec >= 1000 -> "${bytesPerSec / 1000} KB/s"
    else -> "$bytesPerSec B/s"
}

/** ETA 文本：由速度与剩余字节计算；未知时返回空串。 */
private fun formatEta(item: UploadItemUi): String {
    if (item.speedBytesPerSec <= 0 || item.task.totalBytes <= 0) return ""
    val remaining = item.task.totalBytes - item.uploadedBytes
    if (remaining <= 0) return ""
    val seconds = remaining / item.speedBytesPerSec
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> String.format("%d:%02d:%02d", h, m, s)
        else -> String.format("%d:%02d", m, s)
    }
}