package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.DownloadDirInfo
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiGlassSwitch
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.glassOnSurface
import com.nichx.niplayer.designsystem.components.glassOnSurfaceMuted
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.storage.StorageAccess
import com.nichx.niplayer.designsystem.components.FolderPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun DownloadManagerScreen(
    onBack: () -> Unit = {},
    onPlayVideo: (Boolean) -> Unit = {},
    onNavigateToImageViewer: () -> Unit = {},
    viewModel: DownloadManagerViewModel = hiltViewModel(),
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.download_manager_title),
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
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = niFrostSurfaceColor(),
                            border = BorderStroke(NiGlassHairWidth, niGlassBorderColor()),
                            shadowElevation = 6.dp,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_manager_clear_completed)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.removeCompleted()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_manager_retry_failed)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.retryAllFailed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_manager_clear_failed)) },
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
    ) { padding ->
        DownloadManagerTab(
            viewModel = viewModel,
            onPlayVideo = onPlayVideo,
            onNavigateToImageViewer = onNavigateToImageViewer,
            topPadding = padding.calculateTopPadding(),
        )
    }
}

/**
 * 下载任务列表内容（无独立 Scaffold/顶栏），供独立下载管理页与统一的「传输管理」中心页复用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
internal fun DownloadManagerTab(
    viewModel: DownloadManagerViewModel,
    onPlayVideo: (Boolean) -> Unit,
    onNavigateToImageViewer: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val downloadDirInfo by viewModel.downloadDirInfo.collectAsStateWithLifecycle()
    val downloadLrcWithAudio by viewModel.downloadLrcWithAudio.collectAsStateWithLifecycle()
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showDownloadSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is DownloadNavigationEvent.NavigateToPlayer -> onPlayVideo(event.isAudio)
                DownloadNavigationEvent.NavigateToImageViewer -> onNavigateToImageViewer()
            }
        }
    }

    val context = LocalContext.current
    var showFolderPicker by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (displayItems.isEmpty() && downloadDirInfo.path.isNotBlank()) {
        // 下载目录卡片始终显示（含空态时提供重设/清除），任务区用空态占位
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = topPadding + 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "download_dir_section") {
                DownloadSettingsCard(
                    dirInfo = downloadDirInfo,
                    onClick = { showDownloadSettings = true },
                )
            }
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NiEmptyState(
                        icon = Icons.Filled.ArrowDownward,
                        text = stringResource(R.string.download_manager_empty),
                        hint = stringResource(R.string.download_manager_empty_hint),
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = topPadding + 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 下载设置卡片（点击打开下载设置弹窗：目录 + 歌词开关）
            item(key = "download_dir_section") {
                DownloadSettingsCard(
                    dirInfo = downloadDirInfo,
                    onClick = { showDownloadSettings = true },
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

    // 取消确认对话框
    pendingAction?.let { action ->
        when (action) {
            is PendingAction.Cancel -> {
                NiConfirmDialog(
                    title = stringResource(R.string.download_manager_cancel_title),
                    text = stringResource(R.string.download_manager_cancel_confirm, action.taskName),
                    confirmText = stringResource(R.string.download_manager_cancel_confirm_text),
                    onConfirm = {
                        viewModel.cancelTask(action.taskId)
                        pendingAction = null
                    },
                    onDismiss = { pendingAction = null },
                )
            }
            is PendingAction.Delete -> {
                NiConfirmDialog(
                    title = stringResource(R.string.download_manager_delete_title),
                    text = stringResource(R.string.download_manager_delete_confirm, action.taskName),
                    confirmText = stringResource(R.string.download_manager_delete),
                    onConfirm = {
                        viewModel.deleteTask(action.taskId)
                        pendingAction = null
                    },
                    onDismiss = { pendingAction = null },
                )
            }
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            initialPath = downloadDirInfo.path,
            onDismiss = { showFolderPicker = false },
            onConfirm = { path, dirName ->
                viewModel.setDownloadDir(path, dirName)
                showFolderPicker = false
            },
        )
    }

    if (showDownloadSettings) {
        DownloadSettingsDialog(
            dirInfo = downloadDirInfo,
            downloadLrcWithAudio = downloadLrcWithAudio,
            onLrcEnabledChange = { viewModel.setDownloadLrcWithAudio(it) },
            onChooseDirectory = {
                if (StorageAccess.canWriteSharedStorage(context)) {
                    showDownloadSettings = false
                    showFolderPicker = true
                } else {
                    showDownloadSettings = false
                    showPermissionDialog = true
                }
            },
            onClearDirectory = { viewModel.clearDownloadDir() },
            onDismiss = { showDownloadSettings = false },
        )
    }

    if (showPermissionDialog) {
        NiConfirmDialog(
            title = stringResource(R.string.download_dir_permission_title),
            text = stringResource(R.string.download_dir_permission_message),
            confirmText = stringResource(R.string.download_dir_permission_grant),
            onConfirm = {
                showPermissionDialog = false
                StorageAccess.openAllFilesAccessSettings(context)
            },
            onDismiss = { showPermissionDialog = false },
        )
    }
}

@Composable
private fun DownloadSettingsCard(
    dirInfo: DownloadDirInfo,
    onClick: () -> Unit,
) {
    val hasDir = dirInfo.path.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.download_settings_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (hasDir) dirInfo.path else stringResource(R.string.download_manager_no_dir),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.download_settings_title),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 下载设置弹窗（液态玻璃材质）：下载目录管理 + 「同时下载歌词」开关。
 * 点击卡片进入，目录与歌词开关集中在此统一管理。
 * 经 [NiInfoDialog] → [NiGlassOverlay] → [NiGlassDialog] 渲染，backdrop 真模糊，
 * 无 backdrop 或 API < 33 时降级为不透明磨砂卡片（niFrostSurfaceColor）。
 */
@Composable
private fun DownloadSettingsDialog(
    dirInfo: DownloadDirInfo,
    downloadLrcWithAudio: Boolean,
    onLrcEnabledChange: (Boolean) -> Unit,
    onChooseDirectory: () -> Unit,
    onClearDirectory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasDir = dirInfo.path.isNotBlank()
    val onSurface = MaterialTheme.colorScheme.onSurface
    NiInfoDialog(
        title = stringResource(R.string.download_settings_title),
        onDismiss = onDismiss,
        content = {
            // 下载目录行（与开关行同对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (hasDir) Icons.Filled.Folder else Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = if (hasDir) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_manager_has_dir),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = glassOnSurface(),
                    )
                    if (hasDir) {
                        Text(
                            text = dirInfo.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = glassOnSurfaceMuted(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.download_manager_no_dir_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (hasDir) {
                    TextButton(onClick = onChooseDirectory) {
                        Text(stringResource(R.string.download_manager_reset), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onClearDirectory) {
                        Text(stringResource(R.string.download_manager_clear), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    TextButton(onClick = onChooseDirectory) {
                        Text(stringResource(R.string.download_manager_set), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            HorizontalDivider(
                color = onSurface.copy(alpha = 0.08f),
            )
            // 同时下载歌词开关行（整行点击仅委托开关，移除涟漪，与 SettingSwitchRow 一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLrcEnabledChange(!downloadLrcWithAudio) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_lrc_with_audio),
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassOnSurface(),
                    )
                    Text(
                        text = stringResource(R.string.download_lrc_with_audio_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = glassOnSurfaceMuted(),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                NiGlassSwitch(
                    checked = downloadLrcWithAudio,
                    onCheckedChange = onLrcEnabledChange,
                )
            }
        },
    )
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

        val targetLabel = task.targetStorageName ?: stringResource(R.string.download_manager_target_fallback)
        Text(
            text = stringResource(R.string.download_manager_saved_to, targetLabel),
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
                        "${formatFileSize(display.downloadedBytes)} / ${stringResource(R.string.download_manager_unknown_size)}",
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
                        text = stringResource(R.string.download_manager_waiting),
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
        DownloadState.WAITING -> stringResource(R.string.download_state_waiting) to MaterialTheme.colorScheme.outline
        DownloadState.DOWNLOADING -> stringResource(R.string.download_state_downloading) to MaterialTheme.colorScheme.primary
        DownloadState.PAUSED -> stringResource(R.string.download_state_paused) to MaterialTheme.colorScheme.outline
        DownloadState.COMPLETED -> stringResource(R.string.download_state_completed) to MaterialTheme.colorScheme.tertiary
        DownloadState.FAILED -> stringResource(R.string.download_state_failed) to MaterialTheme.colorScheme.error
        DownloadState.CANCELLED -> stringResource(R.string.download_state_cancelled) to MaterialTheme.colorScheme.outline
        else -> stringResource(R.string.download_state_unknown) to MaterialTheme.colorScheme.outline
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
                ActionIconButton(icon = Icons.Filled.Pause, label = stringResource(R.string.download_manager_pause), onClick = onPause)
                ActionIconButton(icon = Icons.Filled.Clear, label = stringResource(R.string.download_manager_cancel), onClick = onCancel)
            }
            DownloadState.PAUSED -> {
                ActionIconButton(icon = Icons.Filled.PlayArrow, label = stringResource(R.string.download_manager_resume), onClick = onResume)
                ActionIconButton(icon = Icons.Filled.Clear, label = stringResource(R.string.download_manager_cancel), onClick = onCancel)
            }
            DownloadState.FAILED -> {
                ActionIconButton(icon = Icons.Filled.Refresh, label = stringResource(R.string.download_manager_retry), onClick = onRetry)
                ActionIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.download_manager_delete), onClick = onDelete)
            }
            DownloadState.COMPLETED -> {
                ActionTextButton(text = stringResource(R.string.download_manager_open), onClick = onOpen)
                ActionTextButton(text = stringResource(R.string.download_manager_clear_record), onClick = onClearRecord)
                ActionIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.download_manager_delete_file), onClick = onDelete)
            }
            DownloadState.CANCELLED -> {
                ActionIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.download_manager_delete), onClick = onDelete)
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
