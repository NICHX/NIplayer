package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiFAB
import com.nichx.niplayer.designsystem.components.NiFabVariant
import com.nichx.niplayer.designsystem.components.NiSkeletonBox
import com.nichx.niplayer.designsystem.components.NiSkeletonLine
import com.nichx.niplayer.designsystem.components.NiSnackbarDefaults
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTextFieldDefaults
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.designsystem.theme.NiSpacings
import kotlinx.coroutines.launch

private val menuShape = RoundedCornerShape(12.dp)
private val cardShape = RoundedCornerShape(12.dp)
private val pillShape = RoundedCornerShape(8.dp)

private enum class LibraryFilter(@StringRes val labelRes: Int) {
    ALL(R.string.library_filter_all),
    LOCAL(R.string.library_filter_local),
    SMB(R.string.library_filter_smb),
    WEBDAV(R.string.library_filter_webdav),
}

private fun filterByType(filter: LibraryFilter, libraries: List<MediaLibraryEntity>): List<MediaLibraryEntity> {
    return when (filter) {
        LibraryFilter.ALL -> libraries
        LibraryFilter.LOCAL -> libraries.filter {
            it.mediaType == MediaType.LOCAL_STORAGE || it.mediaType == MediaType.EXTERNAL_STORAGE
        }
        LibraryFilter.SMB -> libraries.filter { it.mediaType == MediaType.SMB_SERVER }
        LibraryFilter.WEBDAV -> libraries.filter { it.mediaType == MediaType.WEBDAV_SERVER }
    }
}

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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    var showTypeSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MediaLibraryEntity?>(null) }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var isSearchVisible by remember { mutableStateOf(false) }
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
                    IconButton(onClick = {
                        if (isSearchVisible) viewModel.setSearchQuery("")
                        isSearchVisible = !isSearchVisible
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(
                                if (isSearchVisible) R.string.library_close_search else R.string.library_search,
                            ),
                        )
                    }
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
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = fadeIn(animationSpec = tween(NiMotion.DURATION_SWITCH)),
                    exit = fadeOut(animationSpec = tween(NiMotion.DURATION_SWITCH)),
                ) {
                    NiTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = stringResource(R.string.library_search_placeholder),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NiSpacings.screenOuter)
                        .padding(bottom = 8.dp),
                    colors = NiTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

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
                LibrarySkeleton(modifier = Modifier.weight(1f))
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
                        icon = if (searchQuery.isNotEmpty()) Icons.Filled.Search else Icons.Filled.Folder,
                        text = if (searchQuery.isNotEmpty()) stringResource(R.string.library_no_result)
                        else stringResource(R.string.library_no_type),
                        hint = if (searchQuery.isNotEmpty()) stringResource(R.string.library_try_keywords)
                        else stringResource(R.string.library_switch_filter),
                    )
                }
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
                NiFAB(
                    icon = Icons.Filled.Add,
                    onClick = { showTypeSheet = true },
                    contentDescription = stringResource(R.string.library_add_storage),
                    variant = NiFabVariant.PRIMARY,
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

@Composable
private fun FilterChipRow(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items) { index, label ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = pillShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySourceCard(
    library: MediaLibraryEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    val context = LocalContext.current
    val typeInfo = remember(library.mediaType, extraColors) {
        storageTypeInfo(library.mediaType, extraColors, context)
    }
    val canModify by remember(library.mediaType) {
        derivedStateOf { library.mediaType != MediaType.LOCAL_STORAGE }
    }
    var showMenu by remember { mutableStateOf(false) }

    val brandColor = typeInfo.color
    val colorAlpha10 = remember(brandColor) { brandColor.copy(alpha = 0.1f) }
    val outlineAlpha40 = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                // 语义合并：图标/名称/描述合并为单一节点，降低语义树节点数
                .semantics(mergeDescendants = true) {}
                .clip(cardShape)
                .background(extraColors.surfaceLevel2)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (canModify) { { showMenu = true } } else null,
                )
                .padding(start = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(brandColor),
            )

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorAlpha10),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeInfo.icon,
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = library.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeInfo.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        color = brandColor,
                    )
                    val describe = library.describe
                    if (describe != null) {
                        Text(
                            text = " · $describe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = outlineAlpha40,
                modifier = Modifier.padding(end = 12.dp).size(18.dp),
            )
        }

        if (showMenu && canModify) {
            Box {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { showMenu = false },
                    shape = menuShape,
                    containerColor = niFrostSurfaceColor(),
                    border = androidx.compose.foundation.BorderStroke(NiGlassHairWidth, niGlassBorderColor()),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.edit),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageTypePickerSheet(
    onDismiss: () -> Unit,
    onPick: (MediaType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val types = listOf(
        Triple(
            MediaType.SMB_SERVER,
            stringResource(R.string.library_type_smb_label),
            stringResource(R.string.library_type_smb_desc),
        ),
        Triple(
            MediaType.WEBDAV_SERVER,
            stringResource(R.string.library_type_webdav_label),
            stringResource(R.string.library_type_webdav_desc),
        ),
        Triple(
            MediaType.EXTERNAL_STORAGE,
            stringResource(R.string.library_type_external_label),
            stringResource(R.string.library_type_external_desc),
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.library_select_storage_type),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            types.forEach { (type, label, desc) ->
                val typeInfo = storageTypeInfo(type, NiExtraColors.current, LocalContext.current)
                val iconBg = typeInfo.color.copy(alpha = 0.1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onPick(type) },
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = typeInfo.icon,
                            contentDescription = null,
                            tint = typeInfo.color,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    // 骨架与真实列表同构（同为 LazyColumn，item key 命名一致），
    // 数据就绪切换时 LazyColumn 结构与滚动位置保持，仅替换 item 内容，避免整树重建
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = NiSpacings.screenOuter,
            end = NiSpacings.screenOuter,
            top = 4.dp,
            bottom = 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        item(key = "skeleton_chips") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) {
                    NiSkeletonBox(
                        width = 60.dp,
                        height = 32.dp,
                        shape = pillShape,
                    )
                }
            }
        }
        item(key = "section_count") {
            Spacer(Modifier.height(16.dp))
            NiSkeletonLine(widthFraction = 0.15f)
        }
        // 模拟一组存储源分区的 header + 卡片（与真实列表 key 风格一致）
        item(key = "header_local") {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NiSkeletonBox(width = 4.dp, height = 16.dp, shape = RoundedCornerShape(2.dp))
                Spacer(Modifier.width(8.dp))
                NiSkeletonLine(widthFraction = 0.2f)
            }
        }
        repeat(6) {
            item(key = "library_loading_$it") {
                SkeletonCard()
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    val extraColors = NiExtraColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(cardShape)
            .background(extraColors.surfaceLevel2),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(extraColors.surfaceLevel3),
            )
            Spacer(Modifier.width(12.dp))
            NiSkeletonBox(
                width = 36.dp,
                height = 36.dp,
                shape = CircleShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                NiSkeletonLine(widthFraction = 0.6f)
                Spacer(Modifier.height(6.dp))
                NiSkeletonLine(widthFraction = 0.35f)
            }
        }
    }
}

private data class StorageTypeInfo(
    val icon: ImageVector,
    val shortName: String,
    val color: Color,
)

private fun storageTypeInfo(mediaType: MediaType, extraColors: NiExtraColors, context: Context): StorageTypeInfo {
    return when (mediaType) {
        MediaType.LOCAL_STORAGE -> StorageTypeInfo(
            Icons.Filled.PhoneAndroid, context.getString(R.string.storage_type_local), extraColors.storageLocalColor,
        )
        MediaType.SMB_SERVER -> StorageTypeInfo(
            Icons.Filled.Computer, "SMB", extraColors.storageSmbColor,
        )
        MediaType.WEBDAV_SERVER -> StorageTypeInfo(
            Icons.Filled.CloudQueue, "WebDAV", extraColors.storageWebdavColor,
        )
        MediaType.EXTERNAL_STORAGE -> StorageTypeInfo(
            Icons.Filled.SdCard, "SAF", extraColors.storageExternalColor,
        )
        MediaType.OTHER_STORAGE -> StorageTypeInfo(
            Icons.Filled.History, context.getString(R.string.storage_type_history), extraColors.storageHistoryColor,
        )
        MediaType.QUICK_ACCESS -> StorageTypeInfo(
            Icons.Filled.SdCard, context.getString(R.string.storage_type_other), extraColors.storageHistoryColor,
        )
    }
}
