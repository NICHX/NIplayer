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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.absoluteValue

/** 歌词物理行固定高度。 */
private val ROW_HEIGHT = 44.dp

/** 单句歌词最多拆分的物理行数，超出部分截断（保护极端长歌词）。 */
private const val MAX_PHYSICAL_LINES_PER_SENTENCE = 6

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
    val measureStyle = MaterialTheme.typography.titleMedium

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
        val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }

        // 物理行拆分：每句按可用宽度拆成多行，行高统一
        val rows = remember(lrcLines, maxWidthPx, measureStyle, density) {
            buildList {
                lrcLines.forEachIndexed { sentenceIndex, line ->
                    if (line.text.isBlank()) {
                        add(LyricRow(sentenceIndex, 0, ""))
                        return@forEachIndexed
                    }
                    val measured = textMeasurer.measure(
                        text = line.text,
                        style = measureStyle,
                        constraints = Constraints(
                            maxWidth = maxWidthPx.toInt().coerceAtLeast(1),
                        ),
                        maxLines = MAX_PHYSICAL_LINES_PER_SENTENCE,
                        overflow = TextOverflow.Ellipsis,
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
        }

        val currentRowIndex = remember(currentSentenceIndex, rows) {
            rows.indexOfFirst { it.sentenceIndex == currentSentenceIndex }
        }

        val viewportLines = with(density) {
            (maxHeight / ROW_HEIGHT).toInt().coerceAtLeast(3)
        }.let { minOf(it, maxVisibleLines) }

        val viewportHeightPx = with(density) { (ROW_HEIGHT * viewportLines).toPx() }

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
                    text = "暂无歌词",
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
    val animScale by animateFloatAsState(
        targetValue = if (isCurrent) 1.08f else 0.95f,
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
            .height(ROW_HEIGHT)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText ?: AnnotatedString(text),
            style = if (isCurrent) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
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
