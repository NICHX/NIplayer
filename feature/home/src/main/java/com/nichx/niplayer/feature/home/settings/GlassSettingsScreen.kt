package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.GlassSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
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