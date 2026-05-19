package com.xyoye.player_component.audio.manager

import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.model.AudioSong
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerController {
    val playState: StateFlow<AudioPlayState>
    val playProgress: StateFlow<Long>
    val currentSong: StateFlow<AudioSong?>
    val bufferingPercent: StateFlow<Int>
    val playMode: StateFlow<AudioPlayMode>
    val playlist: StateFlow<List<AudioSong>>

    fun play(song: AudioSong)
    fun playPause()
    fun next()
    fun prev()
    fun seekTo(msec: Int)
    fun stop()
    fun setPlayMode(mode: AudioPlayMode)

    fun setPlaylist(songs: List<AudioSong>, startIndex: Int)
    fun addToPlaylist(songs: List<AudioSong>)
    fun removeFromPlaylist(index: Int)
    fun clearPlaylist()
}
