package com.nichx.niplayer.player.kernel.di

import com.nichx.niplayer.player.kernel.NxPlayer
import com.nichx.niplayer.player.kernel.media3.NxMedia3Player
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * :player:kernel Hilt 绑定模块。
 *
 * 不加 @Singleton：调用方（PlayerViewModel / PlayerService）按需 @Inject 创建实例，
 * 持有引用并在销毁时调用 [NxPlayer.release]。
 * 一个屏幕一个 Player 实例，避免跨页面共享已释放的 Player。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    abstract fun bindNxPlayer(impl: NxMedia3Player): NxPlayer
}
