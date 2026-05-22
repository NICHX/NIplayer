package com.xyoye.common_component.config

import com.xyoye.mmkv_annotation.MMKVFiled
import com.xyoye.mmkv_annotation.MMKVKotlinClass

@MMKVKotlinClass(className = "ThumbnailConfig")
object ThumbnailConfigTable {
    @MMKVFiled
    const val generateThumbnail = true

    @MMKVFiled
    const val generateForImage = true

    @MMKVFiled
    const val generateForVideo = true

    @MMKVFiled
    const val generateForAudio = true

    @MMKVFiled
    const val saveInSameDir = true
}
