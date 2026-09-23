package com.nichx.niplayer.feature.player

import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.player.kernel.NxVideoScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContentCropDecision] 判决表。
 *
 * 用例里的数字取自真实片源（《银河护卫队3 IMAX》3840×2024，判据位图 384×202）：
 * - 宽银幕段：内容 384×162 → 2.370（容器 1.897，上下有黑边）
 * - IMAX 扩展段：整帧 384×202 → 1.901（无黑边）
 * 同一部片子里两者交替出现，正是「可变画幅」的典型形态。
 */
class ContentCropDecisionTest {

    /** 《银河护卫队3 IMAX》的容器宽高比。 */
    private val gotgContainer = 3840f / 2024f

    private val unknown = PlayerSettings.BLACK_BAR_VERDICT_UNKNOWN
    private val notApplicable = PlayerSettings.BLACK_BAR_VERDICT_NOT_APPLICABLE

    private fun decide(
        rectWidth: Int,
        rectHeight: Int,
        previous: Float,
        containerAspect: Float = gotgContainer,
        bmpWidth: Int = 384,
        bmpHeight: Int = 202,
    ) = ContentCropDecision.decideVerdict(
        rectWidth = rectWidth,
        rectHeight = rectHeight,
        bmpWidth = bmpWidth,
        bmpHeight = bmpHeight,
        containerAspect = containerAspect,
        previousVerdict = previous,
    )

    // region 满幅帧识别

    @Test
    fun `满幅帧要求几乎铺满整帧 —— 面积不足不算满幅`() {
        // 比例接近容器但只占 88% 面积（暗场把上下误判成黑边）→ 不是满幅
        assertFalse(ContentCropDecision.isFullFrame(1.9f, 0.88f, 1.897f))
        // 真正铺满整帧 → 是满幅
        assertTrue(ContentCropDecision.isFullFrame(1.901f, 1.0f, 1.897f))
        // 比例偏离容器超过 3% → 不是满幅
        assertFalse(ContentCropDecision.isFullFrame(2.37f, 1.0f, 1.897f))
    }

    @Test
    fun `容器比例无效时不判满幅`() {
        assertFalse(ContentCropDecision.isFullFrame(1.9f, 1.0f, 0f))
    }

    // endregion

    // region 判决：可变画幅

    @Test
    fun `先出现带黑边的宽银幕段 —— 记为该内容比例`() {
        val verdict = decide(rectWidth = 384, rectHeight = 162, previous = unknown)
        assertEquals(2.370f.toDouble(), verdict.toDouble(), 0.005)
    }

    @Test
    fun `随后出现 IMAX 满幅段 —— 判为可变画幅并整片禁用`() {
        // 这一条就是《银河护卫队3 IMAX》被误裁的根因：判据必须识别出画幅在片内变化
        val verdict = decide(rectWidth = 384, rectHeight = 202, previous = 2.370f)
        assertEquals(notApplicable.toDouble(), verdict.toDouble(), 0.0)
    }

    @Test
    fun `恒定画幅片续帧保持同一比例 —— 不会误判为可变`() {
        val verdict = decide(rectWidth = 384, rectHeight = 161, previous = 2.370f)
        assertEquals(2.370f.toDouble(), verdict.toDouble(), 0.02)
    }

    @Test
    fun `不适用是终态 —— 后续任何帧都不再恢复为可裁`() {
        assertEquals(
            notApplicable.toDouble(),
            decide(rectWidth = 384, rectHeight = 162, previous = notApplicable).toDouble(),
            0.0,
        )
        assertEquals(
            notApplicable.toDouble(),
            decide(rectWidth = 384, rectHeight = 202, previous = notApplicable).toDouble(),
            0.0,
        )
    }

    // endregion

    // region 判决：无黑边 / 不成比例

    @Test
    fun `整片无黑边的满幅帧 —— 无需裁剪`() {
        val verdict = decide(rectWidth = 384, rectHeight = 202, previous = unknown)
        assertEquals(unknown.toDouble(), verdict.toDouble(), 0.0)
    }

    @Test
    fun `不成比例的矩形顶替不了原判决 —— 字幕与竖排文字不会导致裁剪`() {
        // 384×100 → 3.84，不在成品比例白名单里
        val verdict = decide(rectWidth = 384, rectHeight = 100, previous = unknown)
        assertEquals(unknown.toDouble(), verdict.toDouble(), 0.0)
    }

    @Test
    fun `暗场造成的假满幅不会把恒定画幅片判成可变`() {
        // 比例≈容器但面积只有 88% → 不算满幅；因此不会翻成「不适用」
        val verdict = decide(rectWidth = 338, rectHeight = 180, previous = 2.370f, bmpWidth = 384, bmpHeight = 202)
        assertNotEquals(notApplicable.toDouble(), verdict.toDouble(), 0.0)
    }

    @Test
    fun `尺寸非法时保持原判决`() {
        assertEquals(unknown.toDouble(), decide(rectWidth = 0, rectHeight = 0, previous = unknown).toDouble(), 0.0)
        assertEquals(2.370f.toDouble(), decide(rectWidth = 384, rectHeight = 0, previous = 2.370f).toDouble(), 0.0)
    }

    // endregion

    // region 裁剪标志与目标比例

    @Test
    fun `已测出内容比例时适应与填满需要裁剪 —— 拉伸不需要`() {
        assertTrue(ContentCropDecision.needsVideoCrop(2.370f, NxVideoScaleMode.Contain))
        assertTrue(ContentCropDecision.needsVideoCrop(2.370f, NxVideoScaleMode.Cover))
        assertFalse(ContentCropDecision.needsVideoCrop(2.370f, NxVideoScaleMode.Fill))
    }

    @Test
    fun `没有内容比例时不裁剪 —— 契约是失败即不裁`() {
        assertFalse(ContentCropDecision.needsVideoCrop(null, NxVideoScaleMode.Contain))
        assertFalse(ContentCropDecision.needsVideoCrop(null, NxVideoScaleMode.Cover))
        assertFalse(ContentCropDecision.needsVideoCrop(null, NxVideoScaleMode.Fill))
    }

    @Test
    fun `目标比例优先取内容比例 —— 无内容比例时退回视频比例`() {
        assertEquals(2.370f.toDouble(), ContentCropDecision.targetAspect(2.370f, 1.897f).toDouble(), 0.001)
        assertEquals(1.897f.toDouble(), ContentCropDecision.targetAspect(null, 1.897f).toDouble(), 0.001)
    }

    // endregion
}
