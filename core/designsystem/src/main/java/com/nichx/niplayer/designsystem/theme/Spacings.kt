package com.nichx.niplayer.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距令牌。
 *
 * 统一页面内外边距、卡片内边距、项间距的取值，避免 magic number。
 */
object NiSpacings {
    val screenOuter = 16.dp
    val screenOuterWide = 24.dp
    val cardInner = 12.dp
    val cardInnerLarge = 16.dp
    val listGap = 8.dp
    val cardGroupGap = 12.dp
    val sectionGap = 16.dp
    val touchTarget = 44.dp
    val touchTargetLarge = 48.dp

    val responsiveScreenOuter: Dp
        @Composable
        @ReadOnlyComposable
        get() = when (LocalNiWindowSizeClass.current.width) {
            NiWindowWidthSizeClass.Compact -> 16.dp
            NiWindowWidthSizeClass.Medium -> 20.dp
            NiWindowWidthSizeClass.Expanded -> 24.dp
        }

    val responsiveListGap: Dp
        @Composable
        @ReadOnlyComposable
        get() = when (LocalNiWindowSizeClass.current.width) {
            NiWindowWidthSizeClass.Compact -> 8.dp
            NiWindowWidthSizeClass.Medium -> 12.dp
            NiWindowWidthSizeClass.Expanded -> 12.dp
        }

    val responsiveCardGroupGap: Dp
        @Composable
        @ReadOnlyComposable
        get() = when (LocalNiWindowSizeClass.current.width) {
            NiWindowWidthSizeClass.Compact -> 12.dp
            NiWindowWidthSizeClass.Medium -> 16.dp
            NiWindowWidthSizeClass.Expanded -> 16.dp
        }
}