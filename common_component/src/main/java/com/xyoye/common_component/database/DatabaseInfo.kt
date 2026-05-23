package com.xyoye.common_component.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xyoye.common_component.database.dao.DownloadTaskDao
import com.xyoye.common_component.database.dao.ExtendFolderDao
import com.xyoye.common_component.database.dao.MediaLibraryDao
import com.xyoye.common_component.database.dao.PlayHistoryDao
import com.xyoye.common_component.database.dao.VideoDao
import com.xyoye.data_component.entity.DownloadTaskEntity
import com.xyoye.data_component.entity.ExtendFolderEntity
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.entity.VideoEntity

/**
 * Created by xyoye on 2020/7/29.
 */

@Database(
    entities =
    [VideoEntity::class,
        MediaLibraryEntity::class,
        PlayHistoryEntity::class,
        ExtendFolderEntity::class,
        DownloadTaskEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class DatabaseInfo : RoomDatabase() {

    abstract fun getVideoDao(): VideoDao

    abstract fun getMediaLibraryDao(): MediaLibraryDao

    abstract fun getPlayHistoryDao(): PlayHistoryDao

    abstract fun getExtendFolderDao(): ExtendFolderDao

    abstract fun getDownloadTaskDao(): DownloadTaskDao
}