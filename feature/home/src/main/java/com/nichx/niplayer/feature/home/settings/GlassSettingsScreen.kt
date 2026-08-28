package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.GlassSettings
import com.nichx.niplayer.designsystem.components.NiGlassHairWidth
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.components.niGlassBorderColor
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.R
import kotlin.math.roundToInt

/**
 * 玻璃不透明度设置页。
 *
 * 两类浮层的不透明度**分开设置**：
 * - 薄浮层（底部导航栏 / 顶栏 / 多选操作栏等）：整体玻璃感，偏透；
 * - 面板（对话框 / 菜单等）：偏实以保证内容可读。
 *
 * 改动即时写入 [GlassSettings]，根布局分别经
 * [com.nichx.niplayer.designsystem.components.LocalNiGlassOpacity] 与
 * [com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity] 实时下发。
 */
@Composable
fun GlassSettingsScreen(onBack: () -> Unit) {
    val opacity by GlassSettings.opacityFlow.collectAsStateWithLifecycle()
    val panelOpacity by GlassSettings.panelOpacityFlow.collectAsStateWithLifecycle()

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.settings_glass_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NiStyleIcon(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            style = NiAppIconStyle,
                            containerSize = 40.dp,
                            iconSize = 22.dp,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_glass_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 实时玻璃预览：随两个不透明度滑条实时变化（面板 + 导航栏）
            GlassPreviewCard(opacity = opacity, panelOpacity = panelOpacity)

            // ─── 薄浮层（导航栏/顶栏等） ───
            GlassOpacitySlider(
                label = stringResource(R.string.settings_glass_overlay_opacity),
                value = opacity,
                min = GlassSettings.MIN_OPACITY,
                max = GlassSettings.MAX_OPACITY,
                onValueChange = { GlassSettings.opacity = it },
            )

            // ─── 面板（对话框/菜单） ───
            GlassOpacitySlider(
                label = stringResource(R.string.settings_glass_panel_opacity),
                value = panelOpacity,
                min = GlassSettings.MIN_PANEL_OPACITY,
                max = GlassSettings.MAX_PANEL_OPACITY,
                onValueChange = { GlassSettings.panelOpacity = it },
            )
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

/**
 * 玻璃实时预览：深色柔和渐变背景 + 模拟内容块，上叠「玻璃面板」与「玻璃导航栏」两个示例，
 * 透明度分别跟随面板 [panelOpacity] 与薄浮层 [opacity]，由两个滑条实时驱动。
 */
@Composable
private fun GlassPreviewCard(opacity: Float, panelOpacity: Float) {
    val isDark = NiExtraColors.current.isDark
    // 背景渐变随主题：深色主题用深蓝紫渐变，浅色主题用柔和浅色渐变
    val bgColors = if (isDark) {
        listOf(Color(0xFF23213D), Color(0xFF3D2A63), Color(0xFF1D2A4F))
    } else {
        listOf(Color(0xFFDDCFF6), Color(0xFFC4EAF0), Color(0xFFF6DACD))
    }
    // 模拟内容块颜色：深色主题偏白、浅色主题偏深灰，保证两种主题下都有层次
    val contentColor = if (isDark) Color.White else Color(0xFF3A3A4A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(bgColors)),
    ) {
        // ─── 背景模拟内容（让玻璃面板透出有层次） ───
        // 左上卡片块
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .width(72.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(contentColor.copy(alpha = 0.10f)),
        )
        // 右上文字行
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.12f)),
                )
            }
        }
        // 中部小圆点（模拟内容）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 26.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.15f)),
        )

        // ─── 上部：玻璃面板（面板不透明度） ───
        val panelShape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 46.dp)
                .fillMaxWidth(0.78f)
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = panelOpacity))
                .border(BorderStroke(NiGlassHairWidth, niGlassBorderColor()), panelShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "N",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "玻璃面板预览",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "不透明度 ${(panelOpacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        // ─── 下部：玻璃导航栏（薄浮层不透明度） ───
        val navShape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(0.9f)
                .clip(navShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity))
                .border(BorderStroke(NiGlassHairWidth, niGlassBorderColor()), navShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("首页", "媒体库", "设置").forEachIndexed { index, label ->
                val selected = index == 0
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else Color.Transparent,
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            ),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            }
            Text(
                text = "${(opacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}