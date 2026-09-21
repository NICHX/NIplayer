package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.iconstyle.NiAppIconStyle
import com.nichx.niplayer.designsystem.iconstyle.NiStyleIcon
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.common.media.MediaFileTypes.isImageFile
import com.nichx.niplayer.storage.StorageFile


@Composable
internal fun FileList(
    files: List<StorageFile>,
    thumbnailUrls: Map<String, String>,
    tooShortPaths: Set<String>,
    encryptedPaths: Set<String>,
    isMultiSelect: Boolean,
    selectedPaths: Set<String>,
    preparingPath: String?,
    uploads: List<ActiveUpload>,
    onCancelUpload: (Long) -> Unit,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    onEnterMultiSelect: (StorageFile) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    contentTopInset: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) {
            item(key = "list-header") {
                header()
            }
        }
        if (uploads.isNotEmpty()) {
            item(key = "upload-strip") {
                UploadPendingStrip(uploads = uploads, onCancel = onCancelUpload)
            }
        }
        items(
            items = files,
            key = { it.path },
        ) { file ->
            FileRow(
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
internal fun UploadPendingStrip(uploads: List<ActiveUpload>, onCancel: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uploads.forEach { u ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = u.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (u.fraction >= 0f) {
                        LinearProgressIndicator(
                            progress = { u.fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    } else {
                        // 总大小未知 → 不确定进度
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (u.fraction >= 0f) {
                                stringResource(R.string.storage_file_upload_progress, (u.fraction * 100).toInt())
                            } else {
                                stringResource(R.string.storage_file_upload_waiting)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (u.speedBytesPerSec > 0) {
                            Text(
                                text = formatUploadSpeed(u.speedBytesPerSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { onCancel(u.taskId) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.storage_file_upload_cancel),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileRow(
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
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "rowBgAlpha",
    )

    val thumbShape = RoundedCornerShape(8.dp)

    val rowBgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        NiExtraColors.current.surfaceLevel2
    }

    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = bgAlpha }
            // 多选模式下取消阴影：列表保持平整，选中态用边框+背景区分，避免阴影干扰视觉
            .then(
                if (isMultiSelect) {
                    Modifier
                } else {
                    Modifier.shadow(elevation = 1.dp, shape = rowShape, clip = false)
                }
            )
            .clip(rowShape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = rowShape,
                    )
                } else {
                    Modifier
                }
            )
            .background(rowBgColor)
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
                    // 长按 = 进入多选并选中当前项；多选模式下长按不再响应（避免与多选冲突）
                    if (!isMultiSelect) onEnterMultiSelect()
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelect) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (isSelected) R.string.storage_file_deselect else R.string.storage_file_select,
                ),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (isVideo || isAudio || isImage) {
            // 列表缩略图：音频用 56×56 方形（唱片封套感 + 品牌色底），
            // 视频/图片保持 88×56 16:9 影视感。
            val thumbWidth = if (isAudio) 56.dp else 88.dp
            val thumbHeight = 56.dp
            val thumbBgColor: Color = if (isAudio)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else NiExtraColors.current.surfaceLevel3
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(thumbShape)
                    .background(thumbBgColor),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 中央播放徽章：仅可播放文件
                    if (isVideo || isAudio) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.storage_file_action_play),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    // 文件大小角标：右下角
                    if (file.length > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = formatFileSize(file.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else if (isVideo && isTooShort) {
                    Text(
                        text = "<15s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isImage -> Icons.Rounded.Image
                            isAudio -> Icons.Rounded.MusicNote
                            else -> Icons.Rounded.Movie
                        },
                        contentDescription = null,
                        tint = if (isAudio)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(if (isAudio) 26.dp else 24.dp),
                    )
                    if (preparing) PreparingBadge()
                }
            }
            Spacer(Modifier.width(12.dp))
        } else if (file.isDirectory) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                // 加密文件夹锁定角标
                if (isEncrypted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.storage_file_encrypted),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NiExtraColors.current.surfaceLevel3),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fileIcon(file, isVideo, isAudio),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            // 文件名 2 行自动缩字，长名从 16sp 缩到 12sp，仍超出则省略
            NiAutoSizeText(
                text = file.name,
                maxLines = 2,
                minFontSize = 12.sp,
                maxFontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            // 文件大小已移至缩略图右下角角标，此处仅保留文件夹提示
            if (file.isDirectory) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.storage_file_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (!isMultiSelect) {
            IconButton(
                onClick = onShowFileActions,
                modifier = Modifier.size(32.dp),
            ) {
                NiStyleIcon(
                    icon = Icons.Rounded.MoreVert,
                    style = NiAppIconStyle,
                    containerSize = 32.dp,
                    iconSize = 18.dp,
                    contentDescription = stringResource(R.string.storage_file_more),
                )
            }
        }
    }
}

/** 平铺列表模式下，每个缩进层级左侧增加的缩进宽度。 */
internal val FlatIndentStep = 12.dp

/** 平铺列表缩进封顶深度（竖屏）：超过该层级不再继续缩进，避免深层嵌套让列表项变得过窄。 */
internal const val FlatMaxIndentDepth = 7

/** 平铺列表缩进封顶深度（横屏）：横向空间充足，允许更多缩进层级。 */
internal const val FlatMaxIndentDepthLandscape = 11

/**
 * 平铺列表缩进区：按层级缩进，封顶后不再加宽。
 *
 * 因为手风琴（同级只开一枝）限制了实际深度，封顶设得较高（竖屏 7 / 横屏 11），
 * 常规目录基本不会触及封顶，无需额外深度指示元素。
 */
@Composable
internal fun FlatIndent(depth: Int) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cap = if (isLandscape) FlatMaxIndentDepthLandscape else FlatMaxIndentDepth
    val width = FlatIndentStep * minOf(depth, cap).toFloat()
    Spacer(Modifier.width(width))
}

/** 平铺列表的树节点：文件（含文件夹）+ 其在当前目录树中的缩进深度。 */
internal data class FlatTreeNode(val file: StorageFile, val depth: Int)

/**
 * 将当前目录及其已展开的子树拍平成按顺序排列的 [FlatTreeNode] 列表。
 *
 * 仅缓存了子项的展开文件夹才会内联展开（[treeChildren] 命中），未加载完成的
 * 文件夹只显示折叠行；子文件夹若也处于展开态且已有缓存则递归继续展开。
 */
internal fun buildFlatTreeItems(
    files: List<StorageFile>,
    treeChildren: Map<String, List<StorageFile>>,
    treeExpanded: Set<String>,
): List<FlatTreeNode> {
    val out = ArrayList<FlatTreeNode>()
    fun walk(items: List<StorageFile>, depth: Int) {
        for (f in items) {
            out.add(FlatTreeNode(f, depth))
            if (f.isDirectory) {
                val key = f.path.trimEnd('/')
                if (key in treeExpanded) {
                    treeChildren[key]?.let { walk(it, depth + 1) }
                }
            }
        }
    }
    walk(files, 0)
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileFlatList(
    files: List<StorageFile>,
    thumbnailUrls: Map<String, String>,
    tooShortPaths: Set<String>,
    encryptedPaths: Set<String>,
    isMultiSelect: Boolean,
    selectedPaths: Set<String>,
    preparingPath: String?,
    treeChildren: Map<String, List<StorageFile>>,
    treeExpanded: Set<String>,
    treeLoading: Set<String>,
    uploads: List<ActiveUpload>,
    onCancelUpload: (Long) -> Unit,
    onToggleFolder: (StorageFile) -> Unit,
    onOpenDirectory: (StorageFile) -> Unit,
    onPlayFile: (StorageFile) -> Unit,
    onOpenImageFile: (StorageFile) -> Unit,
    onShowFileActions: (StorageFile) -> Unit,
    onToggleSelection: (StorageFile) -> Unit,
    onEnterMultiSelect: (StorageFile) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    contentTopInset: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
) {
    // 拍平当前目录 + 已展开子目录为纵向顺序的节点列表（key 用 path，树内唯一）
    val nodes = remember(files, treeChildren, treeExpanded) {
        buildFlatTreeItems(files, treeChildren, treeExpanded)
    }
    // 展开/折叠前记录首可见行（node path + 像素 offset）；nodes 变化后复位滚动，
    // 避免手风琴折叠同级导致其上方行被移除、引发首屏内容跳动
    var scrollAnchor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(nodes) {
        val anchor = scrollAnchor ?: return@LaunchedEffect
        scrollAnchor = null
        val idx = nodes.indexOfFirst { it.file.path == anchor.first }
        if (idx >= 0 && listState.firstVisibleItemIndex != idx) {
            listState.scrollToItem(idx, anchor.second)
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) {
            item(key = "list-header") {
                header()
            }
        }
        if (uploads.isNotEmpty()) {
            item(key = "upload-strip") {
                UploadPendingStrip(uploads = uploads, onCancel = onCancelUpload)
            }
        }
        items(
            items = nodes,
            key = { it.file.path },
        ) { node ->
            val file = node.file
            // animateItem 让新增/移除/位移的节点以淡入淡出+展开收缩动画出现，实现展开收起动画
            Box(Modifier.animateItem()) {
                if (file.isDirectory) {
                    val key = file.path.trimEnd('/')
                    Row {
                        FlatIndent(node.depth)
                        Box(Modifier.weight(1f)) {
                            FlatFolderRow(
                                file = file,
                                isExpanded = key in treeExpanded,
                                isLoading = key in treeLoading,
                                isEncrypted = key in encryptedPaths,
                                isMultiSelect = isMultiSelect,
                                isSelected = file.path in selectedPaths,
                                onToggleExpand = {
                                    // 记录当前首可见行作为滚动锚点，展开/折叠由 onToggleFolder 触发
                                    val first = listState.firstVisibleItemIndex
                                    scrollAnchor = nodes.getOrNull(first)?.file?.path
                                        ?.let { it to listState.firstVisibleItemScrollOffset }
                                    onToggleFolder(file)
                                },
                                onOpenDirectory = { onOpenDirectory(file) },
                                onShowFileActions = { onShowFileActions(file) },
                                onToggleSelection = { onToggleSelection(file) },
                                onEnterMultiSelect = { onEnterMultiSelect(file) },
                            )
                        }
                    }
                } else {
                    // 叶子文件行：缩进+层级指示后复用 FileRow（播放/看图/⋮/长按多选行为一致）
                    Row {
                        FlatIndent(node.depth)
                        Box(Modifier.weight(1f)) {
                            FileRow(
                                file = file,
                                thumbnailUrl = thumbnailUrls[file.path],
                                isTooShort = tooShortPaths.contains(file.path),
                                isEncrypted = false,
                                isMultiSelect = isMultiSelect,
                                isSelected = file.path in selectedPaths,
                                preparing = preparingPath != null && file.path == preparingPath,
                                onOpenDirectory = {},
                                onPlayFile = onPlayFile,
                                onOpenImageFile = onOpenImageFile,
                                onShowFileActions = { onShowFileActions(file) },
                                onToggleSelection = { onToggleSelection(file) },
                                onEnterMultiSelect = { onEnterMultiSelect(file) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlatFolderRow(
    file: StorageFile,
    isExpanded: Boolean,
    isLoading: Boolean,
    isEncrypted: Boolean,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onOpenDirectory: () -> Unit,
    onShowFileActions: () -> Unit,
    onToggleSelection: () -> Unit,
    onEnterMultiSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "flatFolderBgAlpha",
    )

    val rowBgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        NiExtraColors.current.surfaceLevel2
    }
    val rowShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = bgAlpha }
            .then(
                if (isMultiSelect) {
                    Modifier
                } else {
                    Modifier.shadow(elevation = 1.dp, shape = rowShape, clip = false)
                }
            )
            .clip(rowShape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = rowShape,
                    )
                } else {
                    Modifier
                }
            )
            .background(rowBgColor)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = {
                    if (isMultiSelect) {
                        onToggleSelection()
                    } else {
                        // 点击卡片其余区域：进入文件夹（加密文件夹由 openDirectory 拦截弹密码）
                        onOpenDirectory()
                    }
                },
                onLongClick = {
                    if (!isMultiSelect) onEnterMultiSelect()
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 展开/折叠箭头：独立可点击区域，消费点击（不会触发上方进入文件夹）
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggleExpand() },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                // 箭头旋转动画：收起→向右，展开→旋转 90°向下
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 90f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "flatChevronRotation",
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.storage_file_toggle_folder),
                    tint = if (isExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = chevronRotation
                            // 旋转中心在图标中心（默认即中心，无需额外处理）
                        },
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (isMultiSelect) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (isSelected) R.string.storage_file_deselect else R.string.storage_file_select,
                ),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        // 文件夹图标（含加密锁角标，与 FileRow 一致）
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            if (isEncrypted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.storage_file_encrypted),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            NiAutoSizeText(
                text = file.name,
                maxLines = 2,
                minFontSize = 12.sp,
                maxFontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isLoading) stringResource(R.string.storage_file_folder_loading)
                else stringResource(R.string.storage_file_folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (!isMultiSelect) {
            IconButton(
                onClick = onShowFileActions,
                modifier = Modifier.size(32.dp),
            ) {
                NiStyleIcon(
                    icon = Icons.Rounded.MoreVert,
                    style = NiAppIconStyle,
                    containerSize = 32.dp,
                    iconSize = 18.dp,
                    contentDescription = stringResource(R.string.storage_file_more),
                )
            }
        }
    }
}
