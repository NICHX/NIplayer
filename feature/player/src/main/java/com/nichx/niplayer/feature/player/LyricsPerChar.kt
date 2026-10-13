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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.flow.collectLatest

/**
 * 歌词「逐字铺色」的公共实现 —— 封面页的单行歌词与歌词页的当前句共用同一套。
 *
 * 三件事：
 * 1. [sungFraction]（见 LrcParser.kt）算出「已唱到多少」的比例，有逐字时间戳的精确到词，
 *    普通 LRC 按行时长估算；
 * 2. [rememberLivePosition] 把 1Hz 的播放位置补成连续值（换句、滚动、逐字都吃它）；
 * 3. [perCharSung] / [perCharUnsung] 按比例把文字裁成「已唱 / 未唱」两层，
 *    裁切边界取「边界字左缘 + 字宽 × 字内进度」——**连续推进**，不会一个字一个字地跳。
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

/**
 * 逐字裁切矩形在行盒外的上下余量（相对行高）。
 *
 * 底部余量夹在两条互相挤压的线之间（按本项目「行高 ≈ 1.18 × 字号」的样式实机量过）：
 * - **不能小**：p / q / g / y 的墨迹会伸出行盒底约 0.07 行高，小于它就把降部切掉；
 * - **不能大**：下一行的**字头**在行盒底下方约 0.12 行高处，大于它就会把下一行的字也裁进「已唱层」，
 *   在下一行字头上方叠出一条被硬切一刀的亮边（就是「折行时底下一行渲染有 bug」）。
 *
 * 0.09 落在两者之间。⚠️ 以前给到 0.20 是照「下一行字头在行顶下 0.20 行高」估的，实测只有 0.12 ——
 * 于是窗口一路漏进下一行约 0.08 行高，看起来就像下一行被点亮了一截。
 */
private const val LYRIC_CLIP_TOP_PAD = 0.06f
private const val LYRIC_CLIP_BOTTOM_PAD = 0.09f

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

/**
 * 逐字铺色的几何。
 *
 * - [sung]：已唱区；
 * - [unsung]：未唱部分；
 * - [glow]：已唱区**向外扩一圈**的区域，专给荧光层用。
 *
 * 为什么要单独给出 [unsung]：已唱 / 未唱是两层同款文本按**同一擦除边界**裁出来的，
 * 两层拼接处要严丝合缝才不会出现缺口或叠色。
 *
 * 为什么 [glow] 要外扩：荧光是「字的晕」，会伸到字外；直接拿已唱区去裁荧光，
 * 最左和最右那个字的晕就会被切出一道竖直的边（用户反馈「像被切断了」）。
 * 向外扩一个晕半径即可，多出来的部分被底层文字盖住，不会露出半个字。
 */
internal class SungSplit(
    val sung: Path,
    val unsung: Path,
    val glow: Path,
)

/**
 * 由排版结果与已唱比例算出几何：已唱区 / 未唱区 / 荧光区。
 *
 * 擦除边界取「边界字左缘 + 字宽 × 字内进度」（[revealed]），因此是**连续推进**的 ——
 * 不会出现「一个字一个字地跳」的观感；两层的裁切都吃同一个 [revealed]，接缝一致。
 */
internal fun buildSungSplit(
    layout: TextLayoutResult,
    fraction: Float,
    glowRadiusPx: Float = 0f,
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

    val sung = Path()
    val glow = Path()
    for (line in 0 until boundaryLine) {
        val rect = rectOf(line, layout.getLineLeft(line), layout.getLineRight(line))
        sung.addRect(rect)
        glow.addRect(rect.inflate(glowRadiusPx))
    }
    val last = rectOf(boundaryLine, lineLeft, revealed)
    sung.addRect(last)
    glow.addRect(last.inflate(glowRadiusPx))

    val unsung = Path()
    unsung.addRect(rectOf(boundaryLine, revealed.coerceAtLeast(lineLeft), lineRight))
    for (line in boundaryLine + 1 until lineCount) {
        unsung.addRect(rectOf(line, layout.getLineLeft(line), layout.getLineRight(line)))
    }

    return SungSplit(sung = sung, unsung = unsung, glow = glow)
}

/** 逐字铺色的「**已唱**」层：按 [splitProvider] 给出的几何把本层文字裁出来。 */
internal fun Modifier.perCharSung(
    splitProvider: () -> SungSplit?,
): Modifier = drawWithContent {
    val split = splitProvider() ?: return@drawWithContent
    clipPath(split.sung) { this@drawWithContent.drawContent() }
}

/**
 * 荧光层用的文本：**已唱部分不透明**（于是它的 shadow 会画出来）、**未唱部分既透明、
 * shadow 也置为透明**（于是既不显示字、也不发光）—— 两段共用同一份排版，
 * 所以位置和主文字完全一致。
 *
 * 为什么需要它：荧光层的裁切范围要往外扩一圈（否则首尾两个字的晕会被裁出竖直的边），
 * 而扩出去的那一圈里正好有**紧邻的未唱字**；不处理它，那个字（的晕）也会跟着发光 ——
 * 只涂透明不够，必须连 shadow 一起置透明（见下方说明）。
 */
internal fun lyricGlowText(text: String, boundary: Int): AnnotatedString = buildAnnotatedString {
    val cut = boundary.coerceIn(0, text.length)
    append(text.substring(0, cut))
    if (cut < text.length) {
        // 未唱部分：字透明，**并且把 shadow 显式置为透明**。
        //
        // 只把字设成透明是不够的：文字渲染里 shadow 用 Shadow 自己的颜色画，与字的填充色无关
        // （Compose 的 SpanStyle.shadow → ShadowSpan → TextPaint.setShadowLayer），
        // 所以未唱的字虽然看不见，它的**晕**仍会被画出来。而荧光的裁切范围向右多扩了一个晕半径，
        // 擦除边界那个字的晕（没有字、只剩一圈影）正好落在已唱区右侧 ——
        // 表现就是「边界字多了一层影子」。置一个透明 shadow 可覆盖段落级 shadow，让未唱段彻底不发光。
        withStyle(
            SpanStyle(
                color = Color.Transparent,
                shadow = Shadow(color = Color.Transparent, blurRadius = 0f),
            ),
        ) { append(text.substring(cut)) }
    }
}

/**
 * 荧光层：只画「已唱区外扩一圈」的范围。
 *
 * 这一层**必须垫在底层文字之下**：外扩出来的部分会带上紧邻的未唱字的晕，
 * 那一小块正好落在未唱区内，会被底层文字盖住 —— 既让首尾两个字的晕完整，又不会露出半个字。
 */
internal fun Modifier.perCharGlow(splitProvider: () -> SungSplit?): Modifier = drawWithContent {
    val split = splitProvider() ?: return@drawWithContent
    clipPath(split.glow) { this@drawWithContent.drawContent() }
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
