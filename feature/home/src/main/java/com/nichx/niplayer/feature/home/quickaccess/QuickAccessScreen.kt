package com.nichx.niplayer.feature.home.quickaccess

import com.nichx.niplayer.feature.home.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.NiSkeletonBox
import com.nichx.niplayer.designsystem.components.NiSkeletonLine
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiSectionHeader
import com.nichx.niplayer.designsystem.components.NiTopBar
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.feature.home.MediaFileTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessScreen(
    onNavigateToStorageFile: (Int, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: (Boolean) -> Unit = {},
    viewModel: QuickAccessViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val dataReady by viewModel.dataReady.collectAsStateWithLifecycle()
    val qaThumbnailUrls by viewModel.qaThumbnailUrls.collectAsStateWithLifecycle()
    var isEditing by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<QuickAccessUiItem?>(null) }

    // 一次性事件路由：文件播放 / 文件夹跳文件浏览 / 错误提示
    val messageController = LocalAppMessageController.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickAccessEvent.NavigateToPlayer -> onNavigateToPlayer(event.isAudio)
                is QuickAccessEvent.NavigateToStorageFile ->
                    onNavigateToStorageFile(event.libraryId, event.relativePath)
                is QuickAccessEvent.ShowError -> messageController.post(NiMessage.error(event.message))
            }
        }
    }

    // 按类型分区的拍平行：Header（分区标题）与 ItemRow 交替，grid 顺序即列表顺序
    val rows = remember(items) { buildQaRows(items) }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = stringResource(R.string.quick_access_title),
                actions = {
                    if (items.isNotEmpty()) {
                        if (isEditing) {
                            TextButton(onClick = { isEditing = false }) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(stringResource(R.string.quick_access_done))
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!dataReady) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false,
            ) {
                items(List(6) { it }) {
                    QuickAccessGridItemSkeleton()
                }
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NiEmptyState(
                    icon = Icons.Filled.BookmarkBorder,
                    text = stringResource(R.string.quick_access_empty),
                    hint = stringResource(R.string.quick_access_empty_hint),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rows.forEach { row ->
                    when (row) {
                        is QaRow.Header -> item(
                            key = row.key,
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            QaSectionHeader(section = row.section, count = row.count)
                        }
                        is QaRow.ItemRow -> item(key = row.key) {
                            val item = row.item
                            QuickAccessGridItem(
                                item = item,
                                thumbnailUrl = qaThumbnailUrls[item.qaThumbKey],
                                isEditing = isEditing,
                                onClick = {
                                    if (!isEditing) viewModel.openItem(item)
                                },
                                onDelete = { deleteTarget = item },
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        NiConfirmDialog(
            title = stringResource(R.string.quick_access_delete_title),
            text = stringResource(R.string.quick_access_delete_confirm, deleteTarget!!.entity.name),
            onConfirm = {
                viewModel.deleteItem(deleteTarget!!.entity.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 快速访问分区类型。 */
private enum class QaSection { FOLDER, VIDEO, AUDIO, IMAGE, FILE }

/** 分区内条目的类型归属：文件夹优先，其次按文件名扩展名判定。 */
private fun sectionOf(item: QuickAccessUiItem): QaSection {
    val name = item.entity.name
    return when {
        item.entity.isDirectory -> QaSection.FOLDER
        MediaFileTypes.isVideoFile(name) -> QaSection.VIDEO
        MediaFileTypes.isAudioFile(name) -> QaSection.AUDIO
        MediaFileTypes.isImageFile(name) -> QaSection.IMAGE
        else -> QaSection.FILE
    }
}

/** 拍平网格行：Header（分区标题，占整行）与 ItemRow（条目卡片）交替。 */
private sealed interface QaRow {
    val key: String

    data class Header(val section: QaSection, val count: Int) : QaRow {
        override val key get() = "qa_header_${section.name}"
    }

    data class ItemRow(val item: QuickAccessUiItem, val section: QaSection) : QaRow {
        override val key get() = "qa_item_${item.entity.id}"
    }
}

/** 按固定分区顺序（文件夹→视频→音频→图片→其他）拍平，空分区不产出 Header。 */
private fun buildQaRows(items: List<QuickAccessUiItem>): List<QaRow> {
    val grouped = items.groupBy { sectionOf(it) }
    return buildList {
        for (section in QaSection.entries) {
            val sectionItems = grouped[section].orEmpty()
            if (sectionItems.isEmpty()) continue
            add(QaRow.Header(section, sectionItems.size))
            sectionItems.forEach { add(QaRow.ItemRow(it, section)) }
        }
    }
}

/** 分区标题行：复用 [NiSectionHeader] 的标题 + 计数胶囊样式（不显示"查看全部"）。 */
@Composable
private fun QaSectionHeader(section: QaSection, count: Int) {
    val title = stringResource(
        when (section) {
            QaSection.FOLDER -> R.string.storage_file_folder
            QaSection.VIDEO -> R.string.storage_file_type_video
            QaSection.AUDIO -> R.string.storage_file_type_audio
            QaSection.IMAGE -> R.string.storage_file_type_image
            QaSection.FILE -> R.string.qa_section_file
        }
    )
    NiSectionHeader(
        title = title,
        count = count,
        onClick = null,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAccessGridItem(
    item: QuickAccessUiItem,
    thumbnailUrl: String?,
    isEditing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "qaPressScale",
    )
    val finalScale = pressScale

    val cardShape = RoundedCornerShape(16.dp)
    val name = item.entity.name
    val isVideo = !item.entity.isDirectory && MediaFileTypes.isVideoFile(name)
    val isAudio = !item.entity.isDirectory && MediaFileTypes.isAudioFile(name)
    val isImage = !item.entity.isDirectory && MediaFileTypes.isImageFile(name)
    val hasThumbnail = thumbnailUrl != null && (isVideo || isAudio || isImage)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = finalScale
                scaleY = finalScale
            }
            .then(
                if (isEditing) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = cardShape,
                ) else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation = 1.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .background(NiExtraColors.current.surfaceLevel3)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            if (item.entity.isDirectory) {
                val pc = MaterialTheme.colorScheme.primaryContainer
                val gradientColors = remember(name) { listOf(pc, pc.copy(alpha = 0.7f)) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradientColors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NiExtraColors.current.surfaceLevel3),
                ) {
                    if (hasThumbnail) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            // 所有媒体统一 16:9 Crop 裁切（视频/音频/图片）
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isVideo || isAudio) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.play),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when {
                                    isVideo -> Icons.Rounded.Movie
                                    isAudio -> Icons.Rounded.MusicNote
                                    isImage -> Icons.Rounded.Image
                                    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = when {
                                    isAudio -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    isVideo || isImage -> Color.White.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.size(52.dp),
                            )
                        }
                    }
                }
            }

            if (isEditing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .combinedClickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        NiAutoSizeText(
            text = name,
            maxLines = 2,
            minFontSize = 11.sp,
            maxFontSize = 13.sp,
            color = if (item.libraryValid) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun QuickAccessGridItemSkeleton() {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(NiExtraColors.current.surfaceLevel3),
            contentAlignment = Alignment.Center,
        ) {
            NiSkeletonBox(
                width = 44.dp,
                height = 44.dp,
                shape = RoundedCornerShape(12.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        NiSkeletonLine(widthFraction = 0.7f)
        Spacer(Modifier.height(4.dp))
        NiSkeletonLine(widthFraction = 0.5f)
    }
}
