package com.meow.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit, modifier: Modifier) {
    // 桌面端没有系统返回键，保持空实现。
}
