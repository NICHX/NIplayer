package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiScheme
import com.nichx.niplayer.designsystem.theme.NiSchemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit = {},
) {
    val themeConfig by ThemeSettings.themeFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            NiTopBar(
                title = "主题",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NiExtraColors.current.surfaceLevel2).padding(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeSettings.Mode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeConfig.mode == mode,
                            onClick = { ThemeSettings.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeSettings.Mode.entries.size),
                        ) {
                            Text(mode.label(), maxLines = 1)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "配色方案",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NiExtraColors.current.surfaceLevel2),
            ) {
                Column {
                    NiScheme.entries.groupBy { it.category }.forEach { (category, schemes) ->
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            )
                        }
                        schemes.forEach { scheme ->
                            SchemeOption(
                                scheme = scheme,
                                isSelected = themeConfig.scheme == scheme,
                                onClick = { ThemeSettings.setThemeScheme(scheme) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NiExtraColors.current.surfaceLevel2).padding(16.dp),
            ) {
                Text(
                    text = themeConfig.scheme.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun SchemeOption(
    scheme: NiScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val schemeColors = remember(scheme) {
        val cs = NiSchemes.buildLight(scheme)
        listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            schemeColors.forEach { color ->
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = scheme.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun ThemeSettings.Mode.label(): String = when (this) {
    ThemeSettings.Mode.LIGHT -> "浅色"
    ThemeSettings.Mode.DARK -> "暗色"
    ThemeSettings.Mode.SYSTEM -> "跟随系统"
}

private fun ThemeSettings.Mode.description(): String = when (this) {
    ThemeSettings.Mode.LIGHT -> "始终使用浅色主题。"
    ThemeSettings.Mode.DARK -> "始终使用暗色主题。"
    ThemeSettings.Mode.SYSTEM -> "跟随系统设置自动切换深浅。"
}

private fun NiScheme.description(): String = when (this) {
    NiScheme.BLUE -> "经典蓝色主色调，干净明快。"
    NiScheme.INDIGO -> "靛蓝主色调，沉稳专业。"
    NiScheme.CYAN -> "青色主色调，科技感十足。"
    NiScheme.SLATE -> "石板灰主色调，极简冷静。"
    NiScheme.PURPLE -> "优雅紫色主色调，富有创意感。"
    NiScheme.ROSE -> "玫瑰红主色调，浪漫复古。"
    NiScheme.CORAL -> "珊瑚橙主色调，温暖元气。"
    NiScheme.PINK -> "粉红主色调，温柔甜美。"
    NiScheme.TEAL -> "青绿主色调，清爽自然。"
    NiScheme.GREEN -> "清新绿色主色调，护眼舒适。"
    NiScheme.FOREST -> "森林绿主色调，沉静深邃。"
    NiScheme.CARAMEL -> "焦糖棕主色调，温暖质朴。"
}

private fun NiScheme.swatchColor(): Color = when (this) {
    NiScheme.BLUE -> Color(0xFF1976D2)
    NiScheme.INDIGO -> Color(0xFF303F9F)
    NiScheme.CYAN -> Color(0xFF0097A7)
    NiScheme.SLATE -> Color(0xFF607D8B)
    NiScheme.PURPLE -> Color(0xFF7B1FA2)
    NiScheme.ROSE -> Color(0xFFE91E63)
    NiScheme.CORAL -> Color(0xFFFF7043)
    NiScheme.PINK -> Color(0xFFC2185B)
    NiScheme.TEAL -> Color(0xFF00796B)
    NiScheme.GREEN -> Color(0xFF388E3C)
    NiScheme.FOREST -> Color(0xFF2E7D32)
    NiScheme.CARAMEL -> Color(0xFF6D4C41)
}
