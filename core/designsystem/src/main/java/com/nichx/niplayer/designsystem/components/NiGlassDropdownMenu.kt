package com.nichx.niplayer.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntRect
import kotlin.uuid.Uuid

/**
 * 玻璃下拉菜单（**同窗口 overlay**，替代独立 Popup 窗口的 M3 DropdownMenu）。
 *
 * 内容投递到 [NiGlassOverlay]（[NiGlassOverlayKind.Dropdown] 形态），由 App 根部
 * [NiGlassOverlayHost] 在 [anchorBounds] 锚点处展开：backdrop 真模糊 + 半透明表面
 * （透明度随面板设置 [LocalNiGlassPanelOpacity]），消除跨独立窗口采样问题。
 *
 * 动画由 overlay 内部的**单一 progress** 驱动（见 `DropdownGlassOverlay`）：
 * 菜单自 [anchorBounds] 对应的那个角「长出来」并带过冲回弹。
 *
 * @param expanded 是否展开
 * @param onDismissRequest 关闭回调（点击外部 / 返回键触发）
 * @param anchorBounds 锚点按钮的**根坐标矩形**——由触发按钮 `onGloballyPositioned` 捕获：
 *   `IntRect(positionInRoot, size)`。菜单据此定位、决定展开方向与缩放起点。
 * @param content 菜单项内容（DropdownMenuItem 等）
 */
@Composable
fun NiGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: IntRect = IntRect.Zero,
    contentVersion: Any? = null,
    content: @Composable () -> Unit,
) {
    val overlayId = remember { "dropdown_${Uuid.random()}" }

    // expanded/anchor 控制展开与位置；contentVersion 变化时（如同一菜单内原地切换页面）
    // 重新经 showOrUpdate 投递，令根宿主用最新 [content] 就地刷新，避免「旧内容退场 + 新内容进场」闪烁。
    LaunchedEffect(expanded, anchorBounds, contentVersion) {
        if (expanded) {
            NiGlassOverlay.showOrUpdate(
                NiGlassOverlayRequest(
                    id = overlayId,
                    kind = NiGlassOverlayKind.Dropdown,
                    anchorBounds = anchorBounds,
                    onDismiss = onDismissRequest,
                ) {
                    content()
                },
            )
        } else {
            NiGlassOverlay.dismiss(overlayId)
        }
    }
    DisposableEffect(Unit) {
        onDispose { NiGlassOverlay.dismiss(overlayId) }
    }
}
