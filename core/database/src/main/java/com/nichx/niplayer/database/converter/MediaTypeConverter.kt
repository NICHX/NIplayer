package com.nichx.niplayer.database.converter

import androidx.room.TypeConverter
import com.nichx.niplayer.database.enums.MediaType

/**
 * MediaType ↔ String 转换器，迁移自旧仓库 MediaTypeConverter。
 */
class MediaTypeConverter {
    @TypeConverter
    fun formValue(value: String): MediaType {
        return MediaType.fromValue(value)
    }

    @TypeConverter
    fun enumToValue(type: MediaType): String {
        return type.value
    }
}
