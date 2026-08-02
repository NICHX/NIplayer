package com.nichx.niplayer.designsystem.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NiWindowWidthSizeClass { Compact, Medium, Expanded }

enum class NiWindowHeightSizeClass { Compact, Medium, Expanded }

data class NiWindowSizeClass(
    val width: NiWindowWidthSizeClass,
    val height: NiWindowHeightSizeClass,
) {
    val isCompactWidth: Boolean get() = width == NiWindowWidthSizeClass.Compact
    val isMediumWidth: Boolean get() = width == NiWindowWidthSizeClass.Medium
    val isExpandedWidth: Boolean get() = width == NiWindowWidthSizeClass.Expanded
}

fun computeNiWindowSizeClass(
    widthDp: Dp,
    heightDp: Dp,
): NiWindowSizeClass {
    val widthClass = when {
        widthDp < 600.dp -> NiWindowWidthSizeClass.Compact
        widthDp < 840.dp -> NiWindowWidthSizeClass.Medium
        else -> NiWindowWidthSizeClass.Expanded
    }
    val heightClass = when {
        heightDp < 480.dp -> NiWindowHeightSizeClass.Compact
        heightDp < 900.dp -> NiWindowHeightSizeClass.Medium
        else -> NiWindowHeightSizeClass.Expanded
    }
    return NiWindowSizeClass(width = widthClass, height = heightClass)
}

val LocalNiWindowSizeClass = compositionLocalOf {
    NiWindowSizeClass(NiWindowWidthSizeClass.Compact, NiWindowHeightSizeClass.Compact)
}
