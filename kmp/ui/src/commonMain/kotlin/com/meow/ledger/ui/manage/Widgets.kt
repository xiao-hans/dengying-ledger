package com.meow.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.db.Widget_user
import com.meow.ledger.shared.manage.WIDGET_KIND_MONTH
import com.meow.ledger.shared.manage.WIDGET_KINDS
import com.meow.ledger.shared.manage.WIDGET_SIZE_SMALL
import com.meow.ledger.shared.manage.WIDGET_SIZES
import com.meow.ledger.shared.manage.WidgetRepository
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

/**
 * 小组件页。key：widgets、widget-new、widget:<id>。
 * 只是本地预览，不挂到系统桌面。
 */
@Composable
fun WidgetsPage(
    repo: WidgetRepository,
    month: Long,
    page: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    when {
        page == "widget-new" -> WidgetEdit(repo, month, null, onBack)
        page.startsWith("widget:") -> {
            val row = repo.get(page.removePrefix("widget:"))
            if (row == null) Missing("找不到这个组件", onBack) else WidgetEdit(repo, month, row, onBack)
        }
        else -> WidgetList(repo, month, onOpen, onBack)
    }
}

@Composable
private fun WidgetList(repo: WidgetRepository, month: Long, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val rows = repo.all()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        BackHeader("小组件", onBack)
        Text("只在这里预览，不会挂到系统桌面", color = palette.textSecondary, fontSize = 12.sp)
        ActionButton("添加组件", true) { onOpen("widget-new") }
        if (rows.isEmpty()) {
            Empty("还没有组件", "选一个尺寸，看看它在桌面上的样子")
        }
        rows.forEach { row ->
            GlassCard(onClick = { onOpen("widget:${row.id}") }) {
                Preview(repo, row, month)
            }
        }
    }
}

@Composable
private fun WidgetEdit(repo: WidgetRepository, month: Long, existing: Widget_user?, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var size by remember { mutableStateOf(existing?.size ?: WIDGET_SIZE_SMALL) }
    var kind by remember { mutableStateOf(existing?.kind ?: WIDGET_KIND_MONTH) }
    var confirming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        BackHeader(if (existing == null) "添加组件" else "编辑组件", onBack)
        Field("名称", name, { name = it }, "留空则用组件种类名")
        Text("尺寸", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            WIDGET_SIZES.forEach { ChoiceChip(it.name, it.id == size) { size = it.id } }
        }
        Text("内容", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            WIDGET_KINDS.forEach { ChoiceChip(it.name, it.id == kind) { kind = it.id } }
        }
        Text("预览", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        GlassCard {
            Preview(repo, Widget_user("preview", name, size, kind, 0), month)
        }
        ActionButton("保存", true) {
            repo.save(name, size, kind, existing?.id ?: "")
            onBack()
        }
        if (existing != null) {
            if (confirming) {
                ConfirmBar("移除这个组件？只删掉预览，账单不受影响。", { confirming = false }, {
                    repo.delete(existing.id)
                    onBack()
                }, "移除")
            } else {
                ActionButton("移除", true) { confirming = true }
            }
        }
    }
}

@Composable
private fun Preview(repo: WidgetRepository, row: Widget_user, month: Long) {
    val palette = LocalPalette.current
    val size = WIDGET_SIZES.firstOrNull { it.id == row.size }?.name ?: row.size
    val kind = WIDGET_KINDS.firstOrNull { it.id == row.kind }?.name ?: row.kind
    val preview = repo.preview(row.kind, month)
    Text(row.name.ifBlank { kind }, color = palette.textPrimary, fontWeight = FontWeight.Medium)
    Text("$kind · $size", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(preview.firstLabel, color = palette.textSecondary, fontSize = 12.sp)
            Text(centsToYuan(preview.firstValue), color = palette.textPrimary)
        }
        Column {
            Text(preview.secondLabel, color = palette.textSecondary, fontSize = 12.sp)
            Text(centsToYuan(preview.secondValue), color = palette.textPrimary)
        }
    }
}
