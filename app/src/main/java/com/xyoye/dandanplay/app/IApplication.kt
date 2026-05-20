package com.xyoye.dandanplay.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.multidex.MultiDex
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.player_component.audio.manager.AudioPlayManager
import com.xyoye.player_component.audio.widget.PlayBarGlobalHelper

/**
 * Created by xyoye on 2020/7/27.
 */

class IApplication : BaseApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        AudioPlayManager.init(this)

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                PlayBarGlobalHelper.addToActivity(activity)
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                PlayBarGlobalHelper.removeFromActivity(activity)
            }
        })
    }
}