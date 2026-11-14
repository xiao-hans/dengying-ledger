package com.meow.ledger.ui.auto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meow.ledger.shared.auto.GUIDE_STEPS
import com.meow.ledger.shared.auto.PendingEdit
import com.meow.ledger.shared.auto.WATCH_APPS
import com.meow.ledger.shared.auto.sourceLabel
import com.meow.ledger.shared.bills.TYPE_EXPENSE
import com.meow.ledger.shared.bills.TYPE_INCOME
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.db.Auto_event
import com.meow.ledger.shared.db.Category
import com.meow.ledger.shared.db.Rule
import com.meow.ledger.ui.theme.DangerConfirmDialog
import com.meow.ledger.ui.theme.GlassChip
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

/**
 * 待确认队列（交互纪律 §6.7）。
 * 支持多选/全选、批量通过、批量忽略，以及逐条「修改」金额/备注/分类后再确认。
 * 任何写账都只在用户点击「确认入账 / 批量通过」后发生，绝不静默入账。
 */
@Composable
fun PendingPage(
    events: List<Auto_event>,
    categories: List<Category>,
    onBack: () -> Unit,
    onConfirmOne: (String, PendingEdit?) -> Unit,
    onBatchConfirm: (Set<String>, Map<String, PendingEdit>) -> Unit,
    onBatchIgnore: (Set<String>) -> Unit,
) {
    val palette = LocalPalette.current
    // 操作后立即从列表移除并清空选择：数据库写完只是持久层，页面必须跟着变。
    var items by remember { mutableStateOf(events) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var edits by remember { mutableStateOf(emptyMap<String, PendingEdit>()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var confirmBatch by remember { mutableStateOf(false) }
    var ignoreBatch by remember { mutableStateOf(false) }

    fun drop(ids: Set<String>) {
        items = items.filterNot { it.id in ids }
        selected = selected - ids
        edits = edits - ids
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("待确认队列", onBack)
        if (items.isEmpty()) {
            EmptyHint("没有待确认的识别结果", "去三路采集粘贴一条支付通知试试")
            return@Column
        }

        val allSelected = selected.size == items.size
        Row(
            Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { on -> selected = if (on) items.map { it.id }.toSet() else emptySet() },
                colors = CheckboxDefaults.colors(checkedColor = palette.brand, uncheckedColor = palette.textSecondary),
            )
            Text("全选（已选 ${selected.size} 条）", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("批量通过", filled = true, enabled = selected.isNotEmpty()) { confirmBatch = true }
            SmallButton("批量忽略", enabled = selected.isNotEmpty()) { ignoreBatch = true }
        }

        items.forEach { event ->
            val edit = edits[event.id]
            val checked = event.id in selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .glassCard(elevation = 2.dp)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { on -> selected = if (on) selected + event.id else selected - event.id },
                        colors = CheckboxDefaults.colors(checkedColor = palette.brand, uncheckedColor = palette.textSecondary),
                    )
                    Text(event.merchant, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val sign = if (event.type == TYPE_INCOME) "+" else "-"
                    Text("$sign${centsToYuan(edit?.money ?: event.money)}", color = if (event.type == TYPE_INCOME) palette.brandSecondary else palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("${sourceLabel(event.source)} · ${event.raw_text}", color = palette.textSecondary, fontSize = 12.sp)
                if (edit != null) {
                    Text("已修改：${edit.desc.ifBlank { event.merchant }} · ${categoryName(categories, edit.memberCateId)}", color = palette.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("修改") { editingId = event.id }
                    SmallButton("忽略") { onBatchIgnore(setOf(event.id)); drop(setOf(event.id)) }
                    SmallButton("确认入账", filled = true) {
                        onConfirmOne(event.id, edit)
                        drop(setOf(event.id))
                    }
                }
            }
        }
    }

    // 批量通过的二次确认：明确写出会写入多少笔账单。
    if (confirmBatch) {
        DangerConfirmDialog(
            title = "批量通过",
            message = "将把选中的 ${selected.size} 条识别结果写入账本，已修改的金额/备注/分类会一并生效。入账后不能撤销。",
            confirmLabel = "确认入账",
            onConfirm = {
                onBatchConfirm(selected, edits)
                drop(selected)
                confirmBatch = false
            },
            onDismiss = { confirmBatch = false },
        )
    }
    // 批量忽略的二次确认：忽略不写账单，但同样是不可恢复的操作，确认按钮用 danger token。
    if (ignoreBatch) {
        DangerConfirmDialog(
            title = "批量忽略",
            message = "选中的 ${selected.size} 条识别结果会被忽略，不再写入账本，不能恢复。",
            confirmLabel = "忽略",
            onConfirm = {
                onBatchIgnore(selected)
                drop(selected)
                ignoreBatch = false
            },
            onDismiss = { ignoreBatch = false },
        )
    }

    editingId?.let { id ->
        val event = items.firstOrNull { it.id == id } ?: return@let
        PendingEditDialog(
            event = event,
            categories = categories,
            initial = edits[id],
            onDismiss = { editingId = null },
            onSave = { edit ->
                edits = edits + (id to edit)
                editingId = null
            },
        )
    }
}

/** 修改金额 / 备注 / 分类。金额用受控字符串 + 即时校验，不吞字符、不改数量级（§6.3）。 */
@Composable
private fun PendingEditDialog(
    event: Auto_event,
    categories: List<Category>,
    initial: PendingEdit?,
    onDismiss: () -> Unit,
    onSave: (PendingEdit) -> Unit,
) {
    val palette = LocalPalette.current
    var money by rememberSaveable { mutableStateOf(centsToYuan(initial?.money ?: event.money)) }
    var desc by rememberSaveable { mutableStateOf(initial?.desc ?: event.desc) }
    var cateId by rememberSaveable { mutableStateOf(initial?.memberCateId ?: event.member_cate_id) }
    var error by rememberSaveable { mutableStateOf("") }
    val visible = categories.filter { it.type == event.type }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = { Text("修改识别结果", color = palette.textPrimary) },
        text = {
            Column {
                Text("金额（元）", color = palette.textSecondary, fontSize = 12.sp)
                BasicTextField(
                    value = money,
                    onValueChange = { money = it; error = "" },
                    textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 18.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.searchBg).padding(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("备注", color = palette.textSecondary, fontSize = 12.sp)
                BasicTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.searchBg).padding(12.dp),
                    decorationBox = { inner -> Box { if (desc.isEmpty()) Text(event.merchant, color = palette.textSecondary, fontSize = 14.sp); inner() } },
                )
                Spacer(Modifier.height(10.dp))
                Text("分类", color = palette.textSecondary, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { cate ->
                        GlassChip(
                            label = cate.title,
                            selected = cate.id == cateId,
                            onClick = { cateId = cate.id },
                        )
                    }
                }
                if (error.isNotEmpty()) Text(error, color = palette.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = toCents(money)
                if (cents == null || cents == 0L) {
                    error = "请输入有效金额（最多两位小数）"
                    return@TextButton
                }
                onSave(PendingEdit(cents, desc.trim(), cateId))
            }, colors = ButtonDefaults.textButtonColors(contentColor = palette.brand)) { Text("保存修改") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary)) { Text("取消") }
        },
    )
}

private fun categoryName(categories: List<Category>, id: Long): String =
    categories.firstOrNull { it.id == id }?.title ?: "未分类"

/** 匹配规则。按关键词命中后自动带入分类与账户。删除必须二次确认（§6.1）。 */
@Composable
fun RulesPage(
    rules: List<Rule>,
    onBack: () -> Unit,
    onAdd: (keyword: String, type: Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val palette = LocalPalette.current
    // 添加/删除后立即反映到列表，与持久层保持一致。
    var rules by remember { mutableStateOf(rules) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Rule?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("匹配规则", onBack)
        Text("关键词越长越优先。命中后，新的识别结果会带上对应的收支类型。", color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = keyword,
            onValueChange = { keyword = it },
            textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 15.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(999.dp)).background(palette.searchBg).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner -> Box { if (keyword.isEmpty()) Text("关键词，例如 星巴克", color = palette.textSecondary, fontSize = 14.sp); inner() } },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (income) "记为收入" else "记为支出", color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = income, onCheckedChange = { income = it })
        }
        ActionButton("添加规则", keyword.trim().isNotEmpty()) {
            val kw = keyword.trim()
            onAdd(kw, if (income) TYPE_INCOME else TYPE_EXPENSE)
            rules = rules + Rule(
                id = (rules.maxOfOrNull { it.id } ?: 0L) + 1,
                key_word = kw,
                source = 0,
                member_id = 0,
                type = if (income) TYPE_INCOME else TYPE_EXPENSE,
                book_id = 0,
                member_cate_id = if (income) 101L else 9L,
                assets_id = 1,
                tag_ids = "",
                create_date = 0,
                update_date = 0,
            )
            keyword = ""
        }
        Spacer(Modifier.height(12.dp))
        if (rules.isEmpty()) EmptyHint("还没有规则", "添加一条关键词开始自动分类")
        rules.forEach { rule ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).glassCard(elevation = 2.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.key_word, color = palette.textPrimary, fontSize = 15.sp)
                    Text(if (rule.type == TYPE_INCOME) "收入" else "支出", color = palette.textSecondary, fontSize = 12.sp)
                }
                Text(
                    "删除",
                    color = palette.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { pendingDelete = rule }.padding(6.dp),
                )
            }
        }
    }

    pendingDelete?.let { rule ->
        DangerConfirmDialog(
            title = "删除规则",
            message = "规则「${rule.key_word}」会被永久移除，不能再用来自动分类，且不能撤销。已记账单不受影响。",
            confirmLabel = "删除",
            onConfirm = {
                onDelete(rule.id)
                rules = rules.filterNot { it.id == rule.id }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 监听白名单。从未保存过时默认前三个。 */
@Composable
fun AppsPage(
    selected: Set<String>,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("监听 App 白名单", onBack)
        Text("只识别白名单内 App 的文本，其余一律跳过。", color = palette.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        WATCH_APPS.forEach { app ->
            val on = app.pkg in selected
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).glassCard(elevation = 2.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(app.name, color = palette.textPrimary, fontSize = 15.sp)
                    Text(app.pkg, color = palette.textSecondary, fontSize = 11.sp)
                }
                Switch(checked = on, onCheckedChange = { onToggle(app.pkg) })
            }
        }
    }
}

/** 保活说明。Web 端没有后台进程，这里只保留开关与说明，不假装拥有系统权限。 */
@Composable
fun KeepAlivePage(on: Boolean, onBack: () -> Unit, onToggle: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        BackHeader("保活设置", onBack)
        Row(
            Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("前台服务保活", color = palette.textPrimary, fontSize = 15.sp)
                Text("安卓端用前台通知降低被回收的概率", color = palette.textSecondary, fontSize = 12.sp)
            }
            Switch(checked = on, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "当前是 Web 复刻，没有常驻后台。开关只记录你的偏好，真正的保活要在安卓端配合厂商的自启动与电池白名单。",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
    }
}

/** 五步引导，对齐 GuideAutoBillActivity。 */
@Composable
fun GuidePage(onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        BackHeader("使用引导", onBack)
        GUIDE_STEPS.forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Box(
                    Modifier.height(24.dp).clip(RoundedCornerShape(999.dp)).background(palette.brand).padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = palette.cardBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(step.body, color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 自动记账设置（只读说明）。
 * 原先的「命中规则后自动入账」开关已下线：它没有任何业务语义，而且与交互纪律 §6.7
 * 「识别结果一律进待确认队列、绝不静默入账」冲突。这里改为明确告知用户这条规则，
 * 不再保留任何可切换的开关（Settings.kt 的 KEY_AUTO_CONFIRM 仅为兼容旧库保留）。
 */
@Composable
fun AutoSettingsPage(onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        BackHeader("自动记账设置", onBack)
        Column(
            Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(16.dp),
        ) {
            Text("确认后才入账", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "所有识别结果（无论是否命中规则）都进入待确认队列，需要你逐条或批量确认后才会写入账本。为了账目准确，本 App 不提供自动入账。",
                color = palette.textSecondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "可在「待确认队列」里批量通过、批量忽略，或逐条修改金额、备注与分类。",
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SmallButton(label: String, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(999.dp))
            .background(if (!enabled) palette.divider else if (filled) palette.brand else palette.searchBg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled && enabled) palette.cardBg else palette.textPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyHint(title: String, next: String) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = palette.textPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(next, color = palette.textSecondary, fontSize = 13.sp)
    }
}
