package com.xyoye.player.controller.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.xyoye.common_component.extension.isInvalid
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.source.base.BaseVideoSource
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import com.xyoye.common_component.utils.dp2px
import com.xyoye.common_component.utils.isAudioFile
import com.xyoye.data_component.enums.PlayState
import com.xyoye.data_component.enums.SettingViewType
import com.xyoye.data_component.enums.TrackType
import com.xyoye.data_component.enums.VideoScreenScale
import com.xyoye.player.info.PlayerInitializer
import com.xyoye.player.utils.MessageTime
import com.xyoye.player.wrapper.ControlWrapper
import com.xyoye.player_component.R
import com.xyoye.player_component.databinding.LayoutPlayerControllerBinding
import com.xyoye.player_component.ui.activities.overlay_permission.OverlayPermissionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private var isAudioMode = false

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
            PlayerInitializer.isOrientationEnabled = false
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

    fun isAudioMode(): Boolean = isAudioMode

    fun loadAudioCover(videoSource: BaseVideoSource) {
        val url = videoSource.getVideoUrl()
        val title = videoSource.getVideoTitle()
        if (!isAudioFile(url) && !isAudioFile(title)) {
            viewBinding.audioCoverIv.visibility = View.GONE
            isAudioMode = false
            return
        }

        viewBinding.audioCoverIv.visibility = View.VISIBLE
        isAudioMode = true
        val uniqueKey = videoSource.getUniqueKey()

        val cachePath = ThumbnailMemoryCache.getCoverPath(uniqueKey)
        if (cachePath != null) {
            Glide.with(viewBinding.audioCoverIv)
                .load(cachePath)
                .fitCenter()
                .error(R.drawable.ic_audio_cover)
                .into(viewBinding.audioCoverIv)
            return
        }

        val cachedFile = uniqueKey.toCoverFile()
        if (cachedFile != null && cachedFile.isInvalid().not()) {
            ThumbnailMemoryCache.putCoverPath(uniqueKey, cachedFile.absolutePath)
            Glide.with(viewBinding.audioCoverIv)
                .load(cachedFile)
                .fitCenter()
                .error(R.drawable.ic_audio_cover)
                .into(viewBinding.audioCoverIv)
            return
        }

        loadEmbeddedAudioCover(url, videoSource.getHttpHeader(), uniqueKey)
    }

    private fun loadEmbeddedAudioCover(url: String, headers: Map<String, String>?, uniqueKey: String) {
        val lifecycleOwner = mContext as? LifecycleOwner ?: return
        lifecycleOwner.lifecycleScope.launchWhenCreated {
            val picture = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    if (headers != null) {
                        retriever.setDataSource(url, headers)
                    } else {
                        retriever.setDataSource(url)
                    }
                    val data = retriever.embeddedPicture
                    retriever.release()
                    data
                } catch (e: Exception) {
                    null
                }
            }
            if (picture != null) {
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                viewBinding.audioCoverIv.setImageBitmap(bitmap)
            } else {
                viewBinding.audioCoverIv.setImageResource(R.drawable.ic_audio_cover)
            }
        }
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