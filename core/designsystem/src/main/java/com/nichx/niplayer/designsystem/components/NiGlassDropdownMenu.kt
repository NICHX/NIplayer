package com.nichx.niplayer.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import kotlin.uuid.Uuid

/**
 * 玻璃下拉菜单（**同窗口 overlay**，替代独立 Popup 窗口的 M3 DropdownMenu）。
 *
 * 内容投递到 [NiGlassOverlay]（[NiGlassOverlayKind.Dropdown] 形态），由 App 根部
 * [NiGlassOverlayHost] 在 [anchor] 锚点下方展开：backdrop 真模糊 + 半透明表面
 * （透明度随面板设置 [LocalNiGlassPanelOpacity]），消除跨独立窗口采样问题。
 *
 * @param expanded 是否展开
 * @param onDismissRequest 关闭回调（点击外部 / 返回键触发）
 * @param anchor 锚点屏幕坐标（一般取触发按钮的 `positionInRoot()`）
 * @param content 菜单项内容（DropdownMenuItem 等）
 */
@Composable
fun NiGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchor: IntOffset = IntOffset.Zero,
    content: @Composable () -> Unit,
) {
    val overlayId = remember { "dropdown_${Uuid.random()}" }

    LaunchedEffect(expanded, anchor) {
        if (expanded) {
            NiGlassOverlay.show(
                NiGlassOverlayRequest(
                    id = overlayId,
                    kind = NiGlassOverlayKind.Dropdown,
                    anchor = anchor,
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
