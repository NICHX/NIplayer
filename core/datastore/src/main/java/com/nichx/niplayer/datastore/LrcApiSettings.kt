package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

object LrcApiSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_API_URL = "lrc_api_url"
    private const val KEY_API_AUTH = "lrc_api_auth"
    private const val KEY_LYRICS_ENABLED = "lrc_api_lyrics_enabled"
    private const val KEY_COVER_ENABLED = "lrc_api_cover_enabled"

    var apiUrl: String
        get() = mmkv.decodeString(KEY_API_URL, "") ?: ""
        set(value) { mmkv.encode(KEY_API_URL, value) }

    var apiAuth: String
        get() = mmkv.decodeString(KEY_API_AUTH, "") ?: ""
        set(value) { mmkv.encode(KEY_API_AUTH, value) }

    /** 在线歌词匹配开关，需同时配置 API 地址才实际生效。 */
    var lyricsMatchEnabled: Boolean
        get() = mmkv.decodeBool(KEY_LYRICS_ENABLED, true)
        set(value) { mmkv.encode(KEY_LYRICS_ENABLED, value) }

    /** 在线封面匹配开关，需同时配置 API 地址才实际生效。 */
    var coverMatchEnabled: Boolean
        get() = mmkv.decodeBool(KEY_COVER_ENABLED, true)
        set(value) { mmkv.encode(KEY_COVER_ENABLED, value) }

    val isConfigured: Boolean
        get() = apiUrl.isNotEmpty()
}
