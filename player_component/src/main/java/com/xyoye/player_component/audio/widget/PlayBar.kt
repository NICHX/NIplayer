package com.xyoye.player_component.audio.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.manager.AudioPlayManager
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.databinding.LayoutPlayBarBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val viewBinding: LayoutPlayBarBinding

    private val rotateAnimator: ObjectAnimator

    init {
        viewBinding = LayoutPlayBarBinding.inflate(LayoutInflater.from(context), this, true)

        rotateAnimator = ObjectAnimator.ofFloat(viewBinding.ivCover, "rotation", 0f, 360f).apply {
            duration = 20000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = LinearInterpolator()
        }

        initView()
        initData()
    }

    private fun initView() {
        viewBinding.root.setOnClickListener {
            com.alibaba.android.arouter.launcher.ARouter.getInstance()
                .build(com.xyoye.common_component.config.RouteTable.Player.AudioPlayer)
                .navigation()
        }

        viewBinding.flPlay.setOnClickListener {
            AudioPlayManager.playPause()
        }

        viewBinding.ivNext.setOnClickListener {
            AudioPlayManager.next()
        }
    }

    private fun initData() {
        val lifecycleOwner = context.findLifecycleOwner()
        lifecycleOwner?.lifecycleScope?.launch {
            AudioPlayManager.currentSong.collectLatest { song ->
                if (song != null) {
                    isVisible = true
                    viewBinding.tvSongInfo.text = buildString {
                        append(song.title)
                        if (song.artist.isNotEmpty()) {
                            append(" - ")
                            append(song.artist)
                        }
                    }
                    val coverUri = song.coverPath ?: song.uri
                    Glide.with(viewBinding.ivCover)
                        .load(coverUri)
                        .fitCenter()
                        .error(com.xyoye.common_component.R.drawable.ic_file_audio)
                        .into(viewBinding.ivCover)
                } else {
                    isVisible = false
                }
            }
        }

        lifecycleOwner?.lifecycleScope?.launch {
            AudioPlayManager.playState.collectLatest { state ->
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
