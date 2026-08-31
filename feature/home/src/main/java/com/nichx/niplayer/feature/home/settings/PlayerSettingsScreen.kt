package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.datastore.PlayerControlEntry
import com.nichx.niplayer.datastore.PlayerControlLayout
import com.nichx.niplayer.datastore.PlayerControlSurface
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar

/** assrt.net 登录注册页，用于 ASSRT Token 指引链接跳转。 */
private const val ASSRT_URL = "https://secure.assrt.net/user/logon.xml"

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
    var orientationMode by remember { mutableStateOf(PlayerSettings.orientationMode) }
    var autoPip by remember { mutableStateOf(PlayerSettings.autoPip) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showOrientationHintDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showLongPressDialog by remember { mutableStateOf(false) }
    var showSeekDialog by remember { mutableStateOf(false) }
    var showDoubleTapDialog by remember { mutableStateOf(false) }
    var showControlCustomize by remember { mutableStateOf(false) }

    // 控制栏自定义为独立子页面（占满整屏、含返回栏），进入时整页替换设置页内容。
    if (showControlCustomize) {
        PlayerControlCustomizeScreen(onBack = { showControlCustomize = false })
        return
    }

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
    val orientationModeLabel = when (orientationMode) {
        1 -> stringResource(R.string.player_orientation_portrait)
        2 -> stringResource(R.string.player_orientation_auto)
        else -> stringResource(R.string.player_orientation_landscape)
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
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_orientation_mode),
                    value = orientationModeLabel,
                    infoOnClick = { showOrientationHintDialog = true },
                    onClick = { showOrientationDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    label = stringResource(R.string.player_auto_pip),
                    description = stringResource(R.string.player_auto_pip_desc),
                    checked = autoPip,
                    onCheckedChange = {
                        autoPip = it
                        PlayerSettings.autoPip = it
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = stringResource(R.string.player_ctrl_customize),
                    value = stringResource(R.string.player_ctrl_customize_desc),
                    onClick = { showControlCustomize = true },
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

    if (showOrientationDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_orientation_title),
            onDismiss = { showOrientationDialog = false },
            items = listOf(
                0 to stringResource(R.string.player_orientation_landscape),
                1 to stringResource(R.string.player_orientation_portrait),
                2 to stringResource(R.string.player_orientation_auto),
            )
                .map { (option, label) ->
                    NiDialogItem(
                        label = label,
                        isSelected = orientationMode == option,
                        onClick = {
                            orientationMode = option
                            PlayerSettings.orientationMode = option
                            showOrientationDialog = false
                        },
                    )
                },
        )
    }

    if (showOrientationHintDialog) {
        NiInfoDialog(
            title = stringResource(R.string.player_orientation_title),
            onDismiss = { showOrientationHintDialog = false },
        ) {
            Text(
                stringResource(R.string.player_orientation_auto_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
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
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(R.string.player_assrt_token_guide_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            val uriHandler = LocalUriHandler.current
            Text(
                text = stringResource(R.string.player_assrt_token_guide_link),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        onClick = { runCatching { uriHandler.openUri(ASSRT_URL) } },
                    )
                    .padding(4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
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
