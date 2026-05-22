package com.xyoye.user_component.ui.activities.music_metadata

import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.MusicMetadataConfig

class MusicMetadataApiViewModel : BaseViewModel() {

    val apiUrl: String
        get() = MusicMetadataConfig.getApiUrl().orEmpty()

    val apiAuth: String
        get() = MusicMetadataConfig.getApiAuth().orEmpty()

    val isConfigured: Boolean
        get() = apiUrl.isNotEmpty()

    val apiStatus: String
        get() = if (apiUrl.isEmpty()) {
            "未启用"
        } else {
            "已启用${if (apiAuth.isNotEmpty()) "（含密钥）" else ""}"
        }

    fun saveApiUrl(url: String) {
        MusicMetadataConfig.putApiUrl(url)
    }

    fun saveApiAuth(auth: String) {
        MusicMetadataConfig.putApiAuth(auth)
    }

    fun saveAll(apiUrl: String, apiAuth: String) {
        MusicMetadataConfig.putApiUrl(apiUrl)
        MusicMetadataConfig.putApiAuth(apiAuth)
    }
}