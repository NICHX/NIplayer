package com.nichx.niplayer.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 扩展色板令牌。
 *
 * 不改动既有 [NiLightColorScheme] / [NiDarkColorScheme]，通过额外 [LocalNiExtraColors]
 * 提供「品牌色族 tonal 色阶 / 深色三级 surface / success / outline 细化」等令牌，
 * 供新设计组件读取。现有页面不读取本类，不受影响。
 *
 * 读取方式：`NiExtraColors.current.surfaceLevel2`（与 `MaterialTheme.colorScheme` 风格一致）。
 */
data class NiExtraColors(
    val isDark: Boolean,
    val brandScale: List<Color>,
    val surfaceLevel1: Color,
    val surfaceLevel2: Color,
    val surfaceLevel3: Color,
    val outlineStrong: Color,
    val outlineSoft: Color,
    val success: Color,
    val onSuccess: Color,
    val thumbnailPlaceholder: Brush,
    val brandOverlay: Color,
    val accent: Color,
    val accentLight: Color,
    val primaryDark: Color,
    val storageLocalColor: Color,
    val storageSmbColor: Color,
    val storageWebdavColor: Color,
    val storageExternalColor: Color,
    val storageHistoryColor: Color,
    val storageQuickAccessColor: Color,
) {
    val brand10 get() = brandScale[1]
    val brand20 get() = brandScale[2]
    val brand40 get() = brandScale[4]
    val brand80 get() = brandScale[8]
    val brand90 get() = brandScale[9]

    companion object {
        private val BrandScaleLight = listOf(
            Color(0xFF001B3D),
            Color(0xFF003065),
            Color(0xFF004A7C),
            Color(0xFF0065B0),
            Color(0xFF2095F4),
            Color(0xFF54B0F7),
            Color(0xFF7AC4F9),
            Color(0xFF9DCAFF),
            Color(0xFFC5E2FF),
            Color(0xFFD6EAFF),
        )
        private val BrandScaleDark = BrandScaleLight

        val LightExtra = NiExtraColors(
            isDark = false,
            brandScale = BrandScaleLight,
            surfaceLevel1 = Color(0xFFFFFFFF),
            surfaceLevel2 = Color(0xFFF4F7FB),
            surfaceLevel3 = Color(0xFFEAEEF4),
            outlineStrong = Color(0xFF5C5C66),
            outlineSoft = Color(0xFFE1E3E8),
            success = Color(0xFF2E7D32),
            onSuccess = Color.White,
            thumbnailPlaceholder = Brush.linearGradient(listOf(Color(0xFF2095F4), Color(0xFF54B0F7))),
            brandOverlay = Color(0xFF2095F4),
            accent = Color(0xFF54B0F7),
            accentLight = Color(0xFFC5E2FF),
            primaryDark = Color(0xFF003065),
            storageLocalColor = Color(0xFF388E3C),
            storageSmbColor = Color(0xFF1565C0),
            storageWebdavColor = Color(0xFF7B1FA2),
            storageExternalColor = Color(0xFF00897B),
            storageHistoryColor = Color(0xFFE65100),
            storageQuickAccessColor = Color(0xFFF57F17),
        )

        val DarkExtra = NiExtraColors(
            isDark = true,
            brandScale = BrandScaleDark,
            surfaceLevel1 = Color(0xFF0D0D0D),
            surfaceLevel2 = Color(0xFF1E1E1E),
            surfaceLevel3 = Color(0xFF2D2D2D),
            outlineStrong = Color(0xFF5C5C66),
            outlineSoft = Color(0xFF2A2A2A),
            success = Color(0xFF7FE08A),
            onSuccess = Color(0xFF0B3000),
            thumbnailPlaceholder = Brush.linearGradient(listOf(Color(0xFF1976D2), Color(0xFF2095F4))),
            brandOverlay = Color(0xFF9DCAFF),
            accent = Color(0xFF54B0F7),
            accentLight = Color(0xFF003065),
            primaryDark = Color(0xFF9DCAFF),
            storageLocalColor = Color(0xFF66BB6A),
            storageSmbColor = Color(0xFF42A5F5),
            storageWebdavColor = Color(0xFFCE93D8),
            storageExternalColor = Color(0xFF4DB6AC),
            storageHistoryColor = Color(0xFFFF8A65),
            storageQuickAccessColor = Color(0xFFFFD54F),
        )

        val current: NiExtraColors
            @Composable
            @ReadOnlyComposable
            get() = LocalNiExtraColors.current
    }
}

internal val LocalNiExtraColors = compositionLocalOf { NiExtraColors.LightExtra }