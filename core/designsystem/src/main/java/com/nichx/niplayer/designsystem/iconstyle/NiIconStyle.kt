package com.nichx.niplayer.designsystem.iconstyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.sharp.Folder
import androidx.compose.material.icons.sharp.Home
import androidx.compose.material.icons.sharp.MoreVert
import androidx.compose.material.icons.sharp.Movie
import androidx.compose.material.icons.sharp.MusicNote
import androidx.compose.material.icons.sharp.Pause
import androidx.compose.material.icons.sharp.PlayArrow
import androidx.compose.material.icons.sharp.Search
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.sharp.SkipNext
import androidx.compose.material.icons.sharp.SkipPrevious
import androidx.compose.material.icons.sharp.Star
import androidx.compose.material.icons.sharp.Favorite
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Movie
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SkipNext
import androidx.compose.material.icons.twotone.SkipPrevious
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 图标家族（material-icons 五大家族）。 */
enum class NiIconFamily(val label: String) {
    FILLED("Filled"),
    OUTLINED("Outlined"),
    ROUNDED("Rounded"),
    SHARP("Sharp"),
    TWOTONE("TwoTone"),
}

/** 图标容器形状。 */
enum class NiIconContainer(val label: String) {
    NONE("无容器"),
    CIRCLE("圆形"),
    SQUIRCLE("圆角方形"),
    PILL("胶囊"),
    SHARP_SQUARE("直角方形"),
    GRADIENT("渐变圆形"),
}

/**
 * 图标风格规格。
 *
 * @param glass 沉浸玻璃：半透明容器底 + 高对比图标（播放器场景）
 * @param gradient 渐变圆形底
 * @param outlined 描边容器（透明底 + 描边）
 */
data class NiIconStyleSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val family: NiIconFamily,
    val container: NiIconContainer,
    val glass: Boolean = false,
    val gradient: Boolean = false,
    val outlined: Boolean = false,
)

/** 预置图标风格（8 种）。 */
val NiIconStyles: List<NiIconStyleSpec> = listOf(
    NiIconStyleSpec(
        id = "system",
        name = "系统统一",
        tagline = "Filled 实心图标，tonal 圆形容器，全主题色，Material You 兼容",
        family = NiIconFamily.FILLED,
        container = NiIconContainer.CIRCLE,
    ),
    NiIconStyleSpec(
        id = "minimal",
        name = "线性极简",
        tagline = "Outlined 细线图标，无容器，轻盈现代，减少视觉噪音",
        family = NiIconFamily.OUTLINED,
        container = NiIconContainer.NONE,
    ),
    NiIconStyleSpec(
        id = "branded",
        name = "品牌圆角",
        tagline = "Filled 实心图标，圆角方形 tonal 容器，层级清晰、辨识度高",
        family = NiIconFamily.FILLED,
        container = NiIconContainer.SQUIRCLE,
    ),
    NiIconStyleSpec(
        id = "immersive",
        name = "沉浸玻璃",
        tagline = "半透明玻璃胶囊 + 白色图标，播放器场景专属沉浸感",
        family = NiIconFamily.FILLED,
        container = NiIconContainer.PILL,
        glass = true,
    ),
    NiIconStyleSpec(
        id = "sharp",
        name = "硬朗直角",
        tagline = "Sharp 直角图标，小圆角方形容器，契合 NIplayer 硬朗语言",
        family = NiIconFamily.SHARP,
        container = NiIconContainer.SHARP_SQUARE,
    ),
    NiIconStyleSpec(
        id = "rounded",
        name = "圆润亲和",
        tagline = "Rounded 圆角图标，圆形 tonal 容器，柔和友好",
        family = NiIconFamily.ROUNDED,
        container = NiIconContainer.CIRCLE,
    ),
    NiIconStyleSpec(
        id = "gradient",
        name = "渐变活力",
        tagline = "品牌渐变圆形底 + 白色图标，主按钮/播放按钮活力强调",
        family = NiIconFamily.FILLED,
        container = NiIconContainer.GRADIENT,
        gradient = true,
    ),
    NiIconStyleSpec(
        id = "outline-pill",
        name = "描边胶囊",
        tagline = "Outlined 细线图标，透明底 + 描边胶囊，现代卡片感",
        family = NiIconFamily.OUTLINED,
        container = NiIconContainer.PILL,
        outlined = true,
    ),
)

/** 全局应用图标风格：圆润亲和（Rounded 圆角图标 + 圆形 tonal 容器，圆角最大化）。 */
val NiAppIconStyle: NiIconStyleSpec = NiIconStyles.first { it.id == "rounded" }

/** 预览演示用代表图标。 */
enum class NiDemoIcon {
    Play,
    Pause,
    SkipNext,
    SkipPrevious,
    Search,
    Settings,
    MoreVert,
    Folder,
    MusicNote,
    Movie,
    Star,
    Favorite,
    Home,
}

/** 按家族解析演示图标对应的 [ImageVector]。 */
fun NiDemoIcon.vector(family: NiIconFamily): ImageVector = when (family) {
    NiIconFamily.FILLED -> when (this) {
        NiDemoIcon.Play -> Icons.Filled.PlayArrow
        NiDemoIcon.Pause -> Icons.Filled.Pause
        NiDemoIcon.SkipNext -> Icons.Filled.SkipNext
        NiDemoIcon.SkipPrevious -> Icons.Filled.SkipPrevious
        NiDemoIcon.Search -> Icons.Filled.Search
        NiDemoIcon.Settings -> Icons.Filled.Settings
        NiDemoIcon.MoreVert -> Icons.Filled.MoreVert
        NiDemoIcon.Folder -> Icons.Filled.Folder
        NiDemoIcon.MusicNote -> Icons.Filled.MusicNote
        NiDemoIcon.Movie -> Icons.Filled.Movie
        NiDemoIcon.Star -> Icons.Filled.Star
        NiDemoIcon.Favorite -> Icons.Filled.Favorite
        NiDemoIcon.Home -> Icons.Filled.Home
    }
    NiIconFamily.OUTLINED -> when (this) {
        NiDemoIcon.Play -> Icons.Outlined.PlayArrow
        NiDemoIcon.Pause -> Icons.Outlined.Pause
        NiDemoIcon.SkipNext -> Icons.Outlined.SkipNext
        NiDemoIcon.SkipPrevious -> Icons.Outlined.SkipPrevious
        NiDemoIcon.Search -> Icons.Outlined.Search
        NiDemoIcon.Settings -> Icons.Outlined.Settings
        NiDemoIcon.MoreVert -> Icons.Outlined.MoreVert
        NiDemoIcon.Folder -> Icons.Outlined.Folder
        NiDemoIcon.MusicNote -> Icons.Outlined.MusicNote
        NiDemoIcon.Movie -> Icons.Outlined.Movie
        NiDemoIcon.Star -> Icons.Outlined.Star
        NiDemoIcon.Favorite -> Icons.Outlined.Favorite
        NiDemoIcon.Home -> Icons.Outlined.Home
    }
    NiIconFamily.ROUNDED -> when (this) {
        NiDemoIcon.Play -> Icons.Rounded.PlayArrow
        NiDemoIcon.Pause -> Icons.Rounded.Pause
        NiDemoIcon.SkipNext -> Icons.Rounded.SkipNext
        NiDemoIcon.SkipPrevious -> Icons.Rounded.SkipPrevious
        NiDemoIcon.Search -> Icons.Rounded.Search
        NiDemoIcon.Settings -> Icons.Rounded.Settings
        NiDemoIcon.MoreVert -> Icons.Rounded.MoreVert
        NiDemoIcon.Folder -> Icons.Rounded.Folder
        NiDemoIcon.MusicNote -> Icons.Rounded.MusicNote
        NiDemoIcon.Movie -> Icons.Rounded.Movie
        NiDemoIcon.Star -> Icons.Rounded.Star
        NiDemoIcon.Favorite -> Icons.Rounded.Favorite
        NiDemoIcon.Home -> Icons.Rounded.Home
    }
    NiIconFamily.SHARP -> when (this) {
        NiDemoIcon.Play -> Icons.Sharp.PlayArrow
        NiDemoIcon.Pause -> Icons.Sharp.Pause
        NiDemoIcon.SkipNext -> Icons.Sharp.SkipNext
        NiDemoIcon.SkipPrevious -> Icons.Sharp.SkipPrevious
        NiDemoIcon.Search -> Icons.Sharp.Search
        NiDemoIcon.Settings -> Icons.Sharp.Settings
        NiDemoIcon.MoreVert -> Icons.Sharp.MoreVert
        NiDemoIcon.Folder -> Icons.Sharp.Folder
        NiDemoIcon.MusicNote -> Icons.Sharp.MusicNote
        NiDemoIcon.Movie -> Icons.Sharp.Movie
        NiDemoIcon.Star -> Icons.Sharp.Star
        NiDemoIcon.Favorite -> Icons.Sharp.Favorite
        NiDemoIcon.Home -> Icons.Sharp.Home
    }
    NiIconFamily.TWOTONE -> when (this) {
        NiDemoIcon.Play -> Icons.TwoTone.PlayArrow
        NiDemoIcon.Pause -> Icons.TwoTone.Pause
        NiDemoIcon.SkipNext -> Icons.TwoTone.SkipNext
        NiDemoIcon.SkipPrevious -> Icons.TwoTone.SkipPrevious
        NiDemoIcon.Search -> Icons.TwoTone.Search
        NiDemoIcon.Settings -> Icons.TwoTone.Settings
        NiDemoIcon.MoreVert -> Icons.TwoTone.MoreVert
        NiDemoIcon.Folder -> Icons.TwoTone.Folder
        NiDemoIcon.MusicNote -> Icons.TwoTone.MusicNote
        NiDemoIcon.Movie -> Icons.TwoTone.Movie
        NiDemoIcon.Star -> Icons.TwoTone.Star
        NiDemoIcon.Favorite -> Icons.TwoTone.Favorite
        NiDemoIcon.Home -> Icons.TwoTone.Home
    }
}

private fun containerShape(container: NiIconContainer): Shape? = when (container) {
    NiIconContainer.NONE -> null
    NiIconContainer.CIRCLE -> CircleShape
    NiIconContainer.SQUIRCLE -> RoundedCornerShape(12.dp)
    NiIconContainer.PILL -> RoundedCornerShape(percent = 50)
    NiIconContainer.SHARP_SQUARE -> RoundedCornerShape(4.dp)
    NiIconContainer.GRADIENT -> CircleShape
}

/**
 * 按风格规格渲染单个图标。
 *
 * @param icon 图标向量
 * @param style 图标风格规格
 * @param containerSize 容器边长（NONE 容器时忽略）
 * @param iconSize 图标边长
 * @param selected 是否选中/激活态
 */
@Composable
fun NiStyleIcon(
    icon: ImageVector,
    style: NiIconStyleSpec,
    modifier: Modifier = Modifier,
    containerSize: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    selected: Boolean = false,
    contentDescription: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = containerShape(style.container)
    val isDarkPanel = cs.surface.luminance() < 0.5f

    val tint: Color
    val bgColor: Color?
    val bgBrush: Brush?
    val borderColor: Color?

    when {
        style.glass -> {
            bgColor = if (isDarkPanel) {
                Color.White.copy(alpha = if (selected) 0.28f else 0.14f)
            } else {
                Color.Black.copy(alpha = if (selected) 0.10f else 0.06f)
            }
            tint = if (isDarkPanel) Color.White else cs.onSurface
            bgBrush = null
            borderColor = null
        }
        style.gradient -> {
            bgColor = null
            bgBrush = Brush.linearGradient(listOf(cs.primary, cs.tertiary))
            tint = cs.onPrimary
            borderColor = null
        }
        style.container == NiIconContainer.NONE -> {
            bgColor = null
            bgBrush = null
            tint = if (selected) cs.primary else cs.onSurfaceVariant
            borderColor = null
        }
        else -> {
            bgColor = if (selected) cs.primaryContainer else cs.surfaceVariant
            bgBrush = null
            tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
            borderColor = if (style.outlined) {
                if (selected) cs.primary else cs.outlineVariant
            } else {
                null
            }
        }
    }

    if (shape == null) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(iconSize),
        )
        return
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .clip(shape)
            .then(
                if (bgBrush != null) Modifier.background(bgBrush) else Modifier,
            )
            .then(
                if (bgColor != null) Modifier.background(bgColor) else Modifier,
            )
            .then(
                if (borderColor != null) Modifier.border(1.5.dp, borderColor, shape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
