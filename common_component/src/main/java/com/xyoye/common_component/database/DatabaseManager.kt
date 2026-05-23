package com.xyoye.common_component.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xyoye.common_component.base.app.BaseApplication

/**
 * Created by xyoye on 2020/7/29.
 */


class DatabaseManager private constructor() {
    //"CREATE UNIQUE INDEX IF NOT EXISTS index_anime_search_history_search_text ON anime_search_history(search_text)"
    //"CREATE TABLE anime_search_history( id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, search_text TEXT NOT NULL UNIQUE, search_time INTEGER NOT NULL)"
    //"ALTER TABLE search_history RENAME TO magnet_search_history"
    //"ALTER TABLE magnet_screen ADD COLUMN screen_id INTEGER NOT NULL"

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_library ADD COLUMN remote_secret TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS 'index_media_library_url'")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_media_library_url_media_type ON media_library(url, media_type)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {

            override fun migrate(database: SupportSQLiteDatabase) {
                //新建临时表
                database.execSQL(
                    "CREATE TABLE play_history_temp(" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "video_name TEXT NOT NULL," +
                            "url TEXT NOT NULL UNIQUE, " +
                            "media_type TEXT NOT NULL," +
                            "video_position INTEGER NOT NULL," +
                            "video_duration INTEGER NOT NULL," +
                            "play_time INTEGER NOT NULL," +
                            "danmu_path TEXT," +
                            "episode_id INTEGER NOT NULL," +
                            "subtitle_path TEXT," +
                            "extra TEXT)"
                )
                //旧表数据迁移
                database.execSQL(
                    "INSERT INTO play_history_temp(id, video_name, url, media_type, video_position, video_duration, play_time, danmu_path, episode_id,subtitle_path) " +
                            "SELECT id, video_name, url, media_type, video_position, video_duration, play_time, danmu_path, episode_id,subtitle_path FROM play_history"
                )
                //移除旧表
                database.execSQL("DROP TABLE play_history")
                //重命名为旧表
                database.execSQL("ALTER TABLE play_history_temp RENAME TO play_history")
                //加上唯一约束
                database.execSQL("DROP INDEX IF EXISTS 'index_play_history_url'")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_play_history_url ON play_history(url)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_library ADD COLUMN web_dav_strict INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE play_history ADD COLUMN torrent_path TEXT")
                database.execSQL("ALTER TABLE play_history ADD COLUMN torrent_index INTEGER NOT NULL DEFAULT -1")
                database.execSQL("ALTER TABLE play_history ADD COLUMN http_header TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE play_history ADD COLUMN unique_key TEXT NOT NULL DEFAULT ''")
                //更新新增字段默认值为随机值
                database.execSQL("UPDATE play_history SET unique_key = hex(randomblob(16)) WHERE unique_key = ''")
                //移除旧的唯一约束
                database.execSQL("DROP INDEX IF EXISTS 'index_play_history_url'")
                //设置unique_key和media_type的多列唯一约束
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_play_history_unique_key_media_type ON play_history(unique_key, media_type)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_library ADD COLUMN screencast_address TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_library ADD COLUMN remote_anime_grouping INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE play_history ADD COLUMN is_last_play INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE play_history ADD COLUMN storage_path TEXT")
                database.execSQL("ALTER TABLE play_history ADD COLUMN storage_id INTEGER")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                //新建临时表
                database.execSQL(
                    "CREATE TABLE play_history_temp(" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                            "video_name TEXT NOT NULL," +
                            "url TEXT NOT NULL," +
                            "media_type TEXT NOT NULL," +
                            "video_position INTEGER NOT NULL," +
                            "video_duration INTEGER NOT NULL," +
                            "play_time INTEGER NOT NULL," +
                            "danmu_path TEXT," +
                            "episode_id TEXT," +
                            "subtitle_path TEXT," +
                            "torrent_path TEXT," +
                            "torrent_index INTEGER NOT NULL DEFAULT -1," +
                            "http_header TEXT," +
                            "unique_key TEXT NOT NULL DEFAULT ''," +
                            "storage_path TEXT," +
                            "storage_id INTEGER" +
                            ")"
                )
                //旧表数据迁移
                database.execSQL(
                    "INSERT INTO play_history_temp(" +
                            "id, video_name, url, media_type, video_position, " +
                            "video_duration, play_time, danmu_path, episode_id," +
                            "subtitle_path, torrent_path, torrent_index, http_header, " +
                            "unique_key, storage_path, storage_id" +
                            ") " +
                            "SELECT " +
                            "id, video_name, url, media_type, video_position," +
                            "video_duration, play_time, danmu_path, episode_id," +
                            "subtitle_path, torrent_path, torrent_index, http_header," +
                            "unique_key, storage_path, storage_id" +
                            " FROM play_history"
                )
                //移除旧表
                database.execSQL("DROP TABLE play_history")
                //重命名为旧表
                database.execSQL("ALTER TABLE play_history_temp RENAME TO play_history")
                //移除旧的mediaType + uniqueKey唯一约束
                database.execSQL("DROP INDEX IF EXISTS 'index_play_history_unique_key_media_type'")
                //创建新的storageId + uniqueKey的唯一约束
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_play_history_unique_key_storage_id ON play_history(unique_key, storage_id)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE play_history ADD COLUMN audio_path TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS magnet_search_history")
                database.execSQL("DROP TABLE IF EXISTS magnet_screen")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS download_task (" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "storage_id INTEGER NOT NULL, " +
                            "file_name TEXT NOT NULL, " +
                            "file_path TEXT NOT NULL, " +
                            "unique_key TEXT NOT NULL, " +
                            "total_bytes INTEGER NOT NULL DEFAULT 0, " +
                            "downloaded_bytes INTEGER NOT NULL DEFAULT 0, " +
                            "state INTEGER NOT NULL DEFAULT 0, " +
                            "error_message TEXT, " +
                            "target_storage_url TEXT, " +
                            "target_storage_name TEXT, " +
                            "create_time INTEGER NOT NULL" +
                            ")"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS danmu_block")
                database.execSQL("DROP TABLE IF EXISTS anime_search_history")
                database.execSQL(
                    "CREATE TABLE play_history_temp(" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                            "video_name TEXT NOT NULL," +
                            "url TEXT NOT NULL," +
                            "media_type TEXT NOT NULL," +
                            "video_position INTEGER NOT NULL," +
                            "video_duration INTEGER NOT NULL," +
                            "play_time INTEGER NOT NULL," +
                            "subtitle_path TEXT," +
                            "torrent_path TEXT," +
                            "torrent_index INTEGER NOT NULL DEFAULT -1," +
                            "http_header TEXT," +
                            "unique_key TEXT NOT NULL DEFAULT ''," +
                            "storage_path TEXT," +
                            "storage_id INTEGER," +
                            "audio_path TEXT" +
                            ")"
                )
                database.execSQL(
                    "INSERT INTO play_history_temp(" +
                            "id, video_name, url, media_type, video_position, " +
                            "video_duration, play_time, subtitle_path, " +
                            "torrent_path, torrent_index, http_header, " +
                            "unique_key, storage_path, storage_id, audio_path" +
                            ") " +
                            "SELECT " +
                            "id, video_name, url, media_type, video_position," +
                            "video_duration, play_time, subtitle_path," +
                            "torrent_path, torrent_index, http_header," +
                            "unique_key, storage_path, storage_id, audio_path" +
                            " FROM play_history"
                )
                database.execSQL("DROP TABLE play_history")
                database.execSQL("ALTER TABLE play_history_temp RENAME TO play_history")
                database.execSQL("DROP INDEX IF EXISTS 'index_play_history_unique_key_storage_id'")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_play_history_unique_key_storage_id ON play_history(unique_key, storage_id)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS video_temp(" +
                            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                            "file_id INTEGER NOT NULL," +
                            "file_path TEXT NOT NULL," +
                            "folder_path TEXT NOT NULL," +
                            "subtitle_path TEXT," +
                            "video_duration INTEGER NOT NULL DEFAULT 0," +
                            "file_length INTEGER NOT NULL DEFAULT 0," +
                            "filter INTEGER NOT NULL DEFAULT 0," +
                            "extend INTEGER NOT NULL DEFAULT 0" +
                            ")"
                )
                database.execSQL(
                    "INSERT INTO video_temp(id, file_id, file_path, folder_path, subtitle_path, video_duration, file_length, filter, extend) " +
                            "SELECT id, file_id, file_path, folder_path, subtitle_path, video_duration, file_length, filter, extend FROM video"
                )
                database.execSQL("DROP TABLE video")
                database.execSQL("ALTER TABLE video_temp RENAME TO video")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_file_path ON video(file_path)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scrape_media` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`path` TEXT NOT NULL, " +
                            "`media_type` TEXT NOT NULL, " +
                            "`poster` TEXT, " +
                            "`backdrop` TEXT, " +
                            "`tmdb_id` INTEGER, " +
                            "`genre_ids` TEXT NOT NULL, " +
                            "`vote_average` REAL NOT NULL, " +
                            "`release_time` TEXT, " +
                            "`overview` TEXT, " +
                            "`season` TEXT NOT NULL, " +
                            "`source_json` TEXT NOT NULL, " +
                            "`update_time` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_scrape_media_path_media_type` " +
                            "ON `scrape_media` (`path`, `media_type`)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scrape_mulu_config` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`media_library_id` INTEGER NOT NULL, " +
                            "`mulu_type` TEXT NOT NULL, " +
                            "`path` TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episode` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`media_id` INTEGER NOT NULL, " +
                            "`season_number` INTEGER NOT NULL DEFAULT 1, " +
                            "`episode_number` INTEGER NOT NULL, " +
                            "`title` TEXT, " +
                            "`file_path` TEXT NOT NULL, " +
                            "`file_name` TEXT NOT NULL, " +
                            "`file_size` INTEGER NOT NULL DEFAULT 0, " +
                            "`overview` TEXT, " +
                            "`still_url` TEXT, " +
                            "`duration` INTEGER NOT NULL DEFAULT 0, " +
                            "`update_time` INTEGER NOT NULL, " +
                            "FOREIGN KEY (`media_id`) REFERENCES `scrape_media`(`id`) ON DELETE CASCADE" +
                            ")"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_episode_media_id` ON `episode` (`media_id`)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_episode_media_id_season_number_episode_number` " +
                            "ON `episode` (`media_id`, `season_number`, `episode_number`)"
                )
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tmdb_sync_queue` (" +
                            "`key` TEXT NOT NULL PRIMARY KEY, " +
                            "`tmdb_id` INTEGER NOT NULL, " +
                            "`task_type` TEXT NOT NULL, " +
                            "`season_number` INTEGER, " +
                            "`priority` INTEGER NOT NULL DEFAULT 0, " +
                            "`state` TEXT NOT NULL DEFAULT 'PENDING', " +
                            "`attempt_count` INTEGER NOT NULL DEFAULT 0, " +
                            "`last_error` TEXT, " +
                            "`update_time` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tmdb_sync_queue_state` ON `tmdb_sync_queue` (`state`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tmdb_sync_queue_task_type` ON `tmdb_sync_queue` (`task_type`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tmdb_sync_queue_update_time` ON `tmdb_sync_queue` (`update_time`)"
                )
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scrape_media ADD COLUMN play_key TEXT")
            }
        }

        val instance = DatabaseManager.holder.database
    }

    private object DatabaseManager {
        val holder = DatabaseManager()
    }

    private var database = Room.databaseBuilder(
        BaseApplication.getAppContext(),
        DatabaseInfo::class.java,
        "rood_db"
    ).addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21
    ).build()

    fun getEpisodeDao() = database.getEpisodeDao()

    fun getTmdbSyncQueueDao() = database.getTmdbSyncQueueDao()
}