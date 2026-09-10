package com.nichx.niplayer.common.message

import com.nichx.niplayer.common.error.AppError
import com.nichx.niplayer.common.error.NiMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
 */
@Singleton
class AppMessageController @Inject constructor() {

    private val _messages = MutableSharedFlow<NiMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** App 级待展示消息流，由根宿主收集渲染。 */
    val messages: SharedFlow<NiMessage> = _messages.asSharedFlow()

    /** 发送一条消息（挂起）。VM 内 `viewModelScope.launch { controller.show(...) }` 使用。 */
    suspend fun show(message: NiMessage) {
        _messages.emit(message)
    }

    /** 非挂起发送（UI 点击回调等无协程作用域处使用），返回是否入队成功。 */
    fun post(message: NiMessage): Boolean = _messages.tryEmit(message)

    /** 信息级（动态文案版）。 */
    suspend fun showInfo(message: String, details: String? = null) =
        _messages.emit(NiMessage.info(message, details))

    /** 错误级（动态文案版）。 */
    suspend fun showError(message: String, details: String? = null) =
        _messages.emit(NiMessage.error(message, details))

    /** 错误级（由 [AppError] 构造）。 */
    suspend fun showError(error: AppError, details: String? = null) =
        _messages.emit(NiMessage.from(error, details))

    /** 警告级（动态文案版）。 */
    suspend fun showWarning(message: String, details: String? = null) =
        _messages.emit(NiMessage.warning(message, details))

    /** 信息级（非挂起，UI 回调用）。 */
    fun postInfo(message: String, details: String? = null): Boolean =
        _messages.tryEmit(NiMessage.info(message, details))

    /** 错误级（非挂起，UI 回调用）。 */
    fun postError(message: String, details: String? = null): Boolean =
        _messages.tryEmit(NiMessage.error(message, details))

    /** 警告级（非挂起，UI 回调用）。 */
    fun postWarning(message: String, details: String? = null): Boolean =
        _messages.tryEmit(NiMessage.warning(message, details))
}