package com.xyoye.player_component.audio.model

sealed class AudioPlayMode(val value: Int) {
    object Loop : AudioPlayMode(0)
    object Shuffle : AudioPlayMode(1)
    object Single : AudioPlayMode(2)

    companion object {
        fun valueOf(value: Int): AudioPlayMode {
            return when (value) {
                0 -> Loop
                1 -> Shuffle
                2 -> Single
                else -> Loop
            }
        }
    }
}
