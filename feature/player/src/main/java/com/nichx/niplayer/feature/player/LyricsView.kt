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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import java.util.Locale
import kotlin.math.absoluteValue

/** 歌词物理行固定高度。 */
private val ROW_HEIGHT = 44.dp

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

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

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
 */
@Composable
fun LyricsView(
    lrcLines: List<LrcLine>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibleLines: Int = Int.MAX_VALUE,
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
        val rowHeight = ROW_HEIGHT * scale
        val rowHeightPx = with(density) { rowHeight.toPx() }
        // 测量样式同步放大：保证拆行测量与渲染字号一致
        val scaledMeasureStyle = baseTitleLarge.copy(
            fontSize = baseTitleLarge.fontSize * scale,
            fontWeight = FontWeight.Bold,
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

        val viewportLines = with(density) {
            (maxHeight / rowHeight).toInt().coerceAtLeast(3)
        }.let { minOf(it, maxVisibleLines) }

        val viewportHeightPx = with(density) { (rowHeight * viewportLines).toPx() }

        // 精确居中：contentPadding 上下对称 = (视口高 − 行高) / 2，
        // 无偏移 animateScrollToItem 滚动后当前行中心即视口中心
        val centerPaddingPx = ((viewportHeightPx - rowHeightPx) / 2f)
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
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.height(with(density) { viewportHeightPx.toDp() }),
                    contentPadding = PaddingValues(
                        top = with(density) { centerPaddingPx.toDp() },
                        bottom = with(density) { centerPaddingPx.toDp() },
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
                                formatTime(lrcLines[row.sentenceIndex].timeMs)
                            } else null,
                            onClick = {
                                val sentenceIndex = row.sentenceIndex
                                if (pendingSentenceIndex == sentenceIndex) {
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
    // 当前行不缩放：避免视觉放大后超出按 titleLarge+Bold 测量的行宽造成截断；
    // 当前行靠大字号 + 加粗 + 主题色区分，普通行保持 1.0 比例。
    val animScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "lyricScale",
    )

    // 逐字高亮：当前句且有逐字时间戳时，已唱到的词用主题色，未唱到的用浅色
    val displayText = if (isCurrent && wordTimes.isNotEmpty()) {
        buildAnnotatedString {
            val baseColor = primary.copy(alpha = animAlpha)
            val doneColor = primary.copy(alpha = 1f)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText ?: AnnotatedString(text),
            style = if (isCurrent) {
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * scale,
                )
            } else {
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize * scale,
                )
            },
            color = if (isCurrent) primary.copy(alpha = animAlpha)
            else onSurface.copy(alpha = animAlpha),
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
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
