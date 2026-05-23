package com.xyoye.common_component.source.inter

/**
 * Created by xyoye on 2021/11/14.
 *
 * 扩展资源，字幕+音频
 */

interface ExtraSource {
    fun getSubtitlePath(): String?

    fun setSubtitlePath(path: String?)

    fun getAudioPath(): String?

    fun setAudioPath(path: String?)
}