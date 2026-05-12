package com.xyoye.common_component.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.BuildConfig
import com.xyoye.common_component.extension.notificationManager
import com.xyoye.common_component.notification.Notifications
/**
 * Created by xyoye on 2022/9/14
 */

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 不再需要处理投屏相关的通知
    }
}