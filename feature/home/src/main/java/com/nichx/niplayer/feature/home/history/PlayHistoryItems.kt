package com.nichx.niplayer.feature.home.history

import com.nichx.niplayer.feature.home.mediaTypeLabel

import com.nichx.niplayer.feature.home.formatPlayTime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.designsystem.components.NiSkeletonBox
import com.nichx.niplayer.designsystem.components.NiSkeletonLine
import com.nichx.niplayer.designsystem.components.NiThumbCard
import com.nichx.niplayer.sync.SyncUiState


@Composable
internal fun HistoryItem(
    item: PlayHistoryEntity,
    thumbPath: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val progress = if (item.videoDuration > 0)
        item.videoPosition.toFloat() / item.videoDuration.toFloat() else 0f
    val isAudio = MediaFileTypes.isAudioFile(item.videoName)

    NiThumbCard(
        title = item.videoName,
        durationText = "",
        thumbnailModel = thumbPath,
        progressFraction = progress,
        contentScale = if (isAudio) ContentScale.Fit else ContentScale.Crop,
        onClick = onClick,
        onLongClick = onLongClick,
        horizontal = true,
        subtitleText = formatPlayTime(item.playTime),
        mediaLabel = mediaTypeLabel(item.mediaType),
        squareCover = isAudio,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun HistoryItemSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NiSkeletonBox(width = 64.dp, height = 56.dp, shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            NiSkeletonLine(widthFraction = 0.8f)
            Spacer(Modifier.height(6.dp))
            NiSkeletonLine(widthFraction = 0.5f)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun SyncIndicator(
    state: SyncUiState,
    onSyncClick: () -> Unit,
    onErrorClick: () -> Unit,
    successContentDescription: String,
    idleContentDescription: String,
) {
    val isSyncing = state is SyncUiState.Syncing
    val isError = state is SyncUiState.Done && !state.success
    val isSuccess = !isSyncing && !isError
    // 同步中：同步图标绕中心持续旋转，隐喻"进行中"
    val rotation = rememberInfiniteTransition(label = "sync_rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sync_rotate",
    )
    // 状态配色：就绪/成功=绿色、失败=error、同步中=primary
    val stateColor = when {
        isSuccess -> Color(0xFF4CAF50)
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    // 图标：就绪/成功=cloud_done、失败=cloud_off、同步中=sync（旋转）
    val icon = when {
        isError -> Icons.Outlined.CloudOff
        isSyncing -> Icons.Outlined.Sync
        else -> Icons.Outlined.CloudDone
    }
    // 同步中不可点击；就绪可点击重新同步；失败可点击弹错误窗
    val onClick = when {
        isError -> onErrorClick
        else -> onSyncClick
    }
    IconButton(onClick = onClick, enabled = !isSyncing) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(stateColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isError) idleContentDescription else successContentDescription,
                tint = stateColor,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = if (isSyncing) rotation.value else 0f
                    },
            )
        }
    }
}
