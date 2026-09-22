package com.nichx.niplayer.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 应用主题入口。
 *
 * 在 M3 浅色/深色之上增加「配色方案」维度：按 [scheme] 切换整套配色。
 *
 * 主要定制（区别于 M3 默认）：
 * - **配色**：主色/次级/三级色可按方案切换；浅色冷灰背景、深色真黑背景
 * - **形状**：所有圆角减半，偏硬朗（NiShapes）
 * - **排版**：letterSpacing 归零，行高收紧
 *
 * @param darkTheme 是否使用暗色主题，默认跟随系统
 * @param scheme 配色方案
 */
@Composable
fun NiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    scheme: NiScheme = NiScheme.MISTY,
    content: @Composable () -> Unit,
) {
    // P0-3 修复（2026-09-22）：配色构建是纯函数，但原先每次重组都重跑。
    // 代价：lightColorScheme / darkColorScheme 各 28 个具名参数，外加 buildExtra 的
    // generateTonalScale（10 次 Color.hsl 转换）与 storageColors。
    // 更关键的是 MaterialTheme 的 LocalColorScheme 为 staticCompositionLocalOf —— 实例一变，
    // 整棵子树**无条件**重组（不区分是否读取过 colorScheme）。而 MainActivity 在最外层
    // collect 了玻璃不透明度，拖动该滑杆会每帧触发 NiTheme 重组 → 每帧全应用重组。
    // 按 (darkTheme, scheme) 缓存后，配色不变时保持同一实例引用，滑杆拖动不再波及全树。
    val colorScheme = remember(darkTheme, scheme) {
        if (darkTheme) NiSchemes.buildDark(scheme) else NiSchemes.buildLight(scheme)
    }
    val extraColors = remember(darkTheme, scheme) {
        if (darkTheme) NiSchemes.buildDarkExtra(scheme) else NiSchemes.buildLightExtra(scheme)
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val windowSizeClass = remember(screenWidthDp, screenHeightDp) {
        computeNiWindowSizeClass(
            widthDp = screenWidthDp.dp,
            heightDp = screenHeightDp.dp,
        )
    }

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
