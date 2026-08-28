package com.nichx.niplayer.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/**
 * 弹出菜单 — 磨砂卡片风格（主题自适应）。
 *
 * - 20dp 大圆角 + 不透明磨砂底色 + 细边框 + 阴影
 * - 选中项 primaryContainer 背景高亮
 * - 项高 44dp，间距 2dp
 * - fadeIn + scaleIn(0.92→1.0) 入场动画
 *
 * 菜单位于独立 Popup 窗口，底色统一走 [niFrostSurfaceColor]（不透明），
 * 避免半透明底色把窗口垫层透出来形成多余浅色矩形。
 */
@Composable
fun NiPopupMenu(
    visible: Boolean,
    items: List<NiPopupMenuItem>,
    selectedIndex: Int = -1,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut(animationSpec = tween(100)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(100, easing = FastOutSlowInEasing),
                ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = niGlassPanelSurfaceColor(),
            shadowElevation = 8.dp,
            border = BorderStroke(NiGlassHairWidth, niGlassBorderColor()),
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 140.dp, max = 220.dp)
                    .padding(vertical = 4.dp),
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    PopupMenuItemRow(
                        item = item,
                        isSelected = isSelected,
                        onClick = item.onClick,
                    )
                    if (index < items.size - 1) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupMenuItemRow(
    item: NiPopupMenuItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val textOnSurface = if (NiExtraColors.current.isDark) Color(0xFFE8E8EA) else Color(0xFF1C1B1F)
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val textColor = if (isSelected) onPrimaryContainer else textOnSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isSelected) primaryColor else textOnSurface,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 10.dp),
            )
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 10.dp),
            )
        } else {
            Spacer(Modifier.width(30.dp))
        }
        Text(
            text = item.label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

data class NiPopupMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
)
