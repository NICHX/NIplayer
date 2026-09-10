package com.nichx.niplayer.designsystem.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.common.error.NiMessageSeverity
import com.nichx.niplayer.designsystem.R
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
 * - INFO / WARNING → [SnackbarDuration.Short]（约 4 秒）。全局宿主 [AppMessageHost] 会改走
 *   按严重级别自定义时长（INFO≈2.2s / WARNING≈3.5s）而非依赖此默认值。
 */
internal class NiMessageVisuals(
    val niMessage: NiMessage,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration =
        if (niMessage.severity == NiMessageSeverity.ERROR) SnackbarDuration.Indefinite
        else SnackbarDuration.Short,
) : SnackbarVisuals {

    override val message: String = niMessage.message

    override val withDismissAction: Boolean = false
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
    // i18n：messageRes 非 0 时经 stringResource 解析（支持占位符），否则回退动态 message 文案
    val messageText = if (niMessage?.messageRes != 0 && niMessage != null) {
        stringResource(niMessage.messageRes, *niMessage.messageArgs.toTypedArray())
    } else {
        niMessage?.message ?: visuals.message
    }
    val severity = niMessage?.severity ?: NiMessageSeverity.INFO
    val details = niMessage?.details
    val actionLabel = visuals.actionLabel

    var detailsExpanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // 玻璃浮层配色：沿用全局玻璃规范（磨砂底 + 发丝描边 + 高对比前景），严重级别用局部强调色区分
    val accentColor = when (severity) {
        NiMessageSeverity.ERROR -> MaterialTheme.colorScheme.error
        NiMessageSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        NiMessageSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (severity) {
        NiMessageSeverity.ERROR -> Icons.Filled.Error
        NiMessageSeverity.WARNING -> Icons.Filled.Warning
        NiMessageSeverity.INFO -> Icons.Filled.Info
    }
    val mutedColor = glassOnSurfaceMuted()

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

    val snackbarShape = RoundedCornerShape(20.dp)
    // 瞬态（INFO/WARNING）自动消失：无关闭按钮、文字居中；常驻 ERROR 保留关闭与左对齐。
    val transient = severity != NiMessageSeverity.ERROR
    // 真磨砂（同弹窗/底栏）：宿主已置于 backdrop 捕获层之外，drawBackdrop 采样主内容做真实模糊，
    // 半透明底色跟随"面板不透明度"设置并混入主题三级色（tertiary）轻着色。
    val backdrop = LocalNiBackdrop.current
    val glassEnabled = LocalNiGlassEnabled.current && backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // 磨砂底向主题三级色轻着色：surfaceContainer → tertiary 混 40%，仍跟随面板不透明度
    val themedSurface = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.tertiary,
        0.4f,
    )
    val panelSurface = themedSurface.copy(alpha = LocalNiGlassPanelOpacity.current)

    Box(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .then(
                if (glassEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop!!,
                        shape = { snackbarShape },
                        effects = { blur(NiGlassSheetBlurRadius.toPx()) },
                        onDrawSurface = { drawRect(panelSurface) },
                    )
                } else {
                    // 无 backdrop 时回退为不透明主题色磨砂卡片
                    Modifier.background(themedSurface, snackbarShape)
                }
            )
            .clip(snackbarShape)
            .border(NiGlassHairWidth, niGlassBorderColor(), snackbarShape),
    ) {
        Row(
            modifier = Modifier
                // 瞬态消息按内容贴合（capsule），ERROR 撑满以承载长文本
                .then(if (!transient) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
            // 消息文字独立占用一行（weight=1f 吸满左侧，仅 ERROR），瞬态 wrap 不加 weight 以免撑宽胶囊
            Column(modifier = Modifier.then(if (!transient) Modifier.weight(1f) else Modifier)) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    // 柔和的半透 onSurface：避免纯黑/纯白的生硬对比，与磨砂底更融合
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                    textAlign = if (transient) TextAlign.Center else TextAlign.Start,
                    maxLines = if (details.isNullOrBlank()) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .then(if (!transient) Modifier.fillMaxWidth() else Modifier)
                        .clickable(
                            onClick = {
                                clipboard.setText(AnnotatedString(copyContent))
                                copied = true
                            },
                        )
                        .padding(vertical = 2.dp),
                )
                val hasControls = copied || actionLabel != null || !details.isNullOrBlank()
                if (hasControls) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (copied) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.copied),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                            )
                        }
                        if (actionLabel != null) {
                            TextButton(
                                onClick = { data.performAction() },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    text = actionLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accentColor,
                                )
                            }
                        }
                        if (!details.isNullOrBlank()) {
                            TextButton(
                                onClick = { detailsExpanded = !detailsExpanded },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    text = if (detailsExpanded) stringResource(R.string.collapse)
                                    else stringResource(R.string.details),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = mutedColor,
                                )
                            }
                        }
                    }
                }
            }
            // 关闭按钮右对齐：仅常驻 ERROR 显示（INFO/WARNING 自动消失无需手动关闭）
            if (!transient) {
                IconButton(
                    onClick = { data.dismiss() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mutedColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
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
                color = mutedColor.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 56.dp, bottom = 12.dp),
            )
        }
    }
}
