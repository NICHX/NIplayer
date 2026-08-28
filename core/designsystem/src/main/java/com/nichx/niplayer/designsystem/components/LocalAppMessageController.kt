package com.nichx.niplayer.designsystem.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.nichx.niplayer.common.message.AppMessageController

/**
 * App 级消息控制器 [AppMessageController] 的 CompositionLocal，在 App 根（MainActivity）提供。
 *
 * 各页面读取后把 Snackbar 消息 `post`/`show` 进全局总线，由根部单一 [AppMessageHost] 渲染，
 * 而不再各自持有 [androidx.compose.material3.SnackbarHostState]。
 */
val LocalAppMessageController = staticCompositionLocalOf<AppMessageController> {
    error("LocalAppMessageController 未提供：请在 App 根节点提供该 CompositionLocal")
}