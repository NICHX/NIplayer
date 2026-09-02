package com.nichx.niplayer.player.mpv.di

import com.nichx.niplayer.player.kernel.NxPlayerBackend
import com.nichx.niplayer.player.mpv.NxMpvPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * :player:mpv 的多内核注册。
 *
 * 把 [NxMpvPlayer] 以 `@IntoSet` 方式加入 [NxPlayerBackend] 多绑定集合，
 * 与 :player:kernel 的 media3 后端并列，供 [com.nichx.niplayer.player.kernel.NxPlayerProvider]
 * 能力解析。骨架阶段 [NxMpvPlayer.supports] 恒为 false，media3 仍被选中，不干扰现有播放。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MpvModule {

    @Binds
    @IntoSet
    abstract fun bindMpvBackend(impl: NxMpvPlayer): NxPlayerBackend
}