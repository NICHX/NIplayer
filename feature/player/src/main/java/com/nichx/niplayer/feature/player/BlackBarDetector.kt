package com.nichx.niplayer.feature.player

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * 智能黑边检测器。
 *
 * 移植自 FFmpeg vf_cropdetect.c 的 cropdetect 算法（MODE_BLACK），核心改进：
 * 1. 平均亮度检测（checkline）：计算整行/列的平均亮度，替代"第一个亮像素"策略，
 *    对噪点/字幕/暗场景更鲁棒
 * 2. Outlier 容忍机制：允许黑边区域存在少量亮噪声像素，避免单个亮像素导致误判
 * 3. 多帧投票：边界只扩大不缩小，跨帧统计更稳定
 * 4. Round 偶数对齐（YUV 兼容性）
 *
 * 使用方式：
 * ```
 * val result = BlackBarDetector.detect(bitmap)
 * val aligned = result?.let { BlackBarDetector.roundAlign(it) }
 * val rect = aligned?.let { Rect(it.left, it.top, it.right + 1, it.bottom + 1) }
 * ```
 *
 * [detect] 是纯函数，多帧状态由调用方管理。
 */
object BlackBarDetector {

    /** 亮度阈值（0-255），低于此值视为黑边像素。 */
    private const val THRESHOLD = 10

    /**
     * Outlier 容忍度（连续亮行/列判定阈值）。
     * 检测到"亮"行/列时，连续超过此值 + 1 次才判定为内容区域。
     * 默认 0 表示一次亮行即判定（最严格），设为 1-2 可容忍少量闪点/噪点。
     */
    private const val MAX_OUTLIERS = 0

    /**
     * 有效区域最小占比（相对全图面积），低于此值视为误判。
     */
    private const val MIN_VALID_AREA_RATIO = 0.5f

    /** 黑边检测用的最大位图边长（超过此值先等比缩放）。 */
    private const val MAX_DETECT_SIZE = 640

    /**
     * 单帧检测结果。
     *
     * @param left 有效画面左边界（含）
     * @param top 有效画面上边界（含）
     * @param right 有效画面右边界（含）
     * @param bottom 有效画面下边界（含）
     * @param bmpWidth 检测用位图宽度
     * @param bmpHeight 检测用位图高度
     */
    data class CropDetectResult(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val bmpWidth: Int,
        val bmpHeight: Int,
    ) {
        val isValid: Boolean get() = right > left && bottom > top

        /** 有效画面宽度（含）。 */
        val width: Int get() = right - left + 1

        /** 有效画面高度（含）。 */
        val height: Int get() = bottom - top + 1
    }

    /**
     * 检测单帧的有效画面区域。
     *
     * @param bitmap 已抓取的视频帧位图
     * @return CropDetectResult；全黑/无效时返回 null
     */
    fun detect(bitmap: Bitmap): CropDetectResult? {
        val scaled = scaleBitmap(bitmap) ?: return null

        val width = scaled.width
        val height = scaled.height
        if (width <= 0 || height <= 0) {
            if (scaled !== bitmap) scaled.recycle()
            return null
        }

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        val sampleStep = computeSampleStep(width)

        // FFmpeg 风格四方向扫描：平均亮度 + outlier
        val top = findTop(pixels, width, height, sampleStep)
        val bottom = findBottom(pixels, width, height, sampleStep)
        val left = findLeft(pixels, width, height, sampleStep)
        val right = findRight(pixels, width, height, sampleStep)

        if (top < 0 || bottom < 0 || left < 0 || right < 0) return null

        // 有效区域过小 → 误判
        val validArea = (bottom - top + 1).toFloat() * (right - left + 1).toFloat()
        val totalArea = width.toFloat() * height.toFloat()
        if (validArea / totalArea < MIN_VALID_AREA_RATIO) return null

        return CropDetectResult(
            left = left, top = top, right = right, bottom = bottom,
            bmpWidth = width, bmpHeight = height,
        )
    }

    /**
     * 合并多帧检测结果（FFmpeg 风格：边界只扩大不缩小）。
     * 每帧的检测结果通过本方法与累计结果合并，最终边界取各帧最小值/最大值。
     *
     * @param previous 之前累计的结果（首次传入 null）
     * @param current 当前帧检测结果
     * @return 合并后的结果
     */
    fun merge(previous: CropDetectResult?, current: CropDetectResult): CropDetectResult {
        if (previous == null) return current
        return CropDetectResult(
            left = maxOf(previous.left, current.left),
            top = maxOf(previous.top, current.top),
            right = minOf(previous.right, current.right),
            bottom = minOf(previous.bottom, current.bottom),
            bmpWidth = current.bmpWidth,
            bmpHeight = current.bmpHeight,
        )
    }

    /**
     * 并集合并：取多帧检测结果的**并集**（内容区域只扩大不缩小）。
     *
     * 与 [merge] 不同，[merge] 取交集（边界缩小）用于对同一帧的多次扫描；
     * 本方法取并集用于多帧累积：只要任何一帧在某位置检测到内容，就保留该边界。
     * 解决暗场景帧误判导致内容区域被裁剪的问题。
     *
     * @param previous 之前累计的结果
     * @param current 当前帧检测结果
     * @return 并集合并后的结果
     */
    fun mergeUnion(previous: CropDetectResult, current: CropDetectResult): CropDetectResult {
        return CropDetectResult(
            left = minOf(previous.left, current.left),
            top = minOf(previous.top, current.top),
            right = maxOf(previous.right, current.right),
            bottom = maxOf(previous.bottom, current.bottom),
            bmpWidth = current.bmpWidth,
            bmpHeight = current.bmpHeight,
        )
    }

    /**
     * Round 对齐到偶数（YUV 兼容性）。
     *
     * FFmpeg 要求裁剪后的 w/h 为偶数（YUV 4:2:0 色度采样要求），
     * x/y 对齐到偶数保证色度平面与亮度平面边界一致。
     * 当前 Android 场景下 round=2 即可满足要求。
     *
     * @param result 待对齐的检测结果
     * @param round 对齐值（默认 2，必须是正偶数）
     * @return 对齐后的结果；无效时返回 null
     */
    fun roundAlign(result: CropDetectResult, round: Int = 2): CropDetectResult? {
        if (!result.isValid) return null

        // 1. x1/y1 向上（向中心）取偶
        var x = (result.left + 1) and 1.inv()
        var y = (result.top + 1) and 1.inv()
        var w = result.right - x + 1
        var h = result.bottom - y + 1

        // 2. 确定有效的 round 值
        val r = if (round <= 1) 16 else round
        val effectiveRound = if (r % 2 != 0) r * 2 else r

        // 3. 宽度对齐到 round 的整数倍，从两端均匀收缩
        if (w >= effectiveRound) {
            val shrinkByW = w % effectiveRound
            w -= shrinkByW
            x += (shrinkByW / 2 + 1) and 1.inv()
        }

        // 4. 高度对齐到 round 的整数倍
        if (h >= effectiveRound) {
            val shrinkByH = h % effectiveRound
            h -= shrinkByH
            y += (shrinkByH / 2 + 1) and 1.inv()
        }

        if (w <= 0 || h <= 0) return null

        return CropDetectResult(
            left = x, top = y, right = x + w - 1, bottom = y + h - 1,
            bmpWidth = result.bmpWidth, bmpHeight = result.bmpHeight,
        )
    }

    /** 将 [CropDetectResult] 转换为 [Rect]（right/bottom exclusive）。 */
    fun toRect(result: CropDetectResult): Rect =
        Rect(result.left, result.top, result.right + 1, result.bottom + 1)

    // ---------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------

    /** 等比缩放过大的位图，防 OOM。 */
    private fun scaleBitmap(bitmap: Bitmap): Bitmap? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        return if (bitmap.width > MAX_DETECT_SIZE || bitmap.height > MAX_DETECT_SIZE) {
            val scale = minOf(
                MAX_DETECT_SIZE.toFloat() / bitmap.width,
                MAX_DETECT_SIZE.toFloat() / bitmap.height,
            )
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
    }

    /** 自适应采样步长：小图全扫，大图下采样。 */
    private fun computeSampleStep(width: Int): Int = (width / 480).coerceIn(1, 8)

    /** 计算一个像素的亮度值（Rec.601）。 */
    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }

    /** 判断透明像素（alpha=0），透明像素不参与亮度计算。 */
    private fun isTransparent(pixel: Int): Boolean =
        (pixel ushr 24) and 0xFF == 0

    /**
     * 计算第 [row] 行的平均亮度（采样）。
     *
     * @param pixels 像素数组（行优先）
     * @param row 行号
     * @param width 位图宽度
     * @param sampleStep 采样步长
     * @return 平均亮度（0-255），全透明返回 -1
     */
    private fun rowAverageLuminance(
        pixels: IntArray,
        row: Int,
        width: Int,
        sampleStep: Int,
    ): Int {
        val rowOffset = row * width
        var total = 0
        var count = 0

        var x = 0
        while (x < width) {
            val pixel = pixels[rowOffset + x]
            if (!isTransparent(pixel)) {
                total += luminance(pixel)
                count++
            }
            x += sampleStep
        }
        // 边界补一个（确保宽度非 sampleStep 整数倍时也采到末列）
        if (width > 0) {
            val lastPixel = pixels[rowOffset + width - 1]
            if (!isTransparent(lastPixel)) {
                total += luminance(lastPixel)
                count++
            }
        }

        return if (count > 0) total / count else -1
    }

    /**
     * 计算第 [col] 列的平均亮度（采样）。
     */
    private fun columnAverageLuminance(
        pixels: IntArray,
        col: Int,
        width: Int,
        height: Int,
        sampleStep: Int,
    ): Int {
        var total = 0
        var count = 0

        var y = 0
        while (y < height) {
            val pixel = pixels[y * width + col]
            if (!isTransparent(pixel)) {
                total += luminance(pixel)
                count++
            }
            y += sampleStep
        }
        // 边界补一个
        if (height > 0) {
            val lastPixel = pixels[(height - 1) * width + col]
            if (!isTransparent(lastPixel)) {
                total += luminance(lastPixel)
                count++
            }
        }

        return if (count > 0) total / count else -1
    }

    // ---------------------------------------------------------------
    // 四方向扫描（FFmpeg checkline + outlier 算法）
    // ---------------------------------------------------------------

    /**
     * 从上往下扫描，找到第一个内容区域的起始行。
     *
     * FFmpeg 算法：
     * - 计算每行的平均亮度
     * - 如果平均亮度 >= [THRESHOLD] → 可能是"亮"行（内容）
     * - 连续 [MAX_OUTLIERS + 1] 行都是"亮"行 → 确定进入内容区域
     * - 返回区域的开头行号
     *
     * BUG-8 备注：[MAX_OUTLIERS] 当前硬编码为 0，`brightCount > MAX_OUTLIERS` 条件
     * 在 brightCount=1 时即满足，`y - brightCount + 1` 恒等于 `y`。如需真正启用 outlier
     * 容忍（容忍少量闪点/噪点），将 [MAX_OUTLIERS] 调到 1-2 即可恢复原始表达式。
     *
     * @return 内容起始行号；全黑返回 -1
     */
    private fun findTop(
        pixels: IntArray,
        width: Int,
        height: Int,
        sampleStep: Int,
    ): Int {
        var brightCount = 0
        for (y in 0 until height) {
            val avg = rowAverageLuminance(pixels, y, width, sampleStep)
            if (avg >= THRESHOLD) {
                brightCount++
                if (brightCount > MAX_OUTLIERS) {
                    // MAX_OUTLIERS=0 时 brightCount 必为 1，返回 y
                    return if (MAX_OUTLIERS == 0) y else y - brightCount + 1
                }
            } else {
                brightCount = 0
            }
        }
        return -1 // 全黑
    }

    /**
     * 从下往上扫描，找到最后一个内容区域的结束行。
     *
     * @return 内容结束行号；全黑返回 -1
     */
    private fun findBottom(
        pixels: IntArray,
        width: Int,
        height: Int,
        sampleStep: Int,
    ): Int {
        var brightCount = 0
        for (y in (height - 1) downTo 0) {
            val avg = rowAverageLuminance(pixels, y, width, sampleStep)
            if (avg >= THRESHOLD) {
                brightCount++
                if (brightCount > MAX_OUTLIERS) {
                    return if (MAX_OUTLIERS == 0) y else y + brightCount - 1
                }
            } else {
                brightCount = 0
            }
        }
        return -1
    }

    /**
     * 从左往右扫描，找到第一个内容区域的起始列。
     *
     * @return 内容起始列号；全黑返回 -1
     */
    private fun findLeft(
        pixels: IntArray,
        width: Int,
        height: Int,
        sampleStep: Int,
    ): Int {
        var brightCount = 0
        for (x in 0 until width) {
            val avg = columnAverageLuminance(pixels, x, width, height, sampleStep)
            if (avg >= THRESHOLD) {
                brightCount++
                if (brightCount > MAX_OUTLIERS) {
                    return if (MAX_OUTLIERS == 0) x else x - brightCount + 1
                }
            } else {
                brightCount = 0
            }
        }
        return -1
    }

    /**
     * 从右往左扫描，找到最后一个内容区域的结束列。
     *
     * @return 内容结束列号；全黑返回 -1
     */
    private fun findRight(
        pixels: IntArray,
        width: Int,
        height: Int,
        sampleStep: Int,
    ): Int {
        var brightCount = 0
        for (x in (width - 1) downTo 0) {
            val avg = columnAverageLuminance(pixels, x, width, height, sampleStep)
            if (avg >= THRESHOLD) {
                brightCount++
                if (brightCount > MAX_OUTLIERS) {
                    return if (MAX_OUTLIERS == 0) x else x + brightCount - 1
                }
            } else {
                brightCount = 0
            }
        }
        return -1
    }
}
