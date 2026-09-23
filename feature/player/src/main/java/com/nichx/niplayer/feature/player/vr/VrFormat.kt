package com.nichx.niplayer.feature.player.vr

/**
 * VR 画面格式：布局（整幅 / 左右 / 上下）× 单幅覆盖角度（180° / 360°）。
 *
 * - [layout]：
 *   - `0` 左右（SBS，取左半幅）
 *   - `1` 上下（OU，取上半幅）
 *   - `2` 整幅（mono，整帧即一幅全景）
 *
 *   前两者用于**左右/上下打包的立体全景**（只戴/只用单眼观看时取其中一只眼）；
 *   `2` 用于最常见的**单眼（mono）全景** —— 整帧就是一幅等距柱面图，不需要取半幅。
 * - [halfPanoDegrees]：单幅对应的全景覆盖角度。`360` 表示整幅是完整等距柱面
 *   （经度范围 ±180°）；`180` 表示只涵盖正前方半球（±90°）。
 *
 * **几何假设**：单幅一律按等距柱面采样 —— 经度占横向、纬度占纵向，因此单幅区域的像素
 * 宽高比应为 `2 × halfPanoDegrees : 180`（360° → 2:1，180° → 1:1）。片源不满足该比例时
 * 画面会被拉伸。
 *
 * 枚举顺序即持久化索引（[com.nichx.niplayer.datastore.VrSettings.formatIndex]）。
 * **新增项一律追加到末尾**，否则已保存的索引会指向另一种格式。
 */
enum class VrFormat(val layout: Int, val halfPanoDegrees: Int) {
    SBS_360(0, 360),
    SBS_180(0, 180),
    OU_360(1, 360),
    OU_180(1, 180),
    FULL_360(2, 360),
    FULL_180(2, 180);

    companion object {
        /** 默认格式：左右 360°（立体打包的全景最常见于 3D 片源）。 */
        val DEFAULT: VrFormat = SBS_360

        /** 按持久化索引取格式，越界回退默认。 */
        fun fromIndex(index: Int): VrFormat =
            entries.getOrNull(index) ?: DEFAULT
    }
}
