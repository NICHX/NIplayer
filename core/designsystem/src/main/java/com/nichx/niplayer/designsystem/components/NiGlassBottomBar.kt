package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.nichx.niplayer.designsystem.glass.DampedDragAnimation
import com.nichx.niplayer.designsystem.glass.InteractiveHighlight
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** 底部导航栏标签项数据模型。 */
data class NiBottomBarTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** 玻璃底栏的底部边距（同时用作模糊标志位）。 */
private object NiGlassBarDefaults {
    const val BlurRadius = 8f
    const val LensRadius = 6f
    const val ContainerAlpha = 0.36f
}

/** 提供给各 Tab 项的缩放因子（按压缩放由 DampedDragAnimation.pressProgress 驱动）。 */
val LocalNiGlassBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 玻璃底栏单项。
 * 移植自 legado-with-MD3 FloatingBottomBarItem。
 */
@Composable
fun RowScope.NiGlassBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalNiGlassBarTabScale.current
    Column(
        modifier
            .clip(ContinuousCapsule)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * 悬浮液态玻璃底栏。
 *
 * 完全复刻 legado-with-MD3 的 FloatingBottomBar 交互与质感：
 * - 用 com.kyant.backdrop 捕获 [layerBackdrop] 标记的背景做"真实玻璃"（vibrancy + blur + lens + 高光）；
 * - 玻璃液滴（pill）跟随手指弹簧阻尼滑动，切换/按压产生阻尼缩放；
 * - 按压时图标放大（lift 到 1.2）、触点渲染 AGSL 高光（API 33+）。
 *
 * @param selectedIndex  当前选中索引（应指向数据源，如 pager targetPage / 当前 tab）
 * @param onSelected     手指释放落到某个索引时回调（切换 tab）
 * @param onReselected   再次按下当前选中项时回调
 * @param backdrop       由 [rememberLayerBackdrop] 捕获的页面背景画布
 */
@Composable
fun NiGlassBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    onReselected: (Int) -> Unit = {},
    backdrop: Backdrop,
    tabsCount: Int,
    isBlurEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isInLightTheme = !NiExtraColors.current.isDark
    val containerColor = if (isBlurEnabled) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = NiGlassBarDefaults.ContainerAlpha)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }
    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    val touchX = indicatorX + offset.x
                    padding + touchX
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                if (targetIndex != selectedIndex()) {
                    onSelected(targetIndex)
                } else {
                    onReselected(targetIndex)
                }
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }
    LaunchedEffect(selectedIndex, dampedDragAnimation) {
        snapshotFlow { selectedIndex() }.collectLatest { index ->
            currentIndex = index
            dampedDragAnimation.animateToValue(index.toFloat())
        }
    }

    // 跟随手指的液态白斑（参考项目 InteractiveHighlight），API 33+ 才启用
    val interactiveHighlight =
        if (isBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember(animationScope, tabWidthPx) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            } else {
                                size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            },
                            size.height / 2f,
                        )
                    },
                )
            }
        } else {
            null
        }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        // 1) 玻璃底座：整根胶囊模糊页面背景
        Row(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = contentWidthPx / tabsCount
                }
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        if (isBlurEnabled) {
                            vibrancy()
                            blur(NiGlassBarDefaults.BlurRadius.dp.toPx())
                            lens(
                                NiGlassBarDefaults.LensRadius.dp.toPx(),
                                NiGlassBarDefaults.LensRadius.dp.toPx(),
                            )
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (isBlurEnabled) 1f else 0f)
                    },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f),
                        )
                    },
                    layerBlock = {
                        if (isBlurEnabled) {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(
                    if (isBlurEnabled && interactiveHighlight != null) {
                        interactiveHighlight.modifier
                    } else {
                        Modifier
                    },
                )
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // 2) 液滴使用的背景画布（不可见，仅用于 pill 捕获）
        CompositionLocalProvider(
            LocalNiGlassBarTabScale provides {
                if (isBlurEnabled) {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                } else {
                    1f
                }
            },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            if (isBlurEnabled) {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(NiGlassBarDefaults.BlurRadius.dp.toPx())
                                lens(
                                    NiGlassBarDefaults.LensRadius.dp.toPx() * progress,
                                    NiGlassBarDefaults.LensRadius.dp.toPx() * progress,
                                )
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(
                                alpha = if (isBlurEnabled) {
                                    dampedDragAnimation.pressProgress
                                } else {
                                    0f
                                },
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(
                        if (isBlurEnabled && interactiveHighlight != null) {
                            interactiveHighlight.modifier
                        } else {
                            Modifier
                        },
                    )
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // 3) 玻璃液滴（pill）：独立胶囊，模糊背景并携带全部缩放/高光/内阴影反馈
        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val contentWidth = totalWidthPx - with(density) { 8.dp.toPx() }
                        val singleTabWidth = contentWidth / tabsCount
                        val progressOffset = dampedDragAnimation.value * singleTabWidth
                        translationX = if (isLtr) {
                            progressOffset + panelOffset
                        } else {
                            -progressOffset + panelOffset
                        }
                    }
                    .then(
                        if (isBlurEnabled && interactiveHighlight != null) {
                            interactiveHighlight.gestureModifier
                        } else {
                            Modifier
                        },
                    )
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { ContinuousCapsule },
                        effects = {
                            if (isBlurEnabled) {
                                val progress = dampedDragAnimation.pressProgress
                                lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, true)
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(
                                alpha = if (isBlurEnabled) {
                                    dampedDragAnimation.pressProgress
                                } else {
                                    0f
                                },
                            )
                        },
                        shadow = {
                            Shadow(alpha = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8f.dp * dampedDragAnimation.pressProgress,
                                alpha = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f,
                            )
                        },
                        layerBlock = {
                            if (isBlurEnabled) {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            }
                        },
                        onDrawSurface = {
                            // 与参考项目一致：柔和平铺底色，按压时显现折射/高光/内阴影，产生“液态”感
                            val progress =
                                if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f
                            drawRect(
                                color = if (isInLightTheme) {
                                    Color.Black.copy(0.1f)
                                } else {
                                    Color.White.copy(0.1f)
                                },
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    )
                    .height(56.dp)
                    .width(with(density) { ((totalWidthPx - 8.dp.toPx()) / tabsCount).toDp() }),
            )
        }
    }
}