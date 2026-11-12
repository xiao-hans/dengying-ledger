package com.meow.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
