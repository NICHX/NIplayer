package com.nichx.niplayer.thumbnail

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * 音频头部自解析的单测：验证 FLAC 内嵌封面定位/取图与 ID3v2 标签长度解析。
 *
 * 背景：远程音频只读头部，且部分平台（MIUI）MediaMetadataRetriever 对 FLAC 内嵌封面
 * 直接失败，因此改为自行解析 FLAC PICTURE 块取出图片字节。
 */
class AudioHeaderScanTest {

    private fun intTo4(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte(),
    )

    private fun blockHeader(type: Int, len: Int, last: Boolean): ByteArray = byteArrayOf(
        ((if (last) 0x80 else 0) or (type and 0x7F)).toByte(),
        ((len shr 16) and 0xFF).toByte(),
        ((len shr 8) and 0xFF).toByte(),
        (len and 0xFF).toByte(),
    )

    private fun flac(vararg body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        body.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun picturePayload(image: ByteArray, mime: String = "image/png"): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(intTo4(3)) // picture type = front cover
        out.write(intTo4(mime.length)); out.write(mime.toByteArray())
        out.write(intTo4(0)) // description 长度
        out.write(intTo4(2048)); out.write(intTo4(2048)) // 宽 / 高
        out.write(intTo4(32)); out.write(intTo4(0)) // 位深 / 颜色数
        out.write(intTo4(image.size)); out.write(image)
        return out.toByteArray()
    }

    @Test
    fun `scanFlacMetadata 定位 PICTURE 块区间`() {
        val payload = picturePayload(ByteArray(1000))
        val bytes = flac(
            blockHeader(0, 34, last = false), ByteArray(34),
            blockHeader(6, payload.size, last = true), payload,
        )
        val scan = scanFlacMetadata(bytes)
        assertTrue(scan is FlacScan.Picture)
        scan as FlacScan.Picture
        assertEquals(payload.size, scan.end - scan.start)
    }

    @Test
    fun `scanFlacMetadata PICTURE 超出已读字节时请求更多`() {
        val payload = picturePayload(ByteArray(5000))
        val bytes = flac(
            blockHeader(0, 34, last = false), ByteArray(34),
            blockHeader(6, payload.size, last = true), payload.copyOfRange(0, 10),
        )
        val scan = scanFlacMetadata(bytes)
        assertTrue(scan is FlacScan.NeedMore)
        scan as FlacScan.NeedMore
        // 需要读到 PICTURE 数据末尾：4(magic)+4+34(streaminfo)+4(picture 头)+payload
        assertEquals(4 + 4 + 34 + 4 + payload.size, scan.targetBytes)
    }

    @Test
    fun `scanFlacMetadata 无 PICTURE 返回 NoPicture`() {
        val bytes = flac(blockHeader(0, 34, last = true), ByteArray(34))
        assertTrue(scanFlacMetadata(bytes) is FlacScan.NoPicture)
    }

    @Test
    fun `flacPictureBytes 取出图片数据`() {
        val image = ByteArray(1000) { it.toByte() }
        val payload = picturePayload(image)
        val block = flac(blockHeader(6, payload.size, last = true), payload)
        // 图片块数据起点 = 4(magic) + 4(块头)
        assertArrayEquals(image, flacPictureBytes(block, 8, 8 + payload.size))
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
