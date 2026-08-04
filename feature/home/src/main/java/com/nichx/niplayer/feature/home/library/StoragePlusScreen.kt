package com.nichx.niplayer.feature.home.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiSnackbarHost
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.showNiMessage
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoragePlusScreen(
    onBack: () -> Unit,
    viewModel: StoragePlusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val extraColors = NiExtraColors.current

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = queryDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
            viewModel.updateExternalUri(uri.toString())
            viewModel.ensureExternalDisplayName(name)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoragePlusEvent.ShowError ->
                    snackbarHostState.showNiMessage(NiMessage.error(event.message))

                StoragePlusEvent.NavigateBack -> onBack()
                StoragePlusEvent.Saved -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "编辑存储源" else "添加存储源") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
        snackbarHost = { NiSnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            StorageFormBottomBar(
                state = state,
                onTest = viewModel::testConnection,
                onSave = viewModel::save,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StorageTypeBadge(type = state.mediaType, extraColors = extraColors)

            FormCard(
                title = "基本信息",
                icon = Icons.Filled.Info,
                iconBg = Color(0xFF2095F4),
            ) {
                FormTextField(
                    label = "显示名称",
                    value = state.displayName,
                    onValueChange = viewModel::updateDisplayName,
                    placeholder = "留空使用默认名",
                )
            }

            when (state.mediaType) {
                MediaType.WEBDAV_SERVER -> WebDavCards(state, viewModel, passwordVisible) {
                    passwordVisible = it
                }
                MediaType.SMB_SERVER -> SmbCards(state, viewModel, passwordVisible) {
                    passwordVisible = it
                }
                MediaType.EXTERNAL_STORAGE -> ExternalCard(state, extraColors) {
                    treeLauncher.launch(null)
                }
                else -> Text("不支持的存储类型")
            }

            state.testResult?.let { ok ->
                TestResultCard(ok = ok)
            }
        }
    }

    if (showDeleteDialog) {
        NiConfirmDialog(
            title = "删除存储源",
            text = "确定删除「${state.displayName.ifBlank { state.url }}」？",
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
            confirmText = "删除",
        )
    }
}

// ---- 类型标识卡片 ----

@Composable
private fun StorageTypeBadge(
    type: MediaType,
    extraColors: NiExtraColors,
) {
    val (label, icon, color) = when (type) {
        MediaType.WEBDAV_SERVER -> Triple("WebDAV 服务器", Icons.Filled.CloudQueue, extraColors.storageWebdavColor)
        MediaType.SMB_SERVER -> Triple("SMB 共享", Icons.Filled.CloudQueue, extraColors.storageSmbColor)
        MediaType.EXTERNAL_STORAGE -> Triple("外部存储", Icons.Filled.FolderOpen, extraColors.storageExternalColor)
        else -> Triple("未知", Icons.Filled.Info, Color.Gray)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "存储源类型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ---- 表单卡片容器（无外边框，仅背景色） ----

@Composable
private fun FormCard(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NiExtraColors.current.surfaceLevel2),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) { content() }
        }
    }
}

// ---- 测试结果卡片 ----

@Composable
private fun TestResultCard(ok: Boolean) {
    val bgColor: Color
    val icon: ImageVector
    val tintColor: Color
    val label: String
    if (ok) {
        bgColor = Color(0xFF2E7D32).copy(alpha = 0.08f)
        icon = Icons.Filled.Check
        tintColor = Color(0xFF2E7D32)
        label = "连接成功"
    } else {
        bgColor = Color(0xFFD32F2F).copy(alpha = 0.08f)
        icon = Icons.Filled.Warning
        tintColor = Color(0xFFD32F2F)
        label = "连接失败"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tintColor,
            )
        }
    }
}

// ---- WebDAV 分组卡片 ----

@Composable
private fun WebDavCards(
    state: StoragePlusUiState,
    vm: StoragePlusViewModel,
    passwordVisible: Boolean,
    onTogglePassword: (Boolean) -> Unit,
) {
    val extraColors = NiExtraColors.current

    FormCard(
        title = "连接配置",
        icon = Icons.Filled.CloudQueue,
        iconBg = extraColors.storageWebdavColor,
    ) {
        SegmentedRow(
            label = "协议",
            leftLabel = "http://",
            rightLabel = "https://",
            isLeftSelected = !state.webDavUseHttps,
            onLeft = { vm.updateWebDavUseHttps(false) },
            onRight = { vm.updateWebDavUseHttps(true) },
        )
        FormTextField(
            label = "服务器地址",
            value = state.url,
            onValueChange = vm::updateUrl,
            placeholder = "例如 example.com/webdav",
        )
    }

    FormCard(
        title = "认证设置",
        icon = Icons.Filled.Lock,
        iconBg = extraColors.storageWebdavColor,
    ) {
        SegmentedRow(
            label = "登录方式",
            leftLabel = "匿名",
            rightLabel = "账号",
            isLeftSelected = state.isAnonymous,
            onLeft = { vm.updateAnonymous(true) },
            onRight = { vm.updateAnonymous(false) },
        )
        if (!state.isAnonymous) {
            FormTextField(
                label = "账号",
                value = state.account,
                onValueChange = vm::updateAccount,
            )
            PasswordField(
                label = "密码",
                value = state.password,
                onValueChange = vm::updatePassword,
                visible = passwordVisible,
                onToggleVisible = onTogglePassword,
            )
        }
    }

    FormCard(
        title = "高级设置",
        icon = Icons.Filled.Settings,
        iconBg = extraColors.storageWebdavColor,
    ) {
        SegmentedRow(
            label = "解析模式",
            leftLabel = "严格",
            rightLabel = "普通",
            isLeftSelected = state.webDavStrict,
            onLeft = { vm.updateWebDavStrict(true) },
            onRight = { vm.updateWebDavStrict(false) },
        )
        FieldDescription(
            text = if (state.webDavStrict)
                "严格模式：文件名大小写敏感，按原始名称精确匹配"
            else
                "普通模式：自动处理文件名编码差异，容错性更好",
        )
    }
}

// ---- SMB 分组卡片 ----

@Composable
private fun SmbCards(
    state: StoragePlusUiState,
    vm: StoragePlusViewModel,
    passwordVisible: Boolean,
    onTogglePassword: (Boolean) -> Unit,
) {
    val extraColors = NiExtraColors.current

    FormCard(
        title = "连接配置",
        icon = Icons.Filled.CloudQueue,
        iconBg = extraColors.storageSmbColor,
    ) {
        FormTextField(
            label = "IP 地址",
            value = state.url,
            onValueChange = vm::updateUrl,
            placeholder = "如 192.168.1.1",
        )
        FormTextField(
            label = "端口",
            value = if (state.port == 0) "" else state.port.toString(),
            onValueChange = vm::updatePort,
            keyboardType = KeyboardType.Number,
            placeholder = "默认 445",
        )
        FormTextField(
            label = "共享路径",
            value = state.smbSharePath,
            onValueChange = vm::updateSmbSharePath,
            placeholder = "可选，预设共享目录",
        )
    }

    FormCard(
        title = "认证设置",
        icon = Icons.Filled.Lock,
        iconBg = extraColors.storageSmbColor,
    ) {
        SegmentedRow(
            label = "登录方式",
            leftLabel = "匿名",
            rightLabel = "账号",
            isLeftSelected = state.isAnonymous,
            onLeft = { vm.updateAnonymous(true) },
            onRight = { vm.updateAnonymous(false) },
        )
        if (!state.isAnonymous) {
            FormTextField(
                label = "账号",
                value = state.account,
                onValueChange = vm::updateAccount,
            )
            PasswordField(
                label = "密码",
                value = state.password,
                onValueChange = vm::updatePassword,
                visible = passwordVisible,
                onToggleVisible = onTogglePassword,
            )
            FormTextField(
                label = "域/工作组",
                value = state.domain,
                onValueChange = vm::updateDomain,
                placeholder = "可选，如 WORKGROUP 或 CORP",
            )
        }
    }

    FormCard(
        title = "高级设置",
        icon = Icons.Filled.Settings,
        iconBg = extraColors.storageSmbColor,
    ) {
        SegmentedRow(
            label = "加密传输",
            leftLabel = "关闭",
            rightLabel = "开启",
            isLeftSelected = !state.smbEncryption,
            onLeft = { vm.updateSmbEncryption(false) },
            onRight = { vm.updateSmbEncryption(true) },
        )
        FieldDescription(
            text = if (state.smbEncryption)
                "开启加密传输（SMB 3.0+），防止数据在传输过程中被窃听"
            else
                "关闭加密传输可获得更好的兼容性与传输速度",
        )
    }
}

// ---- External 卡片 ----

@Composable
private fun ExternalCard(
    state: StoragePlusUiState,
    extraColors: NiExtraColors,
    onSelect: () -> Unit,
) {
    FormCard(
        title = "目录选择",
        icon = Icons.Filled.FolderOpen,
        iconBg = extraColors.storageExternalColor,
    ) {
        OutlinedButton(
            onClick = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (state.externalUri.isBlank()) "选择根目录" else "重新选择根目录")
        }
        if (state.externalUri.isNotBlank()) {
            Text(
                text = state.externalUri,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

// ---- 公共表单组件 ----

@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    NiTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder.ifBlank { null },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: (Boolean) -> Unit,
) {
    NiTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { onToggleVisible(!visible) }) {
                Text(if (visible) "隐藏" else "显示")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FieldDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SegmentedRow(
    label: String,
    leftLabel: String,
    rightLabel: String,
    isLeftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FilterChip(
            selected = isLeftSelected,
            onClick = onLeft,
            label = { Text(leftLabel) },
        )
        FilterChip(
            selected = !isLeftSelected,
            onClick = onRight,
            label = { Text(rightLabel) },
        )
    }
}

@Composable
private fun StorageFormBottomBar(
    state: StoragePlusUiState,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.mediaType != MediaType.EXTERNAL_STORAGE) {
            OutlinedButton(
                onClick = onTest,
                enabled = !state.isTesting && !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("测试连接")
                }
            }
            Button(
                onClick = onSave,
                enabled = !state.isTesting && !state.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("保存")
                }
            }
        } else {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("保存")
                }
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()
