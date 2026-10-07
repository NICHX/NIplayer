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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.components.niCardOpacity
import com.nichx.niplayer.designsystem.components.niHasCustomBackground
import com.nichx.niplayer.designsystem.components.niNestedSurface
import com.nichx.niplayer.designsystem.components.LocalNiGlassPanelOpacity
import com.nichx.niplayer.designsystem.motion.niPressable
import com.nichx.niplayer.designsystem.theme.LocalNiWindowSizeClass
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.designsystem.theme.NiWindowWidthSizeClass
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.common.media.MediaFileTypes.isImageFile
import com.nichx.niplayer.datastore.FileBrowserSettings
import com.nichx.niplayer.storage.StorageFile
import kotlin.math.roundToInt


/** 自适应模式下单个网格单元的最小宽度。 */
private val GridAutoMinCellSize = 160.dp

/** 网格内容区左右内边距（与 LazyVerticalGrid 的 contentPadding 保持一致，用于推导单元格宽度）。 */
private val GridContentHPadding = 16.dp

/** 网格列间距上下限（整数 dp）：宽格取上限，格子变窄时按格宽等比收窄至下限。 */
private const val GRID_GAP_MAX_DP = 10
private const val GRID_GAP_MIN_DP = 4

/**
 * 按单元格宽度推导列间距（约为格宽的 1/16），取整到整 dp 后收敛到
 * [GRID_GAP_MIN_DP, GRID_GAP_MAX_DP]。列数越多、格子越窄，间距同步收窄，
 * 在不牺牲观感的前提下把更多宽度让给缩略图。
 */
private fun resolveGridGap(cellWidth: Dp): Dp =
    (cellWidth.value / 16f).roundToInt().coerceIn(GRID_GAP_MIN_DP, GRID_GAP_MAX_DP).dp

/**
 * 解析当前可用宽度下实际生效的列数：
 * 手动模式取用户列数并收敛到 [columnRange]；自适应模式沿用 GridCells.Adaptive 语义
 * （按 [GridAutoMinCellSize] 尽可能多列，此时格宽恒 ≥ 该值，间距维持上限）。
 * 仅用于推导间距，不改变自适应模式的列策略。
 */
private fun resolveGridColumns(
    availableWidth: Dp,
    requestedColumns: Int,
    columnRange: IntRange,
    referenceGap: Dp,
): Int {
    if (requestedColumns != FileBrowserSettings.GRID_COLUMNS_AUTO) {
        return requestedColumns.coerceIn(columnRange.first, columnRange.last)
    }
    if (availableWidth <= 0.dp) return columnRange.first
    val count = ((availableWidth + referenceGap) / (GridAutoMinCellSize + referenceGap)).toInt()
    return count.coerceAtLeast(1)
}

/**
 * 网格列数可调范围（含手动覆盖上、下限），按窗口宽度类收窄：
 * 手机（Compact）2..4 / 平板（Medium）2..6 / 大屏（Expanded）2..8。
 *
 * 下限 2：1 列等同列表，失去网格意义；上限 8：内容区约 960dp 时单元格仍 ≥100dp，
 * 再窄无法辨识缩略图与文件名。
 */
internal fun gridColumnRange(width: NiWindowWidthSizeClass): IntRange = when (width) {
    NiWindowWidthSizeClass.Compact -> 2..4
    NiWindowWidthSizeClass.Medium -> 2..6
    NiWindowWidthSizeClass.Expanded -> 2..8
}

@Composable
internal fun FileGrid(
    files: List<StorageFile>,
    thumbnailUrls: Map<String, String>,
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
    columns: Int = FileBrowserSettings.GRID_COLUMNS_AUTO,
    showTypeBadge: Boolean = false,
    showSizeBadge: Boolean = false,
) {
    val widthClass = LocalNiWindowSizeClass.current.width
    val columnRange = gridColumnRange(widthClass)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 先按可用宽度解析实际列数，再据其推导单元格宽度与列间距：
        // 列数越多、格子越窄，间距同步收窄，避免间距相对卡片过大而挤占卡片宽度。
        val availableWidth = (maxWidth - GridContentHPadding * 2).coerceAtLeast(0.dp)
        val resolvedColumns = resolveGridColumns(availableWidth, columns, columnRange, GRID_GAP_MAX_DP.dp)
        val cellWidth = (availableWidth - GRID_GAP_MAX_DP.dp * (resolvedColumns - 1)) / resolvedColumns
        val gap = resolveGridGap(cellWidth.coerceAtLeast(0.dp))
        // 自适应：按可用宽度推导列数（大屏自然显示更多列）；手动：取用户列数并收敛到当前宽度类范围
        val gridCells = if (columns == FileBrowserSettings.GRID_COLUMNS_AUTO) {
            GridCells.Adaptive(minSize = GridAutoMinCellSize)
        } else {
            GridCells.Fixed(resolvedColumns)
        }
        LazyVerticalGrid(
            state = gridState,
            columns = gridCells,
            contentPadding = PaddingValues(
                start = GridContentHPadding,
                end = GridContentHPadding,
                top = contentTopInset,
                bottom = FabBottomOffset,
            ),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
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
                    showTypeBadge = showTypeBadge,
                    showSizeBadge = showSizeBadge,
                )
            }
        }
    }
}

/**
 * 文件角标配色：默认沿用主题 tertiary 半透明胶囊；启用自定义背景后卡片与占位表面变半透明，
 * 角标会落在多变的底图上，改用高对比纯色底衬 + 白字保证可读性。
 */
@Composable
private fun fileBadgeColors(): Pair<Color, Color> =
    if (niHasCustomBackground) {
        Color.Black.copy(alpha = 0.72f) to Color.White
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = LocalNiGlassPanelOpacity.current) to
            MaterialTheme.colorScheme.onTertiary
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridFileCard(
    file: StorageFile,
    thumbnailUrl: String?,
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
    showTypeBadge: Boolean = false,
    showSizeBadge: Boolean = false,
) {
    val isVideo = MediaFileTypes.isVideoFile(file.name)
    val isAudio = MediaFileTypes.isAudioFile(file.name)
    val isImage = MediaFileTypes.isImageFile(file.name)

    val cardShape = RoundedCornerShape(16.dp)

    // 卡片仅含缩略图/文件夹图标（16:9 圆角），
    // 文件名在卡片外底部居中显示，长文字两行自动缩字。
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 单元格尺寸自适应：以 160dp 格宽为基准等比缩放角标/按钮/图标，
        // 手机多列网格下格子变窄时装饰不再显得过大（下限 0.62 保证仍可辨认）。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                // 启用自定义背景图时取消阴影，避免投影透过半透明卡片形成暗色分层
                .then(
                    if (niHasCustomBackground) {
                        Modifier
                    } else {
                        Modifier.shadow(elevation = 1.dp, shape = cardShape, clip = false)
                    },
                )
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
                // 按压反馈收敛到缩略图本体：原先缩放整列会把文件名一起缩放，且恒等图层常驻
                .niPressable(
                    pressedScale = 0.97f,
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
            val decorScale = (maxWidth.value / 160f).coerceIn(0.62f, 1f)
            val cornerPad = (6f * decorScale).dp
            val badgePadH = (6f * decorScale).dp
            val badgeSizePadH = (5f * decorScale).dp
            val badgePadV = (2f * decorScale).dp
            val badgeFontSize = (11f * decorScale).sp
            val playButtonSize = (36f * decorScale).dp
            val playIconSize = (20f * decorScale).dp
            val moreButtonSize = (30f * decorScale).dp
            val moreButtonPad = (4f * decorScale).dp
            val selectBadgeSize = (26f * decorScale).dp
            val selectIconSize = (16f * decorScale).dp
            val folderIconSize = (48f * decorScale).dp
            val lockBadgeSize = (22f * decorScale).dp
            val lockIconSize = (13f * decorScale).dp
            val fallbackIconSize = (52f * decorScale).dp
            val audioIconSize = (56f * decorScale).dp

            if (file.isDirectory) {
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
                        modifier = Modifier.size(folderIconSize),
                    )
                    // 加密文件夹锁定角标（左上角）
                    if (isEncrypted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(cornerPad)
                                .size(lockBadgeSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.storage_file_encrypted),
                                tint = Color.White,
                                modifier = Modifier.size(lockIconSize),
                            )
                        }
                    }
                }
            } else {
                // 文件：统一 16:9 缩略图比例，音乐视频混存时卡片高度对齐。
                val thumbBg: Color = niNestedSurface(
                    when {
                        isAudio -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else -> NiExtraColors.current.surfaceLevel3
                    },
                )
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
                    if (typeLabel != null && !isMultiSelect && showTypeBadge) {
                        val (badgeBg, badgeContent) = fileBadgeColors()
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = cornerPad, top = cornerPad)
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = badgePadH, vertical = badgePadV),
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = badgeFontSize,
                                color = badgeContent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // 中央播放按钮：仅在有缩略图的可播放文件显示，避免与占位图标重叠
                    if ((isVideo || isAudio) && hasThumbnail) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(playButtonSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.storage_file_action_play),
                                tint = Color.White,
                                modifier = Modifier.size(playIconSize),
                            )
                        }
                    }

                    // 大小角标：右下角
                    if ((isVideo || isAudio || isImage) && file.length > 0 && showSizeBadge) {
                        val (badgeBg, badgeContent) = fileBadgeColors()
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = cornerPad, bottom = cornerPad)
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = badgeSizePadH, vertical = badgePadV),
                        ) {
                            Text(
                                text = formatFileSize(file.length),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = badgeFontSize,
                                color = badgeContent,
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
                                    if (isAudio && !niHasCustomBackground) Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                        ),
                                    ) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isVideo -> Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(fallbackIconSize),
                                )
                                isAudio -> Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(audioIconSize),
                                )
                                isImage -> Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(fallbackIconSize),
                                )
                                else -> Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(fallbackIconSize),
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
                                .padding(top = moreButtonPad, end = moreButtonPad),
                            size = moreButtonSize,
                        )
                    }

                    // 多选模式：左上角选中指示（圆形勾选）
                    if (isMultiSelect) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(cornerPad)
                                .size(selectBadgeSize)
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
                                modifier = Modifier.size(selectIconSize),
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
