package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



// NI_ACCENT / NI_ACCENT_DARK 已移除，统一使用 MaterialTheme.colorScheme.primary

@Composable
internal fun MoreMenuDialog(
    onDismiss: () -> Unit,
    /** 更多菜单中的动作列表（已按用户自定义过滤 + 排序，且与 HUD 配置同步）。 */
    actions: List<MoreAction>,
) {
    val onSurface = PlayerDialogColors.textPrimary
    // scrollable = true：把所有功能放进「更多」时项很多，容器在限高内滚动，避免内容超出窗口被裁切。
    PlayerDialog(onDismiss = onDismiss, maxWidth = 340, scrollable = true) {
        Text(
            text = stringResource(R.string.player_more),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()
        Spacer(Modifier.height(4.dp))
        actions.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 3.dp),
            ) {
                // 固定 3 等份槽位，保证各行的图标/项目落在同一列，末行项数不足也不错位
                repeat(3) { i ->
                    val action = row.getOrNull(i)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (action != null) {
                            MoreMenuItem(
                                icon = action.icon,
                                label = action.label,
                                enabled = action.enabled,
                                onClick = action.onClick,
                                isActive = action.isActive,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class MoreAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isActive: Boolean = false,
)

@Composable
internal fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = PlayerDialogColors.textSecondary
    val tint = when {
        isActive -> primary
        enabled -> primary
        else -> onSurfaceVariant.copy(alpha = 0.38f)
    }
    val bg = when {
        isActive -> primary.copy(alpha = 0.28f)
        enabled -> primary.copy(alpha = 0.10f)
        else -> PlayerDialogColors.divider
    }
    val textColor = when {
        isActive -> primary
        enabled -> PlayerDialogColors.textPrimary
        else -> onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isActive) Modifier.border(
                    1.5.dp,
                    primary.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp),
                ) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .width(72.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MediaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = PlayerDialogColors.textSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = PlayerDialogColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SpeedMenuDialog(
    speedIndex: Int,
    onSelectPreset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = PlayerDialogColors.textPrimary
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, scrollable = false) {
        Text(
            text = stringResource(R.string.player_speed_menu_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()
        Spacer(Modifier.height(8.dp))
        SPEED_LABELS.chunked(4).forEachIndexed { rowIdx, rowItems ->
            val baseIndex = rowIdx * 4
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEachIndexed { colIdx, label ->
                    val index = baseIndex + colIdx
                    val selected = index == speedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .clickable { onSelectPreset(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) primary else onSurface,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

