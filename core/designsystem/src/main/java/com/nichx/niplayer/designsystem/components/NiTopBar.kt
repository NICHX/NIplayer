package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive

/**
 * 顶部应用栏 —— 渐变模糊（真实渐进模糊 + 半透明 scrim，参考 legado-with-MD3）。
 *
 * 当处于 [NiScaffold] 作用域内（[LocalHazeState] 可用）时，顶栏对背后滚动的内容做**真实渐进模糊**：
 * 顶栏顶部强模糊、向下渐变成清晰（[HazeProgressive.verticalGradient]），再叠加一层半透明 surface tint
 * 保证文字可读。此时顶栏必须叠在内容之上，内容经 [NiScaffold] 延伸到其背后滚动。
 *
 * 脱离 [NiScaffold] 作用域（无 [LocalHazeState]）时退化为原先的光栅渐变遮罩：顶部透明、底部过渡到表面色，
 * 保持无模糊也可用的稳妥回退。
 *
 * @param title 标题文本
 * @param modifier 修饰符
 * @param subtitle 副标题文本（可为 null）
 * @param navigationIcon 导航图标区域
 * @param actions 右侧操作按钮区域
 * @param topBackground 顶栏 scrim 基底色（渐变/scrim 终点），默认取页面表面色
 * @param fadeFromRatio 渐变开始位置（占总高度，自定义渐变用），[0,1]
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun NiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    topBackground: Color = MaterialTheme.colorScheme.surface,
    fadeFromRatio: Float = 0f,
) {
    val hazeState = LocalHazeState.current
    // 与底栏（NiGlassBottomBar）统一的「薄浮层不透明度」，设置内可同步调节
    val glassOpacity = LocalNiGlassOpacity.current
    // 半透明 scrim：保证顶栏文字在任何内容上方都清晰可读。
    // 透明度由薄浮层不透明度驱动，明暗主题仅微调基底比例（亮色 0.18 / 暗色 0.25）。
    val scrimColor = remember(topBackground, glassOpacity) {
        topBackground.copy(
            alpha = (if (topBackground.luminance() >= 0.5f) 0.18f else 0.25f) * glassOpacity,
        )
    }
    // 渐显遮罩 Brush：顶部透明 -> 底部表面色（无 LocalHazeState 的渐变回退）
    val fadeBrush = remember(topBackground, fadeFromRatio) {
        Brush.verticalGradient(
            0f to Color.Transparent,
            fadeFromRatio to Color.Transparent,
            1f to topBackground,
        )
    }

    if (hazeState != null) {
        // ===== 真实渐进模糊路径（NiScaffold 作用域）=====
        Column(
            modifier = modifier
                .fillMaxWidth()
                // 先铺一层半透明 scrim 保证可读性
                .background(scrimColor)
                // 再对背后内容做自上而下渐强的真实模糊
                .niHazeEffect(
                    state = hazeState,
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationIcon()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        // 显式用 onSurface：容器为 Transparent 时 contentColorFor(Transparent)
                        // 会得出错误的明暗前景色，导致深色模式下标题不可见
                        color = MaterialTheme.colorScheme.onSurface,
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
    } else {
        // ===== 光栅渐变回退路径（无 LocalHazeState，保持原视觉）=====
        Row(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp)
                .drawBehind {
                    drawRect(
                        brush = fadeBrush,
                        size = size,
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    // 显式用 onSurface：容器为 Transparent 时 contentColorFor(Transparent)
                    // 会得出错误的明暗前景色，导致深色模式下标题不可见
                    color = MaterialTheme.colorScheme.onSurface,
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
}