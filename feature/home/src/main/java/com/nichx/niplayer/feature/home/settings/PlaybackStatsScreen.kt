package com.nichx.niplayer.feature.home.settings

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.dao.MediaTypeStat
import com.nichx.niplayer.database.dao.StorageStat
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
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

    Scaffold(
        topBar = {
            NiTopBar(
                title = "播放统计",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 概览卡片
            item {
                StatsOverviewSection(
                    totalPlayCount = state.totalPlayCount,
                    totalWatchTimeMs = state.totalWatchTimeMs,
                    recent7DaysCount = state.recent7DaysCount,
                    recent30DaysWatchMs = state.recent30DaysWatchMs,
                )
            }

            // 按存储类型分布
            if (state.mediaTypeStats.isNotEmpty()) {
                item {
                    StatsSection(title = "按存储类型", icon = Icons.Filled.Storage) {
                        state.mediaTypeStats.forEach { stat ->
                            StatRow(
                                label = MediaType.fromValue(stat.mediaType).storageName,
                                value = "${stat.count} 次 · ${formatDuration(stat.totalPositionMs)}",
                            )
                        }
                    }
                }
            }

            // 按存储源分布
            if (state.storageStats.isNotEmpty()) {
                item {
                    StatsSection(title = "按存储源", icon = Icons.Filled.Storage) {
                        state.storageStats.forEach { stat ->
                            StatRow(
                                label = stat.storageName ?: "未知存储",
                                value = "${stat.count} 次 · ${formatDuration(stat.totalPositionMs)}",
                            )
                        }
                    }
                }
            }

            // Top 10 观看时长
            if (state.topWatched.isNotEmpty()) {
                item {
                    StatsSection(title = "观看时长 Top 10", icon = Icons.Filled.TrendingUp) {
                        state.topWatched.forEachIndexed { index, item ->
                            StatRow(
                                label = "${index + 1}. ${item.videoName}",
                                value = formatDuration(item.videoPosition),
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.PlayCircleOutline,
            label = "总播放数",
            value = totalPlayCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Schedule,
            label = "总观看时长",
            value = formatDuration(totalWatchTimeMs),
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
            label = "近 7 天播放",
            value = recent7DaysCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Schedule,
            label = "近 30 天观看",
            value = formatDuration(recent30DaysWatchMs),
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
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0 分钟"
    val totalMinutes = ms / 60_000
    return when {
        totalMinutes >= 60 -> {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            String.format(Locale.ROOT, "%d小时%d分", hours, mins)
        }
        totalMinutes > 0 -> "${totalMinutes}分钟"
        else -> "${ms / 1000}秒"
    }
}
