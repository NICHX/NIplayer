package com.nichx.niplayer.designsystem.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.designsystem.R
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlin.uuid.Uuid

// ──────── 主题感知的磨砂玻璃调色板 ────────

/**
 * 对话框面板底色：统一走 [niFrostSurfaceColor]（**不透明**磨砂色）。
 *
 * 对话框位于独立窗口，半透明底色会把窗口背景/系统压暗层透出来形成多余矩形，
 * 因此不再区分玻璃开关的降级色。
 */

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
 * - 24dp 大圆角 + 不透明磨砂底色 + 阴影 + 细边框
 * - 浅色/深色模式自适应
 */
@Composable
fun NiConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "",
    dismissText: String = "",
    confirmDanger: Boolean = false,
) {
    val overlayId = remember { "confirm_${Uuid.random()}" }
    val resolvedConfirm = if (confirmText.isBlank()) stringResource(R.string.action_confirm) else confirmText
    val resolvedDismiss = if (dismissText.isBlank()) stringResource(R.string.action_cancel) else dismissText

    // 投递到全局玻璃浮层槽位（NiGlassDialog，backdrop 真模糊）
    LaunchedEffect(Unit) {
        NiGlassOverlay.show(
            NiGlassOverlayRequest(
                id = overlayId,
                kind = NiGlassOverlayKind.Dialog,
                title = title,
                onDismiss = onDismiss,
            ) {
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
                        Text(resolvedDismiss)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = resolvedConfirm,
                            color = if (confirmDanger) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { NiGlassOverlay.dismiss(overlayId) }
    }
}

/**
 * 列表选择对话框 — 磨砂玻璃卡片风格（主题自适应）。
 *
 * - [Dialog] + [Surface] 自定义实现，绕过 AlertDialog 系统默认装饰
 * - 24dp 大圆角 + 不透明磨砂底色 + 阴影 + 细边框
 * - 标题 + 分隔线 + 列表项
 * - 列表项 44dp 行高，选中项 primaryContainer 高亮 + Check 图标
 */
@Composable
fun NiListItemDialog(
    title: String,
    items: List<NiDialogItem>,
    onDismiss: () -> Unit,
) {
    val overlayId = remember { "list_${Uuid.random()}" }
    // 投递到全局玻璃浮层槽位（NiGlassDialog，backdrop 真模糊）
    LaunchedEffect(Unit) {
        NiGlassOverlay.show(
            NiGlassOverlayRequest(
                id = overlayId,
                kind = NiGlassOverlayKind.Dialog,
                title = title,
                onDismiss = onDismiss,
            ) {
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
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { NiGlassOverlay.dismiss(overlayId) }
    }
}

/**
 * 单个列表项行（公开，供 [NiInfoDialog] 等自定义对话框内容复用）。
 *
 * 样式与 [NiListItemDialog] 内部行完全一致：44dp 行高、选中 primaryContainer 高亮 + Check 图标。
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .height(44.dp)
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
 * 与 [NiListItemDialog] 视觉统一：24dp 圆角 + 不透明磨砂底色 + 阴影 + 边框 + 标题分隔线。
 * 调用方通过 [content] 注入具体内容，通过 [actions] 注入底部按钮区（可选）。
 */
@Composable
fun NiInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val overlayId = remember { "info_${Uuid.random()}" }
    // 投递到全局玻璃浮层槽位（NiGlassDialog，backdrop 真模糊）
    LaunchedEffect(Unit) {
        NiGlassOverlay.show(
            NiGlassOverlayRequest(
                id = overlayId,
                kind = NiGlassOverlayKind.Dialog,
                title = title,
                onDismiss = onDismiss,
            ) {
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
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { NiGlassOverlay.dismiss(overlayId) }
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

/**
 * 弹窗内容已渲染后，自动聚焦 [focusRequester] 指向的输入框并拉起输入法。
 *
 * **必须放在弹窗的 content 内调用**（靠近目标输入框），而非弹窗组件体调用点：
 * 本应用的对话框/浮层内容由根宿主（[NiGlassOverlayHost]）**延迟渲染**，调用点组合时目标
 * 输入框尚未挂载，请求焦点/键盘会落空。把本组件放进 content，LaunchedEffect 会在该子树
 * 组合完成后执行，此时输入框已挂载、可正确聚焦并唤起 IME。
 */
@Composable
fun NiAutoFocusAndShowKeyboard(focusRequester: FocusRequester) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}
