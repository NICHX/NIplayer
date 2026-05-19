package com.xyoye.player_component.audio.model

data class AudioSong(
    val uri: String,
    val title: String,
    val artist: String = "",
    val coverPath: String? = null,
    val duration: Long = 0L,
    val uniqueKey: String,
    val fileName: String = ""
)
