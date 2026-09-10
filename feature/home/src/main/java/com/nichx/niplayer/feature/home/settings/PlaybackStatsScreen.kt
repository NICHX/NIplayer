package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.dao.MediaTypeStat
import com.nichx.niplayer.database.dao.StorageStat
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import java.util.Locale

/**
 * 播放统计页（F-20）。
 *
 * 展示累计播放记录数、观看时长、按存储类型/来源分布、近 7/30 天活跃度、Top 10 观看时长。
 * 数据来自 [PlaybackStatsViewModel] 聚合 Room 查询，响应式刷新。
 */
@Composable
fun PlaybackStatsScreen(
    onBack: () -> Unit = {},
    viewModel: PlaybackStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.playback_stats_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 概览卡片
            item {
                StatsOverviewSection(
                    totalPlayCount = state.totalPlayCount,
                    totalWatchTimeMs = state.totalWatchTimeMs,
                    recent7DaysCount = state.recent7DaysCount,
                    recent30DaysWatchMs = state.recent30DaysWatchMs,
                    context = context,
                )
            }

            // 按存储类型分布
            if (state.mediaTypeStats.isNotEmpty()) {
                item {
                    StatsSection(title = stringResource(R.string.playback_stats_by_type), icon = Icons.Filled.Storage) {
                        state.mediaTypeStats.forEach { stat ->
                            StatRow(
                                label = stringResource(MediaType.fromValue(stat.mediaType).storageNameRes),
                                value = stringResource(R.string.playback_stats_count_duration, stat.count, formatDuration(stat.totalPositionMs, context)),
                            )
                        }
                    }
                }
            }

            // 按存储源分布
            if (state.storageStats.isNotEmpty()) {
                item {
                    StatsSection(title = stringResource(R.string.playback_stats_by_storage), icon = Icons.Filled.Storage) {
                        state.storageStats.forEach { stat ->
                            StatRow(
                                label = stat.storageName ?: stringResource(R.string.playback_stats_unknown_storage),
                                value = stringResource(R.string.playback_stats_count_duration, stat.count, formatDuration(stat.totalPositionMs, context)),
                            )
                        }
                    }
                }
            }

            // Top 10 观看时长
            if (state.topWatched.isNotEmpty()) {
                item {
                    StatsSection(title = stringResource(R.string.playback_stats_top10), icon = Icons.Filled.TrendingUp) {
                        state.topWatched.forEachIndexed { index, item ->
                            StatRow(
                                label = "${index + 1}. ${item.videoName}",
                                value = formatDuration(item.videoPosition, context),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewSection(
    totalPlayCount: Int,
    totalWatchTimeMs: Long,
    recent7DaysCount: Int,
    recent30DaysWatchMs: Long,
    context: Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.PlayCircleOutline,
            label = stringResource(R.string.playback_stats_total_plays),
            value = totalPlayCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.playback_stats_total_time),
            value = formatDuration(totalWatchTimeMs, context),
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.TrendingUp,
            label = stringResource(R.string.playback_stats_7d_plays),
            value = recent7DaysCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.playback_stats_30d_time),
            value = formatDuration(recent30DaysWatchMs, context),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val extraColors = com.nichx.niplayer.designsystem.theme.NiExtraColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(extraColors.surfaceLevel2)
            .padding(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    val extraColors = com.nichx.niplayer.designsystem.theme.NiExtraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extraColors.surfaceLevel2)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 格式化时长（ms → 可读字符串）。 */
private fun formatDuration(ms: Long, context: Context): String {
    if (ms <= 0) return context.getString(R.string.playback_stats_zero_minutes)
    val totalMinutes = ms / 60_000
    return when {
        totalMinutes >= 60 -> {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            String.format(Locale.ROOT, context.getString(R.string.playback_stats_hours_mins), hours, mins)
        }
        totalMinutes > 0 -> context.getString(R.string.playback_stats_minutes, totalMinutes)
        else -> context.getString(R.string.playback_stats_seconds, ms / 1000)
    }
}
