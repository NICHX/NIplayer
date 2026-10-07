package com.nichx.niplayer.feature.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「简约封面」主题的强调色：从当前封面里挑出来的一个可当主色的颜色。
 *
 * @param color 强调色本身（播放键底色 / 进度填充 / 当前歌词高亮）
 * @param onColor 压在 [color] 上的图标色
 */
internal data class CoverAccent(
    val color: Color,
    val onColor: Color,
)

/** 取色只需知道「封面偏什么色」：48px 解码足够，越便宜越好（切歌时每首都要算一次）。 */
private const val ACCENT_DECODE_PX = 48

/** 均色网格。4×4 每格 12×12 像素：既能把局部饱和色块单独拎出来，又不会被单像素噪点带偏。 */
private const val ACCENT_GRID = 4

/**
 * 强调色的目标亮度带（感知亮度 0.2126R + 0.7152G + 0.0722B）。
 *
 * 直接用封面原色不行：亮封面的主色偏白，压在它上面的图标会糊掉；暗封面的主色又会让
 * 进度条与当前歌词埋进背景里。这里把主色**按比例缩放 RGB** 重映射到固定亮度带 ——
 * 色相与彩度关系原样保留，所以「还是这首歌的颜色」，只是深浅可控。
 *
 * 深色模式取偏亮的一档（贴近参考图那种发光的强调色）；浅色模式压暗一档，
 * 否则浅底上的进度条与歌词会读不清。
 */
private const val ACCENT_DARK_LUMINANCE_MIN = 0.44f
private const val ACCENT_DARK_LUMINANCE_MAX = 0.62f
private const val ACCENT_LIGHT_LUMINANCE_MIN = 0.26f
private const val ACCENT_LIGHT_LUMINANCE_MAX = 0.42f

/** 饱和度补偿与上限：格子均值会把颜色拉灰，当主色用之前先补回来。 */
private const val ACCENT_SATURATION_BOOST = 1.25f
private const val ACCENT_SATURATION_MAX = 0.90f

/** 最强格子的彩度低于此值即认为封面「没有主色」（灰阶封面）—— 交给主题色。 */
private const val ACCENT_MIN_SATURATION = 0.10f

/** 强调色亮度高于此值时图标改用深色（当前亮度带下不会触发，留给日后放宽亮度带时兜底）。 */
private const val ACCENT_ON_LIGHT_THRESHOLD = 0.66f

private val accentCache = object : LinkedHashMap<String, CoverAccent?>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverAccent?>) = size > 12
}

/**
 * 读取 [coverPath] 的封面并算出「简约封面」的强调色；未就绪 / 无封面 / 灰阶封面时返回 null，
 * 由调用方回落到主题色。
 *
 * 与 [rememberAppleArtworkPalette] 一样：封面是本地抽帧文件，直接小尺寸解码即可，不走 Coil 像素回读；
 * 同一「路径 + 深浅色」只算一次。
 *
 * @param dark 当前是否深色模式（同一个封面在深/浅色下取到的是两个亮度档，需分别缓存）
 */
@Composable
internal fun rememberCoverAccent(coverPath: String?, dark: Boolean): CoverAccent? {
    val path = coverPath?.takeIf { it.isNotBlank() }
    val key = path?.let { "$dark|$it" }
    var accent by remember(key) { mutableStateOf(key?.let(accentCache::get)) }
    LaunchedEffect(key) {
        if (path == null || key == null) return@LaunchedEffect
        if (accentCache.containsKey(key)) return@LaunchedEffect
        val computed = withContext(Dispatchers.IO) {
            runCatching { decodeAccent(path, dark) }.getOrNull()
        }
        accentCache[key] = computed
        accent = computed
    }
    return accent
}

private fun decodeAccent(path: String, dark: Boolean): CoverAccent? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= ACCENT_DECODE_PX &&
        bounds.outHeight / (sample * 2) >= ACCENT_DECODE_PX
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
    val scaled = if (decoded.width == ACCENT_DECODE_PX && decoded.height == ACCENT_DECODE_PX) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, ACCENT_DECODE_PX, ACCENT_DECODE_PX, true)
    }
    val result = runCatching { accentOf(scaled, dark) }.getOrNull()
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()
    return result
}

/**
 * 从 [bitmap] 里挑出强调色。
 *
 * 不用「全图均色」：封面通常只有局部是饱和色（大片灰/白背景、暗角），一平均就得到一块
 * 谁也不像的中间色。这里改成**按格子打分选最强的那个格子**，再取该格均色 —— 得到的颜色
 * 一定真的出现在封面上，且是彩度最高的那一块。
 *
 * 打分 = 彩度 × 中间调权重：彩度决定「像不像主色」，中间调权重避免纯黑（无彩度的暗角）
 * 与纯白（高光的纸面）被选中。
 */
private fun accentOf(bitmap: Bitmap, dark: Boolean): CoverAccent? {
    val width = bitmap.width
    val height = bitmap.height
    if (width < ACCENT_GRID || height < ACCENT_GRID) return null

    val hsl = FloatArray(3)
    var bestScore = 0f
    var bestRed = 0f
    var bestGreen = 0f
    var bestBlue = 0f
    var bestSaturation = 0f
    var sumRed = 0.0
    var sumGreen = 0.0
    var sumBlue = 0.0
    var sumCount = 0

    for (row in 0 until ACCENT_GRID) {
        val top = row * height / ACCENT_GRID
        val bottom = ((row + 1) * height / ACCENT_GRID).coerceAtLeast(top + 1)
        for (column in 0 until ACCENT_GRID) {
            val left = column * width / ACCENT_GRID
            val right = ((column + 1) * width / ACCENT_GRID).coerceAtLeast(left + 1)

            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L
            for (y in top until bottom) {
                for (x in left until right) {
                    val pixel = bitmap.getPixel(x, y)
                    red += (pixel shr 16) and 0xFF
                    green += (pixel shr 8) and 0xFF
                    blue += pixel and 0xFF
                    count++
                }
            }
            if (count == 0L) continue
            val cellRed = (red.toFloat() / count).coerceIn(0f, 255f)
            val cellGreen = (green.toFloat() / count).coerceIn(0f, 255f)
            val cellBlue = (blue.toFloat() / count).coerceIn(0f, 255f)
            sumRed += cellRed * count
            sumGreen += cellGreen * count
            sumBlue += cellBlue * count
            sumCount += count.toInt()

            ColorUtils.RGBToHSL(
                cellRed.toInt().coerceIn(0, 255),
                cellGreen.toInt().coerceIn(0, 255),
                cellBlue.toInt().coerceIn(0, 255),
                hsl,
            )
            val saturation = hsl[1]
            val midTone = (1f - kotlin.math.abs(hsl[2] - 0.5f) * 1.2f).coerceAtLeast(0f)
            val score = saturation * midTone
            if (score > bestScore) {
                bestScore = score
                bestSaturation = saturation
                bestRed = cellRed
                bestGreen = cellGreen
                bestBlue = cellBlue
            }
        }
    }

    if (sumCount == 0) return null
    // 没有任何一格有彩度（灰阶封面）：用全图均色也只能得到灰，交给主题色更合适
    if (bestSaturation < ACCENT_MIN_SATURATION) return null

    val picked = Color(
        red = (bestRed / 255f).coerceIn(0f, 1f),
        green = (bestGreen / 255f).coerceIn(0f, 1f),
        blue = (bestBlue / 255f).coerceIn(0f, 1f),
        alpha = 1f,
    )
    val boosted = picked.withAccentSaturationBoost()
    val color = if (dark) {
        boosted.withAccentLuminanceIn(ACCENT_DARK_LUMINANCE_MIN, ACCENT_DARK_LUMINANCE_MAX)
    } else {
        boosted.withAccentLuminanceIn(ACCENT_LIGHT_LUMINANCE_MIN, ACCENT_LIGHT_LUMINANCE_MAX)
    }
    val onColor = if (color.accentLuminance() > ACCENT_ON_LIGHT_THRESHOLD) {
        Color(0xFF1A1C1E)
    } else {
        Color.White
    }
    return CoverAccent(color = color, onColor = onColor)
}

/** 感知亮度（伽马编码值上的加权和）：作为「看起来多亮」的单调代理，与色相无关。 */
private fun Color.accentLuminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** 格子均值会把颜色拉灰，取出来当主色前补一点饱和度；已经很灰的（近中性）不动。 */
private fun Color.withAccentSaturationBoost(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    if (hsl[1] < 0.05f) return this
    hsl[1] = (hsl[1] * ACCENT_SATURATION_BOOST).coerceIn(0f, ACCENT_SATURATION_MAX)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** 按比例缩放 RGB 把颜色搬到目标亮度带：色相与彩度关系保持不变。 */
private fun Color.withAccentLuminanceIn(min: Float, max: Float): Color {
    val current = accentLuminance()
    val target = current.coerceIn(min, max)
    if (current < 1e-4f) return Color(target, target, target, alpha = 1f)
    if (current == target) return this
    val scale = target / current
    return Color(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
        alpha = 1f,
    )
}
