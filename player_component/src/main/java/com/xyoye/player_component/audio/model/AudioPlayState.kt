package com.xyoye.player_component.audio.model

sealed class AudioPlayState {
    object Idle : AudioPlayState()
    object Preparing : AudioPlayState()
    object Playing : AudioPlayState()
    object Pause : AudioPlayState()

    val isPlaying: Boolean get() = this is Playing
    val isPausing: Boolean get() = this is Pause
}
