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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.designsystem.components.NiInfoDialog
import com.nichx.niplayer.designsystem.components.NiProgressTrack
import com.nichx.niplayer.designsystem.theme.NiExtraColors

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
        title = "检查更新",
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
                text = "正在检查最新版本…",
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
        title = "发现新版本",
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text("稍后") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::startDownload) { Text("立即更新") }
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
                    label = "当前 ${viewModel.currentVersion.ifBlank { "未知" }}",
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
                    text = "安装包大小 ${state.sizeText}",
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
                        text = "更新内容",
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
                    text = "发现新版本，建议更新以获得更好的体验。",
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
        title = "已是最新版本",
        onDismiss = viewModel::dismiss,
        actions = {
            Button(onClick = viewModel::dismiss) { Text("确定") }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.CheckCircle,
                tint = NiExtraColors.current.success,
            )
            Text(
                text = "当前版本 v${state.currentVersion} 已是最新版本",
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
        title = "检查更新失败",
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text("关闭") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::openReleasesPage) { Text("去浏览器查看") }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.ErrorOutline,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "无法连接更新服务，请检查网络后重试",
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
        title = "正在下载更新",
        // 关闭弹窗（点击外部/返回）不终止下载，转入后台继续
        onDismiss = viewModel::downloadInBackground,
        actions = {
            TextButton(onClick = viewModel::cancelDownload) { Text("取消") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::downloadInBackground) { Text("后台下载") }
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
                    text = "可收起弹窗，在通知栏查看下载进度",
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
        title = "更新已就绪",
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text("稍后") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::install) { Text("立即安装") }
        },
    ) {
        UpdateDialogBody {
            UpdateIconHeader(
                icon = Icons.Filled.DownloadDone,
                tint = NiExtraColors.current.success,
            )
            Text(
                text = "新版本 v${state.version} 已下载完成",
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
                text = "安装后将替换当前版本，已下载内容不受影响",
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
        title = "下载失败",
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text("关闭") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = viewModel::openReleasesPage) { Text("去浏览器下载") }
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
                text = "可前往 GitHub Releases 页面手动下载安装包",
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
        title = "需要安装权限",
        onDismiss = viewModel::dismiss,
        actions = {
            TextButton(onClick = viewModel::dismiss) { Text("关闭") }
            Spacer(Modifier.width(4.dp))
            // 用户授权后返回时直接重新安装已下载的 APK，无需重新下载
            Button(onClick = viewModel::install) { Text("重新安装") }
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
                text = "授权完成后返回本页，点击「重新安装」即可继续",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
