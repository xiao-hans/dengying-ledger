package com.meow.ledger.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.ui.theme.LocalPalette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ImportCsvButton(
    enabled: Boolean,
    modifier: Modifier,
    onPicked: (name: String, content: String) -> Unit,
) {
    // 桌面端没有文件选择用途，保持隐藏由调用方决定。
    Button(onClick = {}, enabled = false, modifier = modifier) { Text("选择 CSV 文件") }
}

actual fun shareTextFile(name: String, content: String): Boolean = false

actual val platformOcrName: String? = null

@Composable
actual fun PickImageButton(enabled: Boolean, modifier: Modifier, onPicked: (path: String, ocrText: String?) -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.divider),
        contentAlignment = Alignment.Center,
    ) {
        Text("选择票据图", color = palette.textSecondary, fontSize = 15.sp)
    }
}