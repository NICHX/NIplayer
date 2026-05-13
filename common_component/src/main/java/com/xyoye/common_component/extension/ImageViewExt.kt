package com.xyoye.common_component.extension

import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import coil.request.videoFramePercent
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import com.xyoye.common_component.R
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.data_component.enums.ResourceType
import java.io.File

/**
 * Created by xyoye on 2020/7/31.
 */

fun ImageView.loadImage(
    source: String?,
    radiusDp: Float = 0f,
    errorRes: Int = 0,
) {
    val transformation = if (radiusDp > 0)
        RoundedCornersTransformation(radiusDp.dp())
    else
        null

    load(source) {
        scale(Scale.FILL)
        error(errorRes)
        crossfade(true)
        transformation?.let { transformations(it) }
    }
}

fun ImageView.loadVideoCover(image: File) {
    load(image) {
        scale(Scale.FILL)
        crossfade(true)
        error(R.drawable.ic_dandanplay)
        transformations(RoundedCornersTransformation(5f.dp()))
        diskCachePolicy(CachePolicy.DISABLED)
        memoryCachePolicy(CachePolicy.DISABLED)
        videoFramePercent(0.1)
    }
}

fun ImageView.loadStorageFileCover(file: StorageFile) {
    val source = file.fileCover()
    val resourceType = source.resourceType()

    // 文件类型的封面，不开启缓存，因为播放进度图会频繁变更
    val cachePolicy = if (resourceType == ResourceType.File)
        CachePolicy.DISABLED
    else
        CachePolicy.ENABLED

    // 根据文件类型选择不同的默认图标
    val defaultIcon = when {
        file.isVideoFile() -> R.drawable.ic_video_cover
        file.isAudioFile() -> R.drawable.ic_audio_cover
        file.isImageFile() -> R.drawable.ic_image_cover
        else -> R.drawable.ic_dandanplay
    }

    load(source ?: defaultIcon) {
        scale(Scale.FILL)
        crossfade(true)
        error(defaultIcon)
        transformations(RoundedCornersTransformation(5f.dp()))
        diskCachePolicy(cachePolicy)
        memoryCachePolicy(cachePolicy)
        videoFramePercent(0.1)
    }
}