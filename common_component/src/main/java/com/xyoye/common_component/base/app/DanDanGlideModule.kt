package com.xyoye.common_component.base.app

import android.app.ActivityManager
import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class DanDanGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRam = activityManager.isLowRamDevice

        val memoryCacheSize = if (isLowRam) {
            16 * 1024 * 1024L
        } else {
            val maxMemory = Runtime.getRuntime().maxMemory()
            (maxMemory * 0.2).toLong().coerceIn(32_000_000L, 128_000_000L)
        }

        builder.setMemoryCache(LruResourceCache(memoryCacheSize))
    }
}
