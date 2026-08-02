package com.nichx.niplayer.feature.home.imageviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.nichx.niplayer.storage.StorageFile

/**
 * 图片查看页：全屏浏览图片，支持双指缩放、双击缩放、多图左右滑动。
 *
 * 替代旧仓库 `ImageViewerActivity`（PhotoView + ViewPager2 + Glide），改为
 * Compose [HorizontalPager] + 自定义 [pointerInput] 缩放手势 + Coil AsyncImage。
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
            uiState.images.isNotEmpty() -> ImagePager(
                images = uiState.images,
                initialPosition = uiState.initialPosition,
                loadImage = viewModel::loadImage,
            )
        }

        // 顶栏返回按钮（半透明，不占布局空间）
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
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

@Composable
private fun ImagePager(
    images: List<StorageFile>,
    initialPosition: Int,
    loadImage: suspend (StorageFile) -> ImageModel?,
) {
    // key 包裹：images 加载完成后重建 PagerState，使 initialPage 生效
    key(images.size, initialPosition) {
        val pagerState = rememberPagerState(initialPage = initialPosition) { images.size }

        HorizontalPager(state = pagerState) { page ->
            ZoomableImagePage(
                file = images[page],
                loadImage = loadImage,
            )
        }

        // 底部页码指示器（仅多图时显示）
        if (images.size > 1) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }
        }
    }
}

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
            // 双击缩放
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
            loadError -> Text("加载失败", color = Color.White.copy(alpha = 0.7f))
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

/** 最大缩放倍数。 */
private const val MAX_SCALE = 5f
