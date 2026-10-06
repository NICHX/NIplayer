package com.nichx.niplayer.feature.home.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
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

/**
 * 视图菜单内的「列数」入口行：显示当前值，点击进入下一级列数选择子页。
 *
 * 列数选项较多，平铺在主菜单里会把浮层撑宽（浮层宽度取最宽项），故下沉到子页。
 */
@Composable
internal fun ColumnsEntryMenuItem(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        modifier = Modifier.height(38.dp),
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

/** 列数选择子页限宽：胶囊按行自动换行，避免撑宽浮层。 */
private val ColumnsPageWidth = 220.dp

/**
 * 下一级「列数」选择子页：顶部「‹ 返回 + 标题」，下方全部列数选项自动换行胶囊。
 *
 * 把列数从视图主菜单下沉，主菜单保持紧凑；此处空间充裕，可直接点选全部列数，
 * 比步进器逐个切换更高效。点击选项即时生效且停留本页，点返回回到主菜单。
 */
@Composable
internal fun ColumnsPickerPage(
    title: String,
    backContentDescription: String,
    autoLabel: String,
    current: Int,
    maxColumns: Int,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val options = remember(maxColumns) {
        buildList {
            add(FileBrowserSettings.GRID_COLUMNS_AUTO)
            for (count in 2..maxColumns) add(count)
        }
    }
    Column(modifier = Modifier.width(ColumnsPageWidth)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                )
                .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = backContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 4,
        ) {
            options.forEach { value ->
                val selected = value == current
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (value == FileBrowserSettings.GRID_COLUMNS_AUTO) autoLabel else value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 菜单内的画廊布局选择行：左侧标签，右侧为「方形 / 瀑布流」胶囊。
 *
 * 点击胶囊即时生效但**不关闭菜单**，便于对比切换；选中项以主色高亮。
 */
@Composable
internal fun GalleryLayoutMenuItem(
    label: String,
    squareLabel: String,
    waterfallLabel: String,
    current: FileBrowserSettings.GalleryLayout,
    onSelect: (FileBrowserSettings.GalleryLayout) -> Unit,
) {
    val options = listOf(
        FileBrowserSettings.GalleryLayout.SQUARE to squareLabel,
        FileBrowserSettings.GalleryLayout.WATERFALL to waterfallLabel,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, text) ->
                val selected = value == current
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
