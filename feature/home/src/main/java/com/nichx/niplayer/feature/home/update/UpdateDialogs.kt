package com.nichx.niplayer.feature.home.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SecurityUpdate
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.home.R

/**
 * 更新流程对话框宿主。
 *
 * 订阅 [UpdateViewModel.uiState]，按状态渲染对应对话框：
 * 检查中 / 发现新版本（含更新日志）/ 已是最新 / 检查失败 / 下载进度 / 下载完成待安装 /
 * 下载失败（浏览器兜底）/ 缺少安装权限。
 *
 * 用于两处：MainActivity（启动自动检查）与设置页（手动检查）。
 *
 * 样式约定：全部基于 [NiInfoDialog] 磨砂玻璃卡片；内容区顶部统一放置
 * 圆形图标头部（[UpdateIconHeader]），按状态区分主色/成功色/错误色/警告色，
 * 底部操作区主操作使用 [Button]、次要操作用 [TextButton]。
 */
@Composable
fun UpdateDialogHost(
    viewModel: UpdateViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val s = state
    when (s) {
        UpdateViewModel.UpdateUiState.Idle -> Unit
        UpdateViewModel.UpdateUiState.Checking -> CheckingDialog()
        UpdateViewModel.UpdateUiState.DownloadingInBackground -> Unit
        is UpdateViewModel.UpdateUiState.UpdateAvailable -> UpdateAvailableDialog(s, viewModel)
        is UpdateViewModel.UpdateUiState.AlreadyLatest -> AlreadyLatestDialog(s, viewModel)
        is UpdateViewModel.UpdateUiState.CheckFailed -> CheckFailedDialog(s, viewModel)
        is UpdateViewModel.UpdateUiState.Downloading -> DownloadingDialog(s, viewModel)
        is UpdateViewModel.UpdateUiState.DownloadReady -> DownloadReadyDialog(s, viewModel)
        is UpdateViewModel.UpdateUiState.DownloadFailed -> DownloadFailedDialog(s, viewModel)
        is UpdateViewModel.UpdateUiState.InstallBlocked -> InstallBlockedDialog(s, viewModel)
    }
}

/** 圆形彩色图标头部：64dp 圆角底 + 32dp 图标，状态色 tint。 */
@Composable
private fun UpdateIconHeader(
    icon: ImageVector,
    tint: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.12f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** 弹窗内容区通用的居中纵向容器。 */
@Composable
private fun UpdateDialogBody(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun CheckingDialog() {
    NiInfoDialog(
        title = stringResource(R.string.update_check_title),
        // 检查耗时很短且结果会覆盖状态，检查中不允许关闭
        onDismiss = {},
    ) {
        UpdateDialogBody {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.update_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    state: UpdateViewModel.UpdateUiState.UpdateAvailable,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_found_title),
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_later)) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::startDownload) { Text(stringResource(R.string.update_now)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.SystemUpdate,
                tint = MaterialTheme.colorScheme.primary,
            )
            // 版本对比：当前版本 → 新版本
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                VersionChip(
                    label = stringResource(
                        R.string.update_current_version,
                        viewModel.currentVersion.ifBlank { stringResource(R.string.update_version_unknown) },
                    ),
                    highlight = false,
                )
                Text(
                    text = "→",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                VersionChip(
                    label = "v${state.latestVersion}",
                    highlight = true,
                )
            }
            if (state.sizeText.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.update_size, state.sizeText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.notes.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_notes_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = state.notes,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.update_suggest),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 版本徽章：highlight=true 用 primaryContainer 底 + 主色字，突出新版本。 */
@Composable
private fun VersionChip(
    label: String,
    highlight: Boolean,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
        color = if (highlight) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlight) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun AlreadyLatestDialog(
    state: UpdateViewModel.UpdateUiState.AlreadyLatest,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_latest_title),
        onDismiss = viewModel::dismiss,
        actions = {
            Button(onClick = viewModel::dismiss) { Text(stringResource(R.string.confirm)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.CheckCircle,
                tint = NiExtraColors.current.success,
            )
            Text(
                text = stringResource(R.string.update_latest_body, state.currentVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CheckFailedDialog(
    state: UpdateViewModel.UpdateUiState.CheckFailed,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_check_failed_title),
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.close)) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::openReleasesPage) { Text(stringResource(R.string.update_open_browser)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.update_check_failed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (state.message.isNotEmpty()) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DownloadingDialog(
    state: UpdateViewModel.UpdateUiState.Downloading,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_downloading_title),
        // 关闭弹窗（点击外部/返回）不终止下载，转入后台继续
        onDismiss = viewModel::downloadInBackground,
        actions = {
            TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::downloadInBackground) { Text(stringResource(R.string.update_background_download)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.SecurityUpdate,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${state.progress}%",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NiProgressTrack(fraction = state.progress / 100f)
                Text(
                    text = stringResource(R.string.update_downloading_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DownloadReadyDialog(
    state: UpdateViewModel.UpdateUiState.DownloadReady,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_ready_title),
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_later)) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::install) { Text(stringResource(R.string.update_install_now)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.DownloadDone,
                tint = NiExtraColors.current.success,
            )
            Text(
                text = stringResource(R.string.update_ready_body, state.version),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.update_ready_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DownloadFailedDialog(
    state: UpdateViewModel.UpdateUiState.DownloadFailed,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_download_failed_title),
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.close)) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::openReleasesPage) { Text(stringResource(R.string.update_open_browser_download)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.update_download_failed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InstallBlockedDialog(
    state: UpdateViewModel.UpdateUiState.InstallBlocked,
    viewModel: UpdateViewModel,
) {
    NiInfoDialog(
        title = stringResource(R.string.update_permission_title),
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.close)) }
            Spacer(Modifier.width(4.dp))
            // 用户授权后返回时直接重新安装已下载的 APK，无需重新下载
            Button(onClick = viewModel::install) { Text(stringResource(R.string.update_reinstall)) }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.Warning,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.update_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
