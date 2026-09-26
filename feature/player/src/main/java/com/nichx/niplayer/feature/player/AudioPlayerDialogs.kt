package com.nichx.niplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.designsystem.components.NiAutoFocusAndShowKeyboard
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.metadata.model.CandidateSource
import com.nichx.niplayer.metadata.model.MatchCandidate
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

/**
 * 候选选择对话框：列出本地推断出的匹配候选，点选即采用。
 *
 * 候选来自 `buildCandidates`（内嵌标签 > 文件名解析 > 目录线索）。选中后走与
 * [ManualMatchLyricsDialog] 完全相同的链路 —— 写入 `audio_match` 并置 `locked = 1`，
 * 因此之后不会再被自动匹配覆盖。
 *
 * 每项尾部标注**来源**：内嵌标签可信度最高、目录线索最弱，用户据此判断该选哪个；
 * 旧版只显示「歌名 - 歌手」，同名不同来源的两条候选在界面上完全无法区分。
 *
 * 两者的分工：这个用于「候选里有想要的」时一键选中，那个用于「都没有」时手动输入。
 */
@Composable
internal fun MatchCandidateDialog(
    candidates: List<MatchCandidate>,
    onDismiss: () -> Unit,
    onPick: (title: String, artist: String) -> Unit,
) {
    val sourceLabels = mapOf(
        CandidateSource.ID3 to stringResource(R.string.player_lyrics_candidate_source_id3),
        CandidateSource.FILENAME to stringResource(R.string.player_lyrics_candidate_source_filename),
        CandidateSource.DIRECTORY to stringResource(R.string.player_lyrics_candidate_source_directory),
        CandidateSource.ONLINE to stringResource(R.string.player_lyrics_candidate_source_online),
    )
    val items = candidates.map { candidate ->
        val base = if (candidate.artist.isBlank()) {
            candidate.title
        } else {
            "${candidate.title} - ${candidate.artist}"
        }
        NiDialogItem(
            label = "$base · ${sourceLabels.getValue(candidate.source)}",
            onClick = { onPick(candidate.title, candidate.artist) },
        )
    }

    if (items.isEmpty()) {
        // 候选为空时给明确指引，而不是弹一个空列表
        NiInfoDialog(
            title = stringResource(R.string.player_lyrics_pick_candidate_title),
            onDismiss = onDismiss,
            actions = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.player_cancel))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.player_lyrics_pick_candidate_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    NiListItemDialog(
        title = stringResource(R.string.player_lyrics_pick_candidate_title),
        items = items,
        onDismiss = onDismiss,
    )
}
