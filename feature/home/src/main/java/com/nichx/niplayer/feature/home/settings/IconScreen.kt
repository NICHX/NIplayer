package com.nichx.niplayer.feature.home.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.IconSettings
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.R

private val IconShape = RoundedCornerShape(24.dp)

/**
 * 应用图标设置页：顶部展示当前图标，下方为 8 款预设缩略图，
 * 点击即时切换桌面图标（activity-alias 运行时切换）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconScreen(onBack: () -> Unit = {}) {
    val current by IconSettings.iconFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.app_icon_title),
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))

            // ── 当前图标预览 ──
            CurrentIconCard(icon = current)

            // ── 预设标题 ──
            Text(
                text = stringResource(R.string.app_icon_presets),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )

            // ── 预设网格（每行四个）──
            IconSettings.AppIcon.entries.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { icon ->
                        IconOptionCard(
                            icon = icon,
                            isSelected = current == icon,
                            onClick = { IconSettings.setIcon(context, icon) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

/** 顶部"当前图标"预览卡：大图 + 名称 + 即时生效提示。 */
@Composable
private fun CurrentIconCard(icon: IconSettings.AppIcon) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconPreview(
            icon = icon,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column {
            Text(
                text = stringResource(R.string.app_icon_current),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = stringResource(icon.labelRes()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_icon_swit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 单个圆角方片图标预览：统一圆角 + Crop 铺满，保证各来源图标同形同尺寸。 */
@Composable
private fun IconPreview(
    icon: IconSettings.AppIcon,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(IconShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        NiExtraColors.current.surfaceLevel1,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon.previewRes()),
            contentDescription = stringResource(icon.labelRes()),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 单款选项卡：方形预览 + 名称，选中时放大、描边并显示右上角徽标。 */
@Composable
private fun IconOptionCard(
    icon: IconSettings.AppIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "iconScale",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            IconPreview(
                icon = icon,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                // 选中描边（绘制在预览之上）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(IconShape)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = IconShape,
                        ),
                )
                // 右上角选中徽标
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(icon.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun IconSettings.AppIcon.previewRes(): Int = when (this) {
    IconSettings.AppIcon.DEFAULT -> R.mipmap.ic_variant_default
    IconSettings.AppIcon.CREAM -> R.mipmap.ic_variant_cream
    IconSettings.AppIcon.SAGE -> R.mipmap.ic_variant_sage
    IconSettings.AppIcon.STEEL -> R.mipmap.ic_variant_steel
    IconSettings.AppIcon.TEXT -> R.mipmap.ic_variant_text
    IconSettings.AppIcon.TEXT2 -> R.mipmap.ic_variant_text2
    IconSettings.AppIcon.PLAY -> R.mipmap.ic_variant_play
    IconSettings.AppIcon.EMOJI -> R.mipmap.ic_variant_emoji
}

private fun IconSettings.AppIcon.labelRes(): Int = when (this) {
    IconSettings.AppIcon.DEFAULT -> R.string.app_icon_default
    IconSettings.AppIcon.CREAM -> R.string.app_icon_cream
    IconSettings.AppIcon.SAGE -> R.string.app_icon_sage
    IconSettings.AppIcon.STEEL -> R.string.app_icon_steel
    IconSettings.AppIcon.TEXT -> R.string.app_icon_text
    IconSettings.AppIcon.TEXT2 -> R.string.app_icon_text2
    IconSettings.AppIcon.PLAY -> R.string.app_icon_play
    IconSettings.AppIcon.EMOJI -> R.string.app_icon_emoji
}