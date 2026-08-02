package com.nichx.niplayer.feature.player

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.datastore.SubtitleSettings

/**
 * 字幕样式设置二级 Dialog（集成在播放器字幕管理 Dialog 内）。
 *
 * 包含 7 项样式：字体、字号、文字颜色、描边宽度、描边颜色、底部边距、应用内嵌样式。
 * 每项修改后立即写入 [SubtitleSettings] 并触发 [onStyleChanged] 回调，
 * 由 [PlayerViewModel.refreshSubtitleStyle] 让 [SubtitleEngine] 应用新值；
 * 外挂字幕的 fontFamily/bottomPadding/fontColor 走 Compose 重组路径自动生效。
 *
 * 设计动机：字幕样式在播放时才能直观看到效果，集成到播放器内比独立设置页更符合使用场景。
 * 设置页仅保留全局项（自动加载同名字幕、优先级、ASSRT Token）。
 *
 * @param onStyleChanged 任意样式修改后的回调（调用 viewModel.refreshSubtitleStyle）
 * @param onDismiss 关闭 Dialog
 */
@Composable
fun SubtitleStyleDialog(
    onStyleChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    // 本地 state（与 SubtitleSettings 同步）：用户修改后立即写回 MMKV 并触发 onStyleChanged
    var fontFamilyKey by remember { mutableStateOf(SubtitleSettings.fontFamilyKey) }
    var textSizeFraction by remember { mutableStateOf(SubtitleSettings.textSizeFraction) }
    var fontColor by remember { mutableStateOf(SubtitleSettings.fontColor) }
    var outlineWidth by remember { mutableStateOf(SubtitleSettings.outlineWidth) }
    var outlineColor by remember { mutableStateOf(SubtitleSettings.outlineColor) }
    var bottomPaddingDp by remember { mutableStateOf(SubtitleSettings.bottomPaddingDp) }
    var applyEmbeddedStyles by remember { mutableStateOf(SubtitleSettings.applyEmbeddedStyles) }

    // 子 Dialog 显示状态
    var showFontFamilyPicker by remember { mutableStateOf(false) }
    var showTextSizePicker by remember { mutableStateOf(false) }
    var showFontColorPicker by remember { mutableStateOf(false) }
    var showOutlineWidthPicker by remember { mutableStateOf(false) }
    var showOutlineColorPicker by remember { mutableStateOf(false) }
    var showBottomPaddingPicker by remember { mutableStateOf(false) }

    val applyAndNotify: () -> Unit = {
        onStyleChanged()
    }

    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, maxHeight = 600) {
        Text(
            text = "字幕样式",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = PlayerDialogColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerDialogDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            StyleClickRow(
                label = "字体",
                value = SubtitleSettings.FONT_FAMILY_OPTIONS.find { it.second == fontFamilyKey }?.first ?: "默认",
                onClick = { showFontFamilyPicker = true },
            )
            StyleClickRow(
                label = "字号",
                value = SubtitleSettings.TEXT_SIZE_OPTIONS.find { it.second == textSizeFraction }?.first ?: "中",
                onClick = { showTextSizePicker = true },
            )
            StyleClickRow(
                label = "文字颜色",
                value = SubtitleSettings.FONT_COLOR_OPTIONS.find { it.second == fontColor }?.first ?: "白色",
                colorDot = fontColor,
                onClick = { showFontColorPicker = true },
            )
            StyleClickRow(
                label = "描边宽度",
                value = SubtitleSettings.OUTLINE_WIDTH_OPTIONS.find { it.second == outlineWidth }?.first ?: "中",
                onClick = { showOutlineWidthPicker = true },
            )
            StyleClickRow(
                label = "描边颜色",
                value = SubtitleSettings.OUTLINE_COLOR_OPTIONS.find { it.second == outlineColor }?.first ?: "黑色",
                colorDot = outlineColor,
                onClick = { showOutlineColorPicker = true },
            )
            StyleClickRow(
                label = "底部边距",
                value = SubtitleSettings.BOTTOM_PADDING_OPTIONS.find { it.second == bottomPaddingDp }?.first ?: "中",
                onClick = { showBottomPaddingPicker = true },
            )
            StyleSwitchRow(
                label = "应用内嵌样式",
                description = "使用字幕文件自带的颜色和样式",
                checked = applyEmbeddedStyles,
                onCheckedChange = {
                    applyEmbeddedStyles = it
                    SubtitleSettings.applyEmbeddedStyles = it
                    applyAndNotify()
                },
            )
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text("完成")
        }
    }

    // ===== 子 Dialog：单选列表 =====
    if (showFontFamilyPicker) {
        SingleSelectDialog(
            title = "字体",
            options = SubtitleSettings.FONT_FAMILY_OPTIONS,
            selected = fontFamilyKey,
            onSelect = {
                fontFamilyKey = it
                SubtitleSettings.fontFamilyKey = it
                applyAndNotify()
                showFontFamilyPicker = false
            },
            onDismiss = { showFontFamilyPicker = false },
        )
    }
    if (showTextSizePicker) {
        SingleSelectDialog(
            title = "字号",
            options = SubtitleSettings.TEXT_SIZE_OPTIONS,
            selected = textSizeFraction,
            onSelect = {
                textSizeFraction = it
                SubtitleSettings.textSizeFraction = it
                applyAndNotify()
                showTextSizePicker = false
            },
            onDismiss = { showTextSizePicker = false },
        )
    }
    if (showOutlineWidthPicker) {
        SingleSelectDialog(
            title = "描边宽度",
            options = SubtitleSettings.OUTLINE_WIDTH_OPTIONS,
            selected = outlineWidth,
            onSelect = {
                outlineWidth = it
                SubtitleSettings.outlineWidth = it
                applyAndNotify()
                showOutlineWidthPicker = false
            },
            onDismiss = { showOutlineWidthPicker = false },
        )
    }
    if (showBottomPaddingPicker) {
        SingleSelectDialog(
            title = "底部边距",
            options = SubtitleSettings.BOTTOM_PADDING_OPTIONS,
            selected = bottomPaddingDp,
            onSelect = {
                bottomPaddingDp = it
                SubtitleSettings.bottomPaddingDp = it
                applyAndNotify()
                showBottomPaddingPicker = false
            },
            onDismiss = { showBottomPaddingPicker = false },
        )
    }
    // ===== 子 Dialog：色板 =====
    if (showFontColorPicker) {
        ColorPickerDialog(
            title = "文字颜色",
            options = SubtitleSettings.FONT_COLOR_OPTIONS,
            selected = fontColor,
            onSelect = {
                fontColor = it
                SubtitleSettings.fontColor = it
                applyAndNotify()
                showFontColorPicker = false
            },
            onDismiss = { showFontColorPicker = false },
        )
    }
    if (showOutlineColorPicker) {
        ColorPickerDialog(
            title = "描边颜色",
            options = SubtitleSettings.OUTLINE_COLOR_OPTIONS,
            selected = outlineColor,
            onSelect = {
                outlineColor = it
                SubtitleSettings.outlineColor = it
                applyAndNotify()
                showOutlineColorPicker = false
            },
            onDismiss = { showOutlineColorPicker = false },
        )
    }
}

/** 单选列表 Dialog（字体/字号/描边宽度/底部边距通用）。 */
@Composable
private fun <T> SingleSelectDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320) {
        PlayerDialogTitle(text = title)
        PlayerDialogDivider()
        Spacer(Modifier.height(4.dp))
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp)
                    .background(
                        color = if (isSelected) PlayerDialogColors.selectedBg else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = label,
                    color = if (isSelected) primary else PlayerDialogColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 色板选择 Dialog（文字颜色/描边颜色通用）。 */
@Composable
private fun ColorPickerDialog(
    title: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, scrollable = false) {
        PlayerDialogTitle(text = title)
        PlayerDialogDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { (_, color) ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable { onSelect(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (color == selected) {
                        val isDarkBg = color == 0xFF000000.toInt() || color == 0xFF424242.toInt()
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (isDarkBg) Color.White else Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleClickRow(
    label: String,
    value: String,
    colorDot: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PlayerDialogColors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (colorDot != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(colorDot)),
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = value,
            color = PlayerDialogColors.textSecondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun StyleSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = PlayerDialogColors.textPrimary, fontSize = 15.sp)
            description?.let {
                Text(
                    text = it,
                    color = PlayerDialogColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
