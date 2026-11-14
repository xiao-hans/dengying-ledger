package com.meow.ledger.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 灯影配色，逐值移植自 dengY 的 Color.kt。
 * 深色不是浅色的反相，而是同一套语义重新取值：底色近黑带暖、卡片亮一档、文字反白。
 *
 * 【单一真相】玻璃质感（fill / fillStrong / darkFill / liquidTint / border / shadow /
 * chipFill / chipSelectedFill / dockHighlight / sheenAlpha）统一由 `Glass.kt` 的
 * [GlassScheme] 定义取值；[PaletteScheme] 只持有「与本配色配套的那一份玻璃方案」
 * （[glass] 字段），不复制任何颜色值。下面六个 `glassXxx` / `chipXxx` / `dockHighlight`
 * 成员是为旧调用点保留的**委托只读属性**，全部转发到 [glass]，因此：
 *   - 取值永远只有 `Glass.kt` 一处定义（无两份真相）；
 *   - `palette.glassFill` 等 35 处旧调用零改动，且随 [paletteOf] 的三态解析自动切换深浅玻璃。
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
    /** 与本配色配套的玻璃方案，取值唯一来源为 `Glass.kt`。 */
    val glass: GlassScheme,
) {
    // ——— 兼容委托：不在本类重复定义任何玻璃色值 ———
    val glassFill: Color get() = glass.fill
    val glassBorder: Color get() = glass.border
    val glassShadow: Color get() = glass.shadow
    val chipFill: Color get() = glass.chipFill
    val chipSelectedFill: Color get() = glass.chipSelectedFill
    val dockHighlight: Color get() = glass.dockHighlight
}

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
    glass = LightGlass,
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
    glass = DarkGlass,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
