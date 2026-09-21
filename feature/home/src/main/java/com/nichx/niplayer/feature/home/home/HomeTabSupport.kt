package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nichx.niplayer.common.media.MediaFileTypes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.components.PlaceholderText
import java.util.Locale


internal fun buildThumbnailModel(
    url: String,
    mediaType: MediaType,
    thumbnailUrls: Map<String, String> = emptyMap(),
): Any? {
    val cachedThumb = thumbnailUrls[url]
    if (cachedThumb != null) return cachedThumb

    val isLocal = mediaType == MediaType.LOCAL_STORAGE || mediaType == MediaType.EXTERNAL_STORAGE
    if (isLocal && url.isNotEmpty()) {
        val fileName = url.substringAfterLast('/')
        if (MediaFileTypes.isAudioFile(fileName)) return null
        return if (url.startsWith("/")) "file://$url" else url
    }
    return null
}

@Composable
internal fun buildHeroThumbnailModel(
    url: String,
    mediaType: MediaType,
    fileName: String,
    thumbnailUrls: Map<String, String> = emptyMap(),
): Any {
    buildThumbnailModel(url, mediaType, thumbnailUrls)?.let { return it }
    // 英雄卡来自播放历史（已播放），此时仍无缩略图说明生成失败/太短，
    // 标签标为"无缩略图"；未播放过的普通条目不传 label
    val firstChar = fileName.firstOrNull { !it.isWhitespace() }?.toString() ?: "▶"
    return PlaceholderText(firstChar, label = stringResource(R.string.thumbnail_none))
}

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** 不可达角标：右上角显示"离线"标识。 */
@Composable
internal fun UnreachableBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.home_offline),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 判断播放历史条目是否可达（库存在 + 远程连接正常）。 */
internal fun isHistoryReachable(
    history: PlayHistoryEntity,
    storageReachability: Map<Int, Boolean>,
): Boolean {
    val sid = history.storageId ?: return true // 本地播放，无 storageId，视为可达
    return storageReachability[sid] != false // 未验证（null）视为可达，明确 false 才不可达
}
