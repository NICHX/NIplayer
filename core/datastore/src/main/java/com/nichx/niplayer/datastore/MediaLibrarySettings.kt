package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/**
 * 媒体库页面设置持久化（MMKV）。
 *
 * 配置项：
 * - [viewMode]：存储源列表视图模式（分组列表 / 双列网格），默认分组列表
 *
 * 使用方式：
 * - 写入端：[com.nichx.niplayer.feature.home.library.LibraryScreen] 顶栏视图切换按钮
 * - 读取端：[com.nichx.niplayer.feature.home.library.LibraryScreen] 根据模式渲染内容
 */
object MediaLibrarySettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_VIEW_MODE = "media_library_view_mode"

    /** 媒体库存储源视图模式：列表 / 网格（与文件浏览视图切换交互一致）。 */
    enum class ViewMode(val value: Int) {
        LIST(0),
        GRID(1);

        companion object {
            fun fromValue(v: Int): ViewMode = entries.find { it.value == v } ?: LIST
        }
    }

    /** 当前视图模式，默认列表。 */
    var viewMode: ViewMode
        get() = ViewMode.fromValue(mmkv.decodeInt(KEY_VIEW_MODE, ViewMode.LIST.value))
        set(value) {
            mmkv.encode(KEY_VIEW_MODE, value.value)
        }
}