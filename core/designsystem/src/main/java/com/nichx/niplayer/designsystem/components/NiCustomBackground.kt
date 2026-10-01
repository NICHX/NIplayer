package com.nichx.niplayer.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自定义背景信息：图片本地路径 + 不透明度 + 卡片不透明度。
 *
 * @param imagePath 背景图片本地文件绝对路径（非空）
 * @param opacity   背景图片不透明度（0..1），供预览组件按此值渲染
 * @param cardOpacity 卡片表面不透明度（0..1），供 [NiTheme] 决定卡片半透明程度
 */
data class NiCustomBackground(
    val imagePath: String,
    val opacity: Float,
    val cardOpacity: Float,
)

/**
 * 当前的自定义背景（图片 + 不透明度）；未设置时为 null。
 *
 * 由根布局（MainActivity）读取 [com.nichx.niplayer.datastore.BackgroundSettings] 后下发：
 * - [NiScaffold] 读取它决定页面容器是否透明（透明则透出根布局绘制的背景图）；
 * - 主题预览等组件可读取它以还原真实观感。
 *
 * 不处于作用域时返回 null（页面使用主题背景色）。
 */
val LocalNiCustomBackground = staticCompositionLocalOf<NiCustomBackground?> { null }

/** 当前是否启用了自定义背景图（已设置且路径非空）。 */
val niHasCustomBackground: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalNiCustomBackground.current?.imagePath?.isNotBlank() == true

/** 卡片表面当前不透明度（未启用自定义背景时为 1f = 完全不透明）。 */
val niCardOpacity: Float
    @Composable
    @ReadOnlyComposable
    get() = LocalNiCustomBackground.current?.cardOpacity ?: 1f

/**
 * 卡片**内嵌**表面取色：启用自定义背景图时返回 [Color.Transparent]，避免「半透明卡片 + 半透明内嵌表面」
 * 叠加导致同一张卡片内部出现透明度不一致的分层（详见卡片缩略图/角标的底色）。
 * 未启用自定义背景图时返回 [base]，保持原观感。
 *
 * @param base 内嵌表面原本的底色（如 `NiExtraColors.current.surfaceLevel3`）
 */
@Composable
@ReadOnlyComposable
fun niNestedSurface(base: Color): Color =
    if (LocalNiCustomBackground.current != null) Color.Transparent else base

/**
 * [niNestedSurface] 的 Brush 版本：用于内嵌表面的渐变/画刷底色。
 * 启用自定义背景图时返回透明画刷，避免与半透明卡片底叠加产生分层。
 */
@Composable
@ReadOnlyComposable
fun niNestedSurfaceBrush(base: Brush): Brush =
    if (LocalNiCustomBackground.current != null) SolidColor(Color.Transparent) else base

/**
 * 卡片投影：启用自定义背景图时返回空 Modifier。
 *
 * 半透明卡片下方的投影会透过卡片本身形成一层暗色分层，故自定义背景下取消卡片投影。
 * 未启用时等价于 [shadow]。
 */
@Composable
@ReadOnlyComposable
fun Modifier.niCardShadow(shape: Shape, elevation: Dp = 1.dp): Modifier =
    if (LocalNiCustomBackground.current != null) this else this.shadow(elevation = elevation, shape = shape, clip = false)
