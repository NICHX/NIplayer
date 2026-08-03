package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.common.error.NiMessageSeverity
import kotlinx.coroutines.flow.collect


@Composable
fun NiSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    topAligned: Boolean = false,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val navBarInsetDp: Dp = with(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.navigationBars())
                ?.bottom
                ?.toDp() ?: 0.dp
        } else {
            @Suppress("DEPRECATION")
            view.rootWindowInsets?.systemWindowInsetBottom?.toDp() ?: 0.dp
        }
    }
    val statusBarInsetDp: Dp = with(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.statusBars())
                ?.top
                ?.toDp() ?: 0.dp
        } else {
            @Suppress("DEPRECATION")
            view.rootWindowInsets?.systemWindowInsetTop?.toDp() ?: 0.dp
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = hostState,
            modifier = if (topAligned) {
                modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarInsetDp + 12.dp)
            } else {
                modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = maxOf(navBarInsetDp, bottomPadding))
            },
            snackbar = { data -> NiSnackbar(data) },
        )
    }
}

@Composable
private fun NiSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionColor = MaterialTheme.colorScheme.inversePrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

// region O-25：基于 NiSnackbarController 的统一消息渲染（支持严重级别 + 可展开详情）

/**
 * 基于 [NiSnackbarController] 的统一 Snackbar 宿主（O-25）。
 *
 * 相比 [NiSnackbarHost]（仅 String 消息），本重载支持：
 * - [NiMessageSeverity] 配色与图标（Error 红 / Warning 琥珀 / Info 默认）；
 * - [NiMessage.details] 可展开详情，用户点击"详情"按钮展开/收起。
 *
 * 与 [NiSnackbarHost] 并存，便于既有页面逐步迁移，新页面优先使用本重载。
 */
@Composable
fun NiSnackbarHost(
    controller: NiSnackbarController,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    topAligned: Boolean = false,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val navBarInsetDp: Dp = with(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.navigationBars())
                ?.bottom
                ?.toDp() ?: 0.dp
        } else {
            @Suppress("DEPRECATION")
            view.rootWindowInsets?.systemWindowInsetBottom?.toDp() ?: 0.dp
        }
    }
    val statusBarInsetDp: Dp = with(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.statusBars())
                ?.top
                ?.toDp() ?: 0.dp
        } else {
            @Suppress("DEPRECATION")
            view.rootWindowInsets?.systemWindowInsetTop?.toDp() ?: 0.dp
        }
    }

    // 当前展示的消息（单条队列，后续消息覆盖前一条，与 SnackbarHost 语义一致）
    var current by remember { mutableStateOf<NiMessage?>(null) }
    LaunchedEffect(controller) {
        controller.messages.collect { msg -> current = msg }
    }

    current?.let { msg ->
        Box(modifier = Modifier.fillMaxSize()) {
            NiMessageSnackbar(
                message = msg,
                onDismiss = { current = null },
                modifier = if (topAligned) {
                    modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = statusBarInsetDp + 12.dp)
                } else {
                    modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = maxOf(navBarInsetDp, bottomPadding))
                },
            )
        }
    }
}

@Composable
private fun NiMessageSnackbar(
    message: NiMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    val hasDetails = !message.details.isNullOrBlank()

    val (containerColor, contentColor, icon: ImageVector?) = when (message.severity) {
        NiMessageSeverity.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.Error,
        )
        NiMessageSeverity.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.Warning,
        )
        NiMessageSeverity.INFO -> Triple(
            MaterialTheme.colorScheme.inverseSurface,
            MaterialTheme.colorScheme.inverseOnSurface,
            Icons.Filled.Info,
        )
    }

    // 自动消失：INFO/WARNING 3s，ERROR 5s
    LaunchedEffect(message) {
        val durationMs = if (message.severity == NiMessageSeverity.ERROR) 5000L else 3000L
        kotlinx.coroutines.delay(durationMs)
        onDismiss()
    }

    Column(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (hasDetails) {
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        text = if (detailsExpanded) "收起" else "详情",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = hasDetails && detailsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = message.details.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

// endregion