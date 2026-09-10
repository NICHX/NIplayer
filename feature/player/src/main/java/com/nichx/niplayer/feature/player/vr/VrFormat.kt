package com.nichx.niplayer.feature.player.vr

/**
 * VR 画面格式：左右/上下 × 180°/360°。
 *
 * - [layout]：0=左右（SBS，取左半幅），1=上下（OU，取上半幅）
 * - [halfPanoDegrees]：该半幅对应的全景覆盖角度。360° 表示整帧是等距柱面全景
 *   （左右范围 ±180°）；180° 表示只涵盖正前方半球（左右范围 ±90°），如常见
 *   180° 设备录制内容。
 *
 * 枚举顺序即持久化索引（[com.nichx.niplayer.datastore.VrSettings.formatIndex]）。
 * 用户可在 VR 顶部控制条循环切换。默认 [DEFAULT] = 左右 360°。
 */
enum class VrFormat(val layout: Int, val halfPanoDegrees: Int) {
    SBS_360(0, 360),
    SBS_180(0, 180),
    OU_360(1, 360),
    OU_180(1, 180);

    companion object {
        val DEFAULT: VrFormat = SBS_360

        /** 按持久化索引取格式，越界回退默认。 */
        fun fromIndex(index: Int): VrFormat =
            entries.getOrNull(index) ?: DEFAULT
    }
}