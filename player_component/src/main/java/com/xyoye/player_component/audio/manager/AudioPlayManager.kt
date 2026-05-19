package com.xyoye.player_component.audio.manager

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.util.Util
import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.model.AudioSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioPlayManager : AudioPlayerController {
    private var exoPlayer: ExoPlayer? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var isInitialized = false

    private val _playState = MutableStateFlow<AudioPlayState>(AudioPlayState.Idle)
    override val playState: StateFlow<AudioPlayState> = _playState.asStateFlow()

    private val _playProgress = MutableStateFlow(0L)
    override val playProgress: StateFlow<Long> = _playProgress.asStateFlow()

    private val _currentSong = MutableStateFlow<AudioSong?>(null)
    override val currentSong: StateFlow<AudioSong?> = _currentSong.asStateFlow()

    private val _bufferingPercent = MutableStateFlow(0)
    override val bufferingPercent: StateFlow<Int> = _bufferingPercent.asStateFlow()

    private val _playMode = MutableStateFlow<AudioPlayMode>(AudioPlayMode.Loop)
    override val playMode: StateFlow<AudioPlayMode> = _playMode.asStateFlow()

    private val _playlist = MutableStateFlow<List<AudioSong>>(emptyList())
    override val playlist: StateFlow<List<AudioSong>> = _playlist.asStateFlow()

    private val _songDuration = MutableStateFlow(0L)
    val songDuration: StateFlow<Long> = _songDuration.asStateFlow()

    private var currentIndex = 0

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext

        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> _playState.value = AudioPlayState.Idle
                    Player.STATE_BUFFERING -> _playState.value = AudioPlayState.Preparing
                    Player.STATE_READY -> {
                        val duration = exoPlayer?.duration ?: C.TIME_UNSET
                        _songDuration.value = if (duration > 0 && duration != C.TIME_UNSET) duration else 0L
                        if (exoPlayer?.playWhenReady == true) {
                            _playState.value = AudioPlayState.Playing
                        } else {
                            _playState.value = AudioPlayState.Pause
                        }
                    }
                    Player.STATE_ENDED -> handleCompletion()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (exoPlayer?.playbackState == Player.STATE_READY) {
                    _playState.value = if (isPlaying) AudioPlayState.Playing else AudioPlayState.Pause
                }
            }

            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            }
        })

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope?.launch {
            while (isActive) {
                val player = exoPlayer ?: break
                if (player.isPlaying) {
                    _playProgress.value = player.currentPosition
                }
                delay(1000)
            }
        }
    }

    private fun handleCompletion() {
        when (_playMode.value) {
            AudioPlayMode.Loop -> {
                val nextIndex = currentIndex + 1
                if (nextIndex < _playlist.value.size) {
                    playAtIndex(nextIndex)
                } else {
                    _playState.value = AudioPlayState.Pause
                }
            }
            AudioPlayMode.Shuffle -> {
                val list = _playlist.value
                if (list.size <= 1) {
                    _playState.value = AudioPlayState.Pause
                    return
                }
                var randomIndex: Int
                do {
                    randomIndex = (0 until list.size).random()
                } while (randomIndex == currentIndex)
                playAtIndex(randomIndex)
            }
            AudioPlayMode.Single -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
        }
    }

    override fun play(song: AudioSong) {
        val index = _playlist.value.indexOfFirst { it.uniqueKey == song.uniqueKey }
        if (index >= 0) {
            playAtIndex(index)
        } else {
            setPlaylist(listOf(song), 0)
        }
    }

    override fun playPause() {
        val player = exoPlayer ?: return
        when (player.playbackState) {
            Player.STATE_IDLE -> player.prepare()
            Player.STATE_READY -> {
                if (player.isPlaying) {
                    player.pause()
                    _playState.value = AudioPlayState.Pause
                } else {
                    player.play()
                    _playState.value = AudioPlayState.Playing
                }
            }
            Player.STATE_ENDED -> {
                player.seekTo(0)
                player.prepare()
            }
            else -> {}
        }
    }

    override fun next() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val nextIndex = when (_playMode.value) {
            AudioPlayMode.Shuffle -> {
                if (list.size <= 1) 0
                else {
                    var randomIndex: Int
                    do {
                        randomIndex = (0 until list.size).random()
                    } while (randomIndex == currentIndex)
                    randomIndex
                }
            }
            else -> (currentIndex + 1) % list.size
        }
        playAtIndex(nextIndex)
    }

    override fun prev() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val prevIndex = (currentIndex - 1 + list.size) % list.size
        playAtIndex(prevIndex)
    }

    override fun seekTo(msec: Int) {
        exoPlayer?.seekTo(msec.toLong())
    }

    override fun stop() {
        exoPlayer?.stop()
        _playState.value = AudioPlayState.Idle
        _playProgress.value = 0
    }

    override fun setPlayMode(mode: AudioPlayMode) {
        _playMode.value = mode
        exoPlayer?.repeatMode = when (mode) {
            AudioPlayMode.Single -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_ALL
        }
    }

    override fun setPlaylist(songs: List<AudioSong>, startIndex: Int) {
        _playlist.value = songs.toList()
        if (songs.isNotEmpty() && startIndex in songs.indices) {
            playAtIndex(startIndex)
        }
    }

    fun updatePlaylist(songs: List<AudioSong>) {
        _playlist.value = songs.toList()
    }

    override fun addToPlaylist(songs: List<AudioSong>) {
        _playlist.value = _playlist.value + songs
    }

    override fun removeFromPlaylist(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _playlist.value = list
            if (index == currentIndex) {
                if (list.isNotEmpty()) {
                    playAtIndex(0)
                } else {
                    stop()
                }
            }
        }
    }

    override fun clearPlaylist() {
        stop()
        _playlist.value = emptyList()
        _currentSong.value = null
        currentIndex = 0
    }

    private fun playAtIndex(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        currentIndex = index
        val song = list[index]
        _currentSong.value = song

        val player = exoPlayer ?: return
        player.stop()
        _playState.value = AudioPlayState.Preparing

        val context = appContext ?: return
        val dataSourceFactory = DefaultDataSourceFactory(
            context,
            Util.getUserAgent(context, "NIplayer")
        )
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(song.uri))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
        _playProgress.value = 0
        _bufferingPercent.value = 0
    }

    fun release() {
        scope?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        appContext = null
        isInitialized = false
    }
}
