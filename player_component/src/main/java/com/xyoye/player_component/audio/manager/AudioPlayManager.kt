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
import androidx.media3.datasource.DefaultHttpDataSource
import com.xyoye.common_component.source.media.StorageVideoSource
import com.xyoye.common_component.utils.AudioMetadata
import com.xyoye.common_component.utils.AudioMetadataCache
import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.model.AudioSong
import com.xyoye.player_component.audio.service.AudioPlayService
import com.xyoye.player_component.audio.utils.AudioMetadataLoader
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

@androidx.media3.common.util.UnstableApi
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

    var lastNavigationDirection: Int = 0

    private var wasPlayingBeforeVideo = false

    private var _pendingSource: StorageVideoSource? = null

    private var lastSwitchTime = 0L
    private val SWITCH_THROTTLE_MS = 500L

    fun setPendingSource(source: StorageVideoSource?) {
        _pendingSource = source
    }

    fun updateSongUri(playlistIndex: Int, uri: String) {
        val list = _playlist.value.toMutableList()
        if (playlistIndex in list.indices) {
            list[playlistIndex] = list[playlistIndex].copy(uri = uri)
            _playlist.value = list
        }
    }

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            mediaItem ?: return
            val playlist = _playlist.value
            val song = playlist.find { it.uniqueKey == mediaItem.mediaId }
            if (song != null) {
                val currentUniqueKey = _currentSong.value?.uniqueKey
                if (currentUniqueKey != song.uniqueKey) {
                    _currentSong.value = song
                }
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
        stopService()
    }

    fun setPlayMode(mode: AudioPlayMode) {
        _playMode.value = mode
        val player = exoPlayer ?: return
        when (mode) {
            AudioPlayMode.Single -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }
            AudioPlayMode.Shuffle -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = true
            }
            AudioPlayMode.Loop -> {
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = false
            }
        }
    }

    fun setPlaylist(songs: List<AudioSong>, startIndex: Int) {
        _playlist.value = songs.toList()
        if (songs.isEmpty()) return

        val context = appContext ?: return
        val player = ensurePlayer(context)

        val headers = if (_pendingSource != null) {
            _pendingSource!!.indexStorageFile(startIndex).storage.getNetworkHeaders()
        } else null

        val dataSourceFactory = if (headers != null && headers.isNotEmpty()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
            DefaultDataSource.Factory(context, httpDataSourceFactory)
        } else {
            DefaultDataSource.Factory(context)
        }

        val mediaSources = songs.map { song ->
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(song.title.ifEmpty { null })
                .setArtist(song.artist.ifEmpty { null })
                .setArtworkUri(song.coverPath?.let { Uri.parse(it) })
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.uniqueKey)
                .setUri(Uri.parse(song.uri))
                .setMediaMetadata(metadataBuilder.build())
                .build()
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }

        player.setMediaSources(mediaSources)
        currentIndex = startIndex
        _currentSong.value = songs[startIndex]
        player.seekTo(startIndex, 0)
        player.prepare()
        player.play()
        startService()
    }

    fun updatePlaylist(songs: List<AudioSong>, playingIndex: Int = -1) {
        _playlist.value = songs.toList()
        if (playingIndex in songs.indices) {
            currentIndex = playingIndex
            val current = _currentSong.value
            if (current?.uniqueKey != songs[playingIndex].uniqueKey) {
                _currentSong.value = songs[playingIndex]
            }
        }
    }

    fun addToPlaylist(songs: List<AudioSong>) {
        val context = appContext ?: return
        val player = exoPlayer ?: return

        val headers = if (_pendingSource != null) {
            _pendingSource!!.indexStorageFile(0).storage.getNetworkHeaders()
        } else null

        val dataSourceFactory = if (headers != null && headers.isNotEmpty()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
            DefaultDataSource.Factory(context, httpDataSourceFactory)
        } else {
            DefaultDataSource.Factory(context)
        }

        val mediaSources = songs.map { song ->
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(song.title.ifEmpty { null })
                .setArtist(song.artist.ifEmpty { null })
                .setArtworkUri(song.coverPath?.let { Uri.parse(it) })
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.uniqueKey)
                .setUri(Uri.parse(song.uri))
                .setMediaMetadata(metadataBuilder.build())
                .build()
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }

        player.addMediaSources(mediaSources)
        _playlist.value = _playlist.value + songs
    }

    fun removeFromPlaylist(index: Int) {
        val player = exoPlayer ?: return
        val list = _playlist.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _playlist.value = list
        player.removeMediaItem(index)
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
        exoPlayer?.clearMediaItems()
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
        var song = list[index]

        val context = appContext ?: return
        val player = ensurePlayer(context)

        if (song.uri.isEmpty() && _pendingSource != null) {
            val resolved = resolveSongUri(song, index)
            if (resolved != null) {
                song = resolved
                updatePlaylistItem(song)
            }
        }

        if (song.lrcUrl == null && _pendingSource != null) {
            val resolved = resolveSongLrc(song, index)
            if (resolved != null) {
                song = resolved
                updatePlaylistItem(song)
            }
        }

        if (song.uri.isEmpty()) return

        player.stop()
        _playState.value = AudioPlayState.Preparing
        _playProgress.value = 0
        _bufferingPercent.value = 0

        _currentSong.value = song

        if (song.duration > 0) {
            _songDuration.value = song.duration
        }

        val dataSourceFactory = if (_pendingSource != null) {
            val storageFile = _pendingSource!!.indexStorageFile(currentIndex)
            val headers = storageFile.storage.getNetworkHeaders()
            if (headers != null && headers.isNotEmpty()) {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(headers)
                DefaultDataSource.Factory(context, httpDataSourceFactory)
            } else {
                DefaultDataSource.Factory(context)
            }
        } else {
            DefaultDataSource.Factory(context)
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title.ifEmpty { null })
            .setArtist(song.artist.ifEmpty { null })
            .setArtworkUri(song.coverPath?.let { Uri.parse(it) })
        val mediaItem = MediaItem.Builder()
            .setMediaId(song.uniqueKey)
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

    private fun resolveSongUri(song: AudioSong, index: Int): AudioSong? {
        val source = _pendingSource ?: return null
        return try {
            val storageFile = source.indexStorageFile(index)
            val playUrl = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                storageFile.storage.createPlayUrl(storageFile)
            }
            playUrl?.let { song.copy(uri = it) }
        } catch (_: Exception) { null }
    }

    private fun resolveSongLrc(song: AudioSong, index: Int): AudioSong? {
        val source = _pendingSource ?: return null
        return try {
            val storageFile = source.indexStorageFile(index)
            val lrcUrl = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                storageFile.storage.cacheLrc(storageFile)
            }
            lrcUrl?.let { song.copy(lrcUrl = it) }
        } catch (_: Exception) { null }
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
        val current = _currentSong.value
        _currentSong.value = updatedSong
        updatePlaylistItem(updatedSong)
    }

    private fun handleCompletion() {
        val player = exoPlayer ?: return
        when (_playMode.value) {
            AudioPlayMode.Single -> {
                player.seekTo(0)
                player.play()
            }
            else -> {
                next()
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

    suspend fun playWithMetadata(song: AudioSong, forceReload: Boolean = false, index: Int = currentIndex): AudioSong {
        val context = appContext ?: return song
        
        val cachedMetadata = AudioMetadataLoader.getCachedMetadata(song.uniqueKey)
        val cachedCoverPath = AudioMetadataLoader.getCachedCoverPath(song.uniqueKey)
        
        val needsReload = forceReload || 
            cachedMetadata == null || 
            cachedCoverPath == null ||
            (song.title.isEmpty() && cachedMetadata.title.isEmpty().not()) ||
            (song.artist.isEmpty() && cachedMetadata.artist.isEmpty().not()) ||
            song.duration == 0L && cachedMetadata.duration > 0
        
        if (needsReload) {
            val headers = if (_pendingSource != null) {
                try {
                    _pendingSource!!.indexStorageFile(index).storage.getNetworkHeaders()
                } catch (_: Exception) { null }
            } else null

            val metadataResult = AudioMetadataLoader.loadMetadata(
                context = context,
                uniqueKey = song.uniqueKey,
                uri = song.uri,
                fileName = song.fileName,
                headers = headers
            )
            
            val updatedSong = song.withMetadata(
                title = if (song.title.isEmpty()) metadataResult.title else null,
                artist = if (song.artist.isEmpty()) metadataResult.artist else null,
                coverPath = if (song.coverPath == null) metadataResult.coverPath else null,
                coverBytes = if (song.coverBytes == null) metadataResult.coverBytes else null,
                duration = if (song.duration == 0L) metadataResult.duration else null
            )
            return updatedSong
        }
        
        return song.withMetadata(
            title = if (song.title.isEmpty()) cachedMetadata?.title else null,
            artist = if (song.artist.isEmpty()) cachedMetadata?.artist else null,
            coverPath = if (song.coverPath == null) cachedCoverPath else null,
            duration = if (song.duration == 0L) cachedMetadata?.duration else null
        )
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
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < SWITCH_THROTTLE_MS) return
        lastSwitchTime = now

        lastNavigationDirection = 1
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

    fun prev() {
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < SWITCH_THROTTLE_MS) return
        lastSwitchTime = now

        lastNavigationDirection = -1
        val list = _playlist.value
        if (list.isEmpty()) return

        val prevIndex = (currentIndex - 1 + list.size) % list.size

        playAtIndex(prevIndex)
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    private fun startService() {
        val context = appContext ?: return
        val intent = Intent(context, AudioPlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopService() {
        AudioPlayService.stopService()
    }
}
