package com.xyoye.player.controller.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.xyoye.common_component.utils.dp2px
import com.xyoye.data_component.enums.PlayState
import com.xyoye.data_component.enums.SettingViewType
import com.xyoye.data_component.enums.TrackType
import com.xyoye.data_component.enums.VideoScreenScale
import com.xyoye.player.utils.MessageTime
import com.xyoye.player.wrapper.ControlWrapper
import com.xyoye.player_component.R
import com.xyoye.player_component.databinding.LayoutPlayerControllerBinding
import com.xyoye.player_component.ui.activities.overlay_permission.OverlayPermissionActivity

/**
 * Created by xyoye on 2022/11/13.
 */

class PlayerControlView(context: Context): InterControllerView {

    private val viewBinding = DataBindingUtil.inflate<LayoutPlayerControllerBinding>(
        LayoutInflater.from(context),
        R.layout.layout_player_controller,
        null,
        false
    )

    private val mContext = context

    private lateinit var mControlWrapper: ControlWrapper

    private var enterPopupModeBlock: (() -> Unit)? = null

    private var mIsLocked = false

    private var currentScaleIndex = 0

    private val scaleTypes = listOf(
        VideoScreenScale.SCREEN_SCALE_DEFAULT,
        VideoScreenScale.SCREEN_SCALE_MATCH_PARENT,
        VideoScreenScale.SCREEN_SCALE_CENTER_CROP
    )

    private val scaleNames = listOf("默认", "填充", "裁剪")

    init {
        viewBinding.playerLockIv.setOnClickListener {
            mControlWrapper.toggleLockState()
        }
        viewBinding.playerSpeedIv.setOnClickListener {
            mControlWrapper.showSettingView(SettingViewType.VIDEO_SPEED)
        }
        viewBinding.playerAudioIv.setOnClickListener {
            mControlWrapper.showSettingView(SettingViewType.TRACKS, TrackType.AUDIO)
        }
        viewBinding.playerSubtitleIv.setOnClickListener {
            mControlWrapper.showSettingView(SettingViewType.TRACKS, TrackType.SUBTITLE)
        }
        viewBinding.ivZoom.setOnClickListener {
            currentScaleIndex = (currentScaleIndex + 1) % scaleTypes.size
            val scaleType = scaleTypes[currentScaleIndex]
            mControlWrapper.setScreenScale(scaleType)
            showMessage("画面比例: ${scaleNames[currentScaleIndex]}", MessageTime.SHOT)
        }
        viewBinding.ivRotateScreen.setOnClickListener {
            val activity = mContext as? Activity ?: return@setOnClickListener
            val currentOrientation = mContext.resources.configuration.orientation
            activity.requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        viewBinding.ivSwitchPopup.setOnClickListener {
            if (OverlayPermissionActivity.hasOverlayPermission().not()) {
                OverlayPermissionActivity.requestOverlayPermission(mContext)
                return@setOnClickListener
            }
            enterPopupModeBlock?.invoke()
        }
        viewBinding.ivScreenshot.setOnClickListener {
            mControlWrapper.showSettingView(SettingViewType.SCREEN_SHOT)
        }
        viewBinding.ivMoreSettings.setOnClickListener {
            mControlWrapper.showSettingView(SettingViewType.PLAYER_SETTING)
        }
    }

    override fun attach(controlWrapper: ControlWrapper) {
        mControlWrapper = controlWrapper
    }

    override fun getView() = viewBinding.root

    override fun onVisibilityChanged(isVisible: Boolean) {
        updateLockVisible(isVisible)
    }

    override fun onPlayStateChanged(playState: PlayState) {

    }

    override fun onProgressChanged(duration: Long, position: Long) {

    }

    override fun onLockStateChanged(isLocked: Boolean) {
        mIsLocked = isLocked
        viewBinding.playerLockIv.isSelected = isLocked
    }

    override fun onVideoSizeChanged(videoSize: Point) {

    }

    override fun onPopupModeChanged(isPopup: Boolean) {

    }

    fun showMessage(text: String, time: MessageTime) {
        viewBinding.messageContainer.showMessage(text, time)
    }

    fun clearMessage() {
        viewBinding.messageContainer.clearMessage()
    }

    fun setEnterPopupModeObserver(block: () -> Unit) {
        enterPopupModeBlock = block
    }

    private fun updateLockVisible(isVisible: Boolean) {
        val translateX = dp2px(200).toFloat()
        if (isVisible && mIsLocked) {
            viewBinding.playerLockIv.isVisible = true
            ViewCompat.animate(viewBinding.playerLockIv)
                .translationX(0f)
                .setDuration(300)
                .start()
            viewBinding.playerRightActionLl.isVisible = false
            viewBinding.playerRightActionLl.translationX = translateX
            viewBinding.playerTopRightActionLl.isVisible = false
            viewBinding.playerTopRightActionLl.translationX = translateX
            viewBinding.playerTopLeftActionLl.isVisible = false
            viewBinding.playerTopLeftActionLl.translationX = -translateX
        } else if (isVisible) {
            viewBinding.playerLockIv.isVisible = true
            ViewCompat.animate(viewBinding.playerLockIv)
                .translationX(0f)
                .setDuration(300)
                .start()
            viewBinding.playerRightActionLl.isVisible = true
            ViewCompat.animate(viewBinding.playerRightActionLl)
                .translationX(0f)
                .setDuration(300)
                .start()
            viewBinding.playerTopRightActionLl.isVisible = true
            ViewCompat.animate(viewBinding.playerTopRightActionLl)
                .translationX(0f)
                .setDuration(300)
                .start()
            viewBinding.playerTopLeftActionLl.isVisible = true
            ViewCompat.animate(viewBinding.playerTopLeftActionLl)
                .translationX(0f)
                .setDuration(300)
                .start()
        } else {
            ViewCompat.animate(viewBinding.playerLockIv)
                .translationX(-translateX)
                .setDuration(300)
                .start()
            ViewCompat.animate(viewBinding.playerRightActionLl)
                .translationX(translateX)
                .setDuration(300)
                .withEndAction {
                    viewBinding.playerRightActionLl.visibility = View.GONE
                }
                .start()
            ViewCompat.animate(viewBinding.playerTopRightActionLl)
                .translationX(translateX)
                .setDuration(300)
                .withEndAction {
                    viewBinding.playerTopRightActionLl.visibility = View.GONE
                }
                .start()
            ViewCompat.animate(viewBinding.playerTopLeftActionLl)
                .translationX(-translateX)
                .setDuration(300)
                .withEndAction {
                    viewBinding.playerTopLeftActionLl.visibility = View.GONE
                }
                .start()
        }
    }
}