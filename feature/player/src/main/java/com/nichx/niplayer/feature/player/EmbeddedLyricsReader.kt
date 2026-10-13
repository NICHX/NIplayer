package com.nichx.niplayer.feature.player

import java.nio.charset.Charset

/**
 * 从音频文件**头部字节**中提取「内嵌歌词」。
 *
 * Android 平台没有标准 API 暴露内嵌歌词（MediaMetadataRetriever / MediaStore 均无歌词字段），
 * 因此沿用封面提取（`AudioCoverExtractor`）的思路，按容器自行解析：
 * - **ID3v2** 的 `USLT`（v2.3/2.4）/ `ULT`（v2.2）帧：MP3 及以 ID3 存标签的容器（WAV/AIFF/DSF 等）；
 * - **FLAC** 的 `VORBIS_COMMENT` 块中的 `LYRICS` / `UNSYNCEDLYRICS` 字段。
 *
 * 两者的标签区都位于文件开头，故只需读头部、无需读取整个音频文件。
 * 内嵌歌词多为「未同步歌词」（无时间轴），也可能是 LRC 文本；是否有时间轴由调用方
 * （[LrcParser]）判定——只有含时间轴的内容才会被渲染成同步歌词。
 */
internal object EmbeddedLyricsReader {

    /** 内嵌歌词首次读取的建议头部字节数。 */
    const val DEFAULT_HEAD_BYTES = 2 * 1024 * 1024

    /** 追读（标签区超出首次头部）允许的最大字节数，防止异常文件导致超大读取。 */
    const val MAX_HEAD_BYTES = 8 * 1024 * 1024

    /** 从头部字节解析内嵌歌词；未找到 / 不支持该容器 / 缓冲区不足时返回 null。 */
    fun parse(head: ByteArray): String? = when (val result = scan(head)) {
        is Scan.Found -> result.text
        else -> null
    }

    /**
     * 判断是否需要追读更多头部字节（标签区超出当前缓冲区），返回所需的总字节数；否则返回 null。
     *
     * 返回值已夹在 [MAX_HEAD_BYTES] 内：超出上限一律视为放弃（返回 null），避免异常文件造成超大读取。
     */
    fun requiredHeadBytes(head: ByteArray): Int? {
        val need = when (val result = scan(head)) {
            is Scan.NeedMore -> result.bytes
            else -> return null
        }
        if (need <= head.size || need > MAX_HEAD_BYTES) return null
        return need
    }

    /** 解析结果：命中 / 确认无内嵌歌词 / 需要更多字节才能判定。 */
    private sealed interface Scan {
        data class Found(val text: String) : Scan
        object Absent : Scan
        data class NeedMore(val bytes: Int) : Scan
    }

    private fun scan(head: ByteArray): Scan = when {
        isId3(head) -> scanId3(head)
        isFlac(head) -> scanFlac(head)
        else -> Scan.Absent
    }

    // ---------------- ID3v2 ----------------

    private fun isId3(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()

    private fun scanId3(bytes: ByteArray): Scan {
        if (bytes.size < 10) return Scan.NeedMore(10)
        val version = bytes[3].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        val size = syncSafeInt(bytes, 6) ?: return Scan.Absent
        val tagEnd = 10 + size
        if (tagEnd > bytes.size) return Scan.NeedMore(tagEnd)

        var offset = 10
        // 扩展头（少见）：跳过。v2.4 的长度为 syncsafe 且含自身；v2.3 为普通 4 字节且不含自身。
        if ((flags and 0x40) != 0) {
            offset = when (version) {
                4 -> offset + (syncSafeInt(bytes, offset) ?: return Scan.Absent)
                3 -> offset + 4 + (readInt32(bytes, offset) ?: return Scan.Absent)
                else -> offset
            }
        }

        val idLength = if (version == 2) 3 else 4
        val frameHeaderSize = if (version == 2) 6 else 10
        while (offset + frameHeaderSize <= tagEnd) {
            val frameId = asciiId(bytes, offset, idLength) ?: return Scan.Absent
            if (frameId.isEmpty()) return Scan.Absent // 到达填充区（0x00）
            val frameSize = when {
                version == 2 -> readInt24(bytes, offset + 3)
                version == 4 -> syncSafeInt(bytes, offset + 4)
                else -> readInt32(bytes, offset + 4)
            } ?: return Scan.Absent
            if (frameSize <= 0) return Scan.Absent
            val dataStart = offset + frameHeaderSize
            val dataEnd = dataStart + frameSize
            if (dataEnd > tagEnd) break
            if (frameId == "USLT" || frameId == "ULT") {
                decodeUslt(bytes, dataStart, dataEnd)?.let { return Scan.Found(it) }
            }
            offset = dataEnd
        }
        return Scan.Absent
    }

    /**
     * 解析 USLT/ULT 帧载荷：
     * 1 字节编码 + 3 字节语言 + 「内容描述符」(以 0x00 / 0x0000 结尾) + 歌词正文。
     */
    private fun decodeUslt(bytes: ByteArray, start: Int, end: Int): String? {
        if (start + 4 > end) return null
        val encoding = bytes[start].toInt() and 0xFF
        val utf16 = encoding == 1 || encoding == 2
        val descriptorEnd = if (utf16) {
            indexOfDoubleZero(bytes, start + 4, end)?.plus(2)
        } else {
            indexOfSingleZero(bytes, start + 4, end)?.plus(1)
        } ?: return null
        if (descriptorEnd >= end) return null
        val charset = charsetOf(encoding) ?: return null
        return String(bytes, descriptorEnd, end - descriptorEnd, charset)
            .trim('\u0000', ' ', '\uFEFF')
            .takeIf { it.isNotBlank() }
    }

    /** 单字节编码：返回内容描述符终止符 0x00 的下标。 */
    private fun indexOfSingleZero(bytes: ByteArray, start: Int, end: Int): Int? {
        for (i in start until end) {
            if (bytes[i].toInt() == 0) return i
        }
        return null
    }

    /** UTF-16 编码：按 2 字节对齐返回内容描述符终止符 0x0000 的首字节下标。 */
    private fun indexOfDoubleZero(bytes: ByteArray, start: Int, end: Int): Int? {
        var i = start
        while (i + 1 < end) {
            if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i
            i += 2
        }
        return null
    }

    private fun charsetOf(encoding: Int): Charset? = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> null
    }

    private fun syncSafeInt(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size) return null
        var value = 0
        for (i in 0 until 4) {
            val b = bytes[offset + i].toInt() and 0xFF
            if ((b and 0x80) != 0) return null
            value = (value shl 7) or (b and 0x7F)
        }
        return value
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size) return null
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readInt24(bytes: ByteArray, offset: Int): Int? {
        if (offset + 3 > bytes.size) return null
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
    }

    /** 读取 [length] 字节的 ASCII 帧 ID；遇到 0x00 视为填充并返回空串；非法字节返回 null。 */
    private fun asciiId(bytes: ByteArray, offset: Int, length: Int): String? {
        if (offset + length > bytes.size) return null
        val builder = StringBuilder(length)
        for (i in 0 until length) {
            val b = bytes[offset + i].toInt() and 0xFF
            if (b == 0) return "" // 填充区
            if (b < 0x20 || b > 0x7E) return null
            builder.append(b.toChar())
        }
        return builder.toString()
    }

    // ---------------- FLAC (Vorbis Comment) ----------------

    private fun isFlac(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()

    private fun scanFlac(bytes: ByteArray): Scan {
        var offset = 4
        while (offset + 4 <= bytes.size) {
            val b0 = bytes[offset].toInt() and 0xFF
            val last = (b0 and 0x80) != 0
            val type = b0 and 0x7F
            val length = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            val dataStart = offset + 4
            val dataEnd = dataStart + length
            if (type == 4) { // VORBIS_COMMENT
                if (dataEnd > bytes.size) return Scan.NeedMore(dataEnd)
                val text = parseVorbisComment(bytes, dataStart, dataEnd)
                return if (text != null) Scan.Found(text) else Scan.Absent
            }
            if (last) return Scan.Absent
            if (dataEnd > bytes.size) return Scan.NeedMore(dataEnd)
            offset = dataEnd
        }
        // 头部不足以读到下一个块头
        return Scan.NeedMore(bytes.size + 64)
    }

    /** 解析 Vorbis Comment 载荷：厂商串 + 注释条数 + 每条 `KEY=VALUE`（UTF-8，键不区分大小写）。 */
    private fun parseVorbisComment(bytes: ByteArray, start: Int, end: Int): String? {
        var offset = start
        fun readLe32(): Int? {
            if (offset + 4 > end) return null
            val value = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return value
        }
        val vendorLength = readLe32() ?: return null
        if (vendorLength < 0 || offset + vendorLength > end) return null
        offset += vendorLength
        val count = readLe32() ?: return null
        if (count < 0) return null
        repeat(count) {
            val length = readLe32() ?: return null
            if (length < 0 || offset + length > end) return null
            val entry = String(bytes, offset, length, Charsets.UTF_8)
            offset += length
            val separator = entry.indexOf('=')
            if (separator > 0) {
                val key = entry.substring(0, separator).replace(" ", "").uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS") {
                    entry.substring(separator + 1).takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return null
    }
}
