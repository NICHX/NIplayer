package com.nichx.niplayer.common.error

/**
 * Snackbar 消息严重级别（O-25）。
 *
 * 决定 [com.nichx.niplayer.designsystem.components.NiSnackbarHost] 的容器配色与图标。
 */
enum class NiMessageSeverity {
    /** 错误：红底，需用户感知的失败（连接失败/认证错误/解码失败）。 */
    ERROR,

    /** 警告：琥珀底，非致命异常或提示（已暂停/已跳过）。 */
    WARNING,

    /** 信息：默认 inverseSurface 底，常规反馈（已添加到下载队列/截图已保存）。 */
    INFO,
}

/**
 * 统一 Snackbar 消息模型（O-25）。
 *
 * 替代项目中三套并存的消息传递模式（SharedFlow\<String\> / StateFlow\<String?\> / 多个独立 SharedFlow），
 * 由 ViewModel 单向 emit，UI 层 collect 后交由 [com.nichx.niplayer.designsystem.components.NiSnackbarHost] 渲染。
 *
 * - [severity] 决定配色与图标；
 * - [message] 为主文案（必填）；
 * - [details] 为可展开详情（可选，如异常堆栈/URL），用户点击"详情"展开查看。
 *
 * 可由 [AppError] 便捷构造：`NiMessage.from(AppError.Network("SMB 超时"))`。
 */
data class NiMessage(
    val severity: NiMessageSeverity,
    val message: String,
    val details: String? = null,
) {
    companion object {
        /** 由 [AppError] 构造 [NiMessage]，文案取 [AppError.displayMessage]。 */
        fun from(error: AppError, details: String? = null): NiMessage {
            val severity = when (error.type) {
                ErrorType.NETWORK,
                ErrorType.AUTH,
                ErrorType.STORAGE,
                ErrorType.DATABASE -> NiMessageSeverity.ERROR

                ErrorType.FILE,
                ErrorType.DECODE,
                ErrorType.UNKNOWN -> NiMessageSeverity.WARNING
            }
            return NiMessage(
                severity = severity,
                message = error.displayMessage,
                details = details ?: error.cause?.message,
            )
        }

        /** 便捷构造信息级消息。 */
        fun info(message: String, details: String? = null): NiMessage =
            NiMessage(NiMessageSeverity.INFO, message, details)

        /** 便捷构造错误级消息。 */
        fun error(message: String, details: String? = null): NiMessage =
            NiMessage(NiMessageSeverity.ERROR, message, details)

        /** 便捷构造警告级消息。 */
        fun warning(message: String, details: String? = null): NiMessage =
            NiMessage(NiMessageSeverity.WARNING, message, details)
    }
}
