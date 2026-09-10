package com.nichx.niplayer.database.converter

import androidx.room.TypeConverter

/**
 * Boolean ↔ Int 转换器。
 */
class BooleanConverter {
    @TypeConverter
    fun formBoolean(value: Boolean?): Int? {
        return if (value == null) null else if (value) 1 else 0
    }

    @TypeConverter
    fun intToBoolean(intValue: Int?): Boolean? {
        return intValue == 1
    }
}
