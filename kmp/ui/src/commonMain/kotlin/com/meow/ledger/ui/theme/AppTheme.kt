package com.meow.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.meow.ledger.shared.auto.THEME_DARK
import com.meow.ledger.shared.auto.THEME_LIGHT

/** 三态主题统一解析：跟随系统 / 浅色 / 深色。入口页与主界面共用，避免深浅色错乱。 */
@Composable
fun isDarkTheme(theme: String): Boolean = when (theme) {
    THEME_DARK -> true
    THEME_LIGHT -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun paletteOf(theme: String): PaletteScheme = if (isDarkTheme(theme)) DarkPalette else LightPalette

/** 与 [paletteOf] 同一份三态判定，玻璃 token 随配色一起切换，避免深浅色玻璃错配。 */
@Composable
fun glassOf(theme: String): GlassScheme = if (isDarkTheme(theme)) DarkGlass else LightGlass

/**
 * 主题注入的唯一入口：一次性提供 [LocalPalette]、[LocalGlassScheme] 与 [LocalTypography]。
 * 配色与玻璃共用同一份三态判定，字阶不随深浅色变化但同样经此注入（预览 / 单测可替换）。
 * 入口页 `EntryNavHost` 与主界面 `MeowApp` 必须用本函数包住内容，否则
 * [LocalGlassScheme] 会退回缺省的 [LightGlass]，深色模式下的玻璃配色会错。
 *
 * 旧的 `CompositionLocalProvider(LocalPalette provides palette)` 写法不提供玻璃方案与
 * 字阶，已全部替换为本函数。
 */
@Composable
fun MeowTheme(theme: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides paletteOf(theme),
        LocalGlassScheme provides glassOf(theme),
        LocalTypography provides DefaultTypography,
        content = content,
    )
}
