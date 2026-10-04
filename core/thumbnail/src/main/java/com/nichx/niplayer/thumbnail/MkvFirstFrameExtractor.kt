package com.nichx.niplayer.thumbnail

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import com.nichx.niplayer.storage.Storage
import com.nichx.niplayer.storage.StorageFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MKV 首帧提取器：自解析 EBML + MediaCodec 硬解，**绕开 MediaMetadataRetriever 的索引限制**。
 *
 * 背景：部分 MKV（如 BluRay Remux）的索引是「头部 SeekHead → 尾部 SeekHead → Cues」的两跳结构，
 * Android 的 `MatroskaExtractor` 不跟随该间接索引，于是取帧时只能从第 0 字节顺序扫完整个容器
 * （实测 2.5GB 读掉 2034MB、单集 70s），在 SMB 上表现为「卡死」。
 *
 * 而实测「解码首帧所需信息」全部位于文件头部（4 个不同来源样本，含 AVC/HEVC、Remux/WEB-DL）：
 * - `Tracks`（含视频轨 `CodecPrivate` = SPS/PPS）实测 offset ≈ 4.3KB
 * - 首个 `Cluster` 里的视频关键帧实测 offset ≤ 273KB
 *
 * 因此这里只读文件头部 [HEAD_BYTES]，自己解析出 codec 配置与首个关键帧，直接交给 MediaCodec 硬解。
 * **完全不依赖索引**，网络读取量 ~1MB，比任何「解索引」方案都更通用。
 *
 * 有意保留的局限：仅支持 MKV；仅取首帧；仅作 `MediaMetadataRetriever` 失败后的兜底。
 * 若 `Tracks` 不在头部（流式/直播产出）或编码不被支持，返回 null 由上层判失败。
 */
internal object MkvFirstFrameExtractor {

    private const val TAG = "MkvFirstFrame"

    /**
     * 读取的文件头部上限。
     *
     * 实测这类 Remux 开头有 **约 2.2 秒纯黑场**（signalstats YAVG=16.0，2.5s 后才到 33+），
     * 只取 1MB（≈0.5s）必然全是黑屏。16MB 对本站点码率约覆盖 8 秒，足以越过黑场。
     */
    private const val HEAD_BYTES = 16 shl 20

    /** 单个环节的解码等待上限。 */
    private const val DECODE_TIMEOUT_US = 3_000_000L

    /** 最多向后扫描的 Cluster 数，避免异常文件无限找下去。 */
    private const val MAX_CLUSTERS_SCAN = 64

    /** 最多收集的视频包数 / 总字节（需覆盖黑场之后的画面，故放宽到与头部同量级）。 */
    private const val MAX_PACKETS = 600
    private const val MAX_PACKET_BYTES = 16 shl 20

    /** 判定「黑场」的平均亮度阈值（limited range 纯黑 Y=16，实测有画面时 ≥33）。 */
    private const val BLACK_LUMA_THRESHOLD = 24

    /** 亮度达到该值即认为足够亮、提前采用；否则取窗口内最亮的一帧。 */
    private const val GOOD_LUMA = 80

    /** 仅当亮度比当前最佳高出该幅度才重新转换，避免每帧都做 YUV→RGB。 */
    private const val LUMA_IMPROVE_MARGIN = 12

    /** YUV→RGB 抽样后的目标短边像素数（缩略图足够，且把转换量降一个数量级）。 */
    private const val TARGET_SHORT_SIDE = 360

    suspend fun extract(storage: Storage, file: StorageFile): Bitmap? = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val head = readHead(storage, file) ?: return@withContext null
            val parsed = parse(head)
            if (parsed == null) {
                Log.w(TAG, "parse failed: ${file.name}（头部未找到视频轨/CodecPrivate/首个关键帧）")
                return@withContext null
            }
            val bitmap = decode(parsed)
            Log.d(
                TAG,
                "extract: ${file.name} codec=${parsed.codecId} ${parsed.width}x${parsed.height} " +
                    "head=${head.size}B ${SystemClock.elapsedRealtime() - startedAt}ms result=${bitmap != null}",
            )
            bitmap
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "extract failed: ${file.name}: ${e.message}")
            null
        }
    }

    // ---------- 读取 + 解析 ----------

    /** 顺序读取文件头部 [HEAD_BYTES]（不足则全读）。SMB 下约等于一次块读。 */
    private suspend fun readHead(storage: Storage, file: StorageFile): ByteArray? {
        val input = storage.openInputStream(file)
        return input.use { ins ->
            val out = ByteArray(HEAD_BYTES)
            var off = 0
            while (off < out.size) {
                val n = try {
                    ins.read(out, off, out.size - off)
                } catch (_: Exception) {
                    -1
                }
                if (n <= 0) break
                off += n
            }
            if (off <= 0) null else out.copyOf(off)
        }
    }

    private class ParsedMkv(
        val codecId: String,
        val codecPrivate: ByteArray,
        val width: Int,
        val height: Int,
        /** 从首个关键帧起的视频包（长度前缀 NAL，即 AVCC/HVCC 形式），按流顺序。 */
        val packets: List<ByteArray>,
    )

    private class TrackInfo {
        var found = false
        var number = -1L
        var codecId: String? = null
        var codecPrivate: ByteArray? = null
        var width = 0
        var height = 0
    }

    private fun parse(b: ByteArray): ParsedMkv? {
        // 顶层：EBML 头 → Segment
        val ebml = readId(b, 0) ?: return null
        if (ebml.first != ID_EBML) return null
        val ebmlSize = readSize(b, ebml.second) ?: return null
        val segIdPos = ebmlSize.second + ebmlSize.first.toInt()
        val seg = readId(b, segIdPos) ?: return null
        if (seg.first != ID_SEGMENT) return null
        val segSize = readSize(b, seg.second) ?: return null
        val segBody = segSize.second
        val segEnd = endOf(b,segBody, segSize.first)

        // 第一遍：找视频轨（Tracks 正常在头部，先拿到 codec 配置）
        val track = TrackInfo()
        forEachChild(b, segBody, segEnd) { id, contentPos, size, _ ->
            if (id == ID_TRACKS) parseTracks(b, contentPos, endOf(b,contentPos, size), track)
        }
        val codecId = track.codecId ?: return null
        val codecPrivate = track.codecPrivate ?: return null
        if (!track.found) return null

        // 第二遍：从首个关键帧起收集视频包（多喂几帧，见 [findVideoPackets] 说明）
        val packets = findVideoPackets(b, segBody, segEnd, track.number)
        if (packets.isEmpty()) return null

        return ParsedMkv(codecId, codecPrivate, track.width, track.height, packets)
    }

    private fun parseTracks(b: ByteArray, start: Int, end: Int, out: TrackInfo) {
        forEachChild(b, start, end) { id, contentPos, size, _ ->
            if (id != ID_TRACK_ENTRY || out.found) return@forEachChild
            var number = -1L
            var type = -1
            var codecId: String? = null
            var priv: ByteArray? = null
            var w = 0
            var h = 0
            val trackEnd = endOf(b,contentPos, size)
            forEachChild(b, contentPos, trackEnd) { eid, ecp, esz, _ ->
                val e = endOf(b,ecp, esz)
                when (eid) {
                    ID_TRACK_NUMBER -> number = readUInt(b, ecp, e)
                    ID_TRACK_TYPE -> type = readUInt(b, ecp, e).toInt()
                    ID_CODEC_ID -> codecId = String(b, ecp, e - ecp, Charsets.US_ASCII).trimEnd('\u0000')
                    ID_CODEC_PRIVATE -> priv = b.copyOfRange(ecp, e)
                    // PixelWidth/PixelHeight 在 Video 元素里，不是 TrackEntry 的直接子元素
                    ID_VIDEO -> forEachChild(b, ecp, e) { vid, vcp, vsz, _ ->
                        val ve = endOf(b,vcp, vsz)
                        when (vid) {
                            ID_PIXEL_WIDTH -> w = readUInt(b, vcp, ve).toInt()
                            ID_PIXEL_HEIGHT -> h = readUInt(b, vcp, ve).toInt()
                        }
                    }
                }
            }
            // TrackType 1 = video，取第一个视频轨
            if (type == 1) {
                out.found = true
                out.number = number
                out.codecId = codecId
                out.codecPrivate = priv
                out.width = w
                out.height = h
            }
        }
    }

    /**
     * 从首个 Cluster 起，按流顺序收集视频轨的包（自第一个关键帧开始）。
     *
     * 只投喂单帧时，部分解码器不会立即吐帧（需要更多数据或 EOS 才输出），实测表现为「3s 等满仍无输出」。
     * 因此这里多收集一些包一起投喂，取到第一个输出帧即返回，兼容性更好。
     * lacing 的视频帧极少见，遇到直接跳过（不解析 lacing，避免复杂化）。
     */
    private fun findVideoPackets(b: ByteArray, start: Int, end: Int, trackNumber: Long): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var started = false
        var clusters = 0
        var totalBytes = 0
        forEachChild(b, start, end) { id, contentPos, size, _ ->
            if (id != ID_CLUSTER || totalBytes >= MAX_PACKET_BYTES || out.size >= MAX_PACKETS) {
                return@forEachChild
            }
            if (clusters++ >= MAX_CLUSTERS_SCAN) return@forEachChild
            val clusterEnd = endOf(b,contentPos, size)
            forEachChild(b, contentPos, clusterEnd) { cid, ccp, csz, _ ->
                if (cid != ID_SIMPLE_BLOCK || totalBytes >= MAX_PACKET_BYTES || out.size >= MAX_PACKETS) {
                    return@forEachChild
                }
                val cEnd = endOf(b,ccp, csz)
                val tn = readVintValue(b, ccp) ?: return@forEachChild
                if (tn.first != trackNumber) return@forEachChild
                val flagPos = tn.second + 2 // 跳过 2 字节 timecode
                if (flagPos + 1 > cEnd) return@forEachChild
                val flags = b[flagPos].toInt() and 0xFF
                if ((flags shr 1) and 0x03 != 0) return@forEachChild // 带 lacing，跳过
                if (!started) {
                    // 从第一个关键帧开始收集，之前的依赖帧无法独立解码
                    if (flags and 0x80 == 0) return@forEachChild
                    started = true
                }
                val payloadStart = flagPos + 1
                if (payloadStart >= cEnd) return@forEachChild
                val packet = b.copyOfRange(payloadStart, cEnd)
                out.add(packet)
                totalBytes += packet.size
            }
        }
        return out
    }

    // ---------- 解码 ----------

    private class Csd(val csd0: ByteArray, val csd1: ByteArray?, val nalLengthSize: Int)

    private fun decode(p: ParsedMkv): Bitmap? {
        val mime = when {
            p.codecId.startsWith("V_MPEG4/ISO/AVC") -> "video/avc"
            p.codecId.startsWith("V_MPEGH/ISO/HEVC") -> "video/hevc"
            else -> {
                Log.w(TAG, "unsupported codec: ${p.codecId}")
                return null
            }
        }
        if (p.width <= 0 || p.height <= 0) {
            Log.w(TAG, "bad video size: ${p.width}x${p.height}")
            return null
        }
        val csd = buildCsd(mime, p.codecPrivate)
        if (csd == null) {
            Log.w(TAG, "csd parse failed (codecPrivate=${p.codecPrivate.size}B)")
            return null
        }
        val frames = p.packets.mapNotNull { toAnnexB(it, csd.nalLengthSize) }
        if (frames.isEmpty()) {
            Log.w(TAG, "annexb convert failed (packets=${p.packets.size} nalLengthSize=${csd.nalLengthSize})")
            return null
        }

        val format = MediaFormat.createVideoFormat(mime, p.width, p.height).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(csd.csd0))
            csd.csd1?.let { setByteBuffer("csd-1", ByteBuffer.wrap(it)) }
        }

        var codec: MediaCodec? = null
        try {
            // 不挂 Surface：Surface 输出在本机（高通 Codec2）走 GraphicBuffer 私有格式，
            // ImageReader 请求 RGBA_8888 时锁 YCbCr 会失败（lockAsyncYCbCr -22），
            // 随后读 plane 直接 SIGSEGV 崩进程。故走 ByteBuffer 输出 + getOutputImage(YUV_420_888) 自转 RGB。
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + DECODE_TIMEOUT_US / 1000
            var fed = 0
            var eosQueued = false
            // 黑场跳过：开头约 2.2s 是纯黑。黑帧仅作最终兜底。
            // 帧选择：越过黑场后画面仍在渐亮，若取「第一个非黑帧」会得到刚结束黑场的暗帧
            // （实测平均亮度仅 20，而 3~5s 的正常画面约 48），明显比刮削缩略图暗。
            // 故取窗口内**最亮**的一帧；亮到 [GOOD_LUMA] 即提前返回。
            var blackFallback: Bitmap? = null
            var bestFrame: Bitmap? = null
            var bestLuma = -1
            var lastLuma = -1
            while (SystemClock.elapsedRealtime() < deadline) {
                // 边投喂边收帧：输入缓冲有限，不能一次全喂完再等输出
                if (fed < frames.size) {
                    val inIndex = codec.dequeueInputBuffer(0)
                    if (inIndex >= 0) {
                        val data = frames[fed]
                        codec.getInputBuffer(inIndex)?.apply {
                            clear()
                            put(data)
                        }
                        codec.queueInputBuffer(
                            inIndex, 0, data.size, fed * 33_000L,
                            if (fed == 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                        )
                        fed++
                        continue
                    }
                } else if (!eosQueued) {
                    // 送 EOS 逼解码器吐出缓冲中的帧（许多解码器不发 EOS 就不出首帧）
                    val inIndex = codec.dequeueInputBuffer(0)
                    if (inIndex >= 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosQueued = true
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val image = codec.getOutputImage(outIndex)
                    if (image == null) {
                        codec.releaseOutputBuffer(outIndex, false)
                        continue
                    }
                    val luma = meanLuma(image)
                    lastLuma = luma
                    val isBlack = luma < BLACK_LUMA_THRESHOLD
                    // 只在「需要」时转换（YUV→RGB 是这里最贵的一步）
                    val shouldConvert = when {
                        isBlack -> blackFallback == null
                        bestFrame == null -> true
                        luma >= GOOD_LUMA -> true
                        luma >= bestLuma + LUMA_IMPROVE_MARGIN -> true
                        else -> false
                    }
                    val bitmap = try {
                        if (shouldConvert) yuvToBitmap(image) else null
                    } finally {
                        image.close()
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bitmap != null) {
                        if (isBlack) {
                            blackFallback?.recycle()
                            blackFallback = bitmap
                        } else {
                            bestFrame?.recycle()
                            bestFrame = bitmap
                            bestLuma = luma
                            if (luma >= GOOD_LUMA) {
                                blackFallback?.recycle()
                                Log.d(
                                    TAG,
                                    "decoded ok: fed=$fed/${frames.size} luma=$luma ${bitmap.width}x${bitmap.height}",
                                )
                                return bitmap
                            }
                        }
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "output format: ${codec.outputFormat}")
                }
            }
            val picked = bestFrame ?: blackFallback
            if (picked === bestFrame) blackFallback?.recycle()
            Log.d(
                TAG,
                "decode end: fed=$fed/${frames.size} eos=$eosQueued bestLuma=$bestLuma lastLuma=$lastLuma " +
                    "picked=${if (picked == null) "null" else "${picked.width}x${picked.height}"}",
            )
            return picked
        } catch (e: Exception) {
            Log.w(TAG, "decode failed: ${e.message}")
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }

    /**
     * 抽样估算 Y 平面平均亮度（0-255）。只取约 32×18 个采样点，开销可忽略。
     * 用于识别片头黑场（limited range 纯黑 Y=16）。
     */
    private fun meanLuma(image: Image): Int {
        val yPlane = image.planes.firstOrNull() ?: return 255
        val buf = yPlane.buffer
        val rowStride = yPlane.rowStride
        val crop = image.cropRect
        val w = if (crop.width() > 0) minOf(crop.width(), image.width) else image.width
        val h = if (crop.height() > 0) minOf(crop.height(), image.height) else image.height
        val left = if (crop.width() > 0) crop.left else 0
        val top = if (crop.height() > 0) crop.top else 0
        if (w <= 0 || h <= 0 || rowStride <= 0) return 255
        val stepX = maxOf(1, w / 32)
        val stepY = maxOf(1, h / 18)
        var sum = 0L
        var count = 0
        var j = 0
        while (j < h) {
            val row = (top + j) * rowStride + left
            var i = 0
            while (i < w) {
                sum += (buf.get(row + i).toInt() and 0xFF).toLong()
                count++
                i += stepX
            }
            j += stepY
        }
        return if (count == 0) 255 else (sum / count).toInt()
    }

    /**
     * YUV_420_888 → Bitmap（ARGB_8888）。
     *
     * 按 [TARGET_SHORT_SIDE] 做**抽样降采样**：缩略图最终只有几百像素宽，无需逐像素转换，
     * 抽样后 1080p 只需转约 23 万像素（数十 ms），比全量转换快一个数量级。
     * 同时用 [Image.getCropRect] 去掉编码对齐产生的多余行（实测 1080 的流输出高度为 1088）。
     */
    private fun yuvToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.size < 3) return null
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        if (yRowStride <= 0 || uvRowStride <= 0 || uvPixelStride <= 0) return null

        val crop = image.cropRect
        val viewW = if (crop.width() > 0) minOf(crop.width(), image.width) else image.width
        val viewH = if (crop.height() > 0) minOf(crop.height(), image.height) else image.height
        val left = if (crop.width() > 0) crop.left else 0
        val top = if (crop.height() > 0) crop.top else 0
        if (viewW <= 0 || viewH <= 0) return null

        val step = maxOf(1, minOf(viewW, viewH) / TARGET_SHORT_SIDE)
        val outW = viewW / step
        val outH = viewH / step
        if (outW <= 0 || outH <= 0) return null

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val pixels = IntArray(outW * outH)
        for (j in 0 until outH) {
            val srcY = top + j * step
            val yRow = srcY * yRowStride
            val uvRow = (srcY / 2) * uvRowStride
            val dstRow = j * outW
            for (i in 0 until outW) {
                val srcX = left + i * step
                val y = yBuf.get(yRow + srcX).toInt() and 0xFF
                val uvIndex = uvRow + (srcX / 2) * uvPixelStride
                val u = (uBuf.get(uvIndex).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvIndex).toInt() and 0xFF) - 128
                // BT.601 limited range
                val yv = (y - 16).coerceAtLeast(0) * 1.164f
                val r = (yv + 1.596f * v).toInt().coerceIn(0, 255)
                val g = (yv - 0.392f * u - 0.813f * v).toInt().coerceIn(0, 255)
                val b = (yv + 2.017f * u).toInt().coerceIn(0, 255)
                pixels[dstRow + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
    }

    // ---------- codec 配置（CodecPrivate → csd） ----------

    private val START_CODE = byteArrayOf(0, 0, 0, 1)

    private fun buildCsd(mime: String, codecPrivate: ByteArray): Csd? = when (mime) {
        "video/avc" -> parseAvcCsd(codecPrivate)
        "video/hevc" -> parseHevcCsd(codecPrivate)
        else -> null
    }

    /** AVCDecoderConfigurationRecord → csd-0(SPS) / csd-1(PPS)。 */
    private fun parseAvcCsd(cp: ByteArray): Csd? {
        if (cp.size < 7) return null
        val nalLengthSize = (cp[4].toInt() and 0x03) + 1
        var pos = 5
        val numSps = cp[pos].toInt() and 0x1F
        pos++
        var sps: ByteArray? = null
        repeat(numSps) {
            val len = readU16(cp, pos) ?: return null
            pos += 2
            if (pos + len > cp.size) return null
            if (sps == null) sps = withStartCode(cp, pos, len)
            pos += len
        }
        if (pos >= cp.size) return null
        val numPps = cp[pos].toInt() and 0xFF
        pos++
        var pps: ByteArray? = null
        repeat(numPps) {
            val len = readU16(cp, pos) ?: return null
            pos += 2
            if (pos + len > cp.size) return null
            if (pps == null) pps = withStartCode(cp, pos, len)
            pos += len
        }
        val s = sps ?: return null
        return Csd(s, pps, nalLengthSize)
    }

    /** hvcC → csd-0(VPS+SPS+PPS 拼接)。 */
    private fun parseHevcCsd(cp: ByteArray): Csd? {
        if (cp.size < 23) return null
        val nalLengthSize = (cp[21].toInt() and 0x03) + 1
        var pos = 22
        val numArrays = cp[pos].toInt() and 0xFF
        pos++
        val out = ByteArrayOutputStream(cp.size)
        var found = false
        repeat(numArrays) {
            if (pos + 3 > cp.size) return if (found) Csd(out.toByteArray(), null, nalLengthSize) else null
            val nalType = cp[pos].toInt() and 0x3F
            var numNalus = (cp[pos + 1].toInt() and 0xFF) shl 8 or (cp[pos + 2].toInt() and 0xFF)
            pos += 3
            while (numNalus-- > 0) {
                val len = readU16(cp, pos) ?: return if (found) Csd(out.toByteArray(), null, nalLengthSize) else null
                pos += 2
                if (pos + len > cp.size) return if (found) Csd(out.toByteArray(), null, nalLengthSize) else null
                // 32=VPS, 33=SPS, 34=PPS
                if (nalType == 32 || nalType == 33 || nalType == 34) {
                    out.write(START_CODE)
                    out.write(cp, pos, len)
                    found = true
                }
                pos += len
            }
        }
        return if (found) Csd(out.toByteArray(), null, nalLengthSize) else null
    }

    /** 长度前缀 NAL（AVCC/HVCC）→ Annex-B（起始码），供 MediaCodec 输入。 */
    private fun toAnnexB(data: ByteArray, nalLengthSize: Int): ByteArray? {
        if (nalLengthSize !in 1..4) return null
        val out = ByteArrayOutputStream(data.size + 16)
        var pos = 0
        while (pos + nalLengthSize <= data.size) {
            var len = 0
            for (i in 0 until nalLengthSize) len = (len shl 8) or (data[pos + i].toInt() and 0xFF)
            pos += nalLengthSize
            if (len <= 0 || pos + len > data.size) return null
            out.write(START_CODE)
            out.write(data, pos, len)
            pos += len
        }
        return if (out.size() > 0) out.toByteArray() else null
    }

    private fun withStartCode(src: ByteArray, offset: Int, len: Int): ByteArray {
        val out = ByteArray(START_CODE.size + len)
        System.arraycopy(START_CODE, 0, out, 0, START_CODE.size)
        System.arraycopy(src, offset, out, START_CODE.size, len)
        return out
    }

    private fun readU16(b: ByteArray, pos: Int): Int? {
        if (pos + 2 > b.size) return null
        return ((b[pos].toInt() and 0xFF) shl 8) or (b[pos + 1].toInt() and 0xFF)
    }

    // ---------- EBML 基础 ----------

    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACK_ENTRY = 0xAEL
    private const val ID_TRACK_NUMBER = 0xD7L
    private const val ID_TRACK_TYPE = 0x83L
    private const val ID_CODEC_ID = 0x86L
    private const val ID_CODEC_PRIVATE = 0x63A2L
    private const val ID_VIDEO = 0xE0L
    private const val ID_PIXEL_WIDTH = 0xB0L
    private const val ID_PIXEL_HEIGHT = 0xBAL
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_SIMPLE_BLOCK = 0xA3L

    /** 元素 ID：首字节前导 1 的位置决定长度（1~4 字节），返回 (id, 下一位置)。 */
    private fun readId(b: ByteArray, pos: Int): Pair<Long, Int>? {
        if (pos < 0 || pos >= b.size) return null
        val first = b[pos].toInt() and 0xFF
        val len = when {
            first and 0x80 != 0 -> 1
            first and 0x40 != 0 -> 2
            first and 0x20 != 0 -> 3
            first and 0x10 != 0 -> 4
            else -> return null
        }
        if (pos + len > b.size) return null
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
        return v to (pos + len)
    }

    /** 元素大小 VINT：返回 (size, 下一位置, 是否 unknown-length)。 */
    private fun readSize(b: ByteArray, pos: Int): Triple<Long, Int, Boolean>? {
        if (pos < 0 || pos >= b.size) return null
        val first = b[pos].toInt() and 0xFF
        var mask = 0x80
        var len = 1
        while (len <= 8 && (first and mask) == 0) {
            mask = mask shr 1
            len++
        }
        if (len > 8 || pos + len > b.size) return null
        var v = (first and (mask - 1)).toLong()
        for (i in 1 until len) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
        val unknown = v == (1L shl (7 * len)) - 1
        return Triple(v, pos + len, unknown)
    }

    /** 读取 VINT 的数值（去掉前导标记位），用于 SimpleBlock 的 track number。 */
    private fun readVintValue(b: ByteArray, pos: Int): Pair<Long, Int>? {
        if (pos < 0 || pos >= b.size) return null
        val first = b[pos].toInt() and 0xFF
        var mask = 0x80
        var len = 1
        while (len <= 8 && (first and mask) == 0) {
            mask = mask shr 1
            len++
        }
        if (len > 8 || pos + len > b.size) return null
        var v = (first and (mask - 1)).toLong()
        for (i in 1 until len) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
        return v to (pos + len)
    }

    private fun readUInt(b: ByteArray, start: Int, end: Int): Long {
        var v = 0L
        var i = start
        while (i < end) {
            v = (v shl 8) or (b[i].toLong() and 0xFF)
            i++
        }
        return v
    }

    /**
     * 元素内容结束位置：**必须截断到已读缓冲末尾**。
     *
     * 缓冲区只有前 [HEAD_BYTES] 字节，末尾元素的声明长度常超出缓冲区；若按声明长度做
     * `copyOfRange`/`readUInt` 会直接 IndexOutOfBounds（实测报 toIndex > size）。
     */
    private fun endOf(b: ByteArray, contentPos: Int, size: Long): Int {
        val end = contentPos.toLong() + size
        return minOf(end, b.size.toLong()).toInt().coerceAtLeast(contentPos)
    }

    /** 遍历 [start, end) 内的同级元素；内容越过缓冲则停止（无法继续定位后续元素）。 */
    private inline fun forEachChild(
        b: ByteArray,
        start: Int,
        end: Int,
        action: (id: Long, contentPos: Int, size: Long, sizeEnd: Int) -> Unit,
    ) {
        var pos = start
        val limit = minOf(end, b.size)
        while (pos < limit - 1) {
            val (id, afterId) = readId(b, pos) ?: return
            val size = readSize(b, afterId) ?: return
            action(id, size.second, size.first, afterId)
            if (size.third) return // unknown-length：无法跳过，停止
            val next = size.second.toLong() + size.first
            if (next <= pos || next > limit) return
            pos = next.toInt()
        }
    }
}
