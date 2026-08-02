package com.nichx.niplayer.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val CARD_W = 110.dp
private val CARD_H = 152.dp
private val MARGIN = 12.dp
private val BOTTOM_MARGIN = 88.dp
private const val IDLE_ALPHA = 0.6f
private val BORDER_WIDTH = 0.5.dp

@Composable
fun MusicBar(
    playbackManager: AudioPlaybackManager,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val isPlaying by playbackManager.isPlaying.collectAsStateWithLifecycle()
    val title by playbackManager.currentTitle.collectAsStateWithLifecycle()
    val coverPath by playbackManager.audioCoverPath.collectAsStateWithLifecycle()

    val hasActiveTrack = title.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = hasActiveTrack && visible,
            enter = scaleIn(tween(300)) + fadeIn(tween(300)),
            exit = scaleOut(tween(300)) + fadeOut(tween(300)),
        ) {
            FloatingMiniPlayerCard(
                coverPath = coverPath,
                title = title,
                isPlaying = isPlaying,
                onPlayPause = { playbackManager.togglePlayPause() },
                onNext = { playbackManager.playNext() },
                onPrevious = { playbackManager.playPrevious() },
                onClose = { playbackManager.stopPlayback() },
                onNavigateToPlayer = onNavigateToPlayer,
            )
        }
    }
}

@Composable
private fun FloatingMiniPlayerCard(
    coverPath: String?,
    title: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val extraColors = NiExtraColors.current
    val isDark = extraColors.isDark
    val surfaceColor = if (isDark) Color(0xFF1C1C2E) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val density = LocalDensity.current
    val titleScrollState = rememberScrollState()

    LaunchedEffect(title) {
        titleScrollState.scrollTo(0)
        delay(1500)
        while (true) {
            val maxScroll = titleScrollState.maxValue
            if (maxScroll > 0) {
                val duration = (maxScroll * 3).toInt().coerceIn(1200, 6000)
                titleScrollState.animateScrollTo(maxScroll, tween(duration))
                delay(2000)
                titleScrollState.animateScrollTo(0, tween(duration))
                delay(2000)
            } else {
                delay(5000)
            }
        }
    }

    var isInteracting by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else IDLE_ALPHA,
        animationSpec = tween(400),
        label = "cardAlpha",
    )

    val cardWPx = with(density) { CARD_W.toPx() }
    val cardHPx = with(density) { CARD_H.toPx() }
    val marginPx = with(density) { MARGIN.toPx() }
    val bottomMarginPx = with(density) { BOTTOM_MARGIN.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = with(density) { maxWidth.toPx() }
        val parentH = with(density) { maxHeight.toPx() }

        if (!initialized && parentW > 0f && parentH > 0f) {
            offsetX = parentW - cardWPx - marginPx
            offsetY = parentH - cardHPx - bottomMarginPx
            initialized = true
        }

        val dragMaxX = (parentW - cardWPx).coerceAtLeast(0f)
        val dragMaxY = (parentH - cardHPx).coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(CARD_W, CARD_H)
                .alpha(cardAlpha)
                .background(surfaceColor, RoundedCornerShape(16.dp))
                .border(BORDER_WIDTH, borderColor, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(onNavigateToPlayer) {
                    detectTapGestures(
                        onTap = {
                            isInteracting = true
                            onNavigateToPlayer()
                        },
                    )
                }
                .pointerInput(dragMaxX, dragMaxY) {
                    detectDragGestures(
                        onDragStart = { isInteracting = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x)
                                .coerceIn(0f, dragMaxX)
                            offsetY = (offsetY + dragAmount.y)
                                .coerceIn(0f, dragMaxY)
                        },
                        onDragEnd = { isInteracting = false },
                        onDragCancel = { isInteracting = false },
                    )
                },
        ) {
            if (coverPath != null) {
                val context = LocalContext.current
                val request = remember(coverPath) {
                    ImageRequest.Builder(context)
                        .data(coverPath)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(titleScrollState)
                        .padding(horizontal = 2.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "上一曲",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    IconButton(onClick = onPlayPause, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "下一曲",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
