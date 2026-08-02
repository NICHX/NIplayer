package com.nichx.niplayer.subtitle.renderer

/**
 * 字幕屏幕对齐方式（ASS Alignment 1..9）。
 *
 * ASS 数字键盘布局：
 * ```
 * 7 8 9   top-left  top-center  top-right
 * 4 5 6   mid-left  mid-center  mid-right
 * 1 2 3   bot-left  bot-center  bot-right
 * ```
 */
enum class SubtitleAlign(val assCode: Int) {
    BOTTOM_LEFT(1),
    BOTTOM_CENTER(2),
    BOTTOM_RIGHT(3),
    MIDDLE_LEFT(4),
    MIDDLE_CENTER(5),
    MIDDLE_RIGHT(6),
    TOP_LEFT(7),
    TOP_CENTER(8),
    TOP_RIGHT(9);

    companion object {
        fun fromAss(code: Int): SubtitleAlign =
            entries.firstOrNull { it.assCode == code } ?: BOTTOM_CENTER
    }
}

/**
 * 带样式的文本片段。
 *
 * 一条字幕对话被 [AssOverrideParser] 拆为多个 [StyledSpan]，
 * 每段有独立的字体样式/颜色/边框/阴影，但共用同一条字幕的位置/对齐/动画。
 *
 * @property text 文本内容（已去除 override tags，已转义 \N 已拆分为独立 span 或换行标记）
 * @property bold 是否粗体（null 表示继承 Style 默认值）
 * @property italic 是否斜体
 * @property underline 是否下划线
 * @property strikeout 是否删除线
 * @property fontSize 字体大小（px，已按 Style.fontSize + \fs 覆盖计算）；null 表示用 Style 默认
 * @property fontName 字体名；null 表示用 Style 默认
 * @property primaryColor 主色（文字颜色）；null 表示用 Style 默认
 * @property outlineColor 边框色；null 表示用 Style 默认
 * @property backColor 阴影/背景色；null 表示用 Style 默认
 * @property outlineWidth 边框宽度（px）；null 表示用 Style 默认
 * @property shadowDepth 阴影偏移（px）；null 表示用 Style 默认
 * @property rotationZ Z 轴旋转角度（度，0..360）；null 表示无旋转
 */
data class StyledSpan(
    val text: String,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikeout: Boolean? = null,
    val fontSize: Float? = null,
    val fontName: String? = null,
    val primaryColor: SubtitleColor? = null,
    val outlineColor: SubtitleColor? = null,
    val backColor: SubtitleColor? = null,
    val outlineWidth: Float? = null,
    val shadowDepth: Float? = null,
    val rotationZ: Float? = null,
)

/**
 * \t(t1,t2,\tags) 时间插值动画。
 *
 * 在 t1..t2 时间内将指定样式从当前值线性插值到目标值。
 * 支持插值的属性：[targetFontSize] [targetPrimary] [targetOutline] [targetPrimaryAlpha]
 * [targetRotationZ]。
 *
 * 不支持插值的属性（如 \b \i）按瞬时切换处理（t1 时刻直接应用）。
 *
 * @property t1 起始时间（ms，相对字幕开始）
 * @property t2 结束时间（ms，相对字幕开始）
 * @property targetFontSize 目标字号（null 表示该属性不参与动画）
 * @property targetPrimary 目标主色（null 表示不参与）
 * @property targetOutline 目标边框色（null 表示不参与）
 * @property targetPrimaryAlpha 目标主色 alpha（null 表示不参与）
 * @property targetRotationZ 目标旋转角度（null 表示不参与）
 * @property targetOutlineWidth 目标边框宽度（null 表示不参与）
 */
data class TransformAnimation(
    val t1: Long,
    val t2: Long,
    val targetFontSize: Float? = null,
    val targetPrimary: SubtitleColor? = null,
    val targetOutline: SubtitleColor? = null,
    val targetPrimaryAlpha: Float? = null,
    val targetRotationZ: Float? = null,
    val targetOutlineWidth: Float? = null,
)

/**
 * 字幕渲染数据。
 *
 * 由 [SubtitleEngine] 根据当前播放时间（已应用 [subtitleOffsetMs]）从 [TimedTextObject] 查询
 * 并经 [AssOverrideParser] 解析生成，作为 [SubtitleOverlay] 的渲染输入。
 *
 * @property spans 文本片段列表（按顺序排列，已包含换行 span）
 * @property align 屏幕对齐（来自 Style 或 \a 覆盖）
 * @property position 屏幕位置归一化（0..1, 0..1），null 表示按 [align] 自动布局
 * @property alpha 整体透明度（0..1，来自 \fad 动画计算）
 * @property rotationZ 整体 Z 轴旋转角度（度，来自 \frz 或 \t 动画）
 * @property styleFont Style 默认字体名
 * @property styleFontSize Style 默认字号（px）
 * @property stylePrimary Style 默认主色
 * @property styleOutline Style 默认边框色
 * @property styleBack Style 默认阴影色
 * @property styleOutlineWidth Style 默认边框宽度
 * @property styleShadowDepth Style 默认阴影偏移
 * @property styleShadowAlpha 阴影透明度（0~1，m-09 修复：原 SubtitleOverlay 硬编码 0.6f）
 */
data class RenderableCaption(
    val spans: List<StyledSpan>,
    val align: SubtitleAlign,
    val position: Pair<Float, Float>? = null,
    val alpha: Float = 1f,
    val rotationZ: Float = 0f,
    val styleFont: String = "sans-serif",
    val styleFontSize: Float = 36f,
    val stylePrimary: SubtitleColor = SubtitleColor.WHITE,
    val styleOutline: SubtitleColor = SubtitleColor.BLACK,
    val styleBack: SubtitleColor = SubtitleColor.BLACK,
    val styleOutlineWidth: Float = 2f,
    val styleShadowDepth: Float = 2f,
    val styleShadowAlpha: Float = 0.6f,
)
