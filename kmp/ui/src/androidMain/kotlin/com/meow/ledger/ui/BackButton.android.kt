package com.meow.ledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit, modifier: Modifier) {
    BackHandler(enabled = enabled, onBack = onBack)
}
