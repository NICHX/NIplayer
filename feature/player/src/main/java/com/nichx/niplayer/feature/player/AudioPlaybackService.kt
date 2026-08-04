package com.nichx.niplayer.feature.player

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
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    @Inject lateinit var playbackManager: AudioPlaybackManager

    private var mediaSession: MediaSession? = null
    private var sessionPlayer: ForwardingPlayer? = null

    companion object {
        var instance: AudioPlaybackService? = null
            private set
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_playback"
        private const val ACTION_PLAY_PAUSE = "com.nichx.niplayer.ACTION_PLAY_PAUSE"
        private const val ACTION_SKIP_NEXT = "com.nichx.niplayer.ACTION_SKIP_NEXT"
        private const val ACTION_SKIP_PREV = "com.nichx.niplayer.ACTION_SKIP_PREV"

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
        val player = playbackManager.getPlayer()
        if (player != null) {
            val forwardingPlayer = object : ForwardingPlayer(player) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }

                override fun hasNextMediaItem(): Boolean = playbackManager.hasNextInPlaylist()
                override fun hasPreviousMediaItem(): Boolean = playbackManager.hasPreviousInPlaylist()

                override fun seekToNextMediaItem() { playbackManager.playNext() }
                override fun seekToPreviousMediaItem() { playbackManager.playPrevious() }
                override fun seekToNext() { playbackManager.playNext() }
                override fun seekToPrevious() { playbackManager.playPrevious() }
            }
            sessionPlayer = forwardingPlayer
            mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
            player.addListener(notificationListener)
            registerReceiver(
                actionReceiver,
                IntentFilter().apply {
                    addAction(ACTION_PLAY_PAUSE)
                    addAction(ACTION_SKIP_NEXT)
                    addAction(ACTION_SKIP_PREV)
                },
                RECEIVER_NOT_EXPORTED,
            )
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

    private val notificationListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            pushNotification(buildNotification(mediaSession ?: return))
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            pushNotification(buildNotification(mediaSession ?: return))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            pushNotification(buildNotification(mediaSession ?: return))
        }
    }

    private fun buildNotification(session: MediaSession): Notification {
        val player = session.player
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString() ?: playbackManager.currentTitle.value.ifEmpty { getString(R.string.player_notification_playing) }
        val artist = metadata?.artist?.toString() ?: playbackManager.currentArtist.value
        val isPlaying = player.isPlaying

        val coverBitmap = playbackManager.getCoverBitmap()
            ?: loadCoverFromPath(playbackManager.audioCoverPath.value)

        val openIntent = packageManager.getLaunchIntentForPackage(
            packageName
        )?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentIntent = openIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStyle(
                MediaStyle()
                    .setMediaSession(MediaSessionCompat.Token.fromToken(session.platformToken))
                    .setShowActionsInCompactView(0, 1, 2),
            )

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }
        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        val playIcon = if (isPlaying) android.R.drawable.ic_media_pause
        else android.R.drawable.ic_media_play

        builder.addAction(android.R.drawable.ic_media_previous, getString(R.string.player_previous), buildActionIntent(ACTION_SKIP_PREV))
        builder.addAction(playIcon, if (isPlaying) getString(R.string.player_pause) else getString(R.string.player_play), buildActionIntent(ACTION_PLAY_PAUSE))
        builder.addAction(android.R.drawable.ic_media_next, getString(R.string.player_next), buildActionIntent(ACTION_SKIP_NEXT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    /**
     * 构建无 MediaSession 的占位通知，用于 player 未就绪时满足前台服务启动契约。
     * player 就绪后由 onUpdateNotification / pushNotification 更新为完整媒体通知。
     */
    private fun buildPlaceholderNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(getString(R.string.player_notification_playing))
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun loadCoverFromPath(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        // 优先使用缓存的封面 Bitmap（由 AudioPlaybackManager 在切歌时预解码设置）
        playbackManager.getCoverBitmap()?.let { return it }
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    private fun buildActionIntent(action: String): PendingIntent {
        val intent = Intent(action)
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> playbackManager.togglePlayPause()
                ACTION_SKIP_NEXT -> playbackManager.playNext()
                ACTION_SKIP_PREV -> playbackManager.playPrevious()
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        mediaSession?.player?.removeListener(notificationListener)
        instance = null
        mediaSession?.run {
            release()
            mediaSession = null
        }
        sessionPlayer = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.player_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.player_notification_channel_desc)
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
