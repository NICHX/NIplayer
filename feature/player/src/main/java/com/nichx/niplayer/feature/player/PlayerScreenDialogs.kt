package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.nichx.niplayer.player.kernel.PlaylistItem

@Composable
internal fun PlaylistDialog(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    onPlayAtIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.player_episode_list, currentIndex + 1, playlist.size),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((playlist.size.coerceAtMost(8) * 52).dp),
                ) {
                    itemsIndexed(playlist) { index, item ->
                        val isCurrent = index == currentIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable { onPlayAtIndex(index) }
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) primary
                                else onSurface.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.width(28.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.fileName,
                                color = if (isCurrent) primary else onSurface,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookmarkListDialog(
    bookmarks: List<VideoBookmarkEntity>,
    onSeek: (Long) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val onSurface = PlayerDialogColors.textPrimary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 260.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.player_bookmark_list, bookmarks.size),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PlayerDialogDivider()
                if (bookmarks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_bookmark_empty),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        color = onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((bookmarks.size.coerceAtMost(8) * 52).dp),
                    ) {
                        itemsIndexed(bookmarks) { _, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSeek(item.positionMs) }
                                    .padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.label?.takeIf { it.isNotBlank() } ?: formatDuration(item.positionMs),
                                    color = onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.label != null) {
                                    Text(
                                        text = formatDuration(item.positionMs),
                                        color = onSurface.copy(alpha = 0.4f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(item.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.player_delete_bookmark),
                                        tint = onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
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
