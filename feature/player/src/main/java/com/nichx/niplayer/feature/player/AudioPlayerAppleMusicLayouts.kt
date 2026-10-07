package com.nichx.niplayer.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Apple Music 风格音频播放器的视觉件。
 *
 * 参照 BitChord 的「正在播放」竖屏：
 * - 背景：封面自身的 6×6 颜色网格（CPU 插值到 32×32），接缝对齐封面底边；
 * - 封面：**全宽贴顶**，底部 42% 溶解进网格；
 * - 进度条：无滑块的发丝胶囊，按住时变粗；
 * - 歌词：左对齐 34sp ExtraBold 纯白，当前行带辉光，未唱到的字更暗。
 */
private val AppleArtworkCorner = 8.dp

/** 构建封面 [ImageRequest]（禁用磁盘缓存，与其它布局保持一致）。 */
private fun appleCoverRequest(
    context: Context,
    coverData: Any,
    sizePx: Int? = null,
): ImageRequest {
    val builder = when (coverData) {
        is ImageRequest -> coverData.newBuilder()
        else -> ImageRequest.Builder(context).data(coverData)
    }
    builder.diskCachePolicy(CachePolicy.DISABLED)
    if (sizePx != null) builder.size(sizePx)
    return builder.build()
}

/** 网格单元数：够保留封面的构图，又不足以看清任何细节。 */
private const val APPLE_MESH_GRID = 6

/**
 * 网格在 CPU 上先插值到的纹理边长。
 *
 * 直接拿 6×6 交给 GPU 双线性过滤虽平滑但不“柔”——分段线性的折痕在格子相距
 * 一百多像素时肉眼可见；先用两端平缓的曲线重采样一次，放大后就看不到折痕，
 * 也就不需要整屏 RenderEffect 模糊。
 */
private const val APPLE_MESH_TEX = 32

/**
 * 网格的解码尺寸，取 [APPLE_MESH_GRID] 的整数倍，保证每格取样均匀。
 *
 * 只需要 6×6 的均色，72px 足够（每格约 12×12 像素）；解码越小，进入播放器/切歌时越省。
 */
private const val APPLE_MESH_DECODE_PX = 72
private const val APPLE_MESH_VIBRANCE = 1.12f
private const val APPLE_MESH_FLOOR = 0.045f
/** 网格换色（切歌）时的交叉淡入时长。 */
private const val APPLE_MESH_FADE_MS = 900
private const val APPLE_MESH_SCRIM_TOP = 0.05f
private const val APPLE_MESH_SCRIM_BOTTOM = 0.42f

/** 还没读到封面颜色时的底色，不是任何人选的颜色。 */
private val AppleBackdropFallback = Color(0xFF121212)

/** 封面底部向上溶解进背景的比例：比参考更紧一些，让封面主体保持清晰。 */
private const val APPLE_COVER_DISSOLVE_FRACTION = 0.34f

/** 会话内最近的封面网格（每个 32×32 ARGB 仅 4KB）。 */
private val appleMeshCache = object : LinkedHashMap<String, ImageBitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>) = size > 8
}

/**
 * Apple Music 风格背景：**封面自身的颜色网格**，不用整屏 RenderEffect 模糊。
 *
 * 参照 BitChord 的 ArtworkMeshBackdrop：把封面平均成 6×6 的网格——第 0 行就是
 * 封面的底边，其余行翻转后整体横向循环移位（避免逐列对齐的镜面感）——再在
 * CPU 上平滑插值到 32×32。整屏只画两张小图 + 一层压暗，切歌时颜色交叉淡入。
 *
 * @param seam 封面底边在屏幕上的位置；网格第 0 行对齐到这里，接缝处没有断色。
 */
@Composable
internal fun AppleArtworkMeshBackdrop(
    mesh: ImageBitmap?,
    seam: Dp,
    modifier: Modifier = Modifier,
) {
    // 屏上的网格与正在淡入的网格分开持有：交叉淡入的是一对位图，不是一对颜色，
    // 旧的那张要一直画到淡完为止。
    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(mesh) {
        val next = mesh ?: return@LaunchedEffect
        // 上一次淡入还没结束就来了新的一版：已经画在屏上的那张才是该被淡出的起点。
        incoming?.let { shown = it }
        incoming = null
        val current = shown
        if (next === current) return@LaunchedEffect
        if (current == null) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(APPLE_MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }
    Canvas(modifier = modifier.fillMaxSize().background(AppleBackdropFallback)) {
        val seamY = seam.toPx().coerceIn(0f, size.height)
        shown?.let { drawAppleMesh(it, seamY, 1f) }
        // 在 draw 里读 Animatable：只让绘制失效，不带上组合。
        incoming?.let { drawAppleMesh(it, seamY, fade.value) }
        // 只压到白字可读为止：封面本身深的地方就该留深。
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = APPLE_MESH_SCRIM_TOP),
                    Color.Black.copy(alpha = APPLE_MESH_SCRIM_BOTTOM),
                ),
            ),
        )
    }
}

/**
 * 把 [image] 铺满整个 surface：接缝以下铺网格，接缝以上铺同一张网格的**镜像**。
 *
 * 接缝以上如果只把第 0 行拉长，会得到一条垂直方向毫无细节的拖影——封面在这里
 * 恰好淡出，那条拖影就露在外面，看起来像“照片被抹了一道”。改成把整张网格上下
 * 翻转铺上去，接缝上方就成了它自己的连续延伸：封面淡出的地方接的是一段同色系的
 * 柔和画面，而不是一条被拉直的色带。
 */
private fun DrawScope.drawAppleMesh(image: ImageBitmap, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val width = size.width.roundToInt()
    if (seamY > 0.5f) {
        // 局部坐标 (x, y) → 屏幕 (x, seamY - y)：第 0 行落在接缝上，往上镜像展开。
        withTransform({
            translate(0f, seamY)
            scale(1f, -1f, pivot = Offset.Zero)
        }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(width, seamY.roundToInt()),
                alpha = alpha,
                filterQuality = FilterQuality.Low,
            )
        }
    }
    val below = (size.height - seamY).roundToInt().coerceAtLeast(0)
    if (below > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(0, seamY.roundToInt()),
            dstSize = IntSize(width, below),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * Apple Music 风格的封面：**全宽、贴顶**（一直画到状态栏背后），底部
 * [APPLE_COVER_DISSOLVE_FRACTION] 的比例向上溶解进背景网格，没有硬边。
 */
@Composable
internal fun AppleMusicCoverBanner(
    coverData: Any?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // DstIn 的擦除只能发生在自己的缓冲里。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height * (1f - APPLE_COVER_DISSOLVE_FRACTION),
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        if (coverData != null) {
            val request = remember(coverData) { appleCoverRequest(context, coverData) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 读取 [coverPath] 的封面并生成 [AppleArtworkMeshBackdrop] 用的网格。
 *
 * 封面是从音频里抽出来缓存在本地的文件，所以直接用小尺寸解码而不是走 Coil
 * 的像素回读；同一路径只会算一次，之后从 [appleMeshCache] 取。
 */
@Composable
internal fun rememberAppleArtworkMesh(coverPath: String?): ImageBitmap? {
    var mesh by remember(coverPath) { mutableStateOf(coverPath?.let(appleMeshCache::get)) }
    LaunchedEffect(coverPath) {
        if (coverPath.isNullOrBlank() || mesh != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(coverPath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= APPLE_MESH_DECODE_PX) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(
                    coverPath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        } ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { appleMeshOf(decoded, coverPath.hashCode()) }
        decoded.recycle()
        if (found != null) {
            appleMeshCache[coverPath] = found
            mesh = found
        }
    }
    return mesh
}

/**
 * 把 [source] 平均成网格：每格取均值而不是量化出主色——该出现在封面下方的是
 * 封面的模糊延续，模糊对“哪块颜色更有意思”没有意见。
 */
private fun appleMeshOf(source: Bitmap, seed: Int): ImageBitmap? {
    val width = source.width
    val height = source.height
    if (width < 1 || height < 1) return null
    val cols = APPLE_MESH_GRID.coerceAtMost(width)
    val rows = APPLE_MESH_GRID.coerceAtMost(height)
    val cells = rows * cols
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)

    // 一次读一行：临时缓冲是封面的一行宽，而不是整张图。
    val line = IntArray(width)
    for (y in 0 until height) {
        source.getPixels(line, 0, width, 0, y, width, 1)
        // 读的时候就翻转：网格第 0 行即封面底边（接缝行）。
        val rowBase = ((height - 1 - y) * rows / height) * cols
        for (x in 0 until width) {
            val cell = rowBase + x * cols / width
            val pixel = line[x]
            red[cell] += (pixel shr 16) and 0xFF
            green[cell] += (pixel shr 8) and 0xFF
            blue[cell] += pixel and 0xFF
            count[cell]++
        }
    }

    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        appleArgb(
            (red[cell] / n).toInt(),
            (green[cell] / n).toInt(),
            (blue[cell] / n).toInt(),
        ).appleLifted()
    }
    val texels = grid.appleRotateBelowSeam(cols, rows, seed).appleResample(cols, rows, APPLE_MESH_TEX)
    return Bitmap
        .createBitmap(texels, APPLE_MESH_TEX, APPLE_MESH_TEX, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/**
 * 接缝行之外整体横向循环移位，并随机左右镜像。
 *
 * 只翻转会读成镜子：封面上的脸、logo、地平线会上下颠倒地出现在原来的列上。
 * 逐格乱序又会把网格打碎成马赛克。循环移位保留了每格的邻居关系（色彩过渡
 * 完好），只把过渡发生的位置挪开——刚好够让封面正下方不再对齐封面本身。
 */
private fun IntArray.appleRotateBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

/** 把 [cols]×[rows] 的控制点重采样成 [size]×[size] 的纹理。 */
private fun IntArray.appleResample(cols: Int, rows: Int, size: Int): IntArray {
    val out = IntArray(size * size)
    for (ty in 0 until size) {
        val fy = (ty + 0.5f) / size * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = appleSmoothstep(fy - y0)
        for (tx in 0 until size) {
            val fx = (tx + 0.5f) / size * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = appleSmoothstep(fx - x0)
            val top = appleLerp(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = appleLerp(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * size + tx] = appleLerp(top, bottom, wy)
        }
    }
    return out
}

/** 两端平缓，放大后不会在格子边界留下折痕。 */
private fun appleSmoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun appleLerp(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
    }
    return appleArgb(channel(16), channel(8), channel(0))
}

private fun appleArgb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/** 平均会把一块区域拉灰：补回一点饱和度，并给纯黑托一个下限。 */
private fun Int.appleLifted(): Int {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(this, it) }
    hsl[1] = (hsl[1] * APPLE_MESH_VIBRANCE).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceAtLeast(APPLE_MESH_FLOOR)
    return ColorUtils.HSLToColor(hsl)
}

/**
 * Apple Music 风格专辑封面：方形、8dp 圆角、10dp 投影；**暂停时回缩到 0.86**。
 * 无封面时音符占位。
 */
@Composable
internal fun AppleMusicArtwork(
    coverData: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(AppleArtworkCorner)
    // Apple 招牌：暂停时封面回缩，播放时弹回
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "appleArtworkScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(Color(0xFF1C1C1E)),
    ) {
        if (coverData != null) {
            val request = remember(coverData) { appleCoverRequest(context, coverData) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        // 参考里封面卡带一圈很细的浅色描边（画在图片之上）
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
        )
    }
}

/** Apple Music 风格封面缩略图（歌词页顶部信息条）。 */
@Composable
internal fun AppleMusicThumbnail(coverData: Any?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(AppleArtworkCorner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF1C1C1E)),
    ) {
        if (coverData != null) {
            val request = remember(coverData) { appleCoverRequest(context, coverData, sizePx = 160) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Apple Music 风格歌名 + 艺术家：左对齐、白色，歌名加粗。[compact] 用于歌词页顶部的信息条。 */
@Composable
internal fun AppleTrackTitle(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (compact) {
                MaterialTheme.typography.titleSmall
            } else {
                // 参照 BitChord：歌名 20sp 粗体白
                MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
            },
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 本地文件常常「歌名 = 艺术家 = 文件名」，两行同一句话就是「双层歌名」，
        // 重复的那行没有信息量，直接省掉。
        if (artist.isNotEmpty() && !artist.equals(title, ignoreCase = true)) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artist,
                style = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    // 参照 BitChord：艺术家同为 20sp Medium，透明度 0.55
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                },
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Apple 封面页的悬浮返回按钮（浅色圆底 + 白色箭头）。 */
@Composable
internal fun AppleMusicBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 12.dp, top = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.player_back),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Apple Music 风格的进度区：发丝胶囊进度条（无滑块，按住变粗）
 * + 下方左右两侧的「已播 / -剩余」时间。
 */
@Composable
internal fun AppleProgressSection(
    positionMs: State<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    val currentPositionMs = positionMs.value
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) {
        dragValue
    } else {
        (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    }
    val displayMs = if (dragging) (dragValue * duration).toLong() else currentPositionMs
    val remainingMs = (durationMs - displayMs).coerceAtLeast(0L)

    Column(modifier = modifier.fillMaxWidth()) {
        AppleThinSlider(
            value = fraction,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                if (dragging) {
                    onSeek((dragValue * duration).toLong())
                    dragging = false
                }
            },
        )
        // 进度条的触控区比可见条高得多，时间标签上提贴回条下
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-9).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationShort(displayMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
            Text(
                text = "-" + formatDurationShort(remainingMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/**
 * Apple Music 的发丝胶囊进度条：没有滑块，条本身在按住时变粗、松手回缩。
 * Material 的 Slider 固定带滑块与更高的轨道，画不出这个形状，故直接绘制。
 */
@Composable
private fun AppleThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idleHeight = 7.dp
    val activeHeight = 12.dp
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "appleSliderHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 可见条只有 7dp，触控区放大到条高 + 22dp
            .height(activeHeight + 22.dp)
            // 点按与拖动共用一个手势循环：拆成两个检测器时，拖动检测器会先吃掉指针，
            // 而点按没有拖动量可报告，于是点按永远不生效。
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }
                    dragging = false
                    onValueChangeFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = Color.White.copy(alpha = 0.26f), cornerRadius = radius)
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    size = Size(
                        width = filled.coerceAtLeast(size.height).coerceAtMost(size.width),
                        height = size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }
    }
}
