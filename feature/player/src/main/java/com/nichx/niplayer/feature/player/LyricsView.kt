package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** 歌词物理行固定高度。 */
private val ROW_HEIGHT = 44.dp

/**
 * Apple Music 风格歌词行高（34sp ExtraBold）。
 *
 * 行高明显大于字号：Apple 的歌词是一句一行、句间留白的排版，行距太小会挤成
 * 一整块，读起来分不出句子的边界。
 */
private val APPLE_ROW_HEIGHT = 64.dp

/** Apple 风格歌词字号倍率：titleLarge 22sp × 1.55 ≈ 34sp。 */
private const val APPLE_FONT_BOOST = 1.55f

/**
 * Apple 非当前行的模糊：最多 [APPLE_BLUR_STEPS] 步、每步 [APPLE_BLUR_STEP_DP]。
 *
 * 模糊值只跟「离当前行多远」有关，是静态值——不做逐帧动画，每行的模糊图层只在
 * 行号交接时才重建一次，滚动的每一帧只是在合成已有图层。
 */
private const val APPLE_BLUR_STEPS = 5
private const val APPLE_BLUR_STEP_DP = 1.2f

/** Apple 风格：当前行落在视口高度的该比例处（偏上方，而非居中）。 */
private const val APPLE_FOCUS_FRACTION = 0.34f

/**
 * 单句歌词最多拆分的物理行数。
 * 设为一个非常大的值：超长歌词按可用宽度完整自动换行，几乎不会触发截断。
 */
private const val MAX_PHYSICAL_LINES_PER_SENTENCE = 30

/**
 * 物理歌词行：由一句歌词按宽度拆分成的一行，用于等高管控与精确居中。
 *
 * @param sentenceIndex 所属原句在 [lrcLines] 中的下标。
 * @param lineIndexInSentence 该物理行在句内的序号（0 起）。
 * @param text 该物理行显示的文本。
 */
private data class LyricRow(
    val sentenceIndex: Int,
    val lineIndexInSentence: Int,
    val text: String,
)

/**
 * 同步歌词视图。
 *
 * 实现要点（物理行方案，保证当前行 100% 精确居中）：
 * - 长歌词先按可用宽度拆成多个等高物理行（每行 [ROW_HEIGHT]），整句显示完整、不截断；
 * - LazyColumn 每行等高，contentPadding 上下对称 = (视口高 − 行高) / 2，
 *   配合无偏移的 animateScrollToItem 滚动，当前行中心精确落在视口中央，不受
 *   scrollToItem scrollOffset 参数 clamp 的影响；
 * - 视口上下边缘的歌词按与当前行的距离动态降低透明度，实现自然淡出过渡；
 * - 点击歌词行进入预览态（右上角显示该句时间），再次点击同一句才跳转播放进度。
 *
 * @param maxVisibleLines 最多同时显示的行数（受容器高度约束，取较小值）。
 * @param appleStyle Apple Music 风格：左对齐、白色大字，当前行明亮、其它行暗淡。
 */
@Composable
fun LyricsView(
    lrcLines: List<LrcLine>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleLines: Int = Int.MAX_VALUE,
    appleStyle: Boolean = false,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // 测量样式基准：当前行最宽渲染样式（titleLarge + Bold）。
    // 这样无论该行是当前行（titleLarge+Bold）还是普通行（titleMedium），
    // 渲染宽度都不超过测量宽度，整句完整自动换行、永不截断。
    // 大屏自适应：最终字号按 scale 等比放大（见下方 BoxWithConstraints）。
    val baseTitleLarge = MaterialTheme.typography.titleLarge

    val currentSentenceIndex = remember(currentPositionMs, lrcLines) {
        if (lrcLines.isEmpty()) 0 else {
            val index = lrcLines.indexOfLast { it.timeMs <= currentPositionMs }
            if (index < 0) 0 else index
        }
    }

    // 点击预览：第一次点击仅选中该句并显示时间，再次点击同一句才跳转
    var pendingSentenceIndex by remember(lrcLines) { mutableStateOf<Int?>(null) }

    // 播放推进到新句时清除预览状态
    LaunchedEffect(currentSentenceIndex) {
        pendingSentenceIndex = null
    }

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { (maxWidth - 48.dp).toPx() }

        // 大屏自适应：可用宽度越宽，行高与字号等比放大，
        // 避免大屏（平板/大屏手机横屏）下歌词行数少、字体显小
        val scale = when {
            maxWidth < 420.dp -> 1f
            maxWidth < 560.dp -> 1.15f
            else -> 1.3f
        }
        val rowHeight = (if (appleStyle) APPLE_ROW_HEIGHT else ROW_HEIGHT) * scale
        val rowHeightPx = with(density) { rowHeight.toPx() }
        // Apple Music 风格：更大的字号与更松的行高
        val fontBoost = if (appleStyle) APPLE_FONT_BOOST else 1f
        // 测量样式同步放大：保证拆行测量与渲染字号一致
        val scaledMeasureStyle = baseTitleLarge.copy(
            fontSize = baseTitleLarge.fontSize * scale * fontBoost,
            lineHeight = baseTitleLarge.lineHeight * scale * fontBoost,
            fontWeight = if (appleStyle) FontWeight.ExtraBold else FontWeight.Bold,
        )

        // 物理行拆分：先按可用宽度把每句完整拆成多行（不限行数），
        // 再对每句限制最多 MAX_PHYSICAL_LINES_PER_SENTENCE 行，超出部分截断并给末行加省略号。
        val rows = remember(lrcLines, maxWidthPx, scaledMeasureStyle, density) {
            val all = buildList {
                lrcLines.forEachIndexed { sentenceIndex, line ->
                    if (line.text.isBlank()) {
                        add(LyricRow(sentenceIndex, 0, ""))
                        return@forEachIndexed
                    }
                    val measured = textMeasurer.measure(
                        text = line.text,
                        style = scaledMeasureStyle,
                        constraints = Constraints(
                            maxWidth = maxWidthPx.toInt().coerceAtLeast(1),
                        ),
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                    if (measured.size.height <= rowHeightPx) {
                        add(LyricRow(sentenceIndex, 0, line.text))
                    } else {
                        val lineCount = measured.lineCount
                        for (lineIdx in 0 until lineCount) {
                            val lineStart = measured.getLineStart(lineIdx)
                            val lineEnd = measured.getLineEnd(lineIdx, visibleEnd = false)
                            if (lineStart >= lineEnd) continue
                            add(
                                LyricRow(
                                    sentenceIndex = sentenceIndex,
                                    lineIndexInSentence = lineIdx,
                                    text = line.text.substring(lineStart, lineEnd).trim(),
                                ),
                            )
                        }
                    }
                }
            }

            val sentenceRowCount = mutableMapOf<Int, Int>()
            buildList {
                for (row in all) {
                    val count = sentenceRowCount[row.sentenceIndex] ?: 0
                    if (count < MAX_PHYSICAL_LINES_PER_SENTENCE) {
                        sentenceRowCount[row.sentenceIndex] = count + 1
                        add(row)
                    } else if (count == MAX_PHYSICAL_LINES_PER_SENTENCE) {
                        // 首次超限：给该句最后一行加省略号，后续超限行直接跳过
                        val last = lastOrNull()?.takeIf { it.sentenceIndex == row.sentenceIndex }
                        if (last != null) {
                            val lastIdx = lastIndex
                            this[lastIdx] = last.copy(text = last.text.trimEnd() + "…")
                        }
                        sentenceRowCount[row.sentenceIndex] = count + 1
                    }
                }
            }
        }

        val currentRowIndex = remember(currentSentenceIndex, rows) {
            rows.indexOfFirst { it.sentenceIndex == currentSentenceIndex }
        }

        val viewportLines = minOf(
            with(density) { (maxHeight / rowHeight).toInt().coerceAtLeast(3) },
            maxVisibleLines,
        )

        val viewportHeightPx = with(density) { (rowHeight * viewportLines).toPx() }

        // 当前行的落点：普通风格居中（0.5），Apple 风格偏上（0.34）。
        // contentPadding 按该比例分配，无偏移的 animateScrollToItem 滚动后
        // 当前行中心即落在视口高度的 focusFraction 处。
        val focusFraction = if (appleStyle) APPLE_FOCUS_FRACTION else 0.5f
        val topPaddingPx = (viewportHeightPx * focusFraction - rowHeightPx / 2f)
            .toInt()
            .coerceAtLeast(0)
        val bottomPaddingPx = (viewportHeightPx - rowHeightPx - topPaddingPx)
            .toInt()
            .coerceAtLeast(0)

        LaunchedEffect(currentRowIndex) {
            if (currentRowIndex >= 0) {
                listState.animateScrollToItem(currentRowIndex)
            }
        }

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
                    modifier = Modifier.height(with(density) { viewportHeightPx.toDp() }),
                    contentPadding = PaddingValues(
                        top = with(density) { topPaddingPx.toDp() },
                        bottom = with(density) { bottomPaddingPx.toDp() },
                    ),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { index, row -> "${row.sentenceIndex}_${row.lineIndexInSentence}_$index" },
                    ) { index, row ->
                        LyricRowItem(
                            text = row.text,
                            isCurrent = row.sentenceIndex == currentSentenceIndex,
                            isPending = pendingSentenceIndex == row.sentenceIndex,
                            timeLabel = if (row.lineIndexInSentence == 0) {
                                formatDurationShort(lrcLines[row.sentenceIndex].timeMs)
                            } else null,
                            onClick = {
                                val sentenceIndex = row.sentenceIndex
                                // Apple：点一下文字就跳转，不做「先预览再确认」那一步——
                                // 这里的点击区域本来就只有文字，再要两次点击会很别扭。
                                if (appleStyle) {
                                    onSeek(lrcLines[sentenceIndex].timeMs)
                                } else if (pendingSentenceIndex == sentenceIndex) {
                                    onSeek(lrcLines[sentenceIndex].timeMs)
                                    pendingSentenceIndex = null
                                } else {
                                    pendingSentenceIndex = sentenceIndex
                                }
                            },
                            distanceFromCurrent = index - currentRowIndex,
                            viewportLines = viewportLines,
                            rowHeight = rowHeight,
                            scale = scale,
                            fontBoost = fontBoost,
                            appleStyle = appleStyle,
                            wordTimes = if (row.lineIndexInSentence == 0) {
                                lrcLines[row.sentenceIndex].wordTimes
                            } else emptyList(),
                            currentPositionMs = currentPositionMs,
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
    distanceFromCurrent: Int,
    viewportLines: Int,
    rowHeight: Dp,
    scale: Float,
    fontBoost: Float,
    appleStyle: Boolean,
    wordTimes: List<Pair<String, Long>>,
    currentPositionMs: Long,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    // 按与当前行的距离动态降低透明度：越靠边缘越淡，实现自然淡出过渡
    val distanceFraction = distanceFromCurrent.toFloat() / viewportLines.coerceAtLeast(1)
    val edgeAlpha = (1f - distanceFraction.coerceIn(-1f, 1f).absoluteValue)
        .coerceIn(0f, 1f)
        .let { 0.2f + 0.8f * it }

    val animAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else edgeAlpha,
        animationSpec = tween(300),
        label = "lyricAlpha",
    )
    // Apple 风格：全部行统一的纯白大字，当前行明亮 + 辉光、其它行按距离更暗；
    // 其它风格沿用主题色与「当前行更大」的层级。
    val highlightColor = if (appleStyle) Color.White else primary
    val normalColor = if (appleStyle) Color.White else onSurface
    val normalAlpha = if (appleStyle) animAlpha * 0.85f else animAlpha
    // Apple 的当前行辉光：随行淡入淡出，交接时不闪断
    val glowAlpha by animateFloatAsState(
        targetValue = if (appleStyle && isCurrent) 0.62f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "lyricGlow",
    )
    // Apple 风格：离当前行越远越模糊——这是 Apple 歌词质感的关键，只靠透明度
    // 会显得很平。当前行不模糊。
    val blurAmount = if (appleStyle) {
        (distanceFromCurrent.absoluteValue.coerceAtMost(APPLE_BLUR_STEPS) * APPLE_BLUR_STEP_DP).dp
    } else {
        0.dp
    }

    // 逐字高亮：当前句且有逐字时间戳时，已唱到的词用高亮色，未唱到的用浅色
    val displayText = if (isCurrent && wordTimes.isNotEmpty()) {
        buildAnnotatedString {
            val baseColor = highlightColor.copy(alpha = if (appleStyle) 0.45f else animAlpha)
            val doneColor = highlightColor.copy(alpha = 1f)
            var cursor = 0
            for ((word, startMs) in wordTimes) {
                val found = text.indexOf(word, cursor)
                if (found < 0) continue
                val isDone = startMs <= currentPositionMs
                withStyle(
                    SpanStyle(
                        color = if (isDone) doneColor else baseColor,
                    ),
                ) {
                    append(word)
                }
                cursor = found + word.length
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

    // Apple：点击区域收在**文字**上，一行里的空白留给外层去切换控件显隐，
    // 所以整行不挂 clickable，只在 Text 上挂，且不给它撑满宽度。
    // 其它风格整行可点：居中排版下整行点更跟手。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .then(if (appleStyle) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = if (appleStyle) Alignment.CenterStart else Alignment.Center,
    ) {
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
            color = if (isCurrent) highlightColor.copy(alpha = animAlpha)
            else normalColor.copy(alpha = normalAlpha),
            textAlign = if (appleStyle) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (appleStyle) {
                Modifier
                    // padding 在外、clickable 在内：点击范围就是文字本身（左右各让出
                    // 28dp 的边距留给「点空白切换控件」）
                    .padding(horizontal = 28.dp)
                    .clickable(onClick = onClick)
                    .then(
                        if (blurAmount > 0.dp) {
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

        // 预览态：该句首行右上角显示时间，提示再次点击可跳转
        if (isPending && timeLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
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
