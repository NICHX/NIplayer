package com.nichx.niplayer.feature.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.support.v4.media.session.MediaSessionCompat

/**
 * 视频后台播放前台服务（后台仅音频）。
 *
 * 平行于 [AudioPlaybackService] 但独立实现，**不混用**音频的 [AudioPlaybackManager]：
 * 通过 [VideoBgPlaybackController] 引用当前视频的 media3 [Player]，建 MediaSession +
 * 媒体通知，命令（播放/暂停/上一/下一）转发到该控制器。前景态由 media3 兜住
 * （player 为 null 时不再自行进入前台，交由 [MediaSessionService] 的 stopSelfSafely 处理）。
 *
 * 所有权归属：播放器实例仍归 PlayerViewModel，服务**只建会话、只转发命令**，
 * 不负责 [Player.release]；释放集中在 ViewModel onCleared，届时经控制器清引用并停本服务。
 */
@OptIn(UnstableApi::class)
class VideoBgPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var sessionPlayer: ForwardingPlayer? = null

    companion object {
        var instance: VideoBgPlaybackService? = null
            private set
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "video_playback"
        private const val ACTION_PLAY_PAUSE = "com.nichx.niplayer.video_bg.ACTION_PLAY_PAUSE"
        private const val ACTION_SKIP_NEXT = "com.nichx.niplayer.video_bg.ACTION_SKIP_NEXT"
        private const val ACTION_SKIP_PREV = "com.nichx.niplayer.video_bg.ACTION_SKIP_PREV"

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
        registerReceiver(
            actionReceiver,
            IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
                addAction(ACTION_SKIP_NEXT)
                addAction(ACTION_SKIP_PREV)
            },
            RECEIVER_NOT_EXPORTED,
        )
        val player = VideoBgPlaybackController.sessionPlayer ?: return
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun hasNextMediaItem(): Boolean = VideoBgPlaybackController.onNext != null
            override fun hasPreviousMediaItem(): Boolean = VideoBgPlaybackController.onPrevious != null

            override fun seekToNextMediaItem() { VideoBgPlaybackController.onNext?.invoke() }
            override fun seekToPreviousMediaItem() { VideoBgPlaybackController.onPrevious?.invoke() }
            override fun seekToNext() { VideoBgPlaybackController.onNext?.invoke() }
            override fun seekToPrevious() { VideoBgPlaybackController.onPrevious?.invoke() }
        }
        sessionPlayer = forwardingPlayer
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
        player.addListener(notificationListener)
        startForeground(NOTIFICATION_ID, buildNotification(mediaSession!!))
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

    /** 封面异步回填后刷新当前通知（不经过 media3 事件）。 */
    fun refreshNotification() {
        mediaSession?.let { pushNotification(buildNotification(it)) }
    }

    private fun pushNotification(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
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
        val title = VideoBgPlaybackController.title
            .ifEmpty { getString(R.string.player_video_bg_playing) }
        val isPlaying = player.isPlaying

        val contentIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_video)
            .setContentTitle(title)
            .setContentText(getString(R.string.player_video_bg_playing))
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(contentIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(MediaSessionCompat.Token.fromToken(session.platformToken))
                    .setShowActionsInCompactView(0, 1, 2),
            )

        // 视频缩略图作为通知封面（large icon），由进入后台时异步回填。
        VideoBgPlaybackController.cover?.let { builder.setLargeIcon(it) }

        val playIcon = if (isPlaying) android.R.drawable.ic_media_pause
        else android.R.drawable.ic_media_play

        builder.addAction(
            android.R.drawable.ic_media_previous,
            getString(R.string.player_previous),
            buildActionIntent(ACTION_SKIP_PREV),
        )
        builder.addAction(
            playIcon,
            if (isPlaying) getString(R.string.player_pause) else getString(R.string.player_play),
            buildActionIntent(ACTION_PLAY_PAUSE),
        )
        builder.addAction(
            android.R.drawable.ic_media_next,
            getString(R.string.player_next),
            buildActionIntent(ACTION_SKIP_NEXT),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
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
            val player = sessionPlayer
            when (intent.action) {
                ACTION_PLAY_PAUSE -> if (player != null) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                ACTION_SKIP_NEXT -> VideoBgPlaybackController.onNext?.invoke()
                ACTION_SKIP_PREV -> VideoBgPlaybackController.onPrevious?.invoke()
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
                getString(R.string.player_video_bg_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.player_video_bg_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
