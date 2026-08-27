package com.nichx.niplayer.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * 当前的 Haze 状态，供浮层（底栏/顶栏）与内容来源共享同一个模糊实例。
 * 在根布局初始化并向下提供；不处于作用域时返回 null（禁用玻璃效果）。
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 是否启用液态玻璃效果（默认开启）。 */
val LocalNiGlassEnabled = staticCompositionLocalOf { true }

/** 液态玻璃浮层底色不透明度（0..1），由根布局读取 GlassSettings 后下发，统一控制各玻璃浮层。 */
val LocalNiGlassOpacity = staticCompositionLocalOf { 0.62f }

/**
 * 液态玻璃**面板**（对话框/菜单）底色不透明度（0..1）。
 *
 * 与 [LocalNiGlassOpacity]（导航栏/顶栏等薄浮层）分开设置：面板需要更实以保证内容可读，
 * 默认更高。由根布局读取 [com.nichx.niplayer.datastore.GlassSettings.panelOpacityFlow] 下发。
 */
val LocalNiGlassPanelOpacity = staticCompositionLocalOf { 0.82f }

/**
 * 玻璃浮层上的高对比主前景色（主文字/图标）。
 *
 * 玻璃底色是半透明叠加在动态背景上的，直接复用 onSurface 在复杂/深色背景下对比不稳定，
 * 因此给定明暗自适配的偏纯前景色：浅色主题近黑、深色主题近白。
 */
@Composable
@ReadOnlyComposable
fun glassOnSurface(): Color =
    if (NiExtraColors.current.isDark) Color(0xFFF4F4F4) else Color(0xFF161616)

/** 玻璃浮层上的高对比次要前景色（次要文字/未选中项），比 onSurfaceVariant / outline 更清晰。 */
@Composable
@ReadOnlyComposable
fun glassOnSurfaceMuted(): Color =
    if (NiExtraColors.current.isDark) Color(0xFFD6D6D6) else Color(0xFF3C3C3C)

/** 在根节点创建共享的 Haze 状态实例。 */
@Composable
fun rememberNiHazeState(): HazeState = remember { HazeState() }

/** 标记内容层为模糊来源；state 为 null 时退化为空实现。 */
fun Modifier.niHazeSource(state: HazeState?): Modifier = this.then(
    if (state != null) Modifier.hazeSource(state) else Modifier,
)

/** 顶栏/底栏统一玻璃效果配置。 */
object NiGlassDefaults {
    /** 背景高斯模糊半径。 */
    val BlurRadius = 28.dp
}

/** 玻璃描边宽度：统一 1px 细发丝线，用于对话框、菜单等面板表面及其内控件。 */
val NiGlassHairWidth: Dp = 1.dp

/** 主题感知的玻璃描边色：暗色主题偏白、亮色主题偏黑，保证在磨砂面板上清晰可见。 */
@Composable
@ReadOnlyComposable
fun niGlassBorderColor(): Color =
    if (NiExtraColors.current.isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.22f)

/**
 * 构建液态玻璃 HazeStyle：以 surface 为基底，通过 [Color.luminance] 判断明暗主题
 * 微调 tint 透明度，模拟玻璃通透折射。可配置模糊半径。
 *
 * 注意：这里 [HazeStyle.backgroundColor] 设为透明——若设为不透明，当 effect 因层级
 * 问题暂时采样不到 source 时会退化为一个纯色块，造成"一层背景挡在内容前"的假象。
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
@ReadOnlyComposable
fun niGlassStyle(blurRadius: Dp = NiGlassDefaults.BlurRadius): HazeStyle {
    val surface = MaterialTheme.colorScheme.surface
    // 暗色主题基底更重：亮色主题 0.28、暗色 0.40，再乘以统一玻璃不透明度（LocalNiGlassOpacity）
    val baseAlpha = if (surface.luminance() >= 0.5f) 0.28f else 0.40f
    val opacity = LocalNiGlassOpacity.current
    return HazeStyle(
        blurRadius = blurRadius,
        backgroundColor = Color.Transparent,
        tint = HazeTint(
            surface.copy(alpha = baseAlpha * opacity),
        ),
    )
}

/**
 * 应用于浮层（如悬浮底栏）：真实背景模糊的液态玻璃效果，
 * 对其背后的 [niHazeSource] 内容做高斯模糊采样。
 *
 * @param state 共享的 Haze 状态
 * @param glassEnabled 是否启用玻璃
 * @param progressive 渐进渐变模糊（顶栏用），null 表示均匀模糊（底栏用）
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.niHazeEffect(
    state: HazeState?,
    glassEnabled: Boolean = LocalNiGlassEnabled.current,
    progressive: HazeProgressive? = null,
): Modifier = this.then(
    if (state != null && glassEnabled) {
        Modifier.hazeEffect(
            state = state,
            style = niGlassStyle(),
        ) {
            this.progressive = progressive
        }
    } else {
        Modifier
    },
)

/** 当前是否处于玻璃作用域（存在 haze 源且玻璃开关未关闭）。 */
@Composable
fun niIsGlassActive(): Boolean =
    LocalHazeState.current != null && LocalNiGlassEnabled.current

/**
 * 主题感知的磨砂面板底色（对话框 / 下拉菜单 / 底部弹层），**始终不透明**。
 *
 * 这些面板渲染在独立的 Dialog/Popup 窗口里，窗口背景、系统压暗层与 OEM 的背景增强层
 * 都垫在面板之下——只要面板带 alpha 通道，这些"看不见的矩形"就会透出来，
 * 表现为面板上多余的分界/浅色矩形。因此改为**不透明混色**：
 * 以 background 为底、按面板不透明度设置（[LocalNiGlassPanelOpacity]）向 surfaceContainer
 * 过渡：数值语义不变（越高越实、对比越强），但像素完全不透明，从根源消除透出。
 * 玻璃总开关关闭时直接返回 surfaceContainer。
 */
@Composable
@ReadOnlyComposable
fun niFrostSurfaceColor(): Color {
    val container = MaterialTheme.colorScheme.surfaceContainer
    if (!LocalNiGlassEnabled.current) return container
    val opacity = LocalNiGlassPanelOpacity.current
    if (opacity >= 1f) return container
    return lerp(MaterialTheme.colorScheme.background, container, opacity)
}