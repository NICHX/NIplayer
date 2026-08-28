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
import com.nichx.niplayer.database.dao.SyncConflictDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.dao.UploadTaskDao
import com.nichx.niplayer.database.dao.VideoBookmarkDao
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
 * [fallbackToDestructiveMigration] 作为其他未预期 schema 变更的兜底。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNiplayerDatabase(@ApplicationContext ctx: Context): NiplayerDatabase =
        Room.databaseBuilder(ctx, NiplayerDatabase::class.java, NiplayerDatabase.DATABASE_NAME)
            .addMigrations(
                NiplayerDatabase.MIGRATION_6_7,
                NiplayerDatabase.MIGRATION_7_8,
                NiplayerDatabase.MIGRATION_8_9,
                NiplayerDatabase.MIGRATION_9_10,
                NiplayerDatabase.MIGRATION_12_13,
                NiplayerDatabase.MIGRATION_13_14,
            )
            .fallbackToDestructiveMigration(true)
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
    fun provideSyncConflictDao(db: NiplayerDatabase): SyncConflictDao = db.getSyncConflictDao()

    @Provides
    fun provideVideoBookmarkDao(db: NiplayerDatabase): VideoBookmarkDao = db.getVideoBookmarkDao()

    @Provides
    fun provideEncryptedFolderDao(db: NiplayerDatabase): EncryptedFolderDao = db.getEncryptedFolderDao()
}
