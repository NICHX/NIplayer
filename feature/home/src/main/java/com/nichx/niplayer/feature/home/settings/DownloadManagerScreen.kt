package com.nichx.niplayer.feature.home.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit = {},
    onPlayVideo: () -> Unit = {},
    onNavigateToImageViewer: () -> Unit = {},
    viewModel: DownloadManagerViewModel = hiltViewModel(),
) {
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val downloadDirInfo by viewModel.downloadDirInfo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                DownloadNavigationEvent.NavigateToPlayer -> onPlayVideo()
                DownloadNavigationEvent.NavigateToImageViewer -> onNavigateToImageViewer()
            }
        }
    }

    val context = LocalContext.current
    var pendingSetDownloadDir by remember { mutableStateOf(false) }
    val downloadDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        pendingSetDownloadDir = false
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) { }
        val dirName = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "下载目录"
        viewModel.setDownloadDir(treeUri.toString(), dirName)
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "下载管理",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "更多",
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("清空已完成") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.removeCompleted()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重试所有失败") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.retryAllFailed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("清空失败/已取消") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.clearFailed()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (displayItems.isEmpty() && downloadDirInfo.uri.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                NiEmptyState(
                    icon = Icons.Filled.ArrowDownward,
                    text = "暂无下载任务",
                    hint = "在文件浏览页长按文件即可下载",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 下载目录设置卡片
                item(key = "download_dir_section") {
                    DownloadDirCard(
                        dirInfo = downloadDirInfo,
                        onSetDownloadDir = {
                            pendingSetDownloadDir = true
                            downloadDirLauncher.launch(null)
                        },
                        onClearDownloadDir = { viewModel.clearDownloadDir() },
                    )
                }

                items(
                    items = displayItems,
                    key = { item ->
                        when (item) {
                            is DownloadGroupedItem.Section -> "section_${item.title}"
                            is DownloadGroupedItem.Task -> "task_${item.display.task.id}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is DownloadGroupedItem.Section -> SectionHeader(
                            title = item.title,
                            count = item.count,
                        )
                        is DownloadGroupedItem.Task -> DownloadTaskCard(
                            display = item.display,
                            onPause = { viewModel.pauseTask(item.display.task.id) },
                            onResume = { viewModel.resumeTask(item.display.task.id) },
                            onCancel = {
                                pendingAction = PendingAction.Cancel(
                                    item.display.task.id,
                                    item.display.task.fileName,
                                )
                            },
                            onRetry = { viewModel.retryTask(item.display.task.id) },
                            onDelete = {
                                pendingAction = PendingAction.Delete(
                                    item.display.task.id,
                                    item.display.task.fileName,
                                )
                            },
                            onClearRecord = { viewModel.clearRecord(item.display.task.id) },
                            onOpen = { viewModel.openDownloadFile(item.display.task) },
                        )
                    }
                }
            }
        }
    }

    // 取消确认对话框
    pendingAction?.let { action ->
        when (action) {
            is PendingAction.Cancel -> {
                NiConfirmDialog(
                    title = "取消下载",
                    text = "已下载的部分将被删除，确定取消「${action.taskName}」吗？",
                    confirmText = "取消下载",
                    onConfirm = {
                        viewModel.cancelTask(action.taskId)
                        pendingAction = null
                    },
                    onDismiss = { pendingAction = null },
                )
            }
            is PendingAction.Delete -> {
                NiConfirmDialog(
                    title = "删除任务",
                    text = "将删除任务记录及已下载文件「${action.taskName}」，确定吗？",
                    confirmText = "删除",
                    onConfirm = {
                        viewModel.deleteTask(action.taskId)
                        pendingAction = null
                    },
                    onDismiss = { pendingAction = null },
                )
            }
        }
    }
}

@Composable
private fun DownloadDirCard(
    dirInfo: com.nichx.niplayer.datastore.DownloadDirInfo,
    onSetDownloadDir: () -> Unit,
    onClearDownloadDir: () -> Unit,
) {
    val hasDir = dirInfo.uri.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hasDir) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
            .clickable { if (!hasDir) onSetDownloadDir() }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (hasDir) Icons.Filled.Folder else Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = if (hasDir) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasDir) "下载目录" else "未设置下载目录",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (hasDir) {
                    Text(
                        text = dirInfo.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "请先设置下载目录才可以下载",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (hasDir) {
                TextButton(onClick = onClearDownloadDir) {
                    Text("清除", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(onClick = onSetDownloadDir) {
                    Text("设置", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DownloadTaskCard(
    display: DownloadTaskDisplay,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onClearRecord: () -> Unit,
    onOpen: () -> Unit,
) {
    val task = display.task
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
            StateBadge(state = state)
        }

        val targetLabel = task.targetStorageName ?: "缓存"
        Text(
            text = "保存至：$targetLabel",
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
            NiProgressTrack(fraction = if (hasKnownSize) display.progress / 100f else 0f)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (hasKnownSize)
                        "${formatFileSize(display.downloadedBytes)} / ${formatFileSize(task.totalBytes)}"
                    else
                        "${formatFileSize(display.downloadedBytes)} / 未知大小",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (display.speed.isNotEmpty()) {
                    Text(
                        text = if (display.eta.isNotEmpty()) "${display.speed} · ${display.eta}" else display.speed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (state == DownloadState.WAITING) {
                    Text(
                        text = "等待中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
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
        ActionButtons(
            state = state,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onDelete = onDelete,
            onClearRecord = onClearRecord,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun StateBadge(state: Int) {
    val (text, color) = when (state) {
        DownloadState.WAITING -> "等待中" to MaterialTheme.colorScheme.outline
        DownloadState.DOWNLOADING -> "下载中" to MaterialTheme.colorScheme.primary
        DownloadState.PAUSED -> "已暂停" to MaterialTheme.colorScheme.outline
        DownloadState.COMPLETED -> "已完成" to MaterialTheme.colorScheme.tertiary
        DownloadState.FAILED -> "失败" to MaterialTheme.colorScheme.error
        DownloadState.CANCELLED -> "已取消" to MaterialTheme.colorScheme.outline
        else -> "未知" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ActionButtons(
    state: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onClearRecord: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            DownloadState.DOWNLOADING, DownloadState.WAITING -> {
                ActionIconButton(icon = Icons.Filled.Pause, label = "暂停", onClick = onPause)
                ActionIconButton(icon = Icons.Filled.Clear, label = "取消", onClick = onCancel)
            }
            DownloadState.PAUSED -> {
                ActionIconButton(icon = Icons.Filled.PlayArrow, label = "继续", onClick = onResume)
                ActionIconButton(icon = Icons.Filled.Clear, label = "取消", onClick = onCancel)
            }
            DownloadState.FAILED -> {
                ActionIconButton(icon = Icons.Filled.Refresh, label = "重试", onClick = onRetry)
                ActionIconButton(icon = Icons.Filled.Delete, label = "删除", onClick = onDelete)
            }
            DownloadState.COMPLETED -> {
                ActionTextButton(text = "打开", onClick = onOpen)
                ActionTextButton(text = "清除记录", onClick = onClearRecord)
                ActionIconButton(icon = Icons.Filled.Delete, label = "删除文件", onClick = onDelete)
            }
            DownloadState.CANCELLED -> {
                ActionIconButton(icon = Icons.Filled.Delete, label = "删除", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ActionIconButton(
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

@Composable
private fun ActionTextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "${bytes} B" else String.format("%.1f %s", size, units[unitIndex])
}

private sealed class PendingAction {
    abstract val taskId: Long
    abstract val taskName: String

    data class Cancel(override val taskId: Long, override val taskName: String = "") : PendingAction()
    data class Delete(override val taskId: Long, override val taskName: String = "") : PendingAction()
}
