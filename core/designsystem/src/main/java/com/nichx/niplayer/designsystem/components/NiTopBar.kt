package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 顶部应用栏组件 —— 控件渐变模糊（边缘渐隐）。
 *
 * 参考 legado-with-MD3 的 FadingEdge 思路：顶栏整体透明，背景绘制一条自上而下
 * 由透明过渡到页面表面色的渐变。当内容滚动到顶栏区域时，越靠近顶栏底部越被
 * 表面色平滑覆盖（而非生硬裁切），产生类似"渐进模糊"的通透层次感。
 *
 * 说明：顶栏嵌套在各页面内容内部，无法作为 hazeEffect 的兄弟浮层，因此这里采用
 * 可靠、无绘制层级依赖的光栅渐变遮罩实现。
 *
 * @param title 标题文本
 * @param modifier 修饰符
 * @param subtitle 副标题文本（可为 null）
 * @param navigationIcon 导航图标区域
 * @param actions 右侧操作按钮区域
 * @param topBackground 顶栏背景色（渐变终点），默认取页面表面色
 * @param fadeFromRatio 渐变开始位置（占总高度），[0,1]
 */
@Composable
fun NiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    topBackground: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    fadeFromRatio: Float = 0f,
) {
    // 渐显遮罩 Brush：顶部透明 -> 底部表面色。缓存以避免每帧重建
    val fadeBrush = remember(topBackground, fadeFromRatio) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            fadeFromRatio to Color.Transparent,
            1f to topBackground,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp)
            .drawBehind {
                // 边缘渐隐：整栏绘制透明->表面色的渐变遮罩
                drawRect(
                    brush = fadeBrush,
                    size = size,
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon()
        Row(
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = "  $subtitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row { actions() }
    }
}