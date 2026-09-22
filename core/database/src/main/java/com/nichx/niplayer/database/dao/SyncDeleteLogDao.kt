package com.nichx.niplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nichx.niplayer.database.entity.SyncDeleteLogEntity

/**
 * 播放历史云同步的**本机墓碑账本**。
 *
 * 本地删除播放历史时写一行（[insertOrReplace]，同一 key 以最新删除时间覆盖），此后**永久保留**：
 * 每次同步都把整本账本作为候选参与折叠并写进云端共享文件。不清空是刻意的 —— 只要墓碑还在，
 * 晚联网的设备就不会把已删记录"复活"；即使云端文件被删，任何设备也能用本地账本重新发布。
 */
@Dao
interface SyncDeleteLogDao {

    /** 读取全部墓碑。 */
    @Query("SELECT * FROM sync_delete_log")
    suspend fun getAll(): List<SyncDeleteLogEntity>

    /**
     * 写入墓碑（REPLACE 语义）。
     *
     * 同一 key 已存在时用新删除时间覆盖：避免"删除→重扫→再删"时旧墓碑时间早于重新写入记录的
     * updatedAt，导致远端无法据此删除（记录"复活"）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: SyncDeleteLogEntity)
}
