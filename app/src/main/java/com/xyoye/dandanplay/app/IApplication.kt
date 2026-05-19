package com.xyoye.dandanplay.app

import android.content.Context
import androidx.multidex.MultiDex
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.player_component.audio.manager.AudioPlayManager

/**
 * Created by xyoye on 2020/7/27.
 */

class IApplication : BaseApplication(){

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        AudioPlayManager.init(this)
    }
}