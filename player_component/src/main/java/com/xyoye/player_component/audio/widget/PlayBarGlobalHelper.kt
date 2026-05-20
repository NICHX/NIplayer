package com.xyoye.player_component.audio.widget

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xyoye.player_component.audio.ui.AudioPlayerActivity
import com.xyoye.player_component.ui.activities.player.PlayerActivity
import com.xyoye.player_component.ui.activities.player_interceptor.PlayerInterceptorActivity

object PlayBarGlobalHelper {

    private val addedActivities = HashSet<Activity>()

    fun addToActivity(activity: Activity) {
        if (addedActivities.contains(activity)) return
        if (activity is AudioPlayerActivity || activity is PlayerActivity || activity is PlayerInterceptorActivity) return

        activity.window?.decorView?.post {
            if (addedActivities.contains(activity)) return@post
            if (activity.isFinishing) return@post

            val contentParent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post

            if (contentParent.findViewWithTag<View>("play_bar_tag") != null) return@post

            val playBar = PlayBar(activity)
            playBar.isClickable = true
            playBar.isFocusable = true

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            contentParent.addView(playBar, params)
            addedActivities.add(activity)
        }
    }

    fun removeFromActivity(activity: Activity) {
        addedActivities.remove(activity)
    }
}