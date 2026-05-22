package com.xyoye.player_component.audio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.manager.AudioPlayManager
import com.xyoye.player_component.audio.model.AudioSong
import com.xyoye.player_component.audio.ui.AudioPlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class AudioPlayService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        var instance: AudioPlayService? = null
            private set
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_PLAY_PAUSE = "com.xyoye.player.ACTION_PLAY_PAUSE"
        private const val ACTION_SKIP_NEXT = "com.xyoye.player.ACTION_SKIP_NEXT"
        private const val ACTION_SKIP_PREV = "com.xyoye.player.ACTION_SKIP_PREV"

        fun isRunning(): Boolean = instance != null

        fun stopService() {
            instance?.apply {
                stopForeground(STOP_FOREGROUND_REMOVE)
                mediaSession?.run {
                    release()
                    mediaSession = null
                }
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        val player = AudioPlayManager.getExoPlayer()
        if (player != null) {
            val forwardingPlayer = object : ForwardingPlayer(player) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }

                override fun hasNextMediaItem(): Boolean = true

                override fun hasPreviousMediaItem(): Boolean = true

                override fun seekToNextMediaItem() {
                    AudioPlayManager.next()
                }

                override fun seekToPreviousMediaItem() {
                    AudioPlayManager.prev()
                }

                override fun seekToNext() {
                    AudioPlayManager.next()
                }

                override fun seekToPrevious() {
                    AudioPlayManager.prev()
                }
            }
            mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
            player.addListener(stateListener)
            registerReceiver(notificationActionReceiver, IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
                addAction(ACTION_SKIP_NEXT)
                addAction(ACTION_SKIP_PREV)
            }, RECEIVER_NOT_EXPORTED)

            serviceScope.launch {
                AudioPlayManager.currentSong.collectLatest {
                    rebuildAndPushNotification()
                }
            }

            startForeground(NOTIFICATION_ID, buildNotification(mediaSession!!))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val notification = buildNotification(session)
        if (startInForegroundRequired) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            pushNotification(notification)
        }
    }

    private fun pushNotification(notification: Notification) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun rebuildAndPushNotification() {
        val session = mediaSession ?: return
        pushNotification(buildNotification(session))
    }

    private val stateListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            rebuildAndPushNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            rebuildAndPushNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            rebuildAndPushNotification()
        }
    }

    private fun buildNotification(session: MediaSession): Notification {
        val player = session.player
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString() ?: "正在播放"
        val artist = metadata?.artist?.toString() ?: ""
        val isPlaying = player.isPlaying

        val currentSong = AudioPlayManager.currentSong.value
        val coverBitmap = getCoverBitmap(currentSong)

        val openIntent = Intent(this, AudioPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "audio_playback")
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        val playIcon = if (isPlaying) R.drawable.ic_play_bar_pause
        else R.drawable.ic_play_bar_play

        builder.addAction(R.drawable.ic_previous, "上一首", buildActionIntent(ACTION_SKIP_PREV))
        builder.addAction(playIcon, if (isPlaying) "暂停" else "播放", buildActionIntent(ACTION_PLAY_PAUSE))
        builder.addAction(R.drawable.ic_next, "下一首", buildActionIntent(ACTION_SKIP_NEXT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun getCoverBitmap(song: AudioSong?): Bitmap? {
        if (song == null) return null

        if (song.coverBytes != null) {
            return try {
                BitmapFactory.decodeByteArray(song.coverBytes, 0, song.coverBytes.size)
            } catch (_: Exception) { null }
        }

        if (song.coverPath != null) {
            return try {
                val file = java.io.File(song.coverPath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(song.coverPath)
                } else null
            } catch (_: Exception) { null }
        }

        return null
    }

    private fun buildActionIntent(action: String): PendingIntent {
        val intent = Intent(action)
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    AudioPlayManager.playPause()
                }
                ACTION_SKIP_NEXT -> {
                    AudioPlayManager.next()
                }
                ACTION_SKIP_PREV -> {
                    AudioPlayManager.prev()
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(notificationActionReceiver)
        mediaSession?.player?.removeListener(stateListener)
        instance = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "audio_playback",
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
