package com.xyoye.common_component.extension

import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import coil3.load
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.size.Scale
import coil3.video.videoFramePercent
import com.xyoye.common_component.R
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import com.xyoye.data_component.enums.ResourceType
import java.io.File

private const val COVER_CORNER_RADIUS_DP = 5f

private fun ImageView.applyRoundedCorners() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = COVER_CORNER_RADIUS_DP.dp()
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }
}

fun ImageView.loadImage(
    source: String?,
    radiusDp: Float = 0f,
    errorRes: Int = 0,
) {
    if (radiusDp > 0) {
        applyRoundedCorners()
    }

    load(source) {
        scale(Scale.FILL)
        error(errorRes)
        crossfade(false)
    }
}

fun ImageView.loadVideoCover(image: File) {
    applyRoundedCorners()
    load(image) {
        scale(Scale.FILL)
        crossfade(false)
        error(R.drawable.ic_dandanplay)
        diskCachePolicy(CachePolicy.DISABLED)
        memoryCachePolicy(CachePolicy.DISABLED)
        videoFramePercent(0.0)
    }
}

fun ImageView.loadStorageFileCover(file: StorageFile, scaleSize: Int? = null) {
    val uniqueKey = file.uniqueKey()
    if (uniqueKey.isNotEmpty()) {
        ThumbnailMemoryCache.get(uniqueKey)?.let { bitmap ->
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
            return
        }
    }

    val source = ThumbnailMemoryCache.getCoverPath(uniqueKey)
        ?: file.fileCover()
        ?.also { ThumbnailMemoryCache.putCoverPath(uniqueKey, it) }

    val defaultIcon = when {
        file.isVideoFile() -> R.drawable.ic_video_cover
        file.isAudioFile() -> R.drawable.ic_audio_cover
        file.isImageFile() -> R.drawable.ic_image_cover
        else -> R.drawable.ic_dandanplay
    }

    if (source == null) {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(defaultIcon)
        return
    }

    val resourceType = source.resourceType()
    val isLocalFile = resourceType == ResourceType.File

    val targetScaleType = if (isLocalFile) {
        ImageView.ScaleType.CENTER_CROP
    } else {
        ImageView.ScaleType.FIT_CENTER
    }

    val diskCachePolicy = if (isLocalFile) {
        CachePolicy.DISABLED
    } else {
        CachePolicy.ENABLED
    }

    val sizePx = scaleSize ?: if (isLocalFile) 512 else null

    applyRoundedCorners()

    scaleType = ImageView.ScaleType.CENTER_INSIDE

    load(source) {
        scale(Scale.FILL)
        crossfade(false)
        placeholder(defaultIcon)
        diskCachePolicy(diskCachePolicy)
        memoryCachePolicy(CachePolicy.ENABLED)
        videoFramePercent(0.0)
        if (sizePx != null) {
            size(sizePx)
        }
        listener(
            onSuccess = { _, result ->
                scaleType = targetScaleType
                if (uniqueKey.isNotEmpty()) {
                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                    if (bitmap != null && !bitmap.isRecycled) {
                        ThumbnailMemoryCache.put(uniqueKey, bitmap)
                    }
                }
            },
            onError = { _, _ ->
                if (uniqueKey.isNotEmpty()) {
                    ThumbnailMemoryCache.removeCoverPath(uniqueKey)
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(defaultIcon)
            }
        )
    }
}
