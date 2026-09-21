package com.nichx.niplayer.designsystem.theme

import androidx.annotation.StringRes
import com.nichx.niplayer.designsystem.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * NIplayer 配色方案枚举。
 *
 * 每套方案是一组「完整氛围色板」，覆盖主色、次级色、三级色、页面背景、卡片表面、
 * 描边色与缩略图渐变。为打破单调单色，[secondary]/[tertiary] 采用反差色相，切换时
 * 整页氛围随主打色的同时，次级/三级装饰色带来丰富的组合观感。
 *
 * 分类：
 * - 冷色系 / 暖色系 / 自然色系（经典氛围）
 * - 马卡龙（明亮柔和粉彩）
 * - 莫兰迪（低饱和高级灰调）
 * - 薄荷曼波（清新鲜明的组合撞色）
 *
 * 展示文案经 [labelRes]/[categoryRes] 资源化，支持 i18n（见 core/designsystem res）。
 */
enum class NiScheme(@StringRes val labelRes: Int, @StringRes val categoryRes: Int) {
    // 冷色系
    MISTY(R.string.color_scheme_misty, R.string.color_scheme_category_cool),
    BLUEBERRY(R.string.color_scheme_blueberry, R.string.color_scheme_category_cool),
    DENIM(R.string.color_scheme_denim, R.string.color_scheme_category_cool),

    // 暖色系
    ROSE_DUST(R.string.color_scheme_rose_dust, R.string.color_scheme_category_warm),
    STRAWBERRY(R.string.color_scheme_strawberry, R.string.color_scheme_category_warm),
    CORAL(R.string.color_scheme_coral, R.string.color_scheme_category_warm),

    // 自然色系
    FOREST(R.string.color_scheme_forest, R.string.color_scheme_category_nature),
    MATCHA(R.string.color_scheme_matcha, R.string.color_scheme_category_nature),
    CARAMEL(R.string.color_scheme_caramel, R.string.color_scheme_category_nature),

    // 马卡龙（明亮粉彩与撞色）
    MINT_MACARON(R.string.color_scheme_mint_macaron, R.string.color_scheme_category_macaron),
    SAKURA_MACARON(R.string.color_scheme_sakura_macaron, R.string.color_scheme_category_macaron),
    LAVENDER_MACARON(R.string.color_scheme_lavender_macaron, R.string.color_scheme_category_macaron),
    SPEARMINT(R.string.color_scheme_spearmint, R.string.color_scheme_category_macaron),
    BUBBLEGUM(R.string.color_scheme_bubblegum, R.string.color_scheme_category_macaron),
    SUMMER_SODA(R.string.color_scheme_summer_soda, R.string.color_scheme_category_macaron),

    // 莫兰迪
    ALMOND(R.string.color_scheme_almond, R.string.color_scheme_category_morandi),
    MAUVE(R.string.color_scheme_mauve, R.string.color_scheme_category_morandi),
    SAGE(R.string.color_scheme_sage, R.string.color_scheme_category_morandi);

    companion object {
        /**
         * 按序数还原配色方案，越界回落到 [MISTY]。
         *
         * A1 架构修复（2026-09-21）：:core:datastore 原先直接持有本枚举类型，使**数据层依赖
         * UI 层**（:core:designsystem）。现由 datastore 只暴露序号，UI 层在边界处调用本方法还原。
         * MMKV 里的存储格式本来就是 ordinal，故无数据迁移。
         */
        fun fromOrdinal(ordinal: Int): NiScheme = entries.getOrElse(ordinal) { MISTY }
    }
}

/**
 * 调色板框架：每个配色方案提供 Light 组的色值。
 */
internal data class Blueprint(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color = Color(0xFFD32F2F),
    val onError: Color = Color.White,
    val errorContainer: Color = Color(0xFFFFEBEE),
    val onErrorContainer: Color = Color(0xFF410002),
    val background: Color = Color(0xFFF4F7FB),
    val onBackground: Color = Color(0xFF1A1C1E),
    val surface: Color = Color.White,
    val onSurface: Color = Color(0xFF1A1C1E),
    val surfaceVariant: Color = Color(0xFFEBECF0),
    val onSurfaceVariant: Color = Color(0xFF49454F),
    val outline: Color = Color(0xFF9B9BA5),
    val outlineVariant: Color = Color(0xFFC9CACE),
    val inverseSurface: Color = Color(0xFF2F3033),
    val inverseOnSurface: Color = Color(0xFFF1F0F4),
    val inversePrimary: Color,
)

/**
 * 调色板框架：每个配色方案提供 Dark 组的色值。
 */
internal data class DarkBlueprint(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color = Color(0xFFFFB4AB),
    val onError: Color = Color(0xFF690005),
    val errorContainer: Color = Color(0xFF93000A),
    val onErrorContainer: Color = Color(0xFFFFDAD6),
    val background: Color = Color(0xFF000000),
    val onBackground: Color = Color(0xFFE6E1E5),
    val surface: Color = Color(0xFF0D0D0D),
    val onSurface: Color = Color(0xFFE6E1E5),
    val surfaceVariant: Color = Color(0xFF222226),
    val onSurfaceVariant: Color = Color(0xFFC9C5CB),
    val outline: Color = Color(0xFF94949E),
    val outlineVariant: Color = Color(0xFF5A5A64),
    val inverseSurface: Color = Color(0xFFE6E1E5),
    val inverseOnSurface: Color = Color(0xFF2F3033),
    val inversePrimary: Color,
)

/**
 * 配色方案工厂。
 */
object NiSchemes {

    // ── 方案查找 ──

    private fun schemeLight(scheme: NiScheme): Blueprint = when (scheme) {
        NiScheme.MISTY -> MistyLight
        NiScheme.BLUEBERRY -> BlueberryLight
        NiScheme.DENIM -> DenimLight
        NiScheme.ROSE_DUST -> RoseDustLight
        NiScheme.STRAWBERRY -> StrawberryLight
        NiScheme.CORAL -> CoralLight
        NiScheme.FOREST -> ForestLight
        NiScheme.MATCHA -> MatchaLight
        NiScheme.CARAMEL -> CaramelLight
        NiScheme.MINT_MACARON -> MintMacaronLight
        NiScheme.SAKURA_MACARON -> SakuraMacaronLight
        NiScheme.LAVENDER_MACARON -> LavenderMacaronLight
        NiScheme.ALMOND -> AlmondLight
        NiScheme.MAUVE -> MauveLight
        NiScheme.SAGE -> SageLight
        NiScheme.SPEARMINT -> SpearmintLight
        NiScheme.BUBBLEGUM -> BubblegumLight
        NiScheme.SUMMER_SODA -> SummerSodaLight
    }

    private fun schemeDark(scheme: NiScheme): DarkBlueprint = when (scheme) {
        NiScheme.MISTY -> MistyDark
        NiScheme.BLUEBERRY -> BlueberryDark
        NiScheme.DENIM -> DenimDark
        NiScheme.ROSE_DUST -> RoseDustDark
        NiScheme.STRAWBERRY -> StrawberryDark
        NiScheme.CORAL -> CoralDark
        NiScheme.FOREST -> ForestDark
        NiScheme.MATCHA -> MatchaDark
        NiScheme.CARAMEL -> CaramelDark
        NiScheme.MINT_MACARON -> MintMacaronDark
        NiScheme.SAKURA_MACARON -> SakuraMacaronDark
        NiScheme.LAVENDER_MACARON -> LavenderMacaronDark
        NiScheme.ALMOND -> AlmondDark
        NiScheme.MAUVE -> MauveDark
        NiScheme.SAGE -> SageDark
        NiScheme.SPEARMINT -> SpearmintDark
        NiScheme.BUBBLEGUM -> BubblegumDark
        NiScheme.SUMMER_SODA -> SummerSodaDark
    }

    /** 根据配色方案构建 Light ColorScheme。 */
    fun buildLight(scheme: NiScheme): androidx.compose.material3.ColorScheme =
        bpToLight(schemeLight(scheme))

    /** 根据配色方案构建 Dark ColorScheme。 */
    fun buildDark(scheme: NiScheme): androidx.compose.material3.ColorScheme =
        bpToDark(schemeDark(scheme))

    private fun bpToLight(bp: Blueprint): androidx.compose.material3.ColorScheme = lightColorScheme(
        primary = bp.primary,
        onPrimary = bp.onPrimary,
        primaryContainer = bp.primaryContainer,
        onPrimaryContainer = bp.onPrimaryContainer,
        secondary = bp.secondary,
        onSecondary = bp.onSecondary,
        secondaryContainer = bp.secondaryContainer,
        onSecondaryContainer = bp.onSecondaryContainer,
        tertiary = bp.tertiary,
        onTertiary = bp.onTertiary,
        tertiaryContainer = bp.tertiaryContainer,
        onTertiaryContainer = bp.onTertiaryContainer,
        error = bp.error,
        onError = bp.onError,
        errorContainer = bp.errorContainer,
        onErrorContainer = bp.onErrorContainer,
        background = bp.background,
        onBackground = bp.onBackground,
        surface = bp.surface,
        onSurface = bp.onSurface,
        surfaceVariant = bp.surfaceVariant,
        onSurfaceVariant = bp.onSurfaceVariant,
        outline = bp.outline,
        outlineVariant = bp.outlineVariant,
        inverseSurface = bp.inverseSurface,
        inverseOnSurface = bp.inverseOnSurface,
        inversePrimary = bp.inversePrimary,
    )

    private fun bpToDark(bp: DarkBlueprint): androidx.compose.material3.ColorScheme = darkColorScheme(
        primary = bp.primary,
        onPrimary = bp.onPrimary,
        primaryContainer = bp.primaryContainer,
        onPrimaryContainer = bp.onPrimaryContainer,
        secondary = bp.secondary,
        onSecondary = bp.onSecondary,
        secondaryContainer = bp.secondaryContainer,
        onSecondaryContainer = bp.onSecondaryContainer,
        tertiary = bp.tertiary,
        onTertiary = bp.onTertiary,
        tertiaryContainer = bp.tertiaryContainer,
        onTertiaryContainer = bp.onTertiaryContainer,
        error = bp.error,
        onError = bp.onError,
        errorContainer = bp.errorContainer,
        onErrorContainer = bp.onErrorContainer,
        background = bp.background,
        onBackground = bp.onBackground,
        surface = bp.surface,
        onSurface = bp.onSurface,
        surfaceVariant = bp.surfaceVariant,
        onSurfaceVariant = bp.onSurfaceVariant,
        outline = bp.outline,
        outlineVariant = bp.outlineVariant,
        inverseSurface = bp.inverseSurface,
        inverseOnSurface = bp.inverseOnSurface,
        inversePrimary = bp.inversePrimary,
    )

    /** 根据配色方案构建 Light NiExtraColors。 */
    fun buildLightExtra(scheme: NiScheme): NiExtraColors {
        val l = schemeLight(scheme)
        return buildExtra(
            isDark = false,
            primary = l.primary,
            secondary = l.secondary,
            tertiary = l.tertiary,
        )
    }

    /** 根据配色方案构建 Dark NiExtraColors。 */
    fun buildDarkExtra(scheme: NiScheme): NiExtraColors {
        val d = schemeDark(scheme)
        return buildExtra(
            isDark = true,
            primary = d.primary,
            secondary = d.secondary,
            tertiary = d.tertiary,
        )
    }

    /**
     * 构建装饰色（NiExtraColors）。
     *
     * 表面层级源于「主色 + 中性底」的轻量 tint，使每套方案的卡片表面带主题色氛围
     * 而非纯灰；缩略图渐变采用 主色→三级色 的组合渐变，强化多色观感。
     */
    private fun buildExtra(isDark: Boolean, primary: Color, secondary: Color, tertiary: Color): NiExtraColors {
        val base = if (isDark) Color.Black else Color.White
        val surfaceLevel1 = lerp(base, primary, if (isDark) 0.07f else 0.04f)
        val surfaceLevel2 = lerp(base, primary, if (isDark) 0.12f else 0.08f)
        val surfaceLevel3 = lerp(base, primary, if (isDark) 0.18f else 0.13f)

        val brandScale = generateTonalScale(primary)
        return NiExtraColors(
            isDark = isDark,
            brandScale = brandScale,
            surfaceLevel1 = surfaceLevel1,
            surfaceLevel2 = surfaceLevel2,
            surfaceLevel3 = surfaceLevel3,
            outlineStrong = if (isDark) secondary.copy(alpha = 0.75f) else secondary,
            outlineSoft = if (isDark) lerp(base, secondary, 0.30f) else lerp(base, secondary, 0.86f),
            success = if (isDark) Color(0xFF7FE08A) else Color(0xFF2E7D32),
            onSuccess = if (isDark) Color(0xFF0B3000) else Color.White,
            thumbnailPlaceholder = Brush.linearGradient(listOf(primary, tertiary)),
            brandOverlay = primary,
            accent = secondary,
            accentLight = lerp(secondary, base, if (isDark) 0.25f else 0.72f),
            primaryDark = brandScale.getOrElse(1) { primary },
            storageLocalColor = storageColors(isDark).local,
            storageSmbColor = storageColors(isDark).smb,
            storageWebdavColor = storageColors(isDark).webdav,
            storageExternalColor = storageColors(isDark).external,
            storageHistoryColor = storageColors(isDark).history,
            storageQuickAccessColor = storageColors(isDark).quickAccess,
        )
    }

    private data class StorageColors(
        val local: Color,
        val smb: Color,
        val webdav: Color,
        val external: Color,
        val history: Color,
        val quickAccess: Color,
    )

    private fun storageColors(isDark: Boolean): StorageColors {
        return if (isDark) StorageColors(
            local = Color(0xFF66BB6A),
            smb = Color(0xFF42A5F5),
            webdav = Color(0xFFCE93D8),
            external = Color(0xFF4DB6AC),
            history = Color(0xFFFF8A65),
            quickAccess = Color(0xFFFFD54F),
        ) else StorageColors(
            local = Color(0xFF388E3C),
            smb = Color(0xFF1565C0),
            webdav = Color(0xFF7B1FA2),
            external = Color(0xFF00897B),
            history = Color(0xFFE65100),
            quickAccess = Color(0xFFF57F17),
        )
    }

    private fun generateTonalScale(primary: Color): List<Color> {
        val r = primary.red
        val g = primary.green
        val b = primary.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val baseL = (max + min) / 2f
        val s = if (delta < 0.0001f) 0f
        else delta / (1f - kotlin.math.abs(2f * baseL - 1f))
        val h = when {
            delta < 0.0001f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0) it + 360f else it }
        return buildList {
            for (i in 0..9) {
                val lightness = when (i) {
                    0 -> 0.02f; 1 -> 0.08f
                    in 2..6 -> 0.18f + (baseL - 0.18f) * ((i - 2) / 4f)
                    7 -> baseL.coerceAtMost(0.85f)
                    8 -> 0.90f; 9 -> 0.95f
                    else -> 0.50f
                }
                add(Color.hsl(h, s.coerceAtMost(0.88f), lightness.coerceIn(0.02f, 0.96f)))
            }
        }
    }
}

internal val LocalNiScheme = compositionLocalOf { NiScheme.MISTY }
