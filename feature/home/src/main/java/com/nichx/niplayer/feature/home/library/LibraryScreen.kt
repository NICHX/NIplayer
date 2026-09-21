package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.datastore.MediaLibrarySettings
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiGlassCircleIcon
import com.nichx.niplayer.designsystem.components.NiSnackbarDefaults
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiSpacings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun LibraryScreen(
    onNavigateToStorageFile: (Int, String) -> Unit,
    onNavigateToStoragePlus: (type: String?, storageId: Int) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    val filteredLibraries by viewModel.filteredLibraries.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    var showTypeSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MediaLibraryEntity?>(null) }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    // 视图模式（分组列表 / 双列网格），持久化于 MediaLibrarySettings；点击顶栏按钮切换
    var viewMode by remember { mutableStateOf(MediaLibrarySettings.viewMode) }
    val isGridView = viewMode == MediaLibrarySettings.ViewMode.GRID
    // 删除存储源使用"软删除 + 撤销"动作型 snackbar，需直连 SnackbarHostState（撤销型属全局总线的合理例外）。
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentFiltered by remember(selectedFilter, filteredLibraries) {
        derivedStateOf { filterByType(selectedFilter, filteredLibraries) }
    }

    NiScaffold(
        containerColor = Color.Transparent,
        topBar = {
            NiTopBar(
                title = stringResource(R.string.library_title),
                actions = {
                    // 视图切换：图标显示当前模式，点击在「分组列表 / 双列网格」间切换并持久化
                    NiGlassCircleIcon(
                        icon = if (isGridView) Icons.Rounded.GridView
                        else Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = stringResource(
                            if (isGridView) R.string.library_view_grid
                            else R.string.library_view_list,
                        ),
                        onClick = {
                            val next = if (isGridView) MediaLibrarySettings.ViewMode.LIST
                            else MediaLibrarySettings.ViewMode.GRID
                            viewMode = next
                            MediaLibrarySettings.viewMode = next
                        },
                    )
                },
            )
        },
        snackbarHost = {
                NiSnackbarHost(
                    hostState = snackbarHostState,
                    bottomObstruction = NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION,
                )
            },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (dataReady && libraries.isNotEmpty()) {
                FilterChipRow(
                    items = LibraryFilter.entries.map { stringResource(it.labelRes) },
                    selectedIndex = selectedFilter.ordinal,
                    onItemSelected = { index ->
                        selectedFilter = LibraryFilter.entries[index]
                    },
                    modifier = Modifier.padding(horizontal = NiSpacings.screenOuter),
                )
                Spacer(Modifier.height(4.dp))
            }

            if (!dataReady) {
                LibrarySkeleton(
                    isGridView = isGridView,
                    modifier = Modifier.weight(1f),
                )
            } else if (libraries.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    NiEmptyState(
                        icon = Icons.Filled.FolderOpen,
                        text = stringResource(R.string.library_empty_title),
                        hint = stringResource(R.string.library_empty_hint),
                        actionText = stringResource(R.string.library_add_storage),
                        onAction = { showTypeSheet = true },
                    )
                }
            } else if (currentFiltered.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    NiEmptyState(
                        icon = Icons.Filled.Folder,
                        text = stringResource(R.string.library_no_type),
                        hint = stringResource(R.string.library_switch_filter),
                    )
                }
            } else if (isGridView) {
                LibrarySourceGrid(
                    libraries = currentFiltered,
                    count = currentFiltered.size,
                    onOpen = { onNavigateToStorageFile(it, "") },
                    onEdit = { onNavigateToStoragePlus(null, it) },
                    onDelete = { deleteTarget = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                val grouped = currentFiltered.groupBy { it.mediaType }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = NiSpacings.screenOuter,
                        end = NiSpacings.screenOuter,
                        top = 0.dp,
                        bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "section_count") {
                        Text(
                            text = stringResource(R.string.library_storage_count, currentFiltered.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }

                    grouped.forEach { (type, libs) ->
                        item(key = "header_${type.value}") {
                            val typeInfo = storageTypeInfo(type, NiExtraColors.current, context)
                            SectionHeader(
                                label = stringResource(type.storageNameRes),
                                count = libs.size,
                                color = typeInfo.color,
                            )
                        }
                        itemsIndexed(
                            items = libs,
                            key = { _, item -> "library_${item.id}" },
                        ) { _, library ->
                            LibrarySourceCard(
                                library = library,
                                onClick = { onNavigateToStorageFile(library.id, "") },
                                onEdit = {
                                    onNavigateToStoragePlus(null, library.id)
                                },
                                onDelete = {
                                    deleteTarget = library
                                },
                            )
                        }
                    }
                }
            }
            }

            if (dataReady && libraries.isNotEmpty()) {
                // 与导航栏 pill 同款灰色圆钮 + tertiary 图标，跟随底栏不透明度，保证视觉统一
                // （此页位于 HomeScreen 捕获层内，无本地 backdrop，用纯灰底而非液态玻璃模糊）
                NiGlassCircleIcon(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.library_add_storage),
                    onClick = { showTypeSheet = true },
                    size = 56.dp,
                    iconSize = 26.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 104.dp),
                )
            }
        }
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        NiConfirmDialog(
            title = stringResource(R.string.library_delete_storage_title),
            text = stringResource(R.string.library_delete_storage_body, target.displayName),
            onConfirm = {
                viewModel.delete(target)
                val deletedId = target.id
                val deletedName = target.displayName
                deleteTarget = null
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.library_deleted, deletedName),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete()
                    } else {
                        viewModel.confirmDelete(deletedId)
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showTypeSheet) {
        StorageTypePickerSheet(
            onDismiss = { showTypeSheet = false },
            onPick = { type ->
                showTypeSheet = false
                onNavigateToStoragePlus(type.value, 0)
            },
        )
    }
}
