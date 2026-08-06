package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.datastore.ThumbnailFramePosition
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: PlayerSettingsViewModel = hiltViewModel(),
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

    var generateThumbnail by remember { mutableStateOf(ThumbnailSettings.generateThumbnail) }
    var generateForVideo by remember { mutableStateOf(ThumbnailSettings.generateForVideo) }
    var generateForImage by remember { mutableStateOf(ThumbnailSettings.generateForImage) }
    var generateForAudio by remember { mutableStateOf(ThumbnailSettings.generateForAudio) }
    var saveInSameDir by remember { mutableStateOf(ThumbnailSettings.saveInSameDir) }
    var updateOnExit by remember { mutableStateOf(ThumbnailSettings.updateOnExit) }
    var framePositionKey by remember { mutableStateOf(ThumbnailSettings.framePositionKey) }
    var customPositionSeconds by remember { mutableStateOf(ThumbnailSettings.customPositionSeconds) }
    var generationMode by remember { mutableStateOf(ThumbnailSettings.generationMode) }
    var showOnlyMediaFiles by remember { mutableStateOf(FileBrowserSettings.showOnlyMediaFiles) }
    var showHiddenFiles by remember { mutableStateOf(FileBrowserSettings.showHiddenFiles) }
    var showFramePositionDialog by remember { mutableStateOf(false) }
    var showCustomPositionDialog by remember { mutableStateOf(false) }
    var showStorageHelpDialog by remember { mutableStateOf(false) }
    var showGenerationModeDialog by remember { mutableStateOf(false) }
    var modeDialogLibId by remember { mutableStateOf<Int?>(null) }

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

    Scaffold(
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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

            SettingsGroupSection(
                title = stringResource(R.string.player_thumbnail_title),
                icon = Icons.Filled.Wallpaper,
                iconBg = Color(0xFF00ACC1),
            ) {
                SettingSwitchRow(
                    label = stringResource(R.string.player_thumbnail_enable),
                    description = stringResource(R.string.player_thumbnail_enable_desc),
                    checked = generateThumbnail,
                    onCheckedChange = {
                        generateThumbnail = it
                        ThumbnailSettings.generateThumbnail = it
                    },
                )
                if (generateThumbnail) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = stringResource(R.string.player_thumbnail_video),
                        description = stringResource(R.string.player_thumbnail_video_desc),
                        checked = generateForVideo,
                        onCheckedChange = {
                            generateForVideo = it
                            ThumbnailSettings.generateForVideo = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = stringResource(R.string.player_thumbnail_image),
                        description = stringResource(R.string.player_thumbnail_image_desc),
                        checked = generateForImage,
                        onCheckedChange = {
                            generateForImage = it
                            ThumbnailSettings.generateForImage = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = stringResource(R.string.player_thumbnail_audio),
                        description = stringResource(R.string.player_thumbnail_audio_desc),
                        checked = generateForAudio,
                        onCheckedChange = {
                            generateForAudio = it
                            ThumbnailSettings.generateForAudio = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = stringResource(R.string.player_thumbnail_upload),
                        description = stringResource(R.string.player_thumbnail_upload_desc),
                        checked = saveInSameDir,
                        onCheckedChange = {
                            saveInSameDir = it
                            ThumbnailSettings.saveInSameDir = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = stringResource(R.string.player_thumbnail_update_on_exit),
                        description = stringResource(R.string.player_thumbnail_update_on_exit_desc),
                        checked = updateOnExit,
                        onCheckedChange = {
                            updateOnExit = it
                            ThumbnailSettings.updateOnExit = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingClickRow(
                        label = stringResource(R.string.player_thumbnail_timing),
                        value = stringResource(generationMode.labelRes),
                        onClick = { showGenerationModeDialog = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingClickRow(
                        label = stringResource(R.string.player_thumbnail_frame),
                        value = stringResource(ThumbnailFramePosition.fromKey(framePositionKey).labelRes) +
                            if (framePositionKey == "custom") stringResource(R.string.thumbnail_custom_suffix, customPositionSeconds) else "",
                        onClick = { showFramePositionDialog = true },
                    )
                }
            }

            SettingsGroupSection(
                title = stringResource(R.string.player_storage_thumbnail_title),
                icon = Icons.Filled.CloudQueue,
                iconBg = Color(0xFF546E7A),
                action = {
                    IconButton(onClick = { showStorageHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.lrcapi_help),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            ) {
                val libraries by viewModel.libraries.collectAsStateWithLifecycle()
                val displayLibraries = remember(libraries) {
                    libraries.filter { lib ->
                        lib.mediaType == MediaType.SMB_SERVER ||
                            lib.mediaType == MediaType.WEBDAV_SERVER ||
                            lib.mediaType == MediaType.EXTERNAL_STORAGE ||
                            lib.mediaType == MediaType.LOCAL_STORAGE
                    }
                }
                if (displayLibraries.isEmpty()) {
                    SettingInfoRow(
                        label = stringResource(R.string.player_storage_empty),
                        value = stringResource(R.string.player_storage_empty_value),
                    )
                } else {
                    displayLibraries.forEachIndexed { index, lib ->
                        val typeLabel = when (lib.mediaType) {
                            MediaType.SMB_SERVER -> "SMB"
                            MediaType.WEBDAV_SERVER -> "WebDAV"
                            MediaType.LOCAL_STORAGE -> stringResource(R.string.storage_type_local)
                            MediaType.EXTERNAL_STORAGE -> "SAF"
                            else -> ""
                        }
                        val libMode = ThumbnailSettings.getLibraryGenerationMode(lib.id)
                        SettingClickRow(
                            label = lib.displayName,
                            value = if (libMode == null) {
                                stringResource(R.string.thumbnail_follow_global, stringResource(generationMode.labelRes))
                            } else {
                                stringResource(libMode.labelRes)
                            },
                            description = typeLabel,
                            onClick = { modeDialogLibId = lib.id },
                        )
                        if (index < displayLibraries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            SettingsGroupSection(
                title = stringResource(R.string.player_browser_title),
                icon = Icons.Filled.FolderOpen,
                iconBg = Color(0xFF43A047),
            ) {
                SettingSwitchRow(
                    label = stringResource(R.string.player_media_only),
                    description = stringResource(R.string.player_media_only_desc),
                    checked = showOnlyMediaFiles,
                    onCheckedChange = {
                        showOnlyMediaFiles = it
                        FileBrowserSettings.showOnlyMediaFiles = it
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    label = stringResource(R.string.player_show_hidden),
                    description = stringResource(R.string.player_show_hidden_desc),
                    checked = showHiddenFiles,
                    onCheckedChange = {
                        showHiddenFiles = it
                        FileBrowserSettings.showHiddenFiles = it
                    },
                )
            }
        }
    }

    if (showFramePositionDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_frame_position_title),
            onDismiss = { showFramePositionDialog = false },
            items = ThumbnailFramePosition.entries.map { option ->
                NiDialogItem(
                    label = stringResource(option.labelRes) +
                        if (option.key == "custom") stringResource(R.string.thumbnail_custom_suffix, customPositionSeconds) else "",
                    isSelected = framePositionKey == option.key,
                    onClick = {
                        if (option.key == "custom") {
                            showFramePositionDialog = false
                            showCustomPositionDialog = true
                        } else {
                            framePositionKey = option.key
                            ThumbnailSettings.framePositionKey = option.key
                            showFramePositionDialog = false
                        }
                    },
                )
            },
        )
    }

    if (showCustomPositionDialog) {
        var seconds by rememberSaveable { mutableStateOf(customPositionSeconds.toString()) }
        NiInfoDialog(
            title = stringResource(R.string.player_custom_seconds_title),
            onDismiss = { showCustomPositionDialog = false },
            actions = {
                TextButton(onClick = { showCustomPositionDialog = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    val s = seconds.toIntOrNull() ?: 10
                    if (s > 0) {
                        customPositionSeconds = s
                        ThumbnailSettings.customPositionSeconds = s
                        framePositionKey = "custom"
                        ThumbnailSettings.framePositionKey = "custom"
                    }
                    showCustomPositionDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) {
            Text(
                stringResource(R.string.player_custom_seconds_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = seconds,
                onValueChange = { seconds = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "10",
                label = stringResource(R.string.player_seconds_label),
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

    if (showGenerationModeDialog) {
        NiListItemDialog(
            title = stringResource(R.string.player_thumbnail_timing_title),
            onDismiss = { showGenerationModeDialog = false },
            items = ThumbnailGenerationMode.entries.map { option ->
                NiDialogItem(
                    label = stringResource(option.labelRes),
                    isSelected = generationMode == option,
                    onClick = {
                        generationMode = option
                        ThumbnailSettings.generationMode = option
                        showGenerationModeDialog = false
                    },
                )
            },
        )
    }

    val dialogLibId = modeDialogLibId
    if (dialogLibId != null) {
        val libs by viewModel.libraries.collectAsStateWithLifecycle()
        val lib = libs.firstOrNull { it.id == dialogLibId }
        var selectedMode by rememberSaveable(dialogLibId) {
            mutableStateOf(ThumbnailSettings.getLibraryGenerationMode(dialogLibId))
        }
        val dialogTitle = stringResource(
            R.string.player_storage_policy_title,
            lib?.displayName ?: stringResource(R.string.player_storage_policy_fallback),
        )
        NiListItemDialog(
            title = dialogTitle,
            onDismiss = { modeDialogLibId = null },
            items = buildList {
                add(
                    NiDialogItem(
                        label = stringResource(R.string.thumbnail_follow_global, stringResource(generationMode.labelRes)),
                        isSelected = selectedMode == null,
                        onClick = {
                            selectedMode = null
                            ThumbnailSettings.setLibraryGenerationMode(dialogLibId, null)
                            modeDialogLibId = null
                        },
                    ),
                )
                ThumbnailGenerationMode.entries.forEach { option ->
                    add(
                        NiDialogItem(
                            label = stringResource(option.labelRes),
                            isSelected = selectedMode == option,
                            onClick = {
                                selectedMode = option
                                ThumbnailSettings.setLibraryGenerationMode(dialogLibId, option)
                                modeDialogLibId = null
                            },
                        ),
                    )
                }
            },
        )
    }

    if (showStorageHelpDialog) {
        NiInfoDialog(
            title = stringResource(R.string.player_storage_thumbnail_title),
            onDismiss = { showStorageHelpDialog = false },
        ) {
            Text(
                stringResource(R.string.player_storage_help_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.player_storage_help_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SettingsGroupSection(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
            action?.let {
                Spacer(Modifier.weight(1f))
                it()
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NiExtraColors.current.surfaceLevel2),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingSwitchRow(label: String, description: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp)) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingClickRow(label: String, value: String, onClick: () -> Unit, description: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
