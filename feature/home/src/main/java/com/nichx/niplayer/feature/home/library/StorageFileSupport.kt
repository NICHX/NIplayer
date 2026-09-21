package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.designsystem.components.niFrostSurfaceColor
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.common.media.MediaFileTypes.isImageFile
import com.nichx.niplayer.storage.StorageFile
import java.util.Locale


// 主 FAB 底部偏移，与媒体库页"新增媒体库"按钮位置保持一致
// （该值已包含对应用底部导航栏 NiBottomBar 的避让）
internal val FabBottomOffset = 104.dp

internal fun fileIcon(file: StorageFile, isVideo: Boolean, isAudio: Boolean): ImageVector = when {
    file.isDirectory -> Icons.Rounded.Folder
    isVideo -> Icons.Rounded.Movie
    isAudio -> Icons.Rounded.MusicNote
    MediaFileTypes.isImageFile(file.name) -> Icons.Rounded.Image
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    // Locale.ROOT：容量展示与区域无关，保持小数点恒为 "."（与 feature:player 一致）
    return if (unitIndex == 0) "${bytes} B" else String.format(Locale.ROOT, "%.1f %s", size, units[unitIndex])
}

/**
 * 传输管理入口图标右上角的数量角标。
 *
 * 作为 [IconButton] 的**外层**叠加层（而非其内部子项），避免被 M3 IconButton 的
 * 圆形 Surface 裁剪；用自定义圆角胶囊完整显示数量。
 */
@Composable
internal fun BoxScope.TransferCountBadge(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onError,
        fontSize = 9.sp,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 1.dp, y = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

internal fun formatDate(timestamp: Long, context: Context): String {
    if (timestamp <= 0) return context.getString(R.string.storage_file_unknown)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

internal fun fileTypeLabel(file: StorageFile, context: Context): String {
    if (file.isDirectory) return context.getString(R.string.storage_file_folder)
    val name = file.name
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return context.getString(R.string.storage_file_file)
    val ext = name.substring(dot + 1).uppercase()
    return when {
        MediaFileTypes.isVideoFile(name) -> context.getString(R.string.storage_file_type_video_ext, ext)
        MediaFileTypes.isAudioFile(name) -> context.getString(R.string.storage_file_type_audio_ext, ext)
        MediaFileTypes.isImageFile(name) -> context.getString(R.string.storage_file_type_image_ext, ext)
        else -> context.getString(R.string.storage_file_type_file_ext, ext)
    }
}

// ---- 文件管理对话框 ----

/**
 * 批量文件操作进度浮层（移动/复制/删除）。
 *
 * 置于页面底部居中，展示操作类型 + 已完成/总数 + 确定性进度条 + 当前文件名。
 * 不拦截点击（仅展示），仅在进行中的批量操作期间显示。
 */
@Composable
internal fun FileOpProgressOverlay(progress: FileOpProgress) {
    val label = when (progress.type) {
        FileOpType.MOVE -> stringResource(R.string.storage_file_op_moving)
        FileOpType.COPY -> stringResource(R.string.storage_file_op_copying)
        FileOpType.DELETE -> stringResource(R.string.storage_file_op_deleting)
    }
    val fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = FabBottomOffset + 24.dp),
        shape = RoundedCornerShape(20.dp),
        color = niFrostSurfaceColor(),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.storage_file_op_progress, progress.done, progress.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            if (progress.currentName.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = progress.currentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

internal fun formatUploadSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1000 * 1000 -> String.format(Locale.ROOT, "%.1f MB/s", bytesPerSec / (1000.0 * 1000.0))
    bytesPerSec >= 1000 -> "${bytesPerSec / 1000} KB/s"
    else -> "$bytesPerSec B/s"
}

