package com.nichx.niplayer.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nichx.niplayer.database.converter.BooleanConverter
import com.nichx.niplayer.database.converter.DateConverter
import com.nichx.niplayer.database.converter.MediaTypeConverter
import com.nichx.niplayer.database.dao.DownloadTaskDao
import com.nichx.niplayer.database.dao.EncryptedFolderDao
import com.nichx.niplayer.database.dao.ExtendFolderDao
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.PlaylistDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.dao.QuickAccessDao
import com.nichx.niplayer.database.dao.SyncConflictDao
import com.nichx.niplayer.database.dao.SyncDeleteLogDao
import com.nichx.niplayer.database.dao.UploadTaskDao
import com.nichx.niplayer.database.dao.VideoBookmarkDao
import com.nichx.niplayer.database.dao.VideoDao
import com.nichx.niplayer.database.entity.DownloadTaskEntity
import com.nichx.niplayer.database.entity.EncryptedFolderEntity
import com.nichx.niplayer.database.entity.ExtendFolderEntity
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.entity.PlaylistEntity
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.nichx.niplayer.database.entity.QuickAccessEntity
import com.nichx.niplayer.database.entity.SyncConflictEntity
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity
import com.nichx.niplayer.database.entity.UploadTaskEntity
import com.nichx.niplayer.database.entity.VideoBookmarkEntity
import com.nichx.niplayer.database.entity.VideoEntity

/**
 * NIplayer 主数据库。
 *
 * Schema 由旧仓库 common_component 的 DatabaseInfo（"rood_db"）迁移而来，
 * 但本仓库为独立应用（applicationId = com.nichx.niplayer），不考虑老用户兼容性，
 * 数据库版本从 1 开始，不携带旧仓库 v1→v17 的历史 Migration。
 *
 * - `play_history` 表的 `danmu_path` / `episode_id` 字段在旧仓库 v15→v16 已移除，
 *   本仓库 schema 直接采用移除后的最终结构
 * - 本仓库移除 Alist 支持，MediaType 不包含 ALIST_STORAGE 项（参见项目 memory）
 *
 * 版本历史：
 * - v1~v5: 初始 schema
 * - v6: 当前稳定版本
 * - v7: 新增 updated_at 字段到所有实体 + sync_delete_log 表
 * - v8: 修正 updated_at 列默认值 schema hash
 * - v9: 新增 video_bookmark 表（F-19 视频书签）
 * - v10: 新增 encrypted_folder 表（文件夹访问加密）
 * - v11: 新增 playlist / playlist_item 表（扩展功能方案二：播放列表系统）
 * - v12: play_history 表新增 playlist_id 列（记录来源歌单，恢复播放时还原歌单播放列表）
 * - v13: 移除 PasswordVault 加密，清空 media_library 中遗留的 enc:v1: 密文密码
 * - v14: 新增 sync_conflict 表（播放历史云同步冲突记录）
 * - v15: playlist 表新增 is_pinned 列（歌单置顶）
 */
@Database(
    entities = [
        VideoEntity::class,
        MediaLibraryEntity::class,
        PlayHistoryEntity::class,
        ExtendFolderEntity::class,
        DownloadTaskEntity::class,
        QuickAccessEntity::class,
        SyncDeleteLogEntity::class,
        VideoBookmarkEntity::class,
        EncryptedFolderEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SyncConflictEntity::class,
        UploadTaskEntity::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(
    BooleanConverter::class,
    DateConverter::class,
    MediaTypeConverter::class
)
abstract class NiplayerDatabase : RoomDatabase() {

    abstract fun getVideoDao(): VideoDao

    abstract fun getMediaLibraryDao(): MediaLibraryDao

    abstract fun getPlayHistoryDao(): PlayHistoryDao

    abstract fun getExtendFolderDao(): ExtendFolderDao

    abstract fun getDownloadTaskDao(): DownloadTaskDao

    abstract fun getUploadTaskDao(): UploadTaskDao

    abstract fun getQuickAccessDao(): QuickAccessDao

    abstract fun getSyncDeleteLogDao(): SyncDeleteLogDao

    abstract fun getSyncConflictDao(): SyncConflictDao

    abstract fun getVideoBookmarkDao(): VideoBookmarkDao

    abstract fun getEncryptedFolderDao(): EncryptedFolderDao

    abstract fun getPlaylistDao(): PlaylistDao

    abstract fun getPlaylistItemDao(): PlaylistItemDao

    companion object {
        const val DATABASE_NAME = "niplayer.db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_library ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE play_history ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quick_access ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE extend_folder ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_delete_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        table_name TEXT NOT NULL,
                        record_key TEXT NOT NULL,
                        deleted_at INTEGER NOT NULL,
                        synced INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_delete_log_table_name_record_key " +
                        "ON sync_delete_log (table_name, record_key)"
                )
            }
        }

        // K1 修复：5 张表的 updated_at 列加了 @ColumnInfo(defaultValue = "0")，
        // 但 v7 新装设备的 updated_at 列没有 DEFAULT（CREATE TABLE 时实体未声明 defaultValue），
        // 需要重建表补上 DEFAULT 0 使其与 v8 schema 一致。
        // v6→v7 升级的设备已有 DEFAULT 0，重建后保持不变，数据安全。
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // media_library
                db.execSQL("CREATE TABLE IF NOT EXISTS `_new_media_library` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `display_name` TEXT NOT NULL, `url` TEXT NOT NULL, `media_type` TEXT NOT NULL, `account` TEXT, `password` TEXT, `domain` TEXT, `is_anonymous` INTEGER NOT NULL, `port` INTEGER NOT NULL, `describe` TEXT, `smb_v2` INTEGER NOT NULL, `smb_share_path` TEXT, `smb_encryption` INTEGER NOT NULL, `remote_secret` TEXT, `web_dav_strict` INTEGER NOT NULL, `screencast_address` TEXT NOT NULL, `updated_at` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO `_new_media_library` SELECT * FROM `media_library`")
                db.execSQL("DROP TABLE `media_library`")
                db.execSQL("ALTER TABLE `_new_media_library` RENAME TO `media_library`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_library_url_media_type` ON `media_library` (`url`, `media_type`)")

                // play_history
                db.execSQL("CREATE TABLE IF NOT EXISTS `_new_play_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `video_name` TEXT NOT NULL, `url` TEXT NOT NULL, `media_type` TEXT NOT NULL, `video_position` INTEGER NOT NULL, `video_duration` INTEGER NOT NULL, `play_time` INTEGER NOT NULL, `subtitle_path` TEXT, `torrent_path` TEXT, `torrent_index` INTEGER NOT NULL, `http_header` TEXT, `unique_key` TEXT NOT NULL, `storage_path` TEXT, `storage_id` INTEGER, `audio_path` TEXT, `updated_at` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO `_new_play_history` SELECT * FROM `play_history`")
                db.execSQL("DROP TABLE `play_history`")
                db.execSQL("ALTER TABLE `_new_play_history` RENAME TO `play_history`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_play_history_unique_key_storage_id` ON `play_history` (`unique_key`, `storage_id`)")

                // quick_access
                db.execSQL("CREATE TABLE IF NOT EXISTS `_new_quick_access` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `storage_path` TEXT NOT NULL, `is_directory` INTEGER NOT NULL, `library_id` INTEGER NOT NULL, `add_time` INTEGER NOT NULL, `sort_index` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO `_new_quick_access` SELECT * FROM `quick_access`")
                db.execSQL("DROP TABLE `quick_access`")
                db.execSQL("ALTER TABLE `_new_quick_access` RENAME TO `quick_access`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quick_access_library_id_storage_path` ON `quick_access` (`library_id`, `storage_path`)")

                // extend_folder
                db.execSQL("CREATE TABLE IF NOT EXISTS `_new_extend_folder` (`folder_path` TEXT NOT NULL, `child_count` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`folder_path`))")
                db.execSQL("INSERT INTO `_new_extend_folder` SELECT * FROM `extend_folder`")
                db.execSQL("DROP TABLE `extend_folder`")
                db.execSQL("ALTER TABLE `_new_extend_folder` RENAME TO `extend_folder`")

                // download_task
                db.execSQL("CREATE TABLE IF NOT EXISTS `_new_download_task` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `storage_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `file_path` TEXT NOT NULL, `unique_key` TEXT NOT NULL, `total_bytes` INTEGER NOT NULL, `downloaded_bytes` INTEGER NOT NULL, `state` INTEGER NOT NULL, `error_message` TEXT, `target_storage_url` TEXT, `target_storage_name` TEXT, `create_time` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO `_new_download_task` SELECT * FROM `download_task`")
                db.execSQL("DROP TABLE `download_task`")
                db.execSQL("ALTER TABLE `_new_download_task` RENAME TO `download_task`")
            }
        }

        // F-19：新增 video_bookmark 表
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `video_bookmark` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `unique_key` TEXT NOT NULL,
                        `storage_id` INTEGER,
                        `video_name` TEXT NOT NULL,
                        `position_ms` INTEGER NOT NULL,
                        `label` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_video_bookmark_unique_key_storage_id_position_ms` " +
                        "ON `video_bookmark` (`unique_key`, `storage_id`, `position_ms`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_video_bookmark_unique_key_storage_id` " +
                        "ON `video_bookmark` (`unique_key`, `storage_id`)"
                )
            }
        }

        // 文件夹访问加密：新增 encrypted_folder 表
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `encrypted_folder` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `storage_id` INTEGER NOT NULL,
                        `folder_path` TEXT NOT NULL,
                        `password_hash` TEXT NOT NULL,
                        `password_salt` TEXT NOT NULL,
                        `iterations` INTEGER NOT NULL DEFAULT 120000,
                        `biometric_secret` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_encrypted_folder_storage_id_folder_path` " +
                        "ON `encrypted_folder` (`storage_id`, `folder_path`)"
                )
            }
        }

        // 播放列表系统：新增 playlist / playlist_item 表
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playlist` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playlist_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlist_id` INTEGER NOT NULL,
                        `library_id` INTEGER NOT NULL,
                        `file_path` TEXT NOT NULL,
                        `file_name` TEXT NOT NULL,
                        `media_type` TEXT NOT NULL,
                        `file_size` INTEGER NOT NULL DEFAULT 0,
                        `sort_order` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_item_playlist_id_file_path` " +
                        "ON `playlist_item` (`playlist_id`, `file_path`)"
                )
            }
        }

        // 播放历史记录来源歌单：play_history 表新增 playlist_id 列
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `play_history` ADD COLUMN `playlist_id` INTEGER"
                )
            }
        }

        // 移除 PasswordVault 加密：清空遗留的 enc:v1: 密文密码，密码改为明文存储
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE media_library SET password = NULL WHERE password LIKE 'enc:v1:%'"
                )
            }
        }

        // 播放历史云同步冲突记录：新增 sync_conflict 表
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_conflict` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `record_key` TEXT NOT NULL,
                        `storage_id` INTEGER,
                        `unique_key` TEXT NOT NULL,
                        `video_name` TEXT NOT NULL,
                        `local_video_position` INTEGER NOT NULL,
                        `local_video_duration` INTEGER NOT NULL,
                        `local_updated_at` INTEGER NOT NULL,
                        `local_play_time` INTEGER NOT NULL,
                        `remote_video_position` INTEGER NOT NULL,
                        `remote_video_duration` INTEGER NOT NULL,
                        `remote_updated_at` INTEGER NOT NULL,
                        `resolved` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflict_record_key` " +
                        "ON `sync_conflict` (`record_key`)"
                )
            }
        }

        // 歌单管理增强：playlist 表新增 is_pinned 列（置顶歌单固定排最前）
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist` ADD COLUMN `is_pinned` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
