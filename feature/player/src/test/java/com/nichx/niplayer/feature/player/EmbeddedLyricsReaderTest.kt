package com.nichx.niplayer.feature.player

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLyricsReaderTest {

    @Test
    fun `解析 ID3v2_3 USLT（UTF-8）`() {
        val lyrics = "[00:01.00]Hello\n[00:03.00]World"
        val head = id3Uslt(version = 3, encoding = 3, lyrics = lyrics.toByteArray(Charsets.UTF_8))

        assertEquals(lyrics, EmbeddedLyricsReader.parse(head))
    }

    @Test
    fun `解析 ID3v2_4 USLT（UTF-16，带 BOM）`() {
        val lyrics = "[00:01.00]你好"
        val payload = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + lyrics.toByteArray(Charsets.UTF_16LE)
        val head = id3Uslt(version = 4, encoding = 1, lyrics = payload)

        assertEquals(lyrics, EmbeddedLyricsReader.parse(head))
    }

    @Test
    fun `ID3 无 USLT 帧返回 null`() {
        // 仅含一个 TIT2 文本帧
        val head = id3Tag(frames = id3Frame("TIT2", 3, "Title".toByteArray(Charsets.UTF_8)))
        assertNull(EmbeddedLyricsReader.parse(head))
    }

    @Test
    fun `ID3 头部被截断时提示追读所需字节`() {
        val full = id3Uslt(version = 3, encoding = 3, lyrics = "[00:01.00]hi".toByteArray(Charsets.UTF_8))
        val truncated = full.copyOf(12)

        assertNull(EmbeddedLyricsReader.parse(truncated))
        assertEquals(full.size, EmbeddedLyricsReader.requiredHeadBytes(truncated))
    }

    @Test
    fun `ID3 头部完整时无需追读`() {
        val full = id3Uslt(version = 3, encoding = 3, lyrics = "[00:01.00]hi".toByteArray(Charsets.UTF_8))
        assertNull(EmbeddedLyricsReader.requiredHeadBytes(full))
    }

    @Test
    fun `解析 FLAC 的 LYRICS 字段`() {
        val lyrics = "[00:12.34]Hello world"
        val head = flac(listOf("LYRICS=$lyrics", "ARTIST=Someone"))

        assertEquals(lyrics, EmbeddedLyricsReader.parse(head))
    }

    @Test
    fun `解析 FLAC 的 UNSYNCEDLYRICS 字段（键不区分大小写）`() {
        val head = flac(listOf("unsyncedlyrics=plain lyrics"))
        assertEquals("plain lyrics", EmbeddedLyricsReader.parse(head))
    }

    @Test
    fun `FLAC 无歌词字段返回 null`() {
        val head = flac(listOf("ARTIST=Someone", "ALBUM=Record"))
        assertNull(EmbeddedLyricsReader.parse(head))
    }

    @Test
    fun `FLAC 元数据块被截断时提示追读`() {
        val full = flac(listOf("LYRICS=[00:01.00]hi"))
        val truncated = full.copyOf(6) // 仅 "fLaC" + 部分块头

        assertNull(EmbeddedLyricsReader.parse(truncated))
        val required = EmbeddedLyricsReader.requiredHeadBytes(truncated)
        assertTrue(required != null && required > truncated.size)
    }

    @Test
    fun `非 ID3 FLAC 容器返回 null`() {
        assertNull(EmbeddedLyricsReader.parse(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)))
        assertNull(EmbeddedLyricsReader.parse(ByteArray(0)))
    }

    // ---------------- 测试用构造器 ----------------

    /** 构造一个含单个 USLT 帧的 ID3v2 标签。 */
    private fun id3Uslt(version: Int, encoding: Int, lyrics: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(encoding)
            write("eng".toByteArray(Charsets.US_ASCII))
            if (encoding == 1 || encoding == 2) write(byteArrayOf(0, 0)) else write(0)
            write(lyrics)
        }.toByteArray()
        return id3Tag(frames = id3Frame("USLT", version, payload))
    }

    /** 构造 ID3v2 标签（版本 3 / 4）：10 字节标签头 + 若干帧。 */
    private fun id3Tag(frames: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(3, 0, 0))
            write(syncSafe(frames.size))
            write(frames)
        }.toByteArray()
    }

    /** 构造一个 ID3 帧：4 字节 ID + 4 字节长度（v2.3 普通 / v2.4 syncsafe）+ 2 字节标志 + 载荷。 */
    private fun id3Frame(id: String, version: Int, payload: ByteArray): ByteArray {
        return ByteArrayOutputStream().apply {
            write(id.toByteArray(Charsets.US_ASCII))
            write(if (version == 4) syncSafe(payload.size) else int32(payload.size))
            write(byteArrayOf(0, 0))
            write(payload)
        }.toByteArray()
    }

    /** 构造 FLAC 文件头：STREAMINFO 块 + VORBIS_COMMENT 块。 */
    private fun flac(comments: List<String>): ByteArray {
        val vorbis = vorbisComment("NIplayerTest", comments)
        return ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.US_ASCII))
            writeFlacBlockHeader(last = false, type = 0, length = 34)
            write(ByteArray(34))
            writeFlacBlockHeader(last = true, type = 4, length = vorbis.size)
            write(vorbis)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeFlacBlockHeader(last: Boolean, type: Int, length: Int) {
        write(if (last) type or 0x80 else type)
        write(byteArrayOf((length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte()))
    }

    private fun vorbisComment(vendor: String, comments: List<String>): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().apply {
            write(le32(vendorBytes.size))
            write(vendorBytes)
            write(le32(comments.size))
            comments.forEach { entry ->
                val bytes = entry.toByteArray(Charsets.UTF_8)
                write(le32(bytes.size))
                write(bytes)
            }
        }.toByteArray()
    }

    private fun syncSafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun le32(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
        (value ushr 16).toByte(),
        (value ushr 24).toByte(),
    )
}
