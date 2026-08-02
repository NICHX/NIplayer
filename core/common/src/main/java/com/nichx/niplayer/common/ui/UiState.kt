package com.nichx.niplayer.common.ui

import com.nichx.niplayer.common.error.AppError

/**
 * 统一列表页 UI 状态封装（O-26）。
 *
 * 规范所有列表页（文件浏览/播放历史/存储源/下载管理/缓存管理）的空状态与加载状态处理：
 * - [Loading]：展示骨架屏（[com.nichx.niplayer.designsystem.components.NiSkeletonBox]）；
 * - [Empty]：展示 [com.nichx.niplayer.designsystem.components.NiEmptyState]；
 * - [Error]：展示错误态 + 重试按钮；
 * - [Success]：展示数据。
 *
 * 替代各 ViewModel 各自定义的 `data class XxxUiState(isLoading: Boolean, ...)` 风格，
 * 统一为四态 sealed class，消除"无加载骨架/空态用纯 Text/加载用 CircularProgressIndicator"等不一致。
 *
 * 使用示例：
 * ```
 * class FooViewModel : ViewModel() {
 *     private val _uiState = MutableStateFlow<UiState<List<Foo>>>(UiState.Loading)
 *     val uiState: StateFlow<UiState<List<Foo>>> = _uiState.asStateFlow()
 *
 *     fun load() = viewModelScope.launch {
 *         _uiState.value = UiState.Loading
 *         _uiState.value = try {
 *             val data = repo.fetch()
 *             if (data.isEmpty()) UiState.Empty else UiState.Success(data)
 *         } catch (e: Exception) {
 *             UiState.Error(AppError.from(e))
 *         }
 *     }
 * }
 * ```
 */
sealed interface UiState<out T> {

    /** 加载中：UI 应展示骨架屏。 */
    data object Loading : UiState<Nothing>

    /** 空数据：UI 应展示 [com.nichx.niplayer.designsystem.components.NiEmptyState]。 */
    data object Empty : UiState<Nothing>

    /** 错误：UI 应展示错误态 + 可选重试。 */
    data class Error(val error: AppError) : UiState<Nothing>

    /** 成功加载数据。 */
    data class Success<T>(val data: T) : UiState<T>
}

/**
 * 将 [UiState] 映射为新数据（保持状态类型，仅 [Success] 时变换 data）。
 */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    UiState.Loading -> UiState.Loading
    UiState.Empty -> UiState.Empty
    is UiState.Error -> this
    is UiState.Success -> UiState.Success(transform(data))
}
