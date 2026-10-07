package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.MotionTokens
import com.nichx.niplayer.feature.player.theme.LyricsTheme
import com.nichx.niplayer.feature.player.theme.VinylLyrics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

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
 * 句与句之间才加句间留白（`LyricsTheme.sentenceGap`）——「按句加大间隔」；同时
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
 * - 点击跳转：是否一次即跳由 [LyricsTheme.seekOnFirstTap] 决定 —— Apple 是，
 *   另两套保留「先预览再确认」的两次点击。
 *
 * @param maxVisibleLines 最多同时显示的行数（受容器高度约束，取较小值）。
 * @param theme 歌词页的外观描述，见 [LyricsTheme]。
 * @param onUserScroll 用户开始手动滚动时回调（供外层「任何交互即呼出控件」用）。
 */
@Composable
internal fun LyricsView(
    lrcLines: List<LrcLine>,
    currentPositionMs: State<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleLines: Int = Int.MAX_VALUE,
    theme: LyricsTheme = VinylLyrics,
    /** 当前句 / 逐字高亮的颜色。默认主题色；简约封面主题传入从封面取到的强调色。 */
    accentColor: Color = MaterialTheme.colorScheme.primary,
    /** 是否正在播放：逐字铺色与光带的动画只在播放时推进（暂停即停、不请求帧）。 */
    isPlaying: Boolean = false,
    /** 「逐字歌词」开关：关掉后当前句整句同色，不做逐字铺色。 */
    perChar: Boolean = true,
    onUserScroll: () -> Unit = {},
) {
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
        val fontBoost = theme.fontBoost

        // 行高（单行文字盒高）：居中落点、视口行数与距离换算都以它为单位
        val lineHeightSp = baseTitleLarge.lineHeight * scale * fontBoost
        val lineHeightPx = with(density) { lineHeightSp.toPx() }
        val lineHeight = with(density) { lineHeightPx.toDp() }
        val sentenceGap = theme.sentenceGap

        // 列表项：每句一项；Apple 风格把长间隔处生成的间奏点缀挂到**下一句**的项内
        val items = remember(lrcLines, theme) {
            lrcLines.mapIndexed { sentenceIndex, line ->
                val interlude = if (theme.interlude && sentenceIndex > 0) {
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
        val focusFraction = theme.focusFraction

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
            val chasing = theme.cascadeChase &&
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
                    if (theme.vignette) {
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
                    color = if (theme.whiteText) Color.White.copy(alpha = 0.6f)
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
                                    theme.seekOnFirstTap -> onSeek(sentenceTimeMs)
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
                            theme = theme,
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
    theme: LyricsTheme,
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
    val alpha = theme.distanceCurve.opacity(lineDistance, focus)
    // 光晕与放大是黑胶的语言（Apple 另有白色辉光）；简约封面两者都不用
    val glowAlpha = focus * theme.glowAlphaPerFocus
    val blurAmount = theme.distanceCurve.blurDp(lineDistance, focus).dp

    // 焦点行的轻微放大：graphicsLayer 只影响绘制、不触发重新布局，避免滚动抖动
    val focusScale = 1f + focus * theme.focusScaleAmount

    // 逐行追赶：焦点下方的行先**抵消掉列表的滚动**（视觉上留在原地），再按距离依次抬升归位。
    // 越靠下等得越久 —— 这就是「下方歌词逐行抬升」。只有相邻向前的换句才会让 generation 变化，
    // 跨行跳转 / 向后跳转时这里不动，行随列表一起走。
    val chase = remember { Animatable(1f) }
    LaunchedEffect(cascade.generation, theme.cascadeChase) {
        if (!theme.cascadeChase || focusOffset <= 0) {
            chase.snapTo(1f)
            return@LaunchedEffect
        }
        chase.snapTo(0f)
        delay(lyricCascadeDelayMs(focusOffset))
        chase.animateTo(1f, MotionTokens.springSoft)
    }

    val idleFontRatio = theme.idleFontRatio
    val letterSpacing = theme.letterSpacing

    val highlightColor = if (theme.whiteText) Color.White else accentColor
    val normalColor = if (theme.whiteText) Color.White else onSurface

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
    // 荧光强度与半径：只在简约封面用，深色 / 浅色各一档（见 LyricsGlow 的说明）。
    // 名字带 glass 前缀：上面那个 glowAlpha 是另两套主题的当前句光晕，别撞。
    val glassGlowDark = NiExtraColors.current.isDark
    val glassGlowAlpha = theme.glow?.let {
        if (glassGlowDark) it.alphaDark else it.alphaLight
    } ?: 0f
    val glowPx = theme.glow?.let {
        with(density) {
            (if (glassGlowDark) it.blurDark else it.blurLight).toPx()
        }
    } ?: 0f
    // 已唱到第几个字。单独派生一个 Int：`SungSplit` 不是 data class，直接读它的 value
    // 每帧都会得到一个「新对象」，整行会被带着每帧重组。
    val glowBoundary by remember(layoutResult, perCharFraction) {
        derivedStateOf {
            val length = layoutResult?.layoutInput?.text?.length ?: 0
            (perCharFraction.value * length).toInt().coerceIn(0, length)
        }
    }
    // 抬升只在 Apple Music 主题做（另两套只做逐字铺色）；glow 区域给荧光层用
    val sungSplit = remember(layoutResult, perCharFraction, theme.liftSung, glowPx) {
        derivedStateOf {
            layoutResult?.let {
                buildSungSplit(it, perCharFraction.value, lift = theme.liftSung, glowRadiusPx = glowPx)
            }
        }
    }

    // 句间留白挂在**项底部**：句内折行由文字引擎按 lineHeight 排，句与句之间才多出这段间隔。
    // Apple：点击区域收在文字上（空白留给外层切换控件显隐）；其它风格整项可点。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = sentenceGap)
            .then(if (theme.tapTargetOnText) Modifier else Modifier.clickable(onClick = onClick)),
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
                    // 缩放只用于黑胶主题（focusScaleAmount 为 0 时不进这条分支）
                    if (theme.focusScaleAmount > 0f) {
                        scaleX = focusScale
                        scaleY = focusScale
                    }
                    // 逐行追赶：把「列表已经滚掉的距离」按追赶进度抵消掉
                    translationY = cascade.distance.value * (1f - chase.value)
                },
        ) {
        val textStyle = when {
            theme.whiteText -> MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * scale * fontBoost,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight * scale * fontBoost,
                fontWeight = FontWeight.ExtraBold,
                shadow = if (glowAlpha > 0f) {
                    Shadow(
                        color = Color.White.copy(alpha = glowAlpha),
                        blurRadius = theme.glowBlurRadiusPx,
                    )
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
                    Shadow(
                        color = highlightColor.copy(alpha = glowAlpha),
                        blurRadius = theme.glowBlurRadiusPx,
                    )
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
        val lyricRisePx = if (reduceMotion || !theme.liftSung) {
            0f
        } else {
            with(density) {
                (textStyle.fontSize.value * LYRIC_LIFT_FONT_RATIO)
                    .coerceIn(LYRIC_LIFT_MIN_DP, LYRIC_LIFT_MAX_DP)
                    .dp
                    .toPx()
            }
        }
        val textModifier = if (theme.tapTargetOnText) {
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
        // 逐字铺色靠「两层同款文本 + 上层裁切」：底层是未唱色，上层是高亮色、按进度裁切。
        // 两层排版必须完全一致，所以样式与修饰符逐字照搬。
        //
        // 两层的配色要分主题：Apple 是统一的纯白大字（它没有主题色层级），逐字只能靠**明暗**
        // 区分 —— 未唱 0.45、已唱 1.0（与原来按词上色的口径一致）；另两套用「正文色 → 强调色」。
        val highlightActive = perChar && isCurrent
        val unsungColor = if (theme.whiteText) {
            Color.White.copy(alpha = 0.45f)
        } else {
            normalColor.copy(alpha = alpha)
        }
        val sungColor = if (theme.whiteText) {
            Color.White
        } else {
            highlightColor.copy(alpha = alpha)
        }
        // 荧光层**垫在最下面**：它的裁切范围比已唱区外扩了一圈，扩出来的那点会落到未唱区，
        // 正好被下面的底层文字盖住 —— 既让首尾两个字的晕完整，又不会露出半个字。
        if (highlightActive && theme.glow != null) {
            // 已唱段不透明（发光）、未唱段透明（不发光）：扩出去的那圈里紧邻的未唱字就不会跟着亮
            Text(
                text = remember(text, glowBoundary) { lyricGlowText(text, glowBoundary) },
                style = textStyle.copy(
                    shadow = Shadow(
                        color = accentColor.copy(alpha = glassGlowAlpha),
                        blurRadius = glowPx,
                    ),
                ),
                color = sungColor,
                textAlign = theme.textAlign,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                modifier = textModifier.perCharGlow { sungSplit.value },
            )
        }
        Text(
            text = text,
            style = textStyle,
            // Apple 用统一的纯白大字（靠透明度/模糊分层），其它风格用主题色的层级
            color = if (highlightActive) {
                unsungColor
            } else if (theme.whiteText) {
                Color.White.copy(alpha = alpha)
            } else if (isCurrent) {
                highlightColor.copy(alpha = alpha)
            } else {
                normalColor.copy(alpha = alpha)
            },
            textAlign = theme.textAlign,
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
                textAlign = theme.textAlign,
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
