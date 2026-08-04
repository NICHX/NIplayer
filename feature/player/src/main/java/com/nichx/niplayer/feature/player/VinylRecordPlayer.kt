package com.nichx.niplayer.feature.player

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

private const val NEEDLE_ANGLE_PLAY = 0f
private const val NEEDLE_ANGLE_PAUSE = -25f
private const val DISC_ROTATION_DURATION_MS = 20_000f

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

@Composable
fun VinylRecordPlayer(
    coverData: Any?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    showNeedle: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val discRotation = rememberDiscRotation(isPlaying)
    val needleAngle by animateFloatAsState(
        targetValue = if (isPlaying) NEEDLE_ANGLE_PLAY else NEEDLE_ANGLE_PAUSE,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "needleAngle",
    )

    val rawNeedleBitmap = remember {
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_playing_needle)
            ?: createFallbackNeedleBitmap(305, 515)
    }
    val rawDiscBitmap = remember {
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.bg_playing_disc)
    }

    val parentSize = remember { mutableStateOf(IntSize.Zero) }

    // Computed layout values shared between Canvas and cover overlay
    val layout = remember(parentSize.value) {
        val pw = parentSize.value.width.toFloat()
        val ph = parentSize.value.height.toFloat()
        if (pw <= 0f || ph <= 0f) return@remember null

        // 唱针存在时需为其尖端留出高度余量（碟面占高度约 80%）；
        // 无唱针（横屏）时可放大碟面至约 91%，让唱片更大更协调
        val unit = if (showNeedle) {
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

        LayoutValues(
            unit, needleW, needleH, needleStartX, needleStartY,
            needleCenterX, needleCenterY, discDiameter, discStartX,
            discStartY, discCenterX, discCenterY, coverSizePx,
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Main Canvas: draws shadow + disc + needle
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { parentSize.value = it },
        ) {
            val pw = size.width
            val ph = size.height
            if (pw <= 0f || ph <= 0f) return@Canvas

            // 与上层 layout 保持一致：无唱针时放大碟面
            val unit = if (showNeedle) {
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

            // Disc shadow (static, centered on disc)
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = discDiameter / 2f + discDiameter * 0.06f,
                center = Offset(discCenterX, discCenterY),
            )

            // Disc + decorations (rotates)
            withTransform({
                rotate(discRotation, Offset(discCenterX, discCenterY))
            }) {
                drawImage(
                    image = rawDiscBitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(rawDiscBitmap.width, rawDiscBitmap.height),
                    dstOffset = IntOffset(discStartX.roundToInt(), discStartY.roundToInt()),
                    dstSize = IntSize(discDiameter.roundToInt(), discDiameter.roundToInt()),
                )

                val coverRadius = coverSizePx / 2f
                val grooveArea = discDiameter / 2f - coverRadius
                val grooveSpacing = grooveArea / 8f
                for (i in 1..6) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.04f),
                        radius = coverRadius + grooveSpacing * i,
                        center = Offset(discCenterX, discCenterY),
                        style = Stroke(width = 1.5f),
                    )
                }

                val arcRadius = discDiameter / 2f * 0.7f
                val arcThickness = discDiameter / 2f * 0.25f
                drawArc(
                    color = Color.White.copy(alpha = 0.04f),
                    startAngle = 300f,
                    sweepAngle = 40f,
                    useCenter = false,
                    topLeft = Offset(discCenterX - arcRadius, discCenterY - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = arcThickness),
                )

                val borderWidth = coverSizePx * 0.035f
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = coverRadius + borderWidth / 2f,
                    center = Offset(discCenterX, discCenterY),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = coverRadius - borderWidth * 0.3f,
                    center = Offset(discCenterX, discCenterY),
                    style = Stroke(width = borderWidth * 0.6f),
                )

                drawCircle(
                    color = Color(0xFF2a2a2a),
                    radius = discDiameter * 0.042f,
                    center = Offset(discCenterX, discCenterY),
                )
                drawCircle(
                    color = Color(0xFF1a1a1a),
                    radius = discDiameter * 0.022f,
                    center = Offset(discCenterX, discCenterY),
                )
            }

            // Needle (not affected by disc rotation)
            if (showNeedle) {
                withTransform({
                    rotate(needleAngle, Offset(needleCenterX, needleCenterY))
                }) {
                    drawImage(
                        image = rawNeedleBitmap.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(rawNeedleBitmap.width, rawNeedleBitmap.height),
                        dstOffset = IntOffset(needleStartX.roundToInt(), needleStartY.roundToInt()),
                        dstSize = IntSize(needleW.roundToInt(), needleH.roundToInt()),
                    )
                }
            }
        }

        // Cover art overlay (positioned on top of disc center)
        if (layout != null) {
            val l = layout
            val coverSizeDp = with(density) { l.coverSizePx.toDp() }

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
            } else {
                Box(
                    modifier = Modifier
                        .size(coverSizeDp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
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

@Composable
private fun rememberDiscRotation(isPlaying: Boolean): Float {
    val rotation = remember { mutableFloatStateOf(0f) }
    val lastFrameNanos = remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            lastFrameNanos.value = System.nanoTime()
            while (true) {
                delay(16)
                val now = System.nanoTime()
                val deltaNanos = now - lastFrameNanos.value
                lastFrameNanos.value = now
                val deltaMs = deltaNanos / 1_000_000
                if (deltaMs in 1L..200L) {
                    rotation.floatValue =
                        (rotation.floatValue + deltaMs.toFloat() * (360f / DISC_ROTATION_DURATION_MS)) % 360f
                }
            }
        }
    }

    return rotation.floatValue
}
