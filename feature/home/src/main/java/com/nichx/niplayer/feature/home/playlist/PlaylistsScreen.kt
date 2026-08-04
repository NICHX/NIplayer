package com.nichx.niplayer.feature.home.playlist

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.showNiMessage
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/**
 * 「我的歌单」列表页（扩展功能方案二 · 页面 1）。
 *
 * 网格卡片展示全部歌单（名称 + 条目数），支持新建 / 长按删除。
 */
@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Int) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    val coverUrls by viewModel.coverUrls.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var manageTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var renameTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }
    var mergeSource by remember { mutableStateOf<PlaylistWithCount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showNiMessage(NiMessage.info(it)) }
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.playlists_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (playlists.isNotEmpty()) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.playlists_create_title),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when {
            !dataReady -> PlaylistsSkeleton(padding)
            playlists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    NiEmptyState(
                        icon = Icons.Rounded.QueueMusic,
                        text = stringResource(R.string.playlists_empty),
                        hint = stringResource(R.string.playlists_empty_hint),
                        actionText = stringResource(R.string.playlists_create_title),
                        onAction = { showCreateDialog = true },
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = stringResource(R.string.playlists_count, playlists.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = playlists,
                            key = { it.playlist.id },
                        ) { playlist ->
                            PlaylistGridCard(
                                playlist = playlist,
                                coverUrl = coverUrls[playlist.playlist.id],
                                onClick = { onOpenPlaylist(playlist.playlist.id) },
                                onManage = { manageTarget = playlist },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    deleteTarget?.let { target ->
        NiConfirmDialog(
            title = stringResource(R.string.playlists_delete),
            text = stringResource(R.string.playlists_delete_confirm, target.playlist.name, target.itemCount),
            onConfirm = {
                viewModel.deletePlaylist(target.playlist.id, target.playlist.name)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    manageTarget?.let { target ->
        PlaylistManageSheet(
            playlist = target,
            onDismiss = { manageTarget = null },
            onRename = {
                renameTarget = target
                manageTarget = null
            },
            onDuplicate = {
                viewModel.duplicatePlaylist(target.playlist.id, target.playlist.name)
                manageTarget = null
            },
            onMerge = {
                mergeSource = target
                manageTarget = null
            },
            onTogglePin = {
                viewModel.togglePinned(
                    target.playlist.id,
                    !target.playlist.isPinned,
                    target.playlist.name,
                )
                manageTarget = null
            },
            onDelete = {
                deleteTarget = target
                manageTarget = null
            },
        )
    }

    renameTarget?.let { target ->
        RenamePlaylistDialog(
            initialName = target.playlist.name,
            onConfirm = { name ->
                viewModel.renamePlaylist(target.playlist.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    mergeSource?.let { source ->
        PlaylistPickerSheet(
            title = stringResource(R.string.playlists_merge_title, source.playlist.name),
            playlists = playlists.filter { it.playlist.id != source.playlist.id },
            onDismiss = { mergeSource = null },
            onPick = { target ->
                viewModel.mergePlaylist(
                    source.playlist.id,
                    source.playlist.name,
                    target.playlist.id,
                    target.playlist.name,
                )
                mergeSource = null
            },
        )
    }
}

/** 长按歌单弹出的管理操作底部弹层（详情页顶栏菜单复用）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistManageSheet(
    playlist: PlaylistWithCount,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMerge: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = playlist.playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            ManageActionRow(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.playlists_rename),
                onClick = onRename,
            )
            ManageActionRow(
                icon = Icons.Outlined.Add,
                label = stringResource(R.string.playlists_duplicate),
                onClick = onDuplicate,
            )
            ManageActionRow(
                icon = Icons.Outlined.List,
                label = stringResource(R.string.playlists_merge_to),
                onClick = onMerge,
            )
            ManageActionRow(
                icon = Icons.Outlined.KeyboardArrowUp,
                label = if (playlist.playlist.isPinned) {
                    stringResource(R.string.playlists_unpin)
                } else {
                    stringResource(R.string.playlists_pin)
                },
                onClick = onTogglePin,
            )
            ManageActionRow(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.playlists_delete),
                onClick = onDelete,
                danger = true,
            )
        }
    }
}

/** 管理弹层单行操作。 */
@Composable
private fun ManageActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

/** 重命名歌单对话框（预填当前名称，详情页复用）。 */
@Composable
internal fun RenamePlaylistDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    NiInfoDialog(
        title = stringResource(R.string.playlists_rename_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        NiTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.playlists_name_label),
        )
    }
}

/** 目标歌单选择底部弹层（排除调用方自行过滤后传入的列表）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistPickerSheet(
    title: String,
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onPick: (PlaylistWithCount) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlists_no_other_playlist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(
                        items = playlists,
                        key = { it.playlist.id },
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.playlists_item_count, item.itemCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: PlaylistWithCount,
    coverUrl: String?,
    onClick: () -> Unit,
    onManage: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onManage,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
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
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            // 置顶角标
            if (playlist.playlist.isPinned) {
                Text(
                    text = stringResource(R.string.playlists_pinned_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            // 条目数角标（跟随封面底色，无黑底）
            Text(
                text = stringResource(R.string.playlists_item_count, playlist.itemCount),
                style = MaterialTheme.typography.labelSmall,
                color = if (coverUrl != null) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (coverUrl != null) {
                            Color.Black.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        NiAutoSizeText(
            text = playlist.playlist.name,
            minFontSize = 11.sp,
            maxFontSize = 14.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaylistsSkeleton(padding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items(6) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NiExtraColors.current.surfaceLevel3),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NiExtraColors.current.surfaceLevel3),
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    NiInfoDialog(
        title = stringResource(R.string.playlists_create_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.create)) }
        },
    ) {
        NiTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.playlists_name_label),
            placeholder = stringResource(R.string.playlists_name_placeholder),
        )
    }
}
