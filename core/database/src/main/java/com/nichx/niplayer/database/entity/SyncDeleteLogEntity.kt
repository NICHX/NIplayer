package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_delete_log",
    indices = [Index(value = ["table_name", "record_key"], unique = true)]
)
data class SyncDeleteLogEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "table_name")
    var tableName: String,

    @ColumnInfo(name = "record_key")
    var recordKey: String,

    @ColumnInfo(name = "deleted_at")
    var deletedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    var synced: Boolean = false,
)
