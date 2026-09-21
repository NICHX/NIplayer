package com.nichx.niplayer.datastore.backup

import com.nichx.niplayer.common.backup.BackupItem
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 应用设置备份项的自注册 Module。
 *
 * **A1 架构修复（2026-09-21）**：[AppSettingsBackup] 原先位于 :core:database，使 Room 模块
 * 不得不依赖 :core:datastore。现备份 SPI（`BackupItem` / `RestoreMode`）下移到 :core:common，
 * 实现随设置层走并在本模块自注册，:core:database 不再需要知道设置层的存在。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppSettingsBackupModule {

    @Binds
    @IntoSet
    abstract fun bindAppSettingsBackup(impl: AppSettingsBackup): BackupItem
}
