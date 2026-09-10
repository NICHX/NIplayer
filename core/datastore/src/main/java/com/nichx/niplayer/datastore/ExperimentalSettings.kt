package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 实验性功能开关（MMKV）。
 *
 * 实验性功能默认关闭，需在"设置 - 实验性功能"中手动开启：
 * - [vrPlaybackEnabled]：VR 播放。播放器内以陀螺仪视角环视全景/左右·上下格式画面。
 *   默认 false。
 * - [flatListViewEnabled]：平铺列表视图。文件浏览页以可折叠列表内联展开子项。
 *   默认 false。
 *
 * 关闭时，对应功能的入口在 UI 中被隐藏或禁用。
 */
object ExperimentalSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_VR_PLAYBACK = "experimental_vr_playback"
    private const val KEY_FLAT_LIST = "experimental_flat_list"

    /** VR 播放实验性开关。 */
    var vrPlaybackEnabled: Boolean
        get() = mmkv.decodeBool(KEY_VR_PLAYBACK, false)
        set(value) { mmkv.encode(KEY_VR_PLAYBACK, value) }

    /** 平铺列表视图实验性开关。 */
    var flatListViewEnabled: Boolean
        get() = mmkv.decodeBool(KEY_FLAT_LIST, false)
        set(value) { mmkv.encode(KEY_FLAT_LIST, value) }
}