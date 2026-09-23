package com.nichx.niplayer.feature.player

import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.player.kernel.NxVideoScaleMode

/**
 * 去黑边（内容框检测）的判决规则与裁剪标志推导。
 *
 * 抽成纯函数（不触碰 Bitmap / Compose），一是让「画幅是否变化」这类核心判断可单测，
 * 二是让 [PlayerViewModel] 与 [PlayerScreen] 共用同一份语义。
 *
 * 判决只看画面内容，不看文件名 —— 文件名不可靠：可变画幅远不止 IMAX 一种命名
 * （Open Matte 等），而 "IMAX Enhanced" 标签也常打在恒定画幅的片源上。
 */
internal object ContentCropDecision {

    /**
     * 「满幅帧」的比例容差。
     *
     * 检测位图被缩放到 ≤640px 再采样，矩形比例与容器比例存在微小误差，故用 3% 相对容差比较。
     */
    const val FULL_FRAME_TOLERANCE = 0.03f

    /**
     * 「满幅帧」的最小有效面积占比。
     *
     * 满幅帧必须几乎铺满整帧；面积门槛用于排除暗场下被误判成「小矩形」的假满幅，
     * 避免恒定画幅的片源被误判成可变画幅而整片禁用去黑边。
     */
    const val FULL_FRAME_MIN_AREA_RATIO = 0.9f

    /**
     * 判定某一帧是否为「满幅帧」（这一帧没有黑边）。
     *
     * @param contentAspect 检测出的内容宽高比
     * @param areaRatio 有效区域占整帧的面积比（0~1）
     * @param containerAspect 容器宽高比（视频原始显示比例）
     */
    fun isFullFrame(contentAspect: Float, areaRatio: Float, containerAspect: Float): Boolean =
        containerAspect > 0f &&
            areaRatio >= FULL_FRAME_MIN_AREA_RATIO &&
            kotlin.math.abs(contentAspect - containerAspect) / containerAspect <= FULL_FRAME_TOLERANCE

    /**
     * 由一次检测结果推导新判决。
     *
     * 判决单调：只会「未知 → 固定 / 不适用」或「固定 → 不适用」，
     * **永不从「不适用」恢复**，因此最多发生一次画面比例变化，不会来回跳（BUG-51）。
     *
     * - 满幅帧 + 此前已判出「有黑边」→ 片内画幅在变化（IMAX 类）→ [PlayerSettings.BLACK_BAR_VERDICT_NOT_APPLICABLE]
     * - 满幅帧 + 此前无黑边 → 整片无黑边 → [PlayerSettings.BLACK_BAR_VERDICT_UNKNOWN]
     * - 带黑边且命中成品比例白名单 → 该内容比例
     * - 其他（不成比例的矩形，多为字幕 / 暗场误判）→ 保持 [previousVerdict]
     *
     * @param rectWidth 检测出的有效区域宽（像素，检测位图坐标系）
     * @param rectHeight 检测出的有效区域高
     * @param bmpWidth 检测位图宽（用于算面积比）
     * @param bmpHeight 检测位图高
     * @param containerAspect 容器宽高比
     * @param previousVerdict 上一次的判决（编码同 [PlayerSettings.loadBlackBarVerdict]）
     */
    fun decideVerdict(
        rectWidth: Int,
        rectHeight: Int,
        bmpWidth: Int,
        bmpHeight: Int,
        containerAspect: Float,
        previousVerdict: Float,
    ): Float {
        if (rectWidth <= 0 || rectHeight <= 0 || bmpWidth <= 0 || bmpHeight <= 0) return previousVerdict
        // 已判为「不适用」就定死，后续任何帧都不再改判
        if (previousVerdict == PlayerSettings.BLACK_BAR_VERDICT_NOT_APPLICABLE) return previousVerdict

        val rectAspect = rectWidth.toFloat() / rectHeight
        val areaRatio = rectWidth.toFloat() * rectHeight / (bmpWidth.toFloat() * bmpHeight)
        val fullFrame = isFullFrame(rectAspect, areaRatio, containerAspect)
        val knownAspect = BlackBarDetector.matchesKnownAspect(rectWidth, rectHeight)

        return when {
            // 此前有黑边、这一帧却满幅 → 片内画幅在变化
            fullFrame && previousVerdict > 0f -> PlayerSettings.BLACK_BAR_VERDICT_NOT_APPLICABLE
            // 满幅帧且此前无黑边 → 整片无黑边，不需要裁剪
            fullFrame -> PlayerSettings.BLACK_BAR_VERDICT_UNKNOWN
            knownAspect -> rectAspect
            else -> previousVerdict
        }
    }

    /**
     * 是否需要让 media3 在 surface 内裁剪。
     *
     * 仅在「已测出内容比例」（目标比例 ≠ 视频比例）且当前档位不是 [NxVideoScaleMode.Fill]
     * 时需要 —— Fill 直接铺满屏幕、忽略目标比例。
     */
    fun needsVideoCrop(contentAspect: Float?, mode: NxVideoScaleMode): Boolean =
        contentAspect != null && mode != NxVideoScaleMode.Fill

    /** 目标比例：去黑边生效时取内容比例，否则取视频原始比例。 */
    fun targetAspect(contentAspect: Float?, videoAspect: Float): Float =
        contentAspect ?: videoAspect
}
