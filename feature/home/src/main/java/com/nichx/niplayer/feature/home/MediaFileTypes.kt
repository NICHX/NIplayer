package com.nichx.niplayer.feature.home

import com.nichx.niplayer.player.kernel.MediaFileTypes as KernelMediaFileTypes

/**
 * 媒体文件类型判断（基于扩展名）。
 *
 * BUG-1 修复：原 :feature:home 维护独立的扩展名表，与 :player:kernel 的
 * [PlaybackRequestHolder.isAudioFile] 和 :core:thumbnail 的 `VIDEO_EXTENSIONS`
 * 三套表不一致。改为委托到 [com.nichx.niplayer.player.kernel.MediaFileTypes]
 *（:player:kernel 中的权威来源），消除重复定义。
 *
 * 保留此对象是为了不破坏 :feature:home 内部既有调用方的导入路径。
 */
object MediaFileTypes {

    /** 是否为视频文件（按扩展名判断）。 */
    fun isVideoFile(name: String): Boolean =
        KernelMediaFileTypes.isVideoFile(name)

    /** 是否为音频文件（按扩展名判断）。 */
    fun isAudioFile(name: String): Boolean =
        KernelMediaFileTypes.isAudioFile(name)

    /** 是否为图片文件（按扩展名判断）。 */
    fun isImageFile(name: String): Boolean =
        KernelMediaFileTypes.isImageFile(name)

    /** 是否为可播放的媒体文件（视频或音频）。 */
    fun isMediaFile(name: String): Boolean =
        KernelMediaFileTypes.isMediaFile(name)
}
