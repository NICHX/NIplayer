package com.nichx.niplayer.designsystem.theme

import androidx.annotation.StringRes
import com.nichx.niplayer.designsystem.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * NIplayer 配色方案枚举。
 *
 * 按 quiz-platform 的设计哲学，每个方案是一组「完整氛围色板」，覆盖主色、次级色、
 * 页面背景、卡片表面、描边色和缩略图渐变，切换时整页氛围变化。
 *
 * 分 3 个分类：冷色系 / 暖色系 / 自然色系。展示文案经 [labelRes]/[categoryRes] 资源化，
 * 支持 i18n（见 core/designsystem res）。
 */
enum class NiScheme(@StringRes val labelRes: Int, @StringRes val categoryRes: Int) {
    BLUE(R.string.color_scheme_blue, R.string.color_scheme_category_cool),
    INDIGO(R.string.color_scheme_indigo, R.string.color_scheme_category_cool),
    CYAN(R.string.color_scheme_cyan, R.string.color_scheme_category_cool),
    SLATE(R.string.color_scheme_slate, R.string.color_scheme_category_cool),
    PURPLE(R.string.color_scheme_purple, R.string.color_scheme_category_warm),
    ROSE(R.string.color_scheme_rose, R.string.color_scheme_category_warm),
    CORAL(R.string.color_scheme_coral, R.string.color_scheme_category_warm),
    PINK(R.string.color_scheme_pink, R.string.color_scheme_category_warm),
    TEAL(R.string.color_scheme_teal, R.string.color_scheme_category_nature),
    GREEN(R.string.color_scheme_green, R.string.color_scheme_category_nature),
    FOREST(R.string.color_scheme_forest, R.string.color_scheme_category_nature),
    CARAMEL(R.string.color_scheme_caramel, R.string.color_scheme_category_nature);
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

    // ═══════════════════════════
    // 冷色系（Cool）
    // ═══════════════════════════

    // ── Blue ──
    private val BlueLight = Blueprint(
        primary = Color(0xFF2095F4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6EAFF),
        onPrimaryContainer = Color(0xFF001B3D),
        secondary = Color(0xFF535F70),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7E3F7),
        onSecondaryContainer = Color(0xFF101C2B),
        tertiary = Color(0xFF6A5778),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF2DAFF),
        onTertiaryContainer = Color(0xFF241532),
        inversePrimary = Color(0xFF9DCAFF),
    )
    private val BlueDark = DarkBlueprint(
        primary = Color(0xFF9DCAFF),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF004A7C),
        onPrimaryContainer = Color(0xFFD6EAFF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3B4856),
        onSecondaryContainer = Color(0xFFD7E3F7),
        tertiary = Color(0xFFD6BEE4),
        onTertiary = Color(0xFF3A2948),
        tertiaryContainer = Color(0xFF523F5F),
        onTertiaryContainer = Color(0xFFF2DAFF),
        inversePrimary = Color(0xFF0061A4),
    )

    // ── Indigo ──
    private val IndigoLight = Blueprint(
        primary = Color(0xFF5C6BC0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8EAF6),
        onPrimaryContainer = Color(0xFF1A237E),
        secondary = Color(0xFF6E7B8B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E8F0),
        onSecondaryContainer = Color(0xFF1A202C),
        tertiary = Color(0xFF7C5E7E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3E5F5),
        onTertiaryContainer = Color(0xFF2D1B2E),
        inversePrimary = Color(0xFFBFC6FF),
        background = Color(0xFFF4F6FA),
        surfaceVariant = Color(0xFFE8EAF0),
        outline = Color(0xFF9797A5),
        outlineVariant = Color(0xFFC5C6D0),
    )
    private val IndigoDark = DarkBlueprint(
        primary = Color(0xFFBFC6FF),
        onPrimary = Color(0xFF1A237E),
        primaryContainer = Color(0xFF3F51B5),
        onPrimaryContainer = Color(0xFFE8EAF6),
        secondary = Color(0xFFB0BEC5),
        onSecondary = Color(0xFF1A202C),
        secondaryContainer = Color(0xFF455A64),
        onSecondaryContainer = Color(0xFFE2E8F0),
        tertiary = Color(0xFFCE93D8),
        onTertiary = Color(0xFF2D1B2E),
        tertiaryContainer = Color(0xFF6A3E6C),
        onTertiaryContainer = Color(0xFFF3E5F5),
        inversePrimary = Color(0xFF5C6BC0),
        surface = Color(0xFF0E0E16),
        surfaceVariant = Color(0xFF22222E),
        outline = Color(0xFF8C8CA0),
        outlineVariant = Color(0xFF4A4A5C),
    )

    // ═══════════════════════════
    // 暖色系（Warm）
    // ═══════════════════════════

    // ── Purple ──
    private val PurpleLight = Blueprint(
        primary = Color(0xFF9C27B0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF3E5F5),
        onPrimaryContainer = Color(0xFF3E0051),
        secondary = Color(0xFF6E5C73),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF6E0FB),
        onSecondaryContainer = Color(0xFF271A2D),
        tertiary = Color(0xFF80556A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E7),
        onTertiaryContainer = Color(0xFF331326),
        inversePrimary = Color(0xFFEABEFF),
    )
    private val PurpleDark = DarkBlueprint(
        primary = Color(0xFFEABEFF),
        onPrimary = Color(0xFF4A0061),
        primaryContainer = Color(0xFF7B1FA2),
        onPrimaryContainer = Color(0xFFF3E5F5),
        secondary = Color(0xFFD7BFDB),
        onSecondary = Color(0xFF3D2E43),
        secondaryContainer = Color(0xFF55445A),
        onSecondaryContainer = Color(0xFFF6E0FB),
        tertiary = Color(0xFFF0B8CE),
        onTertiary = Color(0xFF4C273B),
        tertiaryContainer = Color(0xFF663D52),
        onTertiaryContainer = Color(0xFFFFD8E7),
        inversePrimary = Color(0xFF9C27B0),
    )

    // ── Rose ──
    private val RoseLight = Blueprint(
        primary = Color(0xFFE91E63),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCE4EC),
        onPrimaryContainer = Color(0xFF4A0020),
        secondary = Color(0xFF9C6377),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E4EA),
        onSecondaryContainer = Color(0xFF301B23),
        tertiary = Color(0xFF6B5C7C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0E4F5),
        onTertiaryContainer = Color(0xFF231A2E),
        inversePrimary = Color(0xFFFFB1C8),
        background = Color(0xFFFDF4F6),
        surfaceVariant = Color(0xFFF5E8EC),
        outline = Color(0xFFA5959B),
        outlineVariant = Color(0xFFD1C4C9),
    )
    private val RoseDark = DarkBlueprint(
        primary = Color(0xFFFFB1C8),
        onPrimary = Color(0xFF4A0020),
        primaryContainer = Color(0xFFC2185B),
        onPrimaryContainer = Color(0xFFFCE4EC),
        secondary = Color(0xFFD4A3B5),
        onSecondary = Color(0xFF301B23),
        secondaryContainer = Color(0xFF704657),
        onSecondaryContainer = Color(0xFFF5E4EA),
        tertiary = Color(0xFFC7B8D8),
        onTertiary = Color(0xFF231A2E),
        tertiaryContainer = Color(0xFF534160),
        onTertiaryContainer = Color(0xFFF0E4F5),
        inversePrimary = Color(0xFFE91E63),
        surface = Color(0xFF180A10),
        surfaceVariant = Color(0xFF2D1A22),
        outline = Color(0xFFA5959B),
        outlineVariant = Color(0xFF5A4550),
    )

    // ═══════════════════════════
    // 自然色系（Natural）
    // ═══════════════════════════

    // ── Teal ──
    private val TealLight = Blueprint(
        primary = Color(0xFF00897B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2DFDB),
        onPrimaryContainer = Color(0xFF00251E),
        secondary = Color(0xFF4E6E6A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1F5EF),
        onSecondaryContainer = Color(0xFF0B2321),
        tertiary = Color(0xFF4D647C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD2E4FD),
        onTertiaryContainer = Color(0xFF081E35),
        inversePrimary = Color(0xFF80CBC4),
    )
    private val TealDark = DarkBlueprint(
        primary = Color(0xFF80CBC4),
        onPrimary = Color(0xFF003730),
        primaryContainer = Color(0xFF00695C),
        onPrimaryContainer = Color(0xFFB2DFDB),
        secondary = Color(0xFFB4CCC8),
        onSecondary = Color(0xFF203735),
        secondaryContainer = Color(0xFF364F4B),
        onSecondaryContainer = Color(0xFFD1F5EF),
        tertiary = Color(0xFFB4CBE3),
        onTertiary = Color(0xFF1E344C),
        tertiaryContainer = Color(0xFF354B63),
        onTertiaryContainer = Color(0xFFD2E4FD),
        inversePrimary = Color(0xFF00897B),
    )

    // ── Green ──
    private val GreenLight = Blueprint(
        primary = Color(0xFF43A047),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8F5E9),
        onPrimaryContainer = Color(0xFF002200),
        secondary = Color(0xFF5C7C5E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2F0E3),
        onSecondaryContainer = Color(0xFF1A2C1B),
        tertiary = Color(0xFF3D6B5E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F0EA),
        onTertiaryContainer = Color(0xFF0C201A),
        inversePrimary = Color(0xFFA1DAA4),
        background = Color(0xFFF4F9F4),
        surfaceVariant = Color(0xFFE8F0E9),
        outline = Color(0xFF8FA095),
        outlineVariant = Color(0xFFC5D0C8),
    )
    private val GreenDark = DarkBlueprint(
        primary = Color(0xFFA1DAA4),
        onPrimary = Color(0xFF002200),
        primaryContainer = Color(0xFF2E7D32),
        onPrimaryContainer = Color(0xFFE8F5E9),
        secondary = Color(0xFFA8C7AB),
        onSecondary = Color(0xFF1A2C1B),
        secondaryContainer = Color(0xFF3D5C40),
        onSecondaryContainer = Color(0xFFE2F0E3),
        tertiary = Color(0xFF9DC4B8),
        onTertiary = Color(0xFF0C201A),
        tertiaryContainer = Color(0xFF264F44),
        onTertiaryContainer = Color(0xFFE0F0EA),
        inversePrimary = Color(0xFF43A047),
        surface = Color(0xFF0B150C),
        surfaceVariant = Color(0xFF1A2E1E),
        outline = Color(0xFF8FA095),
        outlineVariant = Color(0xFF3A5542),
    )

    // ═══════════════════════════
    // 扩展色系（新增）
    // ═══════════════════════════

    // ── Cyan（清新青）─
    private val CyanLight = Blueprint(
        primary = Color(0xFF00BCD4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2EBF2),
        onPrimaryContainer = Color(0xFF002F35),
        secondary = Color(0xFF4E7B82),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1EFF4),
        onSecondaryContainer = Color(0xFF0B252A),
        tertiary = Color(0xFF286A6A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFACEFEF),
        onTertiaryContainer = Color(0xFF002020),
        inversePrimary = Color(0xFF4DD0E1),
        background = Color(0xFFF3FBFD),
        surfaceVariant = Color(0xFFE2F0F4),
        outline = Color(0xFF8A9BA0),
        outlineVariant = Color(0xFFC5D1D7),
    )
    private val CyanDark = DarkBlueprint(
        primary = Color(0xFF4DD0E1),
        onPrimary = Color(0xFF003640),
        primaryContainer = Color(0xFF00838F),
        onPrimaryContainer = Color(0xFFB2EBF2),
        secondary = Color(0xFFB2D5DB),
        onSecondary = Color(0xFF0B252A),
        secondaryContainer = Color(0xFF344B50),
        onSecondaryContainer = Color(0xFFD1EFF4),
        tertiary = Color(0xFF91D3D3),
        onTertiary = Color(0xFF003737),
        tertiaryContainer = Color(0xFF0D5050),
        onTertiaryContainer = Color(0xFFACEFEF),
        inversePrimary = Color(0xFF00BCD4),
        surface = Color(0xFF0C1315),
        surfaceVariant = Color(0xFF1C2D32),
        outline = Color(0xFF8A9BA0),
        outlineVariant = Color(0xFF3C5459),
    )

    // ── Slate（石板灰）─
    private val SlateLight = Blueprint(
        primary = Color(0xFF6B7280),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E7EB),
        onPrimaryContainer = Color(0xFF1E293B),
        secondary = Color(0xFF787F8D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8EAEE),
        onSecondaryContainer = Color(0xFF1F2937),
        tertiary = Color(0xFF6B7B8D),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0E8F0),
        onTertiaryContainer = Color(0xFF1A2A3B),
        inversePrimary = Color(0xFFB0B8C4),
        background = Color(0xFFF5F6F8),
        surfaceVariant = Color(0xFFE5E7EB),
        outline = Color(0xFF9CA3AF),
        outlineVariant = Color(0xFFD1D5DB),
    )
    private val SlateDark = DarkBlueprint(
        primary = Color(0xFFB0B8C4),
        onPrimary = Color(0xFF1E293B),
        primaryContainer = Color(0xFF4B5563),
        onPrimaryContainer = Color(0xFFE5E7EB),
        secondary = Color(0xFFB8C0CC),
        onSecondary = Color(0xFF1F2937),
        secondaryContainer = Color(0xFF4B5563),
        onSecondaryContainer = Color(0xFFE8EAEE),
        tertiary = Color(0xFFB0C0D0),
        onTertiary = Color(0xFF1A2A3B),
        tertiaryContainer = Color(0xFF4B5B6B),
        onTertiaryContainer = Color(0xFFE0E8F0),
        inversePrimary = Color(0xFF6B7280),
        surface = Color(0xFF0E0F10),
        surfaceVariant = Color(0xFF1F2125),
        outline = Color(0xFF8A8F98),
        outlineVariant = Color(0xFF3D4148),
    )

    // ── Coral（珊瑚橙）─
    private val CoralLight = Blueprint(
        primary = Color(0xFFFF7043),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE0D0),
        onPrimaryContainer = Color(0xFF3E1400),
        secondary = Color(0xFF996B5C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE2D8),
        onSecondaryContainer = Color(0xFF3C241B),
        tertiary = Color(0xFF5C6372),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE7E7F8),
        onTertiaryContainer = Color(0xFF19202C),
        inversePrimary = Color(0xFFFFB09C),
        background = Color(0xFFFEF7F4),
        surfaceVariant = Color(0xFFF6EBE6),
        outline = Color(0xFFA59590),
        outlineVariant = Color(0xFFD1C4BF),
    )
    private val CoralDark = DarkBlueprint(
        primary = Color(0xFFFFB09C),
        onPrimary = Color(0xFF3E1400),
        primaryContainer = Color(0xFFCC572E),
        onPrimaryContainer = Color(0xFFFFE0D0),
        secondary = Color(0xFFCEA494),
        onSecondary = Color(0xFF3C241B),
        secondaryContainer = Color(0xFF7B5144),
        onSecondaryContainer = Color(0xFFFFE2D8),
        tertiary = Color(0xFFBCC4D4),
        onTertiary = Color(0xFF19202C),
        tertiaryContainer = Color(0xFF414959),
        onTertiaryContainer = Color(0xFFE7E7F8),
        inversePrimary = Color(0xFFFF7043),
        surface = Color(0xFF18120F),
        surfaceVariant = Color(0xFF2D2420),
        outline = Color(0xFFA59590),
        outlineVariant = Color(0xFF594D48),
    )

    // ── Pink（粉红）─
    private val PinkLight = Blueprint(
        primary = Color(0xFFF472B6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE0F0),
        onPrimaryContainer = Color(0xFF3E0020),
        secondary = Color(0xFF996B86),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE2F0),
        onSecondaryContainer = Color(0xFF3C242E),
        tertiary = Color(0xFF6B5C72),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0E4F5),
        onTertiaryContainer = Color(0xFF231A2E),
        inversePrimary = Color(0xFFFFB0D8),
        background = Color(0xFFFDF4F9),
        surfaceVariant = Color(0xFFF6E8F0),
        outline = Color(0xFFA595A0),
        outlineVariant = Color(0xFFD1C4CB),
    )
    private val PinkDark = DarkBlueprint(
        primary = Color(0xFFFFB0D8),
        onPrimary = Color(0xFF3E0020),
        primaryContainer = Color(0xFFCC5590),
        onPrimaryContainer = Color(0xFFFFE0F0),
        secondary = Color(0xFFCEA4BA),
        onSecondary = Color(0xFF3C242E),
        secondaryContainer = Color(0xFF7B5165),
        onSecondaryContainer = Color(0xFFFFE2F0),
        tertiary = Color(0xFFC7B8D8),
        onTertiary = Color(0xFF231A2E),
        tertiaryContainer = Color(0xFF534160),
        onTertiaryContainer = Color(0xFFF0E4F5),
        inversePrimary = Color(0xFFF472B6),
        surface = Color(0xFF181216),
        surfaceVariant = Color(0xFF2D2430),
        outline = Color(0xFFA595A0),
        outlineVariant = Color(0xFF594855),
    )

    // ── Forest（森林绿）─
    private val ForestLight = Blueprint(
        primary = Color(0xFF059669),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF002F1F),
        secondary = Color(0xFF4E7B6A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1F5EA),
        onSecondaryContainer = Color(0xFF0B2520),
        tertiary = Color(0xFF286A6A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFACEFEF),
        onTertiaryContainer = Color(0xFF002020),
        inversePrimary = Color(0xFF34D399),
        background = Color(0xFFF3FBF6),
        surfaceVariant = Color(0xFFE2F0EA),
        outline = Color(0xFF8AA09A),
        outlineVariant = Color(0xFFC5D1CB),
    )
    private val ForestDark = DarkBlueprint(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF003F2A),
        primaryContainer = Color(0xFF047857),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFFB2D5C6),
        onSecondary = Color(0xFF0B2520),
        secondaryContainer = Color(0xFF345B4E),
        onSecondaryContainer = Color(0xFFD1F5EA),
        tertiary = Color(0xFF91D3D3),
        onTertiary = Color(0xFF003737),
        tertiaryContainer = Color(0xFF0D5050),
        onTertiaryContainer = Color(0xFFACEFEF),
        inversePrimary = Color(0xFF059669),
        surface = Color(0xFF0C1512),
        surfaceVariant = Color(0xFF1C2D27),
        outline = Color(0xFF8AA09A),
        outlineVariant = Color(0xFF3C544C),
    )

    // ── Caramel（焦糖棕）─
    private val CaramelLight = Blueprint(
        primary = Color(0xFFB8845C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF5E6D8),
        onPrimaryContainer = Color(0xFF3E2A1A),
        secondary = Color(0xFF996B5C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE2D8),
        onSecondaryContainer = Color(0xFF3C241B),
        tertiary = Color(0xFF5C6372),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE7E7F8),
        onTertiaryContainer = Color(0xFF19202C),
        inversePrimary = Color(0xFFE8C09E),
        background = Color(0xFFF9F4EE),
        surfaceVariant = Color(0xFFF0E8E0),
        outline = Color(0xFFA59588),
        outlineVariant = Color(0xFFD1C8BE),
    )
    private val CaramelDark = DarkBlueprint(
        primary = Color(0xFFE8C09E),
        onPrimary = Color(0xFF3E2A1A),
        primaryContainer = Color(0xFF996A48),
        onPrimaryContainer = Color(0xFFF5E6D8),
        secondary = Color(0xFFCEA494),
        onSecondary = Color(0xFF3C241B),
        secondaryContainer = Color(0xFF7B5144),
        onSecondaryContainer = Color(0xFFFFE2D8),
        tertiary = Color(0xFFBCC4D4),
        onTertiary = Color(0xFF19202C),
        tertiaryContainer = Color(0xFF414959),
        onTertiaryContainer = Color(0xFFE7E7F8),
        inversePrimary = Color(0xFFB8845C),
        surface = Color(0xFF181410),
        surfaceVariant = Color(0xFF2D2822),
        outline = Color(0xFFA59588),
        outlineVariant = Color(0xFF504840),
    )

    // ── 方案查找 ──

    private fun schemeLight(scheme: NiScheme): Blueprint = when (scheme) {
        NiScheme.BLUE -> BlueLight
        NiScheme.INDIGO -> IndigoLight
        NiScheme.CYAN -> CyanLight
        NiScheme.SLATE -> SlateLight
        NiScheme.PURPLE -> PurpleLight
        NiScheme.ROSE -> RoseLight
        NiScheme.CORAL -> CoralLight
        NiScheme.PINK -> PinkLight
        NiScheme.TEAL -> TealLight
        NiScheme.GREEN -> GreenLight
        NiScheme.FOREST -> ForestLight
        NiScheme.CARAMEL -> CaramelLight
    }

    private fun schemeDark(scheme: NiScheme): DarkBlueprint = when (scheme) {
        NiScheme.BLUE -> BlueDark
        NiScheme.INDIGO -> IndigoDark
        NiScheme.CYAN -> CyanDark
        NiScheme.SLATE -> SlateDark
        NiScheme.PURPLE -> PurpleDark
        NiScheme.ROSE -> RoseDark
        NiScheme.CORAL -> CoralDark
        NiScheme.PINK -> PinkDark
        NiScheme.TEAL -> TealDark
        NiScheme.GREEN -> GreenDark
        NiScheme.FOREST -> ForestDark
        NiScheme.CARAMEL -> CaramelDark
    }

    /** 根据配色方案构建 Light ColorScheme。 */
    fun buildLight(scheme: NiScheme): androidx.compose.material3.ColorScheme {
        val bp = schemeLight(scheme)
        return lightColorScheme(
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
    }

    /** 根据配色方案构建 Dark ColorScheme。 */
    fun buildDark(scheme: NiScheme): androidx.compose.material3.ColorScheme {
        val bp = schemeDark(scheme)
        return darkColorScheme(
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
    }

    /** 根据配色方案构建 Light NiExtraColors。 */
    fun buildLightExtra(scheme: NiScheme): NiExtraColors = buildExtra(
        isDark = false, scheme = scheme, primary = schemeLight(scheme).primary,
    )

    /** 根据配色方案构建 Dark NiExtraColors。 */
    fun buildDarkExtra(scheme: NiScheme): NiExtraColors = buildExtra(
        isDark = true, scheme = scheme, primary = schemeDark(scheme).primary,
    )

    private fun buildExtra(isDark: Boolean, scheme: NiScheme, primary: Color): NiExtraColors {
        val brandScale = generateTonalScale(primary)
        val palette = if (isDark) darkAmbient(scheme) else lightAmbient(scheme, primary)
        val (accent, accentLight) = schemeAccent(scheme)
        val storageScheme = schemeStorageColors(isDark)
        return NiExtraColors(
            isDark = isDark,
            brandScale = brandScale,
            surfaceLevel1 = palette.s1,
            surfaceLevel2 = palette.s2,
            surfaceLevel3 = palette.s3,
            outlineStrong = palette.os,
            outlineSoft = palette.ov,
            success = if (isDark) Color(0xFF7FE08A) else Color(0xFF2E7D32),
            onSuccess = if (isDark) Color(0xFF0B3000) else Color.White,
            thumbnailPlaceholder = Brush.linearGradient(listOf(palette.ts, palette.te)),
            brandOverlay = primary,
            accent = accent,
            accentLight = accentLight,
            primaryDark = brandScale.getOrElse(1) { primary },
            storageLocalColor = storageScheme.local,
            storageSmbColor = storageScheme.smb,
            storageWebdavColor = storageScheme.webdav,
            storageExternalColor = storageScheme.external,
            storageHistoryColor = storageScheme.history,
            storageQuickAccessColor = storageScheme.quickAccess,
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

    private fun schemeStorageColors(isDark: Boolean): StorageColors {
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

    // ── 每套方案的表面层级/描边/缩略图渐变 ──

    private fun darkAmbient(scheme: NiScheme) = when (scheme) {
        NiScheme.BLUE -> AmbientPalette(
            Color(0xFF0D0E12), Color(0xFF15171F), Color(0xFF1D212E),
            Color(0xFF7A8AA0), Color(0xFF2A3045),
            Color(0xFF004A7C), Color(0xFF0065B0),
        )
        NiScheme.INDIGO -> AmbientPalette(
            Color(0xFF0E0E16), Color(0xFF1A1A2A), Color(0xFF242442),
            Color(0xFF8C8CA0), Color(0xFF3A3A50),
            Color(0xFF303F9F), Color(0xFF5C6BC0),
        )
        NiScheme.PURPLE -> AmbientPalette(
            Color(0xFF0F0D12), Color(0xFF1A1520), Color(0xFF261E30),
            Color(0xFF9A8AA0), Color(0xFF352A45),
            Color(0xFF6A1B9A), Color(0xFF7B1FA2),
        )
        NiScheme.ROSE -> AmbientPalette(
            Color(0xFF180A10), Color(0xFF26121C), Color(0xFF331A26),
            Color(0xFFA5959B), Color(0xFF5A4550),
            Color(0xFFAD1457), Color(0xFFE91E63),
        )
        NiScheme.TEAL -> AmbientPalette(
            Color(0xFF0C1211), Color(0xFF141E1C), Color(0xFF1C2B27),
            Color(0xFF7AA098), Color(0xFF2A4538),
            Color(0xFF004D40), Color(0xFF00695C),
        )
        NiScheme.GREEN -> AmbientPalette(
            Color(0xFF0B150C), Color(0xFF122315), Color(0xFF1C3020),
            Color(0xFF8FA095), Color(0xFF3A5542),
            Color(0xFF1B5E20), Color(0xFF43A047),
        )
        NiScheme.CYAN -> AmbientPalette(
            Color(0xFF0C1315), Color(0xFF131D20), Color(0xFF1A2A2E),
            Color(0xFF7AA0A0), Color(0xFF2A4545),
            Color(0xFF006064), Color(0xFF00838F),
        )
        NiScheme.SLATE -> AmbientPalette(
            Color(0xFF0E0F10), Color(0xFF15171A), Color(0xFF1E2026),
            Color(0xFF8A8F98), Color(0xFF2A2D35),
            Color(0xFF374151), Color(0xFF4B5563),
        )
        NiScheme.CORAL -> AmbientPalette(
            Color(0xFF18120F), Color(0xFF201814), Color(0xFF2D231E),
            Color(0xFFA59590), Color(0xFF594D48),
            Color(0xFFBF360C), Color(0xFFE64A19),
        )
        NiScheme.PINK -> AmbientPalette(
            Color(0xFF181216), Color(0xFF221A1E), Color(0xFF2E2429),
            Color(0xFFA595A0), Color(0xFF594855),
            Color(0xFFC2185B), Color(0xFFF472B6),
        )
        NiScheme.FOREST -> AmbientPalette(
            Color(0xFF0C1512), Color(0xFF132218), Color(0xFF1A3022),
            Color(0xFF8AA09A), Color(0xFF2A453A),
            Color(0xFF065F46), Color(0xFF047857),
        )
        NiScheme.CARAMEL -> AmbientPalette(
            Color(0xFF181410), Color(0xFF221E18), Color(0xFF2D2822),
            Color(0xFFA59588), Color(0xFF504840),
            Color(0xFF8D6E63), Color(0xFFA1887F),
        )
    }

    private fun lightAmbient(scheme: NiScheme, primary: Color) = when (scheme) {
        NiScheme.BLUE -> AmbientPalette(
            Color.White, Color.White, Color(0xFFEDEFF4),
            Color(0xFF94949E), Color(0xFFC9CACE),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.INDIGO -> AmbientPalette(
            Color.White, Color.White, Color(0xFFEBEDF5),
            Color(0xFF9797A5), Color(0xFFC5C6D0),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.CYAN -> AmbientPalette(
            Color.White, Color.White, Color(0xFFE2F0F4),
            Color(0xFF8A9BA0), Color(0xFFC5D1D7),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.SLATE -> AmbientPalette(
            Color.White, Color.White, Color(0xFFE8EAEE),
            Color(0xFF9CA3AF), Color(0xFFD1D5DB),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.PURPLE -> AmbientPalette(
            Color.White, Color.White, Color(0xFFF4EBF5),
            Color(0xFF9A949E), Color(0xFFCAC4CD),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.ROSE -> AmbientPalette(
            Color.White, Color.White, Color(0xFFF5E8EC),
            Color(0xFFA5959B), Color(0xFFD1C4C9),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.CORAL -> AmbientPalette(
            Color.White, Color.White, Color(0xFFF6EBE6),
            Color(0xFFA59590), Color(0xFFD1C4BF),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.PINK -> AmbientPalette(
            Color.White, Color.White, Color(0xFFF6E8F0),
            Color(0xFFA595A0), Color(0xFFD1C4CB),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.TEAL -> AmbientPalette(
            Color.White, Color.White, Color(0xFFEBF2EE),
            Color(0xFF949E9A), Color(0xFFC9CECA),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.GREEN -> AmbientPalette(
            Color.White, Color.White, Color(0xFFE8F0E9),
            Color(0xFF8FA095), Color(0xFFC5D0C8),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.FOREST -> AmbientPalette(
            Color.White, Color.White, Color(0xFFE2F0EA),
            Color(0xFF8AA09A), Color(0xFFC5D1CB),
            primary, primary.copy(alpha = 0.70f),
        )
        NiScheme.CARAMEL -> AmbientPalette(
            Color.White, Color.White, Color(0xFFF0E8E0),
            Color(0xFFA59588), Color(0xFFD1C8BE),
            primary, primary.copy(alpha = 0.70f),
        )
    }

    // ── 每套方案的装饰色（accent / accentLight）──
    // 参考 quiz-platform 移动端设计，每套方案有独立的装饰色，
    // 用作标签、徽章、装饰性 UI 元素的高亮色。
    private fun schemeAccent(scheme: NiScheme): Pair<Color, Color> = when (scheme) {
        NiScheme.BLUE -> Color(0xFF54B0F7) to Color(0xFFC5E2FF)
        NiScheme.INDIGO -> Color(0xFF7986CB) to Color(0xFFE8EAF6)
        NiScheme.CYAN -> Color(0xFF4DD0E1) to Color(0xFFB2EBF2)
        NiScheme.SLATE -> Color(0xFFB0B8C4) to Color(0xFFE5E7EB)
        NiScheme.PURPLE -> Color(0xFFBA68C8) to Color(0xFFF3E5F5)
        NiScheme.ROSE -> Color(0xFFF06292) to Color(0xFFFCE4EC)
        NiScheme.CORAL -> Color(0xFFFFB09C) to Color(0xFFFFE0D0)
        NiScheme.PINK -> Color(0xFFFFB0D8) to Color(0xFFFFE0F0)
        NiScheme.TEAL -> Color(0xFF4DB6AC) to Color(0xFFB2DFDB)
        NiScheme.GREEN -> Color(0xFF66BB6A) to Color(0xFFE8F5E9)
        NiScheme.FOREST -> Color(0xFF34D399) to Color(0xFFD1FAE5)
        NiScheme.CARAMEL -> Color(0xFFE8C09E) to Color(0xFFF5E6D8)
    }

    private data class AmbientPalette(
        val s1: Color, val s2: Color, val s3: Color,
        val os: Color, val ov: Color,
        val ts: Color, val te: Color,
    )

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
            (0..9).forEach { i ->
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

internal val LocalNiScheme = compositionLocalOf { NiScheme.BLUE }
