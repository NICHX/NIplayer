package com.nichx.niplayer.feature.home.settings

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
        250 -> "快"
        400 -> "慢"
        else -> "标准"
    }
    val seekSensitivityLabel = when (seekSensitivity) {
        1.0f -> "灵敏"
        2.0f -> "精细"
        else -> "标准"
    }
    val doubleTapLabel = when (doubleTapStepSeconds) {
        0 -> "关闭"
        else -> "${doubleTapStepSeconds} 秒"
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "播放器设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                title = "播放器内核",
                icon = Icons.Filled.PlayCircleOutline,
                iconBg = Color(0xFF2095F4),
            ) {
                SettingInfoRow(label = "当前内核", value = "AndroidX Media3")
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    label = "倍速音调保持",
                    description = "倍速播放时保持原音调，关闭则变速变调（适合快速浏览）",
                    checked = pitchPreservation,
                    onCheckedChange = {
                        pitchPreservation = it
                        PlayerSettings.pitchPreservationEnabled = it
                    },
                )
            }

            SettingsGroupSection(
                title = "字幕",
                icon = Icons.Filled.Subtitles,
                iconBg = Color(0xFF9C27B0),
            ) {
                SettingSwitchRow(
                    label = "自动加载同名字幕",
                    description = "播放时自动加载同文件夹下同名字幕文件",
                    checked = autoLoadSubtitle,
                    onCheckedChange = {
                        autoLoadSubtitle = it
                        SubtitleSettings.autoLoadSameNameSubtitle = it
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = "字幕优先级",
                    value = subtitlePriority.ifEmpty { "默认（按文件名排序）" },
                    onClick = { showPriorityDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = "ASSRT Token",
                    value = if (SubtitleSettings.assrtToken.isBlank()) "未设置" else "已设置",
                    onClick = { showTokenDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingInfoRow(
                    label = "字幕样式",
                    value = "在播放器字幕菜单中调整",
                )
            }

            SettingsGroupSection(
                title = "进度",
                icon = Icons.Filled.Speed,
                iconBg = Color(0xFFFF6D00),
            ) {
                SettingClickRow(
                    label = "长按倍速触发",
                    value = longPressTimeoutLabel,
                    onClick = { showLongPressDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = "滑动快进灵敏度",
                    value = seekSensitivityLabel,
                    onClick = { showSeekDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingClickRow(
                    label = "双击快进",
                    value = doubleTapLabel,
                    onClick = { showDoubleTapDialog = true },
                )
            }

            SettingsGroupSection(
                title = "缩略图",
                icon = Icons.Filled.Wallpaper,
                iconBg = Color(0xFF00ACC1),
            ) {
                SettingSwitchRow(
                    label = "生成缩略图",
                    description = "总开关",
                    checked = generateThumbnail,
                    onCheckedChange = {
                        generateThumbnail = it
                        ThumbnailSettings.generateThumbnail = it
                    },
                )
                if (generateThumbnail) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = "视频缩略图",
                        description = "为视频文件生成缩略图",
                        checked = generateForVideo,
                        onCheckedChange = {
                            generateForVideo = it
                            ThumbnailSettings.generateForVideo = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = "图片缩略图",
                        description = "为图片文件生成缩略图",
                        checked = generateForImage,
                        onCheckedChange = {
                            generateForImage = it
                            ThumbnailSettings.generateForImage = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = "音频封面",
                        description = "提取音频内嵌封面图",
                        checked = generateForAudio,
                        onCheckedChange = {
                            generateForAudio = it
                            ThumbnailSettings.generateForAudio = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingSwitchRow(
                        label = "保存到服务器",
                        description = "远端存储时将缩略图回写到 .thumb/ 目录",
                        checked = saveInSameDir,
                        onCheckedChange = {
                            saveInSameDir = it
                            ThumbnailSettings.saveInSameDir = it
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingClickRow(
                        label = "生成时机",
                        value = generationMode.label,
                        onClick = { showGenerationModeDialog = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingClickRow(
                        label = "取帧位置",
                        value = ThumbnailFramePosition.fromKey(framePositionKey).label +
                            if (framePositionKey == "custom") "（${customPositionSeconds}s）" else "",
                        onClick = { showFramePositionDialog = true },
                    )
                }
            }

            SettingsGroupSection(
                title = "存储源缩略图",
                icon = Icons.Filled.CloudQueue,
                iconBg = Color(0xFF546E7A),
                action = {
                    IconButton(onClick = { showStorageHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "帮助",
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
                        label = "暂无存储源",
                        value = "添加存储源后将自动显示",
                    )
                } else {
                    displayLibraries.forEachIndexed { index, lib ->
                        val typeLabel = when (lib.mediaType) {
                            MediaType.SMB_SERVER -> "SMB"
                            MediaType.WEBDAV_SERVER -> "WebDAV"
                            MediaType.LOCAL_STORAGE -> "本地"
                            MediaType.EXTERNAL_STORAGE -> "SAF"
                            else -> ""
                        }
                        val libMode = ThumbnailSettings.getLibraryGenerationMode(lib.id)
                        SettingClickRow(
                            label = lib.displayName,
                            value = if (libMode == null) "跟随全局（${generationMode.label}）" else libMode.label,
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
                title = "文件浏览",
                icon = Icons.Filled.FolderOpen,
                iconBg = Color(0xFF43A047),
            ) {
                SettingSwitchRow(
                    label = "仅显示媒体文件",
                    description = "文件浏览页仅显示视频、音频和图片，隐藏其他文件类型",
                    checked = showOnlyMediaFiles,
                    onCheckedChange = {
                        showOnlyMediaFiles = it
                        FileBrowserSettings.showOnlyMediaFiles = it
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitchRow(
                    label = "显示隐藏文件/文件夹",
                    description = "显示以 . 开头的隐藏文件和文件夹，如 .thumb/、.DS_Store 等",
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
            title = "取帧位置",
            onDismiss = { showFramePositionDialog = false },
            items = ThumbnailFramePosition.entries.map { option ->
                NiDialogItem(
                    label = option.label +
                        if (option.key == "custom") "（${customPositionSeconds}s）" else "",
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
            title = "自定义秒数",
            onDismiss = { showCustomPositionDialog = false },
            actions = {
                TextButton(onClick = { showCustomPositionDialog = false }) { Text("取消") }
                TextButton(onClick = {
                    val s = seconds.toIntOrNull() ?: 10
                    if (s > 0) {
                        customPositionSeconds = s
                        ThumbnailSettings.customPositionSeconds = s
                        framePositionKey = "custom"
                        ThumbnailSettings.framePositionKey = "custom"
                    }
                    showCustomPositionDialog = false
                }) { Text("保存") }
            },
        ) {
            Text(
                "输入从第几秒取帧。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = seconds,
                onValueChange = { seconds = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "10",
                label = "秒",
            )
        }
    }

    if (showTokenDialog) {
        var token by rememberSaveable { mutableStateOf(SubtitleSettings.assrtToken) }
        NiInfoDialog(
            title = "设置 ASSRT Token",
            onDismiss = { showTokenDialog = false },
            actions = {
                TextButton(onClick = { showTokenDialog = false }) { Text("取消") }
                TextButton(onClick = {
                    SubtitleSettings.assrtToken = token
                    showTokenDialog = false
                }) { Text("保存") }
            },
        ) {
            Text(
                "在 assrt.net 注册获取 API Token，用于字幕搜索。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "输入 ASSRT Token",
            )
        }
    }

    if (showPriorityDialog) {
        var priority by rememberSaveable { mutableStateOf(subtitlePriority) }
        NiInfoDialog(
            title = "字幕优先级",
            onDismiss = { showPriorityDialog = false },
            actions = {
                TextButton(onClick = { showPriorityDialog = false }) { Text("取消") }
                TextButton(onClick = {
                    subtitlePriority = priority
                    SubtitleSettings.subtitlePriority = priority
                    showPriorityDialog = false
                }) { Text("保存") }
            },
        ) {
            Text(
                "按逗号分隔的扩展名或语言标识，如 \"chs,cht\"。留空则按文件名排序。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(8.dp))
            NiTextField(
                value = priority,
                onValueChange = { priority = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "如：chs,cht",
            )
        }
    }

    if (showLongPressDialog) {
        NiListItemDialog(
            title = "长按倍速触发",
            onDismiss = { showLongPressDialog = false },
            items = listOf(250 to "快（0.25 秒）", 300 to "标准（0.3 秒）", 400 to "慢（0.4 秒）")
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
            title = "滑动快进灵敏度",
            onDismiss = { showSeekDialog = false },
            items = listOf(1.0f to "灵敏（1 屏）", 1.5f to "标准（1.5 屏）", 2.0f to "精细（2 屏）")
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
            title = "双击快进",
            onDismiss = { showDoubleTapDialog = false },
            items = listOf(0 to "关闭", 5 to "5 秒", 10 to "10 秒", 30 to "30 秒")
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
            title = "生成时机",
            onDismiss = { showGenerationModeDialog = false },
            items = ThumbnailGenerationMode.entries.map { option ->
                NiDialogItem(
                    label = option.label,
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
        val dialogTitle = "${lib?.displayName ?: "存储源"} 生成策略"
        NiListItemDialog(
            title = dialogTitle,
            onDismiss = { modeDialogLibId = null },
            items = buildList {
                add(
                    NiDialogItem(
                        label = "跟随全局（${generationMode.label}）",
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
                            label = option.label,
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
            title = "存储源缩略图",
            onDismiss = { showStorageHelpDialog = false },
        ) {
            Text(
                "每个存储源可独立设置缩略图生成策略，覆盖全局“生成时机”。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "跟随全局：使用全局“生成时机”设置。\n" +
                    "全部生成：浏览时预加载并批量生成缩略图。\n" +
                    "仅播放后生成：浏览时仅加载已有缓存，播放退出后再生成，\n" +
                    "  适合网盘类存储，避免频繁读写触发风控限制。\n" +
                    "关闭：不生成缩略图。",
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
