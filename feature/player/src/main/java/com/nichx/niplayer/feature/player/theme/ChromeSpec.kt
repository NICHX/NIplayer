package com.nichx.niplayer.feature.player.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶栏与控件的**着色族**。只有两种取值，与 `AudioPlayerStyle` 是**多对一**关系。
 *
 * - [MATERIAL]：跟随当前 Material 主题（`onSurface` / `primary` 系）—— 黑胶与简约封面；
 * - [LIGHT_ON_DARK]：固定浅色。Apple 的顶栏与控件压在封面图上，背景明暗不可控，必须用纯白。
 *
 * 为什么用枚举而不是 `Boolean`：组件内部对它的 `when` 是**穷尽**的，
 * 将来新增一族着色时编译器会指出所有待决策处，而不是静默落进 `else`。
 */
internal enum class ChromeTint {
    MATERIAL,
    LIGHT_ON_DARK,
}

/**
 * 进度条区的变体。
 *
 * Apple 的细滑块与其它主题的 Material `Slider` 是两套完全不同的实现，属于「选结构」而非「取值」，
 * 所以用枚举 + 穷尽 `when` 表达。
 */
internal enum class ProgressVariant {
    STANDARD,
    APPLE,
}

/**
 * 一个尺寸在「常规 / 紧凑」两档下的取值。
 *
 * 改造前写作 `when { appleStyle -> 46.dp; compact -> 38.dp; else -> 42.dp }` ——
 * 「主题」与「紧凑度」两个维度挤在同一个 `when` 里，新增主题会落进 `else` 拿到黑胶的尺寸。
 * 拆成「主题给一组两档值、紧凑度选其中一档」之后，两个维度各自独立、互不干扰。
 */
@Immutable
internal data class ControlMetrics(val normal: Dp, val compact: Dp) {
    /** @param compact 当前是否紧凑档。注意与同名属性区分：取的是 `this.compact`。 */
    fun pick(compact: Boolean): Dp = if (compact) this.compact else normal
}

/**
 * 顶栏与「更多」按钮组的外观规格。
 *
 * 三套主题都经过它：简约封面的 `GlassTopBar` 内部也复用 `TopBarActions`。
 *
 * ⚠️ **不含**「是否渲染更多按钮」—— 那是**调用点**的决定，不是主题属性：
 * Apple 的封面页歌名行、歌词页顶栏、横屏顶栏都直接调 `TopBarActions` 并需要这个按钮，
 * 而共享 `TopBar`（只服务黑胶）也要。把它做成主题字段会导致 Apple 那三处的按钮被一起关掉。
 */
@Immutable
internal data class ChromeSpec(
    val tint: ChromeTint,
    /** 「更多」按钮的图标。黑胶竖三点、简约封面三横线、Apple 横向三点。 */
    val moreIcon: ImageVector,
    val moreIconSize: Dp,
    val moreButtonSize: Dp,
)

/**
 * 播放控件行的外观规格。
 *
 * 只服务黑胶与 Apple：简约封面走 `GlassPlaybackControls`（独立实现），不经过这里。
 */
@Immutable
internal data class ControlSpec(
    val tint: ChromeTint,
    val progress: ProgressVariant,
    val sideButton: ControlMetrics,
    val sideIcon: ControlMetrics,
    val mainButton: ControlMetrics,
    val mainIcon: ControlMetrics,
    val gap: ControlMetrics,
    val mainElevation: ControlMetrics,
)

/**
 * 底部信息区（歌名 / 序号行）的规格。
 *
 * 只有黑胶与 Apple 走这一区 —— 简约封面把歌名/艺术家/歌词搬到了封面下方，
 * 那一区只剩细进度条与传输键（`GlassControlColumn`）。
 */
@Immutable
internal data class InfoAreaSpec(
    /** 左右内边距。Apple 参照 BitChord 的 PLAYER_GUTTER = 30dp，其余 24dp。 */
    val horizontalPadding: Dp,
    /** 水平对齐。Apple 左对齐，其余居中。 */
    val horizontalAlignment: Alignment.Horizontal,
    /**
     * 是否渲染「歌名 + 序号」行。
     *
     * Apple 的歌名在封面下方（封面页）或顶部信息条里（歌词页），底部这一区不再重复，故为 false。
     */
    val showsTitleRow: Boolean,
)

// ---------------------------------------------------------------------------
// 实例
// ---------------------------------------------------------------------------

internal val VinylChrome = ChromeSpec(
    tint = ChromeTint.MATERIAL,
    moreIcon = Icons.Rounded.MoreVert,
    moreIconSize = 24.dp,
    moreButtonSize = 40.dp,
)

internal val GlassChrome = ChromeSpec(
    tint = ChromeTint.MATERIAL,
    // 简约封面主题的顶栏用三横线（☰）而不是竖三点（⋮）
    moreIcon = Icons.Rounded.Menu,
    moreIconSize = 22.dp,
    moreButtonSize = 40.dp,
)

internal val AppleChrome = ChromeSpec(
    tint = ChromeTint.LIGHT_ON_DARK,
    // Apple Music 风格的「更多」是横向三点
    moreIcon = Icons.Rounded.MoreHoriz,
    moreIconSize = 24.dp,
    moreButtonSize = 44.dp,
)

internal val VinylControl = ControlSpec(
    tint = ChromeTint.MATERIAL,
    progress = ProgressVariant.STANDARD,
    sideButton = ControlMetrics(normal = 42.dp, compact = 38.dp),
    sideIcon = ControlMetrics(normal = 28.dp, compact = 24.dp),
    mainButton = ControlMetrics(normal = 64.dp, compact = 56.dp),
    mainIcon = ControlMetrics(normal = 36.dp, compact = 32.dp),
    gap = ControlMetrics(normal = 16.dp, compact = 12.dp),
    // 主播放键：实心主色 + 投影，建立明确的「主操作」层次
    mainElevation = ControlMetrics(normal = 8.dp, compact = 6.dp),
)

internal val VinylInfoArea = InfoAreaSpec(
    horizontalPadding = 24.dp,
    horizontalAlignment = Alignment.CenterHorizontally,
    showsTitleRow = true,
)

internal val AppleInfoArea = InfoAreaSpec(
    horizontalPadding = 30.dp,
    horizontalAlignment = Alignment.Start,
    showsTitleRow = false,
)

internal val AppleControl = ControlSpec(
    tint = ChromeTint.LIGHT_ON_DARK,
    progress = ProgressVariant.APPLE,
    // Apple 的控件更大，且**不随紧凑度收缩**（横屏也用同一套尺寸），故两档同值
    sideButton = ControlMetrics(normal = 46.dp, compact = 46.dp),
    sideIcon = ControlMetrics(normal = 30.dp, compact = 30.dp),
    mainButton = ControlMetrics(normal = 72.dp, compact = 72.dp),
    mainIcon = ControlMetrics(normal = 52.dp, compact = 52.dp),
    gap = ControlMetrics(normal = 18.dp, compact = 18.dp),
    // Apple 走扁平纯白，无投影
    mainElevation = ControlMetrics(normal = 0.dp, compact = 0.dp),
)
