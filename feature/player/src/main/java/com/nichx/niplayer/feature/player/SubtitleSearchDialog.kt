package com.nichx.niplayer.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiTextFieldDefaults
import com.nichx.niplayer.network.subtitle.AssrtSubDetail

/**
 * 字幕搜索 BottomSheet。
 *
 * 替代旧仓库 `BindSubtitleSourceFragment`（绑定字幕流程）+ `ShooterSubtitleActivity`
 *（独立下载页），v2 整合为播放页内 BottomSheet。
 *
 * 流程：
 * 1. 进入时默认填入视频标题，自动触发搜索
 * 2. 用户可修改关键词重新搜索
 * 3. 点击搜索结果项 → 加载详情获取下载链接 → 下载字幕文件 → 回调应用到播放器
 * 4. 若未配置 ASSRT token，弹出 token 设置对话框
 *
 * @param videoTitle 视频标题（默认搜索关键词）
 * @param onSubtitleDownloaded 字幕下载完成回调，参数为字幕文件 URI + MIME 类型
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSearchDialog(
    videoTitle: String,
    onSubtitleDownloaded: (android.net.Uri, String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SubtitleSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchText by rememberSaveable { mutableStateOf(videoTitle) }

    LaunchedEffect(Unit) {
        viewModel.onSubtitleDownloaded = { uri, mime -> onSubtitleDownloaded(uri, mime) }
    }

    LaunchedEffect(events) {
        when (val event = events) {
            is SubtitleSearchEvent.NeedToken -> viewModel.showTokenDialog()
            else -> Unit
        }
        viewModel.consumeEvent()
    }

    LaunchedEffect(Unit) {
        if (searchText.isNotBlank() && SubtitleSettings.assrtToken.isNotBlank()) {
            viewModel.search(searchText)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ---- 搜索栏 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "搜索字幕",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.showTokenDialog() }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置 ASSRT Token",
                        tint = PlayerDialogColors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            NiTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "输入搜索关键词",
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.search(searchText) },
                        enabled = !uiState.isSearching && searchText.isNotBlank(),
                    ) {
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    }
                },
                colors = subtitleTextFieldColors(),
            )

            Spacer(modifier = Modifier.size(12.dp))

            // ---- 错误提示 ----
            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // ---- 下载进度 ----
            if (uiState.isLoadingDetail || uiState.isDownloading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (uiState.isDownloading) "正在下载字幕..." else "正在加载详情...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlayerDialogColors.textSecondary,
                    )
                }
            }

            // ---- 搜索结果列表 ----
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                items(
                    items = uiState.results,
                    key = { it.id },
                ) { sub ->
                    SubtitleResultItem(
                        sub = sub,
                        enabled = !uiState.isDownloading && !uiState.isLoadingDetail,
                        onClick = { viewModel.loadDetail(sub) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = PlayerDialogColors.divider,
                    )
                }

                if (uiState.results.isEmpty() && !uiState.isSearching && uiState.error == null) {
                    item {
                        Text(
                            text = if (SubtitleSettings.assrtToken.isBlank()) {
                                "请先设置 ASSRT Token"
                            } else {
                                "输入关键词搜索字幕"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlayerDialogColors.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    // ---- Token 设置对话框 ----
    if (uiState.showTokenDialog) {
        TokenDialog(
            currentToken = SubtitleSettings.assrtToken,
            onSave = { viewModel.saveToken(it) },
            onDismiss = { viewModel.dismissTokenDialog() },
        )
    }
}

/** 播放器暗色弹层内的输入框配色：透明容器 + 品牌色聚焦态 */
@Composable
private fun subtitleTextFieldColors(): TextFieldColors {
    val primary = MaterialTheme.colorScheme.primary
    return NiTextFieldDefaults.colors(
        focusedTextColor = PlayerDialogColors.textPrimary,
        unfocusedTextColor = PlayerDialogColors.textPrimary,
        focusedBorderColor = primary,
        unfocusedBorderColor = PlayerDialogColors.divider,
        cursorColor = primary,
        focusedLabelColor = primary,
        unfocusedLabelColor = PlayerDialogColors.textSecondary,
        focusedPlaceholderColor = PlayerDialogColors.textSecondary,
        unfocusedPlaceholderColor = PlayerDialogColors.textSecondary,
        focusedLeadingIconColor = primary,
        unfocusedLeadingIconColor = PlayerDialogColors.textSecondary,
        focusedTrailingIconColor = primary,
        unfocusedTrailingIconColor = PlayerDialogColors.textSecondary,
    )
}

@Composable
private fun SubtitleResultItem(
    sub: AssrtSubDetail,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sub.native_name ?: sub.videoname ?: "未知字幕",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sub.subtype?.let { type ->
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                sub.lang?.desc?.let { lang ->
                    Text(
                        text = lang,
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayerDialogColors.textSecondary,
                    )
                }
                sub.upload_time?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayerDialogColors.textSecondary,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = "下载",
            tint = PlayerDialogColors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TokenDialog(
    currentToken: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var token by rememberSaveable { mutableStateOf(currentToken) }
    val primary = MaterialTheme.colorScheme.primary

    PlayerDialog(onDismiss = onDismiss, maxWidth = 340, scrollable = false) {
        PlayerDialogTitle(text = "设置 ASSRT Token")
        PlayerDialogDivider()
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "在 assrt.net 注册获取 API Token，用于字幕搜索。",
                color = PlayerDialogColors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.size(8.dp))
            NiTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "输入 ASSRT Token",
                colors = subtitleTextFieldColors(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消", color = PlayerDialogColors.textSecondary)
            }
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = { onSave(token) }) {
                Text("保存", color = primary)
            }
        }
    }
}
