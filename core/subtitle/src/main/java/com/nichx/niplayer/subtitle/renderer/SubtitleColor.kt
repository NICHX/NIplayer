package com.nichx.niplayer.subtitle.renderer

/**
 * ASS 颜色（RGBA，0..1 浮点）。
 *
 * ASS 原始格式为 AABBGGRR（8 位十六进制带 alpha），统一转成 RGBA 浮点供 Compose 使用。
 *
 * @property r 红 0..1
 * @property g 绿 0..1
 * @property b 蓝 0..1
 * @property a alpha 0..1（1 不透明，0 全透明）
 */
data class SubtitleColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
) {
    companion object {
        /** 纯白不透明（默认主色）。 */
        val WHITE = SubtitleColor(1f, 1f, 1f, 1f)

        /** 纯黑不透明（默认边框/阴影色）。 */
        val BLACK = SubtitleColor(0f, 0f, 0f, 1f)

        /** 透明（占位用）。 */
        val TRANSPARENT = SubtitleColor(0f, 0f, 0f, 0f)

        /**
         * 从 ASS AABBGGRR 十六进制字符串构造颜色。
         *
         * 输入示例：`&H00FFFFFF` 或 `00FFFFFF`（alpha=00 全不透明，因 ASS 中 00 表示不透明、FF 表示透明）。
         * 注意 ASS 的 alpha 语义与 RGBA 相反：ASS 0x00 = 不透明，0xFF = 透明。
         */
        fun fromAss(abgr: String): SubtitleColor {
            val cleaned = abgr.removePrefix("&H").removePrefix("&h").removeSuffix("&")
            // 不足 8 位时左侧补 0 至 8 位（AABBGGRR）
            val padded = cleaned.padStart(8, '0')
            val aAss = padded.substring(0, 2).toInt(16)
            val b = padded.substring(2, 4).toInt(16)
            val g = padded.substring(4, 6).toInt(16)
            val r = padded.substring(6, 8).toInt(16)
            // ASS alpha 反转：0x00 → 1f（不透明），0xFF → 0f（透明）
            val a = 1f - (aAss / 255f)
            return SubtitleColor(
                r = r / 255f,
                g = g / 255f,
                b = b / 255f,
                a = a.coerceIn(0f, 1f),
            )
        }

        /**
         * 从 Android ARGB Int 构造颜色（与 android.graphics.Color / Compose Color 同格式）。
         *
         * 用于将 [com.nichx.niplayer.datastore.SubtitleSettings.fontColor] / [outlineColor]
         * 转换为 [SubtitleColor] 注入字幕引擎。
         */
        fun fromArgb(argb: Int): SubtitleColor {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return SubtitleColor(
                r = r.coerceIn(0f, 1f),
                g = g.coerceIn(0f, 1f),
                b = b.coerceIn(0f, 1f),
                a = a.coerceIn(0f, 1f),
            )
        }
    }
}
