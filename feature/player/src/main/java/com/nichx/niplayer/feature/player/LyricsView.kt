package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import com.nichx.niplayer.designsystem.theme.MotionTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Apple 风格歌词字号倍率：titleLarge 22sp × 1.4 ≈ 31sp。 */
private const val APPLE_FONT_BOOST = 1.4f

/**
 * 黑胶主题歌词字号倍率：titleLarge 22sp × 1.15 ≈ 25sp（当前句）。
 *
 * 放大后单行更易读；代价是行高同比放大，同屏可见行数相应减少。
 */
private const val VINYL_FONT_BOOST = 1.15f

/**
 * 黑胶主题：非当前句相对当前句的字号比例。
 *
 * 0.78 → 非当前句约 20sp，主次分明又保证远处可读；两档共用同一 lineHeight，
 * 因此行距均匀、不忽宽忽窄（也让自动跟随的落点计算与实际行高吻合）。
 */
private const val VINYL_IDLE_FONT_RATIO = 0.78f

/** 黑胶主题：歌词字距。中日韩字形略放宽一点，读起来更透气。 */
private val VINYL_LYRIC_LETTER_SPACING = 0.2.sp

/**
 * 简约封面主题的歌词字号倍率：titleLarge 22sp × 1.2 ≈ 26sp（当前句）。
 *
 * 比黑胶的 1.15 还大一档 —— 这是「聚光」方案的核心：把字号差拉开，
 * 当前句一眼就能锁定。代价是行高同比放大、同屏可见行数减少。
 */
private const val GLASS_FONT_BOOST = 1.2f

/**
 * 简约封面主题：非当前句相对当前句的字号比例。
 *
 * 0.73 → 非当前句约 19sp。与 [GLASS_FONT_BOOST] 一起构成「聚光」的字号差
 * （26 / 19，对比黑胶的 25 / 20）。
 */
private const val GLASS_IDLE_FONT_RATIO = 0.73f

/** 简约封面主题：歌词字距。比黑胶更松，配合更小的字号差，整页更透气。 */
private val GLASS_LYRIC_LETTER_SPACING = 0.6.sp

/** 简约封面主题的句间留白：比黑胶（18dp）更松 —— 行间字号差更小，靠留白分层。 */
private val GLASS_SENTENCE_GAP = 26.dp

/**
 * 简约封面：当前句背后「流动光带」的参数。
 *
 * 周期 7.2s 比封面浮动的 5.6s 更慢 —— 光带是**环境**而不是主角，动快了会抢走对歌词的注意力。
 */
private const val GLASS_BAND_PERIOD_MS = 7_200
private const val GLASS_BAND_DRIFT = 0.10f
private const val GLASS_BAND_BREATHE = 0.18f

/**
 * 光带亮度：亮核 + 两侧柔光。
 *
 * 一开始只给了单档 0.20，实机上「看不到」—— 文字本身比它亮得多，0.2 的色块完全被压住了。
 * 现在核心抬到 0.42，并补一档更淡的两侧过渡，让它读成「一束光」而不是一层薄雾。
 */
private const val GLASS_BAND_ALPHA = 0.42f
private const val GLASS_BAND_SOFT_ALPHA = 0.12f
private const val GLASS_BAND_SPAN = 0.55f
/** 光带高度 = 行高的多少倍（纵向柔化会吃掉上下各一段，所以要留富余）。封面页与歌词页共用。 */
internal const val GLASS_BAND_HEIGHT_RATIO = 1.8f
private val GlassBandTwoPi = (2.0 * PI).toFloat()

/** Apple 风格：当前行落在视口高度的该比例处（偏上方，而非居中）。 */
private const val APPLE_FOCUS_FRACTION = 0.34f

/** 句与句之间的额外留白：句内换行间距由字体 lineHeight 决定，句间再多出这一段。 */
private val APPLE_SENTENCE_GAP = 20.dp
private val PLAIN_SENTENCE_GAP = 18.dp

/**
 * 距离 → 模糊半径（dp）的整形。
 *
 * 近处（1.35 行以内）不模糊，之后按线性增长、封顶，再乘以强度；
 * 聚焦中的行（[focus]→1）不做距离模糊。**不是**「超过 N 行就一律最大模糊」的硬截断——
 * 那会把远处一片糊成一块。
 */
private const val BLUR_INTENSITY = 0.6f

/** 距离 → 不透明度的整体压暗量（0=不压暗，1=完全按曲线压暗）。 */
private const val DIM_AMOUNT = 0.85f

/** 焦点交接（当前行 ↔ 非当前行）的过渡时长。 */
private const val FOCUS_TRANSITION_MS = 320

/** 点击预览（选中该句显示时间、待二次点击跳转）的自动超时，避免预览态长期滞留。 */
private const val LyricPreviewTimeoutMs = 2500L

/** 相邻两句间隔超过该值即视为「间奏」，Apple 风格下显示间奏点缀。 */
private const val INTERLUDE_MIN_GAP_MS = 4_000L

/** 倒计时在下一句前该值处结束，让点缀先收掉、正句再起（Apple 口径）。 */
private const val INTERLUDE_TAIL_MS = 250L

/** 间奏点缀至少要有这么长的可视窗口才值得显示。 */
private const val INTERLUDE_MIN_SHOW_MS = 1_200L

/** 单句吟唱时长的估算：字数 × 每字时长，并夹在最短/最长之间（无句末时间戳时的近似）。 */
private const val INTERLUDE_MS_PER_CHAR = 220L
private const val INTERLUDE_MIN_LINE_MS = 1_500L
private const val INTERLUDE_MAX_LINE_MS = 6_000L

/** 间奏点缀：3 个圆点（Apple Music 口径），随进度依次点亮，**不显示数字倒计时**。 */
private const val INTERLUDE_DOT_COUNT = 3
private val INTERLUDE_DOT_RADIUS = 5.dp
private val INTERLUDE_DOT_GAP = 18.dp

/** 点缀的呼吸：±5% 呼吸幅度，周期 1.5s。 */
private const val INTERLUDE_BREATHE_PERIOD_MS = 1_500.0
private const val INTERLUDE_BREATHE_AMPLITUDE = 0.05f

/** 收尾淡出时长：最后这段内整体淡掉，避免下一句到来时点缀还亮着。 */
private const val INTERLUDE_FADE_TAIL_MS = 375f

/**
 * 手动浏览歌词后「回位」的延迟（ms）。
 *
 * 用户拖动/惯性停下后不立刻拽回当前行，而是等这段时间无操作再平滑归位——
 * Apple 的「先拖出去、停一下再弹回来」手感；立即回位会显得在跟用户抢。
 */
private const val LYRIC_FOLLOW_DELAY_MS = 2_600L

/**
 * 逐字时间戳的**不可变包装**。
 *
 * `List` 在 Compose 里被判为不稳定，会连累整个歌词行在每次位置上报（1Hz）时都重组一遍。
 * 包成 `@Immutable` 后，行参数全部稳定 —— 加上逐字进度改由 `snapshotFlow` 驱动，
 * 位置上报连「当前行」都不再重组，只有绘制层重画。
 */
@Immutable
internal data class LyricWordTimes(val words: List<Pair<String, Long>>)

/**
 * 歌词列表项：**一句歌词是一整个列表项**（不按物理行拆）。
 *
 * 这样一句里的换行由文字引擎按 lineHeight 自然折行（句内行距紧），
 * 句与句之间才加 [APPLE_SENTENCE_GAP] 的留白——「按句加大间隔」；同时
 * 整句共用同一个模糊/透明度（不会出现「同一句的第一行清晰、后续行被模糊」）。
 *
 * 间奏点缀**画在紧随其后那一句的项内**（[interludeBefore]）、不单独占一个列表项：
 * 一旦把间奏做成独立项，项号会在间奏开始/结束时整体偏移一格，自动跟随就会多滚一整行
 * ——那正是「回滚跳变」的来源。画在本项内、且高度恒占位，则项号与布局都稳定。
 *
 * @param sentenceIndex 所属原句在 [lrcLines] 中的下标。
 * @param text 整句文本。
 * @param interludeBefore 本句之前的间奏区间；非 null 时在本项顶部画点缀。
 */
private data class LyricItem(
    val sentenceIndex: Int,
    val text: String,
    val interludeBefore: InterludeSpan? = null,
)

/** 一段间奏的时间区间（句末时间戳不可得，用估算出发时间）。 */
private data class InterludeSpan(
    val startMs: Long,
    val endMs: Long,
)

/**
 * 由相邻两句推算间奏区间；间隔不足或可视窗口太短时返回 null。
 *
 * 缺少句末时间戳，故按 [line] 的字数估算其吟唱时长（[INTERLUDE_MS_PER_CHAR]），
 * 估算终点即间奏起点；终点取下一句前 [INTERLUDE_TAIL_MS]（让点缀先收掉、正句再起）。
 */
private fun interludeOf(line: LrcLine, next: LrcLine): InterludeSpan? {
    val gap = next.timeMs - line.timeMs
    if (gap < INTERLUDE_MIN_GAP_MS) return null
    val estimated = (line.text.length * INTERLUDE_MS_PER_CHAR)
        .coerceIn(INTERLUDE_MIN_LINE_MS, INTERLUDE_MAX_LINE_MS)
    val startMs = line.timeMs + estimated
    val endMs = next.timeMs - INTERLUDE_TAIL_MS
    return if (endMs - startMs >= INTERLUDE_MIN_SHOW_MS) InterludeSpan(startMs, endMs) else null
}

/** 距离（行）→ 不透明度：近处接近 1，远处收到底噪 0.12；聚焦行按 [focus] 抬回 1。 */
private fun distanceOpacity(lineDistance: Float, focus: Float): Float {
    val base = when {
        lineDistance <= 1f -> 1f - lineDistance * 0.44f
        lineDistance <= 2f -> 0.56f - (lineDistance - 1f) * 0.22f
        else -> (0.34f - (lineDistance - 2f) * 0.07f).coerceAtLeast(0.12f)
    }
    val dimmed = 1f - (1f - base) * DIM_AMOUNT
    val f = focus.coerceIn(0f, 1f)
    return dimmed + (1f - dimmed) * f
}

/**
 * 黑胶主题的距离 → 不透明度。
 *
 * 比 Apple 的口径更平缓、下限更高（0.40）：远处的歌词只是变暗、仍保持可读，
 * 层次由明暗与放大来体现，而不是「淡到几乎看不见」。聚焦行由 [focus] 抬回 1。
 */
private fun vinylDistanceOpacity(lineDistance: Float, focus: Float): Float {
    val base = (1f - lineDistance * 0.20f).coerceIn(0.40f, 1f)
    val f = focus.coerceIn(0f, 1f)
    return base + (1f - base) * f
}

/**
 * 简约封面主题的距离 → 不透明度（「聚光」口径）。
 *
 * 每行衰减 0.30、下限 0.28：相邻行 0.70、隔一行 0.40、再远就落到底噪。
 * 比黑胶（每行 0.20、下限 0.40）陡得多 —— 配合放大的当前句，把视线收在焦点附近；
 * 黑胶那种「远处也只是变暗、仍保持可读」的口径在这里会让整屏一样重。
 */
private fun glassDistanceOpacity(lineDistance: Float, focus: Float): Float {
    val base = (1f - lineDistance * 0.30f).coerceIn(0.28f, 1f)
    val f = focus.coerceIn(0f, 1f)
    return base + (1f - base) * f
}

/** 距离（行）→ 模糊半径（dp）：1.35 行内为 0，之后线性增长封顶；聚焦行不模糊。 */
private fun distanceBlurDp(lineDistance: Float, focus: Float): Float {
    val progress = (lineDistance - 1.35f).coerceAtLeast(0f)
    val base = (progress * 3.1f).coerceAtMost(10f)
    return base * BLUR_INTENSITY * (1f - focus.coerceIn(0f, 1f))
}

/**
 * 歌词视图的样式族：三套主题各一种，**互不复用**。
 *
 * - [APPLE]：左对齐白色大字，当前行明亮、其余发散模糊（Apple Music 口径）；
 * - [VINYL]：居中，当前句放大 + 主题色光晕 + 整行轻微放大（唱片封面的语言）；
 * - [GLASS]：居中，字号几乎齐平，层次**只由颜色与透明度**给出（简约封面口径）。
 */
enum class LyricsStyle { APPLE, VINYL, GLASS }

/**
 * 同步歌词视图。
 *
 * 实现要点：
 * - **一句歌词 = 一个列表项**：句内折行交给文字引擎（按 lineHeight），句间才加留白，
 *   「按句加大间隔」；同时整句共享同一套模糊/透明度，长句多行不会只有第一行清晰；
 * - 当前句落点：contentPadding.top = 视口高 × [focusFraction] − 半行高，配合无偏移的
 *   animateScrollToItem，当前句首行精确落在焦点位置（与句高无关，故长句也准）；
 * - 距离只驱动「透明度 + 模糊」：自动跟随时按**行号差**，手动浏览时按**屏幕距离**——
 *   后者保证用户滑到哪儿、哪儿就是清晰带，不会「滑过去一片全糊」；
 * - 点击跳转；[LyricsStyle.APPLE] 点一下即跳，另两套保留「先预览再确认」的两次点击。
 *
 * @param maxVisibleLines 最多同时显示的行数（受容器高度约束，取较小值）。
 * @param style 样式族，见 [LyricsStyle]。
 * @param onUserScroll 用户开始手动滚动时回调（供外层「任何交互即呼出控件」用）。
 */
@Composable
fun LyricsView(
    lrcLines: List<LrcLine>,
    currentPositionMs: State<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleLines: Int = Int.MAX_VALUE,
    style: LyricsStyle = LyricsStyle.VINYL,
    /** 当前句 / 逐字高亮的颜色。默认主题色；简约封面主题传入从封面取到的强调色。 */
    accentColor: Color = MaterialTheme.colorScheme.primary,
    /** 是否正在播放：逐字铺色与光带的动画只在播放时推进（暂停即停、不请求帧）。 */
    isPlaying: Boolean = false,
    /** 「逐字歌词」开关：关掉后当前句整句同色，不做逐字铺色。 */
    perChar: Boolean = true,
    onUserScroll: () -> Unit = {},
) {
    val appleStyle = style == LyricsStyle.APPLE
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val baseTitleLarge = MaterialTheme.typography.titleLarge

    // 实时播放位置：整个歌词页**只建一个**，向下传给每一行 —— 换句、滚动、逐字、间奏点缀
    // 都从它算，不会每行各起一个帧循环。
    val livePosition = rememberLivePosition(
        positionMs = currentPositionMs,
        isPlaying = isPlaying,
        enabled = lrcLines.isNotEmpty(),
    )
    // 当前句号由**实时位置**派生，而不是 1Hz 的上报值 —— 否则换句（以及随之而来的滚动）
    // 最多晚一整秒，听起来就是「歌词慢半拍才滚」。
    // derivedStateOf 只在句号真的变化时才通知重组，因此不会每帧重建整页。
    val currentSentenceIndex by remember(lrcLines, livePosition) {
        derivedStateOf {
            if (lrcLines.isEmpty()) 0 else {
                lrcLines.indexOfLast { it.timeMs <= livePosition.value }.coerceAtLeast(0)
            }
        }
    }

    // 点击预览：第一次点击选中该句并显示时间，再次点击同一句才跳转。
    // 预览态用**超时**清除（而非"播放推进到新句就清除"）：后者会在用户第二次点击前
    // 就把预览清掉，导致"永远停留在预览、完不成跳转"。
    var pendingSentenceIndex by remember(lrcLines) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingSentenceIndex) {
        if (pendingSentenceIndex != null) {
            delay(LyricPreviewTimeoutMs)
            pendingSentenceIndex = null
        }
    }

    BoxWithConstraints(modifier = modifier) {
        // 大屏自适应：可用宽度越宽，字号与行高等比放大，避免大屏下字体显小
        val scale = when {
            maxWidth < 420.dp -> 1f
            maxWidth < 560.dp -> 1.15f
            else -> 1.3f
        }
        val fontBoost = when (style) {
            LyricsStyle.APPLE -> APPLE_FONT_BOOST
            LyricsStyle.VINYL -> VINYL_FONT_BOOST
            LyricsStyle.GLASS -> GLASS_FONT_BOOST
        }

        // 行高（单行文字盒高）：居中落点、视口行数与距离换算都以它为单位
        val lineHeightSp = baseTitleLarge.lineHeight * scale * fontBoost
        val lineHeightPx = with(density) { lineHeightSp.toPx() }
        val lineHeight = with(density) { lineHeightPx.toDp() }
        val sentenceGap = when (style) {
            LyricsStyle.APPLE -> APPLE_SENTENCE_GAP
            LyricsStyle.VINYL -> PLAIN_SENTENCE_GAP
            LyricsStyle.GLASS -> GLASS_SENTENCE_GAP
        }

        // 列表项：每句一项；Apple 风格把长间隔处生成的间奏点缀挂到**下一句**的项内
        val items = remember(lrcLines, style) {
            lrcLines.mapIndexed { sentenceIndex, line ->
                val interlude = if (appleStyle && sentenceIndex > 0) {
                    interludeOf(lrcLines[sentenceIndex - 1], line)
                } else {
                    null
                }
                LyricItem(sentenceIndex, line.text, interlude)
            }
        }

        val currentItemIndex = remember(currentSentenceIndex, items) {
            items.indexOfFirst { it.sentenceIndex == currentSentenceIndex }
        }
        // 焦点恒为「当前句所在的项」：间奏不再是独立项，项号不会因间奏而偏移，故无跳变。
        val focusedIndex = currentItemIndex

        // 行距（stride）= 单行高 + 句间留白：视口行数按它算，与「一行一句」的观感密度一致。
        val sentenceGapPx = with(density) { sentenceGap.toPx() }
        val stridePx = lineHeightPx + sentenceGapPx
        val stride = with(density) { stridePx.toDp() }
        val viewportLines = minOf(
            with(density) { (maxHeight / stride).toInt().coerceAtLeast(3) },
            maxVisibleLines,
        )
        val viewportHeightPx = stridePx * viewportLines
        val focusFraction = if (appleStyle) APPLE_FOCUS_FRACTION else 0.5f

        // 当前句落点：contentPadding.top 让「首行中心」落在焦点位置。用 lineHeight/2
        // （而不是整句高/2），长句多行时首行同样精确对齐。
        val topPaddingPx = (viewportHeightPx * focusFraction - lineHeightPx / 2f)
            .toInt()
            .coerceAtLeast(0)
        // 底部留白：让最后一句也能滚到焦点位置（尾部滚动），其余作为自然尾部空白
        val minTailPx = with(density) { 40.dp.toPx() }.toInt()
        val bottomPaddingPx = (viewportHeightPx * (1f - focusFraction))
            .toInt()
            .coerceAtLeast(minTailPx)

        val reducedMotion = LocalNiReduceMotion.current

        // 手动浏览：用户一滚动就进入浏览态、暂停自动跟随；停止操作 LYRIC_FOLLOW_DELAY_MS
        // 后退出浏览并平滑回到当前行——「拖出去、等一下再弹回来」的尾部回位。
        //
        // 只用嵌套滚动的 **UserInput** 判定：程序自身的 animateScrollToItem 不会把自己
        // 误判成用户在拖，否则会出现「回位 → 被当成浏览 → 再回位」的自激抖动。
        var browsing by remember { mutableStateOf(false) }
        var browseGeneration by remember { mutableIntStateOf(0) }
        val currentOnUserScroll by rememberUpdatedState(onUserScroll)
        val browseConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && abs(available.y) > 0.01f) {
                        browsing = true
                        browseGeneration += 1
                        currentOnUserScroll()
                    }
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(browseGeneration) {
            if (browseGeneration <= 0) return@LaunchedEffect
            delay(LYRIC_FOLLOW_DELAY_MS)
            browsing = false
        }

        // 首次落位（进入歌词页、或换歌后第一次跟随）：**直接瞬移**，不做动画。
        // 此刻列表还停在顶部，用动画滚过去会先「飞」一整屏才到位。
        var followSettled by remember(lrcLines) { mutableStateOf(false) }

        // 逐行追赶的共享状态（见 LyricCascade）：行侧要在绘制阶段读「列表滚了多少」
        val cascade = remember(lrcLines) { LyricCascade() }
        var previousFocusedIndex by remember(lrcLines) { mutableIntStateOf(-1) }

        // 自动跟随：当前句 / 间奏切换时回到焦点项；浏览期间不打断，浏览结束再触发回位。
        LaunchedEffect(focusedIndex, browsing) {
            if (focusedIndex < 0 || browsing) return@LaunchedEffect
            if (reducedMotion || !followSettled) {
                listState.scrollToItem(focusedIndex)
                followSettled = true
                previousFocusedIndex = focusedIndex
                return@LaunchedEffect
            }
            // 目标项已在屏内时，按「像素差」用更柔的弹性滚过去（项顶端到锚点 = item.offset，
            // 见 LazyListMeasure：屏幕 y = offset − viewportStartOffset）。比 animateScrollToItem
            // 默认的偏硬弹簧顺得多；不在屏内（含跳转）才交给它处理。
            val visible = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == focusedIndex }
            if (visible == null) {
                // 目标项不在屏内（用户跳了很远 / 跳转）。
                // 注意 LazyListState.animateScrollToItem **不接受** animationSpec，只能用它的内置弹簧；
                // 屏内的精细跟随才走下面的手动滚动 + MotionTokens.springScroll。
                previousFocusedIndex = focusedIndex
                listState.animateScrollToItem(focusedIndex)
                return@LaunchedEffect
            }
            // 本句带间奏点缀时，点缀盒在项顶：多滚一个行高，让**文字首行**（而非点缀）落在锚点
            val dotsLead = if (items[focusedIndex].interludeBefore != null) lineHeightPx else 0f
            val delta = visible.offset.toFloat() + dotsLead
            // 「逐行追赶」**只在 Apple Music 主题做**，且只对**相邻向前**的换句生效
            // （跨行跳转 / 向后跳转直接滚过去）—— 参考实现的口径：只有一行一行往前走时，
            // 追赶才有意义。
            val chasing = style == LyricsStyle.APPLE &&
                focusedIndex - previousFocusedIndex == 1 && delta > 0f
            previousFocusedIndex = focusedIndex
            cascade.distance.snapTo(0f)
            cascade.applied = 0f
            if (chasing) cascade.generation += 1
            // 手动驱动滚动：同一份进度要同时喂给「列表滚动」与行侧的追赶补偿，
            // animateScrollBy 把进度藏在内部、拿不到，所以自己 animateTo + scrollBy。
            listState.scroll {
                cascade.distance.animateTo(delta, MotionTokens.springScroll) {
                    scrollBy(value - cascade.applied)
                    cascade.applied = value
                }
            }
        }

        // 浏览时的「焦点锚点」在行号空间的**小数**位置：由可见项的实际像素位置插值得出。
        // 只有这样，滑到哪儿就哪儿清晰，而不是按「离正在播放那行多远」把一片都糊掉。
        // 仅在浏览态被读取（自动跟随时返回 null，不触碰 layoutInfo）。
        val browseAnchor by remember(viewportHeightPx, focusFraction) {
            derivedStateOf {
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo
                if (visible.isEmpty()) {
                    null
                } else {
                    val anchorOffset = info.viewportStartOffset + viewportHeightPx * focusFraction
                    val hit = visible.firstOrNull {
                        anchorOffset >= it.offset && anchorOffset < it.offset + it.size
                    } ?: visible.minByOrNull { abs(it.offset + it.size / 2f - anchorOffset) }
                    hit?.let { it.index + (anchorOffset - it.offset) / it.size.toFloat() }
                }
            }
        }
        // 自动跟随：按项号差；浏览：按屏幕距离（像素/行高）。二者统一成「行」为单位。
        val distanceReference: Float? = if (browsing) browseAnchor else null

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 主题色底衬（上下渐隐）只为黑胶准备：那里的背景是封面氛围光，明暗/纹理不可控，
                // 底衬给文字一个稳定的对比度，又因与全屏 scrim 同色系、两端透明而不显突兀。
                //
                // 简约封面**不画**：它的背景本身就是「封面模糊 + 主题色 scrim」的平滑渐变，
                // 再压一层主题色会在屏幕中间显出一块方形的深色带（用户反馈「像遮罩」），
                // 而对比度已经由 scrim 保证。Apple 靠纯白大字 + 模糊分层，也不需要。
                .then(
                    if (style == LyricsStyle.VINYL) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (lrcLines.isEmpty()) {
                Text(
                    text = stringResource(R.string.lyrics_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (appleStyle) Color.White.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .height(with(density) { viewportHeightPx.toDp() })
                        .nestedScroll(browseConnection),
                    contentPadding = PaddingValues(
                        top = with(density) { topPaddingPx.toDp() },
                        bottom = with(density) { bottomPaddingPx.toDp() },
                    ),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> "s_${item.sentenceIndex}" },
                    ) { index, item ->
                        val sentenceIndex = item.sentenceIndex
                        val sentence = lrcLines[sentenceIndex]
                        val sentenceTimeMs = sentence.timeMs
                        val lineDistance = distanceReference?.let { abs(index - it) }
                            ?: abs(index - focusedIndex).toFloat()
                        LyricRowItem(
                            text = item.text,
                            interludeBefore = item.interludeBefore,
                            // 传**实时**位置：行内的逐字比例与间奏点缀都按它算
                            positionMs = livePosition,
                            isCurrent = sentenceIndex == currentSentenceIndex,
                            isPending = pendingSentenceIndex == sentenceIndex,
                            timeLabel = formatDurationShort(sentenceTimeMs),
                            onClick = {
                                // Apple：点一下文字就跳转，不做「先预览再确认」那一步。
                                when {
                                    appleStyle -> onSeek(sentenceTimeMs)
                                    pendingSentenceIndex == sentenceIndex -> {
                                        onSeek(sentenceTimeMs)
                                        pendingSentenceIndex = null
                                    }
                                    else -> pendingSentenceIndex = sentenceIndex
                                }
                            },
                            lineDistance = lineDistance,
                            focusOffset = index - focusedIndex,
                            cascade = cascade,
                            lineHeight = lineHeight,
                            sentenceGap = sentenceGap,
                            scale = scale,
                            fontBoost = fontBoost,
                            style = style,
                            accentColor = accentColor,
                            lineStartMs = sentenceTimeMs,
                            nextLineStartMs = lrcLines.getOrNull(sentenceIndex + 1)?.timeMs,
                            isPlaying = isPlaying,
                            perChar = perChar,
                            wordTimes = remember(sentence.wordTimes) {
                                LyricWordTimes(sentence.wordTimes)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricRowItem(
    text: String,
    isCurrent: Boolean,
    isPending: Boolean,
    timeLabel: String?,
    onClick: () -> Unit,
    lineDistance: Float,
    /** 本行相对焦点行的偏移（负=在上方，正=在下方）：逐行追赶按它算等待时长。 */
    focusOffset: Int,
    cascade: LyricCascade,
    lineHeight: Dp,
    sentenceGap: Dp,
    scale: Float,
    fontBoost: Float,
    style: LyricsStyle,
    /** 当前句 / 逐字高亮的颜色。默认主题色；简约封面主题传入从封面取到的强调色。 */
    accentColor: Color = MaterialTheme.colorScheme.primary,
    wordTimes: LyricWordTimes,
    /** 本行开始时间与下一行开始时间：逐字铺色要按它们算「已唱到多少」。 */
    lineStartMs: Long,
    nextLineStartMs: Long?,
    /** 实时播放位置（[rememberLivePosition] 的产物），不是 1Hz 的上报值。 */
    positionMs: State<Long>,
    isPlaying: Boolean,
    perChar: Boolean,
    interludeBefore: InterludeSpan?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val appleStyle = style == LyricsStyle.APPLE
    val glassStyle = style == LyricsStyle.GLASS
    val density = LocalDensity.current
    val reduceMotion = LocalNiReduceMotion.current

    // 焦点进度：当前句 → 1，其它 → 0；平滑过渡，交接时不闪断
    val focus by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(FOCUS_TRANSITION_MS),
        label = "lyricFocus",
    )
    // 透明度与模糊都只由「距离 + 焦点」决定：整句共享一套（长句多行不会各行不同）。
    // 三套主题三种口径：Apple 压得最狠 + 模糊；黑胶平缓 + 光晕 + 轻微放大；
    // 简约封面只做很轻的淡出，层次交给颜色。
    val alpha = when (style) {
        LyricsStyle.APPLE -> distanceOpacity(lineDistance, focus)
        LyricsStyle.VINYL -> vinylDistanceOpacity(lineDistance, focus)
        LyricsStyle.GLASS -> glassDistanceOpacity(lineDistance, focus)
    }
    // 光晕与放大是黑胶的语言（Apple 另有白色辉光）；简约封面两者都不用
    val glowAlpha = when (style) {
        LyricsStyle.APPLE -> focus * 0.62f
        LyricsStyle.VINYL -> focus * 0.55f
        LyricsStyle.GLASS -> 0f
    }
    val blurAmount = if (appleStyle) distanceBlurDp(lineDistance, focus).dp else 0.dp

    // 焦点行的轻微放大：graphicsLayer 只影响绘制、不触发重新布局，避免滚动抖动
    val focusScale = if (style == LyricsStyle.VINYL) 1f + focus * 0.05f else 1f

    // 逐行追赶：焦点下方的行先**抵消掉列表的滚动**（视觉上留在原地），再按距离依次抬升归位。
    // 越靠下等得越久 —— 这就是「下方歌词逐行抬升」。只有相邻向前的换句才会让 generation 变化，
    // 跨行跳转 / 向后跳转时这里不动，行随列表一起走。
    val chase = remember { Animatable(1f) }
    LaunchedEffect(cascade.generation, appleStyle) {
        if (!appleStyle || focusOffset <= 0) {
            chase.snapTo(1f)
            return@LaunchedEffect
        }
        chase.snapTo(0f)
        delay(lyricCascadeDelayMs(focusOffset))
        chase.animateTo(1f, MotionTokens.springSoft)
    }

    val idleFontRatio = if (glassStyle) GLASS_IDLE_FONT_RATIO else VINYL_IDLE_FONT_RATIO
    val letterSpacing = if (glassStyle) GLASS_LYRIC_LETTER_SPACING else VINYL_LYRIC_LETTER_SPACING

    val highlightColor = if (appleStyle) Color.White else accentColor
    val normalColor = if (appleStyle) Color.White else onSurface

    // 逐字铺色：当前句按「已唱到多少」的比例，把上层文字裁切出已唱的部分并逐字抬升（见 perCharSung）。
    // 所有主题、所有歌词格式共用这一套 —— 有逐字时间戳的精确到词，普通 LRC 按行时长估算。
    // 比例只在绘制阶段被读，因此逐帧变化只重画这一层、不触发重组。
    val perCharFraction = rememberSungFraction(
        text = text,
        wordTimes = wordTimes.words,
        lineStartMs = lineStartMs,
        nextLineStartMs = nextLineStartMs,
        livePosition = positionMs,
        enabled = perChar && isCurrent,
    )
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 三段几何（已唱完 / 正在唱 / 未唱）只算一次，两层共用
    // 抬升只在 Apple Music 主题做（另两套只做逐字铺色）
    val sungSplit = remember(layoutResult, perCharFraction, appleStyle) {
        derivedStateOf {
            layoutResult?.let { buildSungSplit(it, perCharFraction.value, lift = appleStyle) }
        }
    }

    // 句间留白挂在**项底部**：句内折行由文字引擎按 lineHeight 排，句与句之间才多出这段间隔。
    // Apple：点击区域收在文字上（空白留给外层切换控件显隐）；其它风格整项可点。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = sentenceGap)
            .then(if (appleStyle) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        // 间奏点缀画在本句**上方**：与文字同项，项号不随间奏出现/消失而偏移（避免自动跟随跳变）
        if (interludeBefore != null) {
            InterludeDots(
                span = interludeBefore,
                positionMs = positionMs,
                lineHeight = lineHeight,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 只用位移/缩放的图层不会分配离屏缓冲；两个值都在绘制阶段读，不触发重组。
                .graphicsLayer {
                    // 缩放只用于黑胶主题
                    if (style == LyricsStyle.VINYL) {
                        scaleX = focusScale
                        scaleY = focusScale
                    }
                    // 逐行追赶：把「列表已经滚掉的距离」按追赶进度抵消掉
                    translationY = cascade.distance.value * (1f - chase.value)
                },
        ) {
        val textStyle = when {
            appleStyle -> MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * scale * fontBoost,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight * scale * fontBoost,
                fontWeight = FontWeight.ExtraBold,
                shadow = if (glowAlpha > 0f) {
                    Shadow(color = Color.White.copy(alpha = glowAlpha), blurRadius = 14f)
                } else {
                    null
                },
            )
            isCurrent -> MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * scale * fontBoost,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight * scale * fontBoost,
                fontWeight = FontWeight.Bold,
                letterSpacing = letterSpacing,
                // 当前句的柔和光晕：用主题色，让「正在唱」的那句从背景里浮起来
                // （简约封面不发光：glowAlpha 恒为 0，这里自然拿到 null）
                shadow = if (glowAlpha > 0f) {
                    Shadow(color = highlightColor.copy(alpha = glowAlpha), blurRadius = 12f)
                } else {
                    null
                },
            )
            else -> MaterialTheme.typography.titleLarge.copy(
                // 与当前句共用同一 lineHeight（= 声明行高 × fontBoost），故行距均匀；
                // 只把字号收小到 [idleFontRatio]，拉开主次又保证可读。
                fontSize = MaterialTheme.typography.titleLarge.fontSize *
                    idleFontRatio * scale * fontBoost,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight * scale * fontBoost,
                fontWeight = FontWeight.Medium,
                letterSpacing = letterSpacing,
            )
        }
        // 逐字抬升量：字号的 10%，夹在 1.5~6dp。
        // **只在 Apple Music 主题做**，且「减少动态效果」时不做。
        val lyricRisePx = if (reduceMotion || !appleStyle) {
            0f
        } else {
            with(density) {
                (textStyle.fontSize.value * LYRIC_LIFT_FONT_RATIO)
                    .coerceIn(LYRIC_LIFT_MIN_DP, LYRIC_LIFT_MAX_DP)
                    .dp
                    .toPx()
            }
        }
        val textModifier = if (appleStyle) {
            Modifier
                // padding 在外、clickable 在内：点击范围就是文字本身（左右各让出
                // 28dp 的边距留给「点空白切换控件」）
                .padding(horizontal = 28.dp)
                .clickable(onClick = onClick)
                .then(
                    if (blurAmount > 0.05.dp) {
                        Modifier.blur(blurAmount, BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    },
                )
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        }
        // 简约封面：当前句背后一条**流动的光带**（亮核缓慢左右漂移 + 极轻呼吸）。
        // 另两套主题的高级感来自各自的光晕/模糊，这里用「光」的另一种形态，不重复它们的语言。
        if (glassStyle && isCurrent) {
            GlassLyricLightBand(
                accent = accentColor,
                isPlaying = isPlaying,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // 光带比行高高，align(TopCenter) 会让它整体偏下 —— 补一个负 offset
                    // 把它的中心对到**首行**的中心（整句折行时也该压在正在唱的那一行上）
                    .offset(y = lineHeight * (1f - GLASS_BAND_HEIGHT_RATIO) / 2f)
                    .fillMaxWidth()
                    .height(lineHeight * GLASS_BAND_HEIGHT_RATIO),
            )
        }
        // 逐字铺色靠「两层同款文本 + 上层裁切」：底层是未唱色，上层是高亮色、按进度裁切。
        // 两层排版必须完全一致，所以样式与修饰符逐字照搬。
        //
        // 两层的配色要分主题：Apple 是统一的纯白大字（它没有主题色层级），逐字只能靠**明暗**
        // 区分 —— 未唱 0.45、已唱 1.0（与原来按词上色的口径一致）；另两套用「正文色 → 强调色」。
        val highlightActive = perChar && isCurrent
        val unsungColor = if (appleStyle) {
            Color.White.copy(alpha = 0.45f)
        } else {
            normalColor.copy(alpha = alpha)
        }
        val sungColor = if (appleStyle) {
            Color.White
        } else {
            highlightColor.copy(alpha = alpha)
        }
        Text(
            text = text,
            style = textStyle,
            // Apple 用统一的纯白大字（靠透明度/模糊分层），其它风格用主题色的层级
            color = if (highlightActive) {
                unsungColor
            } else if (appleStyle) {
                Color.White.copy(alpha = alpha)
            } else if (isCurrent) {
                highlightColor.copy(alpha = alpha)
            } else {
                normalColor.copy(alpha = alpha)
            },
            textAlign = if (appleStyle) TextAlign.Start else TextAlign.Center,
            // 整句完整折行显示，绝不截断
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
            // 只有当前句需要 layout（逐字裁切按它算），其余行不必写这个状态
            onTextLayout = if (highlightActive) {
                { layoutResult = it }
            } else {
                null
            },
            // 底层只画未唱区：已唱的字被抬起后原位会空出来，照常画整句会留下影子
            modifier = textModifier.then(
                if (highlightActive) {
                    Modifier.perCharUnsung { sungSplit.value }
                } else {
                    Modifier
                },
            ),
        )
        if (highlightActive) {
            Text(
                text = text,
                style = textStyle,
                color = sungColor,
                textAlign = if (appleStyle) TextAlign.Start else TextAlign.Center,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                modifier = textModifier.perCharSung(
                    splitProvider = { sungSplit.value },
                    risePx = lyricRisePx,
                ),
            )
        }

        // 预览态：该项右上角显示该句时间，提示再次点击可跳转
        if (isPending && timeLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(onSurface.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
            }
        }
        }
    }
}

/**
 * 简约封面：当前句背后「流动的光带」。
 *
 * 做法：一条横向渐变（中间亮、两端透明）画一遍，再用一条纵向渐变以 [BlendMode.DstIn]
 * 把上下边缘压掉 —— 否则会看到一条有硬边的色带。亮核按相位缓慢左右漂移、亮度轻微呼吸，
 * 于是那束光是**流动**的，而不是一块静止的色斑。
 *
 * 相位与亮度都在绘制阶段读，每帧只重画这一层；暂停或开启「减少动态效果」时相位不推进
 * （循环退出、不请求帧），光带就静静停在原处。
 */
@Composable
internal fun GlassLyricLightBand(
    accent: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalNiReduceMotion.current
    val phase = rememberLoopPhase(
        enabled = isPlaying && !reduceMotion,
        periodMs = GLASS_BAND_PERIOD_MS,
        label = "glassLyricBand",
    )
    Canvas(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val t = phase.value * GlassBandTwoPi
        val drift = sin(t) * size.width * GLASS_BAND_DRIFT
        val breathe = 1f + sin(t) * GLASS_BAND_BREATHE
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.34f to accent.copy(alpha = (GLASS_BAND_SOFT_ALPHA * breathe).coerceIn(0f, 1f)),
                    0.5f to accent.copy(alpha = (GLASS_BAND_ALPHA * breathe).coerceIn(0f, 1f)),
                    0.66f to accent.copy(alpha = (GLASS_BAND_SOFT_ALPHA * breathe).coerceIn(0f, 1f)),
                    1f to Color.Transparent,
                ),
                startX = size.width / 2f + drift - size.width * GLASS_BAND_SPAN,
                endX = size.width / 2f + drift + size.width * GLASS_BAND_SPAN,
            ),
        )
        // 纵向柔化：把上下边缘压掉，只留中间一段
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black, Color.Transparent),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * 间奏点缀：Apple 风格的「点 · 点 · 点」进度点缀（**不显示数字倒计时**）。
 *
 * 高度取一行（[lineHeight]），画在紧随其后那句的上方；高度恒占位，所以不会造成布局/项号偏移。
 * 未进入区间时完全不画（仅留位），到点即依次点亮，末段整体淡出。
 */
@Composable
private fun InterludeDots(
    span: InterludeSpan,
    positionMs: State<Long>,
    lineHeight: Dp,
) {
    val reduced = LocalNiReduceMotion.current
    val density = LocalDensity.current
    val dotsWidth = INTERLUDE_DOT_RADIUS * 2 * INTERLUDE_DOT_COUNT +
        INTERLUDE_DOT_GAP * (INTERLUDE_DOT_COUNT - 1)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeight)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            modifier = Modifier
                .width(dotsWidth)
                // 高度给到直径的 3/2，容下「呼吸 + 末点长大」的峰值半径，避免上下被裁。
                .height(INTERLUDE_DOT_RADIUS * 3),
        ) {
            // 只读 playback 位置：它变化只让本 Canvas 的**绘制**失效，不触发重组。
            val now = positionMs.value
            if (now < span.startMs) return@Canvas
            val duration = (span.endMs - span.startMs).coerceAtLeast(1L)
            val remaining = span.endMs - now
            if (remaining <= 0L) return@Canvas
            val durationF = duration.toFloat()
            val elapsed = (now - span.startMs).coerceIn(0L, duration).toFloat()
            val dotRadiusPx = with(density) { INTERLUDE_DOT_RADIUS.toPx() }
            val dotGapPx = with(density) { INTERLUDE_DOT_GAP.toPx() }
            val centerY = size.height / 2f
            val fadeOut = (remaining / INTERLUDE_FADE_TAIL_MS).coerceIn(0f, 1f)
            val breathe = if (reduced) {
                1f
            } else {
                1f + sin(elapsed / INTERLUDE_BREATHE_PERIOD_MS * 2.0 * PI).toFloat() *
                    INTERLUDE_BREATHE_AMPLITUDE
            }
            repeat(INTERLUDE_DOT_COUNT) { index ->
                // 每个点在自己那一段里从 0.25 充到 1（Apple：点到即亮，越满越实）。
                val segmentStart = durationF * index / INTERLUDE_DOT_COUNT
                val progress = ((elapsed - segmentStart) / (durationF / INTERLUDE_DOT_COUNT))
                    .coerceIn(0.25f, 1f)
                // 最后一个点随充电微微长大，收尾更有指向性。
                val grow = if (index == INTERLUDE_DOT_COUNT - 1) 1f + progress * 0.18f else 1f
                drawCircle(
                    color = Color.White.copy(alpha = progress * fadeOut),
                    radius = dotRadiusPx * breathe * grow,
                    center = Offset(dotRadiusPx + index * dotGapPx, centerY),
                )
            }
        }
    }
}
