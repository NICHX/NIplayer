package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.AudioPlayerStyle
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.nichx.niplayer.datastore.CoverLabelFontStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频播放器设置页：只保留与音频“播放行为 / 外观”直接相关的设置。
 *
 * 视频专属设置（内核、字幕、手势、方向、控制栏）见 [VideoPlayerSettingsScreen]。
 *
 * 两个分组：
 * - **音频播放**：记住音频播放进度、进度记录门槛、倍速音调保持
 * - **外观**：音频播放器外观样式
 *
 * @param onBack 返回回调
 */
@Composable
fun AudioPlayerSettingsScreen(
    onBack: () -> Unit = {},
) {
    var rememberAudioProgress by remember { mutableStateOf(PlayerSettings.rememberAudioProgress) }
    var minMinutes by remember { mutableStateOf(PlayerSettings.audioProgressMinDurationMinutes) }
    var pitchPreservation by remember { mutableStateOf(PlayerSettings.pitchPreservationEnabled) }
    var audioPlayerStyle by remember { mutableStateOf(PlayerSettings.audioPlayerStyle) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }

    // 生成封面的字体：应用不内置字体（CJK 手写体近 3MB），用户可自选一个字体文件，
    // 导入时会被复制到应用私有目录（见 CoverLabelFontStore）。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var coverFontName by remember { mutableStateOf(PlayerSettings.coverLabelFontName) }
    var coverFontRejected by remember { mutableStateOf(false) }
    val pickCoverFont = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { CoverLabelFontStore.apply(context, uri) }
                if (ok) coverFontName = PlayerSettings.coverLabelFontName else coverFontRejected = true
            }
        }
    }

    val audioStyleLabel = when (audioPlayerStyle) {
        AudioPlayerStyle.APPLE_MUSIC -> stringResource(R.string.player_audio_appearance_apple_music)
        AudioPlayerStyle.GLASS -> stringResource(R.string.player_audio_appearance_glass)
        AudioPlayerStyle.VINYL -> stringResource(R.string.player_audio_appearance_vinyl)
    }
    val minMinutesLabel = stringResource(R.string.player_audio_progress_threshold_value, minMinutes)

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.player_audio_settings_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            SettingsGroupSection(
                title = stringResource(R.string.player_audio_group_playback),
                icon = Icons.Filled.MusicNote,
                iconBg = Color(0xFF1DB954),
            ) {
                SettingSwitchRow(
                    label = stringResource(R.string.player_remember_audio_progress),
                    description = stringResource(R.string.player_remember_audio_progress_desc),
                    checked = rememberAudioProgress,
                    onCheckedChange = {
                        rememberAudioProgress = it
                        PlayerSettings.rememberAudioProgress = it
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_audio_progress_threshold),
                    value = minMinutesLabel,
                    onClick = { showThresholdDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    label = stringResource(R.string.player_pitch_preserve),
                    description = stringResource(R.string.player_pitch_preserve_desc),
                    checked = pitchPreservation,
                    onCheckedChange = {
                        pitchPreservation = it
                        PlayerSettings.pitchPreservationEnabled = it
                    },
                )
            }

            SettingsGroupSection(
                title = stringResource(R.string.player_audio_group_appearance),
                icon = Icons.Filled.Palette,
                iconBg = Color(0xFF00ACC1),
            ) {
                SettingClickRow(
                    label = stringResource(R.string.player_audio_appearance),
                    value = audioStyleLabel,
                    onClick = { showAppearanceDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_cover_font),
                    value = coverFontName.ifEmpty { stringResource(R.string.player_cover_font_default) },
                    description = stringResource(R.string.player_cover_font_desc),
                    onClick = { pickCoverFont.launch("*/*") },
                )
                if (coverFontName.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingClickRow(
                        label = stringResource(R.string.player_cover_font_reset),
                        value = "",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { CoverLabelFontStore.apply(context, null) }
                                coverFontName = ""
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }

    if (coverFontRejected) {
        AlertDialog(
            onDismissRequest = { coverFontRejected = false },
            title = { Text(stringResource(R.string.player_cover_font)) },
            text = { Text(stringResource(R.string.player_cover_font_invalid)) },
            confirmButton = {
                TextButton(onClick = { coverFontRejected = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    if (showAppearanceDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_audio_appearance),
            onDismiss = { showAppearanceDialog = false },
            items = listOf(
                AudioPlayerStyle.VINYL to stringResource(R.string.player_audio_appearance_vinyl),
                AudioPlayerStyle.GLASS to stringResource(R.string.player_audio_appearance_glass),
                AudioPlayerStyle.APPLE_MUSIC to stringResource(R.string.player_audio_appearance_apple_music),
            )
                .map { (option, label) ->
                    NiDialogItem(
                        label = label,
                        isSelected = audioPlayerStyle == option,
                        onClick = {
                            audioPlayerStyle = option
                            PlayerSettings.audioPlayerStyle = option
                            showAppearanceDialog = false
                        },
                    )
                },
        )
    }

    if (showThresholdDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_audio_progress_threshold_title),
            onDismiss = { showThresholdDialog = false },
            items = PlayerSettings.AUDIO_PROGRESS_MIN_MINUTES_OPTIONS
                .map { minutes ->
                    NiDialogItem(
                        label = stringResource(R.string.player_audio_progress_threshold_value, minutes),
                        isSelected = minMinutes == minutes,
                        onClick = {
                            minMinutes = minutes
                            PlayerSettings.audioProgressMinDurationMinutes = minutes
                            showThresholdDialog = false
                        },
                    )
                },
        )
    }
}
