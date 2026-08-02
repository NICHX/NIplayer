package com.nichx.niplayer.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放器右侧抽屉面板。
 *
 * 参考 mpv-android-anime4k 的 Drawer 统一样式：
 * - 320dp 宽度，全高
 * - slideInHorizontally + fadeIn 入场（300ms, FastOutSlowInEasing）
 * - slideOutHorizontally + fadeOut 退场（250ms）
 * - 半透明渐变背景（0xCC121212 → 0xE6121212）
 * - 22sp Bold 标题 + "✕" 关闭按钮
 * - 1dp 分隔线 0x33FFFFFF
 * - 内容区域可滚动
 * - 点击背景 / 返回键关闭
 */
@Composable
fun NiDrawerPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    var shouldRender by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            shouldRender = true
            isVisible = true
        } else {
            isVisible = false
            coroutineScope.launch {
                delay(300) // 等待退场动画完成后移除，避免空 Box 拦截触摸事件
                shouldRender = false
            }
        }
    }

    if (!shouldRender) return

    BackHandler(enabled = isVisible) {
        isVisible = false
        coroutineScope.launch {
            delay(300)
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                isVisible = false
                coroutineScope.launch {
                    delay(300)
                    onDismiss()
                }
            },
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(250, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
            ) {
                // 半透明渐变背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xCC121212),
                                    Color(0xE6121212),
                                ),
                            ),
                        ),
                )

                // 内容层（阻止点击穿透到背景）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* 阻止穿透 */ },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        // 标题栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            IconButton(
                                onClick = {
                                    isVisible = false
                                    coroutineScope.launch {
                                        delay(300)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(32.dp),
                            ) {
                                Text(
                                    text = "\u2715",
                                    fontSize = 20.sp,
                                    color = Color(0xFFBBBBBB),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0x33FFFFFF), thickness = 1.dp)
                        Spacer(Modifier.height(8.dp))

                        // 可滚动内容
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 可折叠展开的分组（用于 Drawer 内的设置分组）。
 *
 * 参考 mpv-android-anime4k 的 ExpandableSection：
 * - "▶" 箭头旋转 0→90° spring 弹性动画
 * - 折叠/展开内容有 expandVertically/shrinkVertically + fadeIn/fadeOut
 * - 分组背景 Color(0x1AFFFFFF) + 12dp RoundedCornerShape
 */
@Composable
fun NiExpandableSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "expandArrow",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0x1AFFFFFF),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u25B6",
                fontSize = 14.sp,
                color = Color(0xFF64B5F6),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .rotate(rotationAngle),
            )
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeOut(animationSpec = tween(150)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                content()
            }
        }
    }
}
