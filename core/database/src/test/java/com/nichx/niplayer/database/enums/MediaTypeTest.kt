package com.nichx.niplayer.database.enums

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun `fromValue 映射已知值`() {
        assertEquals(MediaType.LOCAL_STORAGE, MediaType.fromValue("local_storage"))
        assertEquals(MediaType.EXTERNAL_STORAGE, MediaType.fromValue("external_storage"))
        assertEquals(MediaType.SMB_SERVER, MediaType.fromValue("smb_server"))
        assertEquals(MediaType.WEBDAV_SERVER, MediaType.fromValue("webdav_server"))
        assertEquals(MediaType.QUICK_ACCESS, MediaType.fromValue("quick_access"))
    }

    @Test
    fun `fromValue 未知值回退 OTHER_STORAGE`() {
        assertEquals(MediaType.OTHER_STORAGE, MediaType.fromValue("unknown_type"))
        assertEquals(MediaType.OTHER_STORAGE, MediaType.fromValue(""))
    }

    @Test
    fun `value 属性往返`() {
        MediaType.entries.forEach { type ->
            assertEquals(type, MediaType.fromValue(type.value))
        }
    }

    @Test
    fun `排序优先级互不相同`() {
        val orders = MediaType.entries.map { it.sortOrder }.toSet()
        assertEquals(MediaType.entries.size, orders.size)
    }
}

class DateConverterTest {

    private val converter = com.nichx.niplayer.database.converter.DateConverter()

    @Test
    fun `时间戳转 Date`() {
        val date = converter.formTimestamp(1_000_000L)
        assertEquals(1_000_000L, date!!.time)
    }

    @Test
    fun `null 时间戳转 null`() {
        assertEquals(null, converter.formTimestamp(null))
    }

    @Test
    fun `Date 转时间戳`() {
        val date = java.util.Date(2_000_000L)
        assertEquals(2_000_000L, converter.dateToTimestamp(date))
    }

    @Test
    fun `null Date 转 null`() {
        assertEquals(null, converter.dateToTimestamp(null))
    }

    @Test
    fun `往返转换`() {
        val original = 123_456_789L
        val date = converter.formTimestamp(original)
        assertEquals(original, converter.dateToTimestamp(date))
    }
}

class BooleanConverterTest {

    private val converter = com.nichx.niplayer.database.converter.BooleanConverter()

    @Test
    fun `布尔转整数`() {
        assertEquals(1, converter.formBoolean(true))
        assertEquals(0, converter.formBoolean(false))
        assertEquals(null, converter.formBoolean(null))
    }

    @Test
    fun `整数转布尔`() {
        assertEquals(true, converter.intToBoolean(1))
        assertEquals(false, converter.intToBoolean(0))
        assertEquals(false, converter.intToBoolean(2))
        assertEquals(false, converter.intToBoolean(null))
    }

    @Test
    fun `往返转换`() {
        assertEquals(true, converter.intToBoolean(converter.formBoolean(true)))
        assertEquals(false, converter.intToBoolean(converter.formBoolean(false)))
    }
}

class MediaTypeConverterTest {

    private val converter = com.nichx.niplayer.database.converter.MediaTypeConverter()

    @Test
    fun `字符串转枚举`() {
        assertEquals(MediaType.SMB_SERVER, converter.formValue("smb_server"))
    }

    @Test
    fun `枚举转字符串`() {
        assertEquals("smb_server", converter.enumToValue(MediaType.SMB_SERVER))
    }

    @Test
    fun `往返转换`() {
        MediaType.entries.forEach { type ->
            assertEquals(type, converter.formValue(converter.enumToValue(type)))
        }
    }
}
