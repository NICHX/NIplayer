package com.nichx.niplayer.feature.home.settings

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.DownloadDirInfo
import com.nichx.niplayer.designsystem.components.NiGlassSwitch
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.glassOnSurface
import com.nichx.niplayer.designsystem.components.glassOnSurfaceMuted
import com.nichx.niplayer.designsystem.theme.NiExtraColors


@Composable
internal fun DownloadSettingsCard(
    dirInfo: DownloadDirInfo,
    onClick: () -> Unit,
) {
    val hasDir = dirInfo.path.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.download_settings_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (hasDir) dirInfo.path else stringResource(R.string.download_manager_no_dir),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.download_settings_title),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 下载设置弹窗（液态玻璃材质）：下载目录管理 + 「同时下载歌词」开关。
 * 点击卡片进入，目录与歌词开关集中在此统一管理。
 * 经 [NiInfoDialog] → [NiGlassOverlay] → [NiGlassDialog] 渲染，backdrop 真模糊，
 * 无 backdrop 或 API < 33 时降级为不透明磨砂卡片（niFrostSurfaceColor）。
 */
@Composable
internal fun DownloadSettingsDialog(
    dirInfo: DownloadDirInfo,
    downloadLrcWithAudio: Boolean,
    onLrcEnabledChange: (Boolean) -> Unit,
    onChooseDirectory: () -> Unit,
    onClearDirectory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasDir = dirInfo.path.isNotBlank()
    val onSurface = MaterialTheme.colorScheme.onSurface
    NiInfoDialog(
        title = stringResource(R.string.download_settings_title),
        onDismiss = onDismiss,
        content = {
            // 下载目录行（与开关行同对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (hasDir) Icons.Filled.Folder else Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = if (hasDir) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_manager_has_dir),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = glassOnSurface(),
                    )
                    if (hasDir) {
                        Text(
                            text = dirInfo.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = glassOnSurfaceMuted(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.download_manager_no_dir_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (hasDir) {
                    TextButton(onClick = onChooseDirectory) {
                        Text(stringResource(R.string.download_manager_reset), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onClearDirectory) {
                        Text(stringResource(R.string.download_manager_clear), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    TextButton(onClick = onChooseDirectory) {
                        Text(stringResource(R.string.download_manager_set), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            HorizontalDivider(
                color = onSurface.copy(alpha = 0.08f),
            )
            // 同时下载歌词开关行（整行点击仅委托开关，移除涟漪，与 SettingSwitchRow 一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLrcEnabledChange(!downloadLrcWithAudio) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.download_lrc_with_audio),
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassOnSurface(),
                    )
                    Text(
                        text = stringResource(R.string.download_lrc_with_audio_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = glassOnSurfaceMuted(),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                NiGlassSwitch(
                    checked = downloadLrcWithAudio,
                    onCheckedChange = onLrcEnabledChange,
                )
            }
        },
    )
}

@Composable
internal fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
