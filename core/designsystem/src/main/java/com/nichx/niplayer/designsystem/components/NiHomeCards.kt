package com.nichx.niplayer.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nichx.niplayer.designsystem.R
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.designsystem.theme.NiMotion

/**
 * 英雄续播卡：16:9 缩略图 + 渐变 overlay + 播放按钮 + 底部信息 + 续播进度条。
 */
@Composable
fun NiHeroResumeCard(
    title: String,
    durationText: String,
    positionText: String,
    modifier: Modifier = Modifier,
    thumbnailModel: Any? = null,
    progressFraction: Float = 0f,
    // BUG-3 修复：允许调用方按媒体类型选择裁剪策略（音频 Fit / 视频 Crop）
    contentScale: ContentScale = ContentScale.Crop,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "heroScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "heroAlpha",
    )

    val overlayBg = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.35f to Color.Black.copy(alpha = 0.3f),
            0.7f to Color.Black.copy(alpha = 0.6f),
            1f to Color.Black.copy(alpha = 0.85f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        NiVideoThumbnail(
            model = thumbnailModel,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayBg),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.action_play),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$positionText / $durationText",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            if (progressFraction > 0f) {
                Spacer(Modifier.height(6.dp))
                NiProgressTrack(
                    fraction = progressFraction,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }
        }
    }
}

/**
 * 横向最近播放缩略图卡：16:9 缩略图 + 播放按钮 + 时长角标 + 文件名 + 续播进度条。
 * 按压有缩放+alpha 反馈。
 *
 * 当 [horizontal] = true 时切换为横向列表布局（88×50dp 缩略图 + 标题 + 副信息 + 媒体标签），
 * 用于 [PlayHistoryScreen] 等全宽列表场景。
 */
@Composable
fun NiThumbCard(
    title: String,
    durationText: String,
    modifier: Modifier = Modifier,
    thumbnailModel: Any? = null,
    progressFraction: Float = 0f,
    // BUG-3 修复：允许调用方按媒体类型选择裁剪策略（音频 Fit / 视频 Crop）
    contentScale: ContentScale = ContentScale.Crop,
    onClick: () -> Unit = {},
    horizontal: Boolean = false,
    subtitleText: String? = null,
    mediaLabel: String? = null,
    squareCover: Boolean = false,
    // 大屏网格场景下让卡片填满列宽，替代固定 160dp 宽
    fillWidth: Boolean = false,
    // 横向滚动行内自定义卡片宽度（fillWidth = false 时生效），默认按 squareCover 取 160/120dp
    cardWidth: Dp = Dp.Unspecified,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "thumbScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "thumbAlpha",
    )

    val verticalCardWidth = if (squareCover) 120.dp else 160.dp

    if (horizontal) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp))
                .background(NiExtraColors.current.surfaceLevel2)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (squareCover) 50.dp else 88.dp, 50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NiExtraColors.current.surfaceLevel2),
            ) {
                if (thumbnailModel != null) {
                    NiVideoThumbnail(
                        model = thumbnailModel,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                    if (contentScale != ContentScale.Fit) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                NiAutoSizeText(
                    text = title,
                    maxLines = 2,
                    minFontSize = 12.sp,
                    maxFontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (mediaLabel != null) {
                        Text(
                            text = mediaLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (progressFraction > 0f) {
                    Spacer(Modifier.height(6.dp))
                    NiProgressTrack(fraction = progressFraction)
                }
            }
        }
    } else {
        // 竖向卡片：缩略图 + 信息区。 redesign：去掉中央播放按钮（卡片本身可点），
        // 渐变只在底部弱化以托住时长角标，标题改为 2 行避免长名截断，
        // 进度条贴在缩略图底部形成"续播"视觉暗示。
        val overlayBg = remember {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f),
            )
        }

        Column(
            modifier = modifier
                .then(
                    if (fillWidth) Modifier.fillMaxWidth()
                    else Modifier.width(if (cardWidth != Dp.Unspecified) cardWidth else verticalCardWidth),
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp))
                .background(NiExtraColors.current.surfaceLevel2)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (squareCover) 1f else 16f / 9f),
            ) {
                NiVideoThumbnail(
                    model = thumbnailModel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
                // 底部渐变 only，托住角标与进度条
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayBg),
                )
                // 中央播放按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // 存储源标签：左上角
                if (mediaLabel != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 6.dp, top = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = mediaLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // 时长角标：右下角
                if (durationText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                // 进度条贴在缩略图底部
                if (progressFraction > 0f) {
                    NiProgressTrack(
                        fraction = progressFraction,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                    )
                }
            }
            // 标题：2 行，超长时自动缩字到 11sp，仍超出则省略
            NiAutoSizeText(
                text = title,
                maxLines = 2,
                minFontSize = 11.sp,
                maxFontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 快速访问网格卡：图标 + 名称 + 存储源。按压有缩放反馈。
 */
@Composable
fun NiQuickAccessGridItem(
    name: String,
    sourceText: String,
    isDirectory: Boolean,
    modifier: Modifier = Modifier,
    isValid: Boolean = true,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = NiMotion.DURATION_MICRO),
        label = "qaScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(NiExtraColors.current.surfaceLevel2)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        val icon: ImageVector = if (isDirectory) Icons.Filled.Folder
        else Icons.AutoMirrored.Filled.InsertDriveFile
        val iconBg = if (isValid) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
        val iconFg = if (isValid) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconFg,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = if (isValid) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sourceText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 自动缩字文本：超出 [maxLines] 时逐步缩小字号到 [minFontSize]，
 * 仍超出则末尾省略。用于文件名等长文本场景，避免固定 2 行仍显示不全。
 *
 * 文本变化时字号重置为 [maxFontSize] 重新测量，保证短文本回到正常字号。
 */
@Composable
fun NiAutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minLines: Int = 1,
    minFontSize: TextUnit = 11.sp,
    maxFontSize: TextUnit = 14.sp,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        minLines = minLines,
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) {
                val shrunk = (fontSize.value - 1f).sp
                fontSize = if (shrunk < minFontSize) minFontSize else shrunk
            }
        },
    )
}
