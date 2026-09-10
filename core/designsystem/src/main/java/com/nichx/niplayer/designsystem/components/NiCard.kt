package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NiCard(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val extraColors = NiExtraColors.current
    Box(
        modifier = modifier
            .then(
                if (!extraColors.isDark) Modifier.shadow(2.dp, shape, clip = false)
                else Modifier
            )
            .clip(shape)
            .background(extraColors.surfaceLevel2)
            .border(1.dp, extraColors.outlineSoft, shape)
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                    )
                else Modifier
            ),
    ) {
        content()
    }
}

@Composable
fun NiSettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val extraColors = NiExtraColors.current
    Box(
        modifier = modifier
            .then(
                if (!extraColors.isDark) Modifier.shadow(2.dp, shape, clip = false)
                else Modifier
            )
            .clip(shape)
            .background(extraColors.surfaceLevel2)
            .border(1.dp, extraColors.outlineSoft, shape),
    ) {
        content()
    }
}
