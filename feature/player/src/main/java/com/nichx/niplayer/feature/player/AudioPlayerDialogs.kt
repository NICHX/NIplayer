package com.nichx.niplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.designsystem.components.NiAutoFocusAndShowKeyboard
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp


/** 手动输入歌名/歌手进行在线歌词精确匹配的对话框。 */
@Composable
internal fun ManualMatchLyricsDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, artist: String) -> Unit,
) {
    var titleQuery by remember { mutableStateOf("") }
    var artistQuery by remember { mutableStateOf("") }
    val titleFocus = remember { FocusRequester() }
    NiInfoDialog(
        title = stringResource(R.string.player_lyrics_manual_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_cancel))
            }
            TextButton(
                onClick = {
                    if (titleQuery.isNotBlank()) onConfirm(titleQuery, artistQuery)
                },
                enabled = titleQuery.isNotBlank(),
            ) {
                Text(stringResource(R.string.player_confirm))
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.player_lyrics_manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NiTextField(
                value = titleQuery,
                onValueChange = { titleQuery = it },
                label = stringResource(R.string.player_lyrics_manual_title_label),
                placeholder = stringResource(R.string.player_lyrics_manual_title_label),
                modifier = Modifier.focusRequester(titleFocus),
            )
            // 输入框挂载后（延迟渲染的浮层内容）自动聚焦并拉起输入法
            NiAutoFocusAndShowKeyboard(titleFocus)
            NiTextField(
                value = artistQuery,
                onValueChange = { artistQuery = it },
                label = stringResource(R.string.player_lyrics_manual_artist_label),
                placeholder = stringResource(R.string.player_lyrics_manual_artist_label),
            )
        }
    }
}
