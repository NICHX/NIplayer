package com.nichx.niplayer.common

import com.nichx.niplayer.common.coroutine.AppCoroutineScope
import com.nichx.niplayer.common.coroutine.AppCoroutineScopeImpl
import com.nichx.niplayer.common.coroutine.DefaultDispatcherProvider
import com.nichx.niplayer.common.coroutine.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * :core:common Hilt 模块。
 *
 * 通过 [@Binds][Binds] 暴露接口实现，便于测试替换（项目约定：Hilt modules use @Binds
 * for interface implementations to enable easy testing/swap）。
 *
 * 提供：
 * - [DispatcherProvider] → [DefaultDispatcherProvider]
 * - [AppCoroutineScope] → [AppCoroutineScopeImpl]
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindAppCoroutineScope(impl: AppCoroutineScopeImpl): AppCoroutineScope
}
