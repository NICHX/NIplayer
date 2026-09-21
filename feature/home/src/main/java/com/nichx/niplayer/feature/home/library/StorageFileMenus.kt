package com.nichx.niplayer.feature.home.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.FileBrowserSettings


@Composable
internal fun SortByMenuItem(
    label: String,
    icon: ImageVector,
    value: FileBrowserSettings.SortBy,
    current: FileBrowserSettings.SortBy,
    ascending: Boolean,
    onSelect: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    val selected = current == value
    DropdownMenuItem(
        modifier = Modifier.height(38.dp),
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        // 当前排序项显示方向箭头（点击切换升降序），非当前项无箭头（点击仅选中）
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = if (ascending) Icons.Rounded.ArrowUpward
                    else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = { if (selected) onToggleDirection() else onSelect() },
    )
}

/** 菜单内的视图模式项：点击选中并关闭菜单，当前项以主色+半粗体+勾选高亮。 */
@Composable
internal fun ViewModeMenuItem(
    label: String,
    icon: ImageVector,
    value: FileBrowserSettings.ViewMode,
    current: FileBrowserSettings.ViewMode,
    onSelect: () -> Unit,
) {
    val selected = current == value
    DropdownMenuItem(
        modifier = Modifier.height(38.dp),
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onSelect,
    )
}

/** 菜单内的轻量勾选行：点击整行或复选框均可切换，不关闭菜单。 */
@Composable
internal fun SortToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

/** 菜单内的文件类型过滤选项：点击选中并关闭菜单（单选）。 */
@Composable
internal fun FilterMenuItem(
    label: String,
    icon: ImageVector,
    value: FileBrowserSettings.MediaFilter,
    current: FileBrowserSettings.MediaFilter,
    onClick: () -> Unit,
) {
    val selected = current == value
    DropdownMenuItem(
        modifier = Modifier.height(38.dp),
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}
