package com.nichx.niplayer.common.message

import com.nichx.niplayer.common.error.AppError
import com.nichx.niplayer.common.error.NiMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App 级全局消息总线（Snackbar 统一通道）。
 *
 * 替代此前"每个页面各自持有 SnackbarHostState + 各自 collect VM 事件"的做法：
 * - 单例存活于整个 App 生命周期，**跨导航/页面切换不丢消息**（后台扫描、下载、同步等
 *   在用户离开页面后才 completion 的结果也能正确送达）；
 * - 由 App 根部单一的 AppMessageHost 收集渲染，页面不再自建宿主；
 * - 支持同内容去重（由宿主在显示层处理）与错误级优先排队。
 *
 * 系统级页面通知统一经此通道；播放器内的即时 OSD（手势/切集/HDR 等）不属于本类职责，
 * 仍由播放器自绘覆盖层负责。
 *
 * ## 为什么用 [Channel] 而不是 `MutableSharedFlow(replay = 0)`
 *
 * 原实现用 `MutableSharedFlow(replay = 0, extraBufferCapacity = 64, DROP_OLDEST)`。
 * SharedFlow 的 `replay` 是"给后来订阅者重放的历史条数"，为 0 时**没有订阅者期间发射的消息
 * 会被直接丢弃** —— 与本类"跨导航/页面切换不丢消息"的契约不符：Activity 被销毁重建
 * （配置变更、从后台恢复）期间恰好没有订阅者，那段时间内完成的下载/同步/扫描结果会静默消失。
 *
 * 改为 [Channel] 后：
 * - 消息**缓冲在通道里直到有人消费**，宿主重新挂载后会照常收到；
 * - 每条消息**恰好投递一次**，不会像 `replay = 1` 那样让重新订阅的宿主重复弹上一条；
 * - [BufferOverflow.DROP_OLDEST] 保留原有"满则丢最旧"的降级行为，`send`/`trySend` 都不会阻塞
 *   调用方（避免在无消费者时挂住 ViewModel 协程）。
 *
 * 注意：[messages] 由单一宿主收集（[receiveAsFlow] 是热流，多个收集者会竞争同一条消息），
 * 这与"App 根部仅挂载一次"的设计一致。
 */
@Singleton
class AppMessageController @Inject constructor() {

    private val channel = Channel<NiMessage>(
        capacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** App 级待展示消息流，由根宿主收集渲染。 */
    val messages: Flow<NiMessage> = channel.receiveAsFlow()

    /** 发送一条消息（挂起）。VM 内 `viewModelScope.launch { controller.show(...) }` 使用。 */
    suspend fun show(message: NiMessage) {
        channel.send(message)
    }

    /** 非挂起发送（UI 点击回调等无协程作用域处使用），返回是否入队成功。 */
    fun post(message: NiMessage): Boolean = channel.trySend(message).isSuccess

    /** 信息级（动态文案版）。 */
    suspend fun showInfo(message: String, details: String? = null) =
        channel.send(NiMessage.info(message, details))

    /** 错误级（动态文案版）。 */
    suspend fun showError(message: String, details: String? = null) =
        channel.send(NiMessage.error(message, details))

    /** 错误级（由 [AppError] 构造）。 */
    suspend fun showError(error: AppError, details: String? = null) =
        channel.send(NiMessage.from(error, details))

    /** 警告级（动态文案版）。 */
    suspend fun showWarning(message: String, details: String? = null) =
        channel.send(NiMessage.warning(message, details))

    /** 信息级（非挂起，UI 回调用）。 */
    fun postInfo(message: String, details: String? = null): Boolean =
        channel.trySend(NiMessage.info(message, details)).isSuccess

    /** 错误级（非挂起，UI 回调用）。 */
    fun postError(message: String, details: String? = null): Boolean =
        channel.trySend(NiMessage.error(message, details)).isSuccess

    /** 警告级（非挂起，UI 回调用）。 */
    fun postWarning(message: String, details: String? = null): Boolean =
        channel.trySend(NiMessage.warning(message, details)).isSuccess

    private companion object {
        /** 与原先 SharedFlow 的 `extraBufferCapacity` 保持一致。 */
        const val BUFFER_CAPACITY = 64
    }
}
