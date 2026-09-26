package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 音频匹配结果表。
 *
 * 记录「这首歌匹配到了什么」：曲目信息 + 来源 + 是否被用户锁定。
 * 与 `video` 表同理 —— 匹配结果的生命周期由「用户是否满意」驱动，
 * 而播放行为的生命周期由播放驱动（`play_history`），两者必须解耦。
 *
 * 落库替代原先仅存 `cacheDir/lrc_cache/<md5(path)>.lrc` 的做法，解决：
 * - 清缓存即丢（**包括用户手动修正的结果**）
 * - 无法表达「用户确认过，不要再改」（见 [locked]）
 * - 无法查询与统计匹配质量
 *
 * 歌词 / 封面**正文不进库**：只存 [lrcPath] / [coverPath] 指向的缓存文件路径。
 */
@Entity(
    tableName = "audio_match",
    indices = [Index(value = ["file_key"], unique = true)],
)
data class AudioMatchEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    /**
     * 匹配键，与 `OnlineMatchBlacklist` / `OnlineMatchCache` 的约定保持一致：
     * 媒体库文件 `sid:<storageId>:<路径>`，本地文件 `local:<uri 或路径>`。
     */
    @ColumnInfo(name = "file_key")
    var fileKey: String,

    @ColumnInfo(name = "storage_id")
    var storageId: Int? = null,

    @ColumnInfo(name = "file_path")
    var filePath: String,

    @ColumnInfo(name = "title")
    var title: String,

    @ColumnInfo(name = "artist")
    var artist: String = "",

    @ColumnInfo(name = "album")
    var album: String? = null,

    /**
     * 来源标签。取值与 `com.nichx.niplayer.metadata.model.CandidateSource` 的名字对应：
     * `ID3` / `FILENAME` / `DIRECTORY` / `ONLINE` / `MANUAL`。
     *
     * 存字符串而非枚举：`core:database` 不应反向依赖 `core:metadata`。
     */
    @ColumnInfo(name = "source")
    var source: String,

    /**
     * 用户是否确认过这条匹配。
     *
     * `true` 表示用户手动指定（或显式确认）过，后续自动匹配、批量重扫、在线兜底
     * 都**不得覆盖**它。这是「人工修正不白费」的保证，也是旧方案完全缺失的能力
     * （旧的黑名单语义是「永久放弃整首歌」，不是「记住正确的匹配」）。
     */
    @ColumnInfo(name = "locked")
    var locked: Boolean = false,

    @ColumnInfo(name = "lrc_path")
    var lrcPath: String? = null,

    @ColumnInfo(name = "cover_path")
    var coverPath: String? = null,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis(),
)
