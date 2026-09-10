package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶栏/悬浮**圆形灰色图标按钮**：与底部导航栏 pill 同款灰色
 * （[LocalNiGlassOpacity] 跟随底栏不透明度）+ tertiary 图标，保证视觉统一。
 *
 * 用于无法安全使用 drawBackdrop 真模糊的场景（如首页顶栏、媒体库 FAB 等位于
 * backdrop 捕获层内部的位置）——纯灰底、无模糊，性能开销极小。
 */
@Composable
fun NiGlassCircleIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = LocalNiGlassOpacity.current),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}
