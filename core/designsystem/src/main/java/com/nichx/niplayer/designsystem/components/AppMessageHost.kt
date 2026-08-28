package com.nichx.niplayer.designsystem.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.common.error.NiMessageSeverity
import com.nichx.niplayer.common.message.AppMessageController
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * App 级统一 Snackbar 宿主。
 *
 * 应且仅应在 App 根部挂载一次（导航/音乐条之外），收集 [AppMessageController] 的全局消息流。
 * 展示时序采用"**单槽 + 抢占**"模型，解决"上传完成后还在显示正在上传"这类旧消息滞留问题：
 *
 * - **去重**：与当前展示中的消息同内容时丢弃（同一条不重弹）；
 * - **抢占**：新到来一条瞬态消息（INFO/WARNING）会立即顶掉正在展示的瞬态消息——"已上传"
 *   一到就把"正在上传"替换掉，避免串行排队造成的滞后；
 * - **时长可控**：瞬态消息按严重级别设置各自时长（INFO≈2.2s / WARNING≈3.5s），不再用
 *   Material3 固定的 4s；ERROR 常驻直至用户手动关闭；
 * - **错误优先**：ERROR 展示期间忽略普通消息、错误级消息进入备份队列依次展示；
 *
 * @param controller 由 Hilt @Singleton 注入的全局消息控制器。
 * @param bottomObstruction 底部需避让的高度（全局音乐条等）。
 */
@Composable
fun AppMessageHost(
    controller: AppMessageController,
    modifier: Modifier = Modifier,
    bottomObstruction: Dp = NiSnackbarDefaults.MINI_PLAYER_OBSTRUCTION,
) {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 当前展示的一条消息；null 表示空闲。
    var current by remember { mutableStateOf<NiMessage?>(null) }
    // ERROR 级待展示队列（ERROR 常驻，需用户手动关闭，故排队而非抢占）。
    val errorQueue = remember { mutableStateListOf<NiMessage>() }
    // 当前瞬态展示任务，新消息到来时取消它以抢占旧槽位。
    var showJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(controller) {
        controller.messages.collect { msg ->
            if (msg == current) return@collect // 同内容去重
            if (current?.severity == NiMessageSeverity.ERROR) {
                // ERROR 常驻中：普通消息忽略；错误级消息进队（去重）。
                if (msg.severity == NiMessageSeverity.ERROR && errorQueue.none { it == msg }) {
                    errorQueue += msg
                }
                return@collect
            }
            // 抢占：新瞬态消息顶掉旧的瞬态消息。
            showJob?.cancel()
            current = msg
            showJob = scope.launch {
                // 清掉上一槽位的残余展示（如正被抢占的 snackbar）。
                hostState.currentSnackbarData?.dismiss()
                when (msg.severity) {
                    NiMessageSeverity.ERROR -> hostState.showSnackbar(
                        NiMessageVisuals(msg, duration = SnackbarDuration.Indefinite),
                    )
                    NiMessageSeverity.WARNING -> hostState.showControlled(msg, WARNING_DURATION_MS)
                    NiMessageSeverity.INFO -> hostState.showControlled(msg, INFO_DURATION_MS)
                }
                current = null
                // 顺带消费 ERROR 队列。
                while (errorQueue.isNotEmpty()) {
                    val err = errorQueue.removeAt(0)
                    current = err
                    hostState.showSnackbar(
                        NiMessageVisuals(err, duration = SnackbarDuration.Indefinite),
                    )
                }
                current = null
            }
        }
    }

    NiSnackbarHost(
        hostState = hostState,
        modifier = modifier,
        bottomObstruction = bottomObstruction,
    )
}

/** 自动定时消失的展示辅助：以 Indefinite 展示（不依赖 Material3 固定时长），到时后自行关闭。 */
internal suspend fun SnackbarHostState.showControlled(msg: NiMessage, durationMs: Long) =
    coroutineScope {
        val shown = async {
            showSnackbar(NiMessageVisuals(msg, duration = SnackbarDuration.Indefinite))
        }
        delay(durationMs)
        currentSnackbarData?.dismiss()
        shown.await()
    }

private const val INFO_DURATION_MS = 2_200L
private const val WARNING_DURATION_MS = 3_500L