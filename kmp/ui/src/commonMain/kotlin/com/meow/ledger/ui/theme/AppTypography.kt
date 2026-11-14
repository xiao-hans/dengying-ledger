package com.meow.ledger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 灯影字阶（`docs/风格对照.md` §3，对齐 dengY `Theme.kt` 的 `AppTypography`）。
 *
 * 8 档 11–20sp，行高统一按 1.35 倍取整数 sp（中文在 1.2 倍行高下会显得挤）：
 * 20/27、18/24、17/23、15/20、14/19、13/18、12/16、11/15。
 *
 * 【单一真相】字号 / 行高 / 字重只在本文件定义。页面的 `Text` 不直接写死
 * `fontSize = N.sp`，一律取 `AppTypography.xxx`（或 `AppTypography.xxx.bold()`），
 * 深浅色三态共用同一份字阶（字阶不随主题变化，但由 [MeowTheme] 一并注入，
 * 保证预览 / 测试里没有主题上下文时也有确定的缺省值 [DefaultTypography]）。
 */
data class AppTypographyScheme(
    /** 品牌字「喵啊记账」，20/27 Bold。 */
    val titleLarge: TextStyle,
    /** 页面标题，18/24 Bold。 */
    val titleMedium: TextStyle,
    /** 详情 / 对话框标题，17/23 Bold。 */
    val titleSmall: TextStyle,
    /** 列表条目名，15/20。 */
    val bodyLarge: TextStyle,
    /** 正文默认，14/19。 */
    val bodyMedium: TextStyle,
    /** 次要说明，13/18。 */
    val bodySmall: TextStyle,
    /** 元信息 / 分区标题，12/16。 */
    val labelMedium: TextStyle,
    /** Dock 文字 / 角标，11/15。 */
    val labelSmall: TextStyle,
)

/** 行高 = 字号 × 1.35（取整），与 Web 参考 `src/theme/tokens.ts` 的 `TypeScale` 逐值一致。 */
private fun textStyle(size: Int, line: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

val DefaultTypography = AppTypographyScheme(
    titleLarge = textStyle(20, 27, FontWeight.Bold),
    titleMedium = textStyle(18, 24, FontWeight.Bold),
    titleSmall = textStyle(17, 23, FontWeight.Bold),
    bodyLarge = textStyle(15, 20),
    bodyMedium = textStyle(14, 19),
    bodySmall = textStyle(13, 18),
    labelMedium = textStyle(12, 16),
    labelSmall = textStyle(11, 15),
)

/**
 * 当前主题的字阶，由 [MeowTheme] 注入。字阶不随深浅色变化，但走 CompositionLocal
 * 可以让预览 / 单测显式替换，也避免调用点散落 `N.sp` 魔法数字。
 */
val LocalTypography = staticCompositionLocalOf { DefaultTypography }

/** 兼容层：@Composable getter 读 CompositionLocal，调用点零改动。 */
object AppTypography {
    val titleLarge: TextStyle @Composable get() = LocalTypography.current.titleLarge
    val titleMedium: TextStyle @Composable get() = LocalTypography.current.titleMedium
    val titleSmall: TextStyle @Composable get() = LocalTypography.current.titleSmall
    val bodyLarge: TextStyle @Composable get() = LocalTypography.current.bodyLarge
    val bodyMedium: TextStyle @Composable get() = LocalTypography.current.bodyMedium
    val bodySmall: TextStyle @Composable get() = LocalTypography.current.bodySmall
    val labelMedium: TextStyle @Composable get() = LocalTypography.current.labelMedium
    val labelSmall: TextStyle @Composable get() = LocalTypography.current.labelSmall
}

/** 语义化字重别名：半粗用于选中态 chip / 强调数字，粗体用于金额与标题。 */
fun TextStyle.semiBold(): TextStyle = copy(fontWeight = FontWeight.SemiBold)
fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.Bold)
