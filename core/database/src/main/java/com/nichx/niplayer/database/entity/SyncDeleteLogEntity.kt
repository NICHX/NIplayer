package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放历史云同步的本机墓碑账本。
 *
 * 每一行表示"某条记录在本地被删除"。记录**永久保留**（每次同步都会把整本账本作为候选写进云端
 * 共享文件），因此 [synced] 列没有语义（恒为 0）；该列与 [tableName] 仅为兼容既有 schema 而保留，
 * 避免一次可能丢数据的表重建迁移。
 */
@Entity(
    tableName = "sync_delete_log",
    indices = [Index(value = ["table_name", "record_key"], unique = true)]
)
data class SyncDeleteLogEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "table_name")
    var tableName: String,

    /** 设备无关的同步键（归一化存储地址 + 存储内相对路径），见 `syncKey`。 */
    @ColumnInfo(name = "record_key")
    var recordKey: String,

    @ColumnInfo(name = "deleted_at")
    var deletedAt: Long = System.currentTimeMillis(),

    /** 已废弃：队列行的存在本身即"未发布"，此列不再有语义。 */
    @ColumnInfo(name = "synced")
    var synced: Boolean = false,
)
