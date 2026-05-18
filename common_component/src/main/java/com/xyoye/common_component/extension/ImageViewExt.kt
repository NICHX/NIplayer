package com.xyoye.common_component.extension

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.xyoye.common_component.R
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import com.xyoye.data_component.enums.ResourceType
import java.io.File

fun ImageView.loadImage(
    source: String?,
    radiusDp: Float = 0f,
    errorRes: Int = 0,
) {
    Glide.with(this)
        .load(source)
        .apply(RequestOptions().apply {
            centerCrop()
            dontAnimate()
            if (errorRes != 0) error(errorRes)
            if (radiusDp > 0) transform(RoundedCorners(radiusDp.dp().toInt()))
        })
        .into(this)
}

fun ImageView.loadVideoCover(image: File) {
    Glide.with(this)
        .load(image)
        .apply(RequestOptions().apply {
            centerCrop()
            dontAnimate()
            error(R.drawable.ic_dandanplay)
            transform(RoundedCorners(5f.dp().toInt()))
            diskCacheStrategy(DiskCacheStrategy.NONE)
            skipMemoryCache(true)
            frame(0)
        })
        .into(this)
}

fun ImageView.loadStorageFileCover(file: StorageFile, scaleSize: Int? = null) {
    val uniqueKey = file.uniqueKey()

    val source = ThumbnailMemoryCache.getCoverPath(uniqueKey)
        ?: file.fileCover()
        ?.also { ThumbnailMemoryCache.putCoverPath(uniqueKey, it) }

    if (source == null) {
        val defaultIcon = when {
            file.isVideoFile() -> R.drawable.ic_video_cover
            file.isAudioFile() -> R.drawable.ic_audio_cover
            file.isImageFile() -> R.drawable.ic_image_cover
            else -> R.drawable.ic_dandanplay
        }
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(defaultIcon)
        return
    }

    val resourceType = source.resourceType()
    val isLocalFile = resourceType == ResourceType.File

    scaleType = if (isLocalFile) {
        ImageView.ScaleType.CENTER_CROP
    } else {
        ImageView.ScaleType.FIT_CENTER
    }

    val diskCacheStrategy = if (isLocalFile) {
        DiskCacheStrategy.NONE
    } else {
        DiskCacheStrategy.AUTOMATIC
    }

    val sizePx = scaleSize ?: if (isLocalFile) 512 else null

    Glide.with(this)
        .load(source)
        .apply(RequestOptions().apply {
            dontAnimate()
            transform(RoundedCorners(5f.dp().toInt()))
            diskCacheStrategy(diskCacheStrategy)
            skipMemoryCache(false)
            frame(0)
            format(DecodeFormat.PREFER_RGB_565)
            if (sizePx != null) override(sizePx, sizePx)
        })
        .into(this)
}
