package com.nichx.niplayer.feature.home.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar
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

    LaunchedEffect(Unit) {
        viewModel.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "我的歌单",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (playlists.isNotEmpty()) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "新建歌单",
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
                        text = "暂无歌单",
                        hint = "在播放页可将连播内容保存为歌单",
                        actionText = "新建歌单",
                        onAction = { showCreateDialog = true },
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = "共 ${playlists.size} 个歌单",
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
                                onDelete = { deleteTarget = playlist },
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
            title = "删除歌单",
            text = "确定删除歌单「${target.playlist.name}」？${target.itemCount} 个条目将一并移除。",
            onConfirm = {
                viewModel.deletePlaylist(target.playlist.id, target.playlist.name)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: PlaylistWithCount,
    coverUrl: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onDelete,
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
            // 条目数角标（跟随封面底色，无黑底）
            Text(
                text = "${playlist.itemCount} 个条目",
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
        title = "新建歌单",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
    ) {
        NiTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = "歌单名称",
            placeholder = "例如：我的最爱",
        )
    }
}
