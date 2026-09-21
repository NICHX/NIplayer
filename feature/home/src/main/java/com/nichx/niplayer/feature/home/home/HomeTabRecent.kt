package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.feature.home.mediaTypeLabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.nichx.niplayer.designsystem.theme.NiSpacings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.designsystem.components.NiThumbCard


/**
 * 最近播放媒体展示容器：
 * - [columns] <= 1（紧凑宽度）：横向滚动 [LazyRow]，固定宽度卡片。
 * - [columns] > 1（中大屏）：多列网格，卡片填满列宽，末行用等宽占位填齐避免拉伸。
 *
 * 不可达条目统一叠加半透明效果。
 */
@Composable
internal fun RecentMediaGrid(
    mediaItems: List<PlayHistoryEntity>,
    columns: Int,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    contentScale: ContentScale,
    squareCover: Boolean,
    onItemClick: (PlayHistoryEntity) -> Unit,
    edgePadding: Dp = 0.dp,
    cardWidth: Dp = Dp.Unspecified,
) {
    val gap = NiSpacings.responsiveCardGroupGap
    if (columns <= 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(mediaItems, key = { it.id }) { history ->
                RecentThumbItem(
                    history = history,
                    thumbnailUrls = thumbnailUrls,
                    storageReachability = storageReachability,
                    contentScale = contentScale,
                    squareCover = squareCover,
                    fillWidth = false,
                    cardWidth = cardWidth,
                    onClick = { onItemClick(history) },
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            mediaItems.chunked(columns).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { history ->
                        RecentThumbItem(
                            history = history,
                            thumbnailUrls = thumbnailUrls,
                            storageReachability = storageReachability,
                            contentScale = contentScale,
                            squareCover = squareCover,
                            fillWidth = true,
                            onClick = { onItemClick(history) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 用与满行等数量的占位填齐末行，避免末行卡片被拉伸变宽
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 最近播放单条卡片：包裹 [NiThumbCard] 并叠加不可达半透明效果。 */
@Composable
internal fun RecentThumbItem(
    history: PlayHistoryEntity,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    contentScale: ContentScale,
    squareCover: Boolean,
    fillWidth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = Dp.Unspecified,
) {
    val progress = if (history.videoDuration > 0)
        history.videoPosition.toFloat() / history.videoDuration.toFloat() else 0f
    val reachable = isHistoryReachable(history, storageReachability)
    Box(modifier = modifier.graphicsLayer { if (!reachable) alpha = 0.5f }) {
        NiThumbCard(
            title = history.videoName,
            durationText = formatTime(history.videoDuration),
            thumbnailModel = buildThumbnailModel(history.url, history.mediaType, thumbnailUrls),
            progressFraction = progress,
            mediaLabel = mediaTypeLabel(history.mediaType),
            contentScale = contentScale,
            onClick = onClick,
            squareCover = squareCover,
            fillWidth = fillWidth,
            cardWidth = cardWidth,
        )
    }
}
