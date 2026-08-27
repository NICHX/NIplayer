package com.nichx.niplayer.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.nichx.niplayer.designsystem.components.NiBottomBarTab
import com.nichx.niplayer.designsystem.components.glassOnSurfaceMuted
import com.nichx.niplayer.designsystem.components.NiGlassBarItem
import com.nichx.niplayer.designsystem.components.NiGlassBottomBar

/** 底部共享导航栏的悬浮占用高度（玻璃胶囊 56dp + 底部 8dp 间距），供各宿主让位计算。 */
val HomeBottomNavBarHeight: Dp = 56.dp + 8.dp

/**
 * 共享底部玻璃导航栏。
 *
 * 作为独立组件叠加到需要它的页面：
 * - Home 宿主：三个 tab 就地切换（[selectedIndex] 指向 pager，[onSelect] 就地图切换）；
 * - 独立全屏页宿主（如文件浏览）：[selectedIndex] 为该页所属 tab 的固定索引，
 *   [onSelect] 由外层回调负责"导航回 Home 对应 tab"。
 *
 * 玻璃背景由宿主通过 [backdrop] 提供（须已捕获该宿主页面内容）。
 *
 * @param maxWidth  底栏最大宽度，与页面正文对齐（大屏限制，紧凑屏不限制）。
 * @param bottomInset 底栏距屏幕底部的总悬浮间距（通常为 8dp + 手势条高度）。
 */
@Composable
fun HomeBottomNavBar(
    selectedIndex: () -> Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    bottomInset: Dp = 8.dp,
) {
    val tabs = HomeTab.entries.map { tab ->
        NiBottomBarTab(
            route = tab.route,
            label = stringResource(tab.labelRes),
            selectedIcon = tab.selectedIcon,
            unselectedIcon = tab.unselectedIcon,
        )
    }

    NiGlassBottomBar(
        selectedIndex = selectedIndex,
        onSelected = onSelect,
        backdrop = backdrop,
        tabsCount = tabs.size,
        isBlurEnabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth)
            .padding(start = 16.dp, end = 16.dp, bottom = bottomInset),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex()
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                // 未选中项用玻璃浮层高对比前景色，避免灰色在玻璃底（复杂/深色背景）上对比不足
                glassOnSurfaceMuted()
            }
            NiGlassBarItem(onClick = { onSelect(index) }) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}