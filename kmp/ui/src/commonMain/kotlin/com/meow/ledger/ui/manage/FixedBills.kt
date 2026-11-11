package com.meow.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.meow.ledger.shared.bills.centsToYuan
import com.meow.ledger.shared.bills.toCents
import com.meow.ledger.shared.manage.CYCLE_MONTH
import com.meow.ledger.shared.manage.CYCLE_WEEK
import com.meow.ledger.shared.manage.CYCLE_YEAR
import com.meow.ledger.shared.manage.FixedBillRepository
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

private val CYCLES = listOf(CYCLE_MONTH.toLong() to "每月", CYCLE_WEEK.toLong() to "每周", CYCLE_YEAR.toLong() to "每年")

/** 周期账：fixed / fixed-new / fixed-edit:<id>。到期只提醒，不静默入账。 */
@Composable
fun FixedBillsPage(repository: FixedBillRepository, page: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var notice by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            page == "fixed" -> FixedList(repository, onOpen, onBack) { notice = it }
            page == "fixed-new" -> FixedForm(repository, "", onBack) { notice = it }
            page.startsWith("fixed-edit:") -> FixedForm(repository, page.substringAfter(":"), onBack) { notice = it }
        }
        if (notice.isNotEmpty()) Text(notice, color = LocalPalette.current.danger, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun FixedList(repository: FixedBillRepository, onOpen: (String) -> Unit, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    val rows = remember(version) { repository.all() }
    val due = remember(version) { repository.due() }
    BackHeader("周期账", onBack)
    Text("到期只提醒，不会自动写入账单。确认后到记一笔里入账。", color = palette.textSecondary)
    if (due.isNotEmpty()) {
        Text("今天该提醒", color = palette.textPrimary, modifier = Modifier.padding(top = 12.dp))
        due.forEach { reminder ->
            GlassCard {
                Text(reminder.title, color = palette.textPrimary)
                Text("${reminder.hour}:00", color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    if (rows.isEmpty()) Empty("还没有周期账", "房租、会员费这类固定支出可以设成周期", "新增周期账") { onOpen("fixed-new") }
    rows.forEach { bill ->
        val cycle = CYCLES.firstOrNull { it.first == bill.fixed_type }?.second ?: "周期"
        GlassCard(onClick = { onOpen("fixed-edit:${bill.id}") }) {
            Text(bill.desc.ifBlank { "未命名" }, color = palette.textPrimary)
            Text(
                "$cycle ${bill.fixed_day} · ${if (bill.type == 2L) "收入" else "支出"} ${centsToYuan(bill.money)} 元",
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    ActionButton("新增周期账", true) { onOpen("fixed-new") }
    if (version < 0) onNotice("")
}

@Composable
private fun FixedForm(repository: FixedBillRepository, id: String, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    val existing = remember(id) { id.takeIf { it.isNotEmpty() }?.let(repository::one) }
    if (id.isNotEmpty() && existing == null) { Missing("周期账不存在", onBack); return }
    var desc by remember { mutableStateOf(existing?.desc ?: "") }
    var money by remember { mutableStateOf(existing?.let { centsToYuan(it.money) } ?: "") }
    var billType by remember { mutableStateOf(existing?.type ?: 1L) }
    var cycle by remember { mutableStateOf(existing?.fixed_type ?: CYCLE_MONTH.toLong()) }
    var day by remember { mutableStateOf(existing?.fixed_day?.toString() ?: "1") }
    var remind by remember { mutableStateOf(existing?.let { repository.remindersOf(it.id).isNotEmpty() } ?: true) }
    var hour by remember { mutableStateOf(existing?.let { repository.remindersOf(it.id).firstOrNull()?.hour?.toString() } ?: "9") }
    var confirm by remember { mutableStateOf(false) }
    val dayMax = if (cycle == CYCLE_WEEK.toLong()) 7L else 28L
    BackHeader(if (existing == null) "新增周期账" else "编辑周期账", onBack)
    Field("名称", desc, { desc = it }, "例如 房租")
    Field("金额（元）", money, { money = it }, "0.00")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        ChoiceChip("支出", billType == 1L) { billType = 1 }
        ChoiceChip("收入", billType == 2L) { billType = 2 }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CYCLES.forEach { (value, label) -> ChoiceChip(label, cycle == value) { cycle = value } }
    }
    Field(if (cycle == CYCLE_WEEK.toLong()) "周几（1-7）" else "几号（1-28）", day, { day = it }, "1")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        ChoiceChip("到期提醒", remind) { remind = true }
        ChoiceChip("不提醒", !remind) { remind = false }
    }
    if (remind) Field("提醒时间（0-23 点）", hour, { hour = it }, "9")
    ActionButton("保存", desc.isNotBlank() && toCents(money) != null) {
        val amount = toCents(money)
        val dayNumber = day.toLongOrNull()
        val hourNumber = hour.toLongOrNull() ?: 9L
        if (amount == null || amount <= 0) { onNotice("请填写大于 0 的金额"); return@ActionButton }
        if (dayNumber == null || dayNumber !in 1..dayMax) { onNotice("日期要在 1 到 $dayMax 之间"); return@ActionButton }
        if (remind && hourNumber !in 0..23) { onNotice("提醒时间要在 0 到 23 之间"); return@ActionButton }
        val saved = repository.save(desc.trim(), amount, billType, cycle.toLong(), dayNumber, false, existing?.id ?: "") ?: return@ActionButton
        repository.remindersOf(saved.id).forEach { repository.deleteReminder(it.id) }
        if (remind) repository.saveReminder(desc.trim(), dayNumber, hourNumber, saved.id)
        onNotice("周期账已保存")
        onBack()
    }
    if (existing != null) {
        if (confirm) ConfirmBar("删除周期账「${existing.desc}」？已入账的账单不受影响，提醒会一起删掉。", { confirm = false }, {
            repository.delete(existing.id)
            onBack()
        }, "删除")
        else ActionButton("删除周期账", true) { confirm = true }
    }
    if (dayMax < 0) Text("", color = palette.textSecondary)
}

