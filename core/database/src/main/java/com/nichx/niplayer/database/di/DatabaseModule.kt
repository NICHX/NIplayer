package com.nichx.niplayer.database.di

import android.content.Context
import androidx.room.Room
import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.dao.UploadTaskDao
import com.nichx.niplayer.database.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * :core:database 的 Hilt Module。
 *
 * 提供 [NiplayerDatabase] 单例与各 Dao。
 *
 * 迁移策略：注册 v6 → v21 的**完整**迁移链（见 [NiplayerDatabase] 的 companion object），
 * 并只在 DB 版本 1~5 上允许破坏性重建。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNiplayerDatabase(@ApplicationContext ctx: Context): NiplayerDatabase =
        Room.databaseBuilder(ctx, NiplayerDatabase::class.java, NiplayerDatabase.DATABASE_NAME)
            // 迁移列表的**唯一来源**是 NiplayerDatabase.ALL_MIGRATIONS —— 切勿在此逐个列举。
            // 逐个列举的代价是：新增迁移时极易漏登记，而编译期与 MigrationTest 都发现不了，
            // 直到用户升级时抛「A migration from X to Y was required but not found」崩溃
            // （2026-09-25 移除书签功能时实际发生过）。
            .addMigrations(*NiplayerDatabase.ALL_MIGRATIONS)
            // 破坏性回退范围收窄：原先的无参 fallbackToDestructiveMigration(true) 允许 Room 在
            // **任何**找不到迁移路径的情况下静默删除整库重建 —— 只要将来某次 DB 版本提升忘记补迁移，
            // 所有存量用户的媒体库配置、播放历史与进度、书签、加密目录记录、下载/上传任务、
            // 云同步 tombstone 都会无声消失，且没有任何崩溃或日志。
            //
            // 现改为只对 v1~v5 允许破坏性重建（本仓库的迁移链从 v6 开始，这 5 个版本确实无路径可达），
            // 其它任何版本缺口都会抛出 IllegalStateException 使问题在开发/测试阶段立刻暴露。
            // Room 文档亦明确推荐这种做法：既允许对特定版本破坏重建，又保留"漏写迁移即抛异常"的保护。
            //
            // 约束：传入的版本号不得同时出现在任何已注册 Migration 的起止版本中（否则 Room 抛异常）。
            // 1~5 均未被任何迁移引用，符合该约束。
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5)
            .build()

    @Provides
    fun provideVideoDao(db: NiplayerDatabase): VideoDao = db.getVideoDao()

    @Provides
    fun provideMediaLibraryDao(db: NiplayerDatabase): MediaLibraryDao = db.getMediaLibraryDao()

    @Provides
    fun providePlayHistoryDao(db: NiplayerDatabase): PlayHistoryDao = db.getPlayHistoryDao()

    @Provides
    fun provideExtendFolderDao(db: NiplayerDatabase): ExtendFolderDao = db.getExtendFolderDao()

    @Provides
    fun provideDownloadTaskDao(db: NiplayerDatabase): DownloadTaskDao = db.getDownloadTaskDao()

    @Provides
    fun provideUploadTaskDao(db: NiplayerDatabase): UploadTaskDao = db.getUploadTaskDao()

    @Provides
    fun provideQuickAccessDao(db: NiplayerDatabase): QuickAccessDao = db.getQuickAccessDao()

    @Provides
    fun provideSyncDeleteLogDao(db: NiplayerDatabase): SyncDeleteLogDao = db.getSyncDeleteLogDao()

    @Provides
    fun provideEncryptedFolderDao(db: NiplayerDatabase): EncryptedFolderDao = db.getEncryptedFolderDao()
}
