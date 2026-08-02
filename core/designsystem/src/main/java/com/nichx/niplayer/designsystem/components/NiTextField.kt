package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * NIplayer 统一样式输入框。
 *
 * 统一所有页面/对话框的文本输入外观，避免各页面各自拼 `OutlinedTextField` 导致的
 * 圆角、边框、容器色不一致：
 * - 12dp 圆角（与卡片、下拉一致）
 * - 透明容器（融入对话框 / 卡片表面）
 * - 聚焦时品牌色边框与标签，未聚焦时 `outlineVariant` 细边框
 * - 紧凑行高，信息密度更高
 *
 * 特殊场景（如播放器暗色底部弹层）可通过 [colors] 覆盖整套配色。
 */
@Composable
fun NiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    colors: TextFieldColors = NiTextFieldDefaults.colors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier,
        label = label?.let { text -> { Text(text = text) } },
        placeholder = placeholder?.let { text -> { Text(text = text) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        readOnly = readOnly,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = NiTextFieldDefaults.Shape,
        colors = colors,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 0.9f,
        ),
    )
}

object NiTextFieldDefaults {

    /** 输入框统一圆角：与卡片、下拉菜单一致 */
    val Shape: Shape = RoundedCornerShape(12.dp)

    /**
     * 默认配色：透明容器、品牌色聚焦态、`outlineVariant` 未聚焦边框。
     * 如需覆盖（如播放器暗色弹层），可传入部分参数并复用默认值。
     */
    @Composable
    fun colors(
        focusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor: Color = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor: Color = Color.Transparent,
        unfocusedContainerColor: Color = Color.Transparent,
        focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor: Color = MaterialTheme.colorScheme.outlineVariant,
        focusedLabelColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor: Color = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        unfocusedPlaceholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        focusedLeadingIconColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor: Color = MaterialTheme.colorScheme.primary,
        unfocusedTrailingIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = focusedTextColor,
        unfocusedTextColor = unfocusedTextColor,
        focusedContainerColor = focusedContainerColor,
        unfocusedContainerColor = unfocusedContainerColor,
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = unfocusedBorderColor,
        focusedLabelColor = focusedLabelColor,
        unfocusedLabelColor = unfocusedLabelColor,
        cursorColor = cursorColor,
        focusedPlaceholderColor = focusedPlaceholderColor,
        unfocusedPlaceholderColor = unfocusedPlaceholderColor,
        focusedLeadingIconColor = focusedLeadingIconColor,
        unfocusedLeadingIconColor = unfocusedLeadingIconColor,
        focusedTrailingIconColor = focusedTrailingIconColor,
        unfocusedTrailingIconColor = unfocusedTrailingIconColor,
    )
}
