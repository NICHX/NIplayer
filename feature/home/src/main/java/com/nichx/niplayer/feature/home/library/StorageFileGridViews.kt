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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.common.media.MediaFileTypes.isImageFile
import com.nichx.niplayer.storage.StorageFile


@Composable
internal fun FileGrid(
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
    onShowFileActions: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    onEnterMultiSelect: (StorageFile) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    contentTopInset: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) {
            item(key = "list-header", span = { GridItemSpan(maxLineSpan) }) {
                header()
            }
        }
        items(
            items = files,
            key = { it.path },
        ) { file ->
            GridFileCard(
                file = file,
                thumbnailUrl = thumbnailUrls[file.path],
                isTooShort = tooShortPaths.contains(file.path),
                isEncrypted = file.isDirectory && encryptedPaths.contains(file.path.trimEnd('/')),
                isMultiSelect = isMultiSelect,
                isSelected = file.path in selectedPaths,
                preparing = preparingPath != null && file.path == preparingPath,
                onOpenDirectory = onOpenDirectory,
                onPlayFile = onPlayFile,
                onOpenImageFile = onOpenImageFile,
                onShowFileActions = { onShowFileActions(file) },
                onToggleSelection = { onToggleSelection(file) },
                onEnterMultiSelect = { onEnterMultiSelect(file) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridFileCard(
    file: StorageFile,
    thumbnailUrl: String?,
    isTooShort: Boolean,
    isEncrypted: Boolean,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    preparing: Boolean,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: () -> Unit,
    onToggleSelection: () -> Unit,
    onEnterMultiSelect: () -> Unit,
) {
    val isVideo = MediaFileTypes.isVideoFile(file.name)
    val isAudio = MediaFileTypes.isAudioFile(file.name)
    val isImage = MediaFileTypes.isImageFile(file.name)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "gridCardScale",
    )

    val cardShape = RoundedCornerShape(16.dp)

    // 卡片仅含缩略图/文件夹图标（16:9 圆角），
    // 文件名在卡片外底部居中显示，长文字两行自动缩字。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation = 1.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = cardShape,
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
                                file.isDirectory -> onOpenDirectory(file)
                                isVideo || isAudio -> onPlayFile(file)
                                isImage -> onOpenImageFile(file)
                                else -> Unit
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
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    // 加密文件夹锁定角标（左上角）
                    if (isEncrypted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.storage_file_encrypted),
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            } else {
                // 文件：统一 16:9 缩略图比例，音乐视频混存时卡片高度对齐。
                val thumbBg: Color = when {
                    isAudio -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else -> NiExtraColors.current.surfaceLevel3
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(thumbBg),
                ) {
                    val hasThumbnail = (isVideo || isAudio || isImage) && thumbnailUrl != null
                    if (hasThumbnail) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 类型角标：左上角，仅在媒体文件显示（多选模式让位于选中指示）
                    val typeLabel = when {
                        isVideo -> stringResource(R.string.storage_file_type_video)
                        isAudio -> stringResource(R.string.storage_file_type_audio)
                        isImage -> stringResource(R.string.storage_file_type_image)
                        else -> null
                    }
                    if (typeLabel != null && !isMultiSelect) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 6.dp, top = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = LocalNiGlassPanelOpacity.current))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // 中央播放按钮：仅在有缩略图的可播放文件显示，避免与占位图标重叠
                    if ((isVideo || isAudio) && hasThumbnail) {
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
                                contentDescription = stringResource(R.string.storage_file_action_play),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // 大小角标：右下角
                    if ((isVideo || isAudio || isImage) && file.length > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = LocalNiGlassPanelOpacity.current))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = formatFileSize(file.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // 无缩略图占位：按文件类型给不同图标 + 软色
                    if (!hasThumbnail) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isAudio) Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                        ),
                                    ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isVideo && isTooShort -> Text(
                                    text = "<15s",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                )
                                isVideo -> Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(52.dp),
                                )
                                isAudio -> Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(56.dp),
                                )
                                isImage -> Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(52.dp),
                                )
                                else -> Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                            if (preparing) PreparingBadge()
                        }
                    }
                }
            }

                    // 单文件操作入口：右上角 ⋮ 按钮（多选模式下隐藏）
                    if (!isMultiSelect) {
                        CardMoreButton(
                            onClick = onShowFileActions,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp),
                            size = 30.dp,
                        )
                    }

                    // 多选模式：左上角选中指示（圆形勾选）
                    if (isMultiSelect) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(26.dp)
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
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
        }
        // 文件名：卡片外底部居中，长文字两行自动缩字
        NiAutoSizeText(
            text = file.name,
            maxLines = 2,
            minFontSize = 11.sp,
            maxFontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
}
