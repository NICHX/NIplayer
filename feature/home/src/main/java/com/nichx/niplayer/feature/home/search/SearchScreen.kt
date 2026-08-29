package com.nichx.niplayer.feature.home.search

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.entity.PlayHistoryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiSectionHeader
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiTextField
import com.nichx.niplayer.designsystem.components.NiThumbCard
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiIconStyleSpec
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.feature.home.MediaFileTypes
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessUiItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 首页搜索页。
 *
 * 顶部搜索框 + 分组结果（播放历史 / 快速访问），全部基于本地 Room 表，
 * 输入即搜、无网络请求。定位为"快速续播 / 直达书签"。点击行为：
 * - 历史 → 续播
 * - 快速访问文件夹 → 文件浏览；文件 → 播放
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToPlayVideo: () -> Unit,
    onNavigateToStorageFile: (Int, String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val messageController = LocalAppMessageController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.NavigateToPlayer -> onNavigateToPlayVideo()
                is SearchEvent.NavigateToStorageFile ->
                    onNavigateToStorageFile(event.libraryId, event.relativePath)

                is SearchEvent.ShowError -> messageController.post(NiMessage.error(event.message))
            }
        }
    }

    LaunchedEffect(Unit) {
        // 等转场动画结束后再拉起软键盘，避免键盘抖动
        delay(250)
        focusRequester.requestFocus()
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.search_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            NiTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
                placeholder = stringResource(R.string.search_placeholder),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            when {
                uiState.query.isBlank() -> SearchHintState(Modifier.weight(1f))

                uiState.searching -> SearchLoadingState(Modifier.weight(1f))

                uiState.histories.isEmpty() &&
                    uiState.quickAccessItems.isEmpty() -> SearchEmptyState(Modifier.weight(1f))

                else -> SearchResultList(
                    state = uiState,
                    onResumePlay = viewModel::resumePlay,
                    onOpenQuickAccess = viewModel::openQuickAccess,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(padding.calculateBottomPadding()))
    }
}

/** 初始提示态：未输入关键词。 */
@Composable
private fun SearchHintState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        NiEmptyState(
            icon = Icons.Filled.Search,
            text = stringResource(R.string.search_hint_title),
            hint = stringResource(R.string.search_hint_body),
        )
    }
}

/** 搜索中态：本地 Room 查询为毫秒级，仅短暂展示。 */
@Composable
private fun SearchLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** 无结果态。 */
@Composable
private fun SearchEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        NiEmptyState(
            icon = Icons.Filled.Search,
            text = stringResource(R.string.search_empty_title),
            hint = stringResource(R.string.search_empty_hint),
        )
    }
}

/** 分组结果列表：播放历史 / 快速访问。 */
@Composable
private fun SearchResultList(
    state: SearchUiState,
    onResumePlay: (PlayHistoryEntity) -> Unit,
    onOpenQuickAccess: (QuickAccessUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (state.histories.isNotEmpty()) {
            item(key = "header_history") {
                NiSectionHeader(
                    title = stringResource(R.string.search_section_history),
                    count = state.histories.size,
                    onClick = null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.histories, key = { "history_${it.id}" }) { history ->
                HistoryResultRow(
                    item = history,
                    thumbPath = state.historyThumbs[history.url],
                    onClick = { onResumePlay(history) },
                )
            }
        }

        if (state.quickAccessItems.isNotEmpty()) {
            item(key = "header_quick_access") {
                NiSectionHeader(
                    title = stringResource(R.string.search_section_quick_access),
                    count = state.quickAccessItems.size,
                    onClick = null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(state.quickAccessItems, key = { "qa_${it.entity.id}" }) { qa ->
                QuickAccessResultRow(
                    item = qa,
                    thumbPath = state.qaThumbs["${qa.entity.libraryId}/${qa.entity.storagePath}"],
                    onClick = { onOpenQuickAccess(qa) },
                )
            }
        }
    }
}

/** 播放历史结果卡：缩略图 + 名称 + 时间 + 媒体标签 + 续播进度，点击续播。 */
@Composable
private fun HistoryResultRow(
    item: PlayHistoryEntity,
    thumbPath: String?,
    onClick: () -> Unit,
) {
    val isAudio = MediaFileTypes.isAudioFile(item.videoName)
    val progress = if (item.videoDuration > 0)
        item.videoPosition.toFloat() / item.videoDuration.toFloat() else 0f
    NiThumbCard(
        title = item.videoName,
        durationText = "",
        thumbnailModel = thumbPath,
        progressFraction = progress,
        contentScale = if (isAudio) ContentScale.Fit else ContentScale.Crop,
        onClick = onClick,
        horizontal = true,
        subtitleText = formatPlayTime(item.playTime),
        mediaLabel = mediaTypeLabel(item.mediaType),
        squareCover = isAudio,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
}

/** 快速访问结果：文件用缩略图卡片，文件夹用图标行；文件夹跳浏览、文件跳播放。 */
@Composable
private fun QuickAccessResultRow(
    item: QuickAccessUiItem,
    thumbPath: String?,
    onClick: () -> Unit,
) {
    val entity = item.entity
    val libraryName = item.libraryName ?: stringResource(R.string.search_unknown_storage)
    if (entity.isDirectory) {
        SearchResultRow(
            icon = Icons.Filled.Folder,
            iconStyle = NiAppIconStyle,
            title = entity.name,
            subtitle = stringResource(R.string.search_folder_subtitle, libraryName),
            subtitleDimmed = !item.libraryValid,
            onClick = onClick,
        )
        return
    }
    val isAudio = MediaFileTypes.isAudioFile(entity.name)
    val isImage = MediaFileTypes.isImageFile(entity.name)
    NiThumbCard(
        title = entity.name,
        durationText = "",
        thumbnailModel = thumbPath,
        contentScale = if (isAudio) ContentScale.Fit else ContentScale.Crop,
        onClick = onClick,
        horizontal = true,
        subtitleText = libraryName,
        mediaLabel = mediaLabelForFile(isAudio, isImage),
        squareCover = isAudio,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun mediaLabelForFile(isAudio: Boolean, isImage: Boolean): String = when {
    isAudio -> stringResource(R.string.storage_file_type_audio)
    isImage -> stringResource(R.string.storage_file_type_image)
    else -> stringResource(R.string.storage_file_type_video)
}

/** 通用搜索结果行：左图标 + 中标题/副标题 + 右尾随。 */
@Composable
private fun SearchResultRow(
    icon: ImageVector,
    iconStyle: NiIconStyleSpec,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleDimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NiStyleIcon(
            icon = icon,
            style = iconStyle,
            containerSize = 44.dp,
            iconSize = 22.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (subtitleDimmed) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun mediaTypeLabel(type: MediaType): String = when (type) {
    MediaType.LOCAL_STORAGE -> stringResource(R.string.storage_type_local)
    MediaType.EXTERNAL_STORAGE -> stringResource(R.string.storage_type_device)
    MediaType.SMB_SERVER -> "SMB"
    MediaType.WEBDAV_SERVER -> "WebDAV"
    MediaType.QUICK_ACCESS -> stringResource(R.string.storage_type_quick)
    else -> stringResource(R.string.storage_type_other)
}

private fun formatPlayTime(date: Date): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(date)
}
