package com.xyoye.data_component.enums

/**
 * Created by xyoye on 2024/1/23.
 */

enum class TrackType {
    SUBTITLE,

    AUDIO;

    fun getSubtitle(value: Any?): String? {
        if (value != null && value is String && this == SUBTITLE) {
            return value
        }
        return null
    }

    fun getAudio(value: Any?): String? {
        if (value != null && value is String && this == AUDIO) {
            return value
        }
        return null
    }
}