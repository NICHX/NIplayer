package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.theme.NiMotion


@Composable
internal fun ThumbnailProgressBar(progress: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.storage_file_generating_thumbnails),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "识别方向中…"状态标签：居中叠在文件占位图标上，提示正在为进入播放前读取视频宽高比。
 * 仅当点击后又未立即进入播放（维持一小段时间）时让用户感知到等待，背景用主题色缓慢脉冲。
 */
@Composable
internal fun BoxScope.PreparingBadge() {
    val transition = rememberInfiniteTransition(label = "preparing")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(NiMotion.DURATION_PAGE),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "preparingAlpha",
    )
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * alpha))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.storage_file_preparing_playback),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun BreadcrumbBar(
    path: String,
    onJumpToDepth: (Int) -> Unit,
    onGoToRoot: () -> Unit,
) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    // 面包屑作为列表首个 item 随页面滚动，保持全透明观感（透明底 + 不透明胶囊）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 根目录入口：点击直接回到存储根，深层目录无需逐级返回
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onGoToRoot)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "▸",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            val bgColor = if (isLast)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .then(
                        // 任务8 修复：directoryStack[0] = ROOT（path=""，不出现在 segments 中），
                        // segments[0] 是第一级子目录，对应 directoryStack[1]。
                        // 原实现传 index 会导致点击任意段都跳到比预期浅一级
                        // （点击 segments[0] 跳到 directoryStack[0]=ROOT 根目录）。
                        // 修正为 index + 1 让 segments 索引对齐 directoryStack 索引。
                        if (!isLast) Modifier.clickable { onJumpToDepth(index + 1) }
                        else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (index == 0) Icons.Rounded.FolderOpen
                    else Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isLast) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isLast) {
                Text(
                    text = "▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.storage_file_connecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        NiEmptyState(
            icon = Icons.Rounded.Refresh,
            text = stringResource(R.string.storage_file_load_failed),
            hint = message,
            actionText = stringResource(R.string.retry),
            onAction = onRetry,
        )
    }
}

@Composable
internal fun EmptyDirState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        NiEmptyState(
            icon = Icons.Rounded.FolderOpen,
            text = stringResource(R.string.storage_file_empty_dir),
            hint = stringResource(R.string.storage_file_empty_dir_hint),
        )
    }
}
