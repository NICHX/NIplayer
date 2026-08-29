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

    // ── 雾蓝静谧 Misty ──
    private val MistyLight = Blueprint(
        primary = Color(0xFF6E9BBB),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEAF4),
        onPrimaryContainer = Color(0xFF1A3140),
        secondary = Color(0xFF8B8680),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0EDE8),
        onSecondaryContainer = Color(0xFF2E2B27),
        tertiary = Color(0xFF7FA5A5),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE2EFEF),
        onTertiaryContainer = Color(0xFF1E3131),
        inversePrimary = Color(0xFFB6D4EB),
        background = Color(0xFFF3F7FA),
        surfaceVariant = Color(0xFFE3EBF1),
        outline = Color(0xFF9BA6AD),
        outlineVariant = Color(0xFFC6D0D7),
    )
    private val MistyDark = DarkBlueprint(
        primary = Color(0xFF9DC3DC),
        onPrimary = Color(0xFF123B57),
        primaryContainer = Color(0xFF2A5770),
        onPrimaryContainer = Color(0xFFDCEAF4),
        secondary = Color(0xFFC0B9B0),
        onSecondary = Color(0xFF2E2B27),
        secondaryContainer = Color(0xFF45423C),
        onSecondaryContainer = Color(0xFFF0EDE8),
        tertiary = Color(0xFFB4C7C7),
        onTertiary = Color(0xFF1E3131),
        tertiaryContainer = Color(0xFF364E4E),
        onTertiaryContainer = Color(0xFFE2EFEF),
        inversePrimary = Color(0xFF6E9BBB),
        surface = Color(0xFF0E1418),
        surfaceVariant = Color(0xFF1E2930),
        outline = Color(0xFF9BA6AD),
        outlineVariant = Color(0xFF3D4C54),
    )

    // ── 蓝莓之夜 Blueberry Night（靛紫组合）──
    private val BlueberryLight = Blueprint(
        primary = Color(0xFF4A5FA5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDE3F7),
        onPrimaryContainer = Color(0xFF161D3E),
        secondary = Color(0xFF7C58A6),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDE4F8),
        onSecondaryContainer = Color(0xFF29163C),
        tertiary = Color(0xFF4F7CC4),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE8FB),
        onTertiaryContainer = Color(0xFF16263F),
        inversePrimary = Color(0xFFB4C3F0),
        background = Color(0xFFF2F4FB),
        surfaceVariant = Color(0xFFE3E7F3),
        outline = Color(0xFF949BAB),
        outlineVariant = Color(0xFFC4CBD9),
    )
    private val BlueberryDark = DarkBlueprint(
        primary = Color(0xFFAEBDF6),
        onPrimary = Color(0xFF161D3E),
        primaryContainer = Color(0xFF3A4788),
        onPrimaryContainer = Color(0xFFDDE3F7),
        secondary = Color(0xFFC9A6E8),
        onSecondary = Color(0xFF29163C),
        secondaryContainer = Color(0xFF5A3B73),
        onSecondaryContainer = Color(0xFFEDE4F8),
        tertiary = Color(0xFF9DC0EF),
        onTertiary = Color(0xFF16263F),
        tertiaryContainer = Color(0xFF37517E),
        onTertiaryContainer = Color(0xFFDCE8FB),
        inversePrimary = Color(0xFF4A5FA5),
        surface = Color(0xFF10131F),
        surfaceVariant = Color(0xFF202334),
        outline = Color(0xFF949BAB),
        outlineVariant = Color(0xFF39404F),
    )

    // ── 复古牛仔 Denim（蓝＋蓝绿＋铜橙）──
    private val DenimLight = Blueprint(
        primary = Color(0xFF3E5F8A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD7E3F3),
        onPrimaryContainer = Color(0xFF14212F),
        secondary = Color(0xFF4E7A7A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD6ECEC),
        onSecondaryContainer = Color(0xFF0E2424),
        tertiary = Color(0xFFB77B45),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0E0CC),
        onTertiaryContainer = Color(0xFF3A2613),
        inversePrimary = Color(0xFFA9C4E6),
        background = Color(0xFFF1F4F8),
        surfaceVariant = Color(0xFFE0E7EE),
        outline = Color(0xFF8F9AA8),
        outlineVariant = Color(0xFFC3CDD8),
    )
    private val DenimDark = DarkBlueprint(
        primary = Color(0xFF8FB3DC),
        onPrimary = Color(0xFF14212F),
        primaryContainer = Color(0xFF2F4058),
        onPrimaryContainer = Color(0xFFD7E3F3),
        secondary = Color(0xFF7FB3B3),
        onSecondary = Color(0xFF0E2424),
        secondaryContainer = Color(0xFF2F4C4C),
        onSecondaryContainer = Color(0xFFD6ECEC),
        tertiary = Color(0xFFE0A76B),
        onTertiary = Color(0xFF3A2613),
        tertiaryContainer = Color(0xFF6E4C25),
        onTertiaryContainer = Color(0xFFF0E0CC),
        inversePrimary = Color(0xFF3E5F8A),
        surface = Color(0xFF0E1218),
        surfaceVariant = Color(0xFF1E252E),
        outline = Color(0xFF8F9AA8),
        outlineVariant = Color(0xFF39414C),
    )

    // ═══════════════════════════
    // 暖色系（Warm）
    // ═══════════════════════════

    // ── 玫瑰尘埃 Rose Dust（干枯玫瑰＋灰棕）──
    private val RoseDustLight = Blueprint(
        primary = Color(0xFFB0686F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF6DDE0),
        onPrimaryContainer = Color(0xFF3D1A1F),
        secondary = Color(0xFF9A7B78),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0E4E2),
        onSecondaryContainer = Color(0xFF32221F),
        tertiary = Color(0xFFB58A9A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF2E1E8),
        onTertiaryContainer = Color(0xFF37202B),
        inversePrimary = Color(0xFFEABCC1),
        background = Color(0xFFF9F4F3),
        surfaceVariant = Color(0xFFF0E7E6),
        outline = Color(0xFFA59796),
        outlineVariant = Color(0xFFD3C6C5),
    )
    private val RoseDustDark = DarkBlueprint(
        primary = Color(0xFFE4A1A8),
        onPrimary = Color(0xFF4A2328),
        primaryContainer = Color(0xFF823E46),
        onPrimaryContainer = Color(0xFFF6DDE0),
        secondary = Color(0xFFD0B9B5),
        onSecondary = Color(0xFF32221F),
        secondaryContainer = Color(0xFF51403D),
        onSecondaryContainer = Color(0xFFF0E4E2),
        tertiary = Color(0xFFE3C2D1),
        onTertiary = Color(0xFF37202B),
        tertiaryContainer = Color(0xFF70414F),
        onTertiaryContainer = Color(0xFFF2E1E8),
        inversePrimary = Color(0xFFB0686F),
        surface = Color(0xFF1B1213),
        surfaceVariant = Color(0xFF302425),
        outline = Color(0xFFA59796),
        outlineVariant = Color(0xFF524447),
    )

    // ── 草莓奶油 Strawberry Cream（草莓红＋蜜橙＋果粉）──
    private val StrawberryLight = Blueprint(
        primary = Color(0xFFF04A6A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDEE6),
        onPrimaryContainer = Color(0xFF4A001D),
        secondary = Color(0xFFE08A5A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE6D8),
        onSecondaryContainer = Color(0xFF3E2310),
        tertiary = Color(0xFFD65A9E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFEDEF0),
        onTertiaryContainer = Color(0xFF3C0F2A),
        inversePrimary = Color(0xFFFFB1C4),
        background = Color(0xFFFEF4F6),
        surfaceVariant = Color(0xFFFBE8EE),
        outline = Color(0xFFAC949C),
        outlineVariant = Color(0xFFD9C2CA),
    )
    private val StrawberryDark = DarkBlueprint(
        primary = Color(0xFFFF99AF),
        onPrimary = Color(0xFF4A001D),
        primaryContainer = Color(0xFFA32647),
        onPrimaryContainer = Color(0xFFFFDEE6),
        secondary = Color(0xFFFFB787),
        onSecondary = Color(0xFF3E2310),
        secondaryContainer = Color(0xFF8F5527),
        onSecondaryContainer = Color(0xFFFFE6D8),
        tertiary = Color(0xFFFFA3DD),
        onTertiary = Color(0xFF3C0F2A),
        tertiaryContainer = Color(0xFF8E3A6B),
        onTertiaryContainer = Color(0xFFFEDEF0),
        inversePrimary = Color(0xFFF04A6A),
        surface = Color(0xFF201019),
        surfaceVariant = Color(0xFF351F29),
        outline = Color(0xFFAC949C),
        outlineVariant = Color(0xFF5A414A),
    )

    // ── 落日珊瑚 Sunset Coral（珊瑚橙＋蜜桃）──
    private val CoralLight = Blueprint(
        primary = Color(0xFFFF6E5A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE2DB),
        onPrimaryContainer = Color(0xFF3D1008),
        secondary = Color(0xFFFF9B6B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE9DD),
        onSecondaryContainer = Color(0xFF40240F),
        tertiary = Color(0xFFD98A8A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFCE3E3),
        onTertiaryContainer = Color(0xFF3D1717),
        inversePrimary = Color(0xFFFFB4A8),
        background = Color(0xFFFEF4F0),
        surfaceVariant = Color(0xFFFBE8E2),
        outline = Color(0xFFAC9691),
        outlineVariant = Color(0xFFD9C6C0),
    )
    private val CoralDark = DarkBlueprint(
        primary = Color(0xFFFF9A8A),
        onPrimary = Color(0xFF4A160D),
        primaryContainer = Color(0xFFB03E30),
        onPrimaryContainer = Color(0xFFFFE2DB),
        secondary = Color(0xFFFFC295),
        onSecondary = Color(0xFF40240F),
        secondaryContainer = Color(0xFF8A5124),
        onSecondaryContainer = Color(0xFFFFE9DD),
        tertiary = Color(0xFFF0BDBD),
        onTertiary = Color(0xFF3D1717),
        tertiaryContainer = Color(0xFF813A3A),
        onTertiaryContainer = Color(0xFFFCE3E3),
        inversePrimary = Color(0xFFFF6E5A),
        surface = Color(0xFF1E120E),
        surfaceVariant = Color(0xFF342520),
        outline = Color(0xFFAC9691),
        outlineVariant = Color(0xFF57433E),
    )

    // ═══════════════════════════
    // 自然色系（Nature）
    // ═══════════════════════════

    // ── 森野絮语 Forest Whispers（森绿＋灰绿＋赭棕）──
    private val ForestLight = Blueprint(
        primary = Color(0xFF3E7A4E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDAEEDA),
        onPrimaryContainer = Color(0xFF143114),
        secondary = Color(0xFF6B8A66),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4EFDF),
        onSecondaryContainer = Color(0xFF1F2C1C),
        tertiary = Color(0xFFA76B3E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0DFD0),
        onTertiaryContainer = Color(0xFF37240F),
        inversePrimary = Color(0xFF9CCEAC),
        background = Color(0xFFF4F8F3),
        surfaceVariant = Color(0xFFE6EEE5),
        outline = Color(0xFF95A396),
        outlineVariant = Color(0xFFC8D4C9),
    )
    private val ForestDark = DarkBlueprint(
        primary = Color(0xFF7FC98F),
        onPrimary = Color(0xFF143114),
        primaryContainer = Color(0xFF275B32),
        onPrimaryContainer = Color(0xFFDAEEDA),
        secondary = Color(0xFFB4CBAD),
        onSecondary = Color(0xFF1F2C1C),
        secondaryContainer = Color(0xFF3A4F37),
        onSecondaryContainer = Color(0xFFE4EFDF),
        tertiary = Color(0xFFE0A76B),
        onTertiary = Color(0xFF37240F),
        tertiaryContainer = Color(0xFF6E4C25),
        onTertiaryContainer = Color(0xFFF0DFD0),
        inversePrimary = Color(0xFF3E7A4E),
        surface = Color(0xFF0E1610),
        surfaceVariant = Color(0xFF1E2C21),
        outline = Color(0xFF95A396),
        outlineVariant = Color(0xFF3E5342),
    )

    // ── 抹茶微风 Matcha Breeze（抹茶绿＋麦秆黄＋藤绿）──
    private val MatchaLight = Blueprint(
        primary = Color(0xFF86A855),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9F2DA),
        onPrimaryContainer = Color(0xFF273616),
        secondary = Color(0xFFC8B76A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF2EDD8),
        onSecondaryContainer = Color(0xFF3C331A),
        tertiary = Color(0xFF7BA98E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE1EFE7),
        onTertiaryContainer = Color(0xFF1D2F26),
        inversePrimary = Color(0xFFB9D497),
        background = Color(0xFFF7F8F1),
        surfaceVariant = Color(0xFFEDF0E3),
        outline = Color(0xFFA0A494),
        outlineVariant = Color(0xFFCECFBF),
    )
    private val MatchaDark = DarkBlueprint(
        primary = Color(0xFFC3DC99),
        onPrimary = Color(0xFF273616),
        primaryContainer = Color(0xFF50702E),
        onPrimaryContainer = Color(0xFFE9F2DA),
        secondary = Color(0xFFDED3A0),
        onSecondary = Color(0xFF3C331A),
        secondaryContainer = Color(0xFF71653A),
        onSecondaryContainer = Color(0xFFF2EDD8),
        tertiary = Color(0xFFBCD8C5),
        onTertiary = Color(0xFF1D2F26),
        tertiaryContainer = Color(0xFF46604F),
        onTertiaryContainer = Color(0xFFE1EFE7),
        inversePrimary = Color(0xFF86A855),
        surface = Color(0xFF151710),
        surfaceVariant = Color(0xFF25291C),
        outline = Color(0xFFA0A494),
        outlineVariant = Color(0xFF44473A),
    )

    // ── 焦糖琥珀 Caramel Amber（焦糖褐＋玫瑰棕＋琥珀）──
    private val CaramelLight = Blueprint(
        primary = Color(0xFFA97949),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF3E6D6),
        onPrimaryContainer = Color(0xFF3A2716),
        secondary = Color(0xFF9A6B8A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0E2EC),
        onSecondaryContainer = Color(0xFF34232F),
        tertiary = Color(0xFFB08A5A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF1E4D4),
        onTertiaryContainer = Color(0xFF372816),
        inversePrimary = Color(0xFFE0BE96),
        background = Color(0xFFF8F3ED),
        surfaceVariant = Color(0xFFF0EAE1),
        outline = Color(0xFFA79B90),
        outlineVariant = Color(0xFFD5CAC0),
    )
    private val CaramelDark = DarkBlueprint(
        primary = Color(0xFFE3C095),
        onPrimary = Color(0xFF3A2716),
        primaryContainer = Color(0xFF78572E),
        onPrimaryContainer = Color(0xFFF3E6D6),
        secondary = Color(0xFFDBB8D5),
        onSecondary = Color(0xFF34232F),
        secondaryContainer = Color(0xFF624B5D),
        onSecondaryContainer = Color(0xFFF0E2EC),
        tertiary = Color(0xFFE0BE8A),
        onTertiary = Color(0xFF372816),
        tertiaryContainer = Color(0xFF75572E),
        onTertiaryContainer = Color(0xFFF1E4D4),
        inversePrimary = Color(0xFFA97949),
        surface = Color(0xFF181310),
        surfaceVariant = Color(0xFF2C2620),
        outline = Color(0xFFA79B90),
        outlineVariant = Color(0xFF4B443C),
    )

    // ═══════════════════════════
    // 马卡龙（Macaron）
    // ═══════════════════════════

    // ── 薄荷马卡龙 Mint Macaron（薄荷绿＋奶油粉＋奶油蓝）──
    private val MintMacaronLight = Blueprint(
        primary = Color(0xFF5CBFA0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDF3EB),
        onPrimaryContainer = Color(0xFF173A2F),
        secondary = Color(0xFFF2A8B8),
        onSecondary = Color(0xFF4A2530),
        secondaryContainer = Color(0xFFFCE0E6),
        onSecondaryContainer = Color(0xFF4A2530),
        tertiary = Color(0xFF8FCBE3),
        onTertiary = Color(0xFF1B3A49),
        tertiaryContainer = Color(0xFFDFF1F8),
        onTertiaryContainer = Color(0xFF1B3A49),
        inversePrimary = Color(0xFF9ADCC5),
        background = Color(0xFFF3FBF8),
        surfaceVariant = Color(0xFFE3F2EC),
        outline = Color(0xFF93A8A0),
        outlineVariant = Color(0xFFC9D8D1),
    )
    private val MintMacaronDark = DarkBlueprint(
        primary = Color(0xFF8FE0C4),
        onPrimary = Color(0xFF173A2F),
        primaryContainer = Color(0xFF33846D),
        onPrimaryContainer = Color(0xFFDDF3EB),
        secondary = Color(0xFFFFC2CF),
        onSecondary = Color(0xFF4A2530),
        secondaryContainer = Color(0xFF715463),
        onSecondaryContainer = Color(0xFFFCE0E6),
        tertiary = Color(0xFFB0E1F2),
        onTertiary = Color(0xFF1B3A49),
        tertiaryContainer = Color(0xFF2E5A6E),
        onTertiaryContainer = Color(0xFFDFF1F8),
        inversePrimary = Color(0xFF5CBFA0),
        surface = Color(0xFF0F1A17),
        surfaceVariant = Color(0xFF1E302B),
        outline = Color(0xFF93A8A0),
        outlineVariant = Color(0xFF3B5049),
    )

    // ── 樱花马卡龙 Sakura Macaron（樱花粉＋奶油杏＋香芋紫）──
    private val SakuraMacaronLight = Blueprint(
        primary = Color(0xFFF08FB4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCDDE9),
        onPrimaryContainer = Color(0xFF4A2530),
        secondary = Color(0xFFF2C9A0),
        onSecondary = Color(0xFF4A3A25),
        secondaryContainer = Color(0xFFFCEBD9),
        onSecondaryContainer = Color(0xFF4A3A25),
        tertiary = Color(0xFFC5B3E8),
        onTertiary = Color(0xFF3A3560),
        tertiaryContainer = Color(0xFFEFE9FB),
        onTertiaryContainer = Color(0xFF3A3560),
        inversePrimary = Color(0xFFFFB8D2),
        background = Color(0xFFFEF4F8),
        surfaceVariant = Color(0xFFFAE8EE),
        outline = Color(0xFFAF9AA1),
        outlineVariant = Color(0xFFDCC8CF),
    )
    private val SakuraMacaronDark = DarkBlueprint(
        primary = Color(0xFFFFB4D1),
        onPrimary = Color(0xFF4A2530),
        primaryContainer = Color(0xFFA05A78),
        onPrimaryContainer = Color(0xFFFCDDE9),
        secondary = Color(0xFFFFDCBA),
        onSecondary = Color(0xFF4A3A25),
        secondaryContainer = Color(0xFF7A6245),
        onSecondaryContainer = Color(0xFFFCEBD9),
        tertiary = Color(0xFFD9C9F5),
        onTertiary = Color(0xFF3A3560),
        tertiaryContainer = Color(0xFF5B5A8F),
        onTertiaryContainer = Color(0xFFEFE9FB),
        inversePrimary = Color(0xFFF08FB4),
        surface = Color(0xFF1E1018),
        surfaceVariant = Color(0xFF36222C),
        outline = Color(0xFFAF9AA1),
        outlineVariant = Color(0xFF5C4450),
    )

    // ── 薰衣草马卡龙 Lavender Macaron（薰衣草紫＋薄荷蓝＋紫藤）──
    private val LavenderMacaronLight = Blueprint(
        primary = Color(0xFF9290D6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7E5FB),
        onPrimaryContainer = Color(0xFF2C2A4E),
        secondary = Color(0xFF8FCBE3),
        onSecondary = Color(0xFF1B3A49),
        secondaryContainer = Color(0xFFDFF1F8),
        onSecondaryContainer = Color(0xFF1B3A49),
        tertiary = Color(0xFFD6A8DC),
        onTertiary = Color(0xFF3E2A46),
        tertiaryContainer = Color(0xFFF8E7F8),
        onTertiaryContainer = Color(0xFF3E2A46),
        inversePrimary = Color(0xFFBAC2F1),
        background = Color(0xFFF6F5FC),
        surfaceVariant = Color(0xFFE9E7F6),
        outline = Color(0xFF9C99AD),
        outlineVariant = Color(0xFFCDCADB),
    )
    private val LavenderMacaronDark = DarkBlueprint(
        primary = Color(0xFFB9B7F2),
        onPrimary = Color(0xFF2C2A4E),
        primaryContainer = Color(0xFF5E5C9E),
        onPrimaryContainer = Color(0xFFE7E5FB),
        secondary = Color(0xFFB0E1F2),
        onSecondary = Color(0xFF1B3A49),
        secondaryContainer = Color(0xFF2E5A6E),
        onSecondaryContainer = Color(0xFFDFF1F8),
        tertiary = Color(0xFFF0BFF6),
        onTertiary = Color(0xFF3E2A46),
        tertiaryContainer = Color(0xFF62546E),
        onTertiaryContainer = Color(0xFFF8E7F8),
        inversePrimary = Color(0xFF9290D6),
        surface = Color(0xFF151320),
        surfaceVariant = Color(0xFF272435),
        outline = Color(0xFF9C99AD),
        outlineVariant = Color(0xFF444256),
    )

    // ═══════════════════════════
    // 莫兰迪（Morandi）
    // ═══════════════════════════

    // ── 杏仁慕斯 Almond Mousse（米驼＋烟粉＋灰绿）──
    private val AlmondLight = Blueprint(
        primary = Color(0xFFA99A8A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF0E9E0),
        onPrimaryContainer = Color(0xFF37302A),
        secondary = Color(0xFFC0A9A0),
        onSecondary = Color(0xFF40332F),
        secondaryContainer = Color(0xFFF3E8E4),
        onSecondaryContainer = Color(0xFF40332F),
        tertiary = Color(0xFFA8B0A0),
        onTertiary = Color(0xFF343932),
        tertiaryContainer = Color(0xFFE8ECE4),
        onTertiaryContainer = Color(0xFF343932),
        inversePrimary = Color(0xFFD6C8B8),
        background = Color(0xFFF8F6F2),
        surfaceVariant = Color(0xFFEFECE6),
        outline = Color(0xFFA6A09A),
        outlineVariant = Color(0xFFD4CCC5),
    )
    private val AlmondDark = DarkBlueprint(
        primary = Color(0xFFD6C8B8),
        onPrimary = Color(0xFF37302A),
        primaryContainer = Color(0xFF6B5E52),
        onPrimaryContainer = Color(0xFFF0E9E0),
        secondary = Color(0xFFE0C9C0),
        onSecondary = Color(0xFF40332F),
        secondaryContainer = Color(0xFF765E58),
        onSecondaryContainer = Color(0xFFF3E8E4),
        tertiary = Color(0xFFCBD4C4),
        onTertiary = Color(0xFF343932),
        tertiaryContainer = Color(0xFF595F52),
        onTertiaryContainer = Color(0xFFE8ECE4),
        inversePrimary = Color(0xFFA99A8A),
        surface = Color(0xFF191613),
        surfaceVariant = Color(0xFF2B2621),
        outline = Color(0xFFA6A09A),
        outlineVariant = Color(0xFF4E4843),
    )

    // ── 灰霭紫 Grey Mauve（灰紫＋粉灰＋青灰）──
    private val MauveLight = Blueprint(
        primary = Color(0xFF9A8FA8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFECE8F3),
        onPrimaryContainer = Color(0xFF362F40),
        secondary = Color(0xFFB5A8BE),
        onSecondary = Color(0xFF40384A),
        secondaryContainer = Color(0xFFF1EAF4),
        onSecondaryContainer = Color(0xFF40384A),
        tertiary = Color(0xFF8FA3A8),
        onTertiary = Color(0xFF343E40),
        tertiaryContainer = Color(0xFFE6EDEF),
        onTertiaryContainer = Color(0xFF343E40),
        inversePrimary = Color(0xFFCDC4D6),
        background = Color(0xFFF8F6FA),
        surfaceVariant = Color(0xFFEBE9F1),
        outline = Color(0xFFA6A0AC),
        outlineVariant = Color(0xFFD5CED9),
    )
    private val MauveDark = DarkBlueprint(
        primary = Color(0xFFC4B8D2),
        onPrimary = Color(0xFF362F40),
        primaryContainer = Color(0xFF6D6176),
        onPrimaryContainer = Color(0xFFECE8F3),
        secondary = Color(0xFFD9C8DC),
        onSecondary = Color(0xFF40384A),
        secondaryContainer = Color(0xFF77616F),
        onSecondaryContainer = Color(0xFFF1EAF4),
        tertiary = Color(0xFFB5C6C9),
        onTertiary = Color(0xFF343E40),
        tertiaryContainer = Color(0xFF586D70),
        onTertiaryContainer = Color(0xFFE6EDEF),
        inversePrimary = Color(0xFF9A8FA8),
        surface = Color(0xFF161419),
        surfaceVariant = Color(0xFF29252D),
        outline = Color(0xFFA6A0AC),
        outlineVariant = Color(0xFF4B4652),
    )

    // ── 鼠尾草 Sage（灰绿＋沙灰＋冷灰）──
    private val SageLight = Blueprint(
        primary = Color(0xFF8A9E86),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6EDE2),
        onPrimaryContainer = Color(0xFF323A2E),
        secondary = Color(0xFFA8A08A),
        onSecondary = Color(0xFF403A2E),
        secondaryContainer = Color(0xFFEBE7DA),
        onSecondaryContainer = Color(0xFF403A2E),
        tertiary = Color(0xFF849AA5),
        onTertiary = Color(0xFF303C40),
        tertiaryContainer = Color(0xFFE2EAED),
        onTertiaryContainer = Color(0xFF303C40),
        inversePrimary = Color(0xFFB9CAB2),
        background = Color(0xFFF5F7F2),
        surfaceVariant = Color(0xFFE6EAE3),
        outline = Color(0xFF9CA49A),
        outlineVariant = Color(0xFFCBD0C5),
    )
    private val SageDark = DarkBlueprint(
        primary = Color(0xFFB6C9B0),
        onPrimary = Color(0xFF323A2E),
        primaryContainer = Color(0xFF5E6E59),
        onPrimaryContainer = Color(0xFFE6EDE2),
        secondary = Color(0xFFC9C2AB),
        onSecondary = Color(0xFF403A2E),
        secondaryContainer = Color(0xFF6B634E),
        onSecondaryContainer = Color(0xFFEBE7DA),
        tertiary = Color(0xFFAFC2CB),
        onTertiary = Color(0xFF303C40),
        tertiaryContainer = Color(0xFF51656C),
        onTertiaryContainer = Color(0xFFE2EAED),
        inversePrimary = Color(0xFF8A9E86),
        surface = Color(0xFF141614),
        surfaceVariant = Color(0xFF262A25),
        outline = Color(0xFF9CA49A),
        outlineVariant = Color(0xFF454A43),
    )

    // ═══════════════════════════
    // 薄荷曼波（Minty）
    // ═══════════════════════════

    // ── 薄荷曼波 Spearmint（薄荷绿＋桃红＋天空蓝）──
    private val SpearmintLight = Blueprint(
        primary = Color(0xFF2FBF8F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD2F5E8),
        onPrimaryContainer = Color(0xFF0E3327),
        secondary = Color(0xFFFF7A8A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0E4),
        onSecondaryContainer = Color(0xFF3A1B22),
        tertiary = Color(0xFF3FA8E8),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD5EAFA),
        onTertiaryContainer = Color(0xFF12324A),
        inversePrimary = Color(0xFF83DDBE),
        background = Color(0xFFF0FBF6),
        surfaceVariant = Color(0xFFE2F3EC),
        outline = Color(0xFF8CA69C),
        outlineVariant = Color(0xFFC4D8CF),
    )
    private val SpearmintDark = DarkBlueprint(
        primary = Color(0xFF5FE0B0),
        onPrimary = Color(0xFF0E3327),
        primaryContainer = Color(0xFF1F6A52),
        onPrimaryContainer = Color(0xFFD2F5E8),
        secondary = Color(0xFFFF9AA8),
        onSecondary = Color(0xFF3A1B22),
        secondaryContainer = Color(0xFF7C4250),
        onSecondaryContainer = Color(0xFFFFE0E4),
        tertiary = Color(0xFF6EC4F5),
        onTertiary = Color(0xFF12324A),
        tertiaryContainer = Color(0xFF29608C),
        onTertiaryContainer = Color(0xFFD5EAFA),
        inversePrimary = Color(0xFF2FBF8F),
        surface = Color(0xFF0C1613),
        surfaceVariant = Color(0xFF1B2F28),
        outline = Color(0xFF8CA69C),
        outlineVariant = Color(0xFF3B5149),
    )

    // ── 泡泡糖 Bubblegum（泡泡糖粉＋天空蓝＋蜜黄）──
    private val BubblegumLight = Blueprint(
        primary = Color(0xFFF26BB0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCDCEA),
        onPrimaryContainer = Color(0xFF4A2138),
        secondary = Color(0xFF5FC4E8),
        onSecondary = Color(0xFF143541),
        secondaryContainer = Color(0xFFD9EFF8),
        onSecondaryContainer = Color(0xFF143541),
        tertiary = Color(0xFFFFB45A),
        onTertiary = Color(0xFF4A3318),
        tertiaryContainer = Color(0xFFFDEADA),
        onTertiaryContainer = Color(0xFF4A3318),
        inversePrimary = Color(0xFFFF9CD0),
        background = Color(0xFFFEF4FA),
        surfaceVariant = Color(0xFFFAE6EF),
        outline = Color(0xFFAD96A1),
        outlineVariant = Color(0xFFDCC6D0),
    )
    private val BubblegumDark = DarkBlueprint(
        primary = Color(0xFFFF8FCE),
        onPrimary = Color(0xFF4A2138),
        primaryContainer = Color(0xFFA2477E),
        onPrimaryContainer = Color(0xFFFCDCEA),
        secondary = Color(0xFF84D7F5),
        onSecondary = Color(0xFF143541),
        secondaryContainer = Color(0xFF33677A),
        onSecondaryContainer = Color(0xFFD9EFF8),
        tertiary = Color(0xFFFFD287),
        onTertiary = Color(0xFF4A3318),
        tertiaryContainer = Color(0xFF7A5A30),
        onTertiaryContainer = Color(0xFFFDEADA),
        inversePrimary = Color(0xFFF26BB0),
        surface = Color(0xFF1F1018),
        surfaceVariant = Color(0xFF38222D),
        outline = Color(0xFFAD96A1),
        outlineVariant = Color(0xFF5A4450),
    )

    // ── 缤纷汽水 Summer Soda（汽水橙＋苏打蓝＋汽水绿）──
    private val SummerSodaLight = Blueprint(
        primary = Color(0xFFF2873F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFCE3CD),
        onPrimaryContainer = Color(0xFF482612),
        secondary = Color(0xFF3FB8E8),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7EDF8),
        onSecondaryContainer = Color(0xFF11364A),
        tertiary = Color(0xFF62C87A),
        onTertiary = Color(0xFF14301B),
        tertiaryContainer = Color(0xFFDCF1E2),
        onTertiaryContainer = Color(0xFF14301B),
        inversePrimary = Color(0xFFFFB98E),
        background = Color(0xFFFDF6F0),
        surfaceVariant = Color(0xFFF9E9DD),
        outline = Color(0xFFAC9990),
        outlineVariant = Color(0xFFD8C7BD),
    )
    private val SummerSodaDark = DarkBlueprint(
        primary = Color(0xFFFFB57F),
        onPrimary = Color(0xFF482612),
        primaryContainer = Color(0xFF9C5621),
        onPrimaryContainer = Color(0xFFFCE3CD),
        secondary = Color(0xFF7FD3F5),
        onSecondary = Color(0xFF11364A),
        secondaryContainer = Color(0xFF2D5A72),
        onSecondaryContainer = Color(0xFFD7EDF8),
        tertiary = Color(0xFF99E0AA),
        onTertiary = Color(0xFF14301B),
        tertiaryContainer = Color(0xFF3B6E4A),
        onTertiaryContainer = Color(0xFFDCF1E2),
        inversePrimary = Color(0xFFF2873F),
        surface = Color(0xFF1A1310),
        surfaceVariant = Color(0xFF33251C),
        outline = Color(0xFFAC9990),
        outlineVariant = Color(0xFF57463E),
    )

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

internal val LocalNiScheme = compositionLocalOf { NiScheme.MISTY }