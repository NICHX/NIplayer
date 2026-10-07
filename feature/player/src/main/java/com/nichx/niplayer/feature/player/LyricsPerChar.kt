package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.flow.collectLatest

/**
 * 歌词「逐字铺色」的公共实现 —— 封面页的单行歌词与歌词页的当前句共用同一套。
 *
 * 三件事：
 * 1. [sungFraction]（见 LrcParser.kt）算出「已唱到多少」的比例，有逐字时间戳的精确到词，
 *    普通 LRC 按行时长估算；
 * 2. [rememberLivePosition] 把 1Hz 的播放位置补成连续值（换句、滚动、逐字都吃它）；
 * 3. [perCharSung] / [perCharUnsung] 按比例把文字裁成「已唱 / 未唱」两层，
 *    并让已唱的字**逐字抬升**一点（见 [buildSungSplit]）。
 *
 * 为什么用「两层同款文本 + 上层裁切」而不是给每个字上色：逐字上色只能一个字一个字地跳，
 * 而裁切能连续推进；裁切又要按行算矩形，因为整句允许折行。
 */

/**
 * 外推上限（ms）。
 *
 * 上报值之间按流逝时间外推，但夹一个上限：切歌 / seek / 缓冲时上报可能中断，
 * 不封顶的话高亮会一路跑到底。
 */
private const val MaxLyricExtrapolationMs = 1_500L

/** 逐字裁切矩形在行盒外的上下余量（相对行高）。底部给得多 —— 要兜住 p / q / g 的降部。 */
private const val LYRIC_CLIP_TOP_PAD = 0.06f
private const val LYRIC_CLIP_BOTTOM_PAD = 0.20f

private fun elapsedRealtimeMs(): Long = System.nanoTime() / 1_000_000L

/**
 * 「实时播放位置」：把 1Hz 的上报值补成连续值。
 *
 * 播放位置每秒才上报一次，**直接用会有两个可见后果**：换句（以及随之而来的滚动）最多晚一整秒；
 * 逐字高亮同样落后。这里按「上报值 + 帧时钟外推」补上这段流逝时间（封顶
 * [MaxLyricExtrapolationMs]，防止上报中断时一路跑到底）。
 *
 * 整个歌词页**只建一个**，向下传给每一行 —— 派生值（当前句号、逐字比例、间奏点缀）
 * 都从它算，避免每行各起一个帧循环。
 *
 * 它每帧都会写入，所以**只能在绘制阶段或 derivedStateOf 里读**：
 * 前者每帧只重画那一层，后者只在结果真的变化（换句）时才通知重组。
 */
@Composable
internal fun rememberLivePosition(
    positionMs: State<Long>,
    isPlaying: Boolean,
    enabled: Boolean = true,
): State<Long> {
    val live = remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled, isPlaying) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { positionMs.value }.collectLatest { position ->
            val elapsedAtSync = elapsedRealtimeMs()
            while (true) {
                val elapsed = (elapsedRealtimeMs() - elapsedAtSync)
                    .coerceAtMost(MaxLyricExtrapolationMs)
                live.longValue = position + elapsed
                if (!isPlaying) return@collectLatest
                withFrameNanos { }
            }
        }
    }
    return live
}

/**
 * 当前句「已唱到多少」的实时比例（0..1），由 [rememberLivePosition] 派生。
 *
 * 返回值只在**绘制阶段**读，因此逐帧变化只重画那一层、不触发重组。
 * [enabled] 为 false 时恒为 0（不做逐字铺色）。
 */
@Composable
internal fun rememberSungFraction(
    text: String,
    wordTimes: List<Pair<String, Long>>,
    lineStartMs: Long,
    nextLineStartMs: Long?,
    livePosition: State<Long>,
    enabled: Boolean,
): State<Float> {
    // 不写成「if (!enabled) 直接 return 一个常量」：那种写法会让 remember 出现在条件分支里，
    // enabled 翻转时组合的槽位结构跟着变（Compose 会丢掉这段状态）。这里恒定只建一个
    // derivedStateOf，把开关放进 lambda —— 关掉时恒为 0，且值不变就不会通知重组。
    return remember(text, wordTimes, lineStartMs, nextLineStartMs, livePosition, enabled) {
        derivedStateOf {
            if (!enabled) {
                0f
            } else {
                sungFraction(
                    text = text,
                    wordTimes = wordTimes,
                    lineStartMs = lineStartMs,
                    nextLineStartMs = nextLineStartMs,
                    positionMs = livePosition.value,
                )
            }
        }
    }
}

/** 逐字抬升：抬升量 = 字号的 10%，夹在 1.5~6dp（与参考实现 MeloX-Android 同口径）。 */
internal const val LYRIC_LIFT_FONT_RATIO = 0.1f
internal const val LYRIC_LIFT_MIN_DP = 1.5f
internal const val LYRIC_LIFT_MAX_DP = 6f

/**
 * 一个字抬到顶需要「唱过去多少个字」。
 *
 * 取 1.4：大多数时候只有 1~2 个字处在抬起中，读起来是「一个字接一个字地抬起来」；
 * 取值越小越像「一个一个跳」，越大越像「整段一起抬」。
 */
private const val LYRIC_LIFT_SPAN_CHARS = 1.4f

/** 五次平滑（smootherstep）：两端的一阶、二阶导数都为 0，抬升起步与收尾都不「顿」。 */
private fun smootherStep(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

/** 一段「抬升量相同」的已唱区域：一个矩形集合 + 抬升系数（0..1，乘上 risePx）。 */
internal class SungBand(val rects: Path, val lift: Float)

/**
 * 逐字铺色的几何 + 抬升分段。
 *
 * - [bands]：已唱区，按抬升量分段（不抬升时只有一段）；
 * - [unsung]：未唱部分。
 *
 * 为什么要单独给出 [unsung]：已唱的字被抬起后，原位会空出来 —— 底层若照常画整句，
 * 抬起的字下方就会留一个「影子」（参考实现里专门注明过这个坑）。
 */
internal class SungSplit(
    val bands: List<SungBand>,
    val unsung: Path,
)

/**
 * 由排版结果与已唱比例算出几何与抬升分段。
 *
 * [lift] 为 true 时（**只有 Apple Music 主题**）把已唱区按「**一个字一个平移量**」切开：
 * 每个字都是**整字同步**往上平移，不会变形；抬升量由「这个字唱过去多久」决定，
 * 所以读起来是一个字接一个字地从左到右抬起来。
 *
 * [lift] 为 false 时整段一个 band、位移为 0 —— 即只做逐字铺色，不做抬升。
 */
internal fun buildSungSplit(
    layout: TextLayoutResult,
    fraction: Float,
    lift: Boolean,
): SungSplit? {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0) return null
    val progress = fraction.coerceIn(0f, 1f)
    if (progress <= 0f) return null

    val exact = progress * textLength
    val boundary = exact.toInt().coerceIn(0, textLength)
    val inChar = (exact - boundary).coerceIn(0f, 1f)
    val lineCount = layout.lineCount.coerceAtLeast(1)
    val boundaryLine = layout.getLineForOffset(boundary)
    val lineStepPx = layout.size.height.toFloat() / lineCount
    val topPad = lineStepPx * LYRIC_CLIP_TOP_PAD
    val bottomPad = lineStepPx * LYRIC_CLIP_BOTTOM_PAD
    // 只有**整段文字**的首行上方 / 末行下方才放到画布外（那里没有别的字，升降部一定完整）；
    // 折行时中间那些行不能这么放 —— 会连带把下一行的字也点亮。
    val overshoot = lineStepPx * 6f
    val lastTextLine = lineCount - 1

    fun rectOf(line: Int, left: Float, right: Float): Rect = Rect(
        left = left,
        top = if (line == 0) -overshoot else layout.getLineTop(line) - topPad,
        right = right.coerceAtLeast(left),
        bottom = if (line == lastTextLine) {
            layout.size.height.toFloat() + overshoot
        } else {
            layout.getLineBottom(line) + bottomPad
        },
    )

    val lineLeft = layout.getLineLeft(boundaryLine)
    val lineRight = layout.getLineRight(boundaryLine)
    val charLeft = layout.getHorizontalPosition(boundary, usePrimaryDirection = true)
    val next = boundary + 1
    // 边界字的右端：下一个字若已换行（或已到文末），取本行右端 —— 跨行取会得到一个更小的 x（回弹）
    val charRight = if (next <= textLength && layout.getLineForOffset(next) == boundaryLine) {
        layout.getHorizontalPosition(next, usePrimaryDirection = true)
    } else {
        lineRight
    }
    val revealed = charLeft + (charRight - charLeft) * inChar

    val bands = ArrayList<SungBand>(LYRIC_LIFT_SPAN_CHARS.toInt() + 2)

    if (!lift) {
        val all = Path()
        for (line in 0 until boundaryLine) {
            all.addRect(rectOf(line, layout.getLineLeft(line), layout.getLineRight(line)))
        }
        all.addRect(rectOf(boundaryLine, lineLeft, revealed))
        bands += SungBand(all, 1f)
    } else {
        // 抬升量：这个字「唱过去多少个字」—— 0 表示刚开始唱，≥ LYRIC_LIFT_SPAN_CHARS 表示已抬到顶。
        fun liftOf(charIndex: Int): Float =
            smootherStep((exact - charIndex) / LYRIC_LIFT_SPAN_CHARS)

        // 边界行上「已经抬到顶」的字：从行首往右找到第一个还没抬到顶的。
        // （行首之前的字都在别的行，整行抬到顶，见下面的循环。）
        val lineFirstChar = layout.getLineStart(boundaryLine)
        var fullEnd = lineFirstChar
        while (fullEnd < boundary && liftOf(fullEnd) >= 1f) {
            fullEnd++
        }

        val lifted = Path()
        for (line in 0 until boundaryLine) {
            lifted.addRect(rectOf(line, layout.getLineLeft(line), layout.getLineRight(line)))
        }
        if (fullEnd > lineFirstChar) {
            lifted.addRect(
                rectOf(
                    boundaryLine,
                    lineLeft,
                    layout.getHorizontalPosition(fullEnd, usePrimaryDirection = true),
                ),
            )
        }
        bands += SungBand(lifted, 1f)

        // 正在抬起的那些字：一个字一个 band、整字同步平移（不做字内渐变，否则字会变斜）
        for (index in fullEnd..boundary) {
            val left = if (index == boundary) charLeft else {
                layout.getHorizontalPosition(index, usePrimaryDirection = true)
            }
            val right = if (index == boundary) {
                revealed
            } else {
                val after = index + 1
                if (after <= textLength && layout.getLineForOffset(after) == boundaryLine) {
                    layout.getHorizontalPosition(after, usePrimaryDirection = true)
                } else {
                    lineRight
                }
            }
            if (right <= left) continue
            val band = Path()
            band.addRect(rectOf(boundaryLine, left, right))
            bands += SungBand(band, liftOf(index))
        }
    }

    val unsung = Path()
    unsung.addRect(rectOf(boundaryLine, revealed.coerceAtLeast(lineLeft), lineRight))
    for (line in boundaryLine + 1 until lineCount) {
        unsung.addRect(rectOf(line, layout.getLineLeft(line), layout.getLineRight(line)))
    }

    return SungSplit(bands = bands, unsung = unsung)
}

/**
 * 逐字铺色的「**已唱**」层：按 [splitProvider] 给出的分段几何把本层文字裁出来，
 * 每段按自己的抬升系数平移 —— 于是抬升是一个字接一个字地发生，每个字整体同步抬起。
 *
 * 每段各画一次 `drawContent()`（同一个 Modifier 里可以画多次），
 * 因此不需要为「正在抬起的那些字」单独再排一层文字。
 */
internal fun Modifier.perCharSung(
    splitProvider: () -> SungSplit?,
    risePx: Float,
): Modifier = drawWithContent {
    val split = splitProvider() ?: return@drawWithContent
    split.bands.forEach { band ->
        translate(top = -risePx * band.lift) {
            clipPath(band.rects) { this@drawWithContent.drawContent() }
        }
    }
}

/**
 * 逐字铺色的「**未唱**」层：只画未唱区域。
 *
 * 已唱的字被抬起后原位会空出来，底层若照常画整句就会在抬起的字下方留一个影子。
 * 比例还是 0（整句都没唱）时不需要裁 —— 直接整句画出来。
 */
internal fun Modifier.perCharUnsung(splitProvider: () -> SungSplit?): Modifier = drawWithContent {
    val split = splitProvider()
    if (split == null) {
        drawContent()
        return@drawWithContent
    }
    clipPath(split.unsung) { this@drawWithContent.drawContent() }
}

/**
 * 逐行追赶（cascade）的共享状态。
 *
 * 参考实现 MeloX-Android 的 `MeloXLyricsPanel`：换句要滚动时，**焦点下方的行不跟着列表一起走**，
 * 而是先留在原位、再按「离焦点越远等得越久」依次抬升归位 —— 于是读起来是「下方歌词逐行抬升」。
 *
 * 在一个 LazyColumn 里要做到「某几行先不动」，只能让列表照常滚、再给这些行一个反向补偿；
 * 所以这里存的是**列表已经滚了多少**（[distance]），行侧按自己的追赶进度把它抵消掉。
 */
internal class LyricCascade {
    /** 本次跟随滚动的实时距离（px，0 → 目标值）。行在绘制阶段读它。 */
    val distance = Animatable(0f)

    /** 上一次喂给 `scrollBy` 的值，用来算增量。 */
    var applied = 0f

    /** 世代号：只有「相邻向前」的换句才 +1，行侧的追赶动画以它为 key 重启。 */
    var generation by mutableIntStateOf(0)
}

/**
 * 逐行追赶：离焦点第 [focusOffset] 行的等待时长（ms）。
 *
 * 焦点下方第一行立刻追，第二行等一格、第三行等两格…… 差值随距离线性增长，
 * 于是「逐行」的节奏是均匀的。
 */
internal fun lyricCascadeDelayMs(focusOffset: Int): Long =
    (focusOffset - 1).coerceAtLeast(0) * LYRIC_CASCADE_DELAY_STEP_MS

/** 每靠下一行多等多久（ms）。 */
private const val LYRIC_CASCADE_DELAY_STEP_MS = 55L
