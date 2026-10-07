package com.nichx.niplayer.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.nichx.niplayer.designsystem.components.NiGeneratedCoverArt
import com.nichx.niplayer.designsystem.theme.MotionTokens
import com.nichx.niplayer.designsystem.theme.NiExtraColors


/**
 * 黑胶主题的全屏背景：主题底色 + 封面主色氛围光 + 主题色 scrim + 四角暗角。
 *
 * 氛围光用 `CoverBackdropSampleSize` 极小分辨率采样封面再拉伸铺满，天然形成柔和
 * 环境光，**不使用** `Modifier.blur`（整屏 RenderEffect 是硬约束禁止项）；
 * scrim 保证前景控件对比度，暗角把视线收拢到唱盘、增强纵深。
 */
@Composable
internal fun BackgroundLayer(coverData: Any?) {
    val background = MaterialTheme.colorScheme.background
    val isDark = NiExtraColors.current.isDark
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(background))
        // 封面变化（切歌）时让环境光淡入淡出，避免背景「啪」地硬切
        Crossfade(
            targetState = coverData,
            animationSpec = tween(MotionTokens.SURFACE, easing = MotionTokens.easeEnter),
            modifier = Modifier.fillMaxSize(),
            label = "vinylBackdrop",
        ) { cover ->
            if (cover != null) {
                val context = LocalContext.current
                val request = remember(cover) {
                    when (cover) {
                        is String -> ImageRequest.Builder(context)
                            .data(cover)
                            .size(CoverBackdropSampleSize, CoverBackdropSampleSize)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                        is ImageRequest -> cover.newBuilder()
                            .size(CoverBackdropSampleSize, CoverBackdropSampleSize)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                        else -> cover
                    }
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isDark) 0.30f else 0.36f),
                )
            }
        }
        // 主题色 scrim：整体压一层、顶/底更重。中部不再「透到底」（原为 0.18）——
        // 歌词与唱盘恰好落在屏幕中部，那正是最需要对比度的地方。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            background.copy(alpha = 0.46f),
                            background.copy(alpha = 0.42f),
                            background.copy(alpha = 0.54f),
                            background.copy(alpha = 0.84f),
                        ),
                    ),
                ),
        )
        // 四角暗角：把视线收拢到唱盘，增强纵深。
        // 用显式 center/radius 的径向渐变，避免依赖 Brush 默认中心/半径的解析行为。
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        background.copy(alpha = if (isDark) 0.72f else 0.52f),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
        }
    }
}

/** 简约封面主题：封面卡圆角。 */
private val CoverCorner = 24.dp

/**
 * 背景光采样边长（px）：以极小分辨率采样封面，再拉伸铺满，天然形成柔和背景光。
 *
 * 这样**不需要** `Modifier.blur`（整屏 RenderEffect）：整屏逐帧重建离屏层是硬约束禁止项。
 *
 * 取 16 而不是更大的值：64px 采样放大后，封面的构图仍然可辨（一张糊掉的人像），
 * 背景会和前景内容抢注意力、整体显得脏；16px 只剩几块大色域，读起来才是
 * 「以封面主色为基调的环境光」——这正是这个主题想要的。
 */
private const val CoverBackdropSampleSize = 16

/**
 * 背景光的降饱和系数。
 *
 * 封面越复杂（多色相、高对比），越需要把颜色收一收：原色铺满整屏时，背景本身
 * 就是一幅画，和前景内容抢注意力，整页读起来就是「花」。收到 0.6 后只剩一层
 * 淡淡的主色倾向，前景的强调色（播放键 / 进度 / 当前歌词）才立得住。
 */
private const val CoverBackdropSaturation = 0.6f

/**
 * 背景光统一压向主题底色的比例（深浅色各一档）。
 *
 * 竖向 scrim 只管「上轻下重」，压不住封面自身的明暗差（比如深色头发贴着浅色脸）；
 * 再补一层**均匀**的，把整体对比度也收下来 —— 这是「复杂封面看着花」的最后一道闸。
 * 浅色档略大：浅底上的深色块比深底上的亮色块更扎眼。
 */
private const val CoverBackdropWashDark = 0.22f
private const val CoverBackdropWashLight = 0.28f

/**
 * 简约封面主题的全屏背景：主题底色 + 封面模糊铺底（降饱和 + 压平）+ 主题色 scrim。
 *
 * 与黑胶主题 [BackgroundLayer]（弱化封面 + 底部渐变）不同，这里把封面放大模糊，
 * 形成以封面主色为基调的环境光背景。
 */
@Composable
internal fun CoverBlurBackground(coverData: Any?) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val isDark = NiExtraColors.current.isDark
    // 浅色模式的 scrim 略减：保住一点背景色彩，但**不能减太多** —— 减到 0.55 时
    // 封面的构图会透出来，浅底深字压在花背景上会显得脏。0.72 是「有色但不花」的档位。
    val scrimStrength = if (isDark) 1f else 0.72f
    val washStrength = if (isDark) CoverBackdropWashDark else CoverBackdropWashLight
    // 降饱和的色矩阵：只需构造一次
    val desaturate = remember {
        ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(CoverBackdropSaturation) },
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(background))
        if (coverData != null) {
            // ⚠️ memoryCachePolicy(DISABLED) 不是保守起见，是**必须**的：
            // Coil 3 的缓存键只在请求带 transformations 时才把尺寸并进去
            // （`MemoryCacheService.newCacheKey`：`coil#size` 只在 `transformations.isNotEmpty()` 时加入），
            // 所以「同一个文件路径 + 不同 size」会命中同一条缓存。
            // 后果：这里先按 16px 解出的背景光，会被封面卡按全尺寸再写一次（后者更慢、写得更晚），
            // 于是**下次再进同一首歌时，背景读到的是那张全尺寸图** —— 背景变成一张清晰封面。
            // 这也解释了「第一次对、切歌再切回来就不对」。
            val request = remember(coverData) {
                when (coverData) {
                    is String -> ImageRequest.Builder(context)
                        .data(coverData)
                        .size(CoverBackdropSampleSize, CoverBackdropSampleSize)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    is ImageRequest -> coverData.newBuilder()
                        .size(CoverBackdropSampleSize, CoverBackdropSampleSize)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    else -> coverData
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // 低分辨率图被拉伸铺满即得背景光：不再挂整屏 blur / 缩放 / alpha 图层。
                // 放大用双三次（而不是默认的双线性）：双线性在每个源像素的边界上是折线，
                // 放大几十倍后会在屏幕上留下肉眼可见的「方块状云斑」，也就是背景发脏的主因。
                filterQuality = FilterQuality.High,
                colorFilter = desaturate,
                modifier = Modifier.fillMaxSize(),
            )
            // 均匀压一层主题底色：把封面自身的明暗差也收下来（见 CoverBackdropWash* 说明）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background.copy(alpha = washStrength)),
            )
        }
        // 主题色 scrim：顶部几乎不压、往下单调加深到接近纯色底。
        //
        // 参考图就是这个走向：最上面一段基本是封面本身的高光（背景光最亮），
        // 到歌名那一带已经压到能托住白字，到底部控件区几乎只剩底色。
        // 曲线必须**单调**：原先「上 0.34 → 中 0.20」中间反而变亮，会在封面上沿
        // 留出一条突兀的亮带。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            background.copy(alpha = 0.10f * scrimStrength),
                            background.copy(alpha = 0.36f * scrimStrength),
                            background.copy(alpha = 0.70f * scrimStrength),
                            background.copy(alpha = 0.88f * scrimStrength),
                            background.copy(alpha = 0.96f * scrimStrength),
                        ),
                    ),
                ),
        )
    }
}

/**
 * 简约封面主题的封面卡：方形圆角封面 + 轻投影，**不含任何玻璃质感**。
 *
 * 无封面（或封面路径已失效）时由 [GeneratedCoverArt] 现场生成一张：淡主题色纸面 + 文件名。
 * 该底衬**始终**绘制在封面图之下，所以「没有封面」不再是缺陷，而是现场造一张封面。
 */
@Composable
internal fun CoverCard(
    coverData: Any?,
    modifier: Modifier = Modifier,
    labelTitle: String = "",
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(CoverCorner)
    Box(modifier = modifier.shadow(18.dp, shape).clip(shape)) {
        NiGeneratedCoverArt(
            fileName = labelTitle,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
        )
        if (coverData != null) {
            // 同样关掉内存缓存：这条请求不带 transformations，Coil 的缓存键里就没有尺寸，
            // 会与「背景光的 16px」「小窗 / 歌单里的缩略图」互相覆盖（详见 CoverBlurBackground 的说明）。
            val request = remember(coverData) {
                when (coverData) {
                    is String -> ImageRequest.Builder(context)
                        .data(coverData)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    is ImageRequest -> coverData.newBuilder()
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                    else -> coverData
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

