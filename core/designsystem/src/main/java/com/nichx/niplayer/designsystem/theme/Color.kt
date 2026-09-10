package com.nichx.niplayer.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * NIplayer 配色方案。
 *
 * 主色使用品牌蓝 #2095F4，浅色模式冷灰背景（非纯白、非暖白），
 * 深色模式使用真黑色背景（#000000），适合视频播放场景。
 *
 * ⚠️ 可见性要求（WCAG AA）：
 * - 正常文字（<18pt）对比度 ≥ 4.5:1
 * - 大文字（≥18pt 粗体 或 ≥14pt）对比度 ≥ 3:1
 * - 交互组件焦点指示对比度 ≥ 3:1
 * - outline/outlineVariant 作为分隔线需与实际背景对比度足够
 *
 * 本文件中的值适用于「不切换配色方案的现有页面」。
 * 多配色方案支持见 NiColorSchemes.kt。
 */

// ──────── Brand colors ────────

/** 品牌蓝。 */
val BrandBlue = Color(0xFF2095F4)

/** 品牌蓝深色。 */
val BrandBlueDark = Color(0xFF1976D2)

// ──────── Light scheme ────────

private val LightPrimary = BrandBlue
private val LightOnPrimary = Color.White
private val LightPrimaryContainer = Color(0xFFD6EAFF)
private val LightOnPrimaryContainer = Color(0xFF001B3D)

private val LightSecondary = Color(0xFF535F70)
private val LightOnSecondary = Color.White
private val LightSecondaryContainer = Color(0xFFD7E3F7)
private val LightOnSecondaryContainer = Color(0xFF101C2B)

private val LightTertiary = Color(0xFF6A5778)
private val LightOnTertiary = Color.White
private val LightTertiaryContainer = Color(0xFFF2DAFF)
private val LightOnTertiaryContainer = Color(0xFF241532)

private val LightError = Color(0xFFD32F2F)
private val LightOnError = Color.White
private val LightErrorContainer = Color(0xFFFFEBEE)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightBackground = Color(0xFFF4F7FB)
private val LightOnBackground = Color(0xFF1A1C1E)
private val LightSurface = Color.White
private val LightOnSurface = Color(0xFF1A1C1E)
private val LightSurfaceVariant = Color(0xFFEBECF0)
private val LightOnSurfaceVariant = Color(0xFF49454F)
private val LightSurfaceTint = LightPrimary

// 加深 outline/outlineVariant 提升分割可见性
private val LightOutline = Color(0xFF9B9BA5)
private val LightOutlineVariant = Color(0xFFC9CACE)

private val LightInverseSurface = Color(0xFF2F3033)
private val LightInverseOnSurface = Color(0xFFF1F0F4)
private val LightInversePrimary = Color(0xFF9DCAFF)

private val LightScrim = Color.Black

val NiLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = LightScrim,
)

// ──────── Dark scheme ────────

private val DarkPrimary = Color(0xFF9DCAFF)
private val DarkOnPrimary = Color(0xFF003258)
private val DarkPrimaryContainer = Color(0xFF004A7C)
private val DarkOnPrimaryContainer = Color(0xFFD6EAFF)

private val DarkSecondary = Color(0xFFBBC7DB)
private val DarkOnSecondary = Color(0xFF253140)
private val DarkSecondaryContainer = Color(0xFF3B4856)
private val DarkOnSecondaryContainer = Color(0xFFD7E3F7)

private val DarkTertiary = Color(0xFFD6BEE4)
private val DarkOnTertiary = Color(0xFF3A2948)
private val DarkTertiaryContainer = Color(0xFF523F5F)
private val DarkOnTertiaryContainer = Color(0xFFF2DAFF)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF000000)
private val DarkOnBackground = Color(0xFFE6E1E5)
private val DarkSurface = Color(0xFF0D0D0D)
private val DarkOnSurface = Color(0xFFE6E1E5)

// 提升 surfaceVariant 亮度使容器背景更清晰
private val DarkSurfaceVariant = Color(0xFF222226)
private val DarkOnSurfaceVariant = Color(0xFFC9C5CB)
private val DarkSurfaceTint = DarkPrimary

// 提升 outline 亮度，使分割线在深色背景上真正可见
private val DarkOutline = Color(0xFF94949E)
private val DarkOutlineVariant = Color(0xFF5A5A64)

private val DarkInverseSurface = Color(0xFFE6E1E5)
private val DarkInverseOnSurface = Color(0xFF2F3033)
private val DarkInversePrimary = Color(0xFF0061A4)

private val DarkScrim = Color.Black

val NiDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim,
)