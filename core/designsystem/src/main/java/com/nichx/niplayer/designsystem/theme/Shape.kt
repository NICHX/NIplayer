package com.nichx.niplayer.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用形状定义。
 *
 * Material 3 默认圆角偏大（medium=12dp, large=16dp, extraLarge=28dp），
 * Niplayer 风格偏硬朗：所有圆角减半，按钮直角化，更适合视频播放器定位。
 *
 * M3 默认 → Niplayer：
 * - extraSmall: 4dp → 2dp
 * - small:      8dp → 4dp
 * - medium:     12dp → 8dp
 * - large:      16dp → 12dp
 * - extraLarge: 28dp → 16dp
 */
val NiShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)
