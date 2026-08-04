package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.player.kernel.PlaylistItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    playMode: Int,
    onDismiss: () -> Unit,
    onPlayAtIndex: (Int) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (playlist.isNotEmpty()) {
                        stringResource(R.string.player_playlist_with_count, playlist.size)
                    } else {
                        stringResource(R.string.player_playlist)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface,
                    fontWeight = FontWeight.Bold,
                )

                val modeLabel = when (playMode % 3) {
                    0 -> stringResource(R.string.player_play_mode_order)
                    1 -> stringResource(R.string.player_play_mode_shuffle)
                    else -> stringResource(R.string.player_play_mode_single)
                }
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.6f),
                )
            }

            if (playlist.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                )
            } else {
                val listState = rememberLazyListState()

                LaunchedEffect(currentIndex) {
                    if (currentIndex in playlist.indices) {
                        listState.scrollToItem(currentIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    itemsIndexed(
                        items = playlist,
                        key = { index, item -> item.filePath },
                    ) { index, item ->
                        val isCurrent = index == currentIndex
                        PlaylistItemRow(
                            title = item.fileName,
                            index = index + 1,
                            isCurrent = isCurrent,
                            onClick = { onPlayAtIndex(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    title: String,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val bgColor = if (isCurrent) {
        primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodySmall,
                color = onSurface.copy(alpha = 0.4f),
                modifier = Modifier.width(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (isCurrent) primary else onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) onSurface else onSurface.copy(alpha = 0.65f),
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
