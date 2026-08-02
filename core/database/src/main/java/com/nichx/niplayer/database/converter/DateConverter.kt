package com.nichx.niplayer.database.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Date ↔ Long(timestamp) 转换器，迁移自旧仓库 DateConverter。
 */
class DateConverter {
    @TypeConverter
    fun formTimestamp(value: Long?): Date? {
        return if (value == null) null else Date(value)
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
