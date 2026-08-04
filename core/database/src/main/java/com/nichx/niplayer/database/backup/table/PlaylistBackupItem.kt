package com.nichx.niplayer.database.backup.table

import androidx.room.withTransaction
import com.nichx.niplayer.database.NiplayerDatabase
import com.nichx.niplayer.database.backup.BackupItem
import com.nichx.niplayer.database.backup.RestoreMode
import com.nichx.niplayer.database.dao.PlaylistDao
import com.nichx.niplayer.database.dao.PlaylistItemDao
import com.nichx.niplayer.database.entity.PlaylistEntity
import com.nichx.niplayer.database.entity.PlaylistItemEntity
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import javax.inject.Inject

/**
 * 歌单联合备份项（playlist + playlist_item）。
 *
 * 与普通单表备份 [com.nichx.niplayer.database.backup.BackupTable] 不同，歌单与条目间存在
 * 外键依赖（playlist_item.playlist_id -> playlist.id），而 playlist.id 是自增主键，跨设备
 * 恢复时 id 会变化。因此不能按原主键直接还原，必须：
 * 1. 先插入 playlist，拿到新生成的 id；
 * 2. 把 playlist_item 的 playlist_id 重映射到新 id，再插入。
 *
 * 快照结构（松散 Map，由 Moshi toJsonValue/fromJsonValue 处理）：
 * ```
 * { "playlists": [...], "items": [...] }
 * ```
 * 两个列表均为空时 [snapshot] 返回 null（无数据可导出）。恢复时 [RestoreMode] 仅影响是否
 * 清表，id 重映射在两种模式下都执行（MERGE 下备份内歌单可能与本机同名但 id 不同）。
 */
class PlaylistBackupItem @Inject constructor(
    private val db: NiplayerDatabase,
    moshi: Moshi,
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
) : BackupItem {

    override val key: String = "playlists"

    /**
     * 带日期适配器的 Moshi 实例：PlaylistEntity.createdAt 为 [java.util.Date]，需与
     * [com.nichx.niplayer.database.backup.BackupManager] 统一按 Long 时间戳序列化。
     */
    private val moshiInstance = moshi.newBuilder()
        .add(DateAdapter)
        .build()

    private val playlistAdapter by lazy {
        moshiInstance.adapter<List<PlaylistEntity>>(
            Types.newParameterizedType(List::class.java, PlaylistEntity::class.java),
        )
    }

    private val itemAdapter by lazy {
        moshiInstance.adapter<List<PlaylistItemEntity>>(
            Types.newParameterizedType(List::class.java, PlaylistItemEntity::class.java),
        )
    }

    override suspend fun snapshot(): Any? {
        val playlists = playlistDao.getAll()
        val items = playlistItemDao.getAll()
        if (playlists.isEmpty() && items.isEmpty()) return null
        val envelope: Map<String, Any?> = linkedMapOf(
            FIELD_PLAYLISTS to playlistAdapter.toJsonValue(playlists),
            FIELD_ITEMS to itemAdapter.toJsonValue(items),
        )
        return envelope
    }

    override suspend fun restore(data: Any?, mode: RestoreMode) {
        if (data == null) return
        val envelope = data as? Map<*, *> ?: return
        val playlists: List<PlaylistEntity> =
            playlistAdapter.fromJsonValue(envelope[FIELD_PLAYLISTS]) ?: emptyList()
        val items: List<PlaylistItemEntity> =
            itemAdapter.fromJsonValue(envelope[FIELD_ITEMS]) ?: emptyList()
        if (playlists.isEmpty() && items.isEmpty()) return

        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                // 先删条目再删歌单，避免悬空引用
                playlistItemDao.deleteAll()
                playlistDao.deleteAll()
            }
            // 逐条插入歌单，建立 旧 id -> 新 id 映射（insert 返回新生成的 rowId）
            val idMap = HashMap<Int, Int>(playlists.size)
            for (playlist in playlists) {
                val oldId = playlist.id
                val newId = playlistDao.insert(playlist.copy(id = 0))
                if (newId > 0) {
                    idMap[oldId] = newId.toInt()
                }
            }
            // 重映射 playlist_id 并清零 id（让自增）；无法映射到新歌单的条目丢弃
            val remapped = items.mapNotNull { item ->
                val newPlaylistId = idMap[item.playlistId] ?: return@mapNotNull null
                item.copy(id = 0, playlistId = newPlaylistId)
            }
            if (remapped.isNotEmpty()) {
                playlistItemDao.insertAll(remapped)
            }
        }
    }

    override fun describe(data: Any?): String? {
        if (data == null) return null
        val envelope = data as? Map<*, *> ?: return null
        val playlists: List<PlaylistEntity> =
            playlistAdapter.fromJsonValue(envelope[FIELD_PLAYLISTS]) ?: emptyList()
        val items: List<PlaylistItemEntity> =
            itemAdapter.fromJsonValue(envelope[FIELD_ITEMS]) ?: emptyList()
        if (playlists.isEmpty() && items.isEmpty()) return null
        return "歌单: ${playlists.size} 个 (${items.size} 首曲目)"
    }

    private companion object {
        const val FIELD_PLAYLISTS = "playlists"
        const val FIELD_ITEMS = "items"
    }
}

/** Date <-> Long 时间戳适配器，与 BackupManager 保持一致。 */
private object DateAdapter {
    @ToJson
    fun toJson(date: java.util.Date): Long = date.time

    @FromJson
    fun fromJson(timestamp: Long): java.util.Date = java.util.Date(timestamp)
}
