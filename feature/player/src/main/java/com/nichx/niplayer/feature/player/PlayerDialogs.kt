package com.nichx.niplayer.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nichx.niplayer.designsystem.components.NiDialogItem

/**
 * 播放器内 Dialog 统一样式定义。
 *
 * 设计目标（解决用户反馈的三个问题）：
 * 1. **占全高不好看** → 所有 Dialog 统一 `heightIn(max = 580.dp)`，超出可滚动
 * 2. **整体样式不好看** → 统一 28dp 圆角、16dp 阴影、0.5dp 细边框、统一宽度策略与 padding
 * 3. **高亮度刺眼** → 暗色玻璃拟态配色（视频播放器场景：播放始终是暗色画面，暗色弹窗不刺眼且协调）
 *
 * **重要：本组强制暗色弹窗仅用于视频播放器场景**（[PlayerScreen] 全屏暗色画面）。
 * 音频播放器等浅色场景请使用 core/designsystem 的主题自适应弹窗组件
 * （[com.nichx.niplayer.designsystem.components.NiInfoDialog]、
 * [com.nichx.niplayer.designsystem.components.NiConfirmDialog]、
 * [com.nichx.niplayer.designsystem.components.NiListItemDialog]），
 * 避免浅色模式下出现黑色弹窗。
 *
 * 暗色调色板（固定暗色，不依赖 MaterialTheme.colorScheme.surface）：
 * - 背景：`0xFF1C1C1E` alpha=0.92（iOS 风格深灰半透明，让视频隐约透出降低割裂感）
 * - 文字主色：`0xFFE8E8EA`（柔和白，非纯白降低刺眼度）
 * - 文字次色：`0xFF9E9EA2`
 * - 边框：`0xFFFFFFFF` alpha=0.08（浅色细边框增加层次）
 * - 分隔线：`0xFFFFFFFF` alpha=0.08
 * - 选中高亮：`0xFFFFFFFF` alpha=0.10
 * - 强调色：仍用 MaterialTheme.colorScheme.primary（保留主题色调感）
 */

// ===== 暗色玻璃拟态调色板（播放器专用，固定不跟随系统主题） =====

/** Dialog 背景色：深灰半透明 */
private val PlayerDialogBg = Color(0xFF1C1C1E).copy(alpha = 0.92f)

/** 主文字色：柔和白（非纯白，降低刺眼度） */
private val PlayerTextPrimary = Color(0xFFE8E8EA)

/** 次文字色：中灰 */
private val PlayerTextSecondary = Color(0xFF9E9EA2)

/** 边框色：白色低透明度 */
private val PlayerBorder = Color.White.copy(alpha = 0.06f)

/** 分隔线色 */
private val PlayerDivider = Color.White.copy(alpha = 0.05f)

/** 选中项背景高亮 */
private val PlayerSelectedBg = Color.White.copy(alpha = 0.10f)

/**
 * 播放器统一 Dialog 容器。
 *
 * 强制暗色玻璃拟态背景 + 28dp 圆角 + 高度上限 + 内置滚动。
 * 所有播放器内自定义 Dialog 都应使用此容器以保证视觉统一。
 *
 * @param onDismiss 关闭回调
 * @param modifier 额外修饰符
 * @param maxWidth 最大宽度（默认 360.dp）
 * @param maxHeight 最大高度（默认 580.dp，超出可滚动）
 * @param scrollable 内容是否可滚动（默认 true）
 * @param content 内容
 */
/** 按当前屏幕宽度收缩对话框最大宽度：竖屏下避免 360dp 对话框几乎占满全屏，最多占屏宽 92%。 */
@Composable
fun adaptiveDialogMaxWidth(requestedMaxWidth: Int): Int {
    return minOf(
        requestedMaxWidth,
        (LocalConfiguration.current.screenWidthDp * 0.92f).toInt().coerceAtLeast(280),
    )
}

// ===== 液态玻璃材质（统一弹窗设计语言） =====
// 覆盖在播放画面上的弹窗用「液态玻璃」表达：垂向渐变营造底部更暗的光学聚焦，
// 顶部一条高光细线呈现玻璃边缘反光，配合细描边与大圆角形成统一、有质感的暗色玻璃面板。
// 透明度与主界面玻璃保持一致（约 91% 不透明），让底层画面隐约透出，避免生硬的不透明色块。
private val LiquidGlassTop = Color(0xE82C2C30)
private val LiquidGlassBottom = Color(0xE8141416)
private val LiquidGlassEdgeHighlight = Color.White.copy(alpha = 0.10f)

/**
 * 播放器统一「液态玻璃」弹窗面板。
 *
 * 负责渲染暗色液态玻璃底（垂向渐变 + 顶部高光细线 + 圆角 + 细描边 + 阴影），
 * 圆角裁剪，内容叠在其上。所有播放器弹窗（[PlayerDialog] 及独立的 AB/选集/书签等）
 * 都通过它保证材质统一。
 *
 * @param modifier 额外修饰符
 * @param shape 面板形状（默认 28dp 圆角）
 * @param content 内容
 */
@Composable
fun PlayerDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 16.dp,
        border = BorderStroke(0.5.dp, PlayerBorder),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(LiquidGlassTop, LiquidGlassBottom))),
        ) {
            // 顶部高光细线（玻璃边缘反光）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LiquidGlassEdgeHighlight)
                    .align(Alignment.TopCenter),
            )
            content()
        }
    }
}

@Composable
fun PlayerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Int = 360,
    maxHeight: Int = 580,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 竖屏下按屏幕宽度动态收缩，横屏时仍用 maxWidth 上限
        val effectiveMaxWidth = adaptiveDialogMaxWidth(maxWidth)
        PlayerDialogSurface(
            modifier = modifier
                .widthIn(min = 280.dp, max = effectiveMaxWidth.dp)
                .heightIn(max = maxHeight.dp),
        ) {
            val colModifier = if (scrollable) {
                Modifier.verticalScroll(rememberScrollState())
            } else {
                Modifier
            }
            Column(
                modifier = colModifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

/**
 * 播放器列表选择 Dialog（替代 [com.nichx.niplayer.designsystem.components.NiListItemDialog]）。
 *
 * 标题 + 分隔线 + 列表项。列表项 48dp 行高，选中项高亮 + Check 图标。
 * 列表项超多时自动滚动。
 *
 * @param title 标题
 * @param items 列表项（复用 NiDialogItem data class）
 * @param onDismiss 关闭回调
 */
@Composable
fun PlayerListDialog(
    title: String,
    items: List<NiDialogItem>,
    onDismiss: () -> Unit,
) {
    PlayerDialog(onDismiss = onDismiss, maxHeight = 560) {
        PlayerDialogTitle(text = title)
        HorizontalDivider(
            color = PlayerDivider,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            PlayerItemRow(item)
            if (index < items.size - 1) {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/**
 * 播放器信息展示 Dialog（替代 [com.nichx.niplayer.designsystem.components.NiInfoDialog]）。
 *
 * 标题 + 分隔线 + 自定义内容。内容超长时自动滚动。
 *
 * @param title 标题
 * @param onDismiss 关闭回调
 * @param content 内容
 */
@Composable
fun PlayerInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    PlayerDialog(onDismiss = onDismiss, maxHeight = 600) {
        PlayerDialogTitle(text = title)
        HorizontalDivider(
            color = PlayerDivider,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            content()
        }
    }
}

/**
 * 播放器确认 Dialog（替代 [com.nichx.niplayer.designsystem.components.NiConfirmDialog]）。
 *
 * 标题 + 文本 + 确认/取消按钮。
 */
@Composable
fun PlayerConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "",
    dismissText: String = "",
) {
    // 默认按钮文案从资源解析（Composable 默认参数无法调用 stringResource）
    val resolvedConfirmText = confirmText.ifEmpty { stringResource(R.string.player_confirm) }
    val resolvedDismissText = dismissText.ifEmpty { stringResource(R.string.player_cancel) }
    PlayerDialog(onDismiss = onDismiss, maxWidth = 340, scrollable = false) {
        PlayerDialogTitle(text = title)
        HorizontalDivider(
            color = PlayerDivider,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = PlayerTextPrimary,
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
                Text(resolvedDismissText, color = PlayerTextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) {
                Text(resolvedConfirmText, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 统一标题样式 */
@Composable
fun PlayerDialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        color = PlayerTextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 统一分隔线 */
@Composable
fun PlayerDialogDivider(horizontalPadding: Int = 16) {
    HorizontalDivider(
        color = PlayerDivider,
        modifier = Modifier.padding(horizontal = horizontalPadding.dp),
    )
}

/** 列表项行（暗色风格） */
@Composable
fun PlayerItemRow(item: NiDialogItem) {
    val isSelected = item.isSelected
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = if (isSelected) primaryColor else PlayerTextPrimary
    val iconTint = if (isSelected) primaryColor else (item.iconTint ?: PlayerTextPrimary)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp)
            .background(
                color = if (isSelected) PlayerSelectedBg else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = item.onClick)
            .padding(horizontal = 12.dp),
    ) {
        val icon = item.icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
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
            color = item.labelColor ?: textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 暴露调色板供其他 Dialog 复用（如 SubtitleManageDialog 内的次级文字） */
object PlayerDialogColors {
    val background = PlayerDialogBg
    val textPrimary = PlayerTextPrimary
    val textSecondary = PlayerTextSecondary
    val border = PlayerBorder
    val divider = PlayerDivider
    val selectedBg = PlayerSelectedBg
}
