package com.nichx.niplayer.database.backup

import com.nichx.niplayer.common.backup.BackupItem
import com.nichx.niplayer.database.backup.table.EncryptedFolderBackupTable
import com.nichx.niplayer.database.backup.table.ExtendFolderBackupTable
import com.nichx.niplayer.database.backup.table.MediaLibraryBackupTable
import com.nichx.niplayer.database.backup.table.QuickAccessBackupTable
import com.nichx.niplayer.database.backup.table.VideoBookmarkBackupTable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 备份项多绑定 Module。
 *
 * 每个 @IntoSet 绑定对应一个 [BackupItem] 实现类，BackupManager 通过
 * `Set<BackupItem>` 注入全部实例。新增**数据库表**备份项只需在此追加一行 @Binds 即可；
 * 设置类备份项由各自所在模块（如 :core:datastore）自行 @IntoSet 注册。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @IntoSet
    abstract fun bindMediaLibraryTable(impl: MediaLibraryBackupTable): BackupItem

    @Binds
    @IntoSet
    abstract fun bindQuickAccessTable(impl: QuickAccessBackupTable): BackupItem

    @Binds
    @IntoSet
    abstract fun bindVideoBookmarkTable(impl: VideoBookmarkBackupTable): BackupItem

    @Binds
    @IntoSet
    abstract fun bindExtendFolderTable(impl: ExtendFolderBackupTable): BackupItem

    @Binds
    @IntoSet
    abstract fun bindEncryptedFolderTable(impl: EncryptedFolderBackupTable): BackupItem

    // AppSettingsBackup 已随设置层迁至 :core:datastore（A1 修复），
    // 其 @IntoSet 绑定见 core/datastore 的 AppSettingsBackupModule
}
