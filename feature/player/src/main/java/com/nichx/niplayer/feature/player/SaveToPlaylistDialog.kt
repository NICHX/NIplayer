package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.dao.PlaylistWithCount
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.player.kernel.PlaylistItem

/**
 * 播放页「保存为歌单」Dialog（扩展功能方案二 · 入口①）。
 *
 * 将当前播放列表保存到已有歌单或新建歌单；重复文件由唯一索引自动跳过。
 * 保存成功后通过 [onSaved] 回调消息文本，由调用方提示。
 *
 * 容器使用设计系统主题自适应的 [NiInfoDialog]（浅色/深色跟随应用主题），
 * 不复用播放器视频场景专用的暗色 [PlayerDialog]。
 */
@Composable
fun SaveToPlaylistDialog(
    playlist: List<PlaylistItem>,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: PlaylistSaveViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<SavePlaylistTarget>(SavePlaylistTarget.New) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaylistSaveEvent.Saved -> {
                    val message = if (event.addedCount == 0) {
                        "所选文件已全部存在于歌单「${event.playlistName}」"
                    } else {
                        "已保存 ${event.addedCount} 个文件到歌单「${event.playlistName}」"
                    }
                    onSaved(message)
                    onDismiss()
                }
            }
        }
    }

    val canSave = when (target) {
        is SavePlaylistTarget.Existing -> true
        SavePlaylistTarget.New -> newName.isNotBlank()
    }

    NiInfoDialog(
        title = "保存为歌单",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    viewModel.save(target, newName, playlist)
                },
                enabled = canSave,
            ) {
                Text(
                    "保存",
                    color = if (canSave) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = "当前播放列表（${playlist.size} 个文件）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        SaveTargetRow(
            label = "新建歌单",
            hint = "为当前播放列表创建新歌单",
            selected = target is SavePlaylistTarget.New,
            onClick = { target = SavePlaylistTarget.New },
        )
        if (target is SavePlaylistTarget.New) {
            Spacer(Modifier.height(8.dp))
            NiTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                label = "歌单名称",
                placeholder = "例如：我的最爱",
            )
        }

        if (playlists.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "保存到已有歌单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(
                    items = playlists,
                    key = { it.playlist.id },
                ) { item ->
                    SavePlaylistRow(
                        item = item,
                        selected = (target as? SavePlaylistTarget.Existing)?.playlistId == item.playlist.id,
                        onClick = {
                            target = SavePlaylistTarget.Existing(item.playlist.id)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SaveTargetRow(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SavePlaylistRow(
    item: PlaylistWithCount,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.playlist.name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.itemCount} 个条目",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
