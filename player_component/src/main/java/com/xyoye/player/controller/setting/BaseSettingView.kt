package com.xyoye.player.controller.setting

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorListener
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.xyoye.data_component.enums.PlayState
import com.xyoye.data_component.enums.TrackType
import com.xyoye.player.wrapper.ControlWrapper


abstract class BaseSettingView<V : ViewDataBinding> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), InterSettingView {
    protected lateinit var mControlWrapper: ControlWrapper

    private val settingLayoutId by lazy { getLayoutId() }
    private val parentView by lazy { this }

    protected val viewBinding = DataBindingUtil.inflate<V>(
        LayoutInflater.from(context),
        settingLayoutId,
        parentView,
        true
    )!!

    init {
        viewBinding.root.alpha = 0f
    }

    override fun attach(controlWrapper: ControlWrapper) {
        mControlWrapper = controlWrapper
    }

    override fun getView(): View {
        return this
    }

    override fun onSettingVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            setBackgroundColor(Color.parseColor("#80000000"))
            viewBinding.root.apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0.85f
                scaleY = 0.85f
                translationX = 0f
            }
            ViewCompat.animate(viewBinding.root)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setListener(object : ViewPropertyAnimatorListener {
                    override fun onAnimationStart(view: View) {
                        onViewShow()
                    }

                    override fun onAnimationEnd(view: View) {
                        onViewShowed()
                    }

                    override fun onAnimationCancel(view: View) {
                        onViewHide()
                    }
                })
                .start()
        } else {
            ViewCompat.animate(viewBinding.root)
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(200)
                .setListener(object : ViewPropertyAnimatorListener {
                    override fun onAnimationStart(view: View) {
                        onViewHide()
                    }

                    override fun onAnimationEnd(view: View) {
                        setBackgroundColor(Color.TRANSPARENT)
                        viewBinding.root.visibility = View.INVISIBLE
                    }

                    override fun onAnimationCancel(view: View) {

                    }
                })
                .start()
        }
    }

    override fun isSettingShowing(): Boolean {
        return viewBinding.root.alpha > 0f
    }

    override fun onVisibilityChanged(isVisible: Boolean) {

    }

    override fun onPlayStateChanged(playState: PlayState) {

    }

    override fun onProgressChanged(duration: Long, position: Long) {

    }

    override fun onLockStateChanged(isLocked: Boolean) {

    }

    override fun onPopupModeChanged(isPopup: Boolean) {

    }

    override fun onVideoSizeChanged(videoSize: Point) {

    }

    override fun onTrackChanged(type: TrackType) {

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return false
    }

    open fun getGravity() = Gravity.END

    open fun onViewShow() {

    }

    open fun onViewShowed() {

    }

    open fun onViewHide() {

    }

    abstract fun getLayoutId(): Int
}