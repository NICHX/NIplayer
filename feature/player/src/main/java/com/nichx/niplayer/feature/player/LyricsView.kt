package com.nichx.niplayer.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LyricsView(
    lrcLines: List<LrcLine>,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val currentLineIndex = remember(currentPositionMs, lrcLines) {
        if (lrcLines.isEmpty()) 0 else {
            val index = lrcLines.indexOfLast { it.timeMs <= currentPositionMs }
            if (index < 0) 0 else index
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val itemHeightPx = with(density) { 44.dp.toPx() }
        val visibleLines = if (itemHeightPx > 0) (maxHeightPx / itemHeightPx).toInt() else 6
        val topLines = (visibleLines * 0.3f).toInt().coerceAtLeast(2)
        val bottomLines = (visibleLines * 0.55f).toInt().coerceAtLeast(3)
        val topPadding = with(density) { (topLines * itemHeightPx).toDp() }
        val bottomPadding = with(density) { (bottomLines * itemHeightPx).toDp() }

        LaunchedEffect(currentLineIndex) {
            if (lrcLines.isNotEmpty() && currentLineIndex in lrcLines.indices) {
                listState.scrollToItem(currentLineIndex)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (lrcLines.isEmpty()) {
                Text(
                    text = "暂无歌词",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
                ) {
                    itemsIndexed(
                        items = lrcLines,
                        key = { index, line -> "${line.timeMs}_${index}" },
                    ) { index, line ->
                        LyricLineItem(
                            text = line.text,
                            isCurrent = index == currentLineIndex,
                            onClick = { onSeek(line.timeMs) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    val animAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.35f,
        animationSpec = tween(400),
        label = "lyricAlpha",
    )
    val animScale by animateFloatAsState(
        targetValue = if (isCurrent) 1.15f else 0.90f,
        animationSpec = tween(400),
        label = "lyricScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (isCurrent) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (isCurrent) primary.copy(alpha = animAlpha) else onSurface.copy(alpha = animAlpha),
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            },
        )
    }
}
