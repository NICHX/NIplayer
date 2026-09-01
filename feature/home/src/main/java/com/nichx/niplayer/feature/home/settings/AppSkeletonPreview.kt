package com.nichx.niplayer.feature.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nichx.niplayer.designsystem.components.LocalHazeState
import com.nichx.niplayer.designsystem.components.niHazeEffect

/**
 * 软件实际骨架预览：精简的 NIplayer 首页结构（状态栏 / 顶栏 / 影院横幅 / 底部玻璃导航胶囊），
 * 供主题与玻璃不透明度设置页复用。
 *
 * @param cs 生效的配色方案（主题模式由方案构建，玻璃模式直接用 [androidx.compose.material3.MaterialTheme.colorScheme]）
 * @param glassTopBarOpacity 提供时进入「玻璃模式」：顶栏以 surface 半透明渲染
 * @param glassPanelOpacity  提供时中央叠出对话框（面板），按此值半透明
 * @param glassNavOpacity    提供时底部导航胶囊以 surfaceContainer 半透明渲染
 *   三参数均为 null 时为主题模式：顶栏/导航不透明 surface、不显示对话框
 */
@Composable
fun AppSkeletonPreview(
    cs: ColorScheme,
    modifier: Modifier = Modifier,
    glassTopBarOpacity: Float? = null,
    glassPanelOpacity: Float? = null,
    glassNavOpacity: Float? = null,
) {
    val glass = glassTopBarOpacity != null
    // 与真实组件一致的取色：顶栏/导航/面板均为半透明 surface/surfaceContainer，透明度滑条实时可见区别
    val topBarColor = if (glass) cs.surface.copy(alpha = glassTopBarOpacity) else cs.surface
    val navColor = if (glass) cs.surfaceContainer.copy(alpha = glassNavOpacity ?: 0f) else cs.surface
    val panelColor = if (glass) cs.surfaceContainer.copy(alpha = glassPanelOpacity ?: 0f) else cs.surfaceContainer
    // 100%：不透明，跳过模糊叠层
    val topSolid = glass && glassTopBarOpacity >= 1f
    val navSolid = glass && (glassNavOpacity ?: 0f) >= 1f
    val panelSolid = glass && (glassPanelOpacity ?: 0f) >= 1f
    val hazeState = LocalHazeState.current

    val onSurface = cs.onSurface
    val barStrong = onSurface.copy(alpha = 0.50f)
    val barMid = onSurface.copy(alpha = 0.28f)
    val barSoft = onSurface.copy(alpha = 0.14f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cs.background)
            .border(1.dp, cs.outline.copy(alpha = 0.30f), RoundedCornerShape(18.dp)),
    ) {
        // ── 内容层：影院横幅铺满全框（无标题占位条），延伸至顶栏/导航栏下方（模拟滚动到玻璃层背后） ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(cs.primary, cs.tertiary))),
            ) {
                // 遮罩：与真实首页影院横幅一致，左侧轻微加深托住前景，保留渐变色可读性
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.32f),
                                    Color.Black.copy(alpha = 0.06f),
                                ),
                            ),
                        ),
                )
            }
        }

        // ── 顶部：状态栏 + 顶栏（玻璃/主题） ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(topBarColor)
                .then(
                    // 真实玻璃模糊：与真实顶栏一致，背后内容被模糊
                    if (hazeState != null && glass && !topSolid) {
                        Modifier.niHazeEffect(state = hazeState, opacity = glassTopBarOpacity, tintColor = cs.surface)
                    } else Modifier
                ),
        ) {
            // 状态栏：左侧信号点，右侧电池
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(horizontal = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(barMid))
                    Box(Modifier.size(3.dp).clip(CircleShape).background(barMid))
                    Box(Modifier.size(3.dp).clip(CircleShape).background(barStrong))
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(barMid))
                    Box(
                        Modifier
                            .width(12.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(0.5.dp, barMid, RoundedCornerShape(2.dp)),
                    )
                }
            }
            // 顶栏：返回 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 6.dp),
            ) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(74.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(barStrong))
            }
        }

        // ── 底部：玻璃导航胶囊（首页/媒体库/设置），对齐真实底栏：图标+标签、选中项落在半透明液滴胶囊上 ──
        val dropletColor = if (cs.surface.luminance() >= 0.5f) {
            Color.Black.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.10f)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(navColor)
                .then(
                    // 真实玻璃模糊：与真实导航胶囊一致，背后内容被模糊
                    if (hazeState != null && glass && !navSolid) {
                        Modifier.niHazeEffect(state = hazeState, opacity = glassNavOpacity ?: 0f, tintColor = cs.surface)
                    } else Modifier
                )
                .border(0.5.dp, cs.outline.copy(alpha = 0.20f), RoundedCornerShape(50)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                SkeletonNavTab(
                    active = true,
                    label = "首页",
                    icon = Icons.Filled.Home,
                    dropletColor = dropletColor,
                    activeColor = cs.tertiary,
                    mutedColor = cs.onSurfaceVariant,
                )
                SkeletonNavTab(
                    active = false,
                    label = "媒体库",
                    icon = Icons.Outlined.VideoLibrary,
                    dropletColor = dropletColor,
                    activeColor = cs.tertiary,
                    mutedColor = cs.onSurfaceVariant,
                )
                SkeletonNavTab(
                    active = false,
                    label = "设置",
                    icon = Icons.Outlined.Settings,
                    dropletColor = dropletColor,
                    activeColor = cs.tertiary,
                    mutedColor = cs.onSurfaceVariant,
                )
            }
        }

        // ── 对话框（仅玻璃模式，按面板不透明度） ──
        if (glass) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.78f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(panelColor)
                    .then(
                        // 真实玻璃模糊：与真实面板一致，背后内容被模糊
                        if (hazeState != null && !panelSolid) {
                            Modifier.niHazeEffect(state = hazeState, opacity = glassPanelOpacity ?: 0f, tintColor = cs.surfaceContainer)
                        } else Modifier
                    )
                    .border(0.5.dp, cs.outline.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.40f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barStrong),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.70f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barSoft),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(cs.primary),
                        )
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .border(0.5.dp, cs.outline.copy(alpha = 0.5f), RoundedCornerShape(9.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** 底部导航项：对齐真实底栏——图标 + 标签，选中项落在半透明液滴胶囊上并用强调色。 */
@Composable
private fun RowScope.SkeletonNavTab(
    active: Boolean,
    label: String,
    icon: ImageVector,
    dropletColor: Color,
    activeColor: Color,
    mutedColor: Color,
) {
    val contentColor = if (active) activeColor else mutedColor
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(if (active) dropletColor else Color.Transparent),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
