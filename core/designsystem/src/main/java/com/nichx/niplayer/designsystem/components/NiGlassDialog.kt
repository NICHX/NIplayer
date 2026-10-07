package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.nichx.niplayer.designsystem.motion.NiAnimatedVisibility
import com.nichx.niplayer.designsystem.theme.MotionTokens

/**
 * 居中玻璃对话框（**同窗口 overlay**，移植自 NiDialog 的磨砂卡片风格 + backdrop 真模糊）。
 *
 * 与独立 Dialog 窗口不同，本弹窗渲染在 [LocalNiBackdrop] 的 backdrop 捕获层之外
 * （由 [NiGlassOverlayHost] 承载），同窗口采样主内容做真模糊，无循环、定位可靠。
 * 无 backdrop 或 API < 33 时降级为不透明磨砂卡片（[niFrostSurfaceColor]）。
 *
 * 自带：全屏压暗层（点击关闭）、返回键关闭、淡入 + 缩放入场。
 *
 * @param show 是否显示
 * @param onDismissRequest 关闭回调（点击遮罩 / 返回键触发）
 * @param title 可选标题
 * @param blurRadius 背景模糊半径
 * @param content 弹窗内容
 */
@Composable
fun NiGlassDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    blurRadius: Dp = NiGlassSheetBlurRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalNiBackdrop.current
    val glassEnabled = LocalNiGlassEnabled.current && backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val shape = RoundedCornerShape(24.dp)
    // 面板半透明底色，先在 @Composable 作用域内求值
    val panelSurface = niGlassPanelSurfaceColor()

    // 首帧遮挡：浮层节点"请求到达才创建"，首帧玻璃可能先以兜底色绘制一帧再补上模糊/折射，
    // 与入场动画叠加就是"多闪一次"。等首次布局完成后再显示（与玻璃下拉菜单同款处理）。
    var revealed by remember(show) { mutableStateOf(false) }

    // 返回键统一由其宿主 NiGlassOverlayHost 处理（此处不再自注册 BackHandler），
    // 避免返回手势期间浮层关闭导致回调栈中的 BackHandler 被动态移除而卡死。

    Box(modifier = modifier.fillMaxSize()) {
        // 居中容器：**不参与动画**、常驻但不拦触摸（压暗层由根宿主统一提供）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            // 玻璃卡片：动画节点收敛到卡片自身尺寸。
            // 关键：**绝不对全屏层做缩放** —— 缩放整屏 graphicsLayer 会逐帧重建整屏离屏缓冲，
            // 表现为入场时"多闪一次"（底部面板只平移、下拉菜单动画节点是卡片尺寸，故都不闪）。
            // 时长统一走 SURFACE（令牌表中「弹窗」属于 surface），与压暗层同长同曲线，避免"卡片先弹好、压暗还在变"。
            NiAnimatedVisibility(
                visible = show,
                enter = fadeIn(tween(MotionTokens.SURFACE, easing = MotionTokens.easeEnter)) +
                    scaleIn(
                        animationSpec = tween(MotionTokens.SURFACE, easing = MotionTokens.easeEnter),
                        initialScale = 0.92f,
                    ),
                exit = fadeOut(
                    tween(MotionTokens.exitOf(MotionTokens.SURFACE), easing = MotionTokens.easeExit),
                ) + scaleOut(
                    animationSpec = tween(
                        MotionTokens.exitOf(MotionTokens.SURFACE),
                        easing = MotionTokens.easeExit,
                    ),
                    targetScale = 0.92f,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 340.dp)
                        .onGloballyPositioned { revealed = true }
                        .graphicsLayer { alpha = if (revealed) 1f else 0f }
                        .then(
                            if (glassEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { shape },
                                    effects = {
                                        blur(blurRadius.toPx())
                                    },
                                    onDrawSurface = { drawRect(panelSurface) },
                                )
                            } else {
                                Modifier.background(
                                    color = niFrostSurfaceColor(),
                                    shape = shape,
                                )
                            }
                        )
                        .border(NiGlassHairWidth, niGlassBorderColor(), shape)
                        .padding(vertical = 8.dp),
                ) {
                    // 玻璃宿主位于任何 Surface 之外，LocalContentColor 默认黑色；
                    // 显式提供主题 onSurface，避免深色主题下裸 Text 在深色玻璃上不可见
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    ) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                        content()
                    }
                }
            }
        }
    }
}
