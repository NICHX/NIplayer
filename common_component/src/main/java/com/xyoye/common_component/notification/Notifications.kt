package com.xyoye.common_component.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.xyoye.common_component.extension.buildNotificationChannel

object Notifications {

    const val AUDIO_CHANNEL_ID = "audio_playback"

    fun setupNotificationChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            buildNotificationChannel(AUDIO_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW) {
                setName("音频播放")
                setDescription("音频播放控制与状态显示")
                setShowBadge(false)
            }
        )
    }
}
