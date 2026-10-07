package com.nichx.niplayer.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * 兜底配色的原始色（会被 [normalizeForFlow] 归一到明度带）。
 *
 * 排版原则（原方案全挤在深蓝~紫、饱和偏低，读起来是一片"深色"）：
 * - **色相**从蓝青经靛/紫/品红平滑铺到玫红（同类相邻色，不会互补混出灰），
 *   巡游时颜色会真的"走"过一段色相，而不是同一坨紫里打转；
 * - **明度按色相亮度配对**：高亮度色相（偏蓝/青的）给高明度、低亮度色相（紫）给低明度，
 *   这样归一后 9 格的**亮度差**最大（约 0.24），明暗结构才立得住；
 * - 饱和度抬到 0.5 上下，避免发灰。
 */
private val FALLBACK_RAW_CELLS = listOf(
    Color(0xFF295F7A), Color(0xFF25446F), Color(0xFF111632),
    Color(0xFF100E2A), Color(0xFF1A0F2E), Color(0xFF2C143D),
    Color(0xFF411849), Color(0xFF63215E), Color(0xFF541C3D),
)
private val FALLBACK_RAW_AVERAGE = Color(0xFF2A2440)

/**
 * 动态「流光」背景的取色：封面 3×3 网格均色 + 整图均色。
 *
 * 与 [AppleArtworkMeshBackdrop] 的 6×6 网格不同：这里只要 9 个色源给流光场当控制点，
 * 所以取更粗的 3×3；无封面时用 [Fallback] 兜底。
 *
 * 取色统一走 [normalizeForFlow] 做**感知亮度归一**——这是关键：直接用封面原色时，
 * 亮封面会把光场顶得发白刺眼、暗封面又因为色差被压平而整场看不出结构。
 */
internal data class AppleArtworkPalette(
    val cells: List<Color>,
    val average: Color,
) {
    companion object {
        /**
         * 无封面时的兜底配色。
         *
         * 与封面取色走**同一套明度归一**，于是兜底与有封面的场处在同一明度带，
         * 换歌（有封面 ⇄ 无封面）时亮度不会忽明忽暗。
         */
        val Fallback: AppleArtworkPalette =
            normalizeForFlow(FALLBACK_RAW_CELLS, FALLBACK_RAW_AVERAGE, mixWithFallback = false)
    }
}

private const val PALETTE_GRID = 3

/** 取色只需 3×3 的均色，64px 已远超所需（每格约 21×21 像素）——解析越便宜越好。 */
private const val PALETTE_SIZE = 64
private const val PALETTE_SAMPLE_STEP = 2

private val paletteCache = object : LinkedHashMap<String, AppleArtworkPalette>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AppleArtworkPalette>) =
        size > 12
}

/**
 * 读取 [coverPath] 的封面并算出流光背景用的 [AppleArtworkPalette]。
 *
 * 同一路径只算一次并缓存；封面是本地抽帧文件，直接小尺寸解码即可，不走 Coil 像素回读。
 * 未就绪或无封面时先返回 [AppleArtworkPalette.Fallback]。
 */
@Composable
internal fun rememberAppleArtworkPalette(coverPath: String?): AppleArtworkPalette {
    var palette by remember(coverPath) {
        mutableStateOf(coverPath?.let(paletteCache::get) ?: AppleArtworkPalette.Fallback)
    }
    LaunchedEffect(coverPath) {
        if (coverPath.isNullOrBlank() || paletteCache.containsKey(coverPath)) return@LaunchedEffect
        val computed = withContext(Dispatchers.IO) {
            runCatching { decodePalette(coverPath) }.getOrNull()
        } ?: return@LaunchedEffect
        paletteCache[coverPath] = computed
        palette = computed
    }
    return palette
}

private fun decodePalette(path: String): AppleArtworkPalette? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PALETTE_SIZE &&
        bounds.outHeight / (sample * 2) >= PALETTE_SIZE
    ) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null
    val scaled = if (decoded.width == PALETTE_SIZE && decoded.height == PALETTE_SIZE) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, PALETTE_SIZE, PALETTE_SIZE, true)
    }
    val result = runCatching { makePalette(scaled) }.getOrNull()
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    return result
}

private fun makePalette(bitmap: Bitmap): AppleArtworkPalette {
    val width = bitmap.width
    val height = bitmap.height
    val cellWidth = width / PALETTE_GRID
    val cellHeight = height / PALETTE_GRID
    val cells = buildList(PALETTE_GRID * PALETTE_GRID) {
        for (row in 0 until PALETTE_GRID) {
            for (column in 0 until PALETTE_GRID) {
                val left = column * cellWidth
                val top = row * cellHeight
                val right = if (column == PALETTE_GRID - 1) width else (column + 1) * cellWidth
                val bottom = if (row == PALETTE_GRID - 1) height else (row + 1) * cellHeight
                add(averageCell(bitmap, left, top, right, bottom))
            }
        }
    }
    return normalizeForFlow(cells, averageCell(bitmap, 0, 0, width, height))
}

private fun averageCell(
    bitmap: Bitmap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
): Color {
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val pixel = bitmap.getPixel(x, y)
            red += (pixel shr 16) and 0xFF
            green += (pixel shr 8) and 0xFF
            blue += pixel and 0xFF
            count++
            x += PALETTE_SAMPLE_STEP
        }
        y += PALETTE_SAMPLE_STEP
    }
    if (count == 0L) return Color(0xFF33333D)
    return Color(
        red = (red.toFloat() / count / 255f).coerceIn(0f, 1f),
        green = (green.toFloat() / count / 255f).coerceIn(0f, 1f),
        blue = (blue.toFloat() / count / 255f).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/**
 * 目标「感知亮度」带。
 *
 * 用的是**亮度**（0.2126R + 0.7152G + 0.0722B，伽马编码值上的加权和）而不是 HSL 明度：
 * 紫/蓝在同一 HSL 明度下看起来暗得多，只有按亮度归一，"看起来的深浅"才控制得住、且与色相无关。
 */
private const val FLOW_LUMINANCE_MIN = 0.12f
private const val FLOW_LUMINANCE_MAX = 0.30f

/** 封面自身亮度差小于该值时，掺入兜底配色把结构撑开（否则整场是均匀一色，看不到流动）。 */
private const val FLOW_MIN_SPREAD = 0.10f

/** 饱和度补偿与上限：避免浅色高饱和封面把背景弄得刺眼，也避免低饱和封面发灰。 */
private const val FLOW_SATURATION_BOOST = 1.35f
private const val FLOW_SATURATION_MAX = 0.92f

/**
 * 把「封面取色」归一成流光场用的 [AppleArtworkPalette]。
 *
 * 三步：
 * 1. **饱和度补偿**（均值会把格子拉灰）；
 * 2. **补结构**：若 9 格之间的亮度差过小（近单色封面），掺入兜底配色，
 *    否则归一之后仍是一整片同色，看不出任何流动；
 * 3. **亮度归一**：把 9 格的**感知亮度**线性重映射到 `[FLOW_LUMINANCE_MIN, FLOW_LUMINANCE_MAX]`
 *    （按比例缩放 RGB，色相/彩度关系保持不变）。这是「亮封面不至于刺眼、暗封面不至于全黑」的关键：
 *    明暗差异被换成固定亮度带，白字始终可读、光场始终有结构。
 *
 * @param mixWithFallback 兜底配色自身调用时传 false，避免自引用。
 */
private fun normalizeForFlow(
    rawCells: List<Color>,
    rawAverage: Color,
    mixWithFallback: Boolean = true,
): AppleArtworkPalette {
    val cells = rawCells.map { it.withSaturationBoost() }
    val average = rawAverage.withSaturationBoost()

    val spread = cells.maxOf { it.flowLuminance() } - cells.minOf { it.flowLuminance() }
    val mix = if (mixWithFallback) {
        ((FLOW_MIN_SPREAD - spread) / FLOW_MIN_SPREAD).coerceIn(0f, 1f)
    } else {
        0f
    }
    val filledCells = if (mix <= 0f) {
        cells
    } else {
        cells.mapIndexed { index, color ->
            lerpRgb(color, AppleArtworkPalette.Fallback.cells[index], mix)
        }
    }
    val filledAverage = if (mix <= 0f) {
        average
    } else {
        lerpRgb(average, AppleArtworkPalette.Fallback.average, mix)
    }

    val filledLuminance = filledCells.map { it.flowLuminance() }
    val low = filledLuminance.minOrNull() ?: 0f
    val high = filledLuminance.maxOrNull() ?: 0f
    val span = high - low
    fun remap(color: Color): Color {
        val t = if (span < 1e-4f) 0.5f else ((color.flowLuminance() - low) / span).coerceIn(0f, 1f)
        val target = FLOW_LUMINANCE_MIN + t * (FLOW_LUMINANCE_MAX - FLOW_LUMINANCE_MIN)
        val current = color.flowLuminance()
        // 纯黑无法按比例缩放（会全 0）：退化为同等亮度的中性色
        if (current < 1e-4f) return Color(target, target, target, alpha = 1f)
        val scale = target / current
        return Color(
            red = (color.red * scale).coerceIn(0f, 1f),
            green = (color.green * scale).coerceIn(0f, 1f),
            blue = (color.blue * scale).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }
    return AppleArtworkPalette(
        cells = filledCells.map(::remap),
        average = remap(filledAverage),
    )
}

/** 感知亮度（伽马编码值上的加权和）：作为"看起来多亮"的单调代理。 */
private fun Color.flowLuminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun Color.hsl(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun Color.withSaturationBoost(): Color {
    val hsl = hsl()
    if (hsl[1] >= 0.08f) {
        hsl[1] = (hsl[1] * FLOW_SATURATION_BOOST).coerceIn(0f, FLOW_SATURATION_MAX)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun lerpRgb(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

/** 流光网格尺寸（很小：GPU 放大即可，无需整屏模糊）。 */
private const val FLOW_MESH_WIDTH = 32
private const val FLOW_MESH_HEIGHT = 68

/** 流光帧间隔（ms）：约 30fps，够顺滑又不常驻满帧。 */
private const val FLOW_FRAME_INTERVAL_MS = 33L

/** 入场后延迟启动流动的时长（ms）：先让页面入场动画与首帧布局跑完，避免抢帧。 */
private const val FLOW_START_DELAY_MS = 320L

/**
 * 相位推进速度（弧度/秒）。
 *
 * 这是**最慢的那一项**的基准速度（主扭曲项乘数 = 1）：0.85 → 场形约 7.4s 走完一圈。
 * 巡游色源另有 1~3 倍频率，所以它们的周期在 2.5~7.4s，整体是「缓慢流动」而非「快速晃动」。
 */
private const val FLOW_SPEED = 0.85f

/** 域扭曲强度（屏幕归一化单位）——正是它让色场「流动」而不是原地呼吸。 */
private const val FLOW_WARP = 0.26f

/** 每个色源的半径与底色权重：半径越小色块越分明，底色权重越低对比越强。 */
private const val FLOW_SOURCE_RADIUS = 0.30f
private const val FLOW_BASE_WEIGHT = 0.06f

/** 9 个色源各自的巡游频率（整数，即 0.25 的整数倍，见 [FLOW_PHASE_PERIOD]）。 */
private val FLOW_ORBIT_X = intArrayOf(1, 2, 3, 1, 2, 3, 1, 2, 3)
private val FLOW_ORBIT_Y = intArrayOf(2, 3, 1, 3, 1, 2, 2, 3, 1)

/**
 * 相位回绕点（8π = 4 整圈）。
 *
 * **所有**相位乘数（扭曲的 0.5 / 0.75 / 1 / 1.25 / 1.5、巡游的整数）都是 0.25 的整数倍，
 * 因此回绕 8π 时每一项都恰好前进整数圈 → 相位归零的那一帧与起始帧**逐像素相同**，
 * 不会出现「每几秒跳一下」。若把某个乘数改成任意小数，这里必须同步放大回绕点。
 */
private const val FLOW_PHASE_PERIOD = 25.132742f

/**
 * 动态「流光」背景（对齐参考项目的 Flowing Light 场景）。
 *
 * 做法：把 9 个取色结果当作 9 个缓慢巡游的色源，在**很小的网格位图**上按「径向权重叠加 +
 * 两层正弦域扭曲」逐像素合成，再整体放大铺满屏幕——扭曲使色彩被"携带"着流动，而不是原地呼吸。
 * 网格只有 32×68，CPU 合成很便宜，也**不需要整屏模糊**。
 *
 * 几个必须守住的点：
 * - **双缓冲**：正在被 `setPixels` 写的那张不会被画，避免半帧撕裂；
 * - **组合时同步预填一帧**（在 `remember(palette)` 里直接写好）：进入页面 / 暂停态当帧就有内容，
 *   不会先空白一两帧再"跳"出来；
 * - **重绘由 [frameTick] 自增驱动**，而不是"把同一张位图重新赋给 State"——后者赋值前后相等，
 *   State 判定无变化、不失效，画面会一直停在空白，直到恢复播放换到另一张缓冲才突然出现；
 * - 只在**播放中**推进动画：暂停即停（不请求帧、不重绘），避免常驻动画耗电；
 * - 「减少动态效果」开启时只画一帧静态场。
 */
@Composable
internal fun AppleFlowingLightBackdrop(
    palette: AppleArtworkPalette,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalNiReduceMotion.current
    val pixels = remember { IntArray(FLOW_MESH_WIDTH * FLOW_MESH_HEIGHT) }
    // 每帧只算一次色源中心（复用数组，避免逐像素重复三角函数与每帧分配）
    val centersX = remember { FloatArray(FLOW_ORBIT_X.size) }
    val centersY = remember { FloatArray(FLOW_ORBIT_Y.size) }
    // 相位放在 effect 之外：暂停 / 恢复时从原处继续，不会跳回起点
    val phaseHolder = remember { floatArrayOf(0f) }
    // 本次「入场」是否已经启动过流动（同一页面内暂停/恢复不重复等待）
    val flowStarted = remember { booleanArrayOf(false) }

    // 同步预填第一帧。这里用一次性 scratch，
    // 避免与「上一轮 palette 的动画协程」争用共享的 pixels / centers 数组。
    val bitmaps = remember(palette) {
        List(2) { Bitmap.createBitmap(FLOW_MESH_WIDTH, FLOW_MESH_HEIGHT, Bitmap.Config.ARGB_8888) }
            .also { buffers ->
                val scratch = IntArray(FLOW_MESH_WIDTH * FLOW_MESH_HEIGHT)
                val scratchCentersX = FloatArray(FLOW_ORBIT_X.size)
                val scratchCentersY = FloatArray(FLOW_ORBIT_Y.size)
                fillFlowingLight(
                    scratch,
                    FLOW_MESH_WIDTH,
                    FLOW_MESH_HEIGHT,
                    palette,
                    phaseHolder[0],
                    scratchCentersX,
                    scratchCentersY,
                )
                buffers[0].setPixels(
                    scratch, 0, FLOW_MESH_WIDTH, 0, 0, FLOW_MESH_WIDTH, FLOW_MESH_HEIGHT,
                )
            }
    }
    val images = remember(bitmaps) { bitmaps.map(Bitmap::asImageBitmap) }
    // 已完成帧数（从 1 起：预填的第一帧已就绪）。绘制取 (tick-1) 那张，写入取 tick%2 那张。
    val frameTick = remember(bitmaps) { mutableIntStateOf(1) }

    LaunchedEffect(palette, isPlaying, reduceMotion) {
        // 写下一张缓冲，然后让 tick 自增：tick 单调递增 → 一定触发重绘（见类头说明）
        suspend fun renderFrame(phase: Float) {
            val writeIndex = frameTick.intValue % 2
            withContext(Dispatchers.Default) {
                fillFlowingLight(
                    pixels, FLOW_MESH_WIDTH, FLOW_MESH_HEIGHT, palette, phase, centersX, centersY,
                )
                val bitmap = bitmaps[writeIndex]
                if (!bitmap.isRecycled) {
                    bitmap.setPixels(
                        pixels, 0, FLOW_MESH_WIDTH, 0, 0, FLOW_MESH_WIDTH, FLOW_MESH_HEIGHT,
                    )
                }
            }
            frameTick.intValue += 1
        }

        // 先出一帧：换歌 / 暂停时底色也要立刻更新为当前调色板
        renderFrame(phaseHolder[0])
        if (reduceMotion || !isPlaying) return@LaunchedEffect

        // 本次入场的头几帧正被「页面入场动画 + 首帧布局 + 封面解码」占满，
        // 若此刻就开始 30fps 逐帧合成会与之抢帧（表现为进入播放器卡顿）。
        // 先静置一小段再推进流动；首帧已预填，观感上只是"静了一下"。
        // 同一页面内的暂停 / 恢复不重复等待，避免恢复时迟滞。
        if (!flowStarted[0]) {
            delay(FLOW_START_DELAY_MS)
            flowStarted[0] = true
        }

        var lastFrameNanos = 0L
        val intervalNanos = FLOW_FRAME_INTERVAL_MS * 1_000_000L
        while (true) {
            val frameNanos = withFrameNanos { it }
            // 首帧没有参照：直接用一帧的间隔当作步长（否则 delta=0 会永远 continue、一帧都画不出来）
            val deltaNanos = if (lastFrameNanos == 0L) intervalNanos else frameNanos - lastFrameNanos
            if (deltaNanos < intervalNanos) continue
            val deltaMs = (deltaNanos / 1_000_000f).coerceIn(1f, 100f)
            lastFrameNanos = frameNanos
            phaseHolder[0] = (phaseHolder[0] + FLOW_SPEED * deltaMs / 1_000f) % FLOW_PHASE_PERIOD
            renderFrame(phaseHolder[0])
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 在 draw 里读 State：帧更新只让绘制失效，不带上组合。
        // tick 从 1 起单调递增，故 (tick - 1) 恒非负，且每次写入都会换到另一张缓冲。
        val image = images[(frameTick.intValue - 1) % 2]
        drawImage(
            image = image,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
        // 竖向压暗：只为白字/控件兜底，**尽量轻**——封面页能看到的流光恰好落在下半屏，
        // 压得太重会直接把流光"压成深色"。按最亮封面测算，底部仍有 ≈5:1 的白字对比度。
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.04f),
                    0.5f to Color.Black.copy(alpha = 0.08f),
                    1f to Color.Black.copy(alpha = 0.34f),
                ),
            ),
        )
    }
}

/** 逐像素合成流光场：9 个巡游色源按径向权重叠加，采样坐标先过两层域扭曲。 */
private fun fillFlowingLight(
    pixels: IntArray,
    width: Int,
    height: Int,
    palette: AppleArtworkPalette,
    phase: Float,
    centersX: FloatArray,
    centersY: FloatArray,
) {
    val cells = palette.cells
    val average = palette.average
    // 色源中心只跟 phase 有关：整帧算一次即可
    for (index in cells.indices) {
        centersX[index] = 0.5f + 0.32f * sin(phase * FLOW_ORBIT_X[index] + index * 1.7f)
        centersY[index] = 0.5f + 0.32f * cos(phase * FLOW_ORBIT_Y[index] + index * 0.9f)
    }
    val xDenominator = (width - 1).coerceAtLeast(1).toFloat()
    val yDenominator = (height - 1).coerceAtLeast(1).toFloat()
    var pixelIndex = 0
    for (yIndex in 0 until height) {
        val v = yIndex / yDenominator
        for (xIndex in 0 until width) {
            val u = xIndex / xDenominator

            // 两层正弦构成的类卷曲场：phase 前进时整片色彩被"携带"着平移
            // （所有乘数都是 0.25 的整数倍 —— 见 FLOW_PHASE_PERIOD 的回绕说明）
            val warpX = sin(v * 3.0f + phase) * cos(u * 2.4f - phase * 0.75f) +
                0.5f * sin((u + v) * 5.1f - phase * 1.25f)
            val warpY = cos(u * 2.8f - phase) * sin(v * 2.5f + phase * 0.5f) +
                0.5f * cos((u - v) * 5.6f + phase)
            var sampleU = u + FLOW_WARP * warpX
            var sampleV = v + FLOW_WARP * warpY
            // 更细的一层：让边界读起来是"大理石纹"而不是生硬的同心圆
            sampleU += FLOW_WARP * 0.3f * sin(sampleV * 6.9f - phase * 1.5f)
            sampleV += FLOW_WARP * 0.3f * cos(sampleU * 7.3f + phase * 1.5f)

            var red = average.red * FLOW_BASE_WEIGHT
            var green = average.green * FLOW_BASE_WEIGHT
            var blue = average.blue * FLOW_BASE_WEIGHT
            var totalWeight = FLOW_BASE_WEIGHT
            for (index in cells.indices) {
                val dx = (sampleU - centersX[index]) / FLOW_SOURCE_RADIUS
                val dy = (sampleV - centersY[index]) / FLOW_SOURCE_RADIUS
                val distanceSquared = dx * dx + dy * dy
                val falloff = 1f / (1f + distanceSquared * 4.5f)
                val weight = falloff * falloff
                val color = cells[index]
                totalWeight += weight
                red += color.red * weight
                green += color.green * weight
                blue += color.blue * weight
            }
            pixels[pixelIndex++] = packArgb(red / totalWeight, green / totalWeight, blue / totalWeight)
        }
    }
}

private fun packArgb(red: Float, green: Float, blue: Float): Int =
    (0xFF shl 24) or
        ((red.coerceIn(0f, 1f) * 255f).toInt() shl 16) or
        ((green.coerceIn(0f, 1f) * 255f).toInt() shl 8) or
        (blue.coerceIn(0f, 1f) * 255f).toInt()
