package com.nichx.niplayer.designsystem.theme

import androidx.compose.ui.graphics.Color


// ═══════════════════════════
// 莫兰迪（Morandi）
// ═══════════════════════════

// ── 藕荷 Lotus Pink（灰粉紫＋烟粉＋青灰）──
// 原「杏仁 Almond」：主色 #A99A8A（色相 31°）与自然色系的焦糖 #A97949（30°）几乎同色相，
// 属实质重复，且精简后全色板无粉。2026-09-22 换为低饱和灰粉紫（320°），既补上 266°→337°
// 的空档，又保住莫兰迪「低饱和」的调性。持久化 key 仍为 ALMOND。
internal val LotusPinkLight = Blueprint(
    primary = Color(0xFFAF87A2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E3EA),
    onPrimaryContainer = Color(0xFF3A2530),
    secondary = Color(0xFFBFA3A0),
    onSecondary = Color(0xFF40332F),
    secondaryContainer = Color(0xFFF3E8E4),
    onSecondaryContainer = Color(0xFF40332F),
    tertiary = Color(0xFF9AA3B0),
    onTertiary = Color(0xFF2C343D),
    tertiaryContainer = Color(0xFFE4E9F0),
    onTertiaryContainer = Color(0xFF2C343D),
    inversePrimary = Color(0xFFDCBFCD),
    background = Color(0xFFF9F5F7),
    surfaceVariant = Color(0xFFEFE8EC),
    outline = Color(0xFFA79BA1),
    outlineVariant = Color(0xFFD6CBD1),
)
internal val LotusPinkDark = DarkBlueprint(
    primary = Color(0xFFDCBFCD),
    onPrimary = Color(0xFF3A2530),
    primaryContainer = Color(0xFF6E5261),
    onPrimaryContainer = Color(0xFFF2E3EA),
    secondary = Color(0xFFE0C6C2),
    onSecondary = Color(0xFF3B2A27),
    secondaryContainer = Color(0xFF76605C),
    onSecondaryContainer = Color(0xFFF5E5E2),
    tertiary = Color(0xFFC2CBD6),
    onTertiary = Color(0xFF2C343D),
    tertiaryContainer = Color(0xFF59636E),
    onTertiaryContainer = Color(0xFFE4E9F0),
    inversePrimary = Color(0xFFAF87A2),
    surface = Color(0xFF171315),
    surfaceVariant = Color(0xFF2A2427),
    outline = Color(0xFFA79BA1),
    outlineVariant = Color(0xFF4D4548),
)

// ── 灰紫 Mauve（灰紫＋粉灰＋青灰）──
internal val MauveLight = Blueprint(
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
internal val MauveDark = DarkBlueprint(
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

// ── 苔绿 Sage（灰绿＋沙灰＋冷灰）──
internal val SageLight = Blueprint(
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
internal val SageDark = DarkBlueprint(
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
