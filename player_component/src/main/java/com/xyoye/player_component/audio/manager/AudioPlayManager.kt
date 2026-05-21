package com.xyoye.player_component.audio.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import com.xyoye.common_component.utils.AudioMetadata
import com.xyoye.common_component.utils.AudioMetadataCache
import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.model.AudioSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AudioPlayManager {

    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null

    private val _playState = MutableStateFlow<AudioPlayState>(AudioPlayState.Idle)
    val playState: StateFlow<AudioPlayState> = _playState.asStateFlow()

    private val _playlist = MutableStateFlow<List<AudioSong>>(emptyList())
    val playlist: StateFlow<List<AudioSong>> = _playlist.asStateFlow()

    private val _currentSong = MutableStateFlow<AudioSong?>(null)
    val currentSong: StateFlow<AudioSong?> = _currentSong.asStateFlow()

    private val _playMode = MutableStateFlow<AudioPlayMode>(AudioPlayMode.Loop)
    val playMode: StateFlow<AudioPlayMode> = _playMode.asStateFlow()

    private val _playProgress = MutableStateFlow(0L)
    val playProgress: StateFlow<Long> = _playProgress.asStateFlow()

    private val _songDuration = MutableStateFlow(0L)
    val songDuration: StateFlow<Long> = _songDuration.asStateFlow()

    private val _bufferingPercent = MutableStateFlow(0)
    val bufferingPercent: StateFlow<Int> = _bufferingPercent.asStateFlow()

    private var currentIndex = 0

    private var wasPlayingBeforeVideo = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> _playState.value = AudioPlayState.Idle
                Player.STATE_BUFFERING -> _playState.value = AudioPlayState.Preparing
                Player.STATE_READY -> {
                    val player = exoPlayer ?: return
                    val duration = player.duration
                    if (duration > 0 && duration != C.TIME_UNSET) {
                        _songDuration.value = duration
                        cacheDurationToSong(duration)
                    }
                    _playState.value = if (player.playWhenReady) AudioPlayState.Playing else AudioPlayState.Pause
                    updateSongMetadataFromPlayer()
                }
                Player.STATE_ENDED -> handleCompletion()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (exoPlayer?.playbackState == Player.STATE_READY) {
                _playState.value = if (isPlaying) AudioPlayState.Playing else AudioPlayState.Pause
            }
        }
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    private fun ensurePlayer(context: Context): ExoPlayer {
        exoPlayer?.let { return it }

        appContext = context.applicationContext
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(playerListener)
        exoPlayer = player

        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            scope?.launch {
                while (isActive) {
                    val p = exoPlayer ?: break
                    if (p.isPlaying) {
                        _playProgress.value = p.currentPosition
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        return player
    }

    fun init(context: Context) {
        ensurePlayer(context)
    }

    fun stop() {
        exoPlayer?.stop()
        _playState.value = AudioPlayState.Idle
        _playProgress.value = 0
    }

    fun setPlayMode(mode: AudioPlayMode) {
        _playMode.value = mode
        exoPlayer?.repeatMode = when (mode) {
            AudioPlayMode.Single -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaylist(songs: List<AudioSong>, startIndex: Int) {
        _playlist.value = songs.toList()
        if (songs.isNotEmpty() && startIndex in songs.indices) {
            playAtIndex(startIndex)
        }
    }

    fun updatePlaylist(songs: List<AudioSong>) {
        _playlist.value = songs.toList()
    }

    fun addToPlaylist(songs: List<AudioSong>) {
        _playlist.value = _playlist.value + songs
    }

    fun removeFromPlaylist(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _playlist.value = list
        if (index < currentIndex) {
            currentIndex--
        } else if (index == currentIndex) {
            if (list.isEmpty()) {
                stop()
                _currentSong.value = null
                stopService()
            } else {
                val newIndex = index.coerceAtMost(list.size - 1)
                currentIndex = newIndex - 1
                playAtIndex(newIndex)
            }
        }
    }

    fun clearPlaylist() {
        stop()
        _playlist.value = emptyList()
        _currentSong.value = null
        currentIndex = 0
        stopService()
    }

    fun pauseForVideo() {
        val wasPlaying = _playState.value.isPlaying
        wasPlayingBeforeVideo = wasPlaying
        if (wasPlaying) {
            exoPlayer?.pause()
        }
    }

    fun resumeAfterVideo() {
        if (wasPlayingBeforeVideo) {
            wasPlayingBeforeVideo = false
            exoPlayer?.play()
        }
    }

    private fun playAtIndex(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        currentIndex = index
        val song = list[index]
        _currentSong.value = song

        val context = appContext ?: return
        val player = ensurePlayer(context)

        player.stop()
        _playState.value = AudioPlayState.Preparing
        _playProgress.value = 0
        _bufferingPercent.value = 0

        if (song.duration > 0) {
            _songDuration.value = song.duration
        }

        val dataSourceFactory = DefaultDataSource.Factory(context)

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title.ifEmpty { null })
            .setArtworkUri(song.coverPath?.let { Uri.parse(it) })
        if (song.artist.isNotEmpty()) {
            metadataBuilder.setArtist(song.artist)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(song.uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

        startService()
    }

    private fun cacheDurationToSong(duration: Long) {
        val current = _currentSong.value ?: return
        if (current.duration != duration) {
            val updated = current.copy(duration = duration)
            _currentSong.value = updated
            updatePlaylistItem(updated)
        }
        val metadata = AudioMetadataCache.get(current.uniqueKey)
        if (metadata == null || metadata.duration != duration) {
            val newMetadata = AudioMetadata(
                artist = metadata?.artist ?: current.artist,
                title = metadata?.title ?: current.title,
                duration = duration
            )
            AudioMetadataCache.put(current.uniqueKey, newMetadata)
            scope?.launch {
                withContext(Dispatchers.IO) {
                    AudioMetadataCache.saveToDisk(current.uniqueKey, newMetadata)
                }
            }
        }
    }

    private fun updateSongMetadataFromPlayer() {
        val player = exoPlayer ?: return
        val metadata = player.currentMediaItem?.mediaMetadata ?: return
        val current = _currentSong.value ?: return

        val artist = metadata.artist?.toString()?.takeIf { it.isNotEmpty() }
        val title = metadata.title?.toString()?.takeIf { it.isNotEmpty() }

        var updated = current
        if (artist != null && current.artist.isEmpty()) {
            updated = updated.copy(artist = artist)
        }
        if (title != null && current.title.isEmpty()) {
            updated = updated.copy(title = title)
        }
        if (updated != current) {
            _currentSong.value = updated
            updatePlaylistItem(updated)
        }

        val cacheArtist = artist ?: current.artist.ifEmpty { null }
        val cacheTitle = title ?: current.title.ifEmpty { null }
        if (cacheArtist != null || cacheTitle != null) {
            val existingMeta = AudioMetadataCache.get(current.uniqueKey)
            val newMeta = AudioMetadata(
                artist = cacheArtist ?: existingMeta?.artist ?: "",
                title = cacheTitle ?: existingMeta?.title ?: "",
                duration = existingMeta?.duration ?: current.duration
            )
            AudioMetadataCache.put(current.uniqueKey, newMeta)
            scope?.launch {
                withContext(Dispatchers.IO) {
                    AudioMetadataCache.saveToDisk(current.uniqueKey, newMeta)
                }
            }
        }
    }

    private fun updatePlaylistItem(updatedSong: AudioSong) {
        val list = _playlist.value.toMutableList()
        val index = list.indexOfFirst { it.uniqueKey == updatedSong.uniqueKey }
        if (index >= 0) {
            list[index] = updatedSong
            _playlist.value = list
        }
    }

    fun updateCurrentSong(updatedSong: AudioSong) {
        _currentSong.value = updatedSong
        updatePlaylistItem(updatedSong)
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

    fun play(song: AudioSong) {
        val index = _playlist.value.indexOfFirst { it.uniqueKey == song.uniqueKey }
        if (index >= 0) {
            playAtIndex(index)
        } else {
            setPlaylist(listOf(song), 0)
        }
    }

    fun playPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun next() {
        val list = _playlist.value
        if (list.isEmpty()) return
        when (_playMode.value) {
            AudioPlayMode.Shuffle -> {
                if (list.size <= 1) {
                    playAtIndex(0)
                    return
                }
                var randomIndex: Int
                do {
                    randomIndex = (0 until list.size).random()
                } while (randomIndex == currentIndex)
                playAtIndex(randomIndex)
            }
            else -> {
                val nextIndex = (currentIndex + 1) % list.size
                playAtIndex(nextIndex)
            }
        }
    }

    fun prev() {
        val list = _playlist.value
        if (list.isEmpty()) return
        when (_playMode.value) {
            AudioPlayMode.Shuffle -> {
                if (list.size <= 1) {
                    playAtIndex(0)
                    return
                }
                var randomIndex: Int
                do {
                    randomIndex = (0 until list.size).random()
                } while (randomIndex == currentIndex)
                playAtIndex(randomIndex)
            }
            else -> {
                val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
                playAtIndex(prevIndex)
            }
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    private fun startService() {
        val context = appContext ?: return
        val intent = Intent(context, com.xyoye.player_component.audio.service.AudioPlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopService() {
        val context = appContext ?: return
        context.stopService(Intent(context, com.xyoye.player_component.audio.service.AudioPlayService::class.java))
    }
}
