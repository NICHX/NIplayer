package com.xyoye.player.controller.setting

import android.content.Context
import android.view.KeyEvent
import com.xyoye.data_component.enums.SettingViewType
import com.xyoye.data_component.enums.TrackType
import com.xyoye.player.wrapper.InterSettingController

/**
 * Created by xyoye on 2021/4/14.
 */

class SettingController(
    private val context: Context,
    private val addView: (InterSettingView) -> Unit
) : InterSettingController {

    private lateinit var playerSettingView: PlayerSettingView
    private lateinit var switchVideoSourceView: SwitchVideoSourceView
    private lateinit var screenShotView: ScreenShotView
    private lateinit var videoSpeedView: SettingVideoSpeedView
    private lateinit var videoAspectView: SettingVideoAspectView
    private lateinit var offsetTimeView: SettingOffsetTimeView
    private lateinit var subtitleStyleView: SettingSubtitleStyleView

    private val switchSourceView by lazy {
        return@lazy SwitchSourceView(context).also { addView.invoke(it) }
    }

    private val settingTracksView by lazy {
        return@lazy SettingTracksView(context).also { addView.invoke(it) }
    }

    private val showingSettingViews = mutableListOf<InterSettingView>()
    private var isPopupMode = false

    override fun isSettingViewShowing(): Boolean {
        return showingSettingViews.find { it.isSettingShowing() } != null
    }

    override fun showSettingView(viewType: SettingViewType, extra: Any?) {
        if (isPopupMode) {
            return
        }

        val settingView = getSettingView(viewType, extra)
        if (settingView.isSettingShowing().not()) {
            showingSettingViews.add(settingView)
            settingView.onSettingVisibilityChanged(true)
        }
    }

    override fun hideSettingView() {
        val iterator = showingSettingViews.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view.isSettingShowing()) {
                view.onSettingVisibilityChanged(false)
                iterator.remove()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val iterator = showingSettingViews.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view.isSettingShowing()) {
                if (view.onKeyDown(keyCode, event)) {
                    return true
                }
            }
        }
        return false
    }

    override fun settingRelease() {

    }

    fun setSwitchVideoSourceBlock(block: (Int) -> Unit) {
        (getSettingView(SettingViewType.SWITCH_VIDEO_SOURCE) as SwitchVideoSourceView)
            .setSwitchVideoSourceBlock(block)
    }

    private fun getSettingView(type: SettingViewType, extra: Any? = null): InterSettingView {
        when (type) {
            SettingViewType.PLAYER_SETTING -> {
                if (this::playerSettingView.isInitialized.not()) {
                    playerSettingView = PlayerSettingView(context)
                    addView.invoke(playerSettingView)
                }
                return playerSettingView
            }

            SettingViewType.SWITCH_SOURCE -> {
                return switchSourceView.apply {
                    setTrackType((extra as? TrackType) ?: TrackType.SUBTITLE)
                }
            }

            SettingViewType.SWITCH_VIDEO_SOURCE -> {
                if (this::switchVideoSourceView.isInitialized.not()) {
                    switchVideoSourceView = SwitchVideoSourceView(context)
                    addView.invoke(switchVideoSourceView)
                }
                return switchVideoSourceView
            }

            SettingViewType.SCREEN_SHOT -> {
                if (this::screenShotView.isInitialized.not()) {
                    screenShotView = ScreenShotView(context)
                    addView.invoke(screenShotView)
                }
                return screenShotView
            }

            SettingViewType.VIDEO_SPEED -> {
                if (this::videoSpeedView.isInitialized.not()) {
                    videoSpeedView = SettingVideoSpeedView(context)
                    addView.invoke(videoSpeedView)
                }
                return videoSpeedView
            }

            SettingViewType.VIDEO_ASPECT -> {
                if (this::videoAspectView.isInitialized.not()) {
                    videoAspectView = SettingVideoAspectView(context)
                    addView.invoke(videoAspectView)
                }
                return videoAspectView
            }

            SettingViewType.SUBTITLE_OFFSET_TIME -> {
                if (this::offsetTimeView.isInitialized.not()) {
                    offsetTimeView = SettingOffsetTimeView(context)
                    addView.invoke(offsetTimeView)
                }
                offsetTimeView.setSettingType(type)
                return offsetTimeView
            }

            SettingViewType.SUBTITLE_STYLE -> {
                if (this::subtitleStyleView.isInitialized.not()) {
                    subtitleStyleView = SettingSubtitleStyleView(context)
                    addView.invoke(subtitleStyleView)
                }
                return subtitleStyleView
            }

            SettingViewType.TRACKS -> {
                return settingTracksView.apply {
                    setTrackType((extra as? TrackType) ?: TrackType.SUBTITLE)
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported setting view type: $type")
            }
        }
    }

    fun setPopupMode(isPopupMode: Boolean) {
        this.isPopupMode = isPopupMode
        if (isPopupMode) {
            hideSettingView()
        }
    }
}