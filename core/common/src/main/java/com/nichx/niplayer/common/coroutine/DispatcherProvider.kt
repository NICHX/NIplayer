package com.nichx.niplayer.common.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * 协程调度器提供者抽象。
 *
 * 项目硬性约束：所有阻塞型协程操作必须通过 [io] 调度，禁止在主线程执行 IO。
 * 通过接口抽象便于在单元测试中替换为 [kotlinx.coroutines.test.UnconfinedTestDispatcher]
 * 或 [kotlinx.coroutines.test.StandardTestDispatcher]，避免真实 IO/Default 调度干扰测试时序。
 *
 * 迁移说明：当前仅 [AppCoroutineScope] 与新迁移的游离作用域站点使用本接口；
 * 既有 ViewModel 内的 `Dispatchers.IO` 直引将在后续阶段逐步替换为 [io]。
 */
interface DispatcherProvider {
    /** 主线程调度器，用于 UI 操作与 Compose 状态写入。 */
    val main: CoroutineDispatcher

    /** IO 调度器，用于网络/磁盘/数据库等阻塞操作。 */
    val io: CoroutineDispatcher

    /** 默认调度器，用于 CPU 密集型计算（排序、解析、位图处理等）。 */
    val default: CoroutineDispatcher
}

/**
 * 生产环境 [DispatcherProvider] 实现，直接委托 [Dispatchers]。
 *
 * 注：需 public 以供 Hilt @Binds 暴露（Kotlin 不允许 public 函数暴露 internal 参数类型）；
 * 实际构造由 Hilt 管理，外部应注入 [DispatcherProvider] 接口而非本类。
 */
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
