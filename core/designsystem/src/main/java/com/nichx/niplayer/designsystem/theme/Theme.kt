package com.nichx.niplayer.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 应用主题入口。
 *
 * 在 M3 浅色/深色之上增加「配色方案」维度：Blue / Purple / Teal。
 *
 * 主要定制（区别于 M3 默认）：
 * - **配色**：主色可根据 [scheme] 切换为品牌蓝、紫色或青绿；浅色冷灰背景、深色真黑背景
 * - **形状**：所有圆角减半，偏硬朗（NiShapes）
 * - **排版**：letterSpacing 归零，行高收紧
 *
 * @param darkTheme 是否使用暗色主题，默认跟随系统
 * @param scheme 配色方案，默认使用品牌蓝
 */
@Composable
fun NiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    scheme: NiScheme = NiScheme.BLUE,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NiSchemes.buildDark(scheme) else NiSchemes.buildLight(scheme)
    val extraColors = if (darkTheme) NiSchemes.buildDarkExtra(scheme) else NiSchemes.buildLightExtra(scheme)

    val configuration = LocalConfiguration.current
    val windowSizeClass = computeNiWindowSizeClass(
        widthDp = configuration.screenWidthDp.dp,
        heightDp = configuration.screenHeightDp.dp,
    )

    CompositionLocalProvider(
        LocalNiExtraColors provides extraColors,
        LocalNiScheme provides scheme,
        LocalNiWindowSizeClass provides windowSizeClass,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme,
            typography = NiTypography,
            shapes = NiShapes,
            content = content,
        )
    }
}