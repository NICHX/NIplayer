package com.nichx.niplayer.player.kernel

/**
 * 视频尺寸（像素）。
 *
 * - [pixelWidthHeightRatio]：像素宽高比，非方形像素时 != 1（如 DVD 480×720 + 1.5 → 16:9）。
 * - [unappliedRotationDegrees]：未应用的旋转角度（顺时针，0/90/180/270）。
 *   media3 应用此旋转到 SurfaceView 输出，但宽度/高度不随之交换，因此 [aspectRatio]
 *   在 90°/270° 时自动交换宽高，确保 UI 层布局使用正确的显示比例。
 */
data class VideoSize(
    val width: Int,
    val height: Int,
    val pixelWidthHeightRatio: Float = 1f,
    val unappliedRotationDegrees: Int = 0,
) {
    /** 尺寸是否有效（用于排除初始 0×0 与释放后的值）。 */
    val isValid: Boolean get() = width > 0 && height > 0

    /**
     * 显示宽高比 = (width × pixelWidthHeightRatio) : height。
     * 当 [unappliedRotationDegrees] 为 90° 或 270° 时自动交换宽高，
     * 确保竖屏拍摄视频正确显示。
     */
    val aspectRatio: Float
        get() {
            if (height == 0) return 0f
            val displayW = width * pixelWidthHeightRatio
            val displayH = height.toFloat()
            return if (unappliedRotationDegrees % 180 == 0) {
                displayW / displayH
            } else {
                displayH / displayW
            }
        }
}
