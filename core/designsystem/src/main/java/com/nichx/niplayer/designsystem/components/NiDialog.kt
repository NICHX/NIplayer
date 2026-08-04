package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.designsystem.theme.NiExtraColors

// ──────── 主题感知的磨砂玻璃调色板 ────────

/**
 * 对话框背景色：浅色磨砂白 / 深色磨砂灰。
 *
 * 基于应用主题（[NiExtraColors.current.isDark]）而非系统主题判断，
 * 保证应用强制浅色/深色时对话框与页面保持一致。
 */
@Composable
private fun dialogSurfaceColor(): Color {
    return if (NiExtraColors.current.isDark) {
        Color(0xFF2C2C2E).copy(alpha = 0.95f)
    } else {
        Color.White.copy(alpha = 0.96f)
    }
}

/** 对话框边框色 */
@Composable
private fun dialogBorderColor(): Color {
    return if (NiExtraColors.current.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
}

/** 对话框标题/主文字色 */
@Composable
private fun dialogOnSurfaceColor(): Color {
    return if (NiExtraColors.current.isDark) {
        Color(0xFFE8E8EA)
    } else {
        Color(0xFF1C1B1F)
    }
}

/** 对话框分隔线色 */
@Composable
private fun dialogDividerColor(): Color {
    return if (NiExtraColors.current.isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
}

/**
 * 通用确认对话框 — 磨砂玻璃卡片风格（主题自适应）。
 *
 * - [Dialog] + [Surface] 自定义实现，替代 plain M3 AlertDialog
 * - 24dp 大圆角 + 半透明背景 + 阴影 + 细边框
 * - 浅色/深色模式自适应
 */
@Composable
fun NiConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确认",
    dismissText: String = "取消",
    confirmDanger: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogSurfaceColor(),
            shadowElevation = 12.dp,
            border = BorderStroke(0.5.dp, dialogBorderColor()),
            modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = dialogOnSurfaceColor(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider(
                    color = dialogDividerColor(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = confirmText,
                            color = if (confirmDanger) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 列表选择对话框 — 磨砂玻璃卡片风格（主题自适应）。
 *
 * - [Dialog] + [Surface] 自定义实现，绕过 AlertDialog 系统默认装饰
 * - 24dp 大圆角 + 半透明背景 + 阴影 + 细边框
 * - 标题 + 分隔线 + 列表项
 * - 列表项 48dp 行高，选中项 primaryContainer 高亮 + Check 图标
 */
@Composable
fun NiListItemDialog(
    title: String,
    items: List<NiDialogItem>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogSurfaceColor(),
            shadowElevation = 12.dp,
            border = BorderStroke(0.5.dp, dialogBorderColor()),
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = dialogOnSurfaceColor(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider(
                    color = dialogDividerColor(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                items.forEachIndexed { index, item ->
                    NiDialogItemRow(item)
                    if (index < items.size - 1) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

/**
 * 单个列表项行（公开，供 [NiInfoDialog] 等自定义对话框内容复用）。
 *
 * 样式与 [NiListItemDialog] 内部行完全一致：48dp 行高、选中 primaryContainer 高亮 + Check 图标。
 */
@Composable
fun NiDialogItemRow(item: NiDialogItem) {
    val isSelected = item.isSelected
    val textOnSurface = dialogOnSurfaceColor()
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textOnSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = item.onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isSelected) primaryColor else textOnSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(30.dp))
        }
        Text(
            text = item.label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 信息展示对话框 — 磨砂玻璃卡片风格（主题自适应）。
 *
 * 与 [NiListItemDialog] 视觉统一：24dp 圆角 + 半透明背景 + 阴影 + 边框 + 标题分隔线。
 * 调用方通过 [content] 注入具体内容，通过 [actions] 注入底部按钮区（可选）。
 */
@Composable
fun NiInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogSurfaceColor(),
            shadowElevation = 12.dp,
            border = BorderStroke(0.5.dp, dialogBorderColor()),
            modifier = Modifier.widthIn(min = 260.dp, max = 340.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = dialogOnSurfaceColor(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider(
                    color = dialogDividerColor(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    content()
                }
                if (actions != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

data class NiDialogItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val iconTint: Color? = null,
    val labelColor: Color? = null,
    val isSelected: Boolean = false,
)
