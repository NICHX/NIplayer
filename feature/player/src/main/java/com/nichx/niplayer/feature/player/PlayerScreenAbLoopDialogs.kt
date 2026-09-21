package com.nichx.niplayer.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


@Composable
internal fun AbLoopDialog(
    abLoopA: Long?,
    abLoopB: Long?,
    durationMs: Long,
    positionMs: Long,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isActive = abLoopA != null && abLoopB != null && abLoopB > abLoopA
    val aSet = abLoopA != null
    val posFormatted = formatDuration(positionMs)
    val aFormatted = abLoopA?.let { formatDuration(it) } ?: stringResource(R.string.player_ab_loop_not_set)
    val bFormatted = abLoopB?.let { formatDuration(it) } ?: stringResource(R.string.player_ab_loop_not_set)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val outlineVariant = PlayerDialogColors.divider
        val onSurfaceVariant = PlayerDialogColors.textSecondary
        val surfaceVariant = PlayerDialogColors.background
        val dialogMaxW = adaptiveDialogMaxWidth(340)
        PlayerDialogSurface(
            modifier = Modifier.widthIn(min = 280.dp, max = dialogMaxW.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {

                // 标题行：图标 + 标题 + 当前播放时间
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AbLoopIcon,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFFFFAB40) else onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_ab_loop_title),
                        color = PlayerDialogColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = posFormatted,
                        color = PlayerDialogColors.textPrimary.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(16.dp))

                // A/B 时间显示：两张等宽玻璃卡片
                if (durationMs > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AbLoopPointCard(
                            label = stringResource(R.string.player_ab_loop_point_a),
                            time = if (aSet) aFormatted else stringResource(R.string.player_ab_loop_not_set),
                            accent = Color(0xFFFFAB40),
                            set = aSet,
                            modifier = Modifier.weight(1f),
                        )
                        AbLoopPointCard(
                            label = stringResource(R.string.player_ab_loop_point_b),
                            time = if (abLoopB != null) bFormatted else stringResource(R.string.player_ab_loop_not_set),
                            accent = Color(0xFFFF5252),
                            set = abLoopB != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                    Spacer(Modifier.height(12.dp))

                    // 进度条
                    val aFrac = (abLoopA?.toFloat()?.div(durationMs) ?: 0f).coerceIn(0f, 1f)
                    val bFrac = (abLoopB?.toFloat()?.div(durationMs) ?: 0f).coerceIn(0f, 1f)
                    val posFrac = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val r = CornerRadius(h / 2, h / 2)

                            drawRoundRect(
                                color = surfaceVariant,
                                topLeft = Offset.Zero,
                                size = Size(w, h),
                                cornerRadius = r,
                            )

                            if (isActive) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFFFFAB40), Color(0xFFFFAB40), Color(0xFFFF5252), Color(0xFFFF5252)),
                                    ),
                                    topLeft = Offset(aFrac * w, 0f),
                                    size = Size((bFrac - aFrac).coerceAtLeast(2f) * w, h),
                                    cornerRadius = r,
                                )
                            } else if (aSet) {
                                drawRoundRect(
                                    color = Color(0xFFFFAB40).copy(alpha = 0.5f),
                                    topLeft = Offset(aFrac * w, 0f),
                                    size = Size(w * (1f - aFrac), h),
                                    cornerRadius = r,
                                )
                            }

                            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(posFrac * w, h / 2f))
                            drawCircle(color = Color(0xFF2095F4), radius = 2.dp.toPx(), center = Offset(posFrac * w, h / 2f))
                        }
                    }

                Spacer(Modifier.height(16.dp))

                // 操作按钮：统一药丸玻璃样式
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AbLoopActionPill(
                        label = if (aSet) stringResource(R.string.player_ab_loop_a_value, aFormatted) else stringResource(R.string.player_ab_loop_set_a, posFormatted),
                        accent = Color(0xFFFFAB40),
                        enabled = !aSet,
                        modifier = Modifier.weight(1f),
                        onClick = onSetPointA,
                    )
                    AbLoopActionPill(
                        label = if (abLoopB != null) stringResource(R.string.player_ab_loop_b_value, bFormatted) else stringResource(R.string.player_ab_loop_set_b, posFormatted),
                        accent = Color(0xFFFF5252),
                        enabled = aSet && abLoopB == null,
                        modifier = Modifier.weight(1f),
                        onClick = onSetPointB,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 状态提示 + 清除按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isActive -> Color(0xFFFFAB40).copy(alpha = 0.08f)
                                    aSet -> Color(0xFFFFAB40).copy(alpha = 0.05f)
                                    else -> surfaceVariant
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = when {
                                isActive -> stringResource(R.string.player_ab_loop_active, aFormatted, bFormatted)
                                aSet -> stringResource(R.string.player_ab_loop_a_set_prompt)
                                else -> stringResource(R.string.player_ab_loop_b_set_prompt)
                            },
                            fontSize = 12.sp,
                            color = when {
                                isActive -> Color(0xFFFFAB40)
                                aSet -> Color(0xFFFFAB40)
                                else -> onSurfaceVariant
                            },
                        )
                    }

                    if (aSet) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onClearAbLoop,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.player_ab_loop_clear), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // 快速操作提示
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.player_ab_loop_quick_hint),
                        color = onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

/** A-B 循环弹窗的端点卡片：A/B 起止时间的高亮玻璃卡片。 */
@Composable
internal fun AbLoopPointCard(
    label: String,
    time: String,
    accent: Color,
    set: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (set) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f))
            .border(
                0.5.dp,
                if (set) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (set) accent else Color.White.copy(alpha = 0.25f)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = PlayerDialogColors.textSecondary,
                fontSize = 11.sp,
            )
            Text(
                text = time,
                color = if (set) accent else PlayerDialogColors.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** A-B 循环弹窗的操作按钮：统一药丸玻璃样式，未启用时置灰。 */
@Composable
internal fun AbLoopActionPill(
    label: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = if (enabled) accent else PlayerDialogColors.textSecondary.copy(alpha = 0.6f)
    val bg = if (enabled) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
    val borderColor = if (enabled) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(bg)
            .border(0.5.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
