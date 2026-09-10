package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTopBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@Composable
fun StoragePlusScreen(
    onBack: () -> Unit,
    viewModel: StoragePlusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messageController = LocalAppMessageController.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showThumbnailModeDialog by remember { mutableStateOf(false) }
    var showThumbnailWriteBackDialog by remember { mutableStateOf(false) }
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
                messageController.post(NiMessage.error(event.message))

                StoragePlusEvent.NavigateBack -> onBack()
                StoragePlusEvent.Saved -> Unit
            }
        }
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = if (viewModel.isEditMode) stringResource(R.string.storage_plus_edit_title) else stringResource(R.string.storage_plus_add_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            StorageTypeBadge(type = state.mediaType, extraColors = extraColors)

            FormCard(
                title = stringResource(R.string.storage_plus_basic_info),
                icon = Icons.Filled.Info,
                iconBg = Color(0xFF2095F4),
            ) {
                FormTextField(
                    label = stringResource(R.string.storage_plus_display_name),
                    value = state.displayName,
                    onValueChange = viewModel::updateDisplayName,
                    placeholder = stringResource(R.string.storage_plus_default_name_hint),
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
                else -> Text(stringResource(R.string.storage_plus_unsupported_type))
            }

            state.testResult?.let { ok ->
                TestResultCard(ok = ok)
            }

            if (state.mediaType == MediaType.SMB_SERVER || state.mediaType == MediaType.WEBDAV_SERVER) {
                ThumbnailCard(
                    state = state,
                    onPickMode = { showThumbnailModeDialog = true },
                    onPickWriteBack = { showThumbnailWriteBackDialog = true },
                )
            }

            StorageFormActions(
                state = state,
                onTest = viewModel::testConnection,
                onSave = viewModel::save,
            )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }

    if (showDeleteDialog) {
        NiConfirmDialog(
            title = stringResource(R.string.storage_plus_delete_title),
            text = stringResource(R.string.storage_plus_delete_confirm, state.displayName.ifBlank { state.url }),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
            confirmText = stringResource(R.string.delete),
        )
    }

    if (showThumbnailModeDialog) {
        val globalMode = ThumbnailSettings.generationMode
        NiListItemDialog(
            title = stringResource(R.string.storage_plus_thumbnail_mode_title),
            onDismiss = { showThumbnailModeDialog = false },
            items = buildList {
                add(
                    NiDialogItem(
                        label = stringResource(R.string.thumbnail_follow_global, stringResource(globalMode.labelRes)),
                        isSelected = state.thumbnailMode == null,
                        onClick = {
                            viewModel.updateThumbnailMode(null)
                            showThumbnailModeDialog = false
                        },
                    ),
                )
                ThumbnailGenerationMode.entries.forEach { option ->
                    add(
                        NiDialogItem(
                            label = stringResource(option.labelRes),
                            isSelected = state.thumbnailMode == option,
                            onClick = {
                                viewModel.updateThumbnailMode(option)
                                showThumbnailModeDialog = false
                            },
                        ),
                    )
                }
            },
        )
    }

    if (showThumbnailWriteBackDialog) {
        val globalWb = if (ThumbnailSettings.saveInSameDir) {
            stringResource(R.string.thumbnail_writeback_enabled)
        } else {
            stringResource(R.string.thumbnail_writeback_disabled)
        }
        NiListItemDialog(
            title = stringResource(R.string.storage_plus_thumbnail_writeback_title),
            onDismiss = { showThumbnailWriteBackDialog = false },
            items = listOf(
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_follow_global, globalWb),
                    isSelected = state.thumbnailWriteBack == null,
                    onClick = {
                        viewModel.updateThumbnailWriteBack(null)
                        showThumbnailWriteBackDialog = false
                    },
                ),
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_writeback_enabled),
                    isSelected = state.thumbnailWriteBack == true,
                    onClick = {
                        viewModel.updateThumbnailWriteBack(true)
                        showThumbnailWriteBackDialog = false
                    },
                ),
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_writeback_disabled),
                    isSelected = state.thumbnailWriteBack == false,
                    onClick = {
                        viewModel.updateThumbnailWriteBack(false)
                        showThumbnailWriteBackDialog = false
                    },
                ),
            ),
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
        MediaType.WEBDAV_SERVER -> Triple(stringResource(R.string.storage_type_webdav), Icons.Filled.CloudQueue, extraColors.storageWebdavColor)
        MediaType.SMB_SERVER -> Triple(stringResource(R.string.storage_type_smb), Icons.Filled.CloudQueue, extraColors.storageSmbColor)
        MediaType.EXTERNAL_STORAGE -> Triple(stringResource(R.string.storage_type_external), Icons.Filled.FolderOpen, extraColors.storageExternalColor)
        else -> Triple(stringResource(R.string.storage_type_unknown), Icons.Filled.Info, Color.Gray)
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
                    text = stringResource(R.string.storage_plus_type),
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
        label = stringResource(R.string.storage_plus_connect_success)
    } else {
        bgColor = Color(0xFFD32F2F).copy(alpha = 0.08f)
        icon = Icons.Filled.Warning
        tintColor = Color(0xFFD32F2F)
        label = stringResource(R.string.storage_plus_connect_failed)
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
        title = stringResource(R.string.storage_plus_connection_title),
        icon = Icons.Filled.CloudQueue,
        iconBg = extraColors.storageWebdavColor,
    ) {
        SegmentedRow(
            label = stringResource(R.string.storage_plus_protocol),
            leftLabel = "http://",
            rightLabel = "https://",
            isLeftSelected = !state.webDavUseHttps,
            onLeft = { vm.updateWebDavUseHttps(false) },
            onRight = { vm.updateWebDavUseHttps(true) },
        )
        FormTextField(
            label = stringResource(R.string.storage_plus_server_url),
            value = state.url,
            onValueChange = vm::updateUrl,
            placeholder = "example.com/webdav",
        )
    }

    FormCard(
        title = stringResource(R.string.storage_plus_auth_title),
        icon = Icons.Filled.Lock,
        iconBg = extraColors.storageWebdavColor,
    ) {
        SegmentedRow(
            label = stringResource(R.string.storage_plus_login_method),
            leftLabel = stringResource(R.string.storage_plus_anonymous),
            rightLabel = stringResource(R.string.storage_plus_account),
            isLeftSelected = state.isAnonymous,
            onLeft = { vm.updateAnonymous(true) },
            onRight = { vm.updateAnonymous(false) },
        )
        if (!state.isAnonymous) {
            FormTextField(
                label = stringResource(R.string.storage_plus_username),
                value = state.account,
                onValueChange = vm::updateAccount,
            )
            PasswordField(
                label = stringResource(R.string.storage_plus_password),
                value = state.password,
                onValueChange = vm::updatePassword,
                visible = passwordVisible,
                onToggleVisible = onTogglePassword,
            )
        }
    }

    FormCard(
        title = stringResource(R.string.storage_plus_advanced_title),
        icon = Icons.Filled.Settings,
        iconBg = extraColors.storageWebdavColor,
    ) {
        SegmentedRow(
            label = stringResource(R.string.storage_plus_parse_mode),
            leftLabel = stringResource(R.string.storage_plus_parse_strict),
            rightLabel = stringResource(R.string.storage_plus_parse_normal),
            isLeftSelected = state.webDavStrict,
            onLeft = { vm.updateWebDavStrict(true) },
            onRight = { vm.updateWebDavStrict(false) },
        )
        FieldDescription(
            text = if (state.webDavStrict)
                stringResource(R.string.storage_plus_parse_strict_desc)
            else
                stringResource(R.string.storage_plus_parse_normal_desc),
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
        title = stringResource(R.string.storage_plus_connection_title),
        icon = Icons.Filled.CloudQueue,
        iconBg = extraColors.storageSmbColor,
    ) {
        FormTextField(
            label = stringResource(R.string.storage_plus_ip),
            value = state.url,
            onValueChange = vm::updateUrl,
            placeholder = "192.168.1.1",
        )
        FormTextField(
            label = stringResource(R.string.storage_plus_port),
            value = if (state.port == 0) "" else state.port.toString(),
            onValueChange = vm::updatePort,
            keyboardType = KeyboardType.Number,
            placeholder = "445",
        )
        FormTextField(
            label = stringResource(R.string.storage_plus_share_path),
            value = state.smbSharePath,
            onValueChange = vm::updateSmbSharePath,
            placeholder = stringResource(R.string.storage_plus_share_path_hint),
        )
    }

    FormCard(
        title = stringResource(R.string.storage_plus_auth_title),
        icon = Icons.Filled.Lock,
        iconBg = extraColors.storageSmbColor,
    ) {
        SegmentedRow(
            label = stringResource(R.string.storage_plus_login_method),
            leftLabel = stringResource(R.string.storage_plus_anonymous),
            rightLabel = stringResource(R.string.storage_plus_account),
            isLeftSelected = state.isAnonymous,
            onLeft = { vm.updateAnonymous(true) },
            onRight = { vm.updateAnonymous(false) },
        )
        if (!state.isAnonymous) {
            FormTextField(
                label = stringResource(R.string.storage_plus_username),
                value = state.account,
                onValueChange = vm::updateAccount,
            )
            PasswordField(
                label = stringResource(R.string.storage_plus_password),
                value = state.password,
                onValueChange = vm::updatePassword,
                visible = passwordVisible,
                onToggleVisible = onTogglePassword,
            )
            FormTextField(
                label = stringResource(R.string.storage_plus_domain),
                value = state.domain,
                onValueChange = vm::updateDomain,
                placeholder = stringResource(R.string.storage_plus_domain_hint),
            )
        }
    }

    FormCard(
        title = stringResource(R.string.storage_plus_advanced_title),
        icon = Icons.Filled.Settings,
        iconBg = extraColors.storageSmbColor,
    ) {
        SegmentedRow(
            label = stringResource(R.string.storage_plus_encryption),
            leftLabel = stringResource(R.string.storage_plus_encryption_off),
            rightLabel = stringResource(R.string.storage_plus_encryption_on),
            isLeftSelected = !state.smbEncryption,
            onLeft = { vm.updateSmbEncryption(false) },
            onRight = { vm.updateSmbEncryption(true) },
        )
        FieldDescription(
            text = if (state.smbEncryption)
                stringResource(R.string.storage_plus_encryption_on_desc)
            else
                stringResource(R.string.storage_plus_encryption_off_desc),
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
        title = stringResource(R.string.storage_plus_dir_picker_title),
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
            Text(
                if (state.externalUri.isBlank()) {
                    stringResource(R.string.storage_plus_select_root_dir)
                } else {
                    stringResource(R.string.storage_plus_reselect_root_dir)
                },
            )
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

// ---- 缩略图卡片（仅 SMB / WebDAV） ----

@Composable
private fun ThumbnailCard(
    state: StoragePlusUiState,
    onPickMode: () -> Unit,
    onPickWriteBack: () -> Unit,
) {
    val globalMode = ThumbnailSettings.generationMode
    val modeValue = state.thumbnailMode?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.thumbnail_follow_global, stringResource(globalMode.labelRes))
    val globalWb = if (ThumbnailSettings.saveInSameDir) {
        stringResource(R.string.thumbnail_writeback_enabled)
    } else {
        stringResource(R.string.thumbnail_writeback_disabled)
    }
    val writeBackValue = when (state.thumbnailWriteBack) {
        true -> stringResource(R.string.thumbnail_writeback_enabled)
        false -> stringResource(R.string.thumbnail_writeback_disabled)
        null -> stringResource(R.string.thumbnail_follow_global, globalWb)
    }

    FormCard(
        title = stringResource(R.string.player_thumbnail_title),
        icon = Icons.Filled.Wallpaper,
        iconBg = Color(0xFF00ACC1),
    ) {
        FormSelectRow(
            label = stringResource(R.string.player_thumbnail_timing),
            value = modeValue,
            onClick = onPickMode,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        FormSelectRow(
            label = stringResource(R.string.player_thumbnail_upload),
            value = writeBackValue,
            onClick = onPickWriteBack,
        )
    }
}

@Composable
private fun FormSelectRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
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
                Text(if (visible) stringResource(R.string.hide) else stringResource(R.string.show))
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
private fun StorageFormActions(
    state: StoragePlusUiState,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
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
                    Text(stringResource(R.string.storage_plus_test_connection))
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
                    Text(stringResource(R.string.save))
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
                    Text(stringResource(R.string.save))
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
