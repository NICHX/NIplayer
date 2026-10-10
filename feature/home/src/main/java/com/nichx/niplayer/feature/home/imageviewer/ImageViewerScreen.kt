package com.nichx.niplayer.feature.home.imageviewer

import com.nichx.niplayer.feature.home.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.SurfaceHolder
import android.view.SurfaceView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.nichx.niplayer.common.media.MediaFileTypes
import com.nichx.niplayer.player.kernel.PlaybackState
import com.nichx.niplayer.storage.StorageFile
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 媒体查看页：全屏浏览同一目录下的图片与视频，支持双指缩放、双击缩放、左右滑动。
 *
 * 图片基于 Compose [HorizontalPager] + 自定义 [pointerInput] 缩放手势 + Coil AsyncImage 渲染；
 * 当 [com.nichx.niplayer.datastore.ExperimentalSettings.viewerVideoEnabled] 开启时，
 * 同目录的视频会与图片按名称混排，滑到视频页即用内嵌 [com.nichx.niplayer.player.kernel.NxPlayer]
 * 自动静音播放，并提供浮层控件（播放/暂停、进度、静音；轻点画面显隐、播放中自动隐藏）。
 *
 * 缩放方案：不引入 PhotoView / telephoto 等第三方库，用 Compose 原生
 * [awaitEachGesture] + [calculateZoom] / [calculatePan] + [graphicsLayer] 实现：
 * - 双指缩放（1x ~ 5x）
 * - 双击在 1x / 3x 间切换
 * - 缩放 > 1x 时单指拖拽平移（消费事件阻止 Pager 切页）
 * - 缩放回 1x 时自动重置偏移
 *
 * 图片加载由 [ImageViewerViewModel.loadImage] 按存储协议分流：
 * - Local / DocumentFile / WebDAV → URL（Coil 直接加载，WebDAV 带认证头）
 * - SMB → ByteArray（ViewModel 内 LruCache 缓存）
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    onBack: () -> Unit = {},
    viewModel: ImageViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorText(uiState.error!!)
            uiState.media.isNotEmpty() -> MediaPager(
                media = uiState.media,
                initialPosition = uiState.initialPosition,
                viewModel = viewModel,
            )
        }

        // 顶栏返回按钮（半透明；加 statusBarsPadding 避开系统状态栏点按/滚顶手势，否则点不到）
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorText(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

/**
 * 图片/视频混合 Pager。
 *
 * 当前页为视频时，该页渲染 [VideoPlayerLayer]（含渲染表面 + 简易控件）；
 * 其余视频页只渲染 [VideoThumbnailPage] 占位图（不挂载表面，避免多表面互相顶替）。
 * 因此任一时刻最多只有一个渲染表面，天然规避表面竞态。
 */
@Composable
private fun MediaPager(
    media: List<StorageFile>,
    initialPosition: Int,
    viewModel: ImageViewerViewModel,
) {
    // key 包裹：media 加载完成后重建 PagerState，使 initialPage 生效
    key(media.size, initialPosition) {
        val pagerState = rememberPagerState(initialPage = initialPosition) { media.size }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState) { page ->
                val file = media[page]
                if (MediaFileTypes.isVideoFile(file.name)) {
                    if (page == pagerState.currentPage) {
                        VideoPlayerLayer(
                            file = file,
                            viewModel = viewModel,
                        )
                    } else {
                        VideoThumbnailPage(
                            file = file,
                            loadThumbnail = viewModel::loadVideoThumbnail,
                        )
                    }
                } else {
                    ZoomableImagePage(
                        file = file,
                        loadImage = viewModel::loadImage,
                    )
                }
            }

            val currentFile = media.getOrNull(pagerState.currentPage)
            val currentIsVideo = currentFile != null && MediaFileTypes.isVideoFile(currentFile.name)

            // 底部页码指示器（多项且非视频页时显示；视频页底部由控制面板承载，避免遮挡）
            if (media.size > 1 && !currentIsVideo) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${media.size}",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
            }
        }
    }
}

/**
 * 非当前页的视频占位：显示缓存缩略图（若有）与居中播放徽标，不挂载渲染表面。
 */
@Composable
private fun VideoThumbnailPage(
    file: StorageFile,
    loadThumbnail: suspend (StorageFile) -> String?,
) {
    var thumbPath by remember(file.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.path) {
        thumbPath = loadThumbnail(file)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        thumbPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.image_viewer_video_badge),
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(56.dp),
        )
    }
}

/**
 * 当前视频页的播放层：渲染表面（按视频比例适配）+ 浮层控件。
 *
 * 进入本页自动静音播放，离开本页暂停并由表面销毁回调解绑。
 * 浮层控件交互：
 * - 轻点画面：切换控件显隐
 * - 播放中静置 [CONTROLS_AUTO_HIDE_MS] 后自动隐藏；暂停/出错时保持显示
 */
@Composable
private fun VideoPlayerLayer(
    file: StorageFile,
    viewModel: ImageViewerViewModel,
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val videoSize by viewModel.videoSize.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()

    // 控件显隐：进入该视频页默认显示；轻点画面切换
    var controlsVisible by remember(file.path) { mutableStateOf(true) }

    DisposableEffect(file.path) {
        viewModel.onEnterVideo(file)
        onDispose { viewModel.onLeaveVideo() }
    }

    // 播放中且控件可见时空转计时，超时自动隐藏；一旦暂停/缓冲/出错即取消
    LaunchedEffect(controlsVisible, state) {
        if (controlsVisible && state is PlaybackState.Playing) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 轻点画面切换控件显隐（不消费拖拽，左右滑动仍可翻页）
            .pointerInput(file.path) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val containerAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
            val videoAspect = if (videoSize.isValid) videoSize.aspectRatio else 16f / 9f
            val surfaceModifier = if (videoAspect >= containerAspect) {
                Modifier.fillMaxWidth().aspectRatio(videoAspect)
            } else {
                Modifier.fillMaxHeight().aspectRatio(videoAspect)
            }
            AndroidView(
                modifier = surfaceModifier,
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                viewModel.onSurfaceAvailable(file.path, holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                viewModel.onSurfaceDestroyed(file.path)
                            }
                        })
                    }
                },
            )
        }

        when (state) {
            is PlaybackState.Buffering ->
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            is PlaybackState.Error -> Text(
                text = stringResource(R.string.image_viewer_video_error),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center),
            )
            // 枚举穷尽化：其余状态无需额外覆盖层
            is PlaybackState.Idle,
            is PlaybackState.Ready,
            is PlaybackState.Playing,
            is PlaybackState.Paused,
            is PlaybackState.Ended -> Unit
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(CONTROLS_FADE_MS)),
            exit = fadeOut(tween(CONTROLS_FADE_MS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 底部渐变遮罩：提升控件在亮画面上的可读性
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ),
                )

                // 顶部右侧：静音开关
                FrostIconButton(
                    icon = if (muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (muted) R.string.image_viewer_unmute else R.string.image_viewer_mute,
                    ),
                    onClick = viewModel::toggleMute,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                )

                VideoControls(
                    state = state,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onTogglePlay = viewModel::togglePlayPause,
                    onSeek = viewModel::seekTo,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * 视频浮层控件：圆角磨砂面板内单行「播放/暂停 · 当前时间 · 进度条 · 总时长」。
 *
 * 进度条样式对齐完整播放器的 `PlayerProgressBar`；上一个/下一个通过左右滑动完成，故不设按钮。
 */
@Composable
private fun VideoControls(
    state: PlaybackState,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    val positionFraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val isPlaying = state is PlaybackState.Playing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
            .frostPanel(RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.image_viewer_pause else R.string.image_viewer_play,
                ),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(positionMs),
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(8.dp))
        ViewerProgressBar(
            positionFraction = positionFraction,
            onSeek = { fraction -> onSeek((fraction * duration).toLong()) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(durationMs),
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * 查看器内的视频进度条，视觉对齐完整播放器的 `PlayerProgressBar`：
 * 4dp 轨道 + 主题色已播放段 + 三层圆点滑块，整体 20dp 触摸高度；按下即 seek，可拖动。
 */
@Composable
private fun ViewerProgressBar(
    positionFraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeight = 4.dp
    val thumbRadius = 8.dp
    val primary = MaterialTheme.colorScheme.primary

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val displayFraction = if (isDragging) dragFraction else positionFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight + thumbRadius * 2)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    dragFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(dragFraction)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            isDragging = false
                            break
                        }
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(dragFraction)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .height(trackHeight),
        ) {
            val w = size.width
            val h = size.height
            val corner = h / 2

            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(corner, corner),
            )

            val display = displayFraction.coerceIn(0f, 1f)
            if (display > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(primary, primary),
                        startX = 0f,
                        endX = w * display,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(w * display, h),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }

            val thumbX = w * display
            drawCircle(
                color = Color.White,
                radius = thumbRadius.toPx() - 1.dp.toPx(),
                center = Offset(thumbX, h / 2f),
            )
            drawCircle(
                color = primary,
                radius = thumbRadius.toPx() - 2.dp.toPx(),
                center = Offset(thumbX, h / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = (thumbRadius - 3.dp).toPx(),
                center = Offset(thumbX, h / 2f),
            )
        }
    }
}

/** 圆形磨砂图标按钮：暗色半透明底 + 发丝描边，用于视频浮层。 */
@Composable
private fun FrostIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerSize: Dp = 44.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.32f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 视频浮层的磨砂面板：暗色半透明底 + 发丝描边（沿用应用玻璃语言）。 */
private fun Modifier.frostPanel(shape: RoundedCornerShape): Modifier = this
    .clip(shape)
    .background(Color.Black.copy(alpha = 0.45f), shape)
    .border(1.dp, Color.White.copy(alpha = 0.14f), shape)

/**
 * 单页可缩放图片。
 *
 * 手势优先级：
 * 1. [detectTapGestures]（双击缩放）— 先处理，不消费单指拖拽事件
 * 2. [awaitEachGesture]（双指缩放 + 单指平移）— scale > 1 时消费 pan 事件阻止 Pager 切页
 */
@Composable
private fun ZoomableImagePage(
    file: StorageFile,
    loadImage: suspend (StorageFile) -> ImageModel?,
) {
    var imageModel by remember(file.path) { mutableStateOf<ImageModel?>(null) }
    var isLoading by remember(file.path) { mutableStateOf(true) }
    var loadError by remember(file.path) { mutableStateOf(false) }

    LaunchedEffect(file.path) {
        isLoading = true
        loadError = false
        imageModel = loadImage(file)
        isLoading = false
        if (imageModel == null) loadError = true
    }

    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offsetX by remember(file.path) { mutableFloatStateOf(0f) }
    var offsetY by remember(file.path) { mutableFloatStateOf(0f) }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(file.path) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 3f
                        }
                    },
                )
            }
            // 双指缩放 + 单指平移（scale > 1 时消费事件阻止 Pager 切页）
            .pointerInput(file.path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()

                        if (zoom != 1f) {
                            scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            event.changes.forEach { it.consume() }
                        }
                        if (scale > 1f && pan != Offset.Zero) {
                            offsetX += pan.x
                            offsetY += pan.y
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = Color.White)
            loadError -> Text(stringResource(R.string.image_viewer_load_error), color = Color.White.copy(alpha = 0.7f))
            imageModel != null -> {
                val coilModel = when (val m = imageModel!!) {
                    is ImageModel.Url -> {
                        if (m.headers.isEmpty()) {
                            m.url
                        } else {
                            val headers = NetworkHeaders.Builder()
                                .apply { m.headers.forEach { (k, v) -> set(k, v) } }
                                .build()
                            ImageRequest.Builder(context)
                                .data(m.url)
                                .httpHeaders(headers)
                                .build()
                        }
                    }
                    is ImageModel.Bytes -> m.bytes
                }

                AsyncImage(
                    model = coilModel,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )
            }
        }
    }
}

/** 把毫秒格式化为 `mm:ss`（超过 1 小时为 `h:mm:ss`）。 */
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 最大缩放倍数。 */
private const val MAX_SCALE = 5f

/** 视频浮层控件淡入淡出时长（ms）。 */
private const val CONTROLS_FADE_MS = 200

/** 播放中视频浮层控件自动隐藏的静置时长（ms）。 */
private const val CONTROLS_AUTO_HIDE_MS = 3_500L
