package com.nichx.niplayer.feature.player

import androidx.lifecycle.Lifecycle
import com.nichx.niplayer.player.kernel.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PipRules] 判定表：PiP 宽高比换算 + PiP 退出判定。
 *
 * 这两条规则都出过隐蔽缺陷，用测试钉住：
 * - **宽高比**：超出系统区间会抛 `IllegalArgumentException: Aspect ratio is too extreme`，
 *   被调用方 try/catch 吞掉 → 用户侧表现「点 PiP 按钮毫无反应」。
 * - **退出判定**：曾以 onStart 作为「已回到前台」的判据，而展开回全屏永远不会走 onStart
 *   → 展开后 250ms 播放器被 finish（本用例第 1 组锁的就是这条）。
 */
class PipRulesTest {

    // region PiP 宽高比

    /** 换算后的比值。分母是常量，故直接由分子得出，无需触碰 android.util.Rational。 */
    private fun ratioOf(size: VideoSize): Float =
        pipAspectRatioNumerator(size).toFloat() / PIP_ASPECT_DENOMINATOR

    private fun assertRatio(expected: Float, size: VideoSize, delta: Float = 0.002f) {
        assertEquals(expected.toDouble(), ratioOf(size).toDouble(), delta.toDouble())
    }

    @Test
    fun `常规 16比9 视频保持原比例`() {
        assertRatio(1.778f, VideoSize(1920, 1080))
    }

    @Test
    fun `2点40比1 影视被钳制到上界 —— 不再抛异常导致 PiP 静默失效`() {
        // 1920×800 / 3840×1600 这类 scope 影视，以及智能去黑边后的有效尺寸，都会落到这里
        assertRatio(MAX_PIP_ASPECT, VideoSize(1920, 800))
        assertRatio(MAX_PIP_ASPECT, VideoSize(3840, 1600))
    }

    @Test
    fun `超宽画面被钳制到上界`() {
        assertRatio(MAX_PIP_ASPECT, VideoSize(3840, 1080))
    }

    @Test
    fun `区间内的 2点39比1 保持原比例`() {
        assertRatio(2.387f, VideoSize(2048, 858))
    }

    @Test
    fun `极端竖屏被钳制到下界且不低于下界`() {
        val ratio = ratioOf(VideoSize(1080, 3000))
        assertTrue("比值 $ratio 低于下界 $MIN_PIP_ASPECT", ratio >= MIN_PIP_ASPECT)
        assertRatio(MIN_PIP_ASPECT, VideoSize(1080, 3000))
    }

    @Test
    fun `竖拍视频带 90 度旋转标记时按显示比例换算 —— 小窗比例不颠倒`() {
        // 1080×1920 且 unappliedRotationDegrees=90 → 实际显示为横屏 16:9
        assertRatio(1.778f, VideoSize(1080, 1920, unappliedRotationDegrees = 90))
    }

    @Test
    fun `尺寸为 0 时退回 16比9 兜底`() {
        assertRatio(16f / 9f, VideoSize(0, 0))
    }

    @Test
    fun `任何输入换算结果都落在系统区间内`() {
        val sizes = listOf(
            VideoSize(1920, 1080), VideoSize(1920, 800), VideoSize(3840, 1600),
            VideoSize(3840, 1080), VideoSize(1080, 1920), VideoSize(1080, 3000),
            VideoSize(2048, 858), VideoSize(720, 576), VideoSize(1, 1),
            VideoSize(0, 0), VideoSize(1080, 1920, unappliedRotationDegrees = 90),
            VideoSize(1920, 1080, pixelWidthHeightRatio = 1.5f),
        )
        sizes.forEach { size ->
            val ratio = ratioOf(size)
            assertTrue(
                "$size 换算出的比值 $ratio 超出 [$MIN_PIP_ASPECT, $MAX_PIP_ASPECT]",
                ratio >= MIN_PIP_ASPECT && ratio <= MAX_PIP_ASPECT,
            )
        }
    }

    // endregion

    // region PiP 退出判定

    @Test
    fun `展开回全屏时生命周期不低于 STARTED —— 不判为关闭`() {
        // 展开只走 onResume：Activity 由 PAUSED 回到 RESUMED
        assertFalse(PipExitPolicy.activityIsInvisible(Lifecycle.State.STARTED))
        assertFalse(PipExitPolicy.activityIsInvisible(Lifecycle.State.RESUMED))
    }

    @Test
    fun `关闭小窗时已走到 onStop —— 判为关闭`() {
        // onStop 之后生命周期为 CREATED；onDestroy 之后为 DESTROYED
        assertTrue(PipExitPolicy.activityIsInvisible(Lifecycle.State.CREATED))
        assertTrue(PipExitPolicy.activityIsInvisible(Lifecycle.State.DESTROYED))
    }

    @Test
    fun `PiP 会话中退出 PiP 模式后 onStop —— 判为关闭小窗`() {
        assertTrue(
            PipExitPolicy.closedPipWindow(
                inPipSession = true,
                isInPictureInPictureMode = false,
            ),
        )
    }

    @Test
    fun `PiP 中息屏同样 onStop 但仍在 PiP 模式 —— 不判为关闭`() {
        assertFalse(
            PipExitPolicy.closedPipWindow(
                inPipSession = true,
                isInPictureInPictureMode = true,
            ),
        )
    }

    @Test
    fun `普通退后台（未进过 PiP）—— 不判为关闭`() {
        assertFalse(
            PipExitPolicy.closedPipWindow(
                inPipSession = false,
                isInPictureInPictureMode = false,
            ),
        )
    }

    // endregion
}
