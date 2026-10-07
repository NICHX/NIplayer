package com.nichx.niplayer.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.nichx.niplayer.designsystem.components.NiGeneratedCoverArt
import com.nichx.niplayer.designsystem.theme.MotionTokens
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import kotlin.math.min
import kotlin.math.roundToInt

private const val NEEDLE_ANGLE_PLAY = 0f
private const val NEEDLE_ANGLE_PAUSE = -25f

/**
 * 唱片旋转一圈的时长（ms）。
 *
 * 真实黑胶 33⅓ 转/分 ≈ 1800ms/圈，但那样旋转过快、高光频扫容易晕眩；
 * 取 10s/圈——既让「高光扫过」的转动感清晰可见，又保持舒缓的观感。
 */
private const val DISC_ROTATION_DURATION_MS = 10_000f

/** 暂停时唱片惯性缓停的时长（ms）：从满速线性衰减到静止。 */
private const val DISC_SPINDOWN_MS = 1200f

private fun createFallbackNeedleBitmap(w: Int, h: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3A3A.toInt()
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        moveTo(w * 0.5f, 0f)
        lineTo(w * 0.65f, h * 0.08f)
        lineTo(w * 0.55f, h * 0.92f)
        lineTo(w * 0.45f, h.toFloat())
        lineTo(w * 0.35f, h * 0.92f)
        lineTo(w * 0.35f, h * 0.08f)
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

private fun decodeNeedleBitmap(context: Context): Bitmap =
    android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_playing_needle)
        ?: createFallbackNeedleBitmap(305, 515)

/**
 * 唱片层（**不含唱针**）。
 *
 * 布局在**组合期**由约束同步算出（[BoxWithConstraints]），Canvas 与封面浮层同一帧就位；
 * 旧实现依赖 `onSizeChanged`，首帧 `layout == null`，封面会晚一帧才出现（两段式加载）。
 * 入场/切换动效交由外层转场处理，组件本身不再自带动画，避免叠加。
 *
 * 唱针已拆到 [VinylNeedle]：换片跟手时唱片要随手指平移，而唱机上的唱针是**固定**的——
 * 两者在同一组件里就无法各自运动。
 *
 * @param needleSpace 是否在布局中为唱针预留空间（决定碟面占比）。唱针由 [VinylNeedle] 画，
 *   但几何必须与本层一致，故该开关仍留在本层。
 * @param drawShadow 是否绘制碟面投影。跟手换片时唱片会滑出唱盘，投影交由固定的
 *   [VinylPlatter] 单独绘制，本层需传 `false`，否则会「影子跟着唱片跑」。
 * @param labelTitle 无封面时中心那张手写纸贴纸上要写的内容（通常传当前曲目文件名）。
 */
@Composable
fun VinylRecordPlayer(
    coverData: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    needleSpace: Boolean = true,
    drawShadow: Boolean = true,
    labelTitle: String = "",
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = NiExtraColors.current.isDark
    val discRotation = rememberDiscRotation(isPlaying)

    val rawDiscBitmap = remember {
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.bg_playing_disc)
    }
    // 旋转时不再每帧创建 Bitmap 包装对象（原实现逐帧 asImageBitmap 造成 GC 压力）
    val discImage = remember(rawDiscBitmap) { rawDiscBitmap.asImageBitmap() }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val hasBoundedSize = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val layout = remember(maxWidth, maxHeight, needleSpace, hasBoundedSize) {
            if (!hasBoundedSize) {
                null
            } else {
                with(density) {
                    computeVinylLayout(maxWidth.toPx(), maxHeight.toPx(), needleSpace)
                }
            }
        }

        // Main Canvas: draws shadow + disc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = layout ?: return@Canvas

            // 碟面投影（主题自适应：深色下加深，浅色下轻）。
            // 跟手换片时本层会随唱片移动，投影必须交给固定的 [VinylPlatter] 画，故此处可关。
            if (drawShadow) {
                drawCircle(
                    color = Color.Black.copy(alpha = if (isDark) 0.45f else 0.14f),
                    radius = l.discDiameter / 2f + l.discDiameter * 0.05f,
                    center = Offset(l.discCenterX, l.discCenterY),
                )
            }

            // 深色主题下补一圈浅色轮廓光：避免黑盘与深色背景融为一体、边界消失
            drawCircle(
                color = Color.White.copy(alpha = if (isDark) 0.12f else 0.05f),
                radius = l.discDiameter / 2f,
                center = Offset(l.discCenterX, l.discCenterY),
                style = Stroke(width = 1.5f),
            )

            // Disc + decorations (rotates)
            withTransform({
                rotate(discRotation, Offset(l.discCenterX, l.discCenterY))
            }) {
                drawImage(
                    image = discImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(rawDiscBitmap.width, rawDiscBitmap.height),
                    dstOffset = IntOffset(l.discStartX.roundToInt(), l.discStartY.roundToInt()),
                    dstSize = IntSize(l.discDiameter.roundToInt(), l.discDiameter.roundToInt()),
                )

                // 细密沟槽：更接近真实黑胶的纹理密度
                val coverRadius = l.coverSizePx / 2f
                val grooveArea = l.discDiameter / 2f - coverRadius
                val grooveCount = 9
                val grooveSpacing = grooveArea / (grooveCount + 2)
                for (i in 1..grooveCount) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.05f),
                        radius = coverRadius + grooveSpacing * i,
                        center = Offset(l.discCenterX, l.discCenterY),
                        style = Stroke(width = 1.2f),
                    )
                }

                // 一道柔和的反光弧：黑胶表面的油亮感来自这一笔，不再叠加第二道以免显脏
                val arcRadius = l.discDiameter / 2f * 0.7f
                val arcThickness = l.discDiameter / 2f * 0.25f
                drawArc(
                    color = Color.White.copy(alpha = 0.05f),
                    startAngle = 300f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(l.discCenterX - arcRadius, l.discCenterY - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = arcThickness),
                )

                // 封面凹槽外圈：白边 + 内阴影黑边，让封面「嵌进」碟面
                val borderWidth = l.coverSizePx * 0.035f
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = coverRadius + borderWidth / 2f,
                    center = Offset(l.discCenterX, l.discCenterY),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = coverRadius,
                    center = Offset(l.discCenterX, l.discCenterY),
                    style = Stroke(width = borderWidth * 0.9f),
                )

                // 注：此处原有一段「中心金属轴心」。但 coverSizePx = unit*4、discDiameter = unit*6，
                // 中心那张图（或占位标签）直径是碟面的 2/3，轴心恒被其完全覆盖、永远不可见，故移除。
            }
        }

        // Cover art overlay (positioned on top of disc center)
        val l = layout
        if (l != null) {
            val coverSizeDp = with(density) { l.coverSizePx.toDp() }

            // 中心底衬：**始终**先铺一层「生成封面」，真实封面图再叠在它上面。
            // 详见 [GeneratedCoverArt] 注释：封面路径失效时 Coil 什么都不画，
            // 没有这层底衬就会透出下层唱盘投影（浅色背景上即那块「大洞」灰圆）。
            NiGeneratedCoverArt(
                fileName = labelTitle,
                shape = CircleShape,
                rotation = discRotation,
                modifier = Modifier.size(coverSizeDp),
            )

            if (coverData != null) {
                val ctx = LocalContext.current
                val request = remember(coverData) {
                    when (coverData) {
                        is String -> ImageRequest.Builder(ctx)
                            .data(coverData)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                        is ImageRequest -> coverData.newBuilder()
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                        else -> coverData
                    }
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier
                        .size(coverSizeDp)
                        .graphicsLayer {
                            rotationZ = discRotation
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            clip = true
                            shape = CircleShape
                        }
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * 唱针层（独立于唱片）。
 *
 * 拆出来是为了让唱片能单独平移：换片跟手时唱片随手指移动，而唱机上的唱针**固定不动**，
 * 只在「抬起 / 落下」之间转动。
 *
 * 几何与 [VinylRecordPlayer] 共用 [computeVinylLayout]；调用方须给两层**同一个尺寸的盒子**
 * （如同样的 `fillMaxWidth(0.85f).aspectRatio(1f)`），否则唱针与碟面会错位。
 */
@Composable
internal fun VinylNeedle(
    isPlaying: Boolean,
    needleLifted: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // 唱针角度：播放且未抬针 → 落针；否则（暂停 / 换片抬针）→ 抬针。
    val needleAngle by animateFloatAsState(
        targetValue = if (isPlaying && !needleLifted) NEEDLE_ANGLE_PLAY else NEEDLE_ANGLE_PAUSE,
        animationSpec = tween(MotionTokens.ENTER, easing = FastOutSlowInEasing),
        label = "needleAngle",
    )
    val rawNeedleBitmap = remember { decodeNeedleBitmap(context) }
    val needleImage = remember(rawNeedleBitmap) { rawNeedleBitmap.asImageBitmap() }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val hasBoundedSize = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val layout = remember(maxWidth, maxHeight, hasBoundedSize) {
            if (!hasBoundedSize) {
                null
            } else {
                with(density) { computeVinylLayout(maxWidth.toPx(), maxHeight.toPx(), true) }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = layout ?: return@Canvas
            withTransform({
                rotate(needleAngle, Offset(l.needleCenterX, l.needleCenterY))
            }) {
                drawImage(
                    image = needleImage,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(rawNeedleBitmap.width, rawNeedleBitmap.height),
                    dstOffset = IntOffset(l.needleStartX.roundToInt(), l.needleStartY.roundToInt()),
                    dstSize = IntSize(l.needleW.roundToInt(), l.needleH.roundToInt()),
                )
            }
        }
    }
}

/**
 * 唱盘阴影层（**固定不动**）。
 *
 * 它是整组视觉的**最底层**——半透明的一圈碟面投影，语义上属于「唱机」而非「唱片」：
 * 跟手换片时唱片会滑出视野，这层阴影应当留在原地，像唱机的盘面。
 * 因此从 [VinylRecordPlayer] 里拆出来，作为移动层之外的固定兄弟层绘制
 * （对应的唱片层需传 `drawShadow = false`）。
 *
 * 几何与另两层一致，调用方给同一个尺寸的盒子即可对齐。
 */
@Composable
internal fun VinylPlatter(
    modifier: Modifier = Modifier,
    needleSpace: Boolean = true,
) {
    val density = LocalDensity.current
    val isDark = NiExtraColors.current.isDark

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val hasBoundedSize = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val layout = remember(maxWidth, maxHeight, needleSpace, hasBoundedSize) {
            if (!hasBoundedSize) {
                null
            } else {
                with(density) {
                    computeVinylLayout(maxWidth.toPx(), maxHeight.toPx(), needleSpace)
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = layout ?: return@Canvas
            drawCircle(
                color = Color.Black.copy(alpha = if (isDark) 0.45f else 0.14f),
                radius = l.discDiameter / 2f + l.discDiameter * 0.05f,
                center = Offset(l.discCenterX, l.discCenterY),
            )
        }
    }
}

/**
 * 唱片布局的唯一计算来源：由容器尺寸推导唱针 / 碟面 / 封面的几何参数。
 *
 * [VinylRecordPlayer] 与 [VinylNeedle] 都调用本函数，避免同一套公式在两处各写一份而漂移。
 */
private fun computeVinylLayout(pw: Float, ph: Float, needleSpace: Boolean): LayoutValues {
    // 有唱针时需为其尖端留出高度余量（碟面占高度约 80%）；
    // 无唱针（横屏）时可放大碟面至约 91%，让唱片更大更协调
    val unit = if (needleSpace) {
        min(pw / 7f, ph / 7.5f)
    } else {
        min(pw / 6.2f, ph / 6.6f)
    }

    val needleW = unit * 2f
    val needleH = unit * 3.33f
    val scaledNeedleW = unit * 2f
    val needleStartX = pw / 2f - scaledNeedleW / 5.5f
    val needleCenterX = pw / 2f

    val discDiameter = unit * 6f
    val discStartX = (pw - discDiameter) / 2f
    val discCenterX = pw / 2f

    // Center the whole group vertically: discCenterY = ph/2
    val discCenterY = ph / 2f
    val discStartY = discCenterY - discDiameter / 2f
    val needleStartY = discStartY - needleH * 0.65f
    val needleCenterY = needleStartY + scaledNeedleW / 5.5f

    val coverSizePx = unit * 4f

    return LayoutValues(
        unit, needleW, needleH, needleStartX, needleStartY,
        needleCenterX, needleCenterY, discDiameter, discStartX,
        discStartY, discCenterX, discCenterY, coverSizePx,
    )
}

private class LayoutValues(
    val unit: Float,
    val needleW: Float,
    val needleH: Float,
    val needleStartX: Float,
    val needleStartY: Float,
    val needleCenterX: Float,
    val needleCenterY: Float,
    val discDiameter: Float,
    val discStartX: Float,
    val discStartY: Float,
    val discCenterX: Float,
    val discCenterY: Float,
    val coverSizePx: Float,
)

/**
 * 唱片角速度驱动。
 *
 * - 播放时由 [withFrameNanos] 与 vsync 对齐（避免 delay(16) 与刷新率不同步导致的周期性丢帧/微跳）；
 * - 暂停时不做「急停」，而是以当前满速线性衰减到静止（[DISC_SPINDOWN_MS]），模拟真实唱机停机惯性。
 *
 * 角速度保存在 remember 中跨 effect 持续，因此首次进入（本来就暂停）不会产生多余的自转。
 */
@Composable
private fun rememberDiscRotation(isPlaying: Boolean): Float {
    val rotation = remember { mutableFloatStateOf(0f) }
    // 角速度（度 / 毫秒）
    val velocity = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        val targetVelocity = 360f / DISC_ROTATION_DURATION_MS
        val decel = targetVelocity / DISC_SPINDOWN_MS

        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val deltaMs = (now - lastFrameNanos) / 1_000_000f
            lastFrameNanos = now
            if (deltaMs <= 0f || deltaMs > 200f) continue

            if (isPlaying) {
                // 起步直接到目标速度（唱机起转很快），保持匀速旋转
                velocity.floatValue = targetVelocity
            } else {
                velocity.floatValue -= decel * deltaMs
                if (velocity.floatValue <= 0f) {
                    velocity.floatValue = 0f
                    break
                }
            }

            rotation.floatValue = (rotation.floatValue + deltaMs * velocity.floatValue) % 360f
        }
    }

    return rotation.floatValue
}
