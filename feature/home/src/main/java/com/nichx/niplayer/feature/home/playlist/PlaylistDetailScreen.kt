package com.nichx.niplayer.feature.home.playlist

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiSnackbarDefaults
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.showNiMessage
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.MediaFileTypes
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

/** 底部操作栏的批量操作类型。 */
private enum class SelectionAction { MOVE, COPY }

/**
 * 歌单详情页（扩展功能方案二 · 页面 2）。
 *
 * 顶部展示「播放全部」，下方为可拖拽排序的条目列表（[org.burnoutcrew.reorderable]），
 * 支持移除单个条目；播放成功后由事件回调导航到播放守卫路由。
 */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onPlayVideo: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val coverUrls by viewModel.coverUrls.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var orderedItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) { orderedItems = items }

    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var removeTarget by remember { mutableStateOf<PlaylistItemEntity?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showMergePicker by remember { mutableStateOf(false) }
    var pickerAction by remember { mutableStateOf<SelectionAction?>(null) }
    var showRemoveSelected by remember { mutableStateOf(false) }
    var showDeletePlaylist by remember { mutableStateOf(false) }

    val filteredItems = remember(orderedItems, searchQuery, searchActive) {
        if (!searchActive || searchQuery.isBlank()) orderedItems
        else orderedItems.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
    }

    val listState = rememberReorderableLazyListState(
        onMove = { from, to ->
            orderedItems = orderedItems.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        onDragEnd = { _, _ ->
            viewModel.persistOrder(orderedItems)
        },
    )

    // 长按拖拽排序仅在编辑模式下启用；普通模式长按用于进入编辑模式
    val reorderModifier = if (editMode) {
        Modifier
            .reorderable(listState)
            .detectReorderAfterLongPress(listState)
    } else {
        Modifier
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistDetailEvent.NavigateToPlayer -> onPlayVideo()
                is PlaylistDetailEvent.ShowError -> snackbarHostState.showNiMessage(NiMessage.error(event.message))
                is PlaylistDetailEvent.ShowMessage -> snackbarHostState.showNiMessage(NiMessage.info(event.message))
                PlaylistDetailEvent.PlaylistDeleted -> onBack()
            }
        }
    }

    fun exitEditMode() {
        editMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onBack = {
                        searchActive = false
                        searchQuery = ""
                    },
                )
            } else if (editMode) {
                NiTopBar(
                    title = stringResource(R.string.playlist_detail_edit_mode),
                    navigationIcon = {
                        IconButton(onClick = { exitEditMode() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.playlist_detail_exit_edit),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { exitEditMode() }) {
                            Text(stringResource(R.string.playlist_detail_done), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            } else {
                NiTopBar(
                    title = playlist?.name ?: stringResource(R.string.playlist_detail_title_fallback),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        if (orderedItems.isNotEmpty()) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                        }
                        IconButton(onClick = { showManage = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.playlist_detail_manage),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (editMode) {
                SelectionActionBar(
                    selectedCount = selectedIds.size,
                    totalCount = filteredItems.size,
                    onToggleSelectAll = {
                        selectedIds = if (selectedIds.size == filteredItems.size && filteredItems.isNotEmpty()) {
                            emptySet()
                        } else {
                            filteredItems.map { it.id }.toSet()
                        }
                    },
                    onMove = { pickerAction = SelectionAction.MOVE },
                    onCopy = { pickerAction = SelectionAction.COPY },
                    onRemove = { showRemoveSelected = true },
                )
            }
        },
        snackbarHost = {
            NiSnackbarHost(
                hostState = snackbarHostState,
                bottomObstruction = NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION,
            )
        },
    ) { padding ->
        when {
            playlist == null -> PlaylistDetailSkeleton(padding)
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        // 搜索激活时点击内容任意区域即收起（Compose 点击不可聚焦组件不会自动移焦）
                        .pointerInput(searchActive) {
                            if (searchActive) {
                                detectTapGestures {
                                    searchActive = false
                                    searchQuery = ""
                                }
                            }
                        },
                ) {
                    PlaylistPlayAllHeader(
                        count = orderedItems.size,
                        enabled = orderedItems.isNotEmpty(),
                        onPlayAll = viewModel::playAll,
                    )
                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (orderedItems.isEmpty()) {
                                NiEmptyState(
                                    icon = Icons.Rounded.QueueMusic,
                                    text = stringResource(R.string.playlist_detail_empty),
                                    hint = stringResource(R.string.playlist_detail_empty_hint),
                                )
                            } else {
                                NiEmptyState(
                                    icon = Icons.Rounded.MusicNote,
                                    text = stringResource(R.string.playlist_detail_no_match),
                                    hint = stringResource(R.string.playlist_detail_no_match_hint),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(reorderModifier),
                            state = listState.listState,
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            items(
                                items = filteredItems,
                                key = { it.id },
                            ) { item ->
                                val index = orderedItems.indexOf(item)
                                ReorderableItem(
                                    state = listState,
                                    key = item.id,
                                ) { isDragging ->
                                    PlaylistItemRow(
                                        item = item,
                                        coverUrl = coverUrls[item.filePath],
                                        isDragging = isDragging,
                                        editMode = editMode,
                                        selected = item.id in selectedIds,
                                        onToggleSelect = {
                                            selectedIds = if (item.id in selectedIds) {
                                                selectedIds - item.id
                                            } else {
                                                selectedIds + item.id
                                            }
                                        },
                                        onPlay = { viewModel.playItem(index) },
                                        onLongPress = {
                                            searchActive = false
                                            searchQuery = ""
                                            selectedIds = emptySet()
                                            editMode = true
                                        },
                                        onRemove = { removeTarget = item },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    removeTarget?.let { target ->
        NiConfirmDialog(
            title = stringResource(R.string.playlist_detail_remove),
            text = stringResource(R.string.playlist_detail_remove_confirm, target.fileName),
            confirmText = stringResource(R.string.playlist_detail_remove_confirm_text),
            onConfirm = {
                viewModel.removeItem(target.id)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }

    if (showManage) {
        playlist?.let { p ->
            PlaylistManageSheet(
                playlist = PlaylistWithCount(playlist = p, itemCount = orderedItems.size),
                onDismiss = { showManage = false },
                onRename = {
                    showRename = true
                    showManage = false
                },
                onDuplicate = {
                    viewModel.duplicatePlaylist()
                    showManage = false
                },
                onMerge = {
                    showMergePicker = true
                    showManage = false
                },
                onTogglePin = {
                    viewModel.togglePinned(!p.isPinned)
                    showManage = false
                },
                onDelete = {
                    showDeletePlaylist = true
                    showManage = false
                },
            )
        }
    }

    if (showRename) {
        playlist?.let { p ->
            RenamePlaylistDialog(
                initialName = p.name,
                onConfirm = { name ->
                    viewModel.renamePlaylist(name)
                    showRename = false
                },
                onDismiss = { showRename = false },
            )
        }
    }

    if (showMergePicker) {
        PlaylistPickerSheet(
            title = stringResource(R.string.playlist_detail_merge_title, playlist?.name ?: ""),
            playlists = allPlaylists.filter { it.playlist.id != playlist?.id },
            onDismiss = { showMergePicker = false },
            onPick = { target ->
                viewModel.mergeInto(target.playlist.id)
                showMergePicker = false
            },
        )
    }

    pickerAction?.let { action ->
        PlaylistPickerSheet(
            title = when (action) {
                SelectionAction.MOVE -> stringResource(R.string.playlist_detail_move_title)
                SelectionAction.COPY -> stringResource(R.string.playlist_detail_copy_title)
            },
            playlists = allPlaylists.filter { it.playlist.id != playlist?.id },
            onDismiss = { pickerAction = null },
            onPick = { target ->
                val ids = selectedIds.toList()
                when (action) {
                    SelectionAction.MOVE -> viewModel.moveSelectedTo(target.playlist.id, ids)
                    SelectionAction.COPY -> viewModel.copySelectedTo(target.playlist.id, ids)
                }
                pickerAction = null
                exitEditMode()
            },
        )
    }

    if (showRemoveSelected) {
        NiConfirmDialog(
            title = stringResource(R.string.playlist_detail_remove_selected_title),
            text = stringResource(R.string.playlist_detail_remove_selected_confirm, selectedIds.size),
            confirmText = stringResource(R.string.playlist_detail_remove_selected_confirm_text),
            onConfirm = {
                viewModel.removeItems(selectedIds.toList())
                selectedIds = emptySet()
                showRemoveSelected = false
            },
            onDismiss = { showRemoveSelected = false },
        )
    }

    if (showDeletePlaylist) {
        playlist?.let { p ->
            NiConfirmDialog(
                title = stringResource(R.string.playlist_detail_delete_title),
                text = stringResource(R.string.playlist_detail_delete_confirm, p.name, orderedItems.size),
                confirmText = stringResource(R.string.delete),
                confirmDanger = true,
                onConfirm = {
                    viewModel.deletePlaylist()
                    showDeletePlaylist = false
                },
                onDismiss = { showDeletePlaylist = false },
            )
        }
    }
}

/** 顶部「播放全部」操作区。 */
@Composable
private fun PlaylistPlayAllHeader(
    count: Int,
    enabled: Boolean,
    onPlayAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        val bgColor = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .clickable(enabled = enabled, onClick = onPlayAll)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.playlist_detail_play_all, count),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
        }
        Text(
            text = stringResource(R.string.playlist_detail_reorder_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

/** 歌单条目行：封面缩略图 + 文件名 + 大小；普通模式点击播放、长按进入编辑模式；编辑模式下勾选批量操作，长按可拖拽排序。 */
@Composable
private fun PlaylistItemRow(
    item: PlaylistItemEntity,
    coverUrl: String?,
    isDragging: Boolean,
    editMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    val isVideo = MediaFileTypes.isVideoFile(item.fileName)
    val isAudio = MediaFileTypes.isAudioFile(item.fileName)
    val isImage = MediaFileTypes.isImageFile(item.fileName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> NiExtraColors.current.surfaceLevel2
                }
            )
            .combinedClickable(
                onClick = if (editMode) onToggleSelect else onPlay,
                onLongClick = if (editMode) null else onLongPress,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 编辑模式下的多选勾选框
        if (editMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NiExtraColors.current.surfaceLevel3),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = when {
                        isImage -> Icons.Rounded.Image
                        isAudio -> Icons.Rounded.MusicNote
                        else -> Icons.Rounded.Movie
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatFileSize(item.fileSize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(4.dp))
        // 仅编辑模式显示移出按钮，避免常驻图标误触
        if (editMode) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.playlist_detail_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 编辑模式底部批量操作栏：全选 / 已选计数 / 移到歌单 / 复制到歌单 / 移除。 */
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onToggleSelectAll: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleSelectAll) {
                    Text(
                        text = if (totalCount > 0 && selectedCount == totalCount) {
                            stringResource(R.string.playlist_detail_cancel_select_all)
                        } else {
                            stringResource(R.string.playlist_detail_select_all)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = stringResource(R.string.playlist_detail_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onMove,
                    enabled = selectedCount > 0,
                ) {
                    Text(stringResource(R.string.playlist_detail_move_to_playlist))
                }
                TextButton(
                    onClick = onCopy,
                    enabled = selectedCount > 0,
                ) {
                    Text(stringResource(R.string.playlist_detail_copy_to_playlist))
                }
                IconButton(
                    onClick = onRemove,
                    enabled = selectedCount > 0,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.playlist_detail_remove_selected_title),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 搜索态顶栏：56dp 高容纳完整输入框，进入自动聚焦，失焦自动收起。 */
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // 标记是否已真正获得过焦点：避免组合初期的 isFocused=false 回调误触发收起
    var hadFocus by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.playlist_detail_exit_search),
            )
        }
        NiTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        onBack()
                    }
                },
            placeholder = stringResource(R.string.playlist_detail_search_placeholder),
            singleLine = true,
        )
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/** 加载骨架。 */
@Composable
private fun PlaylistDetailSkeleton(padding: PaddingValues) {
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NiExtraColors.current.surfaceLevel3),
        )
        Spacer(Modifier.height(16.dp))
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NiExtraColors.current.surfaceLevel3),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} B"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}
