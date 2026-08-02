package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class NiFabVariant {
    PRIMARY,
    TONAL,
    OUTLINED,
}

@Composable
fun NiFAB(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    variant: NiFabVariant = NiFabVariant.TONAL,
) {
    val containerColor = when (variant) {
        NiFabVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        NiFabVariant.TONAL -> MaterialTheme.colorScheme.primaryContainer
        NiFabVariant.OUTLINED -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (variant) {
        NiFabVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        NiFabVariant.TONAL -> MaterialTheme.colorScheme.onPrimaryContainer
        NiFabVariant.OUTLINED -> MaterialTheme.colorScheme.primary
    }
    val borderModifier = when (variant) {
        NiFabVariant.OUTLINED -> Modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            CircleShape,
        )
        NiFabVariant.TONAL -> if (isSystemInDarkTheme()) {
            Modifier.border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                CircleShape,
            )
        } else {
            Modifier
        }
        NiFabVariant.PRIMARY -> Modifier
    }
    // Outlined 风格为平面样式，消除默认 elevation 阴影与描边之间的圆角错位
    val elevation = if (variant == NiFabVariant.OUTLINED) {
        FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        )
    } else {
        FloatingActionButtonDefaults.elevation()
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.then(borderModifier),
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun NiExtendedFAB(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NiFabVariant = NiFabVariant.TONAL,
) {
    val containerColor = when (variant) {
        NiFabVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        NiFabVariant.TONAL -> MaterialTheme.colorScheme.primaryContainer
        NiFabVariant.OUTLINED -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (variant) {
        NiFabVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        NiFabVariant.TONAL -> MaterialTheme.colorScheme.onPrimaryContainer
        NiFabVariant.OUTLINED -> MaterialTheme.colorScheme.primary
    }
    // 描边与按钮容器共用同一 shape，保证圆角完全一致
    val fabShape = RoundedCornerShape(16.dp)
    val borderModifier = when (variant) {
        NiFabVariant.OUTLINED -> Modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            fabShape,
        )
        NiFabVariant.TONAL -> if (isSystemInDarkTheme()) {
            Modifier.border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                fabShape,
            )
        } else {
            Modifier
        }
        NiFabVariant.PRIMARY -> Modifier
    }
    // Outlined 风格为平面样式，消除默认 elevation 阴影与描边之间的圆角错位
    val elevation = if (variant == NiFabVariant.OUTLINED) {
        FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        )
    } else {
        FloatingActionButtonDefaults.elevation()
    }

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.then(borderModifier),
        shape = fabShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
        text = {
            Text(text = text)
        },
    )
}
