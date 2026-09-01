package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.GlassSettings
import com.nichx.niplayer.datastore.ThemeSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiScheme
import com.nichx.niplayer.designsystem.theme.NiSchemes
import com.nichx.niplayer.feature.home.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit = {},
) {
    val themeConfig by ThemeSettings.themeFlow.collectAsStateWithLifecycle()
    // 生效深浅色：跟随系统模式下用 isSystemInDarkTheme 实时判断，使预览正确随系统切换
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeConfig.mode) {
        ThemeSettings.Mode.LIGHT -> false
        ThemeSettings.Mode.DARK -> true
        ThemeSettings.Mode.SYSTEM -> systemDark
    }
    // 玻璃不透明度并入主题设置，实时驱动组合预览
    val glassOpacity by GlassSettings.opacityFlow.collectAsStateWithLifecycle()
    val glassTopBarOpacity by GlassSettings.topBarOpacityFlow.collectAsStateWithLifecycle()
    val glassPanelOpacity by GlassSettings.panelOpacityFlow.collectAsStateWithLifecycle()

    // 选中的配色分类：默认定位到当前方案所属分类
    var selectedCategoryRes by remember {
        mutableIntStateOf(themeConfig.scheme.categoryRes)
    }

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

            // ── 主题模式 ──
            SectionLabel(text = stringResource(R.string.theme_mode))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeSettings.Mode.entries.forEach { mode ->
                    ThemeModeCard(
                        mode = mode,
                        isSelected = themeConfig.mode == mode,
                        onClick = { ThemeSettings.setThemeMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── 配色分类选择 ──
            SectionLabel(text = stringResource(R.string.theme_color_scheme))
            CategoryChipRow(
                selected = selectedCategoryRes,
                onSelect = { selectedCategoryRes = it },
            )

            // ── 当前分类方案网格（每行三个）──
            val schemes = NiScheme.entries.filter { it.categoryRes == selectedCategoryRes }
            schemes.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { scheme ->
                        SchemeCard(
                            scheme = scheme,
                            isSelected = themeConfig.scheme == scheme,
                            onClick = { ThemeSettings.setThemeScheme(scheme) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── 当前主题说明 ──
            Text(
                text = stringResource(themeConfig.scheme.descriptionRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NiExtraColors.current.surfaceLevel2)
                    .padding(16.dp),
            )

            // ── 玻璃不透明度（顶栏/导航栏/面板分开调节） ──
            SectionLabel(text = stringResource(R.string.settings_glass_title))
            GlassOpacitySlider(
                label = stringResource(R.string.settings_glass_overlay_opacity),
                value = glassOpacity,
                min = GlassSettings.MIN_OPACITY,
                max = GlassSettings.MAX_OPACITY,
                onValueChange = { GlassSettings.opacity = it },
            )
            GlassOpacitySlider(
                label = stringResource(R.string.settings_glass_top_bar_opacity),
                value = glassTopBarOpacity,
                min = GlassSettings.MIN_OPACITY,
                max = GlassSettings.MAX_OPACITY,
                onValueChange = { GlassSettings.topBarOpacity = it },
            )
            GlassOpacitySlider(
                label = stringResource(R.string.settings_glass_panel_opacity),
                value = glassPanelOpacity,
                min = GlassSettings.MIN_PANEL_OPACITY,
                max = GlassSettings.MAX_PANEL_OPACITY,
                onValueChange = { GlassSettings.panelOpacity = it },
            )

            Spacer(Modifier.height(4.dp))

            // ── 组合预览：主题配色 + 玻璃不透明度（置于底部，紧跟滑条便于实时查看） ──
            ThemePreviewCard(
                scheme = themeConfig.scheme,
                dark = effectiveDark,
                opacity = glassOpacity,
                topBarOpacity = glassTopBarOpacity,
                panelOpacity = glassPanelOpacity,
            )

            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

/** 单条透明度滑条：标题 + 百分比 + Slider。 */
@Composable
private fun GlassOpacitySlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
        )
    }
}

/** 分区标签。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** 主题模式卡片：图标 + 模式名，选中态高亮。 */
@Composable
private fun ThemeModeCard(
    mode: ThemeSettings.Mode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        NiExtraColors.current.surfaceLevel2
    }
    val content = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .border(
                width = 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = mode.icon(),
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
    }
}

/** 配色方案分类横向 chips。 */
@Composable
private fun CategoryChipRow(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val categories = remember {
        NiScheme.entries.map { it.categoryRes }.distinct()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { categoryRes ->
            val isSelected = categoryRes == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else NiExtraColors.current.surfaceLevel2,
                    )
                    .clickable { onSelect(categoryRes) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(categoryRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 配色方案卡片（用于分类网格，每行三个）。 */
@Composable
private fun SchemeCard(
    scheme: NiScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = remember(scheme) {
        val cs = NiSchemes.buildLight(scheme)
        listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(NiExtraColors.current.surfaceLevel1)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else NiExtraColors.current.outlineSoft.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(7.dp),
    ) {
        // 封面：组合色渐变 + 主色圆点
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(pal[0], pal[1], pal[2]))),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .background(pal[0]),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 三色圆点组
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(pal[0], pal[1], pal[2]).forEach { c ->
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(c))
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(scheme.labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 底部氛围条：容器色底 + 三等分主/次/三细条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(pal[3]),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                listOf(1f to pal[0], 1f to pal[1], 1f to pal[2]).forEach { (_, c) ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(c.copy(alpha = 0.75f)))
                }
            }
        }
    }
}

/**
 * 组合预览：用所选方案的配色绘制实际骨架，并按顶栏/面板/导航栏不透明度渲染玻璃层
 * （顶栏模糊、中央对话框、底部导航胶囊），随下方滑条实时联动。
 */
@Composable
private fun ThemePreviewCard(
    scheme: NiScheme,
    dark: Boolean,
    opacity: Float,
    topBarOpacity: Float,
    panelOpacity: Float,
    modifier: Modifier = Modifier,
) {
    val cs = remember(scheme, dark) {
        if (dark) NiSchemes.buildDark(scheme) else NiSchemes.buildLight(scheme)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.theme_preview),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(scheme.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
            )
        }

        // ── 软件实际骨架（主题配色 + 玻璃不透明度） ──
        AppSkeletonPreview(
            cs = cs,
            glassTopBarOpacity = topBarOpacity,
            glassPanelOpacity = panelOpacity,
            glassNavOpacity = opacity,
        )

        // 调色条：主 / 次 / 三 / 容器
        PreviewStrip(listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer))
    }
}

/** 四段渐变色调条：从左到右 主→次→三→容器。 */
@Composable
private fun PreviewStrip(colors: List<Color>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.horizontalGradient(colors)),
    ) {
        colors.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colors[index].copy(alpha = 0.55f),
                                colors[index],
                            ),
                        ),
                    ),
            )
        }
    }
}

private fun ThemeSettings.Mode.icon(): ImageVector = when (this) {
    ThemeSettings.Mode.LIGHT -> Icons.Filled.LightMode
    ThemeSettings.Mode.DARK -> Icons.Filled.DarkMode
    ThemeSettings.Mode.SYSTEM -> Icons.Filled.BrightnessAuto
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
    NiScheme.MISTY -> R.string.theme_scheme_misty_desc
    NiScheme.BLUEBERRY -> R.string.theme_scheme_blueberry_desc
    NiScheme.DENIM -> R.string.theme_scheme_denim_desc
    NiScheme.ROSE_DUST -> R.string.theme_scheme_rose_dust_desc
    NiScheme.STRAWBERRY -> R.string.theme_scheme_strawberry_desc
    NiScheme.CORAL -> R.string.theme_scheme_coral_desc
    NiScheme.FOREST -> R.string.theme_scheme_forest_desc
    NiScheme.MATCHA -> R.string.theme_scheme_matcha_desc
    NiScheme.CARAMEL -> R.string.theme_scheme_caramel_desc
    NiScheme.MINT_MACARON -> R.string.theme_scheme_mint_macaron_desc
    NiScheme.SAKURA_MACARON -> R.string.theme_scheme_sakura_macaron_desc
    NiScheme.LAVENDER_MACARON -> R.string.theme_scheme_lavender_macaron_desc
    NiScheme.ALMOND -> R.string.theme_scheme_almond_desc
    NiScheme.MAUVE -> R.string.theme_scheme_mauve_desc
    NiScheme.SAGE -> R.string.theme_scheme_sage_desc
    NiScheme.SPEARMINT -> R.string.theme_scheme_spearmint_desc
    NiScheme.BUBBLEGUM -> R.string.theme_scheme_bubblegum_desc
    NiScheme.SUMMER_SODA -> R.string.theme_scheme_summer_soda_desc
}