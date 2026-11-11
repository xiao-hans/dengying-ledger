package com.meow.ledger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.ui.theme.LocalPalette
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun ImportCsvButton(
    enabled: Boolean,
    modifier: Modifier,
    onPicked: (name: String, content: String) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryName(context, uri)
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull().orEmpty()
        onPicked(name, content)
    }
    Button(
        onClick = { launcher.launch(arrayOf("text/*", "text/comma-separated-values", "application/csv", "text/csv")) },
        enabled = enabled,
        modifier = modifier,
    ) { Text("选择 CSV 文件") }
}

private fun queryName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
    }
    return uri.lastPathSegment ?: "导入文件.csv"
}

actual fun shareTextFile(name: String, content: String): Boolean = try {
    val file = File(fileShareDir(), name)
    file.writeText(content, Charsets.UTF_8)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享 " + name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    AndroidHost.appContext.startActivity(chooser)
    true
} catch (_: Exception) {
    false
}

actual val platformOcrName: String? = null

private fun fileShareDir(): File = File(AndroidHost.appContext.cacheDir, "share").apply { mkdirs() }

@Composable
actual fun PickImageButton(enabled: Boolean, modifier: Modifier, onPicked: (path: String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryName(context, uri)
        val copied = runCatching {
            val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
            val out = File(dir, name.ifBlank { "ticket.jpg" })
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.absolutePath
        }.getOrNull()
        if (copied != null) onPicked(copied)
    }
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.brand else palette.divider)
            .clickable(enabled = enabled) { launcher.launch(arrayOf("image/*")) },
        contentAlignment = Alignment.Center,
    ) {
        Text("选择票据图", color = palette.cardBg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}