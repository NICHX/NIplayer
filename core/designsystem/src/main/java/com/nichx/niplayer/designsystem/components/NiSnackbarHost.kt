package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.common.error.NiMessageSeverity
import kotlinx.coroutines.delay

/**
 * 统一通知配置。
 */
object NiSnackbarDefaults {

    /**
     * 底部悬浮迷你播放器（MusicBar）在默认位置时需要抬升的高度。
     * 全局 MusicBar 位于非播放器页面右下角，底部通知统一抬升该距离避免重叠。
     */
    val MINI_PLAYER_OBSTRUCTION: Dp = 80.dp
}

/**
 * [NiMessage] 到 Material3 [SnackbarVisuals] 的映射。
 *
 * - ERROR → [SnackbarDuration.Indefinite]：常驻直至用户手动关闭；
 * - INFO / WARNING → [SnackbarDuration.Short]：约 4 秒后自动消失。
 */
private class NiMessageVisuals(
    val niMessage: NiMessage,
    override val actionLabel: String? = null,
) : SnackbarVisuals {

    override val message: String = niMessage.message

    override val withDismissAction: Boolean = false

    override val duration: SnackbarDuration =
        if (niMessage.severity == NiMessageSeverity.ERROR) SnackbarDuration.Indefinite
        else SnackbarDuration.Short
}

/**
 * 在 [SnackbarHostState] 上以统一样式展示一条 [NiMessage]。
 *
 * 底层复用 Material3 的 [SnackbarHostState.showSnackbar] 队列语义：
 * 消息依次排队、自动/手动消失，action 结果通过 [SnackbarResult] 返回。
 *
 * @param actionLabel 可选操作按钮文案（如"撤销"），点击后返回 [SnackbarResult.ActionPerformed]。
 */
suspend fun SnackbarHostState.showNiMessage(
    message: NiMessage,
    actionLabel: String? = null,
): SnackbarResult = showSnackbar(NiMessageVisuals(message, actionLabel))

/**
 * 统一通知宿主（底部对齐 + 自动避让）。
 *
 * 所有页面统一在屏幕底部展示通知，通过 [bottomObstruction] 抬升以避开
 * 底部悬浮元素（全局 MusicBar、页面底部操作栏等）。
 *
 * @param hostState 由页面创建并持有，可直接调用 [showNiMessage] / [SnackbarHostState.showSnackbar]。
 * @param bottomObstruction 底部需要避让的高度（如 [NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION]）。
 */
@Composable
fun NiSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomObstruction: Dp = 0.dp,
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

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = hostState,
            modifier = modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = navBarInsetDp + 12.dp + bottomObstruction),
            snackbar = { data -> NiMessageSnackbar(data) },
        )
    }
}

/**
 * 基于 [NiSnackbarController] 的统一通知宿主。
 *
 * 收集 [NiSnackbarController.messages] 后经 [SnackbarHostState] 依次展示，
 * 与直接调用 [showNiMessage] 保持一致的渲染与消失逻辑；同内容消息正在展示时自动去重。
 *
 * @param hostState 由页面创建并持有，便于需要 [SnackbarResult] 的调用（如撤销操作）直接使用。
 */
@Composable
fun NiSnackbarHost(
    hostState: SnackbarHostState,
    controller: NiSnackbarController,
    modifier: Modifier = Modifier,
    bottomObstruction: Dp = 0.dp,
) {
    LaunchedEffect(controller) {
        controller.messages.collect { msg ->
            val current = hostState.currentSnackbarData?.visuals
            if (current !is NiMessageVisuals || current.niMessage != msg) {
                hostState.showNiMessage(msg)
            }
        }
    }
    NiSnackbarHost(
        hostState = hostState,
        modifier = modifier,
        bottomObstruction = bottomObstruction,
    )
}

/**
 * 便捷重载：内部持有 [SnackbarHostState]，仅需事件驱动的页面直接使用。
 */
@Composable
fun NiSnackbarHost(
    controller: NiSnackbarController,
    modifier: Modifier = Modifier,
    bottomObstruction: Dp = 0.dp,
) {
    val hostState = remember { SnackbarHostState() }
    NiSnackbarHost(
        hostState = hostState,
        controller = controller,
        modifier = modifier,
        bottomObstruction = bottomObstruction,
    )
}

/**
 * 统一通知卡片。
 *
 * 内容自适应宽度（最长 480dp），支持 [NiMessageSeverity] 配色与图标、可展开详情、
 * 点击消息文字复制、关闭；对非 [NiMessageVisuals] 的裸 [SnackbarVisuals]（如带 action
 * 的 [SnackbarHostState.showSnackbar]）自动回退为 INFO 样式并保留其 action 按钮。
 */
@Composable
private fun NiMessageSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    val niMessage = (visuals as? NiMessageVisuals)?.niMessage
    val messageText = niMessage?.message ?: visuals.message
    val severity = niMessage?.severity ?: NiMessageSeverity.INFO
    val details = niMessage?.details
    val actionLabel = visuals.actionLabel

    var detailsExpanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val (containerColor, contentColor, icon) = when (severity) {
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

    val copyContent = buildString {
        append(messageText)
        if (!details.isNullOrBlank()) append('\n').append(details)
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = if (details.isNullOrBlank()) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(
                        onClick = {
                            clipboard.setText(AnnotatedString(copyContent))
                            copied = true
                        },
                    )
                    .padding(vertical = 2.dp),
            )
            if (copied) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "已复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                }
            }
            if (actionLabel != null) {
                TextButton(
                    onClick = { data.performAction() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                    )
                }
            }
            if (!details.isNullOrBlank()) {
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = if (detailsExpanded) "收起" else "详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                }
            }
            IconButton(
                onClick = { data.dismiss() },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = detailsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = details.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}
