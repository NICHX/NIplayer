package com.nichx.niplayer.feature.home.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.datastore.PlayHistorySyncConfig
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTextFieldDefaults
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.sync.SyncUiState
import com.nichx.niplayer.storage.StorageFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingWebDavRestore by remember { mutableStateOf<String?>(null) }
    var selectedWebDavFile by remember { mutableStateOf<String?>(null) }

    // SAF 文件保存（备份）
    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.export(resolver, uri)
    }

    // SAF 文件选择（恢复）：先弹确认框，确认后才执行导入
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(
        topBar = {
            NiTopBar(
                title = "备份与同步",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 卡片① 本机备份与恢复（不依赖服务器，始终可用）
            LocalBackupCard(
                enabled = state !is BackupUiState.Working,
                onExport = {
                    createFileLauncher.launch(BackupViewModel.defaultFileName())
                },
                onRestore = {
                    openFileLauncher.launch(arrayOf("application/json"))
                },
            )

            // 卡片② WebDAV 服务器（独立前置，备份与云同步共用）
            val webDavLibraries by viewModel.webDavLibraries.collectAsStateWithLifecycle()
            val selectedWebDavId by viewModel.selectedWebDavId.collectAsStateWithLifecycle()
            WebDavServerCard(
                libraries = webDavLibraries,
                selectedId = selectedWebDavId,
                onSelect = viewModel::selectWebDavServer,
            )

            // 卡片③ 播放历史云同步
            val syncState by viewModel.syncState.collectAsStateWithLifecycle()
            val syncConfig by viewModel.historySyncConfig.collectAsStateWithLifecycle()
            PlayHistorySyncCard(
                config = syncConfig,
                syncState = syncState,
                serverSelected = selectedWebDavId > 0,
                enabled = state !is BackupUiState.Working,
                onEnabledChange = viewModel::setHistorySyncEnabled,
                onAutoSyncChange = viewModel::setAutoSync,
                onSyncNow = viewModel::syncNow,
            )

            // 卡片④ WebDAV 备份与恢复（依赖卡片②的服务器）
            val webDavBackupFiles by viewModel.webDavBackupFiles.collectAsStateWithLifecycle()
            val webDavBackupLoading by viewModel.webDavBackupLoading.collectAsStateWithLifecycle()
            val webDavBackupError by viewModel.webDavBackupError.collectAsStateWithLifecycle()
            LaunchedEffect(selectedWebDavId) {
                selectedWebDavFile = null
                selectedWebDavId.takeIf { it > 0 }?.let(viewModel::loadWebDavBackupFiles)
            }
            WebDavBackupCard(
                selectedId = selectedWebDavId,
                backupFiles = webDavBackupFiles,
                loading = webDavBackupLoading,
                loadError = webDavBackupError,
                selectedFileName = selectedWebDavFile,
                enabled = state !is BackupUiState.Working,
                onSelectFile = { selectedWebDavFile = it },
                onRefresh = { selectedWebDavId.takeIf { it > 0 }?.let(viewModel::loadWebDavBackupFiles) },
                onUpload = { selectedWebDavId.takeIf { it > 0 }?.let(viewModel::exportToWebDav) },
                onRestore = {
                    val file = selectedWebDavFile ?: return@WebDavBackupCard
                    pendingWebDavRestore = file
                },
            )

            // 卡片⑤ 注意事项
            val extraColors = NiExtraColors.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(extraColors.surfaceLevel2)
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "注意事项",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "• 备份文件为明文 JSON，不含加密\n• 存储源密码已由系统密钥库加密，跨设备恢复后需重新输入\n• 播放历史经「播放历史云同步」跨设备同步，不随备份文件恢复\n• 云同步与备份共用同一 WebDAV 服务器，同步文件位于 NIplayer_backup/sync/ 子目录\n• 恢复操作会替换当前存储源、快速访问、书签、扩展目录数据\n• 音乐 API、Assrt 字幕配置及主题、播放器、字幕样式、均衡器、缩略图、文件浏览、视频扩展名、下载目录等应用设置随备份导出/恢复",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }

    // 加载中遮罩
    if (state is BackupUiState.Working) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "请稍候...",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }

    // 结果弹窗
    when (val s = state) {
        is BackupUiState.ExportSuccess -> {
            ResultDialog(
                title = "备份完成",
                message = s.message,
                onDismiss = { viewModel.resetState() },
            )
        }
        is BackupUiState.ImportSuccess -> {
            val msg = buildString {
                append("恢复完成\n\n")
                append("存储源: ${s.summary.mediaLibraries} 条\n")
                append("快速访问: ${s.summary.quickAccesses} 条\n")
                append("视频书签: ${s.summary.videoBookmarks} 条\n")
                append("扩展目录: ${s.summary.extendFolders} 条\n")
                append("音乐 API: ${if (s.summary.lrcApiConfigured) "已恢复" else "无配置"}\n")
                append("Assrt 字幕: ${if (s.summary.assrtConfigured) "已恢复" else "无配置"}\n")
                append("应用设置: ${if (s.summary.appSettingsRestored) "已恢复" else "旧版备份无设置"}\n\n")
                append("播放历史不受影响")
            }
            ResultDialog(
                title = "恢复完成",
                message = msg,
                onDismiss = { viewModel.resetState() },
            )
        }
        is BackupUiState.Error -> {
            ResultDialog(
                title = "操作失败",
                message = s.message,
                onDismiss = { viewModel.resetState() },
            )
        }
        else -> {}
    }

    // 恢复确认弹窗
    pendingImportUri?.let { uri ->
        NiInfoDialog(
            title = "确认恢复",
            onDismiss = { pendingImportUri = null },
            actions = {
                TextButton(onClick = { pendingImportUri = null }) { Text("取消") }
                TextButton(
                    onClick = {
                        pendingImportUri = null
                        viewModel.import(resolver, uri)
                    },
                ) { Text("恢复") }
            },
        ) {
            Text(
                text = "将从所选文件恢复存储源、快速访问、视频书签、扩展目录、音乐 API、Assrt 字幕配置及主题、播放器、字幕样式、均衡器、缩略图等应用设置。" +
                    "当前数据将被替换，播放历史不受影响。此操作不可撤销，是否继续？",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    // WebDAV 恢复确认弹窗
    pendingWebDavRestore?.let { fileName ->
        NiInfoDialog(
            title = "确认恢复",
            onDismiss = { pendingWebDavRestore = null },
            actions = {
                TextButton(onClick = { pendingWebDavRestore = null }) { Text("取消") }
                TextButton(
                    onClick = {
                        val libraryId = viewModel.selectedWebDavId.value.takeIf { it > 0 }
                            ?: return@TextButton
                        pendingWebDavRestore = null
                        viewModel.restoreFromWebDav(libraryId, fileName)
                    },
                ) { Text("恢复") }
            },
        ) {
            Text(
                text = "将从服务器下载备份文件 $fileName 并恢复存储源、快速访问、视频书签、扩展目录、音乐 API、Assrt 字幕配置及主题、播放器、字幕样式、均衡器、缩略图等应用设置。" +
                    "当前数据将被替换，播放历史不受影响。此操作不可撤销，是否继续？",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 卡片②：WebDAV 服务器选择（独立卡片，备份与播放历史云同步共用的公共前置）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavServerCard(
    libraries: List<MediaLibraryEntity>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
) {
    val extraColors = NiExtraColors.current
    val webdavColor = extraColors.storageWebdavColor
    val selected = libraries.firstOrNull { it.id == selectedId }
    var menuExpanded by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(12.dp)
    val dropdownColors = NiTextFieldDefaults.colors(
        focusedBorderColor = webdavColor,
        unfocusedBorderColor = extraColors.outlineSoft,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedLabelColor = webdavColor,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = webdavColor,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = webdavColor,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(extraColors.surfaceLevel2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(webdavColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lan,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = "WebDAV 服务器",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = "备份与播放历史云同步共用此服务器。备份文件位于 NIplayer_backup 目录，同步文件位于其 sync 子目录。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )

        if (libraries.isEmpty()) {
            Text(
                text = "未添加 WebDAV 服务器，请先在「存储」中添加。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
            ) {
                NiTextField(
                    value = selected?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = "WebDAV 服务器",
                    placeholder = "选择服务器",
                    colors = dropdownColors,
                    trailingIcon = {
                        Icon(
                            imageVector = if (menuExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (menuExpanded) webdavColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = shape,
                    containerColor = extraColors.surfaceLevel1,
                    border = BorderStroke(1.dp, extraColors.outlineSoft),
                ) {
                    libraries.forEach { library ->
                        val isSelected = library.id == selectedId
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = library.displayName,
                                    color = if (isSelected) webdavColor else Color.Unspecified,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = webdavColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                            onClick = {
                                onSelect(library.id)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 卡片③：播放历史云同步（选择服务器后自动启用）。 */
@Composable
private fun PlayHistorySyncCard(
    config: PlayHistorySyncConfig,
    syncState: SyncUiState,
    serverSelected: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    val isSyncing = syncState is SyncUiState.Syncing

    val lastSyncText = if (config.lastSyncTime > 0) {
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(config.lastSyncTime))
        if (config.lastSyncSuccess) "上次同步：$time · 成功"
        else "上次同步：$time · ${config.lastSyncMessage}"
    } else {
        "尚未同步"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extraColors.surfaceLevel2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(extraColors.storageWebdavColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudSync,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = "播放历史云同步",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = config.enabled,
                onCheckedChange = onEnabledChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = extraColors.storageWebdavColor,
                    uncheckedTrackColor = extraColors.outlineSoft,
                ),
            )
        }
        Text(
            text = "将播放历史按设备文件同步到服务器的 NIplayer_backup/sync 目录，多设备间自动合并，冲突时保留较新的播放进度。选择服务器后自动启用。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )

        if (config.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "自动同步（启动 / 退出播放器后）",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = config.autoSync,
                    onCheckedChange = onAutoSyncChange,
                    enabled = enabled && serverSelected,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = extraColors.storageWebdavColor,
                        uncheckedTrackColor = extraColors.outlineSoft,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSyncNow,
                    enabled = enabled && serverSelected && !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = extraColors.storageWebdavColor),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("同步中...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("立即同步")
                    }
                }
            }
            Text(
                text = if (!serverSelected) {
                    "请先在上方选择 WebDAV 服务器"
                } else if (isSyncing) {
                    "正在同步，请稍候..."
                } else {
                    lastSyncText
                },
                fontSize = 12.sp,
                color = if (!serverSelected) {
                    MaterialTheme.colorScheme.error
                } else if (config.lastSyncSuccess) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        } else {
            Text(
                text = "开启后，播放历史将跨设备同步。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 卡片④：WebDAV 备份与恢复（依赖卡片②的服务器）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavBackupCard(
    selectedId: Int,
    backupFiles: List<StorageFile>,
    loading: Boolean,
    loadError: String?,
    selectedFileName: String?,
    enabled: Boolean,
    onSelectFile: (String) -> Unit,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    val webdavColor = extraColors.storageWebdavColor
    val serverSelected = selectedId > 0
    var fileMenuExpanded by remember { mutableStateOf(false) }
    val selectedFile = backupFiles.firstOrNull { it.name == selectedFileName }

    val shape = RoundedCornerShape(12.dp)
    val dropdownColors = NiTextFieldDefaults.colors(
        focusedBorderColor = webdavColor,
        unfocusedBorderColor = extraColors.outlineSoft,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedLabelColor = webdavColor,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = webdavColor,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = webdavColor,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(extraColors.surfaceLevel2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(webdavColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = "WebDAV 备份与恢复",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // 手动刷新备份文件列表
            IconButton(
                onClick = onRefresh,
                enabled = enabled && serverSelected && !loading,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新备份文件列表",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = "将备份文件上传到上方服务器的 NIplayer_backup 目录，或从该目录选择备份文件恢复。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )

        if (!serverSelected) {
            Text(
                text = "请先在上方选择 WebDAV 服务器",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "正在加载备份文件...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (loadError != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = loadError,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 18.sp,
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = enabled,
                ) {
                    Text("重试")
                }
            }
        } else {
            if (backupFiles.isEmpty()) {
                Text(
                    text = "服务器上暂无备份文件",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = fileMenuExpanded,
                    onExpandedChange = { fileMenuExpanded = it },
                ) {
                    NiTextField(
                        value = selectedFile?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = "备份文件",
                        placeholder = "选择备份文件",
                        colors = dropdownColors,
                        trailingIcon = {
                            Icon(
                                imageVector = if (fileMenuExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = if (fileMenuExpanded) webdavColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = fileMenuExpanded,
                        onDismissRequest = { fileMenuExpanded = false },
                        shape = shape,
                        containerColor = extraColors.surfaceLevel1,
                        border = BorderStroke(1.dp, extraColors.outlineSoft),
                    ) {
                        backupFiles.forEach { file ->
                            val isSelected = file.name == selectedFile?.name
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = file.name,
                                        color = if (isSelected) webdavColor else Color.Unspecified,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = webdavColor,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else null,
                                onClick = {
                                    onSelectFile(file.name)
                                    fileMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onUpload,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = webdavColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("上传备份")
                }
                OutlinedButton(
                    onClick = onRestore,
                    enabled = enabled && selectedFile != null,
                    border = BorderStroke(1.dp, webdavColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = webdavColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("从服务器恢复")
                }
            }
        }
    }
}

@Composable
private fun LocalBackupCard(
    enabled: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(extraColors.surfaceLevel2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF43A047)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = "本机备份与恢复",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = "将存储源、快速访问、书签、扩展目录、音乐 API、Assrt 字幕配置及主题、播放器、字幕样式、均衡器、缩略图等应用设置导出为 JSON 文件，或从备份文件恢复。播放历史不包含在备份中。",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onExport,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("导出备份")
            }
            Button(
                onClick = onRestore,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("恢复")
            }
        }
    }
}

@Composable
private fun ResultDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    NiInfoDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
