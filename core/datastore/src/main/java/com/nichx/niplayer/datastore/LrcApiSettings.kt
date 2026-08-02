package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

object LrcApiSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_API_URL = "lrc_api_url"
    private const val KEY_API_AUTH = "lrc_api_auth"

    var apiUrl: String
        get() = mmkv.decodeString(KEY_API_URL, "") ?: ""
        set(value) { mmkv.encode(KEY_API_URL, value) }

    var apiAuth: String
        get() = mmkv.decodeString(KEY_API_AUTH, "") ?: ""
        set(value) { mmkv.encode(KEY_API_AUTH, value) }

    val isConfigured: Boolean
        get() = apiUrl.isNotEmpty()
}
