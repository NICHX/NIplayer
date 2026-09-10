package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** 玻璃底部面板顶部圆角（对齐 legado / MD3 BottomSheet 风格）。 */
val NiGlassSheetCornerRadius: Dp = 28.dp

/** 玻璃底部面板背景模糊半径（偏小以保留背景细节，通透感更好）。 */
val NiGlassSheetBlurRadius: Dp = 14.dp

/** 玻璃底部面板打开时背后的压暗层透明度。 */
const val NiGlassSheetScrimAlpha = 0.40f

/**
 * 液态玻璃底部面板（**同窗口 overlay**，移植自 legado-with-MD3 / backdrop 官方 Glass Bottom Sheet）。
 *
 * 与独立 Dialog 窗口不同，本面板渲染在**调用方所在窗口**的内容层之上（同窗口 overlay）：
 * - 背后用 [LocalNiBackdrop] 的 backdrop 采样**同一窗口**的页面内容做真模糊（[drawBackdrop]），
 *   同窗口定位可靠（与悬浮底栏同机制），避免跨独立窗口采样时的坐标错位；
 * - 无 backdrop 或 API < 33 时降级为不透明面板（[niFrostSurfaceColor]），不劣化。
 *
 * 自带：压暗层（点击关闭）、返回键关闭、自下而上的滑入/滑出动画。
 *
 * @param show 是否显示
 * @param onDismissRequest 关闭回调（点击遮罩 / 返回键触发）
 * @param title 可选标题（居中显示，不传则无标题栏）
 * @param blurRadius 背景模糊半径（仅 backdrop 真模糊路径生效）
 * @param content 面板内容；需要滚动时由调用方自行包 verticalScroll
 */
@Composable
fun NiGlassBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    blurRadius: Dp = NiGlassSheetBlurRadius,
    bottomInset: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalNiBackdrop.current
    val glassEnabled = LocalNiGlassEnabled.current && backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // 返回键统一由其宿主 NiGlassOverlayHost 处理（此处不再自注册 BackHandler）：
    // 避免返回手势期间回调栈中的 BackHandler 被动态移除导致 predictive back 状态不一致卡死。

    // 面板最大高度：窗口的 80%
    val maxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * 0.8f).toDp()
    }
    // 面板半透明底色（backdrop 真模糊路径）与顶部圆角，先在 @Composable 作用域内求值
    val panelSurface = niGlassPanelSurfaceColor()
    val sheetShape = RoundedCornerShape(
        topStart = NiGlassSheetCornerRadius,
        topEnd = NiGlassSheetCornerRadius,
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 压暗层：整体淡入淡出（位置固定，不随面板上移），点击关闭
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = NiGlassSheetScrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
        }

        // 玻璃面板：仅面板本身自下而上滑入/滑出
        AnimatedVisibility(
            visible = show,
            enter = slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it },
        ) {
            // 内层再包一个与父同尺寸的 Box：col{align} 必须落在 BoxScope 上，面板才能正确贴底，
            // 否则 align 会错误解析到外层 Box 导致面板被顶到顶部。
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .then(
                            if (glassEnabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop!!,
                                    shape = { sheetShape },
                                    effects = {
                                        blur(blurRadius.toPx())
                                    },
                                    onDrawSurface = { drawRect(panelSurface) },
                                )
                            } else {
                                Modifier.background(
                                    color = niFrostSurfaceColor(),
                                    shape = sheetShape,
                                )
                            }
                        ),
                ) {
                    // 内容层：导航栏避让 + 底部 inset（玻璃背景保持贴底）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .then(
                                if (bottomInset != Dp.Unspecified) {
                                    Modifier.padding(bottom = bottomInset)
                                } else {
                                    Modifier
                                },
                            ),
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
                                    .padding(horizontal = 56.dp, vertical = 16.dp),
                            )
                        }
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 底部系统导航栏高度（dp），供面板内容在需要时精确避让。
 */
@Composable
fun niNavigationBarInset(): Dp {
    return with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
}
