package com.nichx.niplayer.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.nichx.niplayer.designsystem.theme.NiExtraColors

/**
 * 文字占位 model，供 [NiVideoThumbnail] 在无缩略图时显示文件名首字。
 *
 * BUG-38 修复：SMB/WebDAV/FTP 缩略图异步生成期间（可能数秒）英雄卡无视觉内容，
 * 改为显示文件名首字占位。UI 层（如 [NiHeroResumeCard]）传入本类实例，
 * [NiVideoThumbnail] 通过 instanceof 判断渲染为 Text 而非 AsyncImage。
 *
 * 占位样式：居中大号水印字母（衬托中央播放按钮，避免大卡片大面积留白；
 * 白色低透明度保证深浅色模式下对比度一致）+ 左上角状态标签（[label]）。
 *
 * [label] 用于区分播放状态：英雄卡（已播放但缩略图缺失/生成失败）传
 * "无缩略图"；未播放过的条目不传 label，仅显示水印字母。
 */
data class PlaceholderText(val text: String, val label: String? = null)

/**
 * 视频/音频缩略图：有 model 时用 [AsyncImage] 加载；为 null 时纯渐变占位（无图标，避免与父级播放按钮重叠）。
 *
 * BUG-3 修复：新增 [contentScale] 参数，调用方可按媒体类型选择裁剪策略——
 * 视频用 [ContentScale.Crop]（填满 16:9），音频用 [ContentScale.Fit]（保留方形封面完整比例）。
 * 默认保持 Crop 以兼容既有调用方。
 *
 * BUG-38 修复：支持 [PlaceholderText] 文字占位 model，SMB/WebDAV 无缩略图缓存时
 * 显示文件名首字，避免英雄卡空白。Coil 不识别 PlaceholderText 类型会报错，
 * 所以这里单独 instanceof 判断渲染为 Text 而非交给 AsyncImage。
 *
 * W-N6 修复：禁用 Coil 磁盘缓存。缩略图本身已由 [ThumbnailManager] 写入
 * `cacheDir/video_cover/`（带 LRU 淘汰），Coil 再缓存一份解码后的 bitmap 到
 * `cacheDir/image_cache/` 会导致双倍磁盘占用。内存缓存仍开启（[memoryCachePolicy]
 * 保持 ENABLED），覆盖"滚动回到已查看缩略图"场景。
 */
@Composable
fun NiVideoThumbnail(
    model: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(NiExtraColors.current.thumbnailPlaceholder),
    ) {
        when (model) {
            null -> { /* 纯渐变占位 */ }
            is PlaceholderText -> {
                // BUG-38：无缩略图占位。居中大号水印字母衬托播放按钮，
                // 左上角可选状态标签（[PlaceholderText.label]，区分播放状态）
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = model.text,
                        color = Color.White.copy(alpha = 0.16f),
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (model.label != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = model.label,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            else -> {
                // W-N6 修复：包装为 ImageRequest 禁用磁盘缓存
                // model 可能是 String（文件路径）、ImageRequest、或其他类型
                val request = when (model) {
                    is String -> {
                        // 缩略图更新后路径会追加 ?t=timestamp 作 cache-buster，
                        // 实际加载文件时去掉查询参数，但用完整字符串作 memoryCacheKey 绕过旧缓存
                        val queryIdx = model.indexOf("?t=")
                        val actualPath = if (queryIdx >= 0) model.substring(0, queryIdx) else model
                        ImageRequest.Builder(LocalContext.current)
                            .data(actualPath)
                            .memoryCacheKey(model)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                    }
                    is ImageRequest -> model.newBuilder()
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    else -> model  // 其他类型（如 ByteArray）原样传递
                }
                ThumbnailImage(request = request, contentScale = contentScale)
            }
        }
    }
}

/**
 * 无闪烁缩略图：用 [rememberAsyncImagePainter] 并保留最近一次成功解码的 [Painter]，
 * 当缩略图更新导致 Coil key 变化（memoryCacheKey 追加 ?t= 作 cache-buster）时，
 * 上一张图继续显示、新图就绪后经 [Crossfade] 淡入替换，避免"旧图 → 空白 → 新图"的闪烁。
 */
@Composable
private fun ThumbnailImage(request: Any, contentScale: ContentScale) {
    val painter = rememberAsyncImagePainter(model = request)
    val state by painter.state.collectAsState()
    var lastPainter by remember { mutableStateOf<Painter?>(null) }
    val successPainter = (state as? AsyncImagePainter.State.Success)?.painter
    // state 切回 Loading（缩略图更新重载）期间保持 lastPainter 显示旧图，不露空白
    LaunchedEffect(successPainter) {
        if (successPainter != null) lastPainter = successPainter
    }
    Crossfade(
        targetState = successPainter ?: lastPainter,
        animationSpec = tween(220),
        label = "thumbnail",
    ) { painterOrNull ->
        if (painterOrNull != null) {
            Image(
                painter = painterOrNull,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
