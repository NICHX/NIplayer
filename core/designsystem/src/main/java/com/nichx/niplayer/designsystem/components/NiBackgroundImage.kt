package com.nichx.niplayer.designsystem.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * 自定义背景图绘图层。
 *
 * 与旧实现（`AsyncImage` + `graphicsLayer { alpha }`）相比，本组件避免每帧重建图片请求与图层：
 * - 背景文件**只解码一次**，按屏幕长边降采样，结果按 `(路径, 目标尺寸)` 缓存；
 * - 用 [Image] 单次绘制，不透明度作为绘制参数传入（不产生额外 RenderNode / 离屏层）。
 *
 * 背景图会被绘制在玻璃 backdrop 的捕获层内（供底栏等玻璃浮层采样），旧实现额外叠加的
 * 独立图层与每帧请求是自定义背景引入滚动掉帧的原因之一。
 *
 * @param imagePath 背景图片本地文件绝对路径
 * @param opacity   背景不透明度（0..1）
 */
@Composable
fun NiBackgroundImage(
    imagePath: String,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val containerSize = LocalWindowInfo.current.containerSize
    // 目标解码边长：屏幕长边即可，避免按原始大图（可能数千万像素）解码占用内存与带宽。
    // 窗口尺寸尚未测量时用 1080 兜底，避免先解出一张 1px 的废图再重解码。
    val targetSize = remember(containerSize) {
        val longest = max(containerSize.width, containerSize.height)
        if (longest > 0) longest else FALLBACK_TARGET_SIZE
    }
    val key = "$imagePath#$targetSize"
    var bitmap by remember(key) { mutableStateOf(BackgroundBitmapCache.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null) {
            val decoded = withContext(Dispatchers.IO) { decodeCapped(imagePath, targetSize) }
            if (decoded != null) {
                BackgroundBitmapCache.put(key, decoded)
                bitmap = decoded
            }
        }
    }
    val image = bitmap ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = opacity.coerceIn(0f, 1f),
        modifier = modifier,
    )
}

private const val FALLBACK_TARGET_SIZE = 1080

/** 同一背景的解码结果缓存（容量很小：通常只有一张当前背景图）。 */
private object BackgroundBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(2)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * 按 [maxDimension]（长边上限）降采样解码图片文件；文件不存在或解码失败返回 null。
 */
private fun decodeCapped(path: String, maxDimension: Int): ImageBitmap? {
    return try {
        if (!File(path).exists()) return null
        // 第一遍只读取尺寸，不分配像素内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
