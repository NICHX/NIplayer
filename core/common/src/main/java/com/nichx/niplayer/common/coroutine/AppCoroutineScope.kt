package com.nichx.niplayer.common.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * 应用级 [CoroutineScope]，生命周期与进程一致。
 *
 * 用于替代项目中的游离 `CoroutineScope(Dispatchers.IO).launch { }`（O-13）：
 * - 这些任务需要存活到进程结束（如 [com.nichx.niplayer.feature.home.PlayStarter] 后台构造播放列表、
 *   [com.nichx.niplayer.feature.player.PlayerViewModel.onCleared] 保存播放进度），
 *   既不属于 [androidx.lifecycle.viewModelScope]（已取消），也不应各自新建无取消入口的 scope。
 * - 统一注入本作用域后，进程退出时由系统回收，便于排查与统一取消。
 *
 * 使用 [SupervisorJob] 保证单个子协程异常不会取消其他子协程；
 * 调度器来自 [DispatcherProvider.io]，遵守"阻塞操作走 IO"硬性约束。
 *
 * 注意：本作用域仅用于必须存活到进程结束的后台任务；ViewModel 内任务应继续使用
 * [androidx.lifecycle.viewModelScope]，Composable 内任务应使用 `rememberCoroutineScope()`。
 */
interface AppCoroutineScope : CoroutineScope

/**
 * 生产环境 [AppCoroutineScope] 实现。
 *
 * 构造时建立 [SupervisorJob] + [DispatcherProvider.io] 的 [CoroutineScope]，
 * 由于与进程同生命周期，不提供取消入口（进程结束时自动回收）。
 */
@Singleton
class AppCoroutineScopeImpl @Inject constructor(
    dispatcherProvider: DispatcherProvider,
) : AppCoroutineScope {

    override val coroutineContext: CoroutineContext =
        SupervisorJob() + dispatcherProvider.io
}
