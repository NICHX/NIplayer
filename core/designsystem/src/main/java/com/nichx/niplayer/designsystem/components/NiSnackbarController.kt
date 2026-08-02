package com.nichx.niplayer.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nichx.niplayer.common.error.AppError
import com.nichx.niplayer.common.error.NiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 统一 Snackbar 控制器（O-25）。
 *
 * 持有 [NiMessage] 事件流，由 ViewModel 单向 emit、UI 层 collect 后交由
 * [NiSnackbarHost] 渲染。替代项目中三套并存的消息传递模式
 * （SharedFlow\<String\> / StateFlow\<String?\> / 多个独立 SharedFlow）。
 *
 * 使用方式：
 * ```
 * @Composable
 * fun FooScreen() {
 *     val snackbarController = rememberNiSnackbarController()
 *     // ...
 *     NiSnackbarHost(controller = snackbarController)
 * }
 *
 * // ViewModel 侧：
 * fun fail() = viewModelScope.launch {
 *     try { ... }
 *     catch (e: Exception) {
 *         _messages.emit(NiMessage.from(AppError.from(e)))
 *     }
 * }
 * ```
 *
 * 注意：本类为纯 Kotlin 类（非 Compose State），可在 ViewModel 中持有并 emit；
 * [rememberNiSnackbarController] 用于在 Composable 中创建并跨重组保持实例。
 */
class NiSnackbarController {

    private val _messages = MutableSharedFlow<NiMessage>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    /** 待展示的消息流，UI 层 collect 后渲染。 */
    val messages: SharedFlow<NiMessage> = _messages.asSharedFlow()

    /** 发送一条 [NiMessage]。 */
    suspend fun show(message: NiMessage) {
        _messages.emit(message)
    }

    /** 发送一条信息级消息。 */
    suspend fun showInfo(message: String, details: String? = null) {
        show(NiMessage.info(message, details))
    }

    /** 发送一条错误级消息。 */
    suspend fun showError(message: String, details: String? = null) {
        show(NiMessage.error(message, details))
    }

    /** 由 [AppError] 构造并发送消息。 */
    suspend fun showError(error: AppError, details: String? = null) {
        show(NiMessage.from(error, details))
    }

    /** 发送一条警告级消息。 */
    suspend fun showWarning(message: String, details: String? = null) {
        show(NiMessage.warning(message, details))
    }
}

/** 在 Composable 中创建并记住 [NiSnackbarController] 实例。 */
@Composable
fun rememberNiSnackbarController(): NiSnackbarController = remember { NiSnackbarController() }
