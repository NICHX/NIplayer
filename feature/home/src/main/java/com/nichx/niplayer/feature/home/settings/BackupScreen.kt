package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

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

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.backup_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))

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
                        text = stringResource(R.string.backup_notes_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.backup_notes_body),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }

    // 备份/恢复弹窗：确认 → 处理中 → 结果统一为同一固定 id 弹窗，原地刷新，
    // 消除「旧窗退场 + 新窗进场」的关闭重开闪动（与更新弹窗同因同治）。
    val backupDialogState: BackupDialogState? = run {
        val importUri = pendingImportUri
        val webdavFile = pendingWebDavRestore
        val s = state
        when {
            importUri != null -> BackupDialogState.ConfirmRestore(
                title = stringResource(R.string.backup_confirm_restore),
                message = stringResource(R.string.backup_restore_confirm_body),
            )
            webdavFile != null -> BackupDialogState.ConfirmRestore(
                title = stringResource(R.string.backup_confirm_restore),
                message = stringResource(R.string.backup_webdav_restore_confirm_body, webdavFile),
            )
            s is BackupUiState.Working -> BackupDialogState.Working
            s is BackupUiState.ExportSuccess -> BackupDialogState.Result(
                title = stringResource(R.string.backup_export_done_title),
                message = s.message,
            )
            s is BackupUiState.ImportSuccess -> BackupDialogState.Result(
                title = stringResource(R.string.backup_restore_done_title),
                message = buildString {
                    append(stringResource(R.string.backup_restore_done_body))
                    if (s.summary.descriptions.isEmpty()) {
                        append(stringResource(R.string.backup_no_data))
                    } else {
                        s.summary.descriptions.forEach { append(it).append('\n') }
                        append(stringResource(R.string.backup_history_untouched))
                    }
                },
            )
            s is BackupUiState.Error -> BackupDialogState.Result(
                title = stringResource(R.string.backup_failed_title),
                message = s.message,
            )
            else -> null
        }
    }

    BackupDialogHost(
        state = backupDialogState,
        onDismiss = {
            // 取消确认：清掉待恢复来源；关闭结果：重置状态回到空闲
            pendingImportUri = null
            pendingWebDavRestore = null
            viewModel.resetState()
        },
        onConfirmRestore = {
            val importUri = pendingImportUri
            val webdavFile = pendingWebDavRestore
            when {
                importUri != null -> {
                    pendingImportUri = null
                    viewModel.import(resolver, importUri)
                }
                webdavFile != null -> {
                    val libraryId = viewModel.selectedWebDavId.value.takeIf { it > 0 }
                    if (libraryId != null) {
                        pendingWebDavRestore = null
                        viewModel.restoreFromWebDav(libraryId, webdavFile)
                    }
                }
            }
        },
    )
}
