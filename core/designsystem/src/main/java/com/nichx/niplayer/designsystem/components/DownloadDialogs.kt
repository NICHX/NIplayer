package com.nichx.niplayer.designsystem.components

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.designsystem.R
import java.io.File

/**
 * 宽面板对话框的通用外壳（各 feature 共享）。
 *
 * **液态玻璃材质**：面板采用项目玻璃设计令牌——磨砂表面 [niFrostSurfaceColor] +
 * 玻璃细描边 [niGlassBorderColor]，宽面板显式控制宽度（min=320, max=480，非平台默认窄宽）。
 * [forceDark] = true（视频播放器等）时强制深色配色。
 *
 * 保持独立 Dialog（而非全局玻璃浮层）：这些选择器内含需实时更新的交互状态（如目录导航），
 * 浮层宿主按 id 去重、内容仅组合一次无法响应状态变化。外部点击不关闭
 * （dismissOnClickOutside=false），标题右侧为「关闭」按钮。
 */
@Composable
fun DownloadDialogShell(
    forceDark: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Dialog(
        onDismissRequest = { /* 外部点击不关闭，仅返回/按钮可关 */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        if (forceDark) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                WideGlassPanel(title = title, onClose = onClose, content = content, actions = actions)
            }
        } else {
            WideGlassPanel(title = title, onClose = onClose, content = content, actions = actions)
        }
    }
}

/** 液态玻璃宽面板主体：压暗遮罩 + 磨砂玻璃面板（标题行/分隔线/内容区/底部操作行）。 */
@Composable
private fun WideGlassPanel(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    // 项目玻璃设计令牌：磨砂表面 + 玻璃细描边（独立 Dialog 窗口，不依赖 backdrop 真模糊）
    val panelSurface = niFrostSurfaceColor()
    val borderColor = niGlassBorderColor()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 系统样式压暗遮罩（不拦截点击，外部点击不关闭）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
        )
        Surface(
            shape = shape,
            color = panelSurface,
            border = BorderStroke(NiGlassHairWidth, borderColor),
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    content()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
    }
}

/** 对话框内通用可点行（图标底托 + 标题，可选副标题 + 尾箭头）。 */
@Composable
private fun DarkRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val iconBoxBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBoxBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Icon(
                imageVector = trailing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 下载目标选择对话框（通用）。
 *
 * 每次显示「预设目录 / 选择下载目录」：
 * - **预设目录**：已设置则回调 [onDownloadToPreset]；未设置进入目录选择并顺手设为预设
 * - **选择下载目录**：应用内目录浏览，单次下载（不改预设）
 *
 * @param presetPath 当前预设下载目录绝对路径；null 表示未设置
 * @param forceDark 固定深色（视频播放器）或跟随主题
 */
@Composable
fun DownloadTargetChooserDialog(
    presetPath: String?,
    onDismiss: () -> Unit,
    onDownloadToPreset: () -> Unit,
    onDownloadToPath: (path: String, dirName: String, setAsPreset: Boolean) -> Unit,
    forceDark: Boolean = false,
) {
    var showPicker by remember { mutableStateOf(false) }
    var pickSetAsPreset by remember { mutableStateOf(false) }

    if (showPicker) {
        FolderPickerDialog(
            onDismiss = { showPicker = false },
            onConfirm = { path, name -> onDownloadToPath(path, name, pickSetAsPreset) },
            // 顶部 ✕ 一次性关闭整个弹窗
            onClose = onDismiss,
            forceDark = forceDark,
        )
        return
    }

    val presetSet = presetPath != null
    DownloadDialogShell(
        forceDark = forceDark,
        title = stringResource(R.string.download_choose_target_title),
        onClose = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        content = {
            DarkRow(
                icon = Icons.Filled.Folder,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.download_target_preset),
                subtitle = if (presetSet) {
                    stringResource(R.string.download_target_preset_subtitle, presetPath!!)
                } else {
                    stringResource(R.string.download_target_preset_not_set)
                },
                onClick = {
                    if (presetSet) {
                        onDownloadToPreset()
                    } else {
                        pickSetAsPreset = true
                        showPicker = true
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            DarkRow(
                icon = Icons.Filled.ArrowForward,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = stringResource(R.string.download_target_custom),
                onClick = {
                    pickSetAsPreset = false
                    showPicker = true
                },
            )
        },
    )
}

/**
 * 应用内目录选择器（通用）。
 *
 * 依赖已授予的「所有文件访问权限」，原生 File 遍历共享存储，应用内选目录——
 * 不触发系统 SAF「允许访问文件夹吗」弹窗。目录间无分割线。
 *
 * @param onClose 顶部 ✕ 关闭：一次性关闭整个弹窗（区别于底部「取消」返回上一层）
 * @param forceDark 固定深色（视频播放器）或跟随主题
 */
@Composable
fun FolderPickerDialog(
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    onDismiss: () -> Unit,
    onConfirm: (path: String, dirName: String) -> Unit,
    onClose: () -> Unit = onDismiss,
    forceDark: Boolean = false,
) {
    val root = remember { Environment.getExternalStorageDirectory().absolutePath }
    var currentDir by remember(initialPath) {
        mutableStateOf(File(initialPath).takeIf { it.isDirectory }?.absolutePath ?: root)
    }
    val hasParent = remember(currentDir) {
        File(currentDir).parentFile?.absolutePath?.let { it != currentDir } ?: false
    }
    val children = remember(currentDir) {
        File(currentDir).listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    DownloadDialogShell(
        forceDark = forceDark,
        title = stringResource(R.string.folder_picker_choose_title),
        onClose = onClose,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = {
                val dir = File(currentDir)
                val name = dir.name.ifBlank { currentDir.substringAfterLast('/') }
                onConfirm(currentDir, name)
            }) {
                Text(stringResource(R.string.folder_picker_choose_here), fontSize = 14.sp)
            }
        },
        content = {
            // 当前路径胶囊 + 返回上级
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                if (hasParent) {
                    IconButton(onClick = { File(currentDir).parentFile?.let { currentDir = it.absolutePath } }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.folder_picker_parent),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .then(
                            if (hasParent) {
                                Modifier.clickable { File(currentDir).parentFile?.let { currentDir = it.absolutePath } }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = currentDir,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 子目录列表（无分割线、无间距，平铺紧凑排列）
            if (children.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.folder_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                ) {
                    itemsIndexed(children, key = { _, f -> f.absolutePath }) { _, dir ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { currentDir = dir.absolutePath }
                                .padding(horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = dir.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}