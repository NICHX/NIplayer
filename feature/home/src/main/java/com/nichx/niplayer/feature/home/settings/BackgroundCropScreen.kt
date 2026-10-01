package com.nichx.niplayer.feature.home.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.feature.home.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 裁剪时用户缩放下限（1 = 恰好铺满裁剪视口）。 */
private const val MIN_USER_SCALE = 1f

/** 裁剪时用户缩放上限。 */
private const val MAX_USER_SCALE = 5f

/**
 * 自定义背景裁剪页：全屏黑底，图片按「铺满视口」为基础，支持拖动与双指缩放，
 * 视口即最终输出画面（与屏幕比例一致，作为全屏背景无需再二次裁切）。
 *
 * 交互与坐标推导：图片以 [ContentScale.Fit] 绘制满视口并居中，再经 [graphicsLayer] 叠加
 * `coverScale/fitScale` 的基础缩放与用户缩放/位移。视口左上角映射回原图像素坐标即得裁剪矩形。
 *
 * @param source 待裁剪的原始位图（软件位图）
 * @param onCancel 取消裁剪
 * @param onConfirm 确认裁剪，回传裁剪后的位图
 */
@Composable
fun BackgroundCropScreen(
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val imageBitmap = remember(source) { source.asImageBitmap() }
    val imageWidth = source.width.toFloat()
    val imageHeight = source.height.toFloat()

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var userScale by remember { mutableFloatStateOf(MIN_USER_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val viewportWidth = viewport.width.toFloat()
    val viewportHeight = viewport.height.toFloat()
    val measured = imageWidth > 0f && imageHeight > 0f && viewportWidth > 0f && viewportHeight > 0f

    // 铺满视口所需的基础缩放（Crop）；Fit 基础缩放用于把图层缩放换算到 Fit 画布上
    val coverScale = if (measured) max(viewportWidth / imageWidth, viewportHeight / imageHeight) else 1f
    val fitScale = if (measured) min(viewportWidth / imageWidth, viewportHeight / imageHeight) else 1f
    val totalScale = coverScale * userScale
    val layerScale = if (fitScale > 0f) totalScale / fitScale else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(viewport) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newUserScale = (userScale * zoom).coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
                        val newTotal = coverScale * newUserScale
                        // 位移钳制：保证图片始终覆盖满视口，视口内不会露出黑边
                        val maxX = ((imageWidth * newTotal - viewportWidth) / 2f).coerceAtLeast(0f)
                        val maxY = ((imageHeight * newTotal - viewportHeight) / 2f).coerceAtLeast(0f)
                        userScale = newUserScale
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = layerScale
                    scaleY = layerScale
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        Text(
            text = stringResource(R.string.theme_custom_background_crop_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 20.dp, start = 24.dp, end = 24.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel), color = Color.White)
            }
            TextButton(
                onClick = {
                    if (measured) {
                        onConfirm(cropBitmap(source, totalScale, offset, viewportWidth, viewportHeight))
                    }
                },
            ) {
                Text(text = stringResource(R.string.confirm), color = Color.White)
            }
        }
    }
}

/** 按当前缩放/位移把视口映射回原图坐标并裁剪出对应区域。 */
private fun cropBitmap(
    source: Bitmap,
    totalScale: Float,
    offset: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): Bitmap {
    val left = (source.width / 2f - (viewportWidth / 2f + offset.x) / totalScale)
        .roundToInt()
        .coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val top = (source.height / 2f - (viewportHeight / 2f + offset.y) / totalScale)
        .roundToInt()
        .coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val width = (viewportWidth / totalScale).roundToInt().coerceIn(1, source.width - left)
    val height = (viewportHeight / totalScale).roundToInt().coerceIn(1, source.height - top)
    return Bitmap.createBitmap(source, left, top, width, height)
}
