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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiScheme
import com.nichx.niplayer.designsystem.theme.NiSchemes
import com.nichx.niplayer.feature.home.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit = {},
) {
    val themeConfig by ThemeSettings.themeFlow.collectAsStateWithLifecycle()

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.theme_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Text(
                text = stringResource(R.string.theme_mode),
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
                            Text(stringResource(mode.labelRes()), maxLines = 1)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.theme_color_scheme),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NiExtraColors.current.surfaceLevel2),
            ) {
                Column {
                    NiScheme.entries.groupBy { it.categoryRes }.forEach { (category, schemes) ->
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(category),
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
                    text = stringResource(themeConfig.scheme.descriptionRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
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
            text = stringResource(scheme.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun ThemeSettings.Mode.labelRes(): Int = when (this) {
    ThemeSettings.Mode.LIGHT -> R.string.theme_mode_light
    ThemeSettings.Mode.DARK -> R.string.theme_mode_dark
    ThemeSettings.Mode.SYSTEM -> R.string.theme_mode_system
}

private fun ThemeSettings.Mode.descriptionRes(): Int = when (this) {
    ThemeSettings.Mode.LIGHT -> R.string.theme_mode_light_desc
    ThemeSettings.Mode.DARK -> R.string.theme_mode_dark_desc
    ThemeSettings.Mode.SYSTEM -> R.string.theme_mode_system_desc
}

private fun NiScheme.descriptionRes(): Int = when (this) {
    NiScheme.BLUE -> R.string.theme_scheme_blue_desc
    NiScheme.INDIGO -> R.string.theme_scheme_indigo_desc
    NiScheme.CYAN -> R.string.theme_scheme_cyan_desc
    NiScheme.SLATE -> R.string.theme_scheme_slate_desc
    NiScheme.PURPLE -> R.string.theme_scheme_purple_desc
    NiScheme.ROSE -> R.string.theme_scheme_rose_desc
    NiScheme.CORAL -> R.string.theme_scheme_coral_desc
    NiScheme.PINK -> R.string.theme_scheme_pink_desc
    NiScheme.TEAL -> R.string.theme_scheme_teal_desc
    NiScheme.GREEN -> R.string.theme_scheme_green_desc
    NiScheme.FOREST -> R.string.theme_scheme_forest_desc
    NiScheme.CARAMEL -> R.string.theme_scheme_caramel_desc
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
