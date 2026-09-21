package com.nichx.niplayer.di

import com.nichx.niplayer.datastore.AudioSettings
import com.nichx.niplayer.player.kernel.audio.EqualizerConfig
import com.nichx.niplayer.player.kernel.audio.EqualizerConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 应用级音频装配（组合根）。
 *
 * **A1 架构修复（2026-09-21）**：:player:kernel 原先直接读取 :core:datastore 的全局
 * `AudioSettings`，形成「播放内核 → 设置持久化层」的**依赖倒置**。现内核只依赖自身定义的
 * [EqualizerConfigProvider] 抽象，具体实现（读 MMKV）在组合根装配 —— 内核与设置层互不相识。
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioWiringModule {

    /** 把 MMKV 里的均衡器设置适配为内核所需的 [EqualizerConfig]。 */
    @Provides
    fun provideEqualizerConfigProvider(): EqualizerConfigProvider = EqualizerConfigProvider {
        EqualizerConfig(
            enabled = AudioSettings.equalizerEnabled,
            presetIndex = AudioSettings.equalizerPresetIndex,
            bandLevelsMb = List(AudioSettings.BAND_COUNT) { AudioSettings.getBandLevel(it) },
        )
    }
}
