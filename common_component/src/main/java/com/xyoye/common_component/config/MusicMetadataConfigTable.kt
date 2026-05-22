package com.xyoye.common_component.config

import com.xyoye.mmkv_annotation.MMKVFiled
import com.xyoye.mmkv_annotation.MMKVKotlinClass

@MMKVKotlinClass(className = "MusicMetadataConfig")
object MusicMetadataConfigTable {

    //音乐元数据API地址
    @MMKVFiled
    const val apiUrl = ""

    //音乐元数据API认证密钥
    @MMKVFiled
    const val apiAuth = ""
}