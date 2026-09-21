package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.OutlinedTextField
import com.nichx.niplayer.designsystem.components.DownloadDialogShell
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nichx.niplayer.designsystem.components.NiAutoFocusAndShowKeyboard
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.nichx.niplayer.storage.StorageFile


@Composable
internal fun FileInfoDialog(file: StorageFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    NiInfoDialog(
        title = stringResource(R.string.storage_file_properties_title),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            InfoRow(label = stringResource(R.string.storage_file_info_name), value = file.name)
            if (!file.isDirectory && file.length > 0) {
                InfoRow(label = stringResource(R.string.storage_file_info_size), value = formatFileSize(file.length))
            }
            if (file.lastModified > 0) {
                InfoRow(label = stringResource(R.string.storage_file_info_modified), value = formatDate(file.lastModified, context))
            }
            InfoRow(label = stringResource(R.string.storage_file_info_path), value = file.path)
            InfoRow(label = stringResource(R.string.storage_file_info_type), value = fileTypeLabel(file, context))
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 移动/复制冲突弹窗：目标目录已有同名文件，让用户选择「跳过重复 / 覆盖 / 取消」。 */
@Composable
internal fun TransferConflictDialog(
    duplicateCount: Int,
    onSkip: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    NiInfoDialog(
        title = stringResource(R.string.storage_file_conflict_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.storage_file_conflict_skip)) }
            TextButton(onClick = onOverwrite) { Text(stringResource(R.string.storage_file_conflict_overwrite)) }
        },
    ) {
        Text(
            text = stringResource(R.string.storage_file_conflict_body, duplicateCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 重命名对话框。预填当前文件名（不含扩展名），用户确认后回调 [onConfirm]。 */
@Composable
fun RenameFileDialog(
    fileName: String,
    isDirectory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 预填名称：目录取全名；文件仅在确实存在后缀（最后一个 . 不在首位/末尾）时取主名，
    // 文件名中间的 . 视为名称本身的一部分，不当作扩展名分隔符
    val dot = if (isDirectory) -1 else fileName.lastIndexOf('.')
    val hasExtension = dot > 0 && dot < fileName.length - 1
    val extension = if (hasExtension) fileName.substring(dot + 1) else ""
    val initial = if (hasExtension) fileName.substringBeforeLast('.') else fileName
// 用 TextFieldValue 控制光标：初始即定位到文本末尾，长文件名默认光标在最后
    var nameState by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length))) }
    val newName = nameState.text
    // 弹窗显示即自动聚焦输入框并拉起输入法（在 content 内触发，见 NiAutoFocusAndShowKeyboard）
    val focusRequester = remember { FocusRequester() }

    NiInfoDialog(
        title = stringResource(R.string.storage_file_rename_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    // 文件重命名时若新名称未携带原扩展名（按结尾匹配，避免把名称中间的 . 当作扩展名），自动补回
                    val trimmed = newName.trim()
                    val finalName = if (isDirectory || !hasExtension ||
                        trimmed.endsWith(extension, ignoreCase = true)
                    ) {
                        trimmed
                    } else {
                        trimmed + ".$extension"
                    }
                    onConfirm(finalName)
                },
                enabled = newName.isNotBlank() && newName != initial,
            ) { Text(stringResource(R.string.confirm)) }
        },
    ) {
        NiAutoFocusAndShowKeyboard(focusRequester)
        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text(stringResource(R.string.storage_file_rename_new_name)) },
            // 长文件名支持换行显示；上限 5 行防止弹窗过高，超出内部滚动
            singleLine = false,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            shape = NiTextFieldDefaults.Shape,
            colors = NiTextFieldDefaults.colors(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 0.9f,
            ),
        )
    }
}

/** 多选批量传输操作类型：移动到 / 复制到。 */
internal enum class BatchTransferOp { MOVE, COPY }

/** 目录选择对话框目录列表固定高度：加载/空态/列表共用，切换目录时弹窗尺寸不跳动。 */
internal val TargetListHeight = 320.dp

/**
 * 目录浏览式目标选择对话框（用于"移动到"/"复制到"）。
 *
 * 复用下载管理器文件选择弹窗样式（[DownloadDialogShell] 宽面板）。
 * **点击子目录仅进入该目录浏览**，点击底部「确定」才把当前所在目录作为移动/复制目标，
 * 避免误触即执行。
 *
 * @param title 标题文案
 * @param confirmText 底部确认按钮文案（如"移动到此处"/"复制到此处"）
 * @param startPath 初始浏览目录（当前所在目录路径）
 * @param listSubfolders 列出指定路径下直接子目录的挂起回调
 * @param toDirectory 由路径构造目录 [StorageFile]（确认时的目标）
 * @param onDismiss 关闭
 * @param onConfirm 点确认：以当前目录为目标执行
 */
@Composable
internal fun FolderTargetDialog(
    title: String,
    confirmText: String,
    startPath: String,
    listSubfolders: suspend (String) -> List<StorageFile>,
    toDirectory: (String) -> StorageFile,
    encryptedPaths: Set<String>,
    unlockFolder: suspend (StorageFile, String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (StorageFile) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 当前浏览目录路径：进入子目录则更新，返回上级则回溯
    var currentPath by remember { mutableStateOf(startPath) }
    val subfolders = remember { mutableStateOf<List<StorageFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    // 本次弹窗会话内已成功解锁的文件夹路径：已解锁后再次点击直接进入，不再重复弹密码
    var unlockedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 待解锁文件夹（点击加密目录时置非空，在弹窗内部联显示密码输入，避免根遮罩层级被压）
    var unlockTarget by remember { mutableStateOf<StorageFile?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var unlocking by remember { mutableStateOf(false) }
    val wrongPasswordText = stringResource(R.string.storage_file_wrong_password)

    LaunchedEffect(currentPath) {
        loading = true
        subfolders.value = listSubfolders(currentPath)
        loading = false
    }

    DownloadDialogShell(
        forceDark = false,
        title = title,
        onClose = onDismiss,
        content = {
            // 当前位置指示：可点击返回上级目录
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .then(
                        if (currentPath.isNotEmpty()) {
                            Modifier.clickable { currentPath = currentPath.substringBeforeLast('/') }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = if (currentPath.isNotEmpty()) Icons.AutoMirrored.Rounded.ArrowBack
                    else Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (currentPath.isEmpty()) stringResource(R.string.storage_file_move_root)
                    else currentPath,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            // 固定高度区域：目录加载/空态/列表共用同一高度，避免切换目录时弹窗尺寸跳动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TargetListHeight),
            ) {
                when {
                    unlockTarget != null -> TargetUnlockForm(
                        folder = unlockTarget!!,
                        password = unlockPassword,
                        error = unlockError,
                        unlocking = unlocking,
                        onPasswordChange = { unlockPassword = it; unlockError = null },
                        onCancel = { unlockTarget = null },
                        onSubmit = {
                            unlockError = null
                            unlocking = true
                            scope.launch {
                                val target = unlockTarget
                                val ok = if (target != null) {
                                    unlockFolder(target, unlockPassword.trim())
                                } else {
                                    false
                                }
                                unlocking = false
                                if (ok && target != null) {
                                    unlockedPaths = unlockedPaths + target.path.trimEnd('/')
                                    unlockTarget = null
                                    currentPath = target.path
                                } else {
                                    unlockError = wrongPasswordText
                                }
                            }
                        },
                    )
                    loading -> TargetListLoading()
                    subfolders.value.isEmpty() -> TargetListEmpty(stringResource(R.string.storage_file_move_no_target))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(subfolders.value, key = { _, child -> child.path }) { _, child ->
                        val childKey = child.path.trimEnd('/')
                        // 加密且未在本会话解锁：点击弹出密码框，解锁成功后自动进入
                        val isLocked = childKey in encryptedPaths && childKey !in unlockedPaths
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                // 点击仅进入该子目录浏览，不直接执行移动/复制
                                .clickable {
                                    if (isLocked) {
                                        unlockTarget = child
                                        unlockPassword = ""
                                        unlockError = null
                                    } else {
                                        currentPath = child.path
                                    }
                                }
                                .padding(horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = if (isLocked) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            if (isLocked) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = stringResource(R.string.storage_file_encrypted),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = child.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(toDirectory(currentPath)) }) { Text(confirmText) }
        },
    )
}

/** 候选目录加载占位：避免异步加载期间短暂显示"空"造成的闪烁。 */
@Composable
internal fun TargetListLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** 无可选目录提示。 */
@Composable
internal fun TargetListEmpty(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 目录选择弹窗内的加密文件夹解锁表单。
 *
 * 直接渲染在目录选择器自身的 Dialog 窗口内，规避根玻璃遮罩被独立 Dialog 窗口压在下方的问题。
 */
@Composable
internal fun TargetUnlockForm(
    folder: StorageFile,
    password: String,
    error: String?,
    unlocking: Boolean,
    onPasswordChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.storage_file_unlock_body, folder.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onCancel,
                enabled = !unlocking,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        NiAutoFocusAndShowKeyboard(focusRequester)
        NiTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.storage_file_password_label),
            placeholder = stringResource(R.string.storage_file_password_placeholder),
            isError = error != null,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (password.isNotBlank() && !unlocking) onSubmit() },
            ),
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !unlocking,
            ) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onSubmit,
                enabled = password.isNotBlank() && !unlocking,
            ) { Text(stringResource(R.string.storage_file_unlock)) }
        }
    }
}

/** 删除确认对话框。 */
@Composable
fun DeleteConfirmDialog(
    fileName: String,
    isDirectory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NiInfoDialog(
        title = stringResource(
            if (isDirectory) R.string.storage_file_delete_folder
            else R.string.storage_file_delete_file,
        ),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.delete)) }
        },
    ) {
        Text(
            text = stringResource(
                if (isDirectory) R.string.storage_file_delete_confirm_dir
                else R.string.storage_file_delete_confirm_file,
                fileName,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 新建文件夹对话框。 */
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // 弹窗显示即自动聚焦输入框并拉起输入法（需在 content 内触发，见 NiAutoFocusAndShowKeyboard）
    val focusRequester = remember { FocusRequester() }
    NiInfoDialog(
        title = stringResource(R.string.storage_file_new_folder),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.create)) }
        },
    ) {
        NiAutoFocusAndShowKeyboard(focusRequester)
        NiTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.storage_file_folder_name),
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- 文件夹访问加密对话框 ----

/** 设置 / 取消文件夹访问密码对话框。 */
@Composable
fun FolderPasswordDialog(
    title: String,
    subtitle: String,
    confirmText: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    NiInfoDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onConfirm(password.trim()) },
                enabled = password.length >= 4,
            ) { Text(confirmText) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NiAutoFocusAndShowKeyboard(focusRequester)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NiTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.storage_file_password_label_min4),
                placeholder = stringResource(R.string.storage_file_password_placeholder),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 解锁加密文件夹对话框：密码输入 + 解锁，密码错误时内联提示。 */
@Composable
fun FolderUnlockDialog(
    folder: StorageFile,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onPasswordSubmit: (String) -> Unit,
    onPasswordChange: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    // 弹窗显示即自动聚焦密码输入框并拉起输入法（在 content 内触发）
    val focusRequester = remember { FocusRequester() }
    NiInfoDialog(
        title = stringResource(R.string.storage_file_unlock_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onPasswordSubmit(password.trim()) },
                enabled = password.isNotBlank(),
            ) { Text(stringResource(R.string.storage_file_unlock)) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NiAutoFocusAndShowKeyboard(focusRequester)
            Text(
                text = stringResource(R.string.storage_file_unlock_body, folder.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NiTextField(
                value = password,
                onValueChange = {
                    password = it
                    onPasswordChange()
                },
                label = stringResource(R.string.storage_file_password_label),
                placeholder = stringResource(R.string.storage_file_password_placeholder),
                isError = errorMessage != null,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 修改文件夹访问密码对话框：验证当前密码 + 输入新密码两次。 */
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun ResetFolderPasswordDialog(
    folder: StorageFile,
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // 弹窗显示即自动聚焦"当前密码"输入框并拉起输入法（在 content 内触发）
    val focusRequester = remember { FocusRequester() }
    NiInfoDialog(
        title = stringResource(R.string.storage_file_change_password_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = {
                    if (newPassword.length < 4) {
                        error = context.getString(R.string.storage_file_password_min4_error)
                    } else if (newPassword != confirmPassword) {
                        error = context.getString(R.string.storage_file_password_mismatch)
                    } else {
                        onConfirm(oldPassword.trim(), newPassword.trim())
                    }
                },
                enabled = oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NiAutoFocusAndShowKeyboard(focusRequester)
            Text(
                text = stringResource(R.string.storage_file_change_password_body, folder.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NiTextField(
                value = oldPassword,
                onValueChange = {
                    oldPassword = it
                    error = null
                },
                label = stringResource(R.string.storage_file_current_password),
                placeholder = stringResource(R.string.storage_file_current_password_placeholder),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NiTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    error = null
                },
                label = stringResource(R.string.storage_file_new_password),
                placeholder = stringResource(R.string.storage_file_new_password_placeholder),
                isError = error != null,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NiTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    error = null
                },
                label = stringResource(R.string.storage_file_confirm_password),
                placeholder = stringResource(R.string.storage_file_confirm_password_placeholder),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
