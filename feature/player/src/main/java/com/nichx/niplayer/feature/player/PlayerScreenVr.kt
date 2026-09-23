package com.nichx.niplayer.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale


/** VR 控制条距顶部距离，避免与状态栏 / 挖孔重叠。 */
internal val VR_OVERLAY_TOP_DP: Dp = 88.dp

/**
 * VR 模式控制条（顶部玻璃胶囊）。
 *
 * 在 VR 环视模式下提供三个操作：循环切换画面格式（左右/上下/整幅 × 180°/360°）、画面归中、
 * 退出 VR。随控制栏显隐一起淡入淡出。
 *
 * 位置固定为距顶部 [VR_OVERLAY_TOP_DP] 处并向下展开，避免沉浸式全屏下与状态栏 / 挖孔
 * 重叠导致看不清。
 *
 * @param formatLabel 当前格式的显示文案（如「左右 · 360°」）
 * @param fovDegrees 当前垂直视场角（度）
 * @param sensitivity 当前陀螺仪灵敏度
 * @param visible 是否可见（跟随控制栏显隐）
 */
@Composable
internal fun VrControlOverlay(
    formatLabel: String,
    fovDegrees: Int,
    sensitivity: Float,
    zoom: Float,
    viewLocked: Boolean,
    visible: Boolean,
    onClickFormat: () -> Unit,
    onClickRecenter: () -> Unit,
    onToggleViewLock: () -> Unit,
    onFovChange: (Float) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onClickExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier.padding(top = VR_OVERLAY_TOP_DP),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.player_vr) + " · " + formatLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                // 实验性标记：VR 视觉/兼容性仍在迭代，明确告知用户
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF6C9CFF).copy(alpha = 0.25f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = stringResource(R.string.player_vr_experimental),
                        color = Color(0xFF6C9CFF),
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClickFormat, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = stringResource(R.string.player_vr_format_hint),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onClickRecenter, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = stringResource(R.string.player_vr_recenter),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onToggleViewLock, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (viewLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = stringResource(R.string.player_vr_view_lock),
                        tint = if (viewLocked) Color(0xFFFFC107) else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onClickExit, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.player_vr_exit),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${stringResource(R.string.player_vr_fov)} $fovDegrees°",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                SmallRoundIconButton(onFovChange, -5f, Icons.Rounded.Remove,
                    stringResource(R.string.player_vr_fov_hint) + "-")
                SmallRoundIconButton(onFovChange, 5f, Icons.Rounded.Add,
                    stringResource(R.string.player_vr_fov_hint) + "+")
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.player_vr_sensitivity)} ${(sensitivity * 100).toInt()}",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                SmallRoundIconButton(onSensitivityChange, -0.05f, Icons.Rounded.Remove,
                    stringResource(R.string.player_vr_sensitivity_hint) + "-")
                SmallRoundIconButton(onSensitivityChange, 0.05f, Icons.Rounded.Add,
                    stringResource(R.string.player_vr_sensitivity_hint) + "+")
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.player_vr_zoom)} ${
                        String.format(Locale.ROOT, "%.1f", zoom)
                    }×",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                SmallRoundIconButton(onZoomChange, -0.1f, Icons.Rounded.Remove,
                    stringResource(R.string.player_vr_zoom_hint) + "-")
                SmallRoundIconButton(onZoomChange, 0.1f, Icons.Rounded.Add,
                    stringResource(R.string.player_vr_zoom_hint) + "+")
            }
        }
    }
}

@Composable
internal fun SmallRoundIconButton(
    onChange: (Float) -> Unit,
    delta: Float,
    icon: ImageVector,
    contentDescription: String,
) {
    IconButton(
        onClick = { onChange(delta) },
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ===== 配置化 HUD 按钮系统 =====
// 中部侧边按钮由配置列表驱动，增删/排序/调样式只需改 [HudButtonConfig] 列表。
// 后续用户自定义只需在设置页读写同一份配置列表，无需改动渲染逻辑。
