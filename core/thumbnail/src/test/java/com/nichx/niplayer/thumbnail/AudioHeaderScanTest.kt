package com.nichx.niplayer.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * 音频头部扫描的单测：验证"包含完整内嵌封面所需字节数"的推算逻辑。
 *
 * 背景：远程音频只读文件头部，固定 2MB 上限会把大封面（如 FLAC 内嵌 5MB PNG）截断，
 * 需据容器结构精确补读。此处锁定 FLAC 元数据块扫描与 ID3v2 标签长度解析。
 */
class AudioHeaderScanTest {

    private fun block(type: Int, len: Int, last: Boolean, dataLen: Int = len): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((if (last) 0x80 else 0) or (type and 0x7F))
        out.write((len shr 16) and 0xFF)
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
        repeat(dataLen) { out.write(0) }
        return out.toByteArray()
    }

    private fun flac(vararg blocks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        blocks.forEach { out.write(it) }
        return out.toByteArray()
    }

    @Test
    fun `FLAC 有 PICTURE 返回块末尾偏移`() {
        val bytes = flac(
            block(type = 0, len = 34, last = false),
            block(type = 6, len = 1000, last = true, dataLen = 0),
        )
        // 4(magic) + 4+34(streaminfo) + 4(picture 头) + 1000 = 1046
        assertEquals(1046L, flacPictureEndOffset(bytes))
    }

    @Test
    fun `FLAC 无 PICTURE 且已到最后块返回 -1`() {
        val bytes = flac(block(type = 0, len = 34, last = true))
        assertEquals(-1L, flacPictureEndOffset(bytes))
    }

    @Test
    fun `FLAC 块长超出已读字节且未到末块返回 null`() {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        out.write(byteArrayOf(0x04, 0x00, 0x07, 0xD0.toByte())) // type4 len=2000 last=0
        repeat(20) { out.write(0) }
        assertNull(flacPictureEndOffset(out.toByteArray()))
    }

    @Test
    fun `ID3v2 syncsafe 长度返回标签末尾偏移`() {
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray())
        out.write(byteArrayOf(3, 0, 0)) // 版本 3.0，flags 无
        out.write(byteArrayOf(0, 0, 7, 0x68)) // syncsafe = 1000
        repeat(100) { out.write(0) }
        assertEquals(1010L, id3TagEndOffset(out.toByteArray()))
    }

    @Test
    fun `ID3v2 带 footer 额外加 10`() {
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray())
        out.write(byteArrayOf(4, 0, 0x10)) // flags 0x10 = footer present
        out.write(byteArrayOf(0, 0, 0, 100)) // size 100
        assertEquals(120L, id3TagEndOffset(out.toByteArray()))
    }
}
