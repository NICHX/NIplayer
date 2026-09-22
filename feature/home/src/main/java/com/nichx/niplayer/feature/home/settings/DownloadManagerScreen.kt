package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
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
                // UI-5 修复（2026-09-22）：本列表是 Section / Task 两种布局混排，
                // 不声明 contentType 会让 LazyList 的复用池在两种 item 之间串用，
                // 滚动时更容易出现抖动与多余的重新布局。全仓原先 0 处 contentType。
                contentType = { item ->
                    when (item) {
                        is DownloadGroupedItem.Section -> "section"
                        is DownloadGroupedItem.Task -> "task"
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
