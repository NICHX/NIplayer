package com.nichx.niplayer.feature.player.theme

import androidx.compose.runtime.Immutable
import com.nichx.niplayer.datastore.AudioPlayerStyle

/**
 * 播放器**骨架**：决定「这块 UI 归谁」。
 *
 * 骨架与主题是**多对一**的 —— 新增主题若复用某个骨架（例如「换个配色的简约封面」），
 * 只需在 [themeOf] 里映射到同一个骨架，布局代码一行不用改。
 *
 * - [DISC]：黑胶 —— 唱盘 + 唱针 + 跟手换片 + 邻居唱片预览；
 * - [CARD]：简约封面 —— 方形封面卡 + 悬浮动画 + 封面下方信息栈；
 * - [APPLE_BANNER]：Apple Music —— 贴顶全宽封面 + 独立歌词页 + 色彩网格背景。
 */
internal enum class PlayerSkeleton { DISC, CARD, APPLE_BANNER }

/**
 * 播放器主题的共享描述。
 *
 * 设计规则：**只放「三套主题共有的同一概念」**。骨架独有的几何（黑胶的拖动参数、
 * 简约封面的悬浮参数、Apple 的封面高度）留在各自骨架内，避免造出一个满是默认值的万能 spec。
 *
 * 收口进度：歌词页（阶段 1）→ 顶栏与控件（阶段 2）→ 封面页几何（阶段 3，尚未做）。
 */
@Immutable
internal data class PlayerThemeTokens(
    val lyrics: LyricsTheme,
    val chrome: ChromeSpec,
    val control: ControlSpec,
    val infoArea: InfoAreaSpec,
    /**
     * 骨架。布局里的「这块 UI 属于哪个主题」一律读它，而不是去比较 `AudioPlayerStyle`。
     *
     * 这样新增主题时，编译器会在 [themeOf] 的穷尽 `when` 处点名一次，
     * 而不是让新主题在几十处 `style == APPLE_MUSIC` 里静默走错分支。
     */
    val skeleton: PlayerSkeleton,
)

/**
 * 全项目**唯一**的 `AudioPlayerStyle` → 主题映射点。
 *
 * 这里必须保持穷尽 `when`：新增一套主题时，编译器会在此处直接报缺分支，
 * 而不是让新主题静默套用某套旧主题的外观（这正是改造前 85 处布尔分支的病灶）。
 */
internal fun themeOf(style: AudioPlayerStyle): PlayerThemeTokens = when (style) {
    AudioPlayerStyle.VINYL -> VinylTheme
    AudioPlayerStyle.GLASS -> GlassTheme
    AudioPlayerStyle.APPLE_MUSIC -> AppleTheme
}

internal val VinylTheme = PlayerThemeTokens(
    lyrics = VinylLyrics,
    chrome = VinylChrome,
    control = VinylControl,
    infoArea = VinylInfoArea,
    skeleton = PlayerSkeleton.DISC,
)

internal val GlassTheme = PlayerThemeTokens(
    lyrics = GlassLyrics,
    chrome = GlassChrome,
    // 简约封面走 GlassPlaybackControls（独立实现），**不读**这份 spec ——
    // 与黑胶同值只为让字段非空。将来若改走共享控件，必须按 GlassPlaybackControls 的实际数值重新取值。
    control = VinylControl,
    // 同理：简约封面把歌名/艺术家/歌词搬到封面下方，不读这一区
    infoArea = VinylInfoArea,
    skeleton = PlayerSkeleton.CARD,
)

internal val AppleTheme = PlayerThemeTokens(
    lyrics = AppleLyrics,
    chrome = AppleChrome,
    control = AppleControl,
    infoArea = AppleInfoArea,
    skeleton = PlayerSkeleton.APPLE_BANNER,
)
