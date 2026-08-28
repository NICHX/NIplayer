package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiGlassSwitch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.VideoExtensionSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/**
 * 扫描目录管理页。
 *
 * 替代旧仓库 `ScanManagerActivity`（ViewPager + FragmentPagerAdapter + 两个 Fragment），
 * v2 用 Compose [TabRow] + 单一 [ScanManagerViewModel] 实现。
 *
 * 两个 Tab：
 * - **扫描目录**：管理用户手动添加的扩展扫描目录（extend_folder 表）。添加时输入路径，
 *   [VideoScanner] 扫描入库；删除时清理该目录视频并重新扫描剩余目录。
 * - **屏蔽目录**：按 folder_path 屏蔽/取消屏蔽（更新 video 表 filter 字段，filter=true
 *   的目录不在 [com.nichx.niplayer.storage.impl.VideoStorage] 根目录列表中显示）。
 *
 * 顶栏右侧提供视频扩展名配置入口（[VideoExtensionSettings]），影响扩展目录扫描的
 * 文件识别。
 *
 * @param onBack 返回回调
 */
@Composable
fun ScanManagerScreen(
    onBack: () -> Unit = {},
) {
    val viewModel: ScanManagerViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val messageController = LocalAppMessageController.current

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExtensionDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<com.nichx.niplayer.database.entity.ExtendFolderEntity?>(null) }

    // Toast 消息 → Snackbar
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            messageController.post(NiMessage.info(msg))
            viewModel.consumeToast()
        }
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.scan_manager_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showExtensionDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.scan_manager_extensions),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.scan_manager_tab_scan)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.scan_manager_tab_filter)) },
                )
            }

            when (selectedTab) {
                0 -> ExtendFolderTab(
                    folders = uiState.extendFolders,
                    onDelete = { folderToDelete = it },
                )
                1 -> FilterFolderTab(
                    folders = uiState.filterFolders,
                    onToggle = viewModel::toggleFolderFilter,
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
        if (selectedTab == 0) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.scan_manager_add_dir))
            }
        }
        }
    }

    // ---- 添加扩展目录对话框 ----
    if (showAddDialog) {
        var path by rememberSaveable { mutableStateOf("") }
        NiInfoDialog(
            title = stringResource(R.string.scan_manager_add_title),
            onDismiss = { showAddDialog = false },
            actions = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    onClick = {
                        viewModel.addExtendFolder(path)
                        showAddDialog = false
                    },
                ) { Text(stringResource(R.string.scan_manager_add)) }
            },
        ) {
            Text(
                text = stringResource(R.string.scan_manager_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(8.dp))
            NiTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.scan_manager_path_placeholder),
            )
        }
    }

    // ---- 删除确认对话框 ----
    folderToDelete?.let { entity ->
        NiConfirmDialog(
            title = stringResource(R.string.scan_manager_remove_title),
            text = stringResource(R.string.scan_manager_remove_confirm, entity.folderPath),
            onConfirm = {
                viewModel.removeExtendFolder(entity)
                folderToDelete = null
            },
            onDismiss = { folderToDelete = null },
            confirmText = stringResource(R.string.scan_manager_remove_confirm_text),
        )
    }

    // ---- 视频扩展名配置对话框 ----
    if (showExtensionDialog) {
        var extensionText by rememberSaveable {
            mutableStateOf(VideoExtensionSettings.supportText)
        }
        NiInfoDialog(
            title = stringResource(R.string.scan_manager_extensions_title),
            onDismiss = { showExtensionDialog = false },
            actions = {
                TextButton(onClick = {
                    VideoExtensionSettings.resetDefault()
                    extensionText = VideoExtensionSettings.supportText
                }) { Text(stringResource(R.string.scan_manager_reset)) }
                TextButton(onClick = { showExtensionDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    onClick = {
                        VideoExtensionSettings.supportText = extensionText
                        showExtensionDialog = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
        ) {
            Text(
                text = stringResource(R.string.scan_manager_extensions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(8.dp))
            NiTextField(
                value = extensionText,
                onValueChange = { extensionText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.scan_manager_extensions_placeholder),
            )
        }
    }
}

/** 扫描目录 Tab：展示扩展目录列表。 */
@Composable
private fun ExtendFolderTab(
    folders: List<com.nichx.niplayer.database.entity.ExtendFolderEntity>,
    onDelete: (com.nichx.niplayer.database.entity.ExtendFolderEntity) -> Unit,
) {
    if (folders.isEmpty()) {
        EmptyStateHint(text = stringResource(R.string.scan_manager_extend_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(
            count = folders.size,
            key = { index -> folders[index].folderPath },
        ) { index ->
            val folder = folders[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NiExtraColors.current.surfaceLevel2),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.folderPath,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.scan_manager_video_count, folder.childCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(folder) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.scan_manager_remove_action),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (index < folders.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** 屏蔽目录 Tab：展示所有扫描到的目录，Switch 切换屏蔽状态。 */
@Composable
private fun FilterFolderTab(
    folders: List<com.nichx.niplayer.database.bean.FolderBean>,
    onToggle: (com.nichx.niplayer.database.bean.FolderBean) -> Unit,
) {
    if (folders.isEmpty()) {
        EmptyStateHint(text = stringResource(R.string.scan_manager_filter_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(
            count = folders.size,
            key = { index -> folders[index].folderPath },
        ) { index ->
            val folder = folders[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NiExtraColors.current.surfaceLevel2),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.folderPath,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.scan_manager_video_count, folder.fileCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NiGlassSwitch(
                        checked = folder.isFilter,
                        onCheckedChange = { onToggle(folder) },
                    )
                }
            }
            if (index < folders.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** 空状态提示。 */
@Composable
private fun EmptyStateHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
