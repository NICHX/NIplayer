package com.xyoye.player_component.audio.widget

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import com.xyoye.player_component.R
import kotlin.math.min

class AlbumCoverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val coverBorder: Drawable? by lazy {
        ResourcesCompat.getDrawable(resources, R.drawable.bg_playing_cover_border, null)
    }

    private var discBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_playing_disc)
    private val discMatrix = Matrix()
    private var discStartX = 0
    private var discStartY = 0
    private var discCenterX = 0
    private var discCenterY = 0
    private var discRotation = 0f

    private var needleBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_playing_needle)
    private val needleMatrix = Matrix()
    private var needleStartX = 0
    private var needleStartY = 0
    private var needleCenterX = 0
    private var needleCenterY = 0
    private var needleRotation = NEEDLE_ROTATION_PAUSE

    private var coverBitmap: Bitmap? = null
    private val coverMatrix = Matrix()
    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 255
    }
    private val coverHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1a1a1a.toInt()
        style = Paint.Style.FILL
    }
    private var coverStartX = 0
    private var coverStartY = 0
    private var coverSize = 0
    private var coverBorderWidth = 0

    private var coverAlpha = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            coverPaint.alpha = (field * 255).toInt()
            invalidate()
        }
    private var coverFadeAnimator: ValueAnimator? = null

    private var slideOffset = 0f
    private var slideAnimator: ValueAnimator? = null
    private var needleLiftAnimator: ValueAnimator? = null
    private var needleDropAnimator: ValueAnimator? = null

    private var isPlaying = false
    private var switchInProgress = false
    private var pendingCover: Bitmap? = null

    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 20000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            discRotation = it.animatedValue as Float
            invalidate()
        }
    }

    private val playAnimator = ValueAnimator.ofFloat(NEEDLE_ROTATION_PAUSE, NEEDLE_ROTATION_PLAY).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            needleRotation = it.animatedValue as Float
            invalidate()
        }
    }

    private val pauseAnimator = ValueAnimator.ofFloat(NEEDLE_ROTATION_PLAY, NEEDLE_ROTATION_PAUSE).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            needleRotation = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) initSize()
    }

    private fun dp2px(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun initSize() {
        val unit = min(width / 8, height / 9)

        needleBitmap = needleBitmap.scale(unit * 2, (unit * 3.33).toInt())
        needleStartX = (width / 2 - needleBitmap.width / 5.5f).toInt()
        needleStartY = 0
        needleCenterX = width / 2
        needleCenterY = (needleBitmap.width / 5.5f).toInt()

        discBitmap = discBitmap.scale(unit * 6, unit * 6)
        val discOffsetY = (needleBitmap.height / 1.5).toInt()
        discStartX = (width - discBitmap.width) / 2
        discStartY = discOffsetY
        discCenterX = width / 2
        discCenterY = discBitmap.height / 2 + discOffsetY

        coverSize = unit * 4
        coverStartX = (width - coverSize) / 2
        coverStartY = discOffsetY + (discBitmap.height - coverSize) / 2
        coverBorderWidth = dp2px(6f)
    }

    fun initNeedle(isPlaying: Boolean) {
        needleRotation = if (isPlaying) NEEDLE_ROTATION_PLAY else NEEDLE_ROTATION_PAUSE
        if (isPlaying) {
            this.isPlaying = true
            if (!rotationAnimator.isStarted) {
                rotationAnimator.start()
            }
        } else {
            this.isPlaying = false
            discRotation = 0f
        }
        invalidate()
    }

    fun setCoverBitmap(bitmap: Bitmap?) {
        coverFadeAnimator?.cancel()
        coverAlpha = 1f
        coverBitmap = bitmap
        invalidate()
    }

    fun isSwitchInProgress(): Boolean = switchInProgress

    fun setPendingCover(bitmap: Bitmap?) {
        pendingCover = bitmap
    }

    fun fadeCover(bitmap: Bitmap, duration: Long = 300) {
        coverBitmap = bitmap
        coverAlpha = 0f
        coverFadeAnimator?.cancel()
        coverFadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                coverAlpha = it.animatedValue as Float
            }
            start()
        }
    }

    fun switchSong(newCover: Bitmap?, direction: Int, onSlideComplete: () -> Unit = {}) {
        if (direction == 0) {
            reset()
            coverBitmap = newCover
            invalidate()
            onSlideComplete()
            return
        }
        slideAnimator?.cancel()
        needleLiftAnimator?.cancel()
        needleDropAnimator?.cancel()
        playAnimator.cancel()
        pauseAnimator.cancel()
        isPlaying = false
        switchInProgress = true

        val slideWidth = width.coerceAtLeast(height)
        val slideTarget = if (direction > 0) -slideWidth.toFloat() else slideWidth.toFloat()

        // Lift needle simultaneously with slide-out
        needleLiftAnimator = ValueAnimator.ofFloat(needleRotation, NEEDLE_ROTATION_PAUSE).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                needleRotation = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }

        // Phase 1: slide old record out
        slideAnimator = ValueAnimator.ofFloat(0f, slideTarget).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                slideOffset = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    needleLiftAnimator?.cancel()
                    needleRotation = NEEDLE_ROTATION_PAUSE

                    isPlaying = false
                    discRotation = 0f
                    rotationAnimator.cancel()
                    coverBitmap = pendingCover ?: newCover
                    pendingCover = null
                    coverAlpha = 1f

                    slideOffset = -slideTarget
                    postInvalidateOnAnimation()

                    // Phase 2: slide new record in with needle dropping simultaneously
                    needleDropAnimator = ValueAnimator.ofFloat(NEEDLE_ROTATION_PAUSE, NEEDLE_ROTATION_PLAY).apply {
                        duration = 350
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            needleRotation = it.animatedValue as Float
                            postInvalidateOnAnimation()
                        }
                        start()
                    }

                    slideAnimator = ValueAnimator.ofFloat(-slideTarget, 0f).apply {
                        duration = 350
                        interpolator = DecelerateInterpolator()
                        addUpdateListener {
                            slideOffset = it.animatedValue as Float
                            postInvalidateOnAnimation()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                slideOffset = 0f
                                needleDropAnimator?.cancel()
                                needleRotation = NEEDLE_ROTATION_PLAY
                                if (pendingCover != null) {
                                    coverBitmap = pendingCover
                                    pendingCover = null
                                }
                                switchInProgress = false
                                postInvalidateOnAnimation()
                                onSlideComplete()
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

    fun start() {
        if (isPlaying) return
        if (switchInProgress) return
        isPlaying = true
        if (!rotationAnimator.isStarted) {
            rotationAnimator.start()
        } else if (rotationAnimator.isPaused) {
            rotationAnimator.resume()
        }
        if (needleRotation != NEEDLE_ROTATION_PLAY) {
            if (playAnimator.isRunning) playAnimator.cancel()
            playAnimator.start()
        }
    }

    fun pause() {
        if (!isPlaying) return
        if (switchInProgress) return
        isPlaying = false
        rotationAnimator.pause()
        if (needleRotation != NEEDLE_ROTATION_PAUSE) {
            if (pauseAnimator.isRunning) pauseAnimator.cancel()
            pauseAnimator.start()
        }
    }

    fun reset() {
        slideAnimator?.cancel()
        needleLiftAnimator?.cancel()
        needleDropAnimator?.cancel()
        switchInProgress = false
        pendingCover = null
        slideOffset = 0f
        coverFadeAnimator?.cancel()
        coverAlpha = 1f
        isPlaying = false
        discRotation = 0f
        needleRotation = NEEDLE_ROTATION_PAUSE
        rotationAnimator.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (discBitmap.isRecycled || needleBitmap.isRecycled) return

        canvas.save()
        canvas.translate(slideOffset, 0f)

        // 1. Draw cover image
        coverBitmap?.let { cover ->
            coverMatrix.setRotate(discRotation, discCenterX.toFloat(), discCenterY.toFloat())
            coverMatrix.preTranslate(coverStartX.toFloat(), coverStartY.toFloat())
            coverMatrix.preScale(coverSize.toFloat() / cover.width, coverSize.toFloat() / cover.height)
            canvas.drawBitmap(cover, coverMatrix, coverPaint)
        } ?: run {
            canvas.drawCircle(discCenterX.toFloat(), discCenterY.toFloat(), (coverSize / 2f), coverHolePaint)
        }

        // 2. Draw cover border
        coverBorder?.setBounds(
            discStartX - coverBorderWidth,
            discStartY - coverBorderWidth,
            discStartX + discBitmap.width + coverBorderWidth,
            discStartY + discBitmap.height + coverBorderWidth
        )
        coverBorder?.draw(canvas)

        // 3. Draw disc
        discMatrix.setRotate(discRotation, discCenterX.toFloat(), discCenterY.toFloat())
        discMatrix.preTranslate(discStartX.toFloat(), discStartY.toFloat())
        canvas.drawBitmap(discBitmap, discMatrix, null)

        canvas.restore()

        // 4. Draw needle (not affected by slide offset)
        needleMatrix.setRotate(needleRotation, needleCenterX.toFloat(), needleCenterY.toFloat())
        needleMatrix.preTranslate(needleStartX.toFloat(), needleStartY.toFloat())
        canvas.drawBitmap(needleBitmap, needleMatrix, null)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        slideAnimator?.cancel()
        coverFadeAnimator?.cancel()
        needleLiftAnimator?.cancel()
        needleDropAnimator?.cancel()
        rotationAnimator.cancel()
        playAnimator.cancel()
        pauseAnimator.cancel()
    }

    companion object {
        private const val NEEDLE_ROTATION_PLAY = 0f
        private const val NEEDLE_ROTATION_PAUSE = -25f
    }
}
