package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenuItem
import com.nichx.niplayer.designsystem.components.NiGlassDropdownMenu
import com.nichx.niplayer.designsystem.components.NiGlassOverlay
import com.nichx.niplayer.designsystem.components.NiGlassOverlayKind
import com.nichx.niplayer.designsystem.components.NiGlassOverlayRequest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity
import com.nichx.niplayer.designsystem.components.glassOnSurface
import com.nichx.niplayer.designsystem.components.glassOnSurfaceMuted
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.storage.StorageFile
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow


/**
 * 卡片/瓦片右上角的单文件操作入口（⋮）按钮。
 *
 * 仅渲染纯 ⋮ 图标、无任何背景/描边/阴影，视觉干净利落。
 */
@Composable
internal fun CardMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.storage_file_more),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileActionsSheet(
    file: StorageFile,
    isFavorited: Boolean,
    canDownload: Boolean,
    showFileManagement: Boolean,
    showDelete: Boolean = false,
    isEncrypted: Boolean = false,
    isRemoteStorage: Boolean = true,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onToggleQuickAccess: () -> Unit,
    onShowInfo: () -> Unit,
    onRename: () -> Unit = {},
    onMove: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEncrypt: () -> Unit = {},
    onDecrypt: () -> Unit = {},
    onResetPassword: () -> Unit = {},
) {
    val overlayId = remember(file.path) { "file_actions_${file.path}" }
    val isPlayable = !file.isDirectory && (MediaFileTypes.isVideoFile(file.name) || MediaFileTypes.isAudioFile(file.name))

    // 投递到全局玻璃浮层槽位（NiGlassBottomSheet，backdrop 真模糊，透明度随面板设置）
    LaunchedEffect(file, isFavorited, canDownload, showFileManagement, isEncrypted, isRemoteStorage) {
        NiGlassOverlay.show(
            NiGlassOverlayRequest(
                id = overlayId,
                kind = NiGlassOverlayKind.BottomSheet,
                onDismiss = onDismiss,
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

            if (isPlayable) {
                ActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    text = stringResource(R.string.storage_file_action_play),
                    onClick = onPlay,
                )
            }
            if (canDownload) {
                ActionRow(
                    icon = Icons.Rounded.Download,
                    text = stringResource(R.string.storage_file_action_download),
                    onClick = onDownload,
                )
            }
            ActionRow(
                icon = if (isFavorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                text = stringResource(
                    if (isFavorited) R.string.storage_file_action_remove_from_quick_access
                    else R.string.storage_file_action_add_to_quick_access,
                ),
                onClick = onToggleQuickAccess,
            )
            if (showFileManagement) {
                ActionRow(
                    icon = Icons.Rounded.Edit,
                    text = stringResource(R.string.storage_file_action_rename),
                    onClick = onRename,
                )
                ActionRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    text = stringResource(R.string.storage_file_action_move),
                    onClick = onMove,
                )
            }
            if (showDelete) {
                ActionRow(
                    icon = Icons.Rounded.Delete,
                    text = stringResource(R.string.storage_file_action_delete),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // 文件夹访问加密（仅远程存储 SMB/WebDAV 支持；本地/SAF 不加密）
            if (file.isDirectory && isRemoteStorage) {
                if (isEncrypted) {
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = stringResource(R.string.storage_file_action_reset_password),
                        onClick = onResetPassword,
                    )
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = stringResource(R.string.storage_file_action_decrypt),
                        onClick = onDecrypt,
                    )
                } else {
                    ActionRow(
                        icon = Icons.Rounded.Lock,
                        text = stringResource(R.string.storage_file_action_encrypt_folder),
                        onClick = onEncrypt,
                    )
                }
            }
            ActionRow(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.storage_file_action_properties),
                onClick = onShowInfo,
            )
            }
        }
    )
    }
    DisposableEffect(overlayId) {
        onDispose { NiGlassOverlay.dismiss(overlayId) }
    }
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

/** 多选模式底部操作栏：液态玻璃胶囊。固定三操作位（全选/下载/删除）+ 顶部"已选N项/关闭"行。 */
@Composable
internal fun MultiSelectActionBar(
    backdrop: Backdrop,
    selectedCount: Int,
    allSelected: Boolean,
    downloadEnabled: Boolean,
    fileManagementEnabled: Boolean,
    moreMenuExpanded: Boolean,
    onMoreMenuOpenChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onAddToQuickAccess: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInLightTheme = !NiExtraColors.current.isDark
    // 与悬浮底栏一致的液态玻璃容器：vibrancy + blur + lens + 高光边 + 柔和阴影，
    // 背景由 [backdrop] 捕获页面内容，实现真实背景模糊；不透明度由 LocalNiGlassOpacity 统一控制
    val containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalNiGlassOpacity.current)
    // "更多"菜单锚点：取按钮右下角，从按钮下方展开（近屏底时自动上抬）
    var moreAnchor by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(6f.dp.toPx(), 6f.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 1f) },
                shadow = {
                    Shadow.Default.copy(color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f))
                },
                onDrawSurface = { drawRect(containerColor) },
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部行：已选数量 + 关闭按钮（选中态指示下沉到操作栏，并提供一键退出）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_file_selected_count, selectedCount),
                    style = MaterialTheme.typography.labelMedium,
                    // 玻璃底上用高对比前景色，避免灰色次文字对比不足
                    color = glassOnSurfaceMuted(),
                )
                IconButton(onClick = onClose) {
                    NiStyleIcon(
                        icon = Icons.Rounded.Close,
                        style = NiAppIconStyle,
                        containerSize = 32.dp,
                        iconSize = 18.dp,
                        contentDescription = stringResource(R.string.storage_file_cancel_multi_select),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            // 固定操作位（全选/下载）+ 更多：不按选中内容显隐，仅置灰，保证布局稳定不抖动；
            // 低频/破坏性操作收进「更多」展开菜单，避免固定位过多
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionBarItem(
                    icon = if (allSelected) Icons.Rounded.Close else Icons.Rounded.SelectAll,
                    label = stringResource(
                        if (allSelected) R.string.storage_file_deselect_all
                        else R.string.storage_file_select_all,
                    ),
                    enabled = selectedCount > 0,
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                )
                ActionBarItem(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.storage_file_action_download),
                    enabled = downloadEnabled,
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                )
                ActionBarItem(
                    icon = Icons.Rounded.MoreVert,
                    label = stringResource(R.string.more),
                    enabled = selectedCount > 0,
                    onClick = { onMoreMenuOpenChange(true) },
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            val topLeft = coords.localToRoot(Offset.Zero)
                            moreAnchor = topLeft + Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                        },
                )
            }
        }
        // 「更多」展开菜单：移动/复制（需文件管理能力）/快速访问/删除
        NiGlassDropdownMenu(
            expanded = moreMenuExpanded,
            onDismissRequest = { onMoreMenuOpenChange(false) },
            anchor = IntOffset(moreAnchor.x.toInt(), moreAnchor.y.toInt()),
        ) {
            val hasSelection = selectedCount > 0
            BatchActionMenuItem(
                icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                text = stringResource(R.string.move_to),
                enabled = fileManagementEnabled && hasSelection,
                onClick = onMove,
            )
            BatchActionMenuItem(
                icon = Icons.Rounded.ContentCopy,
                text = stringResource(R.string.copy_to),
                enabled = fileManagementEnabled && hasSelection,
                onClick = onCopy,
            )
            BatchActionMenuItem(
                icon = Icons.Rounded.Star,
                text = stringResource(R.string.storage_file_action_add_to_quick_access),
                enabled = hasSelection,
                onClick = onAddToQuickAccess,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            BatchActionMenuItem(
                icon = Icons.Rounded.Delete,
                text = stringResource(R.string.delete),
                enabled = hasSelection,
                onClick = onDelete,
                isDanger = true,
            )
        }
    }
}

/** 「更多」菜单内的批量操作项（图标 + 文案）。 */
@Composable
internal fun BatchActionMenuItem(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDanger: Boolean = false,
) {
    DropdownMenuItem(
        modifier = Modifier.height(40.dp),
        enabled = enabled,
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    isDanger -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    isDanger -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
internal fun ActionBarItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> glassOnSurfaceMuted().copy(alpha = 0.5f)
        isDanger -> MaterialTheme.colorScheme.error
        else -> glassOnSurface()
    }
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
