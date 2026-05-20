package com.xyoye.player_component.audio.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.xyoye.common_component.extension.isNightMode
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.manager.AudioPlayManager
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.ui.AudioPlaylistDialog
import com.xyoye.player_component.databinding.LayoutPlayBarBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val viewBinding: LayoutPlayBarBinding
    private val rotateAnimator: ObjectAnimator
    private val isNight: Boolean
    private val textPrimaryColor: Int
    private val textDimColor: Int
    private val progressTrackColor: Int
    private var playlistDialog: AudioPlaylistDialog? = null

    init {
        viewBinding = LayoutPlayBarBinding.inflate(LayoutInflater.from(context), this, true)

        tag = "play_bar_tag"

        isNight = context.isNightMode()
        if (isNight) {
            textPrimaryColor = Color.WHITE
            textDimColor = ContextCompat.getColor(context, R.color.translucent_white_p50)
            progressTrackColor = Color.parseColor("#33ffffff")
        } else {
            textPrimaryColor = Color.parseColor("#DE000000")
            textDimColor = Color.parseColor("#80000000")
            progressTrackColor = Color.parseColor("#33000000")
        }

        rotateAnimator = ObjectAnimator.ofFloat(viewBinding.flCover, "rotation", 0f, 360f).apply {
            duration = 20000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
        }

        initView()
        initData()
    }

    private fun initView() {
        applyTheme()
        setupSwipe()

        viewBinding.root.setOnClickListener {
            com.alibaba.android.arouter.launcher.ARouter.getInstance()
                .build(com.xyoye.common_component.config.RouteTable.Player.AudioPlayer)
                .navigation()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            viewBinding.ivCover.clipToOutline = true
        }

        viewBinding.flPlay.setOnClickListener {
            AudioPlayManager.playPause()
        }

        viewBinding.ivPlaylist.setOnClickListener {
            val activity = context as? AppCompatActivity ?: return@setOnClickListener
            if (playlistDialog == null) {
                playlistDialog = AudioPlaylistDialog(activity, activity)
            }
            playlistDialog?.show()
        }

        viewBinding.ivClose.setOnClickListener {
            viewBinding.playBarRoot.animate()
                .translationX(20f)
                .setDuration(80)
                .withEndAction {
                    viewBinding.playBarRoot.animate()
                        .translationX(-viewBinding.playBarRoot.width.toFloat())
                        .setDuration(250)
                        .withEndAction {
                            viewBinding.playBarRoot.translationX = 0f
                            AudioPlayManager.clearPlaylist()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun applyTheme() {
        val density = resources.displayMetrics.density
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * density
            setColor(if (isNight) Color.parseColor("#303030") else Color.WHITE)
        }
        setBackground(bgDrawable)

        viewBinding.tvSongInfo.setTextColor(textPrimaryColor)
        viewBinding.playProgress.setIndicatorColor(textPrimaryColor)
        viewBinding.playProgress.trackColor = progressTrackColor
        viewBinding.playLoading.setIndicatorColor(textPrimaryColor)
        viewBinding.playLoading.trackColor = Color.TRANSPARENT

        viewBinding.ivPlay.imageTintList = android.content.res.ColorStateList.valueOf(textPrimaryColor)
        viewBinding.ivPlaylist.imageTintList = android.content.res.ColorStateList.valueOf(textPrimaryColor)
        viewBinding.ivClose.imageTintList = android.content.res.ColorStateList.valueOf(textPrimaryColor)
    }

    private fun setupSwipe() {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var isDragging = false
        var isSwiping = false

        viewBinding.tvSongInfo.setOnTouchListener { v, event ->
            val playlistSize = AudioPlayManager.playlist.value.size
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    isDragging = false
                    isSwiping = false
                    v.translationX = 0f
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.x - downX
                    if (Math.abs(diffX) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        isSwiping = true
                        val maxTranslate = v.width * 0.3f
                        v.translationX = diffX.coerceIn(-maxTranslate, maxTranslate)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - downX
                    if (isSwiping && Math.abs(diffX) > touchSlop * 2 && playlistSize > 1) {
                        val targetX = if (diffX < 0) -v.width.toFloat() else v.width.toFloat()
                        v.animate()
                            .translationX(targetX)
                            .setDuration(180)
                            .withEndAction {
                                v.translationX = 0f
                                if (diffX < 0) AudioPlayManager.next()
                                else AudioPlayManager.prev()
                            }
                            .start()
                        true
                    } else {
                        v.animate().translationX(0f).setDuration(180).start()
                        if (!isDragging) {
                            v.performClick()
                        }
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().translationX(0f).setDuration(180).start()
                    true
                }
                else -> true
            }
        }

        viewBinding.tvSongInfo.setOnClickListener {
            com.alibaba.android.arouter.launcher.ARouter.getInstance()
                .build(com.xyoye.common_component.config.RouteTable.Player.AudioPlayer)
                .navigation()
            (context as? Activity)?.overridePendingTransition(
                com.xyoye.player_component.R.anim.slide_in_bottom,
                0
            )
        }
    }

    private fun initData() {
        val lifecycleOwner = context.findLifecycleOwner()
        lifecycleOwner?.lifecycleScope?.launch {
            AudioPlayManager.currentSong.collectLatest { song ->
                if (song != null) {
                    viewBinding.playBarRoot.translationX = 0f
                    isVisible = true
                    viewBinding.tvSongInfo.setTextColor(textPrimaryColor)
                    viewBinding.tvSongInfo.text = buildSpannedString {
                        append(song.title)
                        if (song.artist.isNotEmpty()) {
                            append(" - ")
                            inSpans(ForegroundColorSpan(textDimColor)) {
                                append(song.artist)
                            }
                        }
                    }
                    val coverUri = song.coverPath ?: song.uri
                    Glide.with(viewBinding.ivCover)
                        .load(coverUri)
                        .centerCrop()
                        .error(R.drawable.bg_playing_default_cover)
                        .into(viewBinding.ivCover)
                } else {
                    isVisible = false
                }
            }
        }

        lifecycleOwner?.lifecycleScope?.launch {
            AudioPlayManager.playState.collectLatest { state ->
                viewBinding.flPlay.isEnabled = state !is AudioPlayState.Preparing
                viewBinding.ivPlay.isSelected = state.isPlaying
                viewBinding.playLoading.isVisible = state is AudioPlayState.Preparing

                if (state.isPlaying) {
                    if (rotateAnimator.isPaused) {
                        rotateAnimator.resume()
                    } else if (!rotateAnimator.isStarted) {
                        rotateAnimator.start()
                    }
                } else {
                    if (rotateAnimator.isRunning) {
                        rotateAnimator.pause()
                    }
                }
            }
        }

        lifecycleOwner?.lifecycleScope?.launch {
            AudioPlayManager.playProgress.collectLatest { progress ->
                val song = AudioPlayManager.currentSong.value
                if (song != null && song.duration > 0) {
                    viewBinding.playProgress.max = song.duration.toInt()
                    viewBinding.playProgress.progress = progress.toInt()
                }
            }
        }
    }

    private fun Context.findLifecycleOwner(): LifecycleOwner? {
        return when (this) {
            is LifecycleOwner -> this
            else -> {
                var ctx = this
                while (ctx is android.content.ContextWrapper) {
                    ctx = ctx.baseContext
                    if (ctx is LifecycleOwner) return ctx
                }
                null
            }
        }
    }
}