package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.designsystem.components.NiGlassSwitch
import kotlin.math.roundToInt

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
            text = stringResource(R.string.subtitle_style_title),
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
                label = stringResource(R.string.subtitle_font_label),
                value = stringResource(SubtitleSettings.FONT_FAMILY_OPTIONS.find { it.second == fontFamilyKey }?.first ?: R.string.subtitle_font_default),
                onClick = { showFontFamilyPicker = true },
            )
            StyleClickRow(
                label = stringResource(R.string.subtitle_size_label),
                value = stringResource(SubtitleSettings.TEXT_SIZE_OPTIONS.find { it.second == textSizeFraction }?.first ?: R.string.subtitle_size_medium),
                onClick = { showTextSizePicker = true },
            )
            StyleClickRow(
                label = stringResource(R.string.subtitle_color_label),
                value = stringResource(SubtitleSettings.FONT_COLOR_OPTIONS.find { it.second == fontColor }?.first ?: R.string.subtitle_color_white),
                colorDot = fontColor,
                onClick = { showFontColorPicker = true },
            )
            StyleClickRow(
                label = stringResource(R.string.subtitle_outline_width_label),
                value = stringResource(SubtitleSettings.OUTLINE_WIDTH_OPTIONS.find { it.second == outlineWidth }?.first ?: R.string.subtitle_outline_medium),
                onClick = { showOutlineWidthPicker = true },
            )
            StyleClickRow(
                label = stringResource(R.string.subtitle_outline_color_label),
                value = stringResource(SubtitleSettings.OUTLINE_COLOR_OPTIONS.find { it.second == outlineColor }?.first ?: R.string.subtitle_outline_color_black),
                colorDot = outlineColor,
                onClick = { showOutlineColorPicker = true },
            )
            StyleClickRow(
                label = stringResource(R.string.subtitle_bottom_padding_label),
                // 底部边距用滑条精确控制，直接显示数值（dp）
                value = "${bottomPaddingDp} dp",
                onClick = { showBottomPaddingPicker = true },
            )
            StyleSwitchRow(
                label = stringResource(R.string.subtitle_apply_embedded),
                description = stringResource(R.string.subtitle_apply_embedded_desc),
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
            Text(stringResource(R.string.subtitle_done))
        }
    }

    // ===== 子 Dialog：单选列表 =====
    if (showFontFamilyPicker) {
        SingleSelectDialog(
            title = stringResource(R.string.subtitle_font_label),
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
            title = stringResource(R.string.subtitle_size_label),
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
            title = stringResource(R.string.subtitle_outline_width_label),
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
        BottomPaddingSliderDialog(
            currentValue = bottomPaddingDp,
            onValueChangeFinished = { value ->
                bottomPaddingDp = value
                SubtitleSettings.bottomPaddingDp = value
                applyAndNotify()
                showBottomPaddingPicker = false
            },
            onDismiss = { showBottomPaddingPicker = false },
        )
    }
    // ===== 子 Dialog：色板 =====
    if (showFontColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.subtitle_color_label),
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
            title = stringResource(R.string.subtitle_outline_color_label),
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
    options: List<Pair<Int, T>>,
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
                    text = stringResource(label),
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
    options: List<Pair<Int, Int>>,
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

/** 底部边距滑条 Dialog（精确数值控制，替代固定档位）。 */
@Composable
private fun BottomPaddingSliderDialog(
    currentValue: Int,
    onValueChangeFinished: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 拖动中只更新本地数值预览，点击"完成"时才一次性写回设置并通知，避免拖动期间频繁刷新渲染
    var sliderValue by remember { mutableFloatStateOf(currentValue.toFloat()) }
    PlayerDialog(onDismiss = onDismiss, maxWidth = 320, maxHeight = 240, scrollable = false) {
        PlayerDialogTitle(text = stringResource(R.string.subtitle_bottom_padding_label))
        PlayerDialogDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${sliderValue.roundToInt()} dp",
                color = PlayerDialogColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                // -100~160dp，每 5dp 一档（steps = 区间内等分数 - 1 = 52 - 1）；
                // 正值上移、负值下移（PGS 位图默认偏上时用负值下移）
                valueRange = -100f..160f,
                steps = 51,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Text(
                text = stringResource(R.string.subtitle_bottom_padding_desc),
                color = PlayerDialogColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TextButton(
            onClick = { onValueChangeFinished(sliderValue.roundToInt()) },
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(stringResource(R.string.subtitle_done))
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
            // 带按钮的设置项：整行点击仅委托开关，移除涟漪（反馈由 Switch 承担）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
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
        NiGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
