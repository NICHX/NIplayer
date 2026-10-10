package com.nichx.niplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.feature.player.theme.ChromeTint
import com.nichx.niplayer.feature.player.theme.ControlSpec
import com.nichx.niplayer.feature.player.theme.ProgressVariant

/** 底部细进度条：3dp 高，按播放进度填充主色，不参与交互，用于沉浸模式下指示进度。 */
@Composable
internal fun ThinProgressBar(
    positionMs: State<Long>,
    durationMs: Long,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
) {
    val progress = if (durationMs > 0) {
        (positionMs.value.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .height(3.dp)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(accentColor),
        )
    }
}

/**
 * 播放控件列：进度条 + 一行式播放控制。
 *
 * 竖屏（compact=false）沿用原尺寸与间距；横屏（compact=true）收紧
 * 按钮尺寸与间距，为上方歌词区域腾出空间。
 */
@Composable
internal fun ControlColumn(
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playMode: Int,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    compact: Boolean = false,
    spec: ControlSpec,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressArea(
            variant = spec.progress,
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

        PlaybackControls(
            isPlaying = isPlaying,
            buffering = false,
            hasPrev = hasPrev,
            hasNext = hasNext,
            onTogglePlay = onTogglePlay,
            onPrevious = onPrevious,
            onNext = onNext,
            playMode = playMode,
            modeIcon = modeIcon,
            modeLabel = modeLabel,
            onCyclePlayMode = onCyclePlayMode,
            onShowPlaylist = onShowPlaylist,
            compact = compact,
            spec = spec,
        )
    }
}

/**
 * 进度条区：按主题选实现。
 *
 * Apple 的细滑块与其它主题的 Material `Slider` 是两套实现，属于「选结构」而非「取值」，
 * 所以用穷尽 `when` 表达 —— 新增变体时编译器会点名，而不是静默套用某一种。
 */
@Composable
internal fun ProgressArea(
    variant: ProgressVariant,
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    when (variant) {
        ProgressVariant.STANDARD -> ProgressSection(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )
        ProgressVariant.APPLE -> AppleProgressSection(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )
    }
}

@Composable
internal fun ProgressSection(
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val duration = durationMs.coerceAtLeast(1L)
    // 只在这里读一次：读取 State 才能让"时间文字 + 滑块"这一小块单独重组
    val currentPositionMs = positionMs.value

    // 拖动进度条时以本地值驱动滑块（跟手），松手才提交 seek
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val sliderPos = if (dragging) dragValue else currentPositionMs.toFloat().coerceIn(0f, duration.toFloat())
    // 拖动中左侧时间显示目标位置（预览），松手后回到实际播放位置
    val displayMs = if (dragging) dragValue.toLong() else currentPositionMs

    Slider(
        value = sliderPos,
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            if (dragging) {
                onSeek(dragValue.toLong())
                dragging = false
            }
        },
        valueRange = 0f..duration.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = primary,
            activeTrackColor = primary.copy(alpha = 0.8f),
            inactiveTrackColor = onSurface.copy(alpha = 0.2f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDurationShort(displayMs),
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = formatDurationShort(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
internal fun PlaybackControls(
    isPlaying: Boolean,
    buffering: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playMode: Int,
    modeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modeLabel: String,
    onCyclePlayMode: () -> Unit,
    onShowPlaylist: () -> Unit,
    compact: Boolean = false,
    spec: ControlSpec,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    // 尺寸：主题给「常规 / 紧凑」两档，紧凑度选其中一档（两个维度互不干扰）
    val sideSize = spec.sideButton.pick(compact)
    val sideIconSize = spec.sideIcon.pick(compact)
    val mainSize = spec.mainButton.pick(compact)
    val mainIconSize = spec.mainIcon.pick(compact)
    val gap = spec.gap.pick(compact)
    val mainElevation = spec.mainElevation.pick(compact)
    // 配色族：Apple 走扁平纯白（无圆底、无投影），其余用主题色实心主键
    val containerColor = when (spec.tint) {
        ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.08f)
        ChromeTint.LIGHT_ON_DARK -> Color.Transparent
    }
    val iconColor = when (spec.tint) {
        ChromeTint.MATERIAL -> onSurface
        ChromeTint.LIGHT_ON_DARK -> Color.White
    }
    val mainContainerColor = when (spec.tint) {
        ChromeTint.MATERIAL -> primary
        ChromeTint.LIGHT_ON_DARK -> Color.Transparent
    }
    val mainIconColor = when (spec.tint) {
        ChromeTint.MATERIAL -> MaterialTheme.colorScheme.onPrimary
        ChromeTint.LIGHT_ON_DARK -> Color.White
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            IconButton(onClick = onCyclePlayMode) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = modeLabel,
                        tint = iconColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }
        }

        // 中间：上一首 / 播放暂停 / 下一首（严格居中）
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = hasPrev,
            ) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.player_previous),
                        tint = iconColor.copy(alpha = if (hasPrev) 0.8f else 0.2f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }

            Spacer(modifier = Modifier.width(gap))

            Box(
                modifier = Modifier
                    .size(mainSize)
                    .shadow(elevation = mainElevation, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(mainContainerColor)
                    .clickable(enabled = !buffering) { onTogglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                    tint = mainIconColor,
                    modifier = Modifier.size(mainIconSize),
                )
            }

            Spacer(modifier = Modifier.width(gap))

            IconButton(
                onClick = onNext,
                enabled = hasNext,
            ) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
                        tint = iconColor.copy(alpha = if (hasNext) 0.8f else 0.2f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            IconButton(
                onClick = onShowPlaylist,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Box(
                    modifier = Modifier
                        .size(sideSize)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.player_playlist),
                        tint = iconColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(sideIconSize),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaybackErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = stringResource(R.string.player_playback_error),
            tint = errorColor,
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.player_error_title),
            style = MaterialTheme.typography.headlineSmall,
            color = onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { onRetry() }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.player_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
