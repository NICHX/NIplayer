package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.AudioSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors

private data class EqBand(val index: Int, val freqLabel: String)

private val EQ_BANDS = listOf(
    EqBand(0, "60Hz"),
    EqBand(1, "230Hz"),
    EqBand(2, "910Hz"),
    EqBand(3, "3.6k"),
    EqBand(4, "14k"),
)

private const val EQ_MIN_MB = -1500
private const val EQ_MAX_MB = 1500
private const val EQ_RANGE = EQ_MAX_MB - EQ_MIN_MB // 3000

private data class EqPreset(@StringRes val nameRes: Int, val gains: IntArray) {
    override fun equals(other: Any?) = other is EqPreset && nameRes == other.nameRes
    override fun hashCode() = nameRes.hashCode()
}

private val BUILTIN_PRESETS: List<EqPreset> = listOf(
    EqPreset(R.string.equalizer_preset_flat, intArrayOf(0, 0, 0, 0, 0)),
    EqPreset(R.string.equalizer_preset_pop, intArrayOf(-200, 0, 400, 800, 400)),
    EqPreset(R.string.equalizer_preset_rock, intArrayOf(500, 300, -100, 400, 700)),
    EqPreset(R.string.equalizer_preset_jazz, intArrayOf(300, 0, 100, 300, 500)),
    EqPreset(R.string.equalizer_preset_classical, intArrayOf(400, 200, 0, 200, 400)),
    EqPreset(R.string.equalizer_preset_bass, intArrayOf(800, 500, 0, -200, -300)),
    EqPreset(R.string.equalizer_preset_treble, intArrayOf(-200, -100, 0, 500, 800)),
)

@Composable
fun EqualizerSettingsScreen(
    onBack: () -> Unit = {},
    onApplyToPlayer: () -> Unit = {},
    onApplyLiveToPlayer: () -> Unit = {},
) {
    var enabled by remember { mutableStateOf(AudioSettings.equalizerEnabled) }
    var selectedPreset by remember {
        mutableStateOf(matchBuiltinPreset(readCurrentGains()))
    }
    // 用于触发滑块重绘（预设切换后各 band 增益变化，需刷新 UI）
    var refreshKey by remember { mutableStateOf(0) }

    // 开关切换：经淡入淡出包装（效果链不重建，此处为兜底）
    val applyEqualizer: () -> Unit = onApplyToPlayer
    // 滑块 / 预设：实时直通，避免拖动时反复打断音乐
    val applyEqualizerLive: () -> Unit = onApplyLiveToPlayer

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.equalizer_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            // 总开关
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NiExtraColors.current.surfaceLevel2)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.equalizer_enable),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.equalizer_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        AudioSettings.equalizerEnabled = it
                        applyEqualizer()
                    })
                }
            }

            // 预设（横向滚动卡片）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NiExtraColors.current.surfaceLevel2)
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.equalizer_presets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.size(12.dp))
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(BUILTIN_PRESETS) { index, preset ->
                        val isSelected = selectedPreset == index
                        PresetCard(
                            name = stringResource(preset.nameRes),
                            isSelected = isSelected,
                            enabled = enabled,
                            onClick = {
                                selectedPreset = index
                                preset.gains.forEachIndexed { band, gain ->
                                    AudioSettings.setBandLevel(band, gain)
                                }
                                AudioSettings.equalizerPresetIndex = -1
                                refreshKey++ // 触发滑块刷新
                                applyEqualizerLive()
                            },
                        )
                    }
                }
            }

            // 频段调节（纵向滑块）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NiExtraColors.current.surfaceLevel2)
                    .padding(horizontal = 18.dp, vertical = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    EQ_BANDS.forEach { band ->
                        val level = AudioSettings.getBandLevel(band.index)
                        VerticalEqBandSlider(
                            label = band.freqLabel,
                            levelMb = level,
                            enabled = enabled,
                            refreshKey = refreshKey,
                            onLevelChange = { newLevel ->
                                AudioSettings.setBandLevel(band.index, newLevel)
                                if (selectedPreset != -1) selectedPreset = -1
                                applyEqualizerLive()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                Text(
                    text = if (enabled) {
                        stringResource(R.string.equalizer_custom_hint)
                    } else {
                        stringResource(R.string.equalizer_disabled_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

/**
 * 预设卡片。
 *
 * 选中态：primary 色描边 + primary 色淡背景 + 勾号图标
 * 未选中：outlineVariant 淡描边 + 透明背景
 */
@Composable
private fun PresetCard(
    name: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 纵向均衡器频段滑块。
 *
 * 实现：将 Material3 水平 [Slider] 旋转 -90° 得到纵向滑块。
 *
 * 布局：容器 Box(width=36dp, height=200dp) 内放置旋转后的 Slider。
 * 旋转后 Slider 的视觉宽度 = 容器高度（200dp），视觉高度 = Slider 原始高度（~48dp）。
 * 通过 `graphicsLayer { rotationZ = -90f }` 旋转，并用 `offset` 校正位置使其居中。
 *
 * 增益值显示在滑块上方，频率标签显示在下方。
 */
@Composable
private fun VerticalEqBandSlider(
    label: String,
    levelMb: Int,
    enabled: Boolean,
    refreshKey: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 用 refreshKey 强制重置内部状态（预设切换后外部 levelMb 变化需同步到滑块）
    var sliderValue by remember(levelMb, refreshKey) { mutableStateOf(levelMb.toFloat()) }

    // 当前显示值：拖动时用 sliderValue 实时显示，外部变化时跟随 levelMb
    val displayValue = sliderValue

    val sliderHeight = 280.dp
    val sliderWidth = 36.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 当前增益值（dB）—— 实时跟随滑块拖动
        Text(
            text = formatGain(displayValue.toInt()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) {
                when {
                    displayValue > 0 -> MaterialTheme.colorScheme.primary
                    displayValue < 0 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            } else {
                MaterialTheme.colorScheme.outline
            },
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(8.dp))

        // 纵向滑块容器
        Box(
            modifier = Modifier
                .width(sliderWidth)
                .height(sliderHeight),
            contentAlignment = Alignment.Center,
        ) {
            // 0 dB 中线参考线
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // 中线位置：0 dB 在滑块垂直方向的中点
                        // 增益范围 EQ_MIN_MB..EQ_MAX_MB，0 在中间
                        val midY = size.height * (EQ_MAX_MB.toFloat() / EQ_RANGE)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = 1f,
                        )
                    },
            )

            // 旋转的水平 Slider（-90° = 纵向，下小上大）
            // 旋转前布局尺寸：width=sliderHeight(280dp), height=sliderWidth(36dp)
            // 旋转 -90° 后视觉尺寸：width=36dp, height=280dp（纵向轨道）
            // 用 requiredSize 绕过 Box 的 width(36dp) 约束，强制 Slider 以 280x36 布局
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onLevelChange(it.toInt())
                },
                valueRange = EQ_MIN_MB.toFloat()..EQ_MAX_MB.toFloat(),
                enabled = enabled,
                modifier = Modifier
                    .requiredSize(width = sliderHeight, height = sliderWidth)
                    .graphicsLayer { rotationZ = -90f },
            )
        }

        Spacer(Modifier.size(8.dp))

        // 频率标签
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}

/** 将 mB 增益格式化为 dB 字符串。 */
private fun formatGain(levelMb: Int): String {
    val db = levelMb / 100.0
    return when {
        db > 0 -> String.format("+%.1f", db)
        db < 0 -> String.format("%.1f", db)
        else -> "0"
    }
}

private fun readCurrentGains(): IntArray =
    IntArray(EQ_BANDS.size) { AudioSettings.getBandLevel(EQ_BANDS[it].index) }

private fun matchBuiltinPreset(gains: IntArray): Int {
    for ((index, preset) in BUILTIN_PRESETS.withIndex()) {
        if (preset.gains.contentEquals(gains)) return index
    }
    return -1
}
