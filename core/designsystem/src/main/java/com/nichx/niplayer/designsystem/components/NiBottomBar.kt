package com.nichx.niplayer.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/**
 * 底部导航栏标签项数据模型。
 *
 * @param route 导航路由，用于匹配当前选中项
 * @param label 标签文本
 * @param selectedIcon 选中态图标
 * @param unselectedIcon 非选中态图标
 */
data class NiBottomBarTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * 底部导航栏组件。
 *
 * 带圆角 Card 容器 + 跟随选中项滑动的灰色 Pill 指示器。
 * 两端保留 16dp 边距，适配全面屏底部安全区域。
 *
 * @param tabs 导航标签列表
 * @param currentRoute 当前路由，用于确定选中项
 * @param onTabSelected 标签点击回调
 * @param onTabLongClicked 标签长按回调（如长按媒体库关闭文件浏览页回到存储源列表）
 * @param modifier 修饰符
 */
@Composable
fun NiBottomBar(
    tabs: List<NiBottomBarTab>,
    currentRoute: String?,
    onTabSelected: (NiBottomBarTab) -> Unit,
    modifier: Modifier = Modifier,
    onTabLongClicked: ((NiBottomBarTab) -> Unit)? = null,
) {
    val extraColors = NiExtraColors.current
    val isDark = extraColors.isDark
    // 大圆角 Card 作为容器，阴影高度 24dp 保证与圆角弧度一致
    val barShape = RoundedCornerShape(28.dp)
    val barColor = if (isDark) Color(0xFF1C1C2E) else Color.White
    // Pill 指示器：浅色浅灰；深色用低透明度白叠加，避免深色模式下亮灰指示器过于刺眼
    val pillColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE8E8E8)
    val barHeight = 60.dp

    val selectedIndex = remember(currentRoute, tabs) {
        tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }
    val tabCount = tabs.size

    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0f) }

    // Pill 尺寸计算：基于容器实际宽度等分，避免硬编码
    //
    // tabWidthPx：容器总宽 ÷ 标签数，得到每个标签的均分宽度
    // pillWidthPx：在均分宽基础上左右各缩 8dp，让 Pill 不贴满整个标签区域，视觉上更透气
    // pillHeightPx：51dp 固定高度，略低于 barHeight（60dp），上下留出余白
    // pillRadiusPx：使用与 barShape 相同的 28dp 圆角，保持内外弧线一致
    val tabWidthPx = if (tabCount > 0 && containerWidthPx > 0f) containerWidthPx / tabCount else 0f
    val pillWidthPx = (tabWidthPx - with(density) { 8.dp.toPx() }).coerceAtLeast(0f)
    val pillHeightPx = with(density) { 51.dp.toPx() }
    val pillRadiusPx = with(density) { 28.dp.toPx() }

    // Pill 水平偏移量，用 Animatable 驱动平滑动画
    // 初始值 0f 表示居左（选中第一个标签），切换标签时 animateTo 移到目标位置
    val pillOffsetX = remember { Animatable(0f) }

    // 初始化 Pill 位置，直接跳转无动画
    LaunchedEffect(containerWidthPx) {
        if (containerWidthPx > 0f) {
            val target = selectedIndex * tabWidthPx + (tabWidthPx - pillWidthPx) / 2f
            pillOffsetX.snapTo(target)
        }
    }

    // 选中项切换时 Pill 平滑滚动，320ms 缓动
    LaunchedEffect(selectedIndex) {
        if (containerWidthPx > 0f && tabWidthPx > 0f) {
            val target = selectedIndex * tabWidthPx + (tabWidthPx - pillWidthPx) / 2f
            pillOffsetX.animateTo(target, tween(durationMillis = 320))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .navigationBarsPadding(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            shape = barShape,
            colors = CardDefaults.cardColors(containerColor = barColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        // 监听容器宽度变化，用于计算 Pill 位置
                        if (it.width.toFloat() != containerWidthPx) {
                            containerWidthPx = it.width.toFloat()
                        }
                    }
                    .drawBehind {
                        // 在 Card 内部绘制灰色 Pill 作为选中态背景指示器
                        if (pillWidthPx > 0f && pillHeightPx > 0f) {
                            val pillY = (size.height - pillHeightPx) / 2f
                            drawRoundRect(
                                color = pillColor,
                                topLeft = Offset(pillOffsetX.value, pillY),
                                size = Size(pillWidthPx, pillHeightPx),
                                cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx),
                            )
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        NiBottomBarItem(
                            tab = tab,
                            isSelected = isSelected,
                            onClick = { onTabSelected(tab) },
                            onLongClick = onTabLongClicked?.let { { it(tab) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部导航栏单项按钮。
 *
 * 图标在上、文字在下垂直排列，选中态/非选中态通过颜色动画过渡。
 * 取消 Material ripple 指示效果，由灰色 Pill 统一反馈。
 *
 * 支持长按：[onLongClick] 非空时用 [combinedClickable] 同时处理单击和长按，
 * 单击立即响应不等待（长按在 DOWN 后计时，UP 前未超时则立即触发 onClick）。
 * 无长按需求时退化为 [clickable]，保持原有行为。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NiBottomBarItem(
    tab: NiBottomBarTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) primary else outline,
        animationSpec = tween(300),
        label = "contentColor",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}
