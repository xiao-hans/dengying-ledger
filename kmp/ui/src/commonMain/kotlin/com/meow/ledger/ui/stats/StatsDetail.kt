package com.meow.ledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.meow.ledger.shared.bills.netPaid
import com.meow.ledger.shared.db.Bill
import com.meow.ledger.shared.db.Refund_bill
import com.meow.ledger.shared.db.Tag
import com.meow.ledger.shared.stats.PeriodTotal
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.home.formatYuan
import com.meow.ledger.ui.manage.ChoiceChip
import com.meow.ledger.ui.theme.LocalPalette
import com.meow.ledger.ui.theme.glassCard

/** 统计下钻二级页。page 为 cate:<id>、tags、tag:<id>、type、report。 */
@Composable
fun StatsPages(
    page: String,
    month: Long,
    year: Long,
    type: Long,
    names: Map<Long, String>,
    bills: List<Bill>,
    refunds: List<Refund_bill>,
    tags: List<Tag>,
    months: List<PeriodTotal>,
    onOpenPage: (String) -> Unit,
    onOpenBill: (String) -> Unit,
    onType: (Long) -> Unit,
    onBack: () -> Unit,
) {
    when {
        page.startsWith("cate:") -> {
            val id = page.substringAfter(':').toLongOrNull() ?: 0L
            BillListPage(
                title = names[id] ?: "分类下钻",
                bills = bills.filter { it.member_cate_id == id },
                refunds = refunds,
                names = names,
                onOpenBill = onOpenBill,
                onBack = onBack,
            )
        }
        page.startsWith("tag:") -> {
            val id = page.substringAfter(':').toLongOrNull() ?: 0L
            BillListPage(
                title = tags.firstOrNull { it.tag_id == id }?.name ?: "标签下钻",
                bills = bills,
                refunds = refunds,
                names = names,
                onOpenBill = onOpenBill,
                onBack = onBack,
            )
        }
        page == "tags" -> TagPickerPage(tags, month, year, type, onOpenPage, onBack)
        page == "type" -> BillListPage(
            title = if (type == 2L) "收入明细" else "支出明细",
            bills = bills,
            refunds = refunds,
            names = names,
            onOpenBill = onOpenBill,
            onBack = onBack,
            tabs = {
                ChoiceChip("支出", type == 1L) { onType(1L) }
                ChoiceChip("收入", type == 2L) { onType(2L) }
            },
        )
        else -> ReportPage(year, months, onBack)
    }
}

/** 分类或标签的账单清单。合计按扣掉退款后的净额算。 */
@Composable
private fun BillListPage(
    title: String,
    bills: List<Bill>,
    refunds: List<Refund_bill>,
    names: Map<Long, String>,
    onOpenBill: (String) -> Unit,
    onBack: () -> Unit,
    tabs: (@Composable () -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val refunded = refunds.groupBy { it.client_id }.mapValues { (_, items) -> items.sumOf { it.money } }
    val net = bills.sumOf { bill -> netPaid(bill.money, refunded[bill.client_id] ?: 0L) }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(title, onBack)
        tabs?.invoke()
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("合计 ${formatYuan(net)} · ${bills.size} 笔", color = palette.textSecondary, fontSize = 13.sp)
            if (bills.isEmpty()) {
                Text("这段时间没有账单，去记一笔后这里会出现明细", color = palette.textSecondary, fontSize = 13.sp)
            }
            bills.forEach { bill ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBill(bill.client_id) }
                        .glassCard(elevation = 2.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(bill.desc.ifEmpty { names[bill.member_cate_id] ?: "未分类" }, color = palette.textPrimary, modifier = Modifier.weight(1f))
                        Text(formatYuan(netPaid(bill.money, refunded[bill.client_id] ?: 0L)), color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        buildString {
                            append(bill.day)
                            append(" · ")
                            append(names[bill.member_cate_id] ?: "未分类")
                            val back = refunded[bill.client_id] ?: 0L
                            if (back > 0L) append(" · 已退 ${formatYuan(back)}")
                        },
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** 标签清单：点一个标签进它的账单清单。 */
@Composable
private fun TagPickerPage(
    tags: List<Tag>,
    month: Long,
    year: Long,
    type: Long,
    onOpenPage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("标签", onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tags.isEmpty()) {
                Text("还没有标签，在「分类与标签」里建一个后这里会出现", color = palette.textSecondary, fontSize = 13.sp)
            }
            tags.forEach { tag ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPage("tag:${tag.tag_id}") }
                        .glassCard(elevation = 2.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(tag.name, color = palette.textPrimary)
                    Text("看这个标签下的账单", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** 类型下钻：一年 12 个月的收支列表。 */
@Composable
private fun MonthListPage(title: String, months: List<PeriodTotal>, onBack: () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            months.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glassCard(elevation = 2.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("${row.key / 100} 年 ${row.key % 100} 月", color = palette.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "支 ${formatYuan(row.expense)} · 收 ${formatYuan(row.income)}",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/** 年度报表：12 个月 + 年度合计。 */
@Composable
private fun ReportPage(year: Long, months: List<PeriodTotal>, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val expense = months.sumOf { it.expense }
    val income = months.sumOf { it.income }
    Column(Modifier.fillMaxSize().background(palette.appBg)) {
        BackHeader("${year} 年度报表", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("全年支出", color = palette.textSecondary, fontSize = 12.sp)
                    Text(formatYuan(expense), color = palette.danger, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("全年收入", color = palette.textSecondary, fontSize = 12.sp)
                    Text(formatYuan(income), color = palette.brand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("全年结余", color = palette.textSecondary, fontSize = 12.sp)
                    Text(
                        formatYuan(income - expense),
                        color = if (income - expense < 0) palette.danger else palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            months.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().glassCard(elevation = 2.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("${row.key % 100} 月", color = palette.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "支 ${formatYuan(row.expense)} · 结 ${formatYuan(row.income - row.expense)}",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
