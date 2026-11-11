package com.meow.ledger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 系统返回键的统一接入口。
 * enabled 为 true 时拦截返回交给 onBack（二级页回上级），
 * false 时不拦截，由系统决定行为。
 */
@Composable
expect fun BackButton(enabled: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier)
