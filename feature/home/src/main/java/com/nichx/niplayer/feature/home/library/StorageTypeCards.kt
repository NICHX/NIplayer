package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.nichx.niplayer.datastore.ThumbnailSettings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.theme.NiExtraColors


// ---- 类型标识卡片 ----

@Composable
internal fun StorageTypeBadge(
    type: MediaType,
    extraColors: NiExtraColors,
) {
    val (label, icon, color) = when (type) {
        MediaType.WEBDAV_SERVER -> Triple(stringResource(R.string.storage_type_webdav), Icons.Filled.CloudQueue, extraColors.storageWebdavColor)
        MediaType.SMB_SERVER -> Triple(stringResource(R.string.storage_type_smb), Icons.Filled.CloudQueue, extraColors.storageSmbColor)
        MediaType.EXTERNAL_STORAGE -> Triple(stringResource(R.string.storage_type_external), Icons.Filled.FolderOpen, extraColors.storageExternalColor)
        MediaType.LOCAL_STORAGE,
        MediaType.OTHER_STORAGE,
        MediaType.QUICK_ACCESS -> Triple(stringResource(R.string.storage_type_unknown), Icons.Filled.Info, Color.Gray)
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

// ---- 测试结果卡片 ----

@Composable
internal fun TestResultCard(ok: Boolean) {
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
internal fun WebDavCards(
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
internal fun SmbCards(
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
internal fun ExternalCard(
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
internal fun ThumbnailCard(
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
