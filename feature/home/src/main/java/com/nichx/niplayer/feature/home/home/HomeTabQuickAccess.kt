package com.nichx.niplayer.feature.home.home

import com.nichx.niplayer.feature.home.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nichx.niplayer.designsystem.components.NiAutoSizeText
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion
import com.nichx.niplayer.common.media.MediaFileTypes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nichx.niplayer.feature.home.quickaccess.QuickAccessUiItem


/**
 * 快速访问横向滚动行（杂志式布局用）：
 * 固定宽度 16:9 磁贴横向滚动，与最近播放行的卡片尺寸协调。
 */
@Composable
internal fun HomeQuickAccessLazyRow(
    items: List<QuickAccessUiItem>,
    thumbnailUrls: Map<String, String>,
    storageReachability: Map<Int, Boolean>,
    onItemClick: (QuickAccessUiItem) -> Unit,
    edgePadding: Dp = 0.dp,
    cardWidth: Dp = 200.dp,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = edgePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.qaThumbKey }) { qaItem ->
            val effectiveValid = qaItem.libraryValid &&
                storageReachability[qaItem.entity.libraryId] != false
            HomeQuickAccessGridItem(
                item = qaItem,
                thumbnailUrl = thumbnailUrls[qaItem.qaThumbKey],
                isValid = effectiveValid,
                onClick = { onItemClick(qaItem) },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

@Composable
internal fun HomeQuickAccessGridItem(
    item: QuickAccessUiItem,
    thumbnailUrl: String?,
    isValid: Boolean = item.libraryValid,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "homeQaScale",
    )

    val cardShape = RoundedCornerShape(16.dp)
    val name = item.entity.name
    val isVideo = !item.entity.isDirectory && MediaFileTypes.isVideoFile(name)
    val isAudio = !item.entity.isDirectory && MediaFileTypes.isAudioFile(name)
    val isImage = !item.entity.isDirectory && MediaFileTypes.isImageFile(name)
    val hasThumbnail = thumbnailUrl != null && (isVideo || isAudio || isImage)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 语义合并：封面/名称合并为单一节点，降低语义树节点数
            .semantics(mergeDescendants = true) {}
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(elevation = 1.dp, shape = cardShape, clip = false)
                .clip(cardShape)
                .background(NiExtraColors.current.surfaceLevel3)
                .clickable(
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
                val thumbBg = if (isAudio)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else NiExtraColors.current.surfaceLevel3
                Box(
                    modifier = Modifier.fillMaxSize().background(thumbBg),
                ) {
                    if (hasThumbnail) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
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
        }
        Spacer(Modifier.height(4.dp))
        NiAutoSizeText(
            text = item.entity.name,
            maxLines = 2,
            minFontSize = 11.sp,
            maxFontSize = 13.sp,
            color = if (isValid) MaterialTheme.colorScheme.onSurface
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

