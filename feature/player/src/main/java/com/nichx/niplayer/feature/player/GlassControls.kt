package com.nichx.niplayer.feature.player

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.nichx.niplayer.designsystem.components.LocalNiBackdrop
import com.nichx.niplayer.designsystem.components.LocalNiGlassEnabled
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/** 播放器玻璃按钮的折射/模糊参数（与 NiGlassBarDefaults 一致，保证全局质感统一）。 */
private object GlassButtonDefaults {
    const val BlurRadius = 8f
    const val LensRadius = 6f
}

/**
 * 播放器玻璃按钮的 backdrop 是否可用：
 * 与 NiGlassOverlayHost 同门槛（玻璃开关 + LocalNiBackdrop 存在 + API 33+）。
 */
@Composable
internal fun isPlayerGlassActive(backdrop: Backdrop?): Boolean =
    backdrop != null && LocalNiGlassEnabled.current &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** 玻璃高光描边（无 backdrop 回退时使用）：顶部亮 → 底部暗，模拟边缘折射。 */
internal fun glassEdgeBrush(): Brush = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.55f),
        Color.White.copy(alpha = 0.14f),
        Color.Black.copy(alpha = 0.06f),
    ),
)

/**
 * 统一的液态玻璃圆形按钮：返回 / 外观切换 / 更多 / 播放控件共用同一质感。
 *
 * - 可用时经 [drawBackdrop] 对页面内容做 vibrancy + blur + lens 折射采样，
 *   顶/底高光与投影复用底栏同款配方；
 * - [emphasized] 用于主播放按钮：表面混入 primary 色保持识别度；
 * - 不可用时回退为半透明 surface 色块 + 高光描边。
 *
 * @param backdrop 玻璃采样源；缺省读 [LocalNiBackdrop]
 */
@Composable
internal fun GlassCircleButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalNiBackdrop.current,
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val isInLightTheme = !NiExtraColors.current.isDark
    val glassActive = isPlayerGlassActive(backdrop)
    val primary = MaterialTheme.colorScheme.primary
    val fallbackSurface = when {
        emphasized -> primary.copy(alpha = 0.22f)
        isInLightTheme -> Color.White.copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    // 按压回缩反馈（不改变布局边界）
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(140),
        label = "glassButtonScale",
    )

    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (glassActive) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop!!,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(GlassButtonDefaults.BlurRadius.dp.toPx())
                            lens(
                                GlassButtonDefaults.LensRadius.dp.toPx(),
                                GlassButtonDefaults.LensRadius.dp.toPx(),
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f),
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                when {
                                    emphasized -> primary.copy(alpha = 0.22f)
                                    isInLightTheme -> Color.White.copy(alpha = 0.30f)
                                    else -> Color.White.copy(alpha = 0.10f)
                                },
                            )
                        },
                    )
                } else {
                    Modifier
                        .background(fallbackSurface, shape)
                        .border(1.dp, glassEdgeBrush(), shape)
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * 玻璃版播放控件行：随机 / 上一首 / 播放暂停 / 下一首 / 播放列表。
 * 全部按钮走 [GlassCircleButton] 统一质感；主播放按钮 [GlassCircleButton.emphasized]。
 * 不带任何卡片容器——进度条与控件直接排在页面上（玻璃感只属于按钮本身）。
 *
 * 尺寸档位与 [PlaybackControls] 一致：竖屏 42/64dp、横屏紧凑 38/56dp。
 */
@Composable
internal fun GlassPlaybackControls(
    backdrop: Backdrop? = LocalNiBackdrop.current,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playMode: Int,
    modeIcon: ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    compact: Boolean = false,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val sideSize = if (compact) 38.dp else 42.dp
    val sideIconSize = if (compact) 24.dp else 28.dp
    val mainSize = if (compact) 56.dp else 64.dp
    val mainIconSize = if (compact) 32.dp else 36.dp
    val gap = if (compact) 12.dp else 16.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左端：播放模式
        Box(modifier = Modifier.weight(1f)) {
            GlassCircleButton(
                onClick = onCyclePlayMode,
                icon = modeIcon,
                contentDescription = modeLabel,
                size = sideSize,
                iconSize = sideIconSize,
                tint = onSurface.copy(alpha = 0.7f),
                backdrop = backdrop,
            )
        }

        // 中间：上一首 / 播放暂停 / 下一首（严格居中）
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassCircleButton(
                onClick = onPrevious,
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.player_previous),
                size = sideSize,
                iconSize = sideIconSize,
                tint = onSurface.copy(alpha = if (hasPrev) 0.85f else 0.25f),
                enabled = hasPrev,
                backdrop = backdrop,
            )

            Spacer(modifier = Modifier.width(gap))

            GlassCircleButton(
                onClick = onTogglePlay,
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.player_pause)
                } else {
                    stringResource(R.string.player_play)
                },
                size = mainSize,
                iconSize = mainIconSize,
                tint = primary,
                emphasized = true,
                backdrop = backdrop,
            )

            Spacer(modifier = Modifier.width(gap))

            GlassCircleButton(
                onClick = onNext,
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.player_next),
                size = sideSize,
                iconSize = sideIconSize,
                tint = onSurface.copy(alpha = if (hasNext) 0.85f else 0.25f),
                enabled = hasNext,
                backdrop = backdrop,
            )
        }

        // 右端：播放列表
        Box(modifier = Modifier.weight(1f)) {
            GlassCircleButton(
                onClick = onShowPlaylist,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.player_playlist),
                size = sideSize,
                iconSize = sideIconSize,
                tint = onSurface.copy(alpha = 0.5f),
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
