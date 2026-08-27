package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiListSkeleton
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.showNiMessage
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import java.util.Locale

/**
 * 缓存管理页：展示缓存占用 + 按项/全部清理。
 *
 * 替代旧仓库 `CacheManagerActivity`（user_component/ui/activities/cache_manager/），
 * v2 简化设计：动态扫描 cacheDir 子目录，不硬编码缓存类型枚举。
 *
 * @param onBack 返回回调
 */
@Composable
fun CacheManagerScreen(
    onBack: () -> Unit = {},
    viewModel: CacheManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingClear by remember { mutableStateOf<CacheItem?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showNiMessage(NiMessage.info(it))
            viewModel.consumeToast()
        }
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.cache_manager_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cache_manager_refresh),
                        )
                    }
                    IconButton(
                        onClick = { showClearAll = true },
                        enabled = uiState.items.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.cache_manager_clear_all),
                        )
                    }
                },
            )
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        // O-26：加载态用骨架屏，空态用 NiEmptyState，替代原纯 Text "暂无缓存"
        when {
            uiState.isLoading -> {
                NiListSkeleton(
                    modifier = Modifier
                        .fillMaxSize(),
                    itemCount = 4,
                )
            }
            uiState.items.isEmpty() -> {
                NiEmptyState(
                    icon = Icons.Filled.DeleteSweep,
                    text = stringResource(R.string.cache_manager_empty),
                    hint = stringResource(R.string.cache_manager_empty_hint),
                    modifier = Modifier
                        .fillMaxSize(),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ---- 总览 ----
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NiExtraColors.current.surfaceLevel2),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.cache_manager_total),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = formatSize(uiState.totalSizeBytes),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = stringResource(R.string.cache_manager_file_count, uiState.totalFileCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ---- 缓存项列表 ----
                    item {
                        Text(
                            text = stringResource(R.string.cache_manager_detail),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NiExtraColors.current.surfaceLevel2),
                        ) {
                            Column {
                                uiState.items.forEachIndexed { index, item ->
                                    CacheItemRow(
                                        item = item,
                                        onClear = { pendingClear = item },
                                    )
                                    if (index < uiState.items.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 56.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 单项清理确认 ----
    pendingClear?.let { item ->
        NiConfirmDialog(
            title = stringResource(R.string.cache_manager_clear_title),
            text = stringResource(R.string.cache_manager_clear_confirm, item.displayName, formatSize(item.sizeBytes)),
            onConfirm = {
                viewModel.clearCache(item)
                pendingClear = null
            },
            onDismiss = { pendingClear = null },
            confirmText = stringResource(R.string.cache_manager_clear_confirm_text),
        )
    }

    // ---- 全部清理确认 ----
    if (showClearAll) {
        NiConfirmDialog(
            title = stringResource(R.string.cache_manager_clear_all_title),
            text = stringResource(R.string.cache_manager_clear_all_confirm, formatSize(uiState.totalSizeBytes)),
            onConfirm = {
                viewModel.clearAll()
                showClearAll = false
            },
            onDismiss = { showClearAll = false },
            confirmText = stringResource(R.string.cache_manager_clear_all_confirm_text),
        )
    }
}

@Composable
private fun CacheItemRow(
    item: CacheItem,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.cache_manager_item_info, formatSize(item.sizeBytes), item.fileCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cache_manager_clear_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 格式化字节大小为人类可读字符串。 */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    return String.format(
        Locale.ROOT,
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups],
    )
}
