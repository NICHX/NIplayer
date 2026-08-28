package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar

/**
 * 播放器设置页：只保留与“播放行为”直接相关的设置。
 *
 * 三个分组（与播放器语义无关的缩略图/存储源缩略图/文件浏览设置已迁移到
 * [MediaLibrarySettingsScreen]）：
 * - **播放器内核**：内核信息、倍速音调保持
 * - **字幕**：自动加载同名字幕、字幕优先级、ASSRT Token、字幕样式
 * - **手势与进度**：长按倍速、滑动灵敏度、双击快进
 *
 * @param onBack 返回回调
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit = {},
) {
    var autoLoadSubtitle by remember { mutableStateOf(SubtitleSettings.autoLoadSameNameSubtitle) }
    var subtitlePriority by remember { mutableStateOf(SubtitleSettings.subtitlePriority) }
    var pitchPreservation by remember { mutableStateOf(PlayerSettings.pitchPreservationEnabled) }
    var longPressTimeoutMs by remember { mutableStateOf(PlayerSettings.longPressTimeoutMs) }
    var seekSensitivity by remember { mutableStateOf(PlayerSettings.seekSensitivity) }
    var doubleTapStepSeconds by remember { mutableStateOf(PlayerSettings.doubleTapStepSeconds) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showLongPressDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showDoubleTapDialog by remember { mutableStateOf(false) }

    val longPressTimeoutLabel = when (longPressTimeoutMs) {
        250 -> stringResource(R.string.player_speed_fast)
        400 -> stringResource(R.string.player_speed_slow)
        else -> stringResource(R.string.player_speed_standard)
    }
    val seekSensitivityLabel = when (seekSensitivity) {
        1.0f -> stringResource(R.string.player_slide_sensitive_short)
        2.0f -> stringResource(R.string.player_slide_precise_short)
        else -> stringResource(R.string.player_speed_standard)
    }
    val doubleTapLabel = when (doubleTapStepSeconds) {
        0 -> stringResource(R.string.player_double_tap_off)
        else -> stringResource(R.string.player_double_tap_seconds, doubleTapStepSeconds)
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.player_settings_title),
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
                title = stringResource(R.string.player_kernel),
                icon = Icons.Filled.PlayCircleOutline,
                iconBg = Color(0xFF2095F4),
            ) {
                SettingInfoRow(label = stringResource(R.string.player_current_kernel), value = "AndroidX Media3")
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
                title = stringResource(R.string.player_subtitle_title),
                icon = Icons.Filled.Subtitles,
                iconBg = Color(0xFF9C27B0),
            ) {
                SettingSwitchRow(
                    label = stringResource(R.string.player_auto_subtitle),
                    description = stringResource(R.string.player_auto_subtitle_desc),
                    checked = autoLoadSubtitle,
                    onCheckedChange = {
                        autoLoadSubtitle = it
                        SubtitleSettings.autoLoadSameNameSubtitle = it
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_subtitle_priority),
                    value = subtitlePriority.ifEmpty { stringResource(R.string.player_subtitle_priority_default) },
                    onClick = { showPriorityDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = "ASSRT Token",
                    value = if (SubtitleSettings.assrtToken.isBlank()) stringResource(R.string.lrcapi_not_set) else stringResource(R.string.lrcapi_auth_set),
                    onClick = { showTokenDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingInfoRow(
                    label = stringResource(R.string.player_subtitle_style),
                    value = stringResource(R.string.player_subtitle_style_value),
                )
            }

            SettingsGroupSection(
                title = stringResource(R.string.player_progress_title),
                icon = Icons.Filled.Speed,
                iconBg = Color(0xFFFF6D00),
            ) {
                SettingClickRow(
                    label = stringResource(R.string.player_long_press_speed),
                    value = longPressTimeoutLabel,
                    onClick = { showLongPressDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_slide_seek_sensitivity),
                    value = seekSensitivityLabel,
                    onClick = { showSeekDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_double_tap_skip),
                    value = doubleTapLabel,
                    onClick = { showDoubleTapDialog = true },
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }

    if (showTokenDialog) {
        var token by rememberSaveable { mutableStateOf(SubtitleSettings.assrtToken) }
        NiInfoDialog(
            title = stringResource(R.string.player_assrt_token_title),
            onDismiss = { showTokenDialog = false },
            actions = {
                TextButton(onClick = { showTokenDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    SubtitleSettings.assrtToken = token
                    showTokenDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) {
            Text(
                stringResource(R.string.player_assrt_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.player_assrt_token_placeholder),
            )
        }
    }

    if (showPriorityDialog) {
        var priority by rememberSaveable { mutableStateOf(subtitlePriority) }
        NiInfoDialog(
            title = stringResource(R.string.player_priority_title),
            onDismiss = { showPriorityDialog = false },
            actions = {
                TextButton(onClick = { showPriorityDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    subtitlePriority = priority
                    SubtitleSettings.subtitlePriority = priority
                    showPriorityDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) {
            Text(
                stringResource(R.string.player_priority_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = priority,
                onValueChange = { priority = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.player_priority_placeholder),
            )
        }
    }

    if (showLongPressDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_long_press_title),
            onDismiss = { showLongPressDialog = false },
            items = listOf(
                250 to stringResource(R.string.player_long_press_fast),
                300 to stringResource(R.string.player_long_press_standard),
                400 to stringResource(R.string.player_long_press_slow),
            )
                .map { (option, label) ->
                    NiDialogItem(
                        label = label,
                        isSelected = longPressTimeoutMs == option,
                        onClick = {
                            longPressTimeoutMs = option
                            PlayerSettings.longPressTimeoutMs = option
                            showLongPressDialog = false
                        },
                    )
                },
        )
    }

    if (showSeekDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_slide_title),
            onDismiss = { showSeekDialog = false },
            items = listOf(
                1.0f to stringResource(R.string.player_slide_sensitive),
                1.5f to stringResource(R.string.player_slide_standard),
                2.0f to stringResource(R.string.player_slide_precise),
            )
                .map { (option, label) ->
                    NiDialogItem(
                        label = label,
                        isSelected = seekSensitivity == option,
                        onClick = {
                            seekSensitivity = option
                            PlayerSettings.seekSensitivity = option
                            showSeekDialog = false
                        },
                    )
                },
        )
    }

    if (showDoubleTapDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_double_tap_title),
            onDismiss = { showDoubleTapDialog = false },
            items = listOf(
                0 to stringResource(R.string.player_double_tap_off),
                5 to stringResource(R.string.player_double_tap_seconds, 5),
                10 to stringResource(R.string.player_double_tap_seconds, 10),
                30 to stringResource(R.string.player_double_tap_seconds, 30),
            )
                .map { (option, label) ->
                    NiDialogItem(
                        label = label,
                        isSelected = doubleTapStepSeconds == option,
                        onClick = {
                            doubleTapStepSeconds = option
                            PlayerSettings.doubleTapStepSeconds = option
                            showDoubleTapDialog = false
                        },
                    )
                },
        )
    }
}
