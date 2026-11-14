package com.meow.ledger.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import com.meow.ledger.ui.ImportCsvButton
import com.meow.ledger.ui.shareTextFile
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.manage.AccountRepository
import com.meow.ledger.shared.manage.ExchangeRepository
import com.meow.ledger.shared.manage.SOURCE_IMPORT
import com.meow.ledger.shared.manage.SOURCE_OCR
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

/** 导出与导入。page 为 export、import 或 import:<id>。 */
@Composable
fun ExchangePage(
    exchange: ExchangeRepository,
    account: AccountRepository,
    page: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    when {
        page == "export" -> ExportHome(exchange, onBack)
        page.startsWith("import:") -> {
            val file = account.imports().firstOrNull { it.id.toString() == page.substringAfter(':') }
            if (file == null) Missing("这份导入记录不存在", onBack) else ImportPreview(exchange, account, file.name, file.content, file.id, onBack)
        }
        else -> ImportHome(exchange, account, onOpen, onBack)
    }
}

@Composable
private fun ExportHome(exchange: ExchangeRepository, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val text = remember { exchange.csv() }
    var shared by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("导出", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("下面是全部账单的 CSV，长按可以复制", color = palette.textSecondary, fontSize = 13.sp)
            Button(onClick = { shared = shareTextFile("meow-bills.csv", text) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (shared) "已生成分享文件" else "导出并分享")
            }
            if (shared) {
                Text("文件在应用缓存目录，已拉起系统分享", color = palette.textSecondary, fontSize = 12.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = {},
                readOnly = true,
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(12.dp),
            )
        }
    }
}

/** 粘贴 CSV 直接导入；以前导入过的文件可以重新导入。 */
@Composable
private fun ImportHome(exchange: ExchangeRepository, account: AccountRepository, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var text by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    val files = remember(result) { account.imports() }
    var fileName by rememberSaveable { mutableStateOf("") }
    var fileText by rememberSaveable { mutableStateOf("") }
    var fileRows by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("导入账单", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ImportCsvButton(enabled = true, onPicked = { name, content ->
                fileName = name
                fileText = content
                fileRows = content.count { it == '\n' } + 1
            })
            if (fileText.isNotBlank()) {
                Text("$fileName · $fileRows 行", color = palette.textSecondary, fontSize = 12.sp)
                Button(
                    onClick = {
                        val outcome = exchange.importCsv(fileText, SOURCE_IMPORT)
                        account.recordImport(fileName, outcome.created, fileText)
                        fileText = ""
                        result = "新增 ${outcome.created} 条，跳过重复 ${outcome.duplicate} 条，跳过无效 ${outcome.skipped} 条"
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导入该文件") }
            }
            Text("粘贴 CSV。相同备注、金额和日期的账单会被跳过，不会重复入账", color = palette.textSecondary, fontSize = 13.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).glassCard(elevation = 2.dp).padding(12.dp),
            )
            if (result.isNotEmpty()) Text(result, color = palette.textPrimary, fontSize = 13.sp)
            if (files.isEmpty()) {
                Empty("还没有导入记录", "导入一次后会记在这里，可以重新导入")
            }
            files.forEach { file ->
                GlassCard(onClick = { onOpen("import:${file.id}") }) {
                    Text(file.name, color = palette.textPrimary)
                    Text("${file.count} 条", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            ActionButton("导入", text.isNotBlank()) {
                val outcome = exchange.importCsv(text, SOURCE_IMPORT)
                account.recordImport("粘贴导入", outcome.created, text)
                text = ""
                result = "新增 ${outcome.created} 条，跳过重复 ${outcome.duplicate} 条，跳过无效 ${outcome.skipped} 条"
            }
        }
    }
}

/** 重新导入一份已记录的文件。OCR 来源的文件按 OCR 记账。 */
@Composable
private fun ImportPreview(
    exchange: ExchangeRepository,
    account: AccountRepository,
    name: String,
    content: String,
    id: String,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    var result by rememberSaveable { mutableStateOf("") }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val source = if (name.startsWith("OCR")) SOURCE_OCR else SOURCE_IMPORT
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(name, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (content.isBlank()) {
                Empty("这份记录没有保存内容", "只能重新导入带内容的记录")
            } else {
                BasicTextField(
                    value = content,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(12.dp),
                )
            }
            if (result.isNotEmpty()) Text(result, color = palette.textPrimary, fontSize = 13.sp)
            Text("重新导入不会重复记账：相同备注、金额和日期的会被跳过", color = palette.textSecondary, fontSize = 12.sp)
        }
        if (askDelete) {
            ConfirmBar("删除这条导入记录，已入账的账单会保留", { askDelete = false }, {
                account.deleteImport(id)
                onBack()
            }, "删除")
        } else {
            Box(Modifier.padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("重新导入", content.isNotBlank()) {
                        val outcome = exchange.importCsv(content, source)
                        account.recordImport(name, outcome.created, content)
                        result = "新增 ${outcome.created} 条，跳过重复 ${outcome.duplicate} 条，跳过无效 ${outcome.skipped} 条"
                    }
                    ActionButton("删除记录", true) { askDelete = true }
                }
            }
        }
    }
}
