package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.datastore.ThumbnailFramePosition
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiDialogItemRow
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar

/**
 * 媒体库设置页：收纳与“播放器”语义无关的媒体库/文件浏览设置。
 *
 * 从旧 [PlayerSettingsScreen] 中剥离，包含三组：
 * - **缩略图**：全局缩略图生成总开关、媒体类型（视频/图片/音频）、回写与更新策略、
 *   生成时机、取帧位置
 * - **存储源缩略图**：按存储源独立覆盖全局生成时机（跟随全局/全部生成/仅播放后生成/关闭）
 * - **文件浏览**：仅显示媒体文件、显示隐藏文件/文件夹
 *
 * @param onBack 返回回调
 */
@Composable
fun MediaLibrarySettingsScreen(
    onBack: () -> Unit = {},
    viewModel: MediaLibrarySettingsViewModel = hiltViewModel(),
) {
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
    var configDialogLibId by remember { mutableStateOf<Int?>(null) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.media_library_settings_title),
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
                        // 仅远程存储需要按源配置：回写/生成时机针对服务器过度读取（封控）设计，
                        // 本地与 SAF 无服务器可回写，且无读取风险，自动生成本地缩略图即可
                        lib.mediaType == MediaType.SMB_SERVER || lib.mediaType == MediaType.WEBDAV_SERVER
                    }
                }
                if (displayLibraries.isEmpty()) {
                    SettingInfoRow(
                        label = stringResource(R.string.player_storage_remote_empty),
                        value = stringResource(R.string.player_storage_remote_empty_value),
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
                        val libWriteBack = ThumbnailSettings.getLibraryWriteBack(lib.id)
                        val summary = buildString {
                            append(libMode?.let { stringResource(it.labelRes) }
                                ?: stringResource(R.string.thumbnail_default))
                            when (libWriteBack) {
                                true -> append(stringResource(R.string.thumbnail_wb_on))
                                false -> append(stringResource(R.string.thumbnail_wb_off))
                                null -> {}
                            }
                        }
                        SettingClickRow(
                            label = lib.displayName,
                            value = summary,
                            description = typeLabel,
                            onClick = { configDialogLibId = lib.id },
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
            Spacer(Modifier.height(padding.calculateBottomPadding()))
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

    val configLibId = configDialogLibId
    if (configLibId != null) {
        val libs by viewModel.libraries.collectAsStateWithLifecycle()
        val lib = libs.firstOrNull { it.id == configLibId }
        var selectedMode by rememberSaveable(configLibId) {
            mutableStateOf(ThumbnailSettings.getLibraryGenerationMode(configLibId))
        }
        var selectedWriteBack by rememberSaveable(configLibId) {
            mutableStateOf(ThumbnailSettings.getLibraryWriteBack(configLibId))
        }
        val dialogTitle = stringResource(
            R.string.player_storage_config_title,
            lib?.displayName ?: stringResource(R.string.player_storage_config_fallback),
        )
        val globalWriteBack = if (saveInSameDir) R.string.thumbnail_writeback_enabled
        else R.string.thumbnail_writeback_disabled
        val groupHeader: @Composable (Int) -> Unit = { labelRes ->
            Text(
                text = stringResource(labelRes),
                modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        NiInfoDialog(title = dialogTitle, onDismiss = { configDialogLibId = null }) {
            // 生成时机
            groupHeader(R.string.player_thumbnail_timing)
            NiDialogItemRow(
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_follow_global, stringResource(generationMode.labelRes)),
                    isSelected = selectedMode == null,
                    onClick = {
                        selectedMode = null
                        ThumbnailSettings.setLibraryGenerationMode(configLibId, null)
                    },
                ),
            )
            ThumbnailGenerationMode.entries.forEach { option ->
                NiDialogItemRow(
                    NiDialogItem(
                        label = stringResource(option.labelRes),
                        isSelected = selectedMode == option,
                        onClick = {
                            selectedMode = option
                            ThumbnailSettings.setLibraryGenerationMode(configLibId, option)
                        },
                    ),
                )
            }
            // 回写服务器
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            groupHeader(R.string.player_thumbnail_upload)
            NiDialogItemRow(
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_follow_global, stringResource(globalWriteBack)),
                    isSelected = selectedWriteBack == null,
                    onClick = {
                        selectedWriteBack = null
                        ThumbnailSettings.setLibraryWriteBack(configLibId, null)
                    },
                ),
            )
            NiDialogItemRow(
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_writeback_enabled),
                    isSelected = selectedWriteBack == true,
                    onClick = {
                        selectedWriteBack = true
                        ThumbnailSettings.setLibraryWriteBack(configLibId, true)
                    },
                ),
            )
            NiDialogItemRow(
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_writeback_disabled),
                    isSelected = selectedWriteBack == false,
                    onClick = {
                        selectedWriteBack = false
                        ThumbnailSettings.setLibraryWriteBack(configLibId, false)
                    },
                ),
            )
        }
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
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.player_storage_help_writeback_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
