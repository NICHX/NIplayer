package com.xyoye.common_component.config

import com.tencent.mmkv.MMKV

object TmdbApiConfig {
    private const val KEY_TMDB_API_KEY = "tmdb_api_key"

    private val mmkv by lazy { MMKV.defaultMMKV() }

    var apiKey: String
        get() = mmkv.decodeString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) { mmkv.encode(KEY_TMDB_API_KEY, value) }
}