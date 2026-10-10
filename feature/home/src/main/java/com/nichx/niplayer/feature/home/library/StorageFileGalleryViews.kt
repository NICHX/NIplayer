package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredGridItems
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nichx.niplayer.designsystem.components.NiEmptyState
import com.nichx.niplayer.designsystem.components.niCardOpacity
import com.nichx.niplayer.designsystem.components.niHasCustomBackground
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.motion.niPressable
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.common.media.MediaFileTypes.isImageFile
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.util.concurrent.ConcurrentHashMap


/** 画廊「自适应」模式下单个格子的最小宽度（方形画廊与瀑布流共用）。 */
private val GalleryAutoMinCellSize = 100.dp

/** 方形画廊格子间距（保持既有无缝相册质感，把尽可能多的空间留给缩略图）。 */
private val GalleryGap = 1.dp

/** 瀑布流横/纵间距（错落排布需略大间距以区分相邻瓦片）。 */
private val WaterfallGap = 3.dp

/** 瀑布流缺省宽高比，以及为避免极端长/宽图撑高撑扁而收敛的区间。 */
private const val WATERFALL_FALLBACK_RATIO = 1f
private const val WATERFALL_MIN_RATIO = 0.6f
private const val WATERFALL_MAX_RATIO = 2.2f

/**
 * 画廊列数可调范围（含手动覆盖上、下限），按窗口宽度类收窄：
 * 手机 2..5 / 平板 2..8 / 大屏 2..10。
 *
 * 手动列数此时收敛到该范围；自适应模式不受约束，列数由 [GalleryAutoMinCellSize] 推导，
 * 因此瀑布流默认（自适应）不会锁定固定列数。
 */
internal fun galleryColumnRange(width: NiWindowWidthSizeClass): IntRange = when (width) {
    NiWindowWidthSizeClass.Compact -> 2..5
    NiWindowWidthSizeClass.Medium -> 2..8
    NiWindowWidthSizeClass.Expanded -> 2..10
}

/** 缩略图宽高比进程级缓存，键为缩略图本地绝对路径。 */
private val thumbnailAspectRatioCache = ConcurrentHashMap<String, Float>()

/** 仅解码图片边界读取宽高比（不分配像素内存）；失败返回 null。 */
private fun readThumbnailAspectRatio(path: String): Float? = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth > 0 && options.outHeight > 0) {
        options.outWidth.toFloat() / options.outHeight.toFloat()
    } else {
        null
    }
} catch (_: Exception) {
    null
}

/**
 * 读取缩略图的原始宽高比用于瀑布流：图片缩略图按原图等比缩放（不裁剪），
 * 其宽高比即原图比例；结果带进程级缓存，避免滚动时重复读盘。
 * 缩略图未就绪时返回 null，待 [thumbnailUrl] 更新为本地缓存路径后自动重算。
 */
@Composable
private fun rememberThumbnailAspectRatio(thumbnailUrl: String?): Float? {
    if (thumbnailUrl.isNullOrEmpty()) return null
    var ratio by remember(thumbnailUrl) { mutableStateOf(thumbnailAspectRatioCache[thumbnailUrl]) }
    LaunchedEffect(thumbnailUrl) {
        if (ratio == null) {
            val measured = withContext(Dispatchers.IO) { readThumbnailAspectRatio(thumbnailUrl) }
            if (measured != null && measured.isFinite() && measured > 0f) {
                thumbnailAspectRatioCache[thumbnailUrl] = measured
                ratio = measured
            }
        }
    }
    return ratio
}

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
    waterfallState: LazyStaggeredGridState,
    contentTopInset: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    columns: Int = FileBrowserSettings.GRID_COLUMNS_AUTO,
    layout: FileBrowserSettings.GalleryLayout = FileBrowserSettings.GalleryLayout.SQUARE,
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
    val widthClass = LocalNiWindowSizeClass.current.width
    val columnRange = galleryColumnRange(widthClass)
    val fixedColumns = columns.coerceIn(columnRange.first, columnRange.last)

    if (layout == FileBrowserSettings.GalleryLayout.WATERFALL) {
        // 瀑布流：按缩略图/图片原始宽高比错落排布，文件夹保底为方形瓦片。
        // 自适应模式下不锁定固定列数，列数由 GalleryAutoMinCellSize 推导。
        val staggeredCells = if (columns == FileBrowserSettings.GRID_COLUMNS_AUTO) {
            StaggeredGridCells.Adaptive(minSize = GalleryAutoMinCellSize)
        } else {
            StaggeredGridCells.Fixed(fixedColumns)
        }
        LazyVerticalStaggeredGrid(
            state = waterfallState,
            columns = staggeredCells,
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = contentTopInset,
                bottom = FabBottomOffset,
            ),
            verticalItemSpacing = WaterfallGap,
            horizontalArrangement = Arrangement.spacedBy(WaterfallGap),
        ) {
            if (header != null) {
                item(key = "list-header", span = StaggeredGridItemSpan.FullLine) {
                    header()
                }
            }
            staggeredGridItems(
                items = galleryItems,
                key = { it.path },
            ) { file ->
                val aspectRatio = if (file.isDirectory) {
                    WATERFALL_FALLBACK_RATIO
                } else {
                    rememberThumbnailAspectRatio(thumbnailUrls[file.path]) ?: WATERFALL_FALLBACK_RATIO
                }
                GalleryCell(
                    file = file,
                    thumbnailUrl = thumbnailUrls[file.path],
                    aspectRatio = aspectRatio.coerceIn(WATERFALL_MIN_RATIO, WATERFALL_MAX_RATIO),
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
        return
    }

    // 方形画廊：统一正方形瓦片，自适应或用户指定列数。
    val gridCells = if (columns == FileBrowserSettings.GRID_COLUMNS_AUTO) {
        GridCells.Adaptive(minSize = GalleryAutoMinCellSize)
    } else {
        GridCells.Fixed(fixedColumns)
    }
    LazyVerticalGrid(
        state = galleryState,
        columns = gridCells,
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = contentTopInset,
            bottom = FabBottomOffset,
        ),
        horizontalArrangement = Arrangement.spacedBy(GalleryGap),
        verticalArrangement = Arrangement.spacedBy(GalleryGap),
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
    isEncrypted: Boolean,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    preparing: Boolean,
    onOpenDirectory: () -> Unit,
    onPlayFile: () -> Unit,
    onOpenImageFile: () -> Unit,
    onToggleSelection: () -> Unit,
    onEnterMultiSelect: () -> Unit,
    aspectRatio: Float = 1f,
) {
    val isVideo = MediaFileTypes.isVideoFile(file.name)
    val isImage = MediaFileTypes.isImageFile(file.name)
    // 方形画廊为正方形（1:1）；瀑布流按缩略图/图片原始宽高比；瓦片一律无缝（无圆角）
    val cellShape = RoundedCornerShape(0.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
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
            // 按压反馈：非按压态不挂图层（原实现恒等 graphicsLayer 常驻，画廊密度高时逐格多一层）
            .niPressable(
                pressedScale = 0.96f,
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
                        if (niHasCustomBackground) {
                            // 自定义背景下用均匀半透明填充（不用渐变）：文件夹卡片同样透出背景图
                            SolidColor(MaterialTheme.colorScheme.primaryContainer.copy(alpha = niCardOpacity))
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
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
