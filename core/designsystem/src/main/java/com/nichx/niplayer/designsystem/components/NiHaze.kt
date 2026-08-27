package com.nichx.niplayer.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    return HazeStyle(
        blurRadius = blurRadius,
        backgroundColor = Color.Transparent,
        tint = HazeTint(
            surface.copy(alpha = if (surface.luminance() >= 0.5f) 0.28f else 0.40f),
        ),
    )
}

/**
 * 应用于浮层（如悬浮底栏）：真实背景模糊的液态玻璃效果，
 * 对其背后的 [niHazeSource] 内容做高斯模糊采样。
 *
 * @param state 共享的 Haze 状态
 * @param glassEnabled 是否启用玻璃
 * @param successful 渐进渐变模糊（顶栏用），null 表示均匀模糊（底栏用）
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