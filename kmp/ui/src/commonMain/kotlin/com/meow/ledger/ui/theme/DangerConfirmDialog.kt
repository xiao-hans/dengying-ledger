package com.meow.ledger.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 危险操作的二次确认弹窗（交互纪律 §6.1）。
 * 确认按钮固定用 danger token（§6.10），取消用次要文字色，不用品牌色冒充警告色。
 */
@Composable
fun DangerConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = { Text(title, color = palette.textPrimary) },
        text = { Text(message, color = palette.textSecondary) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.danger),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
            ) { Text("取消") }
        },
    )
}
