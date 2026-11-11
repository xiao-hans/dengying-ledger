package com.meow.ledger.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 灯影配色，逐值移植自 dengY 的 Color.kt。
 * 深色不是浅色的反相，而是同一套语义重新取值：底色近黑带暖、卡片亮一档、文字反白。
 */
data class PaletteScheme(
    val brand: Color,
    val brandSecondary: Color,
    val appBg: Color,
    val cardBg: Color,
    val searchBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val danger: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassShadow: Color,
    val chipFill: Color,
    val chipSelectedFill: Color,
    val dockHighlight: Color,
)

val LightPalette = PaletteScheme(
    brand = Color(0xFF0C0C09),
    brandSecondary = Color(0xFF312C85),
    appBg = Color(0xFFF4F4F1),
    cardBg = Color(0xFFFFFFFF),
    searchBg = Color(0xFFEAEAE5),
    textPrimary = Color(0xFF0C0C09),
    textSecondary = Color(0xFF6E6E68),
    divider = Color(0xFFE4E4DE),
    danger = Color(0xFFDC2626),
    glassFill = Color(0x99FFFFFF),
    glassBorder = Color(0x240C0C09),
    glassShadow = Color(0x1A0C0C09),
    chipFill = Color(0x5CFFFFFF),
    chipSelectedFill = Color(0xCCFFFFFF),
    dockHighlight = Color(0xD9FFFFFF),
)

val DarkPalette = PaletteScheme(
    brand = Color(0xFFEDEDE8),
    brandSecondary = Color(0xFF8F8AD6),
    appBg = Color(0xFF111114),
    cardBg = Color(0xFF1C1C20),
    searchBg = Color(0xFF2A2A2F),
    textPrimary = Color(0xFFF2F2EE),
    // 0xFFB6B6AD 在玻璃卡上约 4.4:1，比 0xFF9B9B93 的 2.5:1 可读。
    textSecondary = Color(0xFFB6B6AD),
    divider = Color(0xFF2E2E33),
    danger = Color(0xFFE15555),
    glassFill = Color(0x731C1C20),
    glassBorder = Color(0x2EF2F2EE),
    glassShadow = Color(0x66000000),
    chipFill = Color(0x33FFFFFF),
    chipSelectedFill = Color(0x4DFFFFFF),
    dockHighlight = Color(0x33FFFFFF),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
