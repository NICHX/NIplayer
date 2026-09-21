package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.nichx.niplayer.database.entity.DownloadState
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import java.util.Locale


@Composable
internal fun DownloadTaskCard(
    display: DownloadTaskDisplay,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onClearRecord: () -> Unit,
    onOpen: () -> Unit,
) {
    val task = display.task
    val state = task.state

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StateBadge(state = state)
        }

        val targetLabel = task.targetStorageName ?: stringResource(R.string.download_manager_target_fallback)
        Text(
            text = stringResource(R.string.download_manager_saved_to, targetLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (state == DownloadState.DOWNLOADING ||
            state == DownloadState.WAITING ||
            state == DownloadState.PAUSED
        ) {
            Spacer(Modifier.height(8.dp))
            val hasKnownSize = task.totalBytes > 0
            NiProgressTrack(fraction = if (hasKnownSize) display.progress / 100f else 0f)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (hasKnownSize)
                        "${formatFileSize(display.downloadedBytes)} / ${formatFileSize(task.totalBytes)}"
                    else
                        "${formatFileSize(display.downloadedBytes)} / ${stringResource(R.string.download_manager_unknown_size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (display.speed.isNotEmpty()) {
                    Text(
                        text = if (display.eta.isNotEmpty()) "${display.speed} · ${display.eta}" else display.speed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (state == DownloadState.WAITING) {
                    Text(
                        text = stringResource(R.string.download_manager_waiting),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        if (state == DownloadState.FAILED && !task.errorMessage.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = task.errorMessage!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = NiExtraColors.current.surfaceLevel3)
        Spacer(Modifier.height(4.dp))
        ActionButtons(
            state = state,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onDelete = onDelete,
            onClearRecord = onClearRecord,
            onOpen = onOpen,
        )
    }
}

@Composable
internal fun StateBadge(state: Int) {
    val (text, color) = when (state) {
        DownloadState.WAITING -> stringResource(R.string.download_state_waiting) to MaterialTheme.colorScheme.outline
        DownloadState.DOWNLOADING -> stringResource(R.string.download_state_downloading) to MaterialTheme.colorScheme.primary
        DownloadState.PAUSED -> stringResource(R.string.download_state_paused) to MaterialTheme.colorScheme.outline
        DownloadState.COMPLETED -> stringResource(R.string.download_state_completed) to MaterialTheme.colorScheme.tertiary
        DownloadState.FAILED -> stringResource(R.string.download_state_failed) to MaterialTheme.colorScheme.error
        DownloadState.CANCELLED -> stringResource(R.string.download_state_cancelled) to MaterialTheme.colorScheme.outline
        else -> stringResource(R.string.download_state_unknown) to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun ActionButtons(
    state: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onClearRecord: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            DownloadState.DOWNLOADING, DownloadState.WAITING -> {
                ActionIconButton(icon = Icons.Filled.Pause, label = stringResource(R.string.download_manager_pause), onClick = onPause)
                ActionIconButton(icon = Icons.Filled.Clear, label = stringResource(R.string.download_manager_cancel), onClick = onCancel)
            }
            DownloadState.PAUSED -> {
                ActionIconButton(icon = Icons.Filled.PlayArrow, label = stringResource(R.string.download_manager_resume), onClick = onResume)
                ActionIconButton(icon = Icons.Filled.Clear, label = stringResource(R.string.download_manager_cancel), onClick = onCancel)
            }
            DownloadState.FAILED -> {
                ActionIconButton(icon = Icons.Filled.Refresh, label = stringResource(R.string.download_manager_retry), onClick = onRetry)
                ActionIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.download_manager_delete), onClick = onDelete)
            }
            DownloadState.COMPLETED -> {
                ActionTextButton(text = stringResource(R.string.download_manager_open), onClick = onOpen)
                ActionTextButton(text = stringResource(R.string.download_manager_clear_record), onClick = onClearRecord)
                ActionIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.download_manager_delete_file), onClick = onDelete)
            }
            DownloadState.CANCELLED -> {
                ActionIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.download_manager_delete), onClick = onDelete)
            }
        }
    }
}

@Composable
internal fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ActionTextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
    // Locale.ROOT：容量展示与区域无关，保持小数点恒为 "."
    return if (unitIndex == 0) "${bytes} B" else String.format(Locale.ROOT, "%.1f %s", size, units[unitIndex])
}

internal sealed class PendingAction {
    abstract val taskId: Long
    abstract val taskName: String

    data class Cancel(override val taskId: Long, override val taskName: String = "") : PendingAction()
    data class Delete(override val taskId: Long, override val taskName: String = "") : PendingAction()
}

