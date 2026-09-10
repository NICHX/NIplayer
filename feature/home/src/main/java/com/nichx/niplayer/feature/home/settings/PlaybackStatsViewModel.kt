package com.nichx.niplayer.feature.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaTypeStat
import com.nichx.niplayer.database.dao.PlayHistoryDao
import com.nichx.niplayer.database.dao.StorageStat
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * 播放统计页 ViewModel（F-20）。
 *
 * 聚合 [PlayHistoryDao] 的统计查询为单一 [PlaybackStatsUiState]，
 * UI 通过 [state] 一次性订阅所有统计指标。
 *
 * 使用嵌套 [combine] 组合 7 个 Flow（kotlinx-coroutines 的 combine 最多支持 5 个带类型的 Flow）。
 */
@HiltViewModel
class PlaybackStatsViewModel @Inject constructor(
    playHistoryDao: PlayHistoryDao,
) : ViewModel() {

    private val sevenDaysAgo: Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis

    private val thirtyDaysAgo: Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -30)
    }.timeInMillis

    private val summaryFlow = combine(
        playHistoryDao.getTotalPlayCountFlow(),
        playHistoryDao.getTotalWatchTimeFlow(),
        playHistoryDao.getMediaTypeStatsFlow(),
    ) { count, watchTime, mediaStats ->
        Triple(count, watchTime, mediaStats)
    }

    private val storageFlow = combine(
        playHistoryDao.getStorageStatsFlow(),
        playHistoryDao.getRecentPlayCountFlow(sevenDaysAgo),
    ) { storageStats, recent7 ->
        Pair(storageStats, recent7)
    }

    private val recentFlow = combine(
        playHistoryDao.getRecentWatchTimeFlow(thirtyDaysAgo),
        playHistoryDao.getTopWatchedFlow(10),
    ) { recent30Watch, topWatched ->
        Pair(recent30Watch, topWatched)
    }

    val state: StateFlow<PlaybackStatsUiState> = combine(
        summaryFlow,
        storageFlow,
        recentFlow,
    ) { (count, watchTime, mediaStats), (storageStats, recent7), (recent30Watch, topWatched) ->
        PlaybackStatsUiState(
            totalPlayCount = count,
            totalWatchTimeMs = watchTime,
            mediaTypeStats = mediaStats,
            storageStats = storageStats,
            recent7DaysCount = recent7,
            recent30DaysWatchMs = recent30Watch,
            topWatched = topWatched,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaybackStatsUiState(),
    )
}

/** 播放统计 UI 状态。 */
data class PlaybackStatsUiState(
    val totalPlayCount: Int = 0,
    val totalWatchTimeMs: Long = 0L,
    val mediaTypeStats: List<MediaTypeStat> = emptyList(),
    val storageStats: List<StorageStat> = emptyList(),
    val recent7DaysCount: Int = 0,
    val recent30DaysWatchMs: Long = 0L,
    val topWatched: List<PlayHistoryEntity> = emptyList(),
)
