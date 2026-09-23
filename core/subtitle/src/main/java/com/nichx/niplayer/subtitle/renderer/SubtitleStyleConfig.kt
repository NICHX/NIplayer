package com.nichx.niplayer.subtitle.renderer

/**
 * 字幕样式配置（由外层注入 [SubtitleEngine]，避免硬编码）。
 *
 * 字段对应 `:core:datastore` 中 [com.nichx.niplayer.datastore.SubtitleSettings] 的字幕样式项，
 * 由 PlayerViewModel 在创建 [SubtitleEngine] 时转换注入，并在用户改设置后更新。
 *
 * 应用规则（与 [SubtitleSettings.applyEmbeddedStyles] 文档一致）：
 * - [applyEmbeddedStyles]=true 时，[SubtitleEngine] 优先使用 ASS Style 自带的 primaryColor/outlineColor；
 *   但 [outlineWidth] 与 [shadowDepth] 始终用本配置覆盖（避免硬编码 2f 不可调）
 * - [applyEmbeddedStyles]=false 时，[primaryColor] 与 [outlineColor] 强制覆盖 ASS Style
 *
 * 字体 family 与底部边距不由本配置承载：
 * - 字体 family 由 [SubtitleOverlay] 在构建 AnnotatedString 时直接应用（不经过 [SubtitleEngine]）
 * - 底部边距由 [SubtitleOverlay] / [SubtitleView] 在布局时直接读取
 *
 * @property outlineWidth 描边宽度（px），默认 2f；0 表示无描边
 * @property shadowDepth 阴影深度（px），默认 2f；0 表示无阴影
 * @property shadowAlpha 阴影透明度（0~1），默认 0.6f（m-09 修复：原 SubtitleOverlay 硬编码 0.6f）
 * @property applyEmbeddedStyles 是否应用 ASS Style 自带颜色；false 时强制用本配置的 [primaryColor]/[outlineColor]
 * @property primaryColor 用户文字颜色（仅 applyEmbeddedStyles=false 时强制覆盖）
 * @property outlineColor 用户描边颜色（仅 applyEmbeddedStyles=false 时强制覆盖）
 * @property textSizeFactor 无 Style 字号时的默认字号（占视图高度的比例）。
 *   放在这里而非 [SubtitleEngine] 构造参数，是为了让播放中改「字号」能经
 *   [SubtitleEngine.updateStyleConfig] 立即生效 —— 构造参数只在开播时读一次，
 *   用户在字幕样式面板里调字号会「改了没反应」，直到重开播放页。
 */
data class SubtitleStyleConfig(
    val outlineWidth: Float = 2f,
    val shadowDepth: Float = 2f,
    val shadowAlpha: Float = 0.6f,
    val applyEmbeddedStyles: Boolean = true,
    val primaryColor: SubtitleColor = SubtitleColor.WHITE,
    val outlineColor: SubtitleColor = SubtitleColor.BLACK,
    val textSizeFactor: Float = 0.0533f,
)
