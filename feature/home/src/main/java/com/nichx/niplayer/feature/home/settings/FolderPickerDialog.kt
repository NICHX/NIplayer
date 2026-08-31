package com.nichx.niplayer.feature.home.settings

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.R
import java.io.File

/**
 * 应用内目录选择器（磨砂玻璃风格，复用 [NiInfoDialog]）。
 *
 * 依赖已授予的「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE），以原生 File 遍历共享存储，
 * 应用内直接选择目录——不触发系统 SAF 的「允许访问文件夹吗」弹窗，写入保持原生直写速度。
 * 确认时用目录名作为展示名。
 */
@Composable
fun FolderPickerDialog(
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    onDismiss: () -> Unit,
    onConfirm: (path: String, dirName: String) -> Unit,
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

    val surfaceTint = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val hoverBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
    val iconBoxBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val dividerColor = NiExtraColors.current.surfaceLevel3

    NiInfoDialog(
        title = stringResource(R.string.download_dir_picker_choose_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.download_dir_picker_cancel))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    val dir = File(currentDir)
                    val name = dir.name.ifBlank { currentDir.substringAfterLast('/') }
                    onConfirm(currentDir, name)
                },
            ) {
                Text(stringResource(R.string.download_dir_picker_choose_here))
            }
        },
    ) {
        // 当前路径胶囊 + 返回上级
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            if (hasParent) {
                IconButton(onClick = {
                    File(currentDir).parentFile?.let { currentDir = it.absolutePath }
                }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.download_dir_picker_parent),
                        tint = surfaceTint,
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
                            Modifier.clickable {
                                File(currentDir).parentFile?.let { currentDir = it.absolutePath }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = surfaceTint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentDir,
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 子目录列表
        if (children.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.download_dir_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(children, key = { _, f -> f.absolutePath }) { index, dir ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(hoverBg, RoundedCornerShape(12.dp))
                            .clickable { currentDir = dir.absolutePath }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(iconBoxBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = surfaceTint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = dir.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (index < children.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            color = dividerColor,
                        )
                    }
                }
            }
        }
    }
}