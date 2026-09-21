package com.nichx.niplayer.feature.home.library

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.common.media.MediaFileTypes.isImageFile
import com.nichx.niplayer.storage.StorageFile


/**
 * 画廊视图：手机相册式密集方形格子，仅展示图片/视频媒体与文件夹。
 *
 * 非媒体文件（音频/文档等）在画廊模式隐藏以保持画面纯净；文件夹保留为紧凑方形
 * 瓦片以支持继续下钻导航。空媒体目录显示"暂无媒体"空态。
 */
@Composable
internal fun FileGallery(
    files: List<StorageFile>,
    thumbnailUrls: Map<String, String>,
    tooShortPaths: Set<String>,
    encryptedPaths: Set<String>,
    isMultiSelect: Boolean,
    selectedPaths: Set<String>,
    preparingPath: String?,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    onEnterMultiSelect: (StorageFile) -> Unit,
    galleryState: LazyGridState,
    contentTopInset: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
) {
    // 仅保留：文件夹（继续导航）+ 图片 / 视频（相册媒体），隐藏音频与其他文件
    val galleryItems = files.filter {
        it.isDirectory || MediaFileTypes.isImageFile(it.name) || MediaFileTypes.isVideoFile(it.name)
    }
    if (galleryItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (header != null) header()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                NiEmptyState(
                    icon = Icons.Rounded.Image,
                    text = stringResource(R.string.storage_file_gallery_empty),
                    hint = stringResource(R.string.storage_file_gallery_empty_hint),
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        state = galleryState,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (header != null) {
            item(key = "list-header", span = { GridItemSpan(maxLineSpan) }) {
                header()
            }
        }
        items(
            items = galleryItems,
            key = { it.path },
        ) { file ->
            GalleryCell(
                file = file,
                thumbnailUrl = thumbnailUrls[file.path],
                isTooShort = tooShortPaths.contains(file.path),
                isEncrypted = file.isDirectory && encryptedPaths.contains(file.path.trimEnd('/')),
                isMultiSelect = isMultiSelect,
                isSelected = file.path in selectedPaths,
                preparing = preparingPath != null && file.path == preparingPath,
                onOpenDirectory = { onOpenDirectory(file) },
                onPlayFile = { onPlayFile(file) },
                onOpenImageFile = { onOpenImageFile(file) },
                onToggleSelection = { onToggleSelection(file) },
                onEnterMultiSelect = { onEnterMultiSelect(file) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GalleryCell(
    file: StorageFile,
    thumbnailUrl: String?,
    isTooShort: Boolean,
    isEncrypted: Boolean,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    preparing: Boolean,
    onOpenDirectory: () -> Unit,
    onPlayFile: () -> Unit,
    onOpenImageFile: () -> Unit,
    onToggleSelection: () -> Unit,
    onEnterMultiSelect: () -> Unit,
) {
    val isVideo = MediaFileTypes.isVideoFile(file.name)
    val isImage = MediaFileTypes.isImageFile(file.name)
    // 相册格统一无缝方形（文件夹与媒体一致）
    val cellShape = RoundedCornerShape(0.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "galleryCellScale",
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(cellShape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = cellShape,
                    )
                } else {
                    Modifier
                }
            )
            .background(NiExtraColors.current.surfaceLevel3)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = {
                    if (isMultiSelect) {
                        onToggleSelection()
                    } else {
                        when {
                            file.isDirectory -> onOpenDirectory()
                            isVideo -> onPlayFile()
                            isImage -> onOpenImageFile()
                            else -> onPlayFile()
                        }
                    }
                },
                onLongClick = {
                    // 长按 = 进入多选并选中当前项；多选模式下长按不再响应
                    if (!isMultiSelect) onEnterMultiSelect()
                },
            ),
    ) {
        if (file.isDirectory) {
            // 文件夹：方形瓦片 + 双层图标 + 玻璃胶囊名（无圆角、无描边）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 文件夹图标
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            // 底部名称：贴合文字宽度的玻璃胶囊（非整条黑底），柔和可读
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(50))
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isEncrypted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.storage_file_encrypted),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        } else {
            // 媒体：方形相册格，缩略图满载裁剪
            val hasThumbnail = thumbnailUrl != null
            if (hasThumbnail) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isVideo && isTooShort -> Text(
                            text = "<15s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                        )
                        isVideo -> Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(34.dp),
                        )
                        isImage -> Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    if (preparing) PreparingBadge()
                }
            }
            // 视频中心播放徽章
            if (isVideo && hasThumbnail) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.storage_file_action_play),
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        // 画廊模式不提供单文件操作入口，仅保留选中指示
        // 多选模式：左上角选中指示（圆形勾选）
        if (isMultiSelect) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.45f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = stringResource(
                        if (isSelected) R.string.storage_file_selected else R.string.storage_file_select,
                    ),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
