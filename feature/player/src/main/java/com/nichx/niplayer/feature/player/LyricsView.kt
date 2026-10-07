package com.nichx.niplayer.feature.player

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import com.nichx.niplayer.designsystem.theme.MotionTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Apple 风格歌词字号倍率：titleLarge 22sp × 1.4 ≈ 31sp。 */
private const val APPLE_FONT_BOOST = 1.4f

/** Apple 风格：当前行落在视口高度的该比例处（偏上方，而非居中）。 */
private const val APPLE_FOCUS_FRACTION = 0.34f

/** 句与句之间的额外留白：句内换行间距由字体 lineHeight 决定，句间再多出这一段。 */
private val APPLE_SENTENCE_GAP = 20.dp
private val PLAIN_SENTENCE_GAP = 16.dp

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
 * 包成 `@Immutable` 后，行参数全部稳定——只有"当前行"因 [doneWordCount] 变化而重组，
 * 其余行直接跳过重组（观感不变，纯性能优化）。
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

/** 距离（行）→ 模糊半径（dp）：1.35 行内为 0，之后线性增长封顶；聚焦行不模糊。 */
private fun distanceBlurDp(lineDistance: Float, focus: Float): Float {
    val progress = (lineDistance - 1.35f).coerceAtLeast(0f)
    val base = (progress * 3.1f).coerceAtMost(10f)
    return base * BLUR_INTENSITY * (1f - focus.coerceIn(0f, 1f))
}

/**
 * 同步歌词视图（Apple Music 风格）。
 *
 * 实现要点：
 * - **一句歌词 = 一个列表项**：句内折行交给文字引擎（按 lineHeight），句间才加留白，
 *   「按句加大间隔」；同时整句共享同一套模糊/透明度，长句多行不会只有第一行清晰；
 * - 当前句落点：contentPadding.top = 视口高 × [focusFraction] − 半行高，配合无偏移的
 *   animateScrollToItem，当前句首行精确落在焦点位置（与句高无关，故长句也准）；
 * - 距离只驱动「透明度 + 模糊」：自动跟随时按**行号差**，手动浏览时按**屏幕距离**——
 *   后者保证用户滑到哪儿、哪儿就是清晰带，不会「滑过去一片全糊」；
 * - 点击跳转；非 Apple 风格保留「先预览再确认」的两次点击。
 *
 * @param maxVisibleLines 最多同时显示的行数（受容器高度约束，取较小值）。
 * @param appleStyle Apple Music 风格：左对齐、白色大字，当前行明亮、其它行暗淡且发散模糊。
 * @param onUserScroll 用户开始手动滚动时回调（供外层「任何交互即呼出控件」用）。
 */
@Composable
fun LyricsView(
    lrcLines: List<LrcLine>,
    currentPositionMs: State<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleLines: Int = Int.MAX_VALUE,
    appleStyle: Boolean = false,
    onUserScroll: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val baseTitleLarge = MaterialTheme.typography.titleLarge

    val currentSentenceIndex = remember(currentPositionMs.value, lrcLines) {
        if (lrcLines.isEmpty()) 0 else {
            val index = lrcLines.indexOfLast { it.timeMs <= currentPositionMs.value }
            if (index < 0) 0 else index
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
        val fontBoost = if (appleStyle) APPLE_FONT_BOOST else 1f

        // 行高（单行文字盒高）：居中落点、视口行数与距离换算都以它为单位
        val lineHeightSp = baseTitleLarge.lineHeight * scale * fontBoost
        val lineHeightPx = with(density) { lineHeightSp.toPx() }
        val lineHeight = with(density) { lineHeightPx.toDp() }
        val sentenceGap = if (appleStyle) APPLE_SENTENCE_GAP else PLAIN_SENTENCE_GAP

        // 列表项：每句一项；Apple 风格把长间隔处生成的间奏点缀挂到**下一句**的项内
        val items = remember(lrcLines, appleStyle) {
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

        // 自动跟随：当前句 / 间奏切换时回到焦点项；浏览期间不打断，浏览结束再触发回位。
        LaunchedEffect(focusedIndex, browsing) {
            if (focusedIndex < 0 || browsing) return@LaunchedEffect
            if (reducedMotion) {
                listState.scrollToItem(focusedIndex)
                return@LaunchedEffect
            }
            // 目标项已在屏内时，按「像素差」用更柔的弹性滚过去（项顶端到锚点 = item.offset，
            // 见 LazyListMeasure：屏幕 y = offset − viewportStartOffset）。比 animateScrollToItem
            // 默认的偏硬弹簧顺得多；不在屏内（含跳转）才交给它处理。
            val visible = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == focusedIndex }
            if (visible != null) {
                // 本句带间奏点缀时，点缀盒在项顶：多滚一个行高，让**文字首行**（而非点缀）落在锚点
                val dotsLead = if (items[focusedIndex].interludeBefore != null) lineHeightPx else 0f
                listState.animateScrollBy(
                    visible.offset.toFloat() + dotsLead,
                    MotionTokens.springScroll,
                )
            } else {
                listState.animateScrollToItem(focusedIndex)
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

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            positionMs = currentPositionMs,
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
                            lineHeight = lineHeight,
                            sentenceGap = sentenceGap,
                            scale = scale,
                            fontBoost = fontBoost,
                            appleStyle = appleStyle,
                            wordTimes = remember(sentence.wordTimes) {
                                LyricWordTimes(sentence.wordTimes)
                            },
                            // 非当前句的已唱词数恒为 -1：位置每秒上报时该参数不变，
                            // 行因此可被跳过重组（观感不变）。
                            doneWordCount = if (sentenceIndex == currentSentenceIndex) {
                                sentence.wordTimes.count { it.second <= currentPositionMs.value }
                            } else {
                                -1
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
    lineHeight: Dp,
    sentenceGap: Dp,
    scale: Float,
    fontBoost: Float,
    appleStyle: Boolean,
    wordTimes: LyricWordTimes,
    doneWordCount: Int,
    interludeBefore: InterludeSpan?,
    positionMs: State<Long>,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    // 焦点进度：当前句 → 1，其它 → 0；平滑过渡，交接时不闪断
    val focus by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(FOCUS_TRANSITION_MS),
        label = "lyricFocus",
    )
    // 透明度与模糊都只由「距离 + 焦点」决定：整句共享一套（长句多行不会各行不同）
    val alpha = distanceOpacity(lineDistance, focus)
    val glowAlpha = if (appleStyle) focus * 0.62f else 0f
    val blurAmount = if (appleStyle) distanceBlurDp(lineDistance, focus).dp else 0.dp

    val highlightColor = if (appleStyle) Color.White else primary
    val normalColor = if (appleStyle) Color.White else onSurface

    // 逐字高亮：当前句且有逐字时间戳时，已唱到的词用高亮色，未唱到的用浅色。
    val displayText = if (isCurrent && wordTimes.words.isNotEmpty()) {
        buildAnnotatedString {
            val baseColor = highlightColor.copy(alpha = if (appleStyle) 0.45f else alpha)
            val doneColor = highlightColor.copy(alpha = 1f)
            var cursor = 0
            wordTimes.words.forEachIndexed { index, entry ->
                val word = entry.first
                val found = text.indexOf(word, cursor)
                if (found >= 0) {
                    withStyle(
                        SpanStyle(
                            color = if (index < doneWordCount) doneColor else baseColor,
                        ),
                    ) {
                        append(word)
                    }
                    cursor = found + word.length
                }
            }
            // 尾部多余文本（逐字时间戳覆盖不到的）用基础色
            if (cursor < text.length) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(cursor))
                }
            }
        }
    } else {
        null
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
        Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = displayText ?: AnnotatedString(text),
            style = when {
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
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * scale,
                    fontWeight = FontWeight.Bold,
                )
                else -> MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * scale,
                )
            },
            // Apple 用统一的纯白大字（靠透明度/模糊分层），其它风格用主题色的层级
            color = if (appleStyle) {
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
            modifier = if (appleStyle) {
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
            },
        )

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
                    color = primary,
                )
            }
        }
        }
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
